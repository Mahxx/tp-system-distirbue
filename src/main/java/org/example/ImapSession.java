package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ImapSession - gere une session IMAP4rev2 complète.
 * Authentification via RMI, emails recuperes depuis MySQL.
 */
class ImapSession extends Thread {

    enum State {
        NON_AUTHENTICATED,
        AUTHENTICATED,
        SELECTED,
        LOGOUT
    }

    private State state = State.NON_AUTHENTICATED;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private List<EmailMessage> messages = new ArrayList<>();
    private Map<Integer, Boolean> seenFlags = new HashMap<>();

    // Connexion RMI
    private AuthService authService;
    private static final String RMI_HOST = "localhost";

    public ImapSession(Socket socket) {
        this.socket = socket;
    }

    private AuthService getAuthService() {
        if (authService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(RMI_HOST, AuthServer.RMI_PORT);
                authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);
            } catch (Exception e) {
                System.err.println("[ImapSession] RMI inaccessible : " + e.getMessage());
            }
        }
        return authService;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("* OK IMAP4rev2 Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("C: " + line);

                String[] parts  = line.split(" ", 3);
                String tag      = parts[0];
                String command  = parts.length > 1 ? parts[1].toUpperCase() : "";
                String args     = parts.length > 2 ? parts[2] : "";

                switch (command) {
                    case "LOGIN":  cmdLOGIN(tag, args);  break;
                    case "SELECT": cmdSELECT(tag, args); break;
                    case "FETCH":  cmdFETCH(tag, args);  break;
                    case "STORE":  cmdSTORE(tag, args);  break;
                    case "SEARCH": cmdSEARCH(tag, args); break;
                    case "CAPABILITY": cmdCAPABILITY(tag); break;
                    case "LOGOUT": cmdLOGOUT(tag); return;
                    default:       send(tag + " BAD Unknown command"); break;
                }
            }
        } catch (Exception e) {
            System.out.println("Session IMAP closed.");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void cmdLOGIN(String tag, String args) {
        if (state != State.NON_AUTHENTICATED) {
            send(tag + " BAD Already authenticated");
            return;
        }

        String[] p = args.split(" ");
        if (p.length < 2) {
            send(tag + " BAD Invalid arguments");
            return;
        }

        String user = p[0];
        String pass = p[1];

        boolean valid = false;
        try {
            AuthService svc = getAuthService();
            if (svc != null) {
                valid = svc.authenticate(user, pass);
                System.out.println("[IMAP] authenticate(" + user + ") -> " + valid);
            } else {
                // Fallback direct DB si RMI inaccessible
                System.err.println("[IMAP] RMI inaccessible, fallback DB.");
                valid = DatabaseManager.getInstance().authenticateUser(
                    user.toLowerCase(), AuthServiceImpl.hash(pass));
            }
        } catch (Exception e) {
            System.err.println("[IMAP] Erreur LOGIN : " + e.getMessage());
        }

        if (!valid) {
            send(tag + " NO Authentication failed");
            return;
        }

        username = user;
        state    = State.AUTHENTICATED;
        send(tag + " OK LOGIN completed");
    }

    private void cmdSELECT(String tag, String mailbox) {
        if (state != State.AUTHENTICATED) {
            send(tag + " BAD Command not allowed now");
            return;
        }
        if (!mailbox.equalsIgnoreCase("INBOX")) {
            send(tag + " NO Mailbox does not exist");
            return;
        }

        messages = DatabaseManager.getInstance().fetchEmails(username);
        seenFlags.clear();
        for (int i = 0; i < messages.size(); i++) seenFlags.put(i + 1, messages.get(i).isRead());

        send("* " + messages.size() + " EXISTS");
        send("* FLAGS (\\Seen)");
        state = State.SELECTED;
        send(tag + " OK [READ-WRITE] SELECT completed");
    }

    private void cmdFETCH(String tag, String args) {
        if (state != State.SELECTED) {
            send(tag + " BAD Command not allowed now");
            return;
        }

        try {
            String[] p = args.split(" ");
            int id = Integer.parseInt(p[0]);

            if (id < 1 || id > messages.size()) {
                send(tag + " NO Message not found");
                return;
            }

            EmailMessage mail = messages.get(id - 1);

            if (args.toUpperCase().contains("FLAGS")) {
                boolean seen = seenFlags.getOrDefault(id, false);
                send("* " + id + " FETCH (FLAGS " + (seen ? "(\\Seen)" : "()") + ")");
            }
            if (args.toUpperCase().contains("BODYSTRUCTURE")) {
                 int length = mail.toRawString().getBytes().length;
                 send("* " + id + " FETCH (BODYSTRUCTURE (\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"7bit\" " + length + " 0))");
            }
            if (args.toUpperCase().contains("BODY[") || args.toUpperCase().contains("BODY.PEEK[") || args.toUpperCase().contains("RFC822")) {
                String raw = mail.toRawString();
                send("* " + id + " FETCH (BODY[] {" + raw.getBytes().length + "}");
                BufferedReader reader = new BufferedReader(new StringReader(raw));
                String line;
                while ((line = reader.readLine()) != null) send(line);
                send(")");
                if (!args.toUpperCase().contains("PEEK")) {
                    seenFlags.put(id, true);
                    DatabaseManager.getInstance().updateReadStatus(mail.getId(), true);
                }
            }


            send(tag + " OK FETCH completed");
        } catch (Exception e) {
            send(tag + " BAD Error: " + e.getMessage());
        }
    }

    private void cmdSTORE(String tag, String args) {
        if (state != State.SELECTED) {
            send(tag + " BAD Command not allowed now");
            return;
        }
        try {
            String[] p = args.split(" ");
            int id = Integer.parseInt(p[0]);
            if (id >= 1 && id <= messages.size()) {
                seenFlags.put(id, true);
                DatabaseManager.getInstance().updateReadStatus(messages.get(id - 1).getId(), true);
            }
            send("* " + id + " FETCH (FLAGS (\\Seen))");
            send(tag + " OK STORE completed");
        } catch (Exception e) {
            send(tag + " BAD Error: " + e.getMessage());
        }
    }

    private void cmdSEARCH(String tag, String args) {
        if (state != State.SELECTED) {
            send(tag + " BAD Command not allowed now");
            return;
        }
        String keyword = args.replace("SUBJECT", "").replace("\"", "").trim();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            String content = messages.get(i).toRawString();
            if (content.contains(keyword)) result.append(i + 1).append(" ");
        }
        send("* SEARCH " + result.toString().trim());
        send(tag + " OK SEARCH completed");
    }

    private void cmdLOGOUT(String tag) {
        send("* BYE IMAP4rev2 Server logging out");
        send(tag + " OK LOGOUT completed");
        state = State.LOGOUT;
    }

    private void cmdCAPABILITY(String tag) {
        // Envoie de la capacité basique au lieu de AUTH=PLAIN, forçant JavaMail à utiliser la commande LOGIN normale.
        send("* CAPABILITY IMAP4rev1");
        send(tag + " OK CAPABILITY completed");
    }

    private void send(String msg) {
        System.out.println("S: " + msg);
        out.println(msg);
    }
}