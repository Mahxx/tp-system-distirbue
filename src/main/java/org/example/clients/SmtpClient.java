package org.example.clients;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.Scanner;

/**
 * Client SMTP (Section 7) — utilise Jakarta Mail API.
 *
 * Serveur : localhost:2525 (port personnalise du SmtpServer maison)
 * Authentification plain login/password, pas de TLS (conformement aux exigences du TP).
 *
 * Fonctionnalites :
 *   - Authentification utilisateur
 *   - Envoi de message vers un destinataire local
 *   - Affichage des en-tetes envoyes
 *   - Gestion des erreurs d'authentification et de connexion
 */
public class SmtpClient {

    // ----- Parametres configurable -----
    private static final String SMTP_HOST = "localhost";
    private static final int    SMTP_PORT = 25;  // port du SmtpServerGUI (port 25)

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=======================================================");
        System.out.println("       CLIENT SMTP — Envoi d'email (JavaMail API)");
        System.out.println("=======================================================");
        System.out.println("  Serveur : " + SMTP_HOST + ":" + SMTP_PORT);
        System.out.println("=======================================================");
        System.out.println();

        // --- 1. Saisie de l'expediteur ---
        System.out.print("Votre nom d'utilisateur (ex: karim) : ");
        String username = scanner.nextLine().trim();

        System.out.print("Votre mot de passe : ");
        String password = scanner.nextLine().trim();

        // --- 2. Saisie du destinataire ---
        System.out.print("Destinataire (ex: karimoo) : ");
        String toRaw = scanner.nextLine().trim();

        System.out.print("Sujet du message : ");
        String subject = scanner.nextLine().trim();

        System.out.print("Corps du message : ");
        String body = scanner.nextLine().trim();

        // Construction des adresses completes
        String fromAddr = toFullEmail(username);
        String toAddr   = toFullEmail(toRaw);

        System.out.println();
        System.out.println("--- En-tetes qui seront envoyes ---");
        System.out.println("  From    : " + fromAddr);
        System.out.println("  To      : " + toAddr);
        System.out.println("  Subject : " + subject);
        System.out.println("-----------------------------------");

        // --- 3. Configuration JavaMail ---
        Properties props = new Properties();
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              String.valueOf(SMTP_PORT));
        props.put("mail.smtp.auth",              "true");
        props.put("mail.smtp.starttls.enable",   "false");  // pas de TLS (TP)
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout",           "5000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        // Decommenter la ligne suivante pour voir le dialogue SMTP complet :
        // session.setDebug(true);

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddr));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddr));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");

            System.out.println("\n... Connexion et envoi en cours ...");
            Transport.send(message);

            System.out.println("\n[SUCCES] Message envoye avec succes !");
            System.out.println("  De      : " + fromAddr);
            System.out.println("  Vers    : " + toAddr);
            System.out.println("  Sujet   : " + subject);

        } catch (AuthenticationFailedException e) {
            System.err.println("\n[ERREUR AUTH] Identifiants incorrects pour l'utilisateur '" + username + "'.");
            System.err.println("  -> Verifiez votre nom d'utilisateur et mot de passe.");
        } catch (MessagingException e) {
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (cause.contains("Connection refused") || cause.contains("connect")) {
                System.err.println("\n[ERREUR CONNEXION] Impossible de joindre le serveur SMTP sur " + SMTP_HOST + ":" + SMTP_PORT);
                System.err.println("  -> Verifiez que le serveur SMTP est demarre ET que vous avez clique 'Start Server' dans sa fenetre GUI.");
            } else if (cause.contains("550")) {
                System.err.println("\n[ERREUR] Destinataire rejete par le serveur : " + toAddr);
                System.err.println("  -> Verifiez que l'utilisateur destinataire existe.");
            } else {
                System.err.println("\n[ERREUR SMTP] " + cause);
            }
        }
    }

    /** Ajoute @example.com si le nom ne contient pas deja un @. */
    private static String toFullEmail(String input) {
        return input.contains("@") ? input : input + "@example.com";
    }
}
