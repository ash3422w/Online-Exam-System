package com.onlineexam.dao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service for issuing and verifying tamper-evident examination certificates.
 * Code format: CERT-YYYY-XXXXXXXX (8 random unambiguous characters, no 0/O/1/I).
 * Only PASSED attempts are eligible.
 */
public final class CertificateService {

    private static final String UNAMBIGUOUS_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CertificateService() {
    }

    public static class CertificateRecord {
        public final int id;
        public final String certCode;
        public final int attemptId;
        public final String userId;
        public final Timestamp issuedAt;

        public CertificateRecord(int id, String certCode, int attemptId, String userId, Timestamp issuedAt) {
            this.id = id;
            this.certCode = certCode;
            this.attemptId = attemptId;
            this.userId = userId;
            this.issuedAt = issuedAt;
        }

        public String getFormattedIssueDate() {
            if (issuedAt == null) return "";
            try {
                return issuedAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
            } catch (Exception ex) {
                return issuedAt.toString();
            }
        }
    }

    public static class VerificationResult {
        public final boolean isValid;
        public final String certCode;
        public final String studentName;
        public final String username;
        public final String examTitle;
        public final String subjectName;
        public final String scoreText;
        public final String percentageText;
        public final String grade;
        public final String examDate;
        public final String issueDate;

        public VerificationResult(boolean isValid, String certCode, String studentName, String username,
                                  String examTitle, String subjectName, String scoreText,
                                  String percentageText, String grade, String examDate, String issueDate) {
            this.isValid = isValid;
            this.certCode = certCode;
            this.studentName = studentName;
            this.username = username;
            this.examTitle = examTitle;
            this.subjectName = subjectName;
            this.scoreText = scoreText;
            this.percentageText = percentageText;
            this.grade = grade;
            this.examDate = examDate;
            this.issueDate = issueDate;
        }

        public static VerificationResult notFound(String searchedCode) {
            return new VerificationResult(false, searchedCode, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Retrieves an existing certificate for the given attemptId, or creates a new one
     * if the attempt was PASSED. Idempotent: repeated calls for the same attempt return the same code.
     * Returns null if the attempt is not eligible (failed or not found).
     */
    public static CertificateRecord getOrCreateCertificate(int attemptId) {
        if (attemptId <= 0) return null;

        try (Connection con = DBConnection.getConnection()) {
            // 1. Check if certificate already exists
            String existingSql = "SELECT id, cert_code, attempt_id, user_id, issued_at FROM certificates WHERE attempt_id = ?";
            try (PreparedStatement ps = con.prepareStatement(existingSql)) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new CertificateRecord(
                                rs.getInt("id"),
                                rs.getString("cert_code"),
                                rs.getInt("attempt_id"),
                                rs.getString("user_id"),
                                rs.getTimestamp("issued_at")
                        );
                    }
                }
            }

            // 2. Verify attempt eligibility (must exist and be PASSED)
            String attemptSql = "SELECT a.id, a.user_id, a.score, a.total_questions, a.marks_obtained, a.total_marks, "
                    + "a.passed, a.attempted_at, e.pass_percentage "
                    + "FROM exam_attempts a "
                    + "LEFT JOIN exams e ON a.exam_id = e.id "
                    + "WHERE a.id = ?";
            String attemptUserId = null;
            boolean passed = false;
            Timestamp attemptedAt = null;

            try (PreparedStatement ps = con.prepareStatement(attemptSql)) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null; // Attempt not found
                    }
                    attemptUserId = rs.getString("user_id");
                    attemptedAt = rs.getTimestamp("attempted_at");

                    int score = rs.getInt("score");
                    int totalQ = rs.getInt("total_questions");
                    if (totalQ <= 0) totalQ = 20;
                    java.math.BigDecimal mo = rs.getBigDecimal("marks_obtained");
                    java.math.BigDecimal tm = rs.getBigDecimal("total_marks");
                    double marks = mo != null ? mo.doubleValue() : score;
                    double totalM = (tm != null && tm.doubleValue() > 0) ? tm.doubleValue() : totalQ;
                    double pct = (marks * 100.0) / totalM;

                    double passPct = rs.getDouble("pass_percentage");
                    if (rs.wasNull()) passPct = 40.0;

                    int pVal = rs.getInt("passed");
                    if (!rs.wasNull()) {
                        passed = (pVal == 1);
                    } else {
                        passed = (pct >= passPct);
                    }
                }
            }

            if (!passed || attemptUserId == null) {
                return null; // Failed or ineligible attempt
            }

            // 3. Generate unique CERT-YYYY-XXXXXXXX code and insert
            int year = Year.now().getValue();
            if (attemptedAt != null) {
                year = attemptedAt.toLocalDateTime().getYear();
            }

            String insertSql = "INSERT INTO certificates (cert_code, attempt_id, user_id, issued_at) VALUES (?, ?, ?, NOW())";
            for (int attempt = 0; attempt < 10; attempt++) {
                String candidateCode = generateCode(year);
                try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, candidateCode);
                    ps.setInt(2, attemptId);
                    ps.setString(3, attemptUserId);
                    ps.executeUpdate();

                    try (ResultSet rsKeys = ps.getGeneratedKeys()) {
                        if (rsKeys.next()) {
                            int generatedId = rsKeys.getInt(1);
                            return new CertificateRecord(
                                    generatedId,
                                    candidateCode,
                                    attemptId,
                                    attemptUserId,
                                    new Timestamp(System.currentTimeMillis())
                            );
                        }
                    }
                } catch (SQLException ex) {
                    // Check if duplicate attempt_id raced concurrently
                    try (PreparedStatement checkPs = con.prepareStatement(existingSql)) {
                        checkPs.setInt(1, attemptId);
                        try (ResultSet rs = checkPs.executeQuery()) {
                            if (rs.next()) {
                                return new CertificateRecord(
                                        rs.getInt("id"),
                                        rs.getString("cert_code"),
                                        rs.getInt("attempt_id"),
                                        rs.getString("user_id"),
                                        rs.getTimestamp("issued_at")
                                );
                            }
                        }
                    }
                    // Otherwise collision on random code: retry loop
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Checks if a certificate has already been issued for this attempt.
     */
    public static CertificateRecord getCertificateByAttempt(int attemptId) {
        if (attemptId <= 0) return null;
        String sql = "SELECT id, cert_code, attempt_id, user_id, issued_at FROM certificates WHERE attempt_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CertificateRecord(
                            rs.getInt("id"),
                            rs.getString("cert_code"),
                            rs.getInt("attempt_id"),
                            rs.getString("user_id"),
                            rs.getTimestamp("issued_at")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Verifies a certificate code against the database.
     * Only reads from certificates joined to attempts/exams/subjects/users and exposes
     * strictly candidate name, exam title, score/grade and dates.
     */
    public static VerificationResult verifyCertificate(String certCode) {
        if (certCode == null || certCode.trim().isEmpty()) {
            return VerificationResult.notFound(certCode);
        }
        String cleanCode = certCode.trim().toUpperCase(Locale.ENGLISH);

        String sql = "SELECT c.cert_code, c.issued_at, c.user_id, "
                + "COALESCE(u.full_name, c.user_id) AS student_name, "
                + "COALESCE(e.title, CONCAT(s.subject_name, ' Exam')) AS exam_title, "
                + "s.subject_name, "
                + "a.score, a.total_questions, a.marks_obtained, a.total_marks, "
                + "a.attempted_at, a.passed "
                + "FROM certificates c "
                + "JOIN exam_attempts a ON c.attempt_id = a.id "
                + "LEFT JOIN exams e ON a.exam_id = e.id "
                + "LEFT JOIN subjects s ON a.subject_id = s.id "
                + "LEFT JOIN users u ON c.user_id = u.username "
                + "WHERE c.cert_code = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cleanCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String foundCode = rs.getString("cert_code");
                    String studentName = rs.getString("student_name");
                    String username = rs.getString("user_id");
                    String examTitle = rs.getString("exam_title");
                    String subjName = rs.getString("subject_name");
                    Timestamp issuedAt = rs.getTimestamp("issued_at");
                    Timestamp attemptedAt = rs.getTimestamp("attempted_at");

                    int score = rs.getInt("score");
                    int totalQ = rs.getInt("total_questions");
                    if (totalQ <= 0) totalQ = 20;

                    java.math.BigDecimal mo = rs.getBigDecimal("marks_obtained");
                    java.math.BigDecimal tm = rs.getBigDecimal("total_marks");
                    double marks = (mo != null) ? mo.doubleValue() : score;
                    double totalMarks = (tm != null && tm.doubleValue() > 0) ? tm.doubleValue() : totalQ;

                    double pct = Math.min(100.0, Math.max(0.0, (marks * 100.0) / totalMarks));
                    String grade = AttemptDAO.calculateGrade(pct);

                    String scoreText = (mo != null && tm != null)
                            ? String.format(Locale.ENGLISH, "%.1f / %.1f Marks", marks, totalMarks)
                            : (score + " / " + totalQ + " Questions");
                    String pctText = String.format(Locale.ENGLISH, "%.1f%%", pct);

                    String issueDateStr = (issuedAt != null)
                            ? issuedAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
                            : "-";
                    String examDateStr = (attemptedAt != null)
                            ? attemptedAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
                            : "-";

                    return new VerificationResult(
                            true,
                            foundCode,
                            studentName,
                            username,
                            examTitle,
                            subjName,
                            scoreText,
                            pctText,
                            grade,
                            examDateStr,
                            issueDateStr
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return VerificationResult.notFound(cleanCode);
    }

    private static String generateCode(int year) {
        StringBuilder sb = new StringBuilder(24);
        sb.append("CERT-").append(year).append("-");
        for (int i = 0; i < 8; i++) {
            int idx = RANDOM.nextInt(UNAMBIGUOUS_CHARS.length());
            sb.append(UNAMBIGUOUS_CHARS.charAt(idx));
        }
        return sb.toString();
    }
}
