package org.example;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * ImapServerGUI — même classe qu'à l'origine.
 * MODIFICATION : ImapSessionGUI.cmdLOGIN() vérifie username + password via RMI.
 */
public class ImapServerGUI {

    private JFrame frame;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel clientLabel;

    private ServerSocket serverSocket;
    private boolean running = false;
    private int clientCount = 0;
    private List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    private PrintWriter eventLogFile;

    private static int PORT = 1430;

    public static void main(String[] args) {
        if (args.length > 0) {
            try { PORT = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        SwingUtilities.invokeLater(() -> new ImapServerGUI().createGUI());
    }

    private void createGUI() {
        frame = new JFrame("IMAP Server Administration");
        frame.setSize(750, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        startButton = new JButton("Start Server");
        stopButton  = new JButton("Stop Server");
        stopButton.setEnabled(false);
        clientLabel = new JLabel("Connected clients: 0");

        topPanel.add(startButton);
        topPanel.add(stopButton);
        topPanel.add(clientLabel);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        frame.setVisible(true);
    }

    public void log(String message) {
        String time        = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String fullMessage = "[" + time + "] " + message;
        SwingUtilities.invokeLater(() -> {
            logArea.append(fullMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        if (eventLogFile != null) eventLogFile.println(fullMessage);
    }

    private void startServer() {
        if (running) return;
        running = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        try {
            File logDir = new File("logs");
            if (!logDir.exists()) logDir.mkdirs();
            eventLogFile = new PrintWriter(new FileWriter("logs/imap_events.log", true), true);
        } catch (IOException e) {
            log("Cannot create log file: " + e.getMessage());
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                log("IMAP Server started on port " + PORT);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        addClient(client);
                        log(client.getRemoteSocketAddress() + " connected");
                        new ImapSessionGUI(client, this).start();
                    } catch (SocketException e) {
                        if (!running) break;
                    }
                }
            } catch (IOException e) {
                log("Server error: " + e.getMessage());
            }
        }).start();
    }

    private void stopServer() {
        running = false;
        log("Stopping server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            synchronized (clients) {
                for (Socket client : clients) {
                    try { client.close(); } catch (IOException ignored) {}
                }
                clients.clear();
            }
            clientCount = 0;
            updateClients();
            log("All client connections closed");
            log("Server stopped");
            if (eventLogFile != null) eventLogFile.close();
        } catch (IOException e) {
            log("Error stopping server: " + e.getMessage());
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void updateClients() {
        SwingUtilities.invokeLater(() -> clientLabel.setText("Connected clients: " + clientCount));
    }

    public void addClient(Socket client) {
        clients.add(client);
        clientCount++;
        updateClients();
    }

    public void removeClient(Socket client) {
        clients.remove(client);
        clientCount--;
        if (clientCount < 0) clientCount = 0;
        updateClients();
    }

    public boolean isRunning() { return running; }
}

// =====================================================================
//  ImapSessionGUI — même nom qu'à l'origine
//  MODIFICATION : cmdLOGIN() vérifie username + password via RMI
// =====================================================================
class ImapSessionGUI extends Thread {

    enum State { NON_AUTHENTICATED, AUTHENTICATED, SELECTED, LOGOUT }

    private State state = State.NON_AUTHENTICATED;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ImapServerGUI gui;

    private String username;
    private List<EmailMessage> messages = new ArrayList<>();
    private Map<Integer, Boolean> seenFlags = new HashMap<>();

    // --- Connexion RMI ---
    private AuthService authService;
    private static final String RMI_HOST = "localhost";

    public ImapSessionGUI(Socket socket, ImapServerGUI gui) {
        this.socket = socket;
        this.gui    = gui;
    }

    /** Connexion lazy au registre RMI */
    private AuthService getAuthService() {
        if (authService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(RMI_HOST, AuthServer.RMI_PORT);
                authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);
                gui.log(clientId() + " Connecté au service RMI");
            } catch (Exception e) {
                gui.log(clientId() + " WARN: RMI inaccessible - " + e.getMessage());
            }
        }
        return authService;
    }

    private String clientId() {
        return socket.getRemoteSocketAddress().toString();
    }

    private void send(String msg) {
        gui.log("Server -> " + msg);
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("* OK IMAP4rev2 Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                gui.log(clientId() + " -> " + line);

                String[] parts = line.split(" ", 3);
                String tag     = parts[0];
                String command = parts.length > 1 ? parts[1].toUpperCase() : "";
                String args    = parts.length > 2 ? parts[2] : "";

                switch (command) {
                    case "LOGIN":  cmdLOGIN(tag, args);  break;
                    case "SELECT": 
                    case "EXAMINE": cmdSELECT(tag, args); break;
                    case "FETCH":  cmdFETCH(tag, args);  break;
                    case "STORE":  cmdSTORE(tag, args);  break;
                    case "SEARCH": cmdSEARCH(tag, args); break;
                    case "CAPABILITY": cmdCAPABILITY(tag); break;
                    case "NOOP":   send(tag + " OK NOOP completed"); break;
                    case "LOGOUT": cmdLOGOUT(tag); return;
                    default:       send(tag + " BAD Unknown command"); break;
                }
            }
        } catch (Exception e) {
            gui.log(clientId() + " session closed unexpectedly");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            gui.removeClient(socket);
        }
    }

    // ================================================================
    //  LOGIN — MODIFICATION : vérifie username + password via RMI
    //  Original : vérifiait seulement si le dossier mailserver/ existait,
    //             sans jamais vérifier le mot de passe.
    // ================================================================
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

        // *** Vérification via RMI ***
        boolean valid = false;
        try {
            AuthService svc = getAuthService();
            if (svc != null) {
                valid = svc.authenticate(user, pass); // ← appel RMI
                gui.log(clientId() + " RMI authenticate(" + user + ") -> " + valid);
            } else {
                // RMI inaccessible : fallback sur la BDD
                gui.log(clientId() + " WARN: RMI inaccessible, fallback DB.");
                valid = DatabaseManager.getInstance().authenticateUser(user.toLowerCase(), AuthServiceImpl.hash(pass));
            }
        } catch (Exception e) {
            gui.log(clientId() + " WARN: erreur RMI LOGIN : " + e.getMessage());
            valid = DatabaseManager.getInstance().authenticateUser(user.toLowerCase(), AuthServiceImpl.hash(pass)); // fallback
        }

        if (!valid) {
            send(tag + " NO Authentication failed");
            return;
        }

        username   = user;
        state      = State.AUTHENTICATED;
        send(tag + " OK LOGIN completed");
    }

    // ================================================================
    //  SELECT — inchangé
    // ================================================================
    private void cmdSELECT(String tag, String mailbox) {
        if (state != State.AUTHENTICATED && state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }
        if (!mailbox.equalsIgnoreCase("INBOX")) { send(tag + " NO Mailbox does not exist"); return; }

        messages = DatabaseManager.getInstance().fetchEmails(username);
        seenFlags.clear();
        for (int i = 0; i < messages.size(); i++) seenFlags.put(i + 1, messages.get(i).isRead());

        send("* " + messages.size() + " EXISTS");
        send("* FLAGS (\\Seen)");
        state = State.SELECTED;
        send(tag + " OK [READ-WRITE] SELECT completed");
    }

    // ================================================================
    //  FETCH — supporte les plages de séquence (1:7, 1:*, etc.)
    // ================================================================
    private void cmdFETCH(String tag, String args) throws Exception {
        if (state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }

        String[] p = args.split(" ", 2);
        String rangeStr  = p[0];
        String fetchItems = p.length > 1 ? p[1].toUpperCase() : "";

        List<Integer> ids = parseRange(rangeStr);
        if (ids.isEmpty()) { send(tag + " NO Message not found"); return; }

        for (int id : ids) {
            if (id < 1 || id > messages.size()) continue;
            EmailMessage mail = messages.get(id - 1);

            if (fetchItems.contains("FLAGS") && !fetchItems.contains("ENVELOPE") && !fetchItems.contains("RFC822.SIZE")) {
                boolean seen = seenFlags.getOrDefault(id, false);
                send("* " + id + " FETCH (FLAGS " + (seen ? "(\\Seen)" : "()") + ")");
            }
            if (fetchItems.contains("ENVELOPE") || fetchItems.contains("RFC822.SIZE") || fetchItems.contains("INTERNALDATE")) {
                String subject = mail.getSubject()   != null ? mail.getSubject()   : "";
                String sender  = mail.getSender()    != null ? mail.getSender()    : "";
                String recip   = mail.getRecipient() != null ? mail.getRecipient() : "";
                int    size    = mail.toRawString().getBytes().length;
                // Date au format IMAP : dd-MMM-yyyy HH:mm:ss +0000
                String internalDate = "01-Jan-1970 00:00:00 +0000";
                if (mail.getDate() != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm:ss Z", java.util.Locale.US);
                    internalDate = sdf.format(mail.getDate());
                }
                String envelope = "(\"" + internalDate + "\" \"" + subject + "\""
                    + " ((\"\" NIL \"" + sender + "\" \"localhost\"))"
                    + " ((\"\" NIL \"" + sender + "\" \"localhost\"))"
                    + " ((\"\" NIL \"" + sender + "\" \"localhost\"))"
                    + " ((\"\" NIL \"" + recip  + "\" \"localhost\"))"
                    + " NIL NIL NIL NIL)";
                boolean seen = seenFlags.getOrDefault(id, false);
                send("* " + id + " FETCH (ENVELOPE " + envelope
                    + " INTERNALDATE \"" + internalDate + "\""
                    + " RFC822.SIZE " + size
                    + " FLAGS " + (seen ? "(\\Seen)" : "()")
                    + ")");
            }
            if (fetchItems.contains("BODYSTRUCTURE")) {
                int length = mail.toRawString().getBytes().length;
                send("* " + id + " FETCH (BODYSTRUCTURE (\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"7bit\" " + length + " 0))");
            }
            if (fetchItems.contains("BODY[") || fetchItems.contains("BODY.PEEK[")) {
                String raw = mail.toRawString();
                send("* " + id + " FETCH (BODY[] {" + raw.getBytes().length + "}");
                BufferedReader reader = new BufferedReader(new java.io.StringReader(raw));
                String line;
                while ((line = reader.readLine()) != null) send(line);
                send(")");
                if (!fetchItems.contains("PEEK")) {
                    seenFlags.put(id, true);
                    DatabaseManager.getInstance().updateReadStatus(mail.getId(), true);
                }
            }
        }

        send(tag + " OK FETCH completed");
    }

    /** Parse une plage IMAP : "3" -> [3], "1:5" -> [1,2,3,4,5], "1:*" -> tous */
    private java.util.List<Integer> parseRange(String range) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        try {
            if (range.contains(":")) {
                String[] parts = range.split(":");
                int start = Integer.parseInt(parts[0].trim());
                int end   = parts[1].trim().equals("*") ? messages.size() : Integer.parseInt(parts[1].trim());
                for (int i = start; i <= end; i++) ids.add(i);
            } else {
                ids.add(Integer.parseInt(range.trim()));
            }
        } catch (NumberFormatException ignored) {}
        return ids;
    }

    // ================================================================
    //  STORE — inchangé
    // ================================================================
    private void cmdSTORE(String tag, String args) {
        if (state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }
        String[] p = args.split(" ");
        int id = Integer.parseInt(p[0]);
        if (id >= 1 && id <= messages.size()) {
            seenFlags.put(id, true);
            DatabaseManager.getInstance().updateReadStatus(messages.get(id - 1).getId(), true);
        }
        send("* " + id + " FETCH (FLAGS (\\Seen))");
        send(tag + " OK STORE completed");
    }

    // ================================================================
    //  SEARCH — inchangé
    // ================================================================
    private void cmdSEARCH(String tag, String args) throws Exception {
        if (state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }
        String keyword = args.replace("SUBJECT", "").replace("\"", "").trim();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            String content = messages.get(i).toRawString();
            if (content.contains(keyword)) result.append(i + 1).append(" ");
        }
        send("* SEARCH " + result.toString().trim());
        send(tag + " OK SEARCH completed");
    }

    // ================================================================
    //  LOGOUT — inchangé
    // ================================================================
    private void cmdLOGOUT(String tag) {
        send("* BYE IMAP4rev2 Server logging out");
        send(tag + " OK LOGOUT completed");
        state = State.LOGOUT;
    }

    private void cmdCAPABILITY(String tag) {
        send("* CAPABILITY IMAP4rev1");
        send(tag + " OK CAPABILITY completed");
    }
}