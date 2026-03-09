package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

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

    private static final int PORT = 1430;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ImapServerGUI().createGUI());
    }

    private void createGUI() {
        frame = new JFrame("IMAP Server Administration");
        frame.setSize(750, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
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
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
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

// ---------------------- IMAP SESSION ------------------------
class ImapSessionGUI extends Thread {

    enum State { NON_AUTHENTICATED, AUTHENTICATED, SELECTED, LOGOUT }

    private State state = State.NON_AUTHENTICATED;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ImapServerGUI gui;

    private String username;
    private File mailboxDir;
    private List<File> messages = new ArrayList<>();
    private Map<Integer, Boolean> seenFlags = new HashMap<>();

    public ImapSessionGUI(Socket socket, ImapServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
    }

    private String clientId() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("* OK IMAP4rev2 Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                gui.log(clientId() + " -> " + line);
                String[] parts = line.split(" ", 3);
                String tag = parts[0];
                String command = parts.length > 1 ? parts[1].toUpperCase() : "";
                String args = parts.length > 2 ? parts[2] : "";

                switch (command) {
                    case "LOGIN": cmdLOGIN(tag, args); break;
                    case "SELECT": cmdSELECT(tag, args); break;
                    case "FETCH": cmdFETCH(tag, args); break;
                    case "STORE": cmdSTORE(tag, args); break;
                    case "SEARCH": cmdSEARCH(tag, args); break;
                    case "LOGOUT": cmdLOGOUT(tag); return;
                    default: send(tag + " BAD Unknown command"); break;
                }
            }
        } catch (Exception e) {
            gui.log(clientId() + " session closed unexpectedly");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            gui.removeClient(socket);
        }
    }

    private void send(String msg) {
        gui.log("Server -> " + msg);
        out.println(msg);
    }

    private void cmdLOGIN(String tag, String args) {
        if (state != State.NON_AUTHENTICATED) { send(tag + " BAD Already authenticated"); return; }
        String[] p = args.split(" ");
        if (p.length < 2) { send(tag + " BAD Invalid arguments"); return; }
        username = p[0];
        File dir = new File("mailserver/" + username);
        if (!dir.exists() || !dir.isDirectory()) { send(tag + " NO Authentication failed"); return; }
        mailboxDir = dir;
        state = State.AUTHENTICATED;
        send(tag + " OK LOGIN completed");
    }

    private void cmdSELECT(String tag, String mailbox) {
        if (state != State.AUTHENTICATED) { send(tag + " BAD Command not allowed now"); return; }
        if (!mailbox.equalsIgnoreCase("INBOX")) { send(tag + " NO Mailbox does not exist"); return; }
        File[] files = mailboxDir.listFiles((d, name) -> name.endsWith(".txt"));
        messages = files == null ? new ArrayList<>() : Arrays.asList(files);
        seenFlags.clear();
        for (int i = 0; i < messages.size(); i++) seenFlags.put(i + 1, false);
        send("* " + messages.size() + " EXISTS");
        send("* FLAGS (\\Seen)");
        state = State.SELECTED;
        send(tag + " OK [READ-WRITE] SELECT completed");
    }

    private void cmdFETCH(String tag, String args) throws Exception {
        if (state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }
        String[] p = args.split(" ");
        int id = Integer.parseInt(p[0]);
        if (id < 1 || id > messages.size()) { send(tag + " NO Message not found"); return; }
        File mail = messages.get(id - 1);

        if (args.toUpperCase().contains("FLAGS")) {
            boolean seen = seenFlags.get(id);
            send("* " + clientId() + " " + id + " FETCH (FLAGS " + (seen ? "(\\Seen)" : "()") + ")");
        } else if (args.toUpperCase().contains("BODY[]")) {
            List<String> lines = Files.readAllLines(mail.toPath());
            send("* " + clientId() + " " + id + " FETCH (BODY[] {" + mail.length() + "}");
            for (String l : lines) send(l);
            send(")");
            seenFlags.put(id, true);
        }

        send(tag + " OK FETCH completed");
    }

    private void cmdSTORE(String tag, String args) {
        if (state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }
        String[] p = args.split(" ");
        int id = Integer.parseInt(p[0]);
        seenFlags.put(id, true);
        send("* " + clientId() + " " + id + " FETCH (FLAGS (\\Seen))");
        send(tag + " OK STORE completed");
    }

    private void cmdSEARCH(String tag, String args) throws Exception {
        if (state != State.SELECTED) { send(tag + " BAD Command not allowed now"); return; }
        String keyword = args.replace("SUBJECT", "").replace("\"", "").trim();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            String content = Files.readString(messages.get(i).toPath());
            if (content.contains(keyword)) result.append(i + 1).append(" ");
        }
        send("* " + clientId() + " SEARCH " + result.toString().trim());
        send(tag + " OK SEARCH completed");
    }

    private void cmdLOGOUT(String tag) {
        send("* BYE " + clientId() + " IMAP4rev2 Server logging out");
        send(tag + " OK LOGOUT completed");
        state = State.LOGOUT;
    }
}