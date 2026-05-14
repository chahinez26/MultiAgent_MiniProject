package agents;

import java.util.Random;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

/**
 * BuyerAgent - Participates in an English auction.
 *
 * Strategy: Bid (current_price + increment) as long as the new price
 *           does not exceed the buyer's private budget.
 *
 */
public class BuyerAgent extends Agent {

    private double budget;
    private double increment;
    private double lastSeenPrice = 0;
    private boolean active = true; 

    @Override
    protected void setup() {
        Random rng = new Random(getLocalName().hashCode());
        budget    = 600 + rng.nextInt(300);   // 600-900
        increment = 10  + rng.nextInt(40);    // 10-50

        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            budget    = Double.parseDouble((String) args[0]);
            increment = Double.parseDouble((String) args[1]);
        }

        System.out.printf("[%s] Ready — Budget: %.2f  Increment: %.2f%n",
            getLocalName(), budget, increment);

        registerInDF();
        addBehaviour(new AuctionParticipantBehaviour());
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("buyer-service");
        sd.setName("auction-buyer");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) { e.printStackTrace(); }
    }

    // ════════════════════════════════════════════════════════════════════════
    private class AuctionParticipantBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            // ── Listen for CFP (new round) ───────────────────────────────
            MessageTemplate cfpMT = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage cfp = myAgent.receive(cfpMT);
            if (cfp != null) {
                String[] parts = cfp.getContent().split(":");
                double   askPrice = Double.parseDouble(parts[1]);
                lastSeenPrice = askPrice;

                double myBid = askPrice + increment;
                if (active && myBid <= budget) {
                    // Place bid
                    ACLMessage propose = cfp.createReply();
                    propose.setPerformative(ACLMessage.PROPOSE);
                    propose.setContent(String.valueOf(myBid));
                    myAgent.send(propose);
                    System.out.printf("  [%s] Bidding %.2f (budget: %.2f)%n",
                        getLocalName(), myBid, budget);
                } else {
                    // Pass — send REFUSE
                    active = false;
                    ACLMessage refuse = cfp.createReply();
                    refuse.setPerformative(ACLMessage.REFUSE);
                    refuse.setContent("TOO_EXPENSIVE");
                    myAgent.send(refuse);
                    System.out.printf("  [%s] Passing — %.2f exceeds budget %.2f%n",
                        getLocalName(), myBid, budget);
                }
                return;
            }

            // ── Listen for highest-price updates ────────────────────────
            MessageTemplate infoMT = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchContent("HIGHEST:.*")   
            );
            ACLMessage info = myAgent.receive(infoMT);
            if (info != null) {
                String content = info.getContent();
                if (content.startsWith("HIGHEST:")) {
                    double newPrice = Double.parseDouble(content.split(":")[1]);
                    lastSeenPrice = newPrice;
                    System.out.printf("  [%s] Notified: new price is %.2f%n",
                        getLocalName(), newPrice);
                } else if (content.startsWith("AUCTION_CLOSED")) {
                    System.out.printf("  [%s] Auction closed with no sale.%n", getLocalName());
                }
                return;
            }

            // ── Listen for final result ──────────────────────────────────
            MessageTemplate resultMT = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
            );
            ACLMessage result = myAgent.receive(resultMT);
            if (result != null) {
                if (result.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    String[] parts = result.getContent().split(":");
                    System.out.printf("%n  [%s] *** WON the auction! Product: %s  Price: %s ***%n",
                        getLocalName(), parts[1], parts[2]);
                } else {
                    System.out.printf("  [%s] Did not win.%n", getLocalName());
                }
                myAgent.doDelete();
                return;
            }

            block(200);
        }
    }
}
