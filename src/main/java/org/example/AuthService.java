package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface RMI exposée sur le réseau.
 * SMTP, POP3 et IMAP l'utilisent pour authentifier les utilisateurs.
 */
public interface AuthService extends Remote {
    boolean authenticate(String username, String password)  throws RemoteException;
    boolean createUser(String username, String password)    throws RemoteException;
    boolean updateUser(String username, String newPassword) throws RemoteException;
    boolean deleteUser(String username)                     throws RemoteException;
    boolean userExists(String username)                     throws RemoteException;
    java.util.List<String> getAllUsers()                    throws RemoteException;
}