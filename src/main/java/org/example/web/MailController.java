package org.example.web;

import org.example.AuthService;
import org.example.AuthServer;
import org.example.DatabaseManager;
import org.example.EmailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Autoriser les requêtes cross-origin pour le front
public class MailController {

    @Autowired
    private JwtUtil jwtUtil;

    // --- MIDDLEWARE D'AUTHENTIFICATION ---
    private String authenticateRequest(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractUsername(token);
    }

    // --- 1. AUTHENTIFICATION ---
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        String username = creds.get("username");
        String password = creds.get("password");

        try {
            // Appel RMI au serveur d'authentification distribué
            Registry registry = LocateRegistry.getRegistry("localhost", AuthServer.RMI_PORT);
            AuthService authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);

            // 1. Vérifier si l'utilisateur existe
            boolean exists = authService.userExists(username);
            if (!exists) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("L'utilisateur n'existe pas");
            }

            // 2. Vérifier le mot de passe
            boolean success = authService.authenticate(username, password);

            if (success) {
                String token = jwtUtil.generateToken(username);
                Map<String, String> res = new HashMap<>();
                res.put("token", token);
                res.put("username", username);
                return ResponseEntity.ok(res);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Mot de passe incorrect");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Service d'authentification indisponible");
        }
    }

    // --- 1.B INSCRIPTION ---
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> creds) {
        String username = creds.get("username");
        String password = creds.get("password");

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", AuthServer.RMI_PORT);
            AuthService authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);

            // Vérifier si existant
            boolean exists = authService.userExists(username);
            if (exists) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce nom d'utilisateur est déjà pris");
            }

            // Création
            boolean success = authService.createUser(username, password);

            if (success) {
                return ResponseEntity.ok("Compte créé avec succès ! Vous pouvez maintenant vous connecter.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de la création du compte dans la base de données.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Service d'authentification indisponible");
        }
    }

    // --- 2. CONSULTATION INBOX ---
    @GetMapping("/messages")
    public ResponseEntity<?> getInbox(@RequestHeader(value="Authorization", required=false) String authHeader) {
        String username = authenticateRequest(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Accès refusé");

        List<EmailMessage> emails = DatabaseManager.getInstance().fetchEmails(username);
        return ResponseEntity.ok(emails);
    }

    // --- 2.B CONSULTATION MESSAGES ENVOYÉS ---
    @GetMapping("/messages/sent")
    public ResponseEntity<?> getSentMessages(@RequestHeader(value="Authorization", required=false) String authHeader) {
        String username = authenticateRequest(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Accès refusé");

        List<EmailMessage> emails = DatabaseManager.getInstance().fetchSentEmails(username);
        return ResponseEntity.ok(emails);
    }

    // --- 2.C MARQUER COMME LU ---
    @PutMapping("/messages/{id}/read")
    public ResponseEntity<?> markAsRead(@RequestHeader(value="Authorization", required=false) String authHeader, @PathVariable int id) {
        String username = authenticateRequest(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Accès refusé");

        DatabaseManager.getInstance().updateReadStatus(id, true);
        return ResponseEntity.ok("Message marqué comme lu");
    }

    // --- 3. SUPPRESSION D'UN MESSAGE ---
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<?> deleteMessage(@RequestHeader(value="Authorization", required=false) String authHeader, @PathVariable int id) {
        String username = authenticateRequest(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Accès refusé");

        DatabaseManager.getInstance().deleteEmail(id);
        return ResponseEntity.ok("Message supprimé avec succès");
    }

    // --- 4. ENVOI D'UN NOUVEAU MESSAGE (Passage par le serveur SMTP distant) ---
    @PostMapping("/messages/send")
    public ResponseEntity<?> sendMessage(@RequestHeader(value="Authorization", required=false) String authHeader, @RequestBody Map<String, String> emailData) {
        String username = authenticateRequest(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Accès refusé");

        String recipient = emailData.get("to");
        String subject = emailData.get("subject");
        String content = emailData.get("content");

        // On interagit en tant que client avec le serveur SMTP distribué (Port 25 ou 2525)
        try (Socket socket = new Socket("localhost", 25)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Lire la bannière 220
            in.readLine();

            out.println("HELO web-client");
            in.readLine(); // 250

            out.println("MAIL FROM:<" + username + "@example.com>");
            String respM = in.readLine();
            if (!respM.startsWith("250")) return ResponseEntity.status(400).body("Erreur expéditeur: " + respM);

            out.println("RCPT TO:<" + recipient + "@example.com>");
            String respR = in.readLine();
            if (!respR.startsWith("250")) return ResponseEntity.status(400).body("Erreur destinataire: " + respR);

            out.println("DATA");
            in.readLine(); // 354

            out.println("Subject: " + subject);
            out.println("From: " + username + "@example.com");
            out.println("To: " + recipient + "@example.com");
            out.println("");
            out.println(content);
            out.println(".");

            String respD = in.readLine();
            if (!respD.startsWith("250")) return ResponseEntity.status(400).body("Erreur lors de l'envoi: " + respD);

            out.println("QUIT");

            return ResponseEntity.ok("Email envoyé avec succès par le service SMTP");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de communication avec le serveur SMTP distribué: " + e.getMessage());
        }
    }
}
