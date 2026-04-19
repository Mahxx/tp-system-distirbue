package org.example;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
public class TestRMI {
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");
            System.out.println("Got authService...");
            
            boolean exists = authService.userExists("hellohello");
            System.out.println("hellohello exists? " + exists);
            
            if(!exists) {
                boolean created = authService.createUser("hellohello", "pass");
                System.out.println("Created hellohello? " + created);
            }
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
