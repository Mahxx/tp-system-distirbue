package org.example.clients;

import javax.mail.*;
import java.util.Properties;
import java.util.Scanner;

/**
 * Client POP3 (Section 7) — utilise Jakarta Mail API.
 *
 * Serveur : localhost:110 (port standard POP3 du Pop3Server maison)
 * Authentification plain login/password, pas de TLS.
 */
public class Pop3Client {

    private static final String POP3_HOST = "localhost";
    private static final int    POP3_PORT = 110;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=======================================================");
        System.out.println("  CLIENT POP3 - Boite de reception (JavaMail API)");
        System.out.println("=======================================================");
        System.out.println("  Serveur : " + POP3_HOST + ":" + POP3_PORT);
        System.out.println("=======================================================");
        System.out.println();
        System.out.println("  Comptes valides : karim  karimoo  karin  admin");
        System.out.println();

        System.out.print("Votre nom d'utilisateur : ");
        String username = scanner.nextLine().trim();

        System.out.print("Votre mot de passe : ");
        String password = scanner.nextLine().trim();

        lireMails(username, password, scanner);
    }

    private static void lireMails(String username, String password, Scanner scanner) {

        Properties props = new Properties();
        props.put("mail.pop3.host",              POP3_HOST);
        props.put("mail.pop3.port",              String.valueOf(POP3_PORT));
        props.put("mail.pop3.starttls.enable",   "false");
        props.put("mail.pop3.disablecapa",       "true");
        props.put("mail.pop3.disabletop",        "true");
        props.put("mail.pop3.connectiontimeout", "5000");
        props.put("mail.pop3.timeout",           "5000");

        Store  store = null;
        Folder inbox = null;

        try {
            System.out.println("\n... Connexion au serveur POP3 " + POP3_HOST + ":" + POP3_PORT + " ...");

            Session session = Session.getInstance(props, null);
            store = session.getStore("pop3");
            store.connect(POP3_HOST, username, password);
            System.out.println("[OK] Connexion reussie en tant que '" + username + "'.\n");

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Verifier que le dossier est vraiment ouvert
            if (!inbox.isOpen()) {
                System.err.println("[ERREUR] Le serveur a refuse d'ouvrir la boite mail.");
                System.err.println("  -> L'utilisateur '" + username + "' n'existe peut-etre pas dans la base de donnees.");
                System.err.println("  -> Utilisez un compte valide : karim, karimoo, karin, admin");
                return;
            }

            Message[] messages = inbox.getMessages();
            int total = messages.length;

            System.out.println("=======================================================");
            System.out.println("  INBOX de '" + username + "' : " + total + " message(s)");
            System.out.println("=======================================================");

            if (total == 0) {
                System.out.println("  Boite vide. Envoyez d'abord un email avec le Client SMTP.");

            } else {
                // --- Liste des messages ---
                System.out.println();
                for (int i = 0; i < messages.length; i++) {
                    String from    = getFrom(messages[i]);
                    String subject = messages[i].getSubject();
                    System.out.printf("  [%d] De : %-30s  Sujet : %s%n", i + 1, from, subject);
                }

                // --- Menu interactif ---
                boolean running = true;
                while (running) {
                    System.out.println();
                    System.out.println("--- Actions ---");
                    System.out.println("  Entrez le numero d'un message pour le LIRE");
                    System.out.println("  Entrez 0 pour QUITTER");
                    System.out.print("Votre choix : ");

                    String input = scanner.nextLine().trim();
                    int choice;
                    try {
                        choice = Integer.parseInt(input);
                    } catch (NumberFormatException e) {
                        System.out.println("[!] Entree invalide.");
                        continue;
                    }

                    if (choice == 0) {
                        running = false;
                    } else if (choice >= 1 && choice <= messages.length) {
                        Message msg = messages[choice - 1];
                        afficherMessage(msg, choice);

                        System.out.print("\nVoulez-vous SUPPRIMER ce message ? (O/N) : ");
                        String rep = scanner.nextLine().trim();
                        if (rep.equalsIgnoreCase("O")) {
                            msg.setFlag(Flags.Flag.DELETED, true);
                            System.out.println("[SUCCES] Message marque pour suppression (effectif a la deconnexion).");
                        } else {
                            System.out.println("[INFO] Message conserve.");
                        }
                    } else {
                        System.out.println("[!] Numero invalide. Choisissez entre 1 et " + total + ".");
                    }
                }
            }

            inbox.close(true); // expunge = applique les suppressions
            inbox = null;
            store.close();
            store = null;
            System.out.println("\n[OK] Deconnexion POP3. Les suppressions ont ete appliquees.");

        } catch (AuthenticationFailedException e) {
            System.err.println("\n[ERREUR AUTH] Identifiants incorrects.");
            System.err.println("  -> Verifiez votre nom d'utilisateur et mot de passe.");
            System.err.println("  -> Comptes valides : karim/karim   karimoo/karim   karin/karin   admin/admin");
        } catch (MessagingException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("Connection refused") || msg.contains("connect")) {
                System.err.println("\n[ERREUR CONNEXION] Impossible de joindre le serveur POP3 sur " + POP3_HOST + ":" + POP3_PORT);
                System.err.println("  -> Le serveur Pop3ServerGUI est-il demarre ? Cliquez sur [Start Server] dans sa fenetre.");
            } else if (msg.contains("not open") || msg.contains("closed") || msg.contains("EOF")) {
                System.err.println("\n[ERREUR] Le serveur a ferme la connexion de facon inattendue.");
                System.err.println("  -> L'utilisateur '" + username + "' n'existe peut-etre pas dans la base de donnees MySQL.");
                System.err.println("  -> Utilisez un compte valide : karim, karimoo, karin, admin");
            } else {
                System.err.println("\n[ERREUR POP3] " + msg);
            }
        } catch (IllegalStateException e) {
            // "Folder not open" - le dossier n'a pas pu etre ouvert
            System.err.println("\n[ERREUR] La boite mail n'a pas pu etre ouverte.");
            System.err.println("  -> L'utilisateur '" + username + "' n'existe peut-etre pas dans la base de donnees MySQL.");
            System.err.println("  -> Utilisez un compte valide : karim, karimoo, karin, admin");
        } catch (Exception e) {
            System.err.println("\n[ERREUR] " + e.getClass().getSimpleName() + " : " + e.getMessage());
        } finally {
            try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) {}
            try { if (store != null && store.isConnected()) store.close();  } catch (Exception ignored) {}
        }
    }

    private static void afficherMessage(Message msg, int num) {
        try {
            System.out.println("\n============== MESSAGE " + num + " ==============");
            System.out.println("  Sujet   : " + msg.getSubject());
            System.out.println("  De      : " + getFrom(msg));
            System.out.println("  Date    : " + msg.getSentDate());
            System.out.println("  Taille  : " + msg.getSize() + " octets");
            System.out.println("----- Corps du message -----");
            Object content = msg.getContent();
            System.out.println(content != null ? content.toString().trim() : "[Pas de contenu texte]");
            System.out.println("====================================");
        } catch (Exception e) {
            System.err.println("[ERREUR] Impossible de lire ce message : " + e.getMessage());
        }
    }

    private static String getFrom(Message msg) {
        try {
            Address[] froms = msg.getFrom();
            return (froms != null && froms.length > 0) ? froms[0].toString() : "Inconnu";
        } catch (Exception e) {
            return "Inconnu";
        }
    }
}
