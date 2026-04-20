package org.example;

public class FixUser {
    public static void main(String[] args) {
        try {
            AuthServiceImpl auth = new AuthServiceImpl();
            boolean exists = auth.userExists("mahmoud");
            System.out.println("mahmoud exists: " + exists);
            if (!exists) {
                boolean created = auth.createUser("mahmoud", "2003");
                System.out.println("Created mahmoud: " + created);
            } else {
                boolean updated = auth.updateUser("mahmoud", "2003");
                System.out.println("Updated mahmoud password: " + updated);
            }
            
            boolean authOk = auth.authenticate("mahmoud", "2003");
            System.out.println("Auth check with 2003: " + authOk);
            
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
