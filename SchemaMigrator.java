package com.onlineexam.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Idempotent MySQL 8 schema migrator.
 * Checks INFORMATION_SCHEMA before creating tables or adding columns so existing
 * databases upgrade safely across repeated application startups.
 */
public final class SchemaMigrator {

    private static final Pattern CREATE_TABLE_NAME = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?"
    );

    private SchemaMigrator() {
    }

    /**
     * Runs all idempotent schema migrations against the configured MySQL database.
     * Logs each applied change to stdout; remains silent when schema is already up to date.
     * Fails with a clear error message if the database connection cannot be established.
     */
    public static void run() {
        try (Connection con = DBConnection.getConnection()) {
            // Track applied migrations metadata (created once; silent on subsequent startups)
            createTableIfMissing(con,
                    "CREATE TABLE schema_migrations ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "migration_key VARCHAR(120) NOT NULL UNIQUE, "
                    + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Task 1: Per-question explanations
            if (tableExists(con, "questions")) {
                addColumnIfMissing(con, "questions", "explanation", "TEXT NULL");
                // Task 4: Per-question difficulty
                addColumnIfMissing(con, "questions", "difficulty", "VARCHAR(10) NOT NULL DEFAULT 'MEDIUM'");
                // Task 5: Multi-type questions & optional image
                addColumnIfMissing(con, "questions", "question_type", "VARCHAR(12) NOT NULL DEFAULT 'SINGLE'");
                addColumnIfMissing(con, "questions", "correct_options", "VARCHAR(20) NULL");
                addColumnIfMissing(con, "questions", "correct_text", "VARCHAR(500) NULL");
                addColumnIfMissing(con, "questions", "image_data", "MEDIUMBLOB NULL");
            }

            // Task 9: User profile full_name and created_at
            if (tableExists(con, "users")) {
                addColumnIfMissing(con, "users", "full_name", "VARCHAR(100) NULL");
                addColumnIfMissing(con, "users", "created_at", "TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
            }

            // Task 2: Real exams table + per-exam attempt scoring columns + default exam seeding
            createTableIfMissing(con,
                    "CREATE TABLE exams ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "subject_id INT NOT NULL, "
                    + "title VARCHAR(150) NOT NULL, "
                    + "description VARCHAR(500) NULL, "
                    + "question_count INT NOT NULL DEFAULT 20, "
                    + "duration_minutes INT NULL, "
                    + "marks_per_question DECIMAL(5,2) NOT NULL DEFAULT 1.00, "
                    + "negative_marking DECIMAL(5,2) NOT NULL DEFAULT 0.00, "
                    + "pass_percentage DECIMAL(5,2) NOT NULL DEFAULT 40.00, "
                    + "max_attempts INT NULL, "
                    + "show_answers VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATELY', "
                    + "start_at DATETIME NULL, "
                    + "end_at DATETIME NULL, "
                    + "is_active TINYINT(1) NOT NULL DEFAULT 1, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "CONSTRAINT fk_exams_subject FOREIGN KEY (subject_id) REFERENCES subjects(id) "
                    + "ON DELETE CASCADE ON UPDATE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            if (tableExists(con, "exam_attempts")) {
                addColumnIfMissing(con, "exam_attempts", "exam_id", "INT NULL");
                addColumnIfMissing(con, "exam_attempts", "marks_obtained", "DECIMAL(7,2) NULL");
                addColumnIfMissing(con, "exam_attempts", "total_marks", "DECIMAL(7,2) NULL");
                addColumnIfMissing(con, "exam_attempts", "passed", "TINYINT(1) NULL");
            }

            // Task 5: Multi-select & fill-in-blank answer columns on exam_attempt_answers
            if (tableExists(con, "exam_attempt_answers")) {
                addColumnIfMissing(con, "exam_attempt_answers", "selected_options", "VARCHAR(20) NULL");
                addColumnIfMissing(con, "exam_attempt_answers", "answer_text", "VARCHAR(500) NULL");
            }

            // Task 4: Per-exam difficulty mix percentages (all 0 = legacy random)
            if (tableExists(con, "exams")) {
                addColumnIfMissing(con, "exams", "easy_pct", "INT NOT NULL DEFAULT 0");
                addColumnIfMissing(con, "exams", "medium_pct", "INT NOT NULL DEFAULT 0");
                addColumnIfMissing(con, "exams", "hard_pct", "INT NOT NULL DEFAULT 0");
                // Task 12: Per-exam webcam proctoring requirement
                addColumnIfMissing(con, "exams", "require_webcam", "TINYINT(1) NOT NULL DEFAULT 0");
            }

            // Task 7: Active student session tracking (single-exam-device enforcement)
            createTableIfMissing(con,
                    "CREATE TABLE active_sessions ("
                    + "user_id VARCHAR(100) PRIMARY KEY, "
                    + "session_token VARCHAR(64) NOT NULL, "
                    + "machine_info VARCHAR(200) NULL, "
                    + "exam_id INT NULL, "
                    + "in_exam TINYINT(1) NOT NULL DEFAULT 0, "
                    + "login_at DATETIME NOT NULL, "
                    + "last_heartbeat DATETIME NOT NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Task 8: Proctoring audit log events
            createTableIfMissing(con,
                    "CREATE TABLE proctor_events ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "exam_session_id VARCHAR(36) NOT NULL, "
                    + "user_id VARCHAR(100) NOT NULL, "
                    + "exam_id INT NULL, "
                    + "attempt_id INT NULL, "
                    + "event_type VARCHAR(40) NOT NULL, "
                    + "details VARCHAR(255) NULL, "
                    + "created_at DATETIME NOT NULL, "
                    + "INDEX idx_proctor_user (user_id), "
                    + "INDEX idx_proctor_session (exam_session_id), "
                    + "INDEX idx_proctor_attempt (attempt_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Task 10: Achievements and user earned badges
            createTableIfMissing(con,
                    "CREATE TABLE achievements ("
                    + "code VARCHAR(40) PRIMARY KEY, "
                    + "name VARCHAR(80) NOT NULL, "
                    + "description VARCHAR(200) NOT NULL, "
                    + "icon_key VARCHAR(20) NOT NULL, "
                    + "sort_order INT NOT NULL DEFAULT 0"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            createTableIfMissing(con,
                    "CREATE TABLE user_achievements ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "user_id VARCHAR(100) NOT NULL, "
                    + "code VARCHAR(40) NOT NULL, "
                    + "attempt_id INT NULL, "
                    + "earned_at DATETIME NOT NULL, "
                    + "UNIQUE KEY uq_user_code (user_id, code), "
                    + "INDEX idx_user_ach_user (user_id), "
                    + "INDEX idx_user_ach_code (code)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            seedAchievementsCatalog(con);

            // Task 11: Verifiable certificates table
            createTableIfMissing(con,
                    "CREATE TABLE certificates ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "cert_code VARCHAR(24) NOT NULL UNIQUE, "
                    + "attempt_id INT NOT NULL UNIQUE, "
                    + "user_id VARCHAR(100) NOT NULL, "
                    + "issued_at DATETIME NOT NULL, "
                    + "INDEX idx_cert_code (cert_code), "
                    + "INDEX idx_cert_attempt (attempt_id), "
                    + "INDEX idx_cert_user (user_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Task 12: Webcam proctoring snapshots table
            createTableIfMissing(con,
                    "CREATE TABLE proctor_snapshots ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "exam_session_id VARCHAR(36) NOT NULL, "
                    + "user_id VARCHAR(100) NOT NULL, "
                    + "taken_at DATETIME NOT NULL, "
                    + "image_data MEDIUMBLOB NOT NULL, "
                    + "flag VARCHAR(40) NULL, "
                    + "attempt_id INT NULL, "
                    + "INDEX idx_snap_session (exam_session_id), "
                    + "INDEX idx_snap_user (user_id), "
                    + "INDEX idx_snap_taken (taken_at), "
                    + "INDEX idx_snap_attempt (attempt_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            seedDefaultExamsForSubjects(con);

            // Task 12: Retention cleanup of snapshots older than N days at startup
            try {
                int purged = ProctorDAO.purgeOldSnapshots(ProctorDAO.getSnapshotRetentionDays());
                if (purged > 0) {
                    System.out.println("[SchemaMigrator] Purged " + purged + " proctor snapshots older than retention limit.");
                }
            } catch (Exception ex) {
                System.err.println("[SchemaMigrator] Snapshot retention purge warning: " + ex.getMessage());
            }
        } catch (SQLException e) {
            String msg = "[SchemaMigrator] FATAL: Could not connect to MySQL or apply schema migrations: " + e.getMessage();
            System.err.println(msg);
            throw new IllegalStateException(msg, e);
        }
    }

    // ================= INFORMATION_SCHEMA HELPERS =================

    public static boolean tableExists(String tableName) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return tableExists(con, tableName);
        }
    }

    public static boolean tableExists(Connection con, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return columnExists(con, tableName, columnName);
        }
    }

    public static boolean columnExists(Connection con, String tableName, String columnName) throws SQLException {
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean addColumnIfMissing(String table, String column, String definition) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return addColumnIfMissing(con, table, column, definition);
        }
    }

    public static boolean addColumnIfMissing(Connection con, String table, String column, String definition) throws SQLException {
        if (!tableExists(con, table)) {
            throw new SQLException("Cannot add column '" + column + "': table '" + table + "' does not exist.");
        }
        if (columnExists(con, table, column)) {
            return false;
        }
        String ddl = "ALTER TABLE `" + sanitizeIdentifier(table) + "` ADD COLUMN `"
                + sanitizeIdentifier(column) + "` " + definition;
        try (Statement st = con.createStatement()) {
            st.executeUpdate(ddl);
        }
        System.out.println("[SchemaMigrator] Added column " + table + "." + column + " (" + definition + ")");
        return true;
    }

    public static boolean createTableIfMissing(String ddl) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return createTableIfMissing(con, ddl);
        }
    }

    public static boolean createTableIfMissing(Connection con, String ddl) throws SQLException {
        String tableName = extractTableName(ddl);
        if (tableName == null || tableName.isEmpty()) {
            throw new SQLException("Could not parse table name from DDL: " + ddl);
        }
        if (tableExists(con, tableName)) {
            return false;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(ddl);
        }
        System.out.println("[SchemaMigrator] Created table " + tableName);
        return true;
    }

    private static void seedAchievementsCatalog(Connection con) throws SQLException {
        if (!tableExists(con, "achievements")) {
            return;
        }
        String sql = "INSERT IGNORE INTO achievements (code, name, description, icon_key, sort_order) VALUES (?, ?, ?, ?, ?)";
        Object[][] catalog = {
            {"FIRST_EXAM", "First Step", "Finish your first exam", "medal", 1},
            {"FIRST_90", "High Achiever", "Score 90% or more on an exam", "star", 2},
            {"PERFECT_SCORE", "Perfectionist", "Score a perfect 100% on an exam", "trophy", 3},
            {"FIVE_IN_WEEK", "Dedicated Learner", "Complete 5 exams within a 7-day period", "calendar", 4},
            {"STREAK_3", "Consistent Spark", "Maintain a 3-day exam streak", "flame", 5},
            {"STREAK_7", "Week on Fire", "Maintain a 7-day exam streak", "flame", 6},
            {"STREAK_30", "Unstoppable Force", "Maintain a 30-day exam streak", "flame", 7},
            {"ALL_SUBJECTS", "Well-Rounded", "Attempt an exam in every subject", "target", 8},
            {"COMEBACK", "Great Comeback", "Improve by 20+ percentage points on a retake of the same exam", "bolt", 9},
            {"SPEED_RUNNER", "Speed Runner", "Score 80%+ using under half the allocated time", "bolt", 10},
            {"CONSISTENT", "Unwavering Excellence", "Pass 5 exams in a row", "medal", 11}
        };

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Object[] row : catalog) {
                ps.setString(1, (String) row[0]);
                ps.setString(2, (String) row[1]);
                ps.setString(3, (String) row[2]);
                ps.setString(4, (String) row[3]);
                ps.setInt(5, (Integer) row[4]);
                ps.executeUpdate();
            }
        }
        System.out.println("[SchemaMigrator] Seeded achievements catalog (11 badges).");
    }

    private static void seedDefaultExamsForSubjects(Connection con) throws SQLException {
        if (!tableExists(con, "subjects") || !tableExists(con, "exams")) {
            return;
        }
        String findSql = "SELECT s.id, s.subject_name FROM subjects s "
                + "WHERE NOT EXISTS (SELECT 1 FROM exams e WHERE e.subject_id = s.id) "
                + "ORDER BY s.id";
        String insertSql = "INSERT INTO exams(subject_id, title, description, question_count, duration_minutes, "
                + "marks_per_question, negative_marking, pass_percentage, max_attempts, show_answers, is_active) "
                + "VALUES (?, ?, ?, 20, NULL, 1.00, 0.00, 40.00, NULL, 'IMMEDIATELY', 1)";

        try (PreparedStatement findPs = con.prepareStatement(findSql);
             ResultSet rs = findPs.executeQuery();
             PreparedStatement insPs = con.prepareStatement(insertSql)) {
            while (rs.next()) {
                int sid = rs.getInt("id");
                String sname = rs.getString("subject_name");
                String title = (sname != null && !sname.trim().isEmpty()) ? (sname.trim() + " Exam") : ("Subject " + sid + " Exam");
                String desc = "Standard " + (sname != null ? sname.trim() : "") + " MCQ examination.";
                insPs.setInt(1, sid);
                insPs.setString(2, title);
                insPs.setString(3, desc);
                insPs.executeUpdate();
                System.out.println("[SchemaMigrator] Seeded default exam '" + title + "' (subject_id=" + sid + ")");
            }
        }
    }

    private static String extractTableName(String ddl) {
        if (ddl == null) return null;
        Matcher m = CREATE_TABLE_NAME.matcher(ddl);
        return m.find() ? m.group(1) : null;
    }

    private static String sanitizeIdentifier(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + name);
        }
        return name;
    }
}
