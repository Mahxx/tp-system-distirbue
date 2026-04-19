package org.example.clients;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Scenario 6 : Test de charge sur l'API Web (HTTP)
 * Remplace Apache Bench en envoyant 100 requêtes concurrentes vers NGINX (port 80).
 */
public class LoadTestScenario6 {

    private static final String TARGET_URL = "http://localhost:80/api/messages";
    private static final int TOTAL_REQUESTS = 100;
    private static final int CONCURRENT_THREADS = 10;

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println("  SCENARIO 6 : Test NGINX Web API (HTTP)");
        System.out.println("  100 requetes HTTP / 10 concurrentes");
        System.out.println("  Cible : " + TARGET_URL);
        System.out.println("==========================================");
        System.out.println();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        long globalStart = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            pool.submit(() -> {
                long t0 = System.currentTimeMillis();
                try {
                    URL url = new URL(TARGET_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    // On envoie un header bidon car on teste juste la capacite de NGINX a router la requete
                    conn.setRequestProperty("Authorization", "Bearer dummy_token");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);

                    // Si NGINX redirige vers 8080 ou 8081, on recevra au moins un code HTTP (ex: 401 Unauthorized ou 200 OK)
                    int code = conn.getResponseCode(); 
                    if (code > 0) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    long rt = System.currentTimeMillis() - t0;
                    totalTime.addAndGet(rt);
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        long globalTime = System.currentTimeMillis() - globalStart;

        System.out.println("------------------------------------------");
        System.out.printf("  Requetes reussies : %d / %d%n", successCount.get(), TOTAL_REQUESTS);
        System.out.printf("  Erreurs web       : %d%n", errorCount.get());
        System.out.printf("  Temps d'execution : %d ms%n", globalTime);
        System.out.printf("  Temps moyen/req   : %.2f ms%n", (double) totalTime.get() / TOTAL_REQUESTS);
        System.out.printf("  Debit             : %.2f requetes/sec%n", (TOTAL_REQUESTS / (globalTime / 1000.0)));
        System.out.println("------------------------------------------");
        System.out.println();
        System.out.println("  Regardez la fenetre ou tourne NGINX !");
        System.out.println("  Vous pouvez ouvrir le ficher E:\\nginx-1.30.0\\logs\\access.log");
        System.out.println("  pour voir la distribution entre les ports 8080 et 8081.");
    }
}
