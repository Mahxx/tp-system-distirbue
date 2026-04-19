package org.example.clients;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Scenario 3 : Saturation progressive — de 2 a 20 clients simultanes.
 * Mesure les temps de reponse a chaque palier pour observer la degradation.
 */
public class LoadTestScenario3 {

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println("  SCENARIO 3 : Saturation progressive");
        System.out.println("  De 2 a 20 clients simultanes");
        System.out.println("==========================================");
        System.out.println();
        System.out.printf("  %-10s %-10s %-10s %-10s%n",
                "Clients", "Succes", "Echecs", "Temps moy.");
        System.out.println("  " + "-".repeat(45));

        for (int nbClients : new int[]{2, 5, 10, 15, 20}) {
            ExecutorService pool = Executors.newFixedThreadPool(nbClients);
            AtomicInteger succes = new AtomicInteger(0);
            AtomicInteger echec  = new AtomicInteger(0);
            AtomicLong    totalTime = new AtomicLong(0);
            CountDownLatch latch = new CountDownLatch(nbClients);

            for (int i = 0; i < nbClients; i++) {
                pool.submit(() -> {
                    try {
                        long t0 = System.currentTimeMillis();
                        boolean ok = LoadTestScenario1.testImapConnection("mahmoud", "2003");
                        long rt    = System.currentTimeMillis() - t0;
                        totalTime.addAndGet(rt);
                        if (ok) succes.incrementAndGet(); else echec.incrementAndGet();
                    } catch (Exception e) {
                        echec.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            pool.shutdown();

            double moyMs = nbClients > 0 ? (double) totalTime.get() / nbClients : 0;
            System.out.printf("  %-10d %-10d %-10d %.1f ms%n",
                    nbClients, succes.get(), echec.get(), moyMs);

            Thread.sleep(500); // pause entre paliers
        }

        System.out.println();
        System.out.println("------------------------------------------");
        System.out.println("  Analyse : une augmentation du temps moyen");
        System.out.println("  indique l'approche de la saturation.");
        System.out.println("------------------------------------------");
    }
}
