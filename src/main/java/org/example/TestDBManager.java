package org.example;
import java.sql.*;
public class TestDBManager {
    public static void main(String[] args) {
        System.out.println("Testing Database tables...");
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/messagerie_db", "root", "mahmoud")) {
            DatabaseMetaData metaData = c.getMetaData();
            ResultSet rs = metaData.getTables(null, null, "users", null);
            if (rs.next()) {
                System.out.println("Table 'users' EXISTS!");
            } else {
                System.out.println("Table 'users' DOES NOT EXIST! init.sql was not run.");
            }
            // What if we call the stored procedure?
            System.out.println("Testing createUser procedure execution...");
            PreparedStatement stmt = c.prepareStatement("INSERT INTO users(username, password_hash) VALUES(?, ?)");
            stmt.setString(1, "testuser_123");
            stmt.setString(2, "hash");
            int i = stmt.executeUpdate();
            System.out.println("Insert result: " + i);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
