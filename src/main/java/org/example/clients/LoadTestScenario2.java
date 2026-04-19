package org.example.clients;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Scenario 2 : Charge concurrente — 5 clients IMAP simultanement.
 * Demontre que le LB gere plusieurs connexions en parallele.
 */
public class LoadTestScenario2 {

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println("  SCENARIO 2 : Charge concurrente");
        System.out.println("  5 clients IMAP simultanes");
        System.out.println("==========================================");
        System.out.println();

        int nbClients = 5;
        ExecutorService pool = Executors.newFixedThreadPool(nbClients);
        AtomicInteger succes = new AtomicInteger(0);
        AtomicInteger echec  = new AtomicInteger(0);
        CountDownLatch latch  = new CountDownLatch(nbClients);

        long debut = System.currentTimeMillis();

        for (int i = 1; i <= nbClients; i++) {
            final int clientId = i;
            pool.submit(() -> {
                try {
                    long t0 = System.currentTimeMillis();
                    boolean ok = LoadTestScenario1.testImapConnection("mahmoud", "2003");
                    long rt    = System.currentTimeMillis() - t0;
                    System.out.printf("  Client-%d : %s  (%d ms)%n",
                            clientId, ok ? "OK" : "ECHEC", rt);
                    if (ok) succes.incrementAndGet(); else echec.incrementAndGet();
                } catch (Exception e) {
                    System.out.printf("  Client-%d : ERREUR - %s%n", clientId, e.getMessage());
                    echec.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        long total = System.currentTimeMillis() - debut;
        System.out.println();
        System.out.println("------------------------------------------");
        System.out.printf("  Succes : %d  |  Echecs : %d%n", succes.get(), echec.get());
        System.out.printf("  Temps total (//): %d ms%n", total);
        System.out.println("------------------------------------------");
    }
}
