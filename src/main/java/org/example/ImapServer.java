package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * IMAP Server
 * Chaque client est géré par un thread séparé (parallélisation)
 */
public class ImapServer {

    private static final int PORT = 1430;

    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(PORT)) {

            System.out.println("IMAP Server listening on port " + PORT);

            while (true) {
                Socket client = server.accept();
                System.out.println("Client connected: " + client.getInetAddress());

                new ImapSession(client).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}