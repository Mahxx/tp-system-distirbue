package org.example;
import java.sql.*;
public class CheckDB {
    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/messagerie_db", "root", "mahmoud");
        CallableStatement stmt = c.prepareCall("{CALL authenticate_user(?, ?, ?)}");
        stmt.setString(1, "mahmoud");
        stmt.setString(2, AuthServiceImpl.hash("2003"));
        stmt.registerOutParameter(3, Types.BOOLEAN);
        stmt.execute();
        System.out.println("Result of proc: " + stmt.getBoolean(3));
        
        System.out.println("Checking length of parameters in procedure...");
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT PARAMETER_NAME, CHARACTER_MAXIMUM_LENGTH FROM information_schema.PARAMETERS WHERE SPECIFIC_NAME = 'authenticate_user'");
        while(rs.next()) {
            System.out.println(rs.getString(1) + ": " + rs.getString(2));
        }
    }
}
