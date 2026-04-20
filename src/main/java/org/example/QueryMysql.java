package org.example;
import java.sql.*;
public class QueryMysql {
    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/messagerie_db", "root", "mahmoud");
        Statement stmt = c.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT username, password_hash FROM users WHERE username='mahmoud'");
        while(rs.next()) {
            System.out.println("DB username: " + rs.getString(1));
            System.out.println("DB password_hash: " + rs.getString(2));
        }
        System.out.println("Hash of 2003 should be: " + AuthServiceImpl.hash("2003"));
    }
}
