package org.example;

import java.io.*;
import java.net.Socket;

public class SmtpClient {

    private static final String SERVER_HOST = "localhost"; 
    private static final int SERVER_PORT = 2525;

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            System.out.println("SERVER: " + in.readLine());

            // HELO
            send(out, in, "HELO client.zmaill.com");

            // MAIL FROM
            send(out, in, "MAIL FROM:<ahmed@zmaill.com>");

            // RCPT TO
            send(out, in, "RCPT TO:<reciever@zmaill.com>");

            // DATA
            send(out, in, "DATA");

            // Email content
            out.println("Hello Reciever,");
            out.println("This zmail is just a test2 .");
            out.println("Ahmed");

            // End of DATA
            out.println(".");
            System.out.println("SERVER: " + in.readLine());

            // QUIT
            send(out, in, "QUIT");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void send(PrintWriter out, BufferedReader in, String cmd)
            throws IOException {
        System.out.println("CLIENT: " + cmd);
        out.println(cmd);
        System.out.println("SERVER: " + in.readLine());
    }
}
