package org.example;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {

    public AuthServiceImpl() throws RemoteException {
        super();
        System.out.println("[AuthServiceImpl] Connecte a la base de donnees MySQL.");
    }

    @Override
    public synchronized boolean authenticate(String username, String password) throws RemoteException {
        if (username == null || password == null) return false;
        boolean ok = DatabaseManager.getInstance().authenticateUser(username.toLowerCase(), hash(password));
        System.out.println("[Auth] authenticate(" + username + ") -> " + ok);
        return ok;
    }

    @Override
    public synchronized boolean createUser(String username, String password) throws RemoteException {
        if (username == null || password == null || username.isBlank()) return false;
        boolean ok = DatabaseManager.getInstance().createUser(username.toLowerCase(), hash(password));
        System.out.println("[Auth] createUser(" + username + ") -> " + (ok ? "OK" : "existe deja ou erreur DB"));
        return ok;
    }

    @Override
    public synchronized boolean updateUser(String username, String newPassword) throws RemoteException {
        if (username == null || newPassword == null) return false;
        boolean ok = DatabaseManager.getInstance().updateUser(username.toLowerCase(), hash(newPassword));
        System.out.println("[Auth] updateUser(" + username + ") -> " + (ok ? "OK" : "introuvable/erreur"));
        return ok;
    }

    @Override
    public synchronized boolean deleteUser(String username) throws RemoteException {
        if (username == null) return false;
        boolean ok = DatabaseManager.getInstance().deleteUser(username.toLowerCase());
        System.out.println("[Auth] deleteUser(" + username + ") -> " + (ok ? "OK" : "introuvable/erreur"));
        return ok;
    }

    @Override
    public synchronized boolean userExists(String username) throws RemoteException {
        if (username == null) return false;
        return DatabaseManager.getInstance().userExists(username.toLowerCase());
    }

    @Override
    public synchronized java.util.List<String> getAllUsers() throws RemoteException {
        return DatabaseManager.getInstance().getAllUsers();
    }

    public static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }
}