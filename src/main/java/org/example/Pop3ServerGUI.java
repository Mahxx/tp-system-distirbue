package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

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

    private static final int PORT = 110;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Pop3ServerGUI().createGUI());
    }

    private void createGUI() {
        frame = new JFrame("POP3 Server Administration");
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

        if (eventLogFile != null) {
            eventLogFile.println(fullMessage);
        }
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
                        log("Client connected: " + client.getInetAddress());
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

// ---------------------- POP3 SESSION ------------------------
class Pop3SessionGUI extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Pop3ServerGUI gui;

    private String username;
    private File userDir;
    private List<File> emails;
    private boolean authenticated;
    private List<Boolean> deletionFlags;

    public Pop3SessionGUI(Socket socket, Pop3ServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
        this.authenticated = false;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("+OK POP3 server ready");

            String line;
            while ((line = in.readLine()) != null) {
                gui.log("Client -> " + line);
                String[] parts = line.split(" ", 2);
                String command = parts[0].toUpperCase();
                String argument = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    case "USER": handleUser(argument); break;
                    case "PASS": handlePass(argument); break;
                    case "STAT": handleStat(); break;
                    case "LIST": handleList(); break;
                    case "RETR": handleRetr(argument); break;
                    case "DELE": handleDele(argument); break;
                    case "RSET": handleRset(); break;
                    case "QUIT": handleQuit(); return;
                    default: send("-ERR Unknown command"); break;
                }
            }
            if (authenticated) gui.log("Connection interrupted without QUIT.");
        } catch (IOException e) {
            gui.log("Client disconnected unexpectedly");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            gui.removeClient(socket);
        }
    }

    private void send(String message) {
        out.println(message);
        gui.log("Server -> " + message);
    }

    private void handleUser(String arg) {
        File dir = new File("mailserver/" + arg);
        if (dir.exists() && dir.isDirectory()) {
            username = arg;
            userDir = dir;
            send("+OK User accepted");
        } else {
            send("-ERR User not found");
        }
    }

    private void handlePass(String arg) {
        if (username == null) { send("-ERR USER required first"); return; }
        authenticated = true;
        File[] files = userDir.listFiles();
        emails = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        deletionFlags = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) deletionFlags.add(false);
        send("+OK Password accepted");
    }

    private void handleStat() {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        long size = emails.stream().mapToLong(File::length).sum();
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
            File emailFile = emails.get(index);
            send("+OK " + emailFile.length() + " octets");
            BufferedReader reader = new BufferedReader(new FileReader(emailFile));
            String line;
            while ((line = reader.readLine()) != null) send(line);
            send(".");
            reader.close();
        } catch (Exception e) { send("-ERR Invalid message number"); }
    }

    private void handleDele(String arg) {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        try {
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size()) { send("-ERR No such message"); return; }
            if (deletionFlags.get(index)) { send("-ERR Message already marked for deletion"); return; }
            deletionFlags.set(index, true);
            send("+OK Message marked for deletion");
        } catch (Exception e) { send("-ERR Invalid message number"); }
    }

    private void handleRset() {
        if (!authenticated) { send("-ERR Authentication required"); return; }
        for (int i = 0; i < deletionFlags.size(); i++) deletionFlags.set(i, false);
        send("+OK Deletion marks reset");
    }

    private void handleQuit() {
        for (int i = deletionFlags.size() - 1; i >= 0; i--) {
            if (deletionFlags.get(i)) {
                File emailFile = emails.get(i);
                if (emailFile.delete()) gui.log("Deleted email: " + emailFile.getAbsolutePath());
                else gui.log("Failed to delete email: " + emailFile.getAbsolutePath());
            }
        }
        send("+OK POP3 server signing off");
    }
}