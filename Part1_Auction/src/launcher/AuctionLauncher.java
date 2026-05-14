package launcher;

import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * AuctionLauncher — :
 *   - 1 SellerAgent  (product: "PRODUCT", OPENING, RESERVE)
 *   - 3 BuyerAgents  with different budgets and increment strategies
 *
 */
public class AuctionLauncher {

    public static void main(String[] args) throws Exception {
        Runtime  rt        = Runtime.instance();
        Profile  p         = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "true");

        AgentContainer container = rt.createMainContainer(p);

        // ── Launch Seller ────────────────────────────────────────────────
        AgentController seller = container.createNewAgent(
            "Seller",
            "agents.SellerAgent",
            new Object[]{"Tableau Les femmes d'alger by Pablo Picasso", "500.0", "800.0"}
        );
        seller.start();

        Thread.sleep(500); // let seller register in DF

        // ── Launch Buyers ────────────────────────────────────────────────
        String[][] buyerConfigs = {
            {"Buyer1", "750.0", "100.0"},   
            {"Buyer2", "820.0", "105.0"},   
            {"Buyer3", "680.0", "110.0"},   
        };

        for (String[] cfg : buyerConfigs) {
            AgentController buyer = container.createNewAgent(
                cfg[0],
                "agents.BuyerAgent",
                new Object[]{cfg[1], cfg[2]}
            );
            buyer.start();
            Thread.sleep(200);
        }

        System.out.println("All agents started. Auction will begin shortly...");
    }
}
