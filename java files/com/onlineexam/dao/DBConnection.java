package com.onlineexam.dao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class DBConnection {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final int MAX_POOL_SIZE = 10;
    private static final ConcurrentLinkedQueue<Connection> POOL = new ConcurrentLinkedQueue<>();
    private static volatile boolean shutdownHookInstalled = false;

    static {
        // Pre-warm database connection asynchronously at class loading to eliminate cold-start delay
        Thread prewarm = new Thread(() -> {
            try {
                Connection c = createPhysicalConnection();
                if (c != null) {
                    POOL.offer(c);
                }
            } catch (Throwable ignored) {}
        }, "DBConnection-Prewarm");
        prewarm.setDaemon(true);
        prewarm.start();

        installPoolShutdownHook();
    }

    private DBConnection() {
    }

    private static synchronized void installPoolShutdownHook() {
        if (!shutdownHookInstalled) {
            shutdownHookInstalled = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Connection c;
                while ((c = POOL.poll()) != null) {
                    try {
                        c.close();
                    } catch (Exception ignored) {}
                }
            }, "DBConnection-Pool-Cleanup"));
        }
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

    private static Connection createPhysicalConnection() throws SQLException {
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
     * Obtains a high-performance pooled database connection.
     * When closed via try-with-resources or con.close(), the connection is reset and
     * returned to the pool without closing the underlying TCP socket.
     */
    public static Connection getConnection() throws SQLException {
        Connection real = POOL.poll();
        while (real != null) {
            try {
                if (!real.isClosed() && real.isValid(1)) {
                    break;
                }
                real.close();
            } catch (Exception ignored) {}
            real = POOL.poll();
        }

        if (real == null) {
            real = createPhysicalConnection();
        }

        final Connection finalReal = real;
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    private boolean closed = false;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("close".equals(name)) {
                            if (!closed) {
                                closed = true;
                                try {
                                    if (!finalReal.isClosed()) {
                                        if (!finalReal.getAutoCommit()) {
                                            finalReal.setAutoCommit(true);
                                        }
                                        if (POOL.size() < MAX_POOL_SIZE) {
                                            POOL.offer(finalReal);
                                            return null;
                                        }
                                    }
                                } catch (Exception ignored) {}
                                finalReal.close();
                            }
                            return null;
                        }
                        if ("isClosed".equals(name)) {
                            return closed || finalReal.isClosed();
                        }
                        try {
                            return method.invoke(finalReal, args);
                        } catch (InvocationTargetException ite) {
                            throw ite.getCause();
                        }
                    }
                }
        );
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
