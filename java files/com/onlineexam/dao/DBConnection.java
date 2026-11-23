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

    private DBConnection() {
    }

    private static String getUrl() {
        String env = System.getenv("DB_URL");
        if (env != null && !env.isBlank()) return env;
        return readProp("db.url", "jdbc:mysql://localhost:3306/online_exam_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata");
    }

    private static String getUser() {
        String env = System.getenv("DB_USER");
        if (env != null && !env.isBlank()) return env;
        return readProp("db.user", "exam_user");
    }

    private static String getPass() {
        String env = System.getenv("DB_PASS");
        if (env != null && !env.isBlank()) return env;
        return readProp("db.pass", "exam123");
    }

    private static String readProp(String key, String def) {
        java.io.File f = new java.io.File("db.properties");
        if (f.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                java.util.Properties p = new java.util.Properties();
                p.load(fis);
                String val = p.getProperty(key);
                if (val != null && !val.isBlank()) return val.trim();
            } catch (Exception ignored) {}
        }
        return def;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found.", e);
        }
        String url = getUrl();
        String user = getUser();
        String pass = getPass();
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException ex) {
            // Intelligent fallback for standard local MySQL root installations
            if (url.contains("localhost") && !"root".equals(user)) {
                try {
                    return DriverManager.getConnection(url, "root", "root");
                } catch (Exception ignored) {
                    try {
                        return DriverManager.getConnection(url, "root", "");
                    } catch (Exception ignored2) {}
                }
            }
            throw ex;
        }
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
