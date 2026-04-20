package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/messagerie_db";
    private static final String USER = "root";
    private static final String PASS = "mahmoud";

    private static DatabaseManager instance = new DatabaseManager();

    public static DatabaseManager getInstance() { return instance; }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public boolean authenticateUser(String username, String passwordHash) {
        try (Connection c = getConnection(); PreparedStatement stmt = c.prepareStatement("SELECT 1 FROM users WHERE username = ? AND password_hash = ?")) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    public boolean createUser(String username, String passwordHash) {
        try (Connection c = getConnection(); PreparedStatement stmt = c.prepareStatement("INSERT INTO users(username, password_hash) VALUES(?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    public boolean userExists(String username) {
        try (Connection c = getConnection(); PreparedStatement stmt = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    public boolean updateUser(String username, String pass) {
        // Use a direct PreparedStatement UPDATE so executeUpdate() reliably
        // returns the number of affected rows (CallableStatement on procedures
        // always returns 0 in MySQL JDBC, causing this to always report failure).
        try (Connection c = getConnection();
             PreparedStatement stmt = c.prepareStatement(
                 "UPDATE users SET password_hash = ? WHERE username = ?")) {
            stmt.setString(1, pass);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    public boolean deleteUser(String username) {
        try (Connection c = getConnection(); PreparedStatement stmt = c.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    public List<String> getAllUsers() {
        List<String> list = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement stmt = c.prepareStatement("SELECT username FROM users")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    list.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void storeEmail(String sender, String recipient, String subject, String content) {
        try (Connection c = getConnection(); CallableStatement stmt = c.prepareCall("{CALL store_email(?, ?, ?, ?)}")) {
            stmt.setString(1, sender);
            stmt.setString(2, recipient);
            stmt.setString(3, subject);
            stmt.setString(4, content);
            stmt.execute();
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
    }

    public List<EmailMessage> fetchEmails(String user) {
        List<EmailMessage> list = new ArrayList<>();
        try (Connection c = getConnection(); CallableStatement stmt = c.prepareCall("{CALL fetch_emails(?)}")) {
            stmt.setString(1, user);
            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    list.add(new EmailMessage(
                        rs.getInt("id"), 
                        rs.getString("sender"), 
                        rs.getString("recipient"), 
                        rs.getString("subject"), 
                        rs.getTimestamp("created_at"), 
                        rs.getBoolean("is_read"), 
                        rs.getString("content")
                    ));
                }
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
        return list;
    }

    public List<EmailMessage> fetchSentEmails(String user) {
        List<EmailMessage> list = new ArrayList<>();
        try (Connection c = getConnection(); 
             PreparedStatement stmt = c.prepareStatement("SELECT * FROM emails WHERE sender = ? OR sender LIKE ? ORDER BY created_at DESC")) {
            stmt.setString(1, user);
            stmt.setString(2, user + "@%");
            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    list.add(new EmailMessage(
                        rs.getInt("id"), 
                        rs.getString("sender"), 
                        rs.getString("recipient"), 
                        rs.getString("subject"), 
                        rs.getTimestamp("created_at"), 
                        rs.getBoolean("is_read"), 
                        rs.getString("content")
                    ));
                }
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
        return list;
    }

    public void deleteEmail(int emailId) {
        try (Connection c = getConnection(); CallableStatement stmt = c.prepareCall("{CALL delete_email(?)}")) {
            stmt.setInt(1, emailId);
            stmt.execute();
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
    }

    public void updateReadStatus(int emailId, boolean isRead) {
        try (Connection c = getConnection(); PreparedStatement stmt = c.prepareStatement("UPDATE emails SET is_read = ? WHERE id = ?")) {
            stmt.setBoolean(1, isRead);
            stmt.setInt(2, emailId);
            stmt.executeUpdate();
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
    }
}
