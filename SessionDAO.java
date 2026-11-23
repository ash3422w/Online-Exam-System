package com.onlineexam.dao;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages active student login sessions in the `active_sessions` table using DB server time (NOW()).
 * Prevents a student account from being used on a second device/window while an exam is in progress.
 */
public class SessionDAO {

    public static final int STALE_TIMEOUT_SECONDS = 45;
    public static final String BLOCKED_EXAM_MESSAGE =
            "This account is currently taking an exam on another device. Try again after it is submitted.";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a");

    public enum LoginOutcome {
        OK,
        BLOCKED_EXAM_IN_PROGRESS,
        ERROR
    }

    public static class LoginResult {
        public final LoginOutcome outcome;
        public final String token;
        public final String message;

        public LoginResult(LoginOutcome outcome, String token, String message) {
            this.outcome = outcome;
            this.token = token;
            this.message = message;
        }

        public boolean isOk() {
            return outcome == LoginOutcome.OK && token != null && !token.isEmpty();
        }
    }

    public static class ActiveSessionInfo {
        public final String userId;
        public final String machineInfo;
        public final Integer examId;
        public final boolean inExam;
        public final boolean alive;
        public final long secondsSinceHeartbeat;
        public final String loginAt;
        public final String lastHeartbeat;

        public ActiveSessionInfo(
                String userId, String machineInfo, Integer examId, boolean inExam,
                boolean alive, long secondsSinceHeartbeat, String loginAt, String lastHeartbeat) {
            this.userId = userId;
            this.machineInfo = machineInfo;
            this.examId = examId;
            this.inExam = inExam;
            this.alive = alive;
            this.secondsSinceHeartbeat = secondsSinceHeartbeat;
            this.loginAt = loginAt;
            this.lastHeartbeat = lastHeartbeat;
        }
    }

    /**
     * Atomically attempts to claim or take over a student session using DB server time NOW().
     * - If an alive session (last_heartbeat within 45s) has in_exam = 1 -> BLOCKED_EXAM_IN_PROGRESS.
     * - Otherwise (no row, stale row, or alive row with in_exam = 0) -> creates/replaces token and returns OK.
     */
    public static LoginResult tryLogin(String userId, String machineInfo) {
        if (userId == null || userId.trim().isEmpty()) {
            return new LoginResult(LoginOutcome.ERROR, null, "Invalid user ID.");
        }
        String cleanUser = userId.trim();
        String cleanMachine = sanitizeMachineInfo(machineInfo);

        String lockSql = "SELECT in_exam, "
                + "(TIMESTAMPDIFF(SECOND, last_heartbeat, NOW()) <= ?) AS is_alive "
                + "FROM active_sessions WHERE user_id = ? FOR UPDATE";
        String insertSql = "INSERT INTO active_sessions(user_id, session_token, machine_info, exam_id, in_exam, login_at, last_heartbeat) "
                + "VALUES (?, ?, ?, NULL, 0, NOW(), NOW())";
        String updateSql = "UPDATE active_sessions SET session_token = ?, machine_info = ?, exam_id = NULL, "
                + "in_exam = 0, login_at = NOW(), last_heartbeat = NOW() WHERE user_id = ?";

        try (Connection con = DBConnection.getConnection()) {
            boolean prevAuto = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                boolean rowExists = false;
                boolean inExam = false;
                boolean isAlive = false;

                try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                    ps.setInt(1, STALE_TIMEOUT_SECONDS);
                    ps.setString(2, cleanUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            rowExists = true;
                            inExam = rs.getInt("in_exam") == 1;
                            isAlive = rs.getInt("is_alive") == 1;
                        }
                    }
                }

                if (rowExists && isAlive && inExam) {
                    con.commit();
                    return new LoginResult(LoginOutcome.BLOCKED_EXAM_IN_PROGRESS, null, BLOCKED_EXAM_MESSAGE);
                }

                String newToken = generateToken();
                if (rowExists) {
                    try (PreparedStatement up = con.prepareStatement(updateSql)) {
                        up.setString(1, newToken);
                        up.setString(2, cleanMachine);
                        up.setString(3, cleanUser);
                        up.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = con.prepareStatement(insertSql)) {
                        ins.setString(1, cleanUser);
                        ins.setString(2, newToken);
                        ins.setString(3, cleanMachine);
                        ins.executeUpdate();
                    }
                }

                con.commit();
                return new LoginResult(LoginOutcome.OK, newToken, "OK");
            } catch (SQLException ex) {
                try { con.rollback(); } catch (SQLException rbEx) { ex.addSuppressed(rbEx); }
                throw ex;
            } finally {
                try { con.setAutoCommit(prevAuto); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new LoginResult(LoginOutcome.ERROR, null, "Session check failed: " + e.getMessage());
        }
    }

    /**
     * Updates last_heartbeat = NOW() if the (userId, token) pair is still the active session.
     * Returns true if valid and updated; false if displaced or force-unlocked.
     */
    public static boolean heartbeat(String userId, String token) {
        if (userId == null || userId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return false;
        }
        String sql = "UPDATE active_sessions SET last_heartbeat = NOW() WHERE user_id = ? AND session_token = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, token.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // Do not spuriously log out on a transient DB hiccup
        }
    }

    /**
     * Marks the student session as currently in an exam (or clears it when exam finishes/closes).
     * Returns false if the session token is no longer valid.
     */
    public static boolean setInExam(String userId, String token, Integer examId, boolean inExam) {
        if (userId == null || userId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return false;
        }
        String sql = "UPDATE active_sessions SET in_exam = ?, exam_id = ?, last_heartbeat = NOW() "
                + "WHERE user_id = ? AND session_token = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inExam ? 1 : 0);
            if (!inExam || examId == null || examId <= 0) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, examId);
            }
            ps.setString(3, userId.trim());
            ps.setString(4, token.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes the active session row ONLY if the token matches, preventing a displaced
     * window's logout/shutdown hook from deleting the new device's session.
     */
    public static boolean logout(String userId, String token) {
        if (userId == null || userId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return false;
        }
        String sql = "DELETE FROM active_sessions WHERE user_id = ? AND session_token = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, token.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isTokenValid(String userId, String token) {
        if (userId == null || userId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM active_sessions WHERE user_id = ? AND session_token = ? LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, token.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<ActiveSessionInfo> listActiveSessions() {
        List<ActiveSessionInfo> list = new ArrayList<>();
        String sql = "SELECT user_id, machine_info, exam_id, in_exam, login_at, last_heartbeat, "
                + "TIMESTAMPDIFF(SECOND, last_heartbeat, NOW()) AS age_sec "
                + "FROM active_sessions "
                + "ORDER BY in_exam DESC, last_heartbeat DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uid = rs.getString("user_id");
                String mach = rs.getString("machine_info");
                int eid = rs.getInt("exam_id");
                Integer examId = rs.wasNull() ? null : eid;
                boolean inExam = rs.getInt("in_exam") == 1;
                long ageSec = Math.max(0L, rs.getLong("age_sec"));
                boolean alive = ageSec <= STALE_TIMEOUT_SECONDS;

                Timestamp loginTs = rs.getTimestamp("login_at");
                Timestamp hbTs = rs.getTimestamp("last_heartbeat");
                String loginStr = loginTs != null ? loginTs.toLocalDateTime().format(DT_FMT) : "-";
                String hbStr = hbTs != null ? hbTs.toLocalDateTime().format(DT_FMT) : "-";

                list.add(new ActiveSessionInfo(
                        uid,
                        (mach == null || mach.trim().isEmpty()) ? "Unknown Device" : mach.trim(),
                        examId, inExam, alive, ageSec, loginStr, hbStr
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Admin emergency action: deletes any active session lock for the given student.
     */
    public static boolean forceUnlock(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        String sql = "DELETE FROM active_sessions WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String buildLocalMachineInfo() {
        String host = "Localhost";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        String os = System.getProperty("os.name", "OS");
        String osUser = System.getProperty("user.name", "user");
        return sanitizeMachineInfo(host + " (" + os + " / " + osUser + ")");
    }

    private static String sanitizeMachineInfo(String info) {
        if (info == null || info.trim().isEmpty()) {
            return "Unknown Device";
        }
        String t = info.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
