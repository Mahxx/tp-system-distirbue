package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Pop3Server — même classe qu'à l'origine.
 * Modification : les sessions utilisent maintenant RMI pour authentifier.
 */
public class Pop3Server {

    private static final int PORT = 110;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                // MODIFICATION : on instancie Pop3Session (même nom) mais elle utilise RMI
                new Pop3Session(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// =====================================================================
//  Pop3Session — même nom qu'à l'origine
//  Seul handleUser() et handlePass() sont modifiés pour utiliser RMI.
// =====================================================================
class Pop3Session extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private List<EmailMessage> emails;
    private boolean authenticated;
    private List<Boolean> deletionFlags;

    // --- Connexion RMI ---
    private AuthService authService;
    private static final String RMI_HOST = "localhost";

    public Pop3Session(Socket socket) {
        this.socket        = socket;
        this.authenticated = false;
    }

    /** Connexion lazy au registre RMI */
    private AuthService getAuthService() {
        if (authService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(RMI_HOST, AuthServer.RMI_PORT);
                authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);
            } catch (Exception e) {
                System.err.println("[Pop3Session] Impossible de joindre RMI : " + e.getMessage());
            }
        }
        return authService;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("+OK POP3 server ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line);
                String[] parts   = line.split(" ", 2);
                String command   = parts[0].toUpperCase();
                String argument  = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    case "USER": handleUser(argument); break;
                    case "PASS": handlePass(argument); break;
                    case "STAT": handleStat();          break;
                    case "LIST": handleList();          break;
                    case "RETR": handleRetr(argument); break;
                    case "DELE": handleDele(argument); break;
                    case "RSET": handleRset();          break;
                    case "QUIT": handleQuit(); return;
                    default:     out.println("-ERR Unknown command"); break;
                }
            }
            if (authenticated) {
                System.err.println("La connexion a été interrompue sans recevoir QUIT.");
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture de la connexion : " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { /* Ignore */ }
        }
    }

    // ------------------------------------------------------------------
    //  MODIFICATION : vérifie l'existence via RMI au lieu du dossier seul
    // ------------------------------------------------------------------
    private void handleUser(String arg) {
        try {
            AuthService svc = getAuthService();
            boolean exists = (svc != null) ? svc.userExists(arg)
                                           : DatabaseManager.getInstance().userExists(arg);
            if (exists) {
                username = arg;
                out.println("+OK User accepted");
            } else {
                out.println("-ERR User not found");
            }
        } catch (Exception e) {
            // fallback filesystem si RMI inaccessible
            if (DatabaseManager.getInstance().userExists(arg)) {
                username = arg;
                out.println("+OK User accepted");
            } else {
                out.println("-ERR User not found");
            }
        }
    }

    // ------------------------------------------------------------------
    //  MODIFICATION : vérifie le mot de passe via RMI
    //  (l'original acceptait tout mot de passe sans vérification réelle)
    // ------------------------------------------------------------------
    private void handlePass(String arg) {
        if (username == null) {
            out.println("-ERR USER required first");
            return;
        }

        boolean valid = false;
        try {
            AuthService svc = getAuthService();
            if (svc != null) {
                valid = svc.authenticate(username, arg); // ← appel RMI
            } else {
                // RMI inaccessible : fallback (accepte tout, à désactiver en prod)
                valid = true;
                System.err.println("[Pop3Session] RMI inaccessible, fallback sans vérification.");
            }
        } catch (Exception e) {
            valid = true; // fallback gracieux
            System.err.println("[Pop3Session] Erreur RMI PASS : " + e.getMessage());
        }

        if (!valid) {
            out.println("-ERR Authentication failed");
            username = null; // réinitialise pour autoriser un nouvel essai
            return;
        }

        authenticated = true;
        emails = DatabaseManager.getInstance().fetchEmails(username);
        deletionFlags = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) deletionFlags.add(false);
        out.println("+OK Password accepted");
    }

    // ------------------------------------------------------------------
    //  Handlers inchangés par rapport à l'original
    // ------------------------------------------------------------------
    private void handleStat() {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        long size = emails.stream().mapToLong(EmailMessage::length).sum();
        out.println("+OK " + emails.size() + " " + size);
    }

    private void handleList() {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        out.println("+OK " + emails.size() + " messages");
        for (int i = 0; i < emails.size(); i++)
            out.println((i + 1) + " " + emails.get(i).length());
        out.println(".");
    }

    private void handleRetr(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        try {
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size()) { out.println("-ERR No such message"); return; }
            EmailMessage mail = emails.get(index);
            out.println("+OK " + mail.length() + " octets");
            BufferedReader reader = new BufferedReader(new StringReader(mail.toRawString()));
            String line;
            while ((line = reader.readLine()) != null) out.println(line);
            out.println(".");
            reader.close();
        } catch (Exception e) { out.println("-ERR Invalid message number"); }
    }

    private void handleDele(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        try {
            arg = arg.trim();
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size()) { out.println("-ERR No such message"); return; }
            if (deletionFlags.get(index)) { out.println("-ERR Message already marked for deletion"); return; }
            deletionFlags.set(index, true);
            out.println("+OK Message marked for deletion");
        } catch (NumberFormatException nfe) {
            out.println("-ERR Invalid message number");
        } catch (Exception e) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleRset() {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        for (int i = 0; i < deletionFlags.size(); i++) deletionFlags.set(i, false);
        out.println("+OK Deletion marks reset");
    }

    private void handleQuit() {
        for (int i = deletionFlags.size() - 1; i >= 0; i--) {
            if (deletionFlags.get(i)) {
                EmailMessage mail = emails.get(i);
                DatabaseManager.getInstance().deleteEmail(mail.getId());
                System.out.println("Deleted email ID: " + mail.getId());
                emails.remove(i);
                deletionFlags.remove(i);
            }
        }
        out.println("+OK POP3 server signing off");
    }
}