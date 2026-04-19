package org.example;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Demarre le registre RMI et expose AuthService sur le port 1099.
 * Lancer ce programme EN PREMIER, avant les serveurs SMTP, POP3, IMAP.
 */
public class AuthServer {

    public static final int    RMI_PORT     = 1099;
    public static final String SERVICE_NAME = "AuthService";

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            System.out.println("[AuthServer] Registre RMI demarre sur le port " + RMI_PORT);

            AuthServiceImpl service = new AuthServiceImpl();
            registry.rebind(SERVICE_NAME, service);

            System.out.println("[AuthServer] Service '" + SERVICE_NAME + "' enregistre.");
            System.out.println("[AuthServer] Comptes stockes dans : MySQL (messagerie_db.users)");
            System.out.println("[AuthServer] En attente de connexions...");

            // Garder le serveur actif indefiniment
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[AuthServer] ERREUR FATALE : " + e.getMessage());
            e.printStackTrace();
        }
    }
}