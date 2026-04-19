package org.example.clients;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Scenario 1 : Repartition simple — 10 connexions IMAP successives
 * Demontre que HAProxy distribue en Round Robin entre les 2 instances.
 */
public class LoadTestScenario1 {

    private static final String HOST = "localhost";
    private static final int    PORT = 1430;  // port HAProxy (redistribue vers 1431/1432)
    private static final String USER = "mahmoud";
    private static final String PASS = "2003";

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println("  SCENARIO 1 : Repartition Round Robin");
        System.out.println("  Host : " + HOST + ":" + PORT + " (HAProxy)");
        System.out.println("  10 connexions IMAP successives");
        System.out.println("==========================================");
        System.out.println();

        int succes = 0, echec = 0;
        long debut = System.currentTimeMillis();

        for (int i = 1; i <= 10; i++) {
            System.out.printf("  [%2d] Connexion ... ", i);
            try {
                long t0 = System.currentTimeMillis();
                boolean ok = testImapConnection(USER, PASS);
                long rt = System.currentTimeMillis() - t0;
                if (ok) {
                    System.out.printf("OK  (%d ms)%n", rt);
                    succes++;
                } else {
                    System.out.printf("AUTH ECHOUEE%n");
                    echec++;
                }
            } catch (Exception e) {
                System.out.printf("ERREUR : %s%n", e.getMessage());
                echec++;
            }
        }

        long total = System.currentTimeMillis() - debut;
        System.out.println();
        System.out.println("------------------------------------------");
        System.out.printf("  Succes : %d  |  Echecs : %d%n", succes, echec);
        System.out.printf("  Temps total : %d ms%n", total);
        System.out.printf("  Temps moyen : %.1f ms/connexion%n", total / 10.0);
        System.out.println();
        System.out.println("  Verifiez les fenetres IMAP Instance-1 [1431]");
        System.out.println("  et IMAP Instance-2 [1432] pour voir la distribution.");
        System.out.println("------------------------------------------");
    }

    static boolean testImapConnection(String user, String pass) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(HOST, PORT), 3000);
            s.setSoTimeout(3000);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter    out = new PrintWriter(s.getOutputStream(), true);

            in.readLine(); // * OK IMAP4rev2 Service Ready

            out.println("A0 CAPABILITY");
            String cap = in.readLine();
            while (cap != null && !cap.startsWith("A0 ")) cap = in.readLine();

            out.println("A1 LOGIN " + user + " " + pass);
            String loginResp = in.readLine();
            while (loginResp != null && !loginResp.startsWith("A1 ")) loginResp = in.readLine();

            boolean ok = loginResp != null && loginResp.contains("OK");

            out.println("A2 LOGOUT");
            return ok;
        }
    }
}
