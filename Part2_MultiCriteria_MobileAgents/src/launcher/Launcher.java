package launcher;

import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * Launcher for Part 2 — Multi-criteria decision with mobile agent.
 *
 * Architecture:
 *   Main-Container        ← MobileBuyerAgent starts here (home)
 *   Container-SellerA     ← SellerAgent A
 *   Container-SellerB     ← SellerAgent B
 *   Container-SellerC     ← SellerAgent C
 *
 * The buyer will migrate to each seller container, collect the offer,
 * then return home to evaluate and select the best offer via f(x).
 *
 * To demo inter-platform migration, start a second JADE platform on
 * another machine and register SellerD there (see README).
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.instance();

        // ── Main container (buyer's home) ────────────────────────────────
        Profile mainProfile = new ProfileImpl();
        mainProfile.setParameter(Profile.MAIN_HOST, "localhost");
        mainProfile.setParameter(Profile.MAIN_PORT, "1099");
        mainProfile.setParameter(Profile.GUI, "true");
        AgentContainer mainContainer = rt.createMainContainer(mainProfile);

        // ── Seller containers (inter-container migration) ────────────────
        String[][] sellers = {
            {"SellerA", "450.0", "85.0",  "30.0"},   // good quality, moderate price
            {"SellerB", "380.0", "60.0",  "15.0"},   // cheap but low quality
            {"SellerC", "520.0", "92.0",  "25.0"},   // expensive, very high quality
        };

        for (String[] cfg : sellers) {
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");
            p.setParameter(Profile.CONTAINER_NAME, "Container-" + cfg[0]);
            AgentContainer container = rt.createAgentContainer(p);

            AgentController sellerCtrl = container.createNewAgent(
                cfg[0],
                "agents.SellerAgent2",
                cfg
            );
            sellerCtrl.start();
            System.out.println("Started " + cfg[0] + " in Container-" + cfg[0]);
            Thread.sleep(300);
        }

        Thread.sleep(1000); // Let sellers register in DF

        // ── Mobile Buyer (starts in main container) ──────────────────────
        // Weights: price=0.5, quality=0.3, delivery=0.2
        AgentController buyer = mainContainer.createNewAgent(
            "MobileBuyer",
            "agents.MobileBuyerAgent",
            new Object[]{"0.5", "0.3", "0.2"}
        );
        buyer.start();
        System.out.println("MobileBuyerAgent started. It will now migrate to each seller...");
    }
}
