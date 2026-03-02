package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;

/*
 * IMAP Session
 * Implémente LOGIN, SELECT, FETCH, STORE, SEARCH, LOGOUT
 * Support des flags (\Seen)
 * FSM selon RFC 9051
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
    private File mailboxDir;
    private List<File> messages = new ArrayList<>();
    private Map<Integer, Boolean> seenFlags = new HashMap<>();

    public ImapSession(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Greeting RFC
            send("* OK IMAP4rev2 Service Ready");

            String line;

            while ((line = in.readLine()) != null) {

                System.out.println("C: " + line);

                String[] parts = line.split(" ", 3);

                String tag = parts[0];
                String command = parts.length > 1 ? parts[1].toUpperCase() : "";
                String args = parts.length > 2 ? parts[2] : "";

                switch (command) {
                    case "LOGIN":
                        cmdLOGIN(tag, args);
                        break;
                    case "SELECT":
                        cmdSELECT(tag, args);
                        break;
                    case "FETCH":
                        cmdFETCH(tag, args);
                        break;
                    case "STORE":
                        cmdSTORE(tag, args);
                        break;
                    case "SEARCH":
                        cmdSEARCH(tag, args);
                        break;
                    case "LOGOUT":
                        cmdLOGOUT(tag);
                        return;
                    default:
                        send(tag + " BAD Unknown command");
                }
            }

        } catch (Exception e) {
            System.out.println("Session closed.");
        }
    }

    // ================= LOGIN =================
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

        username = p[0];

        File dir = new File("mailserver/" + username);
        if (!dir.exists() || !dir.isDirectory()) {
            send(tag + " NO Authentication failed");
            return;
        }

        mailboxDir = dir;
        state = State.AUTHENTICATED;
        send(tag + " OK LOGIN completed");
    }

    // ================= SELECT =================
    private void cmdSELECT(String tag, String mailbox) {
        if (state != State.AUTHENTICATED) {
            send(tag + " BAD Command not allowed now");
            return;
        }

        if (!mailbox.equalsIgnoreCase("INBOX")) {
            send(tag + " NO Mailbox does not exist");
            return;
        }

        File[] files = mailboxDir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null) files = new File[0];

        messages = Arrays.asList(files);

        seenFlags.clear();
        for (int i = 0; i < messages.size(); i++)
            seenFlags.put(i + 1, false);

        send("* " + messages.size() + " EXISTS");
        send("* FLAGS (\\Seen)");

        state = State.SELECTED;
        send(tag + " OK [READ-WRITE] SELECT completed");
    }

    // ================= FETCH =================
    private void cmdFETCH(String tag, String args) throws Exception {
        if (state != State.SELECTED) {
            send(tag + " BAD Command not allowed now");
            return;
        }

        String[] p = args.split(" ");
        int id = Integer.parseInt(p[0]);

        if (id < 1 || id > messages.size()) {
            send(tag + " NO Message not found");
            return;
        }

        File mail = messages.get(id - 1);

        if (args.toUpperCase().contains("FLAGS")) {
            boolean seen = seenFlags.get(id);
            String flags = seen ? "(\\Seen)" : "()";
            send("* " + id + " FETCH (FLAGS " + flags + ")");
        } else if (args.toUpperCase().contains("BODY[]")) {
            List<String> lines = Files.readAllLines(mail.toPath());
            send("* " + id + " FETCH (BODY[] {" + mail.length() + "}");
            for (String l : lines) send(l);
            send(")");
            seenFlags.put(id, true);
        }

        send(tag + " OK FETCH completed");
    }

    // ================= STORE =================
    private void cmdSTORE(String tag, String args) {
        if (state != State.SELECTED) {
            send(tag + " BAD Command not allowed now");
            return;
        }

        String[] p = args.split(" ");
        int id = Integer.parseInt(p[0]);
        seenFlags.put(id, true);

        send("* " + id + " FETCH (FLAGS (\\Seen))");
        send(tag + " OK STORE completed");
    }

    // ================= SEARCH =================
    private void cmdSEARCH(String tag, String args) throws Exception {
        if (state != State.SELECTED) {
            send(tag + " BAD Command not allowed now");
            return;
        }

        String keyword = args.replace("SUBJECT", "").replace("\"", "").trim();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < messages.size(); i++) {
            String content = Files.readString(messages.get(i).toPath());
            if (content.contains(keyword)) result.append(i + 1).append(" ");
        }

        send("* SEARCH " + result.toString().trim());
        send(tag + " OK SEARCH completed");
    }

    // ================= LOGOUT =================
    private void cmdLOGOUT(String tag) {
        send("* BYE IMAP4rev2 Server logging out");
        send(tag + " OK LOGOUT completed");
        state = State.LOGOUT;
    }

    // ================= SEND =================
    private void send(String msg) {
        System.out.println("S: " + msg);
        out.println(msg);
    }
}