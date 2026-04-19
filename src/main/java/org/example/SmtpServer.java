package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

/**
 * SmtpServer - recoit les emails et les stocke en base de donnees MySQL.
 * handleMailFrom() verifie l'expediteur via RMI avant d'accepter.
 * handleRcptTo() verifie le destinataire en base avant d'accepter.
 */
public class SmtpServer {

    private static final int PORT = 2525;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                new SmtpSession(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SmtpSession extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private enum SmtpState {
        CONNECTED,
        HELO_RECEIVED,
        MAIL_FROM_SET,
        RCPT_TO_SET,
        DATA_RECEIVING
    }

    private SmtpState state;
    private String sender;
    private List<String> recipients;
    private StringBuilder dataBuffer;

    // Connexion RMI
    private AuthService authService;
    private static final String RMI_HOST = "localhost";

    public SmtpSession(Socket socket) {
        this.socket     = socket;
        this.state      = SmtpState.CONNECTED;
        this.recipients = new ArrayList<>();
        this.dataBuffer = new StringBuilder();
    }

    private AuthService getAuthService() {
        if (authService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(RMI_HOST, AuthServer.RMI_PORT);
                authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);
            } catch (Exception e) {
                System.err.println("[SmtpSession] RMI inaccessible : " + e.getMessage());
            }
        }
        return authService;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("220 smtp.example.com Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line);

                if (state == SmtpState.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0);
                        state = SmtpState.HELO_RECEIVED;
                        out.println("250 OK: Message accepted for delivery");
                    } else {
                        dataBuffer.append(line).append("\r\n");
                    }
                    continue;
                }

                String command  = extractToken(line).toUpperCase();
                String argument = extractArgument(line);

                switch (command) {
                    case "HELO":
                    case "EHLO": handleHelo(argument);     break;
                    case "MAIL": handleMailFrom(argument); break;
                    case "RCPT": handleRcptTo(argument);   break;
                    case "DATA": handleData();              break;
                    case "QUIT": handleQuit(); return;
                    default:     out.println("500 Command unrecognized"); break;
                }
            }
            if (state == SmtpState.DATA_RECEIVING) {
                System.err.println("Connexion interrompue pendant DATA. Email non stocke.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    private void handleHelo(String arg) {
        state = SmtpState.HELO_RECEIVED;
        sender = "";
        recipients.clear();
        out.println("250 Hello " + arg);
    }

    private void handleMailFrom(String arg) {
        if (!arg.toUpperCase().matches("^FROM:\\s*<[^>]+>$")) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        String potentialEmail = arg.substring(5).trim();
        potentialEmail = potentialEmail.substring(1, potentialEmail.length() - 1).trim();
        String email = extractEmail(potentialEmail);

        if (email == null) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        // Verification RMI : l'expediteur doit etre un utilisateur connu
        String username = email.split("@")[0];
        try {
            AuthService svc = getAuthService();
            if (svc != null) {
                boolean exists = svc.userExists(username);
                System.out.println("[SMTP] RMI userExists(" + username + ") -> " + exists);
                if (!exists) {
                    out.println("550 Sender rejected: unknown user '" + username + "'");
                    return;
                }
            } else {
                System.err.println("[SMTP] RMI inaccessible, expediteur accepte sans verification.");
            }
        } catch (Exception e) {
            System.err.println("[SMTP] Erreur RMI MAIL FROM : " + e.getMessage());
        }

        sender = username;
        state  = SmtpState.MAIL_FROM_SET;
        out.println("250 OK");
    }

    private void handleRcptTo(String arg) {
        if (state != SmtpState.MAIL_FROM_SET && state != SmtpState.RCPT_TO_SET) {
            out.println("503 Bad sequence of commands");
            return;
        }
        if (!arg.toUpperCase().startsWith("TO:")) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }
        String potentialEmail = arg.substring(3).trim();
        String email = extractEmail(potentialEmail);
        if (email == null) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        // Verification que le destinataire existe en base
        String username = email.split("@")[0];
        try {
            AuthService svc = getAuthService();
            boolean exists = (svc != null) ? svc.userExists(username)
                                           : DatabaseManager.getInstance().userExists(username);
            if (!exists) {
                out.println("550 Recipient rejected: unknown user '" + username + "'");
                return;
            }
        } catch (Exception e) {
            System.err.println("[SMTP] Erreur verification destinataire : " + e.getMessage());
        }

        recipients.add(username);
        state = SmtpState.RCPT_TO_SET;
        out.println("250 OK");
    }

    private void handleData() {
        if (state != SmtpState.RCPT_TO_SET || recipients.isEmpty()) {
            out.println("503 Bad sequence of commands");
            return;
        }
        state = SmtpState.DATA_RECEIVING;
        out.println("354 Start mail input; end with <CRLF>.<CRLF>");
    }

    private void handleQuit() {
        out.println("221 smtp.example.com Service closing transmission channel");
    }

    private String extractToken(String line) {
        String[] parts = line.split(" ");
        return parts.length > 0 ? parts[0] : "";
    }

    private String extractArgument(String line) {
        int index = line.indexOf(' ');
        return index > 0 ? line.substring(index).trim() : "";
    }

    private String extractEmail(String input) {
        input = input.replaceAll("[<>]", "");
        if (input.contains("@") && input.indexOf("@") > 0 && input.indexOf("@") < input.length() - 1) {
            return input;
        }
        return null;
    }

    private void storeEmail(String data) {
        for (String recipient : recipients) {
            DatabaseManager.getInstance().storeEmail(sender, recipient, "Email", data);
            System.out.println("Stored email for " + recipient + " in Database");
        }
    }
}