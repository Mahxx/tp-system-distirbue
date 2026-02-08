package org.example;

import java.io.*;
import java.net.Socket;

public class Pop3Client {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 110; // adjust if your server uses a custom port

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            System.out.println("SERVER: " + in.readLine());

            send(out, in, "USER reciever");
            send(out, in, "PASS anything");

            // Get total messages from STAT
            out.println("STAT");
            String statResponse = in.readLine();
            System.out.println("SERVER: " + statResponse);

            int totalMessages = 0;
            if (statResponse.startsWith("+OK")) {
                String[] parts = statResponse.split(" ");
                totalMessages = Integer.parseInt(parts[1]);
            }

            // List all messages
            send(out, in, "LIST");

            // Retrieve all messages
            for (int i = 1; i <= totalMessages; i++) {
                System.out.println("Retrieving message " + i);
                out.println("RETR " + i);

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("SERVER: " + line);
                    if (line.equals(".")) break; // end of message
                }
            }

            // Optionally mark messages for deletion and reset
            send(out, in, "DELE 1"); // example for first message
            send(out, in, "RSET");

            send(out, in, "QUIT");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void send(PrintWriter out, BufferedReader in, String cmd) throws IOException {
        System.out.println("CLIENT: " + cmd);
        out.println(cmd);

        String line;
        while ((line = in.readLine()) != null) {
            System.out.println("SERVER: " + line);
            // Stop reading after single-line response
            if (cmd.startsWith("USER") || cmd.startsWith("PASS") || cmd.startsWith("DELE") || 
                cmd.startsWith("RSET") || cmd.startsWith("QUIT")) {
                break;
            }
            // For multi-line responses (LIST) end when a single dot is received
            if (cmd.startsWith("LIST") && line.equals(".")) {
                break;
            }
        }
    }
}
