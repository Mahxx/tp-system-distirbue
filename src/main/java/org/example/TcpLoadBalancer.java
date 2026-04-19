package org.example;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * TcpLoadBalancer - Equilibreur de charge TCP en Java pur.
 * Gere SMTP, POP3 et IMAP avec la politique Round Robin.
 * Pas besoin de HAProxy ni de logiciel externe.
 *
 * Architecture :
 *   Client → [TcpLoadBalancer] → Instance-1 ou Instance-2
 *
 * Ports frontaux (vus par les clients / JavaMail) :
 *   SMTP  :   25  → distribue vers 2526 et 2527
 *   POP3  :  110  → distribue vers 1111 et 1112
 *   IMAP  : 1430  → distribue vers 1431 et 1432
 */
public class TcpLoadBalancer {

    // ---- Statistiques globales ----
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong totalErrors   = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("  TCP LOAD BALANCER — Systeme de Messagerie BGH");
        System.out.println("  Politique : Round Robin");
        System.out.println("=================================================");
        System.out.println();

        // Lancer les 3 load balancers dans des threads separes
        ExecutorService exec = Executors.newCachedThreadPool();

        exec.submit(new BalancerNode("SMTP",  25,
                Arrays.asList("127.0.0.1:2526", "127.0.0.1:2527")));
        exec.submit(new BalancerNode("POP3",  110,
                Arrays.asList("127.0.0.1:1111", "127.0.0.1:1112")));
        exec.submit(new BalancerNode("IMAP",  1430,
                Arrays.asList("127.0.0.1:1431", "127.0.0.1:1432")));

        System.out.println("  [LB-SMTP]  Ecoute port 25    → 2526 / 2527");
        System.out.println("  [LB-POP3]  Ecoute port 110   → 1111 / 1112");
        System.out.println("  [LB-IMAP]  Ecoute port 1430  → 1431 / 1432");
        System.out.println();
        System.out.println("  Statistiques affichees toutes les 10 secondes.");
        System.out.println("-------------------------------------------------");

        // Thread de statistiques periodiques
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
                System.out.printf("[STATS %s] Requetes totales=%d  Erreurs=%d%n",
                        new SimpleDateFormat("HH:mm:ss").format(new Date()),
                        totalRequests.get(), totalErrors.get());
            }
        }).start();

        exec.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    // =========================================================
    //  BalancerNode : gere UN protocole avec Round Robin
    // =========================================================
    static class BalancerNode implements Runnable {
        private final String    protocol;
        private final int       frontPort;
        private final List<String> backends;
        private final AtomicInteger counter = new AtomicInteger(0);
        private final AtomicLong    requests = new AtomicLong(0);
        private final AtomicLong    errors   = new AtomicLong(0);
        private final boolean[] healthy;

        BalancerNode(String protocol, int frontPort, List<String> backends) {
            this.protocol  = protocol;
            this.frontPort = frontPort;
            this.backends  = backends;
            this.healthy   = new boolean[backends.size()];
            Arrays.fill(this.healthy, true);
        }

        @Override
        public void run() {
            startHealthChecker();
            try (ServerSocket server = new ServerSocket(frontPort)) {
                log("Demarre sur port " + frontPort);
                while (true) {
                    Socket client = server.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            } catch (IOException e) {
                log("ERREUR demarrage : " + e.getMessage());
            }
        }

        private void handleClient(Socket client) {
            String backend = pickBackend();
            if (backend == null) {
                log("Tous les backends sont hors-ligne !");
                errors.incrementAndGet();
                totalErrors.incrementAndGet();
                try { client.close(); } catch (IOException ignored) {}
                return;
            }

            String[] parts = backend.split(":");
            String backHost = parts[0];
            int    backPort = Integer.parseInt(parts[1]);

            try (Socket server = new Socket(backHost, backPort)) {
                requests.incrementAndGet();
                totalRequests.incrementAndGet();
                log(String.format("%-20s → %s  [total=%d]",
                        client.getRemoteSocketAddress(), backend, requests.get()));

                // Relay bidirectionnel client ↔ backend
                Thread t1 = relay(client.getInputStream(),  server.getOutputStream());
                Thread t2 = relay(server.getInputStream(),  client.getOutputStream());
                t1.start();
                t2.start();
                t1.join();
                t2.join();
            } catch (Exception e) {
                errors.incrementAndGet();
                totalErrors.incrementAndGet();
                markUnhealthy(backend);
            } finally {
                try { client.close(); } catch (IOException ignored) {}
            }
        }

        /** Round Robin parmi les backends sains */
        private String pickBackend() {
            for (int attempt = 0; attempt < backends.size(); attempt++) {
                int idx = counter.getAndIncrement() % backends.size();
                if (healthy[idx]) return backends.get(idx);
            }
            return null; // tous down
        }

        private void markUnhealthy(String backend) {
            int idx = backends.indexOf(backend);
            if (idx >= 0) {
                healthy[idx] = false;
                log("Backend OFFLINE : " + backend);
            }
        }

        /** Health check passif : verifie si le backend accepte une connexion TCP */
        private void startHealthChecker() {
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    for (int i = 0; i < backends.size(); i++) {
                        String[] p = backends.get(i).split(":");
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(p[0], Integer.parseInt(p[1])), 1000);
                            if (!healthy[i]) {
                                healthy[i] = true;
                                log("Backend ONLINE (recupere) : " + backends.get(i));
                            }
                        } catch (Exception e) {
                            if (healthy[i]) {
                                healthy[i] = false;
                                log("Backend OFFLINE (health check) : " + backends.get(i));
                            }
                        }
                    }
                }
            }).start();
        }

        private Thread relay(InputStream in, OutputStream out) {
            return new Thread(() -> {
                byte[] buf = new byte[4096];
                try {
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                } catch (IOException ignored) {}
            });
        }

        private void log(String msg) {
            System.out.printf("[%s][%s] %s%n",
                    new SimpleDateFormat("HH:mm:ss").format(new Date()),
                    protocol, msg);
        }
    }
}
