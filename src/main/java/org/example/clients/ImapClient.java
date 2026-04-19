package org.example.clients;

import javax.mail.*;
import javax.mail.search.SubjectTerm;
import java.util.Properties;
import java.util.Scanner;

/**
 * Client IMAP (Section 7) — utilise Jakarta Mail API.
 *
 * Serveur : localhost:1430 (port personnalise du ImapServer maison)
 * Authentification plain login/password, pas de TLS.
 *
 * Fonctionnalites :
 *   - Authentification utilisateur
 *   - Ouverture de l'INBOX
 *   - Liste des messages avec statut Lu/Non-Lu (flag SEEN, exclusif IMAP)
 *   - Lecture d'un message (+ marquage automatique comme lu)
 *   - Marquage manuel Lu / Non-Lu
 *   - Recherche par sujet (SubjectTerm de JavaMail)
 *   - Gestion des erreurs d'auth et de connexion
 */
public class ImapClient {

    // ----- Parametres configurables -----
    private static final String IMAP_HOST = "localhost";
    private static final int    IMAP_PORT = 1430;  // port personnalise du ImapServer maison

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=======================================================");
        System.out.println("   CLIENT IMAP — Gestion avancee de la boite (JavaMail)");
        System.out.println("=======================================================");
        System.out.println("  Serveur : " + IMAP_HOST + ":" + IMAP_PORT);
        System.out.println("=======================================================");
        System.out.println();

        System.out.print("Votre nom d'utilisateur (ex: karim) : ");
        String username = scanner.nextLine().trim();

        System.out.print("Votre mot de passe : ");
        String password = scanner.nextLine().trim();

        // Configuration IMAP JavaMail
        Properties props = new Properties();
        props.put("mail.imap.host",              IMAP_HOST);
        props.put("mail.imap.port",              String.valueOf(IMAP_PORT));
        props.put("mail.imap.starttls.enable",   "false");
        props.put("mail.imap.connectiontimeout", "5000");
        props.put("mail.imap.timeout",           "5000");

        Store store = null;
        Folder inbox = null;

        try {
            System.out.println("\n... Connexion au serveur IMAP " + IMAP_HOST + ":" + IMAP_PORT + " ...");

            Session session = Session.getInstance(props, null);
            store = session.getStore("imap");
            store.connect(IMAP_HOST, username, password);

            System.out.println("[OK] Connexion reussie en tant que '" + username + "'.\n");

            // Scenario 3 : Ouvrir INBOX
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            menuPrincipal(inbox, scanner);

            inbox.close(true);
            inbox = null;
            store.close();
            store = null;

            System.out.println("\n[OK] Deconnexion IMAP terminee.");

        } catch (AuthenticationFailedException e) {
            System.err.println("\n[ERREUR AUTH] Identifiants incorrects.");
            System.err.println("  -> Verifiez votre nom d'utilisateur et votre mot de passe.");
        } catch (MessagingException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("Connection refused") || msg.contains("connect")) {
                System.err.println("\n[ERREUR CONNEXION] Impossible de joindre le serveur IMAP sur " + IMAP_HOST + ":" + IMAP_PORT);
                System.err.println("  -> Le serveur IMAP (ImapServerGUI) est-il demarre ? Lancez 'run.bat'.");
            } else {
                System.err.println("\n[ERREUR IMAP] " + msg);
            }
        } catch (Exception e) {
            System.err.println("\n[ERREUR] " + e.getMessage());
        } finally {
            try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) {}
            try { if (store != null && store.isConnected()) store.close();  } catch (Exception ignored) {}
        }
    }

    // =========================================================
    //  MENU PRINCIPAL
    // =========================================================
    private static void menuPrincipal(Folder inbox, Scanner scanner) throws MessagingException {
        boolean running = true;
        while (running) {
            // Recharge les messages a chaque iteration pour refleter les changements
            Message[] messages = inbox.getMessages();
            int total = messages.length;

            System.out.println("\n=======================================================");
            System.out.println("  INBOX — " + total + " message(s)");
            System.out.println("=======================================================");

            if (total == 0) {
                System.out.println("  Boite vide.");
            } else {
                // Liste avec statut IMAP SEEN
                for (int i = 0; i < messages.length; i++) {
                    boolean seen    = messages[i].isSet(Flags.Flag.SEEN);
                    String  status  = seen ? "[ LU  ]" : "[NONLU]";
                    String  from    = getFrom(messages[i]);
                    String  subject = messages[i].getSubject();
                    System.out.printf("  [%d] %s  De: %-25s  Sujet: %s%n", i + 1, status, from, subject);
                }
            }

            System.out.println();
            System.out.println("--- Actions disponibles ---");
            System.out.println("  1. Lire un message (le marque LU)");
            System.out.println("  2. Marquer un message comme LU");
            System.out.println("  3. Marquer un message comme NON-LU");
            System.out.println("  4. Recherche par sujet");
            System.out.println("  0. Quitter");
            System.out.print("Votre choix : ");

            String input = scanner.nextLine().trim();

            switch (input) {
                case "1" -> actionLire(messages, scanner);
                case "2" -> actionMarquer(messages, scanner, true);
                case "3" -> actionMarquer(messages, scanner, false);
                case "4" -> actionRecherche(inbox, scanner);
                case "0" -> running = false;
                default  -> System.out.println("[!] Choix invalide.");
            }
        }
    }

    // =========================================================
    //  Action 1 : Lire un message
    // =========================================================
    private static void actionLire(Message[] messages, Scanner scanner) {
        if (messages.length == 0) { System.out.println("[!] Aucun message a lire."); return; }
        System.out.print("Numero du message a lire (1-" + messages.length + ") : ");
        int num = lireEntier(scanner);
        if (num < 1 || num > messages.length) {
            System.out.println("[!] Numero invalide.");
            return;
        }
        Message msg = messages[num - 1];
        try {
            System.out.println("\n============== MESSAGE " + num + " ==============");
            System.out.println("  Sujet   : " + msg.getSubject());
            System.out.println("  De      : " + getFrom(msg));
            System.out.println("  Date    : " + msg.getSentDate());
            System.out.println("  Status  : " + (msg.isSet(Flags.Flag.SEEN) ? "Lu" : "Non lu"));
            System.out.println("----- Corps du message -----");
            Object content = msg.getContent();
            System.out.println(content != null ? content.toString().trim() : "[Pas de contenu texte]");
            System.out.println("====================================");

            // JavaMail marque SEEN automatiquement lors de getContent()
            // On s'assure que c'est fait cote serveur :
            if (!msg.isSet(Flags.Flag.SEEN)) {
                msg.setFlag(Flags.Flag.SEEN, true);
            }
            System.out.println("[INFO] Message marque comme LU sur le serveur IMAP.");

        } catch (Exception e) {
            System.err.println("[ERREUR] Impossible de lire ce message : " + e.getMessage());
        }
    }

    // =========================================================
    //  Action 2/3 : Marquer comme Lu ou Non-Lu
    // =========================================================
    private static void actionMarquer(Message[] messages, Scanner scanner, boolean seen) {
        if (messages.length == 0) { System.out.println("[!] Aucun message."); return; }
        String etat = seen ? "LU" : "NON-LU";
        System.out.print("Numero du message a marquer " + etat + " (1-" + messages.length + ") : ");
        int num = lireEntier(scanner);
        if (num < 1 || num > messages.length) {
            System.out.println("[!] Numero invalide.");
            return;
        }
        try {
            messages[num - 1].setFlag(Flags.Flag.SEEN, seen);
            System.out.println("[SUCCES] Message " + num + " marque comme " + etat + " sur le serveur IMAP.");
        } catch (MessagingException e) {
            System.err.println("[ERREUR] Impossible de modifier le flag : " + e.getMessage());
        }
    }

    // =========================================================
    //  Action 4 : Recherche par sujet (SubjectTerm JavaMail)
    // =========================================================
    private static void actionRecherche(Folder inbox, Scanner scanner) {
        System.out.print("Mot-cle a chercher dans les sujets : ");
        String keyword = scanner.nextLine().trim();
        if (keyword.isEmpty()) {
            System.out.println("[!] Mot-cle vide, recherche annulee.");
            return;
        }
        try {
            // SubjectTerm fait une recherche insensible a la casse
            Message[] found = inbox.search(new SubjectTerm(keyword));

            System.out.println("\n--- Resultats de recherche (mot-cle : \"" + keyword + "\") ---");
            System.out.println("  " + found.length + " message(s) trouve(s) :");

            if (found.length == 0) {
                System.out.println("  --> Aucun message ne correspond.");
            } else {
                for (int i = 0; i < found.length; i++) {
                    boolean seen   = found[i].isSet(Flags.Flag.SEEN);
                    String  status = seen ? "[ LU  ]" : "[NONLU]";
                    System.out.printf("  [%d] %s  De: %-25s  Sujet: %s%n",
                            i + 1, status, getFrom(found[i]), found[i].getSubject());
                }
            }
        } catch (MessagingException e) {
            System.err.println("[ERREUR] Recherche IMAP impossible : " + e.getMessage());
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================
    private static String getFrom(Message msg) {
        try {
            Address[] froms = msg.getFrom();
            return (froms != null && froms.length > 0) ? froms[0].toString() : "Inconnu";
        } catch (Exception e) {
            return "Inconnu";
        }
    }

    private static int lireEntier(Scanner scanner) {
        try {
            String line = scanner.nextLine().trim();
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
