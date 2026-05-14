package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

/**
 * SellerAgent - Conducts an English (ascending-price) auction.
 *
 * Protocol:
 *  1. Register auction service in DF.
 *  2. Wait for buyers to register (CFP phase).
 *  3. Broadcast current price as CFP.
 *  4. Collect bids; track the highest bidder.
 *  5. Repeat until no new bids or MAX_ROUNDS reached.
 *  6. Declare winner if final price > reserve price.
 */
public class SellerAgent extends Agent {

    // ── Auction parameters ──────────────────────────────────────────────────
    private String  productName  = "LaptopXPro";
    private double  openingPrice = 500.0;
    private double  reservePrice = 700.0;
    private double  currentPrice;

    // ── Auction state ────────────────────────────────────────────────────────
    private AID     winner       = null;
    private int     round        = 0;
    private static final int    MAX_ROUNDS     = 3;
    private static final long   ROUND_TIMEOUT  = 5000; // ms to wait for bids

    // ── Registered buyers (discovered via DF) ───────────────────────────────
    private List<AID> registeredBuyers = new ArrayList<>();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            productName  = (String) args[0];
            openingPrice = Double.parseDouble((String) args[1]);
            reservePrice = Double.parseDouble((String) args[2]);
        }
        currentPrice = openingPrice;

        System.out.println("=== SELLER AGENT STARTED ===");
        System.out.printf("  Product      : %s%n", productName);
        System.out.printf("  Opening price: %.2f%n", openingPrice);
        System.out.printf("  Reserve price: %.2f (hidden)%n", reservePrice);

        // Register this seller in the DF so buyers can find it
        registerInDF();

        // Start auction after a short delay 
        addBehaviour(new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                myAgent.addBehaviour(new AuctionBehaviour());
            }
        });
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("SellerAgent " + getAID().getName() + " terminating.");
    }

    // ── DF registration ──────────────────────────────────────────────────────
    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("auction-service");
        sd.setName("product-auction");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    // ── Discover buyers from DF ──────────────────────────────────────────────
    private List<AID> discoverBuyers() {
        List<AID> buyers = new ArrayList<>();
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("buyer-service");
        template.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription d : results) {
                buyers.add(d.getName());
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return buyers;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Main auction behaviour
    // ════════════════════════════════════════════════════════════════════════
    private class AuctionBehaviour extends Behaviour {

        // States
        private static final int DISCOVER   = 0;
        private static final int BROADCAST  = 1;
        private static final int COLLECT    = 2;
        private static final int EVALUATE   = 3;
        private static final int CLOSE      = 4;
        private static final int DONE       = 5;

        private int state = DISCOVER;
        private long roundStart;
        private Map<AID, Double> bids = new HashMap<>();
        private boolean auctionDone = false;

        @Override
        public void action() {
            switch (state) {

                // ── Phase 0: discover buyers ─────────────────────────────
                case DISCOVER:
                    registeredBuyers = discoverBuyers();
                    if (registeredBuyers.isEmpty()) {
                        System.out.println("[Seller] No buyers found yet, retrying...");
                        block(1000);
                        return;
                    }
                    System.out.println("[Seller] Found " + registeredBuyers.size() + " buyer(s).");
                    state = BROADCAST;
                    break;

                // ── Phase 1: broadcast CFP ───────────────────────────────
                case BROADCAST:
                    round++;
                    System.out.printf("%n[Seller] ── Round %d ──  Current price: %.2f%n", round, currentPrice);
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.setContent(productName + ":" + currentPrice);
                    cfp.setConversationId("auction-" + getLocalName());
                    for (AID buyer : registeredBuyers) cfp.addReceiver(buyer);
                    send(cfp);
                    bids.clear();
                    roundStart = System.currentTimeMillis();
                    state = COLLECT;
                    break;

                // ── Phase 2: collect bids ────────────────────────────────
                case COLLECT:
                    MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                        MessageTemplate.MatchConversationId("auction-" + getLocalName())
                    );
                    ACLMessage msg = myAgent.receive(mt);
                    if (msg != null) {
                        double bid = Double.parseDouble(msg.getContent());
                        bids.put(msg.getSender(), bid);
                        System.out.printf("  [Seller] Bid from %s: %.2f%n",
                            msg.getSender().getLocalName(), bid);
                    }
                    // Timeout: move to evaluation
                    if (System.currentTimeMillis() - roundStart > ROUND_TIMEOUT) {
                        state = EVALUATE;
                    } else {
                        block(200);
                    }
                    break;

                // ── Phase 3: evaluate bids ───────────────────────────────
                case EVALUATE:
                    if (bids.isEmpty()) {
                        System.out.println("[Seller] No bids received — auction ends.");
                        state = CLOSE;
                        break;
                    }
                    // Find highest bid
                    AID  bestBidder = null;
                    double bestBid = currentPrice;
                    for (Map.Entry<AID, Double> e : bids.entrySet()) {
                        if (e.getValue() > bestBid) {
                            bestBid   = e.getValue();
                            bestBidder = e.getKey();
                        }
                    }
                    if (bestBidder == null) {
                        System.out.println("[Seller] No bid exceeded current price — auction ends.");
                        state = CLOSE;
                        break;
                    }
                    currentPrice = bestBid;
                    winner       = bestBidder;
                    System.out.printf("[Seller] Best bid so far: %.2f by %s%n",
                        currentPrice, winner.getLocalName());

                    // Notify all buyers of the new highest price
                    ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                    inform.setContent("HIGHEST:" + currentPrice);
                    inform.setConversationId("auction-" + getLocalName());
                    for (AID b : registeredBuyers) inform.addReceiver(b);
                    send(inform);

                    if (round >= MAX_ROUNDS) {
                        System.out.println("[Seller] Max rounds reached.");
                        state = CLOSE;
                    } else {
                        state = BROADCAST;
                    }
                    break;

                // ── Phase 4: close auction ───────────────────────────────
                case CLOSE:
                    if (winner != null && currentPrice >= reservePrice) {
                    	System.out.println("\\n\\n");
                        System.out.println("  ╔══════════════════════════════════════════════════╗");
                        System.out.printf( "  ║ SOLD: %-30s                                      ║%n", productName);         
                        System.out.printf( "  ║ Winner : %-28s                                   ║%n", winner.getLocalName());
                        System.out.printf( "  ║ Price  : %-28.2f                                 ║%n", currentPrice);
                        System.out.println("  ╚══════════════════════════════════════════════════╝");

                        // Accept winner
                        ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        accept.addReceiver(winner);
                        accept.setContent("SOLD:" + productName + ":" + currentPrice);
                        accept.setConversationId("auction-" + getLocalName());
                        send(accept);

                        // Reject others
                        for (AID b : registeredBuyers) {
                            if (!b.equals(winner)) {
                                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                reject.addReceiver(b);
                                reject.setContent("NOT_SOLD");
                                reject.setConversationId("auction-" + getLocalName());
                                send(reject);
                            }
                        }
                    } else {
                        System.out.println("\n[Seller] Reserve price not met — item NOT sold.");
                        ACLMessage noSale = new ACLMessage(ACLMessage.INFORM);
                        noSale.setContent("AUCTION_CLOSED:NO_SALE");
                        noSale.setConversationId("auction-" + getLocalName());
                        for (AID b : registeredBuyers) noSale.addReceiver(b);
                        send(noSale);
                    }
                    auctionDone = true;
                    state = DONE;
                    break;
            }
        }

        @Override
        public boolean done() { return auctionDone; }
    }
}
