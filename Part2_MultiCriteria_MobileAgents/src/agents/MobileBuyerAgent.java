package agents;

import jade.core.Agent;
import jade.core.Location;
import jade.core.ContainerID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.AID;

import java.util.*;

/**
 * MobileBuyerAgent — Exercise 5 (DW4) with mobility.
 *
 * ─────────────────────────────────────────────────────────────────────
 * MULTI-CRITERIA DECISION FORMULA  f(x)
 * ─────────────────────────────────────────────────────────────────────
 * Given m sellers, each offering product x described by criteria:
 *   C1(x) = price          → MINIMIZE   (weight P1)
 *   C2(x) = quality        → MAXIMIZE   (weight P2)
 *   C3(x) = delivery_cost  → MINIMIZE   (weight P3)
 *
 * Step 1 — Normalize each criterion over all sellers:
 *   For MAXIMIZE Ci: norm_i(x) = (Ci(x) - Ci_min) / (Ci_max - Ci_min)
 *   For MINIMIZE Ci: norm_i(x) = (Ci_max - Ci(x)) / (Ci_max - Ci_min)
 *   Edge case: if Ci_max == Ci_min → norm_i(x) = 1.0 for all
 *
 * Step 2 — Weighted aggregation:
 *   f(x) = Σ Pi * norm_i(x)   where Σ Pi = 1
 *
 * Step 3 — Select seller with highest f(x).
 * ─────────────────────────────────────────────────────────────────────
 *
 * MOBILITY:
 *   The buyer migrates to each seller's container to collect its offer
 *   locally, then returns home to evaluate all offers.
 *
 *   Two migration scenarios are supported:
 *     - Inter-container (same platform, different containers)
 *     - Inter-platform  (different JADE platforms via JADE-Gateway or LEAP)
 */
public class MobileBuyerAgent extends Agent {

    // ── Buyer preferences (weights must sum to 1) ────────────────────────────
    private double weightPrice    = 0.5;   // P1 — price is most important
    private double weightQuality  = 0.3;   // P2
    private double weightDelivery = 0.2;   // P3

    // ── State carried during migration ──────────────────────────────────────
    private List<String>   containerNames = new ArrayList<>();
    private List<AID>      sellerAIDs     = new ArrayList<>();
    private List<double[]> offers         = new ArrayList<>();  // [price, quality, delivery]
    private int            visitIndex     = 0;
    private String         homeContainer  = "Main-Container";

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            weightPrice    = Double.parseDouble((String) args[0]);
            weightQuality  = Double.parseDouble((String) args[1]);
            weightDelivery = Double.parseDouble((String) args[2]);
        }
        homeContainer = here().getName();

        System.out.println("=== MOBILE BUYER AGENT STARTED ===");
        System.out.printf("  Weights — Price: %.2f  Quality: %.2f  Delivery: %.2f%n",
            weightPrice, weightQuality, weightDelivery);

        addBehaviour(new WakerBehaviour(this, 1500) {
            @Override
            protected void onWake() { myAgent.addBehaviour(new DiscoverAndMigrateBehaviour()); }
        });
    }

    // ── Called after each migration ─────────────────────────────────────────
    @Override
    protected void afterMove() {
        System.out.println("[MobileBuyer] Arrived at: " + here().getName());
        addBehaviour(new CollectOfferBehaviour());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Phase 1 – Discover sellers and prepare migration list
    // ════════════════════════════════════════════════════════════════════════
    private class DiscoverAndMigrateBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("seller-service");
            template.addServices(sd);
            try {
                DFAgentDescription[] results = DFService.search(myAgent, template);
                for (DFAgentDescription d : results) {
                    sellerAIDs.add(d.getName());
                    // Convention: each seller runs in container named after it
                    containerNames.add("Container-" + d.getName().getLocalName());
                    System.out.println("  Found seller: " + d.getName().getLocalName());
                }
            } catch (FIPAException e) { e.printStackTrace(); }

            if (sellerAIDs.isEmpty()) {
                System.out.println("[MobileBuyer] No sellers found!");
                doDelete();
                return;
            }

            // Start migration to first seller
            migrateToNext();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Phase 2 – Collect offer at current container
    // ════════════════════════════════════════════════════════════════════════
    private class CollectOfferBehaviour extends SimpleBehaviour {
        private boolean done = false;

        @Override
        public void action() {
            // Query local seller
            AID seller = sellerAIDs.get(visitIndex - 1);
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(seller);
            req.setContent("GET_OFFER");
            myAgent.send(req);

            // Wait for reply
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage reply = myAgent.receive(mt);
            if (reply != null) {
                String content = reply.getContent(); // "OFFER:price:quality:delivery"
                String[] parts = content.split(":");
                double price    = Double.parseDouble(parts[1]);
                double quality  = Double.parseDouble(parts[2]);
                double delivery = Double.parseDouble(parts[3]);
                offers.add(new double[]{price, quality, delivery});
                System.out.printf("  [MobileBuyer] Collected from %s → P=%.2f Q=%.2f D=%.2f%n",
                    seller.getLocalName(), price, quality, delivery);
                done = true;

                // Migrate to next seller or go home
                if (visitIndex < sellerAIDs.size()) {
                    migrateToNext();
                } else {
                    // Return home
                    System.out.println("[MobileBuyer] All sellers visited. Returning home...");
                    doMove(new ContainerID(homeContainer, null));
                    myAgent.addBehaviour(new EvaluateOffersBehaviour());
                }
            } else {
                block(300);
            }
        }

        @Override public boolean done() { return done; }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Phase 3 – Evaluate all offers using f(x) formula
    // ════════════════════════════════════════════════════════════════════════
    private class EvaluateOffersBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            int n = offers.size();
            if (n == 0) { System.out.println("[MobileBuyer] No offers to evaluate!"); return; }

            // ── Extract raw criteria values ──────────────────────────────
            double[] prices    = new double[n];
            double[] qualities = new double[n];
            double[] deliveries= new double[n];
            for (int i = 0; i < n; i++) {
                prices[i]     = offers.get(i)[0];
                qualities[i]  = offers.get(i)[1];
                deliveries[i] = offers.get(i)[2];
            }

            // ── Normalize ────────────────────────────────────────────────
            double[] normPrice    = normalizeMinimize(prices);
            double[] normQuality  = normalizeMaximize(qualities);
            double[] normDelivery = normalizeMinimize(deliveries);

            // ── Compute f(x) ─────────────────────────────────────────────
            System.out.println("\n══════════════ MULTI-CRITERIA EVALUATION ══════════════");
            System.out.printf("%-10s %8s %8s %8s  %12s%n",
                "Seller", "Price", "Quality", "Delivery", "f(x)");
            System.out.println("─────────────────────────────────────────────────────────");

            int    bestIdx  = -1;
            double bestScore = -1;
            double[] scores = new double[n];

            for (int i = 0; i < n; i++) {
                scores[i] = weightPrice    * normPrice[i]
                          + weightQuality  * normQuality[i]
                          + weightDelivery * normDelivery[i];

                System.out.printf("%-10s %8.2f %8.2f %8.2f  %12.4f%n",
                    sellerAIDs.get(i).getLocalName(),
                    prices[i], qualities[i], deliveries[i], scores[i]);

                if (scores[i] > bestScore) {
                    bestScore = scores[i];
                    bestIdx   = i;
                }
            }

            System.out.println("─────────────────────────────────────────────────────────");
            System.out.printf("Weights: P1(price)=%.2f  P2(quality)=%.2f  P3(delivery)=%.2f%n",
                weightPrice, weightQuality, weightDelivery);
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.printf( "║ BEST OFFER: %-26s ║%n",
                sellerAIDs.get(bestIdx).getLocalName());
            System.out.printf( "║ Score f(x) = %-25.4f ║%n", bestScore);
            System.out.printf( "║ Price=%.2f  Quality=%.2f  Del=%.2f%n",
                prices[bestIdx], qualities[bestIdx], deliveries[bestIdx]);
            System.out.println("╚══════════════════════════════════════╝");

            myAgent.doDelete();
        }
    }

    // ── Normalization helpers ─────────────────────────────────────────────────
    private double[] normalizeMaximize(double[] vals) {
        double min = Arrays.stream(vals).min().getAsDouble();
        double max = Arrays.stream(vals).max().getAsDouble();
        double range = max - min;
        double[] out = new double[vals.length];
        for (int i = 0; i < vals.length; i++)
            out[i] = (range == 0) ? 1.0 : (vals[i] - min) / range;
        return out;
    }

    private double[] normalizeMinimize(double[] vals) {
        double min = Arrays.stream(vals).min().getAsDouble();
        double max = Arrays.stream(vals).max().getAsDouble();
        double range = max - min;
        double[] out = new double[vals.length];
        for (int i = 0; i < vals.length; i++)
            out[i] = (range == 0) ? 1.0 : (max - vals[i]) / range;
        return out;
    }

    // ── Migration helper ─────────────────────────────────────────────────────
    private void migrateToNext() {
        String target = containerNames.get(visitIndex);
        visitIndex++;
        System.out.println("[MobileBuyer] Migrating to: " + target);
        doMove(new ContainerID(target, null));
    }
}
