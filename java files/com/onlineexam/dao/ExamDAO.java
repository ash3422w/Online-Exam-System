package com.onlineexam.dao;

import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DAO for per-exam configurations stored in the `exams` table.
 */
public class ExamDAO {

    public enum ExamAvailability {
        UPCOMING,
        OPEN,
        CLOSED,
        INACTIVE
    }

    public static class ExamAvailabilityResult {
        public final ExamAvailability status;
        public final boolean canStart;
        public final String reason;
        public final LocalDateTime startAt;
        public final LocalDateTime endAt;
        public final LocalDateTime serverNow;
        public final long remainingSeconds; // >0 until startAt (UPCOMING), >=0 until endAt (OPEN), -1 if no endAt (OPEN), 0 (CLOSED/INACTIVE)
        public final int attemptsUsed;
        public final Integer maxAttempts;
        public final ExamConfig exam;

        public ExamAvailabilityResult(
                ExamAvailability status, boolean canStart, String reason,
                LocalDateTime startAt, LocalDateTime endAt, LocalDateTime serverNow,
                long remainingSeconds, int attemptsUsed, Integer maxAttempts, ExamConfig exam) {
            this.status = status;
            this.canStart = canStart;
            this.reason = reason;
            this.startAt = startAt;
            this.endAt = endAt;
            this.serverNow = serverNow;
            this.remainingSeconds = remainingSeconds;
            this.attemptsUsed = attemptsUsed;
            this.maxAttempts = maxAttempts;
            this.exam = exam;
        }

        public boolean isAttemptLimitReached() {
            return maxAttempts != null && maxAttempts > 0 && attemptsUsed >= maxAttempts;
        }
    }

    private static final String BASE_SELECT =
            "SELECT e.id, e.subject_id, s.subject_name, e.title, e.description, "
            + "e.question_count, e.duration_minutes, e.marks_per_question, e.negative_marking, "
            + "e.pass_percentage, e.max_attempts, e.show_answers, e.start_at, e.end_at, "
            + "e.easy_pct, e.medium_pct, e.hard_pct, e.require_webcam, e.is_active, e.created_at "
            + "FROM exams e "
            + "LEFT JOIN subjects s ON e.subject_id = s.id ";

    public static List<ExamConfig> getActiveExams() {
        List<ExamConfig> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE e.is_active = 1 ORDER BY e.subject_id, e.id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapExam(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<ExamConfig> getAllExams() {
        List<ExamConfig> list = new ArrayList<>();
        String sql = BASE_SELECT + "ORDER BY e.is_active DESC, e.subject_id, e.id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapExam(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<ExamConfig> getExamsForSubject(int subjectId) {
        List<ExamConfig> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE e.subject_id = ? AND e.is_active = 1 ORDER BY e.id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExam(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static ExamConfig getById(int examId) {
        if (examId <= 0) return null;
        String sql = BASE_SELECT + "WHERE e.id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapExam(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int create(ExamConfig config) {
        if (config == null) return -1;
        String sql = "INSERT INTO exams(subject_id, title, description, question_count, duration_minutes, "
                + "marks_per_question, negative_marking, pass_percentage, max_attempts, show_answers, "
                + "start_at, end_at, is_active, easy_pct, medium_pct, hard_pct, require_webcam) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParams(ps, config);
            int affected = ps.executeUpdate();
            if (affected == 1) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int newId = keys.getInt(1);
                        config.id = newId;
                        return newId;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static boolean update(ExamConfig config) {
        if (config == null || config.id <= 0) return false;
        String sql = "UPDATE exams SET subject_id = ?, title = ?, description = ?, question_count = ?, "
                + "duration_minutes = ?, marks_per_question = ?, negative_marking = ?, pass_percentage = ?, "
                + "max_attempts = ?, show_answers = ?, start_at = ?, end_at = ?, is_active = ?, "
                + "easy_pct = ?, medium_pct = ?, hard_pct = ?, require_webcam = ? "
                + "WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindParams(ps, config);
            ps.setInt(18, config.id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deactivate(int examId) {
        return setActive(examId, false);
    }

    public static boolean setActive(int examId, boolean active) {
        if (examId <= 0) return false;
        String sql = "UPDATE exams SET is_active = ? WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, examId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int countAttemptsByUser(String userId, int examId) {
        if (userId == null || userId.trim().isEmpty() || examId <= 0) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM exam_attempts WHERE user_id = ? AND exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setInt(2, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static Map<Integer, Integer> getAttemptCountsByUser(String userId) {
        Map<Integer, Integer> counts = new HashMap<>();
        if (userId == null || userId.trim().isEmpty()) {
            return counts;
        }
        String sql = "SELECT exam_id, COUNT(*) AS cnt FROM exam_attempts "
                + "WHERE user_id = ? AND exam_id IS NOT NULL GROUP BY exam_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getInt("exam_id"), rs.getInt("cnt"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return counts;
    }

    public static int countUniqueQuestionsForSubject(int subjectId) {
        Set<String> keys = new HashSet<>();
        String sql = "SELECT question FROM questions WHERE subject_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String q = rs.getString("question");
                    if (q != null && !q.trim().isEmpty()) {
                        keys.add(q.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return keys.size();
    }

    public static Map<String, Integer> countUniqueQuestionsByDifficulty(int subjectId) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put(ExamStore.DIFFICULTY_EASY, 0);
        counts.put(ExamStore.DIFFICULTY_MEDIUM, 0);
        counts.put(ExamStore.DIFFICULTY_HARD, 0);

        Set<String> seenKeys = new HashSet<>();
        String sql = "SELECT question, difficulty FROM questions WHERE subject_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String q = rs.getString("question");
                    if (q == null || q.trim().isEmpty()) continue;
                    if (seenKeys.add(q.trim().toLowerCase(Locale.ROOT))) {
                        String diff = ExamStore.normalizeDifficulty(rs.getString("difficulty"));
                        counts.put(diff, counts.getOrDefault(diff, 0) + 1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return counts;
    }

    public static int countTotalAttemptsForExam(int examId) {
        if (examId <= 0) return 0;
        String sql = "SELECT COUNT(*) FROM exam_attempts WHERE exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static List<ExamConfig> getUpcomingExams(int limit) {
        List<ExamConfig> list = new ArrayList<>();
        int safeLimit = Math.max(1, limit);
        String sql = BASE_SELECT
                + "WHERE e.is_active = 1 AND e.start_at IS NOT NULL AND e.start_at > NOW() "
                + "ORDER BY e.start_at ASC LIMIT ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExam(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Checks exam availability against DB server time (never the PC clock).
     */
    public static ExamAvailabilityResult checkAvailability(int examId, String userId) {
        ExamConfig exam = getById(examId);
        LocalDateTime serverNow = DBConnection.getServerTime();
        int used = (userId != null && !userId.trim().isEmpty() && exam != null)
                ? countAttemptsByUser(userId, examId) : 0;
        if (serverNow == null) {
            return new ExamAvailabilityResult(
                    ExamAvailability.INACTIVE, false,
                    "Cannot verify database server time. Please check your connection.",
                    exam != null ? exam.startAt : null,
                    exam != null ? exam.endAt : null,
                    null, 0L, used, exam != null ? exam.maxAttempts : null, exam
            );
        }
        return evaluateAvailability(exam, serverNow, used, false);
    }

    /**
     * Authoritative start gate: verifies exam active status, start/end schedule against DB server time,
     * AND the user's retake attempt limit in one call.
     */
    public static ExamAvailabilityResult canStart(String userId, int examId) {
        ExamConfig exam = getById(examId);
        LocalDateTime serverNow = DBConnection.getServerTime();
        int used = (userId != null && !userId.trim().isEmpty() && exam != null)
                ? countAttemptsByUser(userId, examId) : 0;
        if (serverNow == null) {
            return new ExamAvailabilityResult(
                    ExamAvailability.INACTIVE, false,
                    "Cannot verify database server time. Exam cannot be started.",
                    exam != null ? exam.startAt : null,
                    exam != null ? exam.endAt : null,
                    null, 0L, used, exam != null ? exam.maxAttempts : null, exam
            );
        }
        return evaluateAvailability(exam, serverNow, used, true);
    }

    /**
     * Pure availability evaluator given a known server timestamp and user's attempt count.
     */
    public static ExamAvailabilityResult evaluateAvailability(
            ExamConfig exam, LocalDateTime serverNow, int attemptsUsed, boolean enforceAttemptLimit) {
        if (exam == null || !exam.active) {
            return new ExamAvailabilityResult(
                    ExamAvailability.INACTIVE, false,
                    "This exam is currently inactive or unavailable.",
                    exam != null ? exam.startAt : null,
                    exam != null ? exam.endAt : null,
                    serverNow, 0L, attemptsUsed, exam != null ? exam.maxAttempts : null, exam
            );
        }
        if (serverNow == null) {
            return new ExamAvailabilityResult(
                    ExamAvailability.INACTIVE, false,
                    "Database server time unavailable.",
                    exam.startAt, exam.endAt, null, 0L, attemptsUsed, exam.maxAttempts, exam
            );
        }

        if (exam.startAt != null && serverNow.isBefore(exam.startAt)) {
            long secs = Math.max(1L, Duration.between(serverNow, exam.startAt).getSeconds());
            String msg = "This exam has not started yet. Starts on "
                    + exam.startAt.format(ExamConfig.DISPLAY_DT)
                    + " (in " + ExamConfig.formatCountdown(secs, false) + ").";
            return new ExamAvailabilityResult(
                    ExamAvailability.UPCOMING, false, msg,
                    exam.startAt, exam.endAt, serverNow, secs, attemptsUsed, exam.maxAttempts, exam
            );
        }

        if (exam.endAt != null && !serverNow.isBefore(exam.endAt)) {
            String msg = "This exam closed on " + exam.endAt.format(ExamConfig.DISPLAY_DT) + ".";
            return new ExamAvailabilityResult(
                    ExamAvailability.CLOSED, false, msg,
                    exam.startAt, exam.endAt, serverNow, 0L, attemptsUsed, exam.maxAttempts, exam
            );
        }

        long remUntilEnd = (exam.endAt != null)
                ? Math.max(0L, Duration.between(serverNow, exam.endAt).getSeconds())
                : -1L;

        if (enforceAttemptLimit && exam.maxAttempts != null && exam.maxAttempts > 0 && attemptsUsed >= exam.maxAttempts) {
            String msg = "Attempt limit reached: you have used " + attemptsUsed + "/" + exam.maxAttempts
                    + " allowed attempt(s) for " + exam.title + ".";
            return new ExamAvailabilityResult(
                    ExamAvailability.OPEN, false, msg,
                    exam.startAt, exam.endAt, serverNow, remUntilEnd, attemptsUsed, exam.maxAttempts, exam
            );
        }

        return new ExamAvailabilityResult(
                ExamAvailability.OPEN, true, "Exam is open.",
                exam.startAt, exam.endAt, serverNow, remUntilEnd, attemptsUsed, exam.maxAttempts, exam
        );
    }

    private static void bindParams(PreparedStatement ps, ExamConfig c) throws SQLException {
        ps.setInt(1, c.subjectId);
        ps.setString(2, c.title == null ? "" : c.title.trim());
        if (c.description == null || c.description.trim().isEmpty()) {
            ps.setNull(3, Types.VARCHAR);
        } else {
            ps.setString(3, c.description.trim());
        }
        ps.setInt(4, Math.max(1, c.questionCount));
        if (c.durationMinutes == null || c.durationMinutes <= 0) {
            ps.setNull(5, Types.INTEGER);
        } else {
            ps.setInt(5, c.durationMinutes);
        }
        ps.setBigDecimal(6, BigDecimal.valueOf(Math.max(0.01, c.marksPerQuestion)));
        ps.setBigDecimal(7, BigDecimal.valueOf(Math.max(0.0, c.negativeMarking)));
        ps.setBigDecimal(8, BigDecimal.valueOf(Math.max(0.0, Math.min(100.0, c.passPercentage))));
        if (c.maxAttempts == null || c.maxAttempts <= 0) {
            ps.setNull(9, Types.INTEGER);
        } else {
            ps.setInt(9, c.maxAttempts);
        }
        String policy = (c.showAnswers == null || c.showAnswers.trim().isEmpty())
                ? ExamConfig.SHOW_IMMEDIATELY : c.showAnswers.trim().toUpperCase(Locale.ROOT);
        ps.setString(10, policy);
        if (c.startAt == null) {
            ps.setNull(11, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(11, Timestamp.valueOf(c.startAt));
        }
        if (c.endAt == null) {
            ps.setNull(12, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(12, Timestamp.valueOf(c.endAt));
        }
        ps.setInt(13, c.active ? 1 : 0);
        if (c.hasDifficultyMix()) {
            ps.setInt(14, c.easyPct);
            ps.setInt(15, c.mediumPct);
            ps.setInt(16, c.hardPct);
        } else {
            ps.setInt(14, 0);
            ps.setInt(15, 0);
            ps.setInt(16, 0);
        }
        ps.setInt(17, c.requireWebcam ? 1 : 0);
    }

    private static ExamConfig mapExam(ResultSet rs) throws SQLException {
        ExamConfig c = new ExamConfig();
        c.id = rs.getInt("id");
        c.subjectId = rs.getInt("subject_id");
        String sName = rs.getString("subject_name");
        c.subjectName = (sName != null && !sName.trim().isEmpty()) ? sName.trim() : ExamStore.getSubjectName(c.subjectId);
        c.title = rs.getString("title");
        c.description = rs.getString("description");
        c.questionCount = rs.getInt("question_count");

        int dur = rs.getInt("duration_minutes");
        c.durationMinutes = rs.wasNull() ? null : dur;

        BigDecimal mpq = rs.getBigDecimal("marks_per_question");
        c.marksPerQuestion = mpq != null ? mpq.doubleValue() : 1.0;

        BigDecimal neg = rs.getBigDecimal("negative_marking");
        c.negativeMarking = neg != null ? neg.doubleValue() : 0.0;

        BigDecimal pass = rs.getBigDecimal("pass_percentage");
        c.passPercentage = pass != null ? pass.doubleValue() : 40.0;

        int maxAtt = rs.getInt("max_attempts");
        c.maxAttempts = rs.wasNull() ? null : maxAtt;

        String show = rs.getString("show_answers");
        c.showAnswers = (show == null || show.trim().isEmpty()) ? ExamConfig.SHOW_IMMEDIATELY : show.trim();

        Timestamp st = rs.getTimestamp("start_at");
        c.startAt = st != null ? st.toLocalDateTime() : null;

        Timestamp en = rs.getTimestamp("end_at");
        c.endAt = en != null ? en.toLocalDateTime() : null;

        c.easyPct = rs.getInt("easy_pct");
        c.mediumPct = rs.getInt("medium_pct");
        c.hardPct = rs.getInt("hard_pct");

        try {
            c.requireWebcam = rs.getInt("require_webcam") == 1;
        } catch (SQLException ignored) {
            c.requireWebcam = false;
        }

        c.active = rs.getInt("is_active") == 1;
        Timestamp cr = rs.getTimestamp("created_at");
        c.createdAt = cr != null ? cr.toLocalDateTime().format(ExamConfig.DISPLAY_DT) : "-";
        return c;
    }
}
