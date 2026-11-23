package com.onlineexam.dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.onlineexam.data.ExamStore;

public class UserDAO {

    public static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int PBKDF2_ITERATIONS = 120000;
    public static final int PBKDF2_KEY_LENGTH = 256; // 32 bytes
    public static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public enum SignupStatus {
        SUCCESS,
        USER_EXISTS,
        DB_ERROR
    }

    public static class UserProfile {
        public final int id;
        public final String username;
        public final String fullName;
        public final String role;
        public final Timestamp createdAt;

        public UserProfile(int id, String username, String fullName, String role, Timestamp createdAt) {
            this.id = id;
            this.username = username != null ? username : "";
            this.fullName = fullName != null ? fullName : "";
            this.role = role != null ? role : "student";
            this.createdAt = createdAt;
        }

        public String getDisplayName() {
            if (fullName != null && !fullName.trim().isEmpty()) {
                return fullName.trim();
            }
            return (username != null && !username.trim().isEmpty()) ? username : "Student";
        }
    }

    public static class ChangePasswordResult {
        public final boolean success;
        public final String message;

        public ChangePasswordResult(boolean success, String message) {
            this.success = success;
            this.message = message != null ? message : "";
        }
    }

    /**
     * Produces a salted PBKDF2WithHmacSHA256 hash formatted as:
     * pbkdf2$120000$<saltBase64>$<hashBase64>
     */
    public static String hashPasswordPbkdf2(String plainPassword) {
        if (plainPassword == null) {
            plainPassword = "";
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = computePbkdf2Hash(plainPassword.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hashB64 = Base64.getEncoder().encodeToString(hash);
        return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + saltB64 + "$" + hashB64;
    }

    private static byte[] computePbkdf2Hash(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 computation error: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy unsalted SHA-256 for backward compatibility with pre-Task 9 test accounts.
     */
    public static String hashSha256(String plainPassword) {
        if (plainPassword == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return plainPassword;
        }
    }

    /**
     * Preserved signature: always writes the new PBKDF2 format.
     */
    public static String hashPassword(String plainPassword) {
        return hashPasswordPbkdf2(plainPassword);
    }

    /**
     * Constant-time verification supporting PBKDF2, legacy unsalted SHA-256, and legacy plain text.
     */
    public static boolean verifyPassword(String plainPassword, String stored) {
        if (stored == null || plainPassword == null) {
            return false;
        }

        // 1. Salted PBKDF2
        if (stored.startsWith("pbkdf2$")) {
            String[] parts = stored.split("\\$");
            if (parts.length == 4) {
                try {
                    int iterations = Integer.parseInt(parts[1]);
                    byte[] salt = Base64.getDecoder().decode(parts[2]);
                    byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
                    byte[] computedHash = computePbkdf2Hash(
                            plainPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
                    return MessageDigest.isEqual(expectedHash, computedHash);
                } catch (Exception e) {
                    return false;
                }
            }
        }

        // 2. Legacy unsalted SHA-256 (64 hex characters)
        String shaHex = hashSha256(plainPassword);
        if (MessageDigest.isEqual(
                stored.toLowerCase().getBytes(StandardCharsets.UTF_8),
                shaHex.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        // 3. Legacy plain text
        if (MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                plainPassword.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        return false;
    }

    public static boolean isLegacyPasswordFormat(String stored) {
        return stored != null && !stored.startsWith("pbkdf2$");
    }

    // ADD USER (SIGNUP)
    public static boolean addUser(String username, String password, String role) {
        return registerUser(username, password, role) == SignupStatus.SUCCESS;
    }

    public static SignupStatus registerUser(String username, String password, String role) {
        return registerUser(username, password, role, null);
    }

    public static SignupStatus registerUser(String username, String password, String role, String fullName) {
        String checkSql = "SELECT id FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users (username, password, role, full_name) VALUES (?, ?, ?, ?)";
        String hashedPassword = hashPasswordPbkdf2(password);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement check = con.prepareStatement(checkSql)) {

            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return SignupStatus.USER_EXISTS;
                }
            }

            try (PreparedStatement insert = con.prepareStatement(insertSql)) {
                insert.setString(1, username);
                insert.setString(2, hashedPassword);
                insert.setString(3, role);
                String safeName = fullName != null ? fullName.trim() : "";
                if (safeName.isEmpty()) {
                    insert.setNull(4, Types.VARCHAR);
                } else {
                    insert.setString(4, safeName);
                }
                insert.executeUpdate();
            }

            return SignupStatus.SUCCESS;

        } catch (SQLException e) {
            e.printStackTrace();
            return SignupStatus.DB_ERROR;
        }
    }

    /**
     * LOGIN VALIDATION:
     * Accepts PBKDF2, legacy unsalted SHA-256, and legacy plain text.
     * After a successful login with a legacy format, silently re-hashes the password to PBKDF2.
     * Also initializes the current user's full name in ExamStore.
     */
    public static boolean validateUser(String username, String password, String role) {
        if (username == null || password == null || role == null) {
            return false;
        }
        String sql = "SELECT id, password, full_name FROM users WHERE username = ? AND role = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            ps.setString(2, role.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }

                int userId = rs.getInt("id");
                String storedPassword = rs.getString("password");
                String fullName = rs.getString("full_name");

                boolean matches = verifyPassword(password, storedPassword);
                if (!matches) {
                    return false;
                }

                // If stored in legacy format, silently upgrade to PBKDF2 now
                if (isLegacyPasswordFormat(storedPassword)) {
                    String newPbkdf2 = hashPasswordPbkdf2(password);
                    String updateSql = "UPDATE users SET password = ? WHERE id = ?";
                    try (PreparedStatement updatePs = con.prepareStatement(updateSql)) {
                        updatePs.setString(1, newPbkdf2);
                        updatePs.setInt(2, userId);
                        updatePs.executeUpdate();
                        System.out.println("[UserDAO] Silently upgraded password for user '" + username + "' to PBKDF2.");
                    } catch (SQLException ex) {
                        System.err.println("[UserDAO] Failed to silently upgrade password for " + username + ": " + ex.getMessage());
                    }
                }

                ExamStore.setCurrentUserFullName(fullName);
                return true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieves the user profile record by username.
     */
    public static UserProfile getProfile(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT id, username, full_name, role, created_at FROM users WHERE username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String uname = rs.getString("username");
                    String fname = rs.getString("full_name");
                    String role = rs.getString("role");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new UserProfile(id, uname, fname, role, createdAt);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Updates the user's full name in the database and active session.
     */
    public static boolean updateFullName(String username, String name) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String sql = "UPDATE users SET full_name = ? WHERE username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            String trimmed = name != null ? name.trim() : "";
            if (trimmed.isEmpty()) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, trimmed);
            }
            ps.setString(2, username.trim());
            int rows = ps.executeUpdate();
            if (rows > 0 && username.trim().equals(ExamStore.getCurrentUserId())) {
                ExamStore.setCurrentUserFullName(trimmed);
            }
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Changes user password, verifying the old password (accepts all 3 formats)
     * and writing the new password in PBKDF2 format.
     */
    public static ChangePasswordResult changePassword(String username, String oldPassword, String newPassword) {
        if (username == null || username.trim().isEmpty()) {
            return new ChangePasswordResult(false, "Username cannot be empty.");
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            return new ChangePasswordResult(false, "Current password is required.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            return new ChangePasswordResult(false, "New password must be at least 8 characters long.");
        }

        String selectSql = "SELECT id, password FROM users WHERE username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(selectSql)) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new ChangePasswordResult(false, "User account not found.");
                }

                int id = rs.getInt("id");
                String stored = rs.getString("password");

                if (!verifyPassword(oldPassword, stored)) {
                    return new ChangePasswordResult(false, "Current password is incorrect.");
                }

                String newPbkdf2 = hashPasswordPbkdf2(newPassword);
                String updateSql = "UPDATE users SET password = ? WHERE id = ?";
                try (PreparedStatement updatePs = con.prepareStatement(updateSql)) {
                    updatePs.setString(1, newPbkdf2);
                    updatePs.setInt(2, id);
                    updatePs.executeUpdate();
                }

                return new ChangePasswordResult(true, "Password updated successfully.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new ChangePasswordResult(false, "Database error: " + e.getMessage());
        }
    }
}
