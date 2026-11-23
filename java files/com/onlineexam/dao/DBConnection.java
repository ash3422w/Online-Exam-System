package com.onlineexam.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public final class DBConnection {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String URL =
            "jdbc:mysql://localhost:3306/online_exam_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
    private static final String USER = "exam_user";
    private static final String PASS = "exam123";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found.", e);
        }
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /**
     * Fetches the authoritative current timestamp from the MySQL server (SELECT NOW()).
     * Falls back to LocalDateTime.now() only if the DB query fails.
     */
    public static LocalDateTime getServerTime() {
        try (Connection con = getConnection()) {
            return getServerTime(con);
        } catch (SQLException e) {
            System.err.println("[DBConnection] Failed to query DB server time: " + e.getMessage());
            return LocalDateTime.now();
        }
    }

    public static LocalDateTime getServerTime(Connection con) throws SQLException {
        String sql = "SELECT NOW()";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                if (ts != null) {
                    return ts.toLocalDateTime();
                }
            }
        }
        return LocalDateTime.now();
    }
}
