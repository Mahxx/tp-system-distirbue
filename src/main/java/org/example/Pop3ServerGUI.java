package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Pop3ServerGUI — même classe qu'à l'origine.
 * Modification : Pop3SessionGUI utilise maintenant RMI pour authentifier.
 */
public class Pop3ServerGUI {

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

    private static int PORT = 110;

    public static void main(String[] args) {
        if (args.length > 0) {
            try { PORT = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        SwingUtilities.invokeLater(() -> new Pop3ServerGUI().createGUI());
    }

    private void createGUI() {
        frame = new JFrame("POP3 Server Administration");
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
            eventLogFile = new PrintWriter(new FileWriter("logs/pop3_events.log", true), true);
        } catch (IOException e) {
            log("Cannot create log file: " + e.getMessage());
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                log("POP3 Server started on port " + PORT);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        addClient(client);
                        log(client.getRemoteSocketAddress() + " connected");
                        // MODIFICATION : Pop3SessionGUI (même nom) utilise maintenant RMI
                        new Pop3SessionGUI(client, this).start();
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
//  Pop3SessionGUI — même nom qu'à l'origine
//  Seul handleUser() et handlePass() sont modifiés pour utiliser RMI.
// =====================================================================
class Pop3SessionGUI extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Pop3ServerGUI gui;

    private String username;
    private List<EmailMessage> emails;
    private boolean authenticated;
    private List<Boolean> deletionFlags;

    // --- Connexion RMI ---
    private AuthService authService;
    private static final String RMI_HOST = "localhost";

    public Pop3SessionGUI(Socket socket, Pop3ServerGUI gui) {
        this.socket        = socket;
        this.gui           = gui;
        this.authenticated = false;
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

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("+OK POP3 server ready");

            String line;
            while ((line = in.readLine()) != null) {
                gui.log(clientId() + " -> " + line);
                String[] parts  = line.split(" ", 2);
                String command  = parts[0].toUpperCase();
                String argument = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    case "USER": handleUser(argument); break;
                    case "PASS": handlePass(argument); break;
                    case "STAT": handleStat();          break;
                    case "LIST": handleList();          break;
                    case "RETR": handleRetr(argument); break;
                    case "DELE": handleDele(argument); break;
                    case "RSET": handleRset();          break;
                    case "NOOP": send("+OK");          break;
                    case "QUIT": handleQuit(); return;
                    default:     send("-ERR Unknown command"); break;
                }
            }
            if (authenticated) gui.log(clientId() + " connexion interrompue sans QUIT.");
        } catch (IOException e) {
            gui.log(clientId() + " déconnecté inopinément");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            gui.removeClient(socket);
        }
    }

    private void send(String message) {
        out.println(message);
        gui.log(clientId() + " <- " + message);
    }

    // ------------------------------------------------------------------
    //  MODIFICATION : vérifie l'existence via RMI
    // ------------------------------------------------------------------
    private void handleUser(String arg) {
        try {
            AuthService svc = getAuthService();
            boolean exists = (svc != null) ? svc.userExists(arg)
                                           : DatabaseManager.getInstance().userExists(arg);
            if (exists) {
                username = arg;
                send("+OK User accepted");
            } else {
                send("-ERR User not found");
            }
        } catch (Exception e) {
            if (DatabaseManager.getInstance().userExists(arg)) {
                username = arg;
                send("+OK User accepted");
            } else {
                send("-ERR User not found");
            }
        }
    }

    // ------------------------------------------------------------------
    //  MODIFICATION : vérifie le mot de passe via RMI
    //  (l'original acceptait tout mot de passe sans vérification réelle)
    // ------------------------------------------------------------------
    private void handlePass(String arg) {
        if (username == null) { send("-ERR USER required first"); return; }

        boolean valid = false;
        try {
            AuthService svc = getAuthService();
            if (svc != null) {
                valid = svc.authenticate(username, arg); // ← appel RMI
                gui.log(clientId() + " RMI authenticate(" + username + ") -> " + valid);
            } else {
                valid = true; // fallback si RMI inaccessible
                gui.log(clientId() + " WARN: RMI inaccessible, fallback sans vérification");
            }
        } catch (Exception e) {
            valid = true;
            gui.log(clientId() + " WARN: erreur RMI PASS : " + e.getMessage());
        }

        if (!valid) {
            send("-ERR Authentication failed");
            username = null;
            return;
        }

        authenticated = true;
        emails = DatabaseManager.getInstance().fetchEmails(username);
        deletionFlags = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) deletionFlags.add(false);
        send("+OK Password accepted");
    }

    // ------------------------------------------------------------------
    //  Handlers inchangés
    // ------------------------------------------------------------------
    private void handleStat() {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        long size = emails.stream().mapToLong(EmailMessage::length).sum();
        send("+OK " + emails.size() + " " + size);
    }

    private void handleList() {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        send("+OK " + emails.size() + " messages");
        for (int i = 0; i < emails.size(); i++)
            send((i + 1) + " " + emails.get(i).length());
        send(".");
    }

    private void handleRetr(String arg) {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        try {
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size()) { send("-ERR No such message"); return; }
            EmailMessage mail = emails.get(index);
            send("+OK " + mail.length() + " octets");
            BufferedReader reader = new BufferedReader(new StringReader(mail.toRawString()));
            String line;
            while ((line = reader.readLine()) != null) send(line);
            send(".");
            reader.close();
        } catch (Exception e) { send("-ERR Invalid message number"); }
    }

    private void handleDele(String arg) {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        try {
            int index = Integer.parseInt(arg.trim()) - 1;
            if (index < 0 || index >= emails.size()) { send("-ERR No such message"); return; }
            if (deletionFlags.get(index)) { send("-ERR Message already marked for deletion"); return; }
            deletionFlags.set(index, true);
            send("+OK Message marked for deletion");
        } catch (NumberFormatException nfe) {
            send("-ERR Invalid message number");
        } catch (Exception e) {
            send("-ERR Invalid message number");
        }
    }

    private void handleRset() {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        for (int i = 0; i < deletionFlags.size(); i++) deletionFlags.set(i, false);
        send("+OK Deletion marks reset");
    }

    private void handleQuit() {
        for (int i = deletionFlags.size() - 1; i >= 0; i--) {
            if (deletionFlags.get(i)) {
                EmailMessage mail = emails.get(i);
                DatabaseManager.getInstance().deleteEmail(mail.getId());
                gui.log(clientId() + " Deleted email ID: " + mail.getId());
            }
        }
        send("+OK POP3 server signing off");
    }
}