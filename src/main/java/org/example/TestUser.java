package org.example;

public class TestUser {
    public static void main(String[] args) {
        System.out.println("Checking user mahmoud...");
        boolean exists = DatabaseManager.getInstance().userExists("mahmoud");
        System.out.println("Exists: " + exists);
        if (!exists) {
            System.out.println("Creating user mahmoud...");
            boolean created = DatabaseManager.getInstance().createUser("mahmoud", "2003");
            System.out.println("Created: " + created);
        } else {
            System.out.println("Authenticating user mahmoud with 2003...");
            boolean auth = DatabaseManager.getInstance().authenticateUser("mahmoud", "2003");
            System.out.println("Auth success: " + auth);
            if (!auth) {
                System.out.println("Updating password to 2003...");
                boolean updated = DatabaseManager.getInstance().updateUser("mahmoud", "2003");
                System.out.println("Updated: " + updated);
            }
        }
    }
}
