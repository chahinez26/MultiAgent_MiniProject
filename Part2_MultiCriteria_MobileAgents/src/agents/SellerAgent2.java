package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.Locale;

public class SellerAgent2 extends Agent {

    private String sellerName    = "SellerA";
    private double price         = 450.0;
    private double quality       = 85.0;
    private double deliveryCost  = 30.00;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 4) {
            sellerName   = (String) args[0];
            price        = Double.parseDouble((String) args[1]);
            quality      = Double.parseDouble((String) args[2]);
            deliveryCost = Double.parseDouble((String) args[3]);
        }
        System.out.printf("[%s] Offer — Price: %.2f  Quality: %.2f  Delivery: %.2f%n",
            sellerName, price, quality, deliveryCost);

        registerInDF();
        addBehaviour(new OfferResponderBehaviour());
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("seller-service");
        sd.setName("product-seller");
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) { e.printStackTrace(); }
    }

    // ── Respond to buyer's REQUEST ───────────────────────────────────────────
    private class OfferResponderBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage req = myAgent.receive(mt);
            if (req != null && "GET_OFFER".equals(req.getContent())) {
                ACLMessage reply = req.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                // Force US locale to ensure dot decimal separator
                reply.setContent(String.format(Locale.US, "OFFER:%.2f:%.2f:%.2f",
                    price, quality, deliveryCost));
                myAgent.send(reply);
                System.out.printf("[%s] Sent offer to %s%n",
                    sellerName, req.getSender().getLocalName());
            } else {
                block(200);
            }
        }
    }
}