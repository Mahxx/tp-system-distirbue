package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class SmtpServerGUI {

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SmtpServerGUI().createGUI());
    }

    private void createGUI() {
        frame = new JFrame("SMTP Server Administration");
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
            eventLogFile = new PrintWriter(new FileWriter("logs/server_events.log", true), true);
        } catch (IOException e) {
            log("Cannot create log file: " + e.getMessage());
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(25);
                log("SMTP Server started on port 25");

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        addClient(client);
                        log(client.getRemoteSocketAddress() + " connected");
                        new SmtpSessionGUI(client, this).start();
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

    public boolean isRunning() {
        return running;
    }
}

// --------------------- SMTP SESSION ------------------------
class SmtpSessionGUI extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private SmtpServerGUI gui;

    private enum SmtpState { CONNECTED, HELO_RECEIVED, MAIL_FROM_SET, RCPT_TO_SET, DATA_RECEIVING }

    private SmtpState state;
    private String sender;
    private List<String> recipients;
    private StringBuilder dataBuffer;

    public SmtpSessionGUI(Socket socket, SmtpServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
        this.state = SmtpState.CONNECTED;
        this.recipients = new ArrayList<>();
        this.dataBuffer = new StringBuilder();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("220 smtp.example.com Service Ready");

            String line;
            while (gui.isRunning() && (line = in.readLine()) != null) {

                // Log every incoming client command with IP:port
                gui.log(clientId() + " -> " + line);

                if (state == SmtpState.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0);
                        state = SmtpState.HELO_RECEIVED;
                        send("250 OK: Message accepted for delivery");
                    } else {
                        dataBuffer.append(line).append("\r\n");
                    }
                    continue;
                }

                String command = extractToken(line).toUpperCase();
                String arg = extractArgument(line);

                switch (command) {
                    case "HELO": case "EHLO": handleHelo(arg); break;
                    case "MAIL": handleMailFrom(arg); break;
                    case "RCPT": handleRcptTo(arg); break;
                    case "DATA": handleData(); break;
                    case "QUIT": handleQuit(); return;
                    default: send("500 Command unrecognized"); break;
                }
            }

        } catch (IOException e) {
            gui.log(clientId() + " disconnected");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            gui.removeClient(socket);
        }
    }

    private String clientId() {
        return socket.getRemoteSocketAddress().toString();
    }

    private void send(String message) {
        out.println(message);
        gui.log(clientId() + " -> " + message);
    }

    private void handleHelo(String arg) {
        state = SmtpState.HELO_RECEIVED;
        sender = "";
        recipients.clear();
        send("250 Hello " + arg);
    }

    private void handleMailFrom(String arg) {
        if (!arg.toUpperCase().matches("^FROM:\\s*<[^>]+>$")) { send("501 Syntax error in parameters or arguments"); return; }
        String email = arg.substring(5).trim();
        email = email.substring(1, email.length()-1).trim();
        sender = email;
        state = SmtpState.MAIL_FROM_SET;
        send("250 OK");
    }

    private void handleRcptTo(String arg) {
        if (state != SmtpState.MAIL_FROM_SET && state != SmtpState.RCPT_TO_SET) { send("503 Bad sequence of commands"); return; }
        if (!arg.toUpperCase().startsWith("TO:")) { send("501 Syntax error in parameters or arguments"); return; }
        String email = arg.substring(3).trim();
        recipients.add(email);
        state = SmtpState.RCPT_TO_SET;
        send("250 OK");
    }

    private void handleData() {
        if (state != SmtpState.RCPT_TO_SET || recipients.isEmpty()) { send("503 Bad sequence of commands"); return; }
        state = SmtpState.DATA_RECEIVING;
        send("354 Start mail input; end with <CRLF>.<CRLF>");
    }

    private void handleQuit() { send("221 smtp.example.com Service closing transmission channel"); }

    private String extractToken(String line) { String[] parts = line.split(" "); return parts.length>0?parts[0]:""; }

    private String extractArgument(String line) { int idx = line.indexOf(' '); return idx>0?line.substring(idx).trim():""; }

    private void storeEmail(String data) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        for (String recipient : recipients) {
            String username = recipient.split("@")[0];
            File userDir = new File("mailserver/"+username);
            if (!userDir.exists()) userDir.mkdirs();
            File emailFile = new File(userDir, timestamp+".txt");

            try (PrintWriter writer = new PrintWriter(new FileWriter(emailFile))) {
                writer.println("From: " + sender);
                writer.println("To: " + String.join(", ", recipients));
                writer.println("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(new Date()));
                writer.println("Subject: Test Email");
                writer.println();
                writer.print(data);
                gui.log(clientId() + " Stored email for " + recipient + " in " + emailFile.getAbsolutePath());
            } catch (IOException e) {
                gui.log(clientId() + " Error storing email: " + e.getMessage());
            }
        }
    }
}