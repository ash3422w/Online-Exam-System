package com.onlineexam.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Data Access Object for proctoring audit log events and risk analysis.
 * Event writes and session-to-attempt links run on a single-thread background
 * daemon executor to prevent any EDT or exam loop latency and guarantee ordering.
 */
public final class ProctorDAO {

    public static final String EVENT_FOCUS_LOST = "FOCUS_LOST";
    public static final String EVENT_WINDOW_MINIMIZED = "WINDOW_MINIMIZED";
    public static final String EVENT_COPY_ATTEMPT = "COPY_ATTEMPT";
    public static final String EVENT_PASTE_ATTEMPT = "PASTE_ATTEMPT";
    public static final String EVENT_CUT_ATTEMPT = "CUT_ATTEMPT";
    public static final String EVENT_RIGHT_CLICK = "RIGHT_CLICK";
    public static final String EVENT_PRINT_SCREEN_KEY = "PRINT_SCREEN_KEY";
    public static final String EVENT_AUTO_SUBMIT_PROCTOR = "AUTO_SUBMIT_PROCTOR";
    public static final String EVENT_CAMERA_LOST = "CAMERA_LOST";
    public static final String EVENT_EXAM_STARTED = "EXAM_STARTED";
    public static final String EVENT_EXAM_SUBMITTED = "EXAM_SUBMITTED";

    public static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum RiskLevel {
        LOW("Low", 1),
        MEDIUM("Medium", 2),
        HIGH("High", 3);

        private final String display;
        private final int severity;

        RiskLevel(String display, int severity) {
            this.display = display;
            this.severity = severity;
        }

        public String getDisplay() {
            return display;
        }

        public int getSeverity() {
            return severity;
        }
    }

    public static class RiskSummary {
        public final RiskLevel riskLevel;
        public final int totalViolations;
        public final boolean autoSubmitted;

        public RiskSummary(RiskLevel riskLevel, int totalViolations, boolean autoSubmitted) {
            this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.LOW;
            this.totalViolations = Math.max(0, totalViolations);
            this.autoSubmitted = autoSubmitted;
        }
    }

    public static class ProctorEvent {
        public final int id;
        public final String examSessionId;
        public final String userId;
        public final Integer examId;
        public final String examTitle;
        public final Integer attemptId;
        public final String eventType;
        public final String details;
        public final LocalDateTime createdAt;
        public final String formattedTime;

        public ProctorEvent(int id, String examSessionId, String userId, Integer examId,
                            String examTitle, Integer attemptId, String eventType,
                            String details, LocalDateTime createdAt) {
            this.id = id;
            this.examSessionId = examSessionId;
            this.userId = userId != null ? userId : "Unknown";
            this.examId = examId;
            this.examTitle = (examTitle != null && !examTitle.trim().isEmpty()) ? examTitle : (examId != null ? "Exam #" + examId : "General Exam");
            this.attemptId = attemptId;
            this.eventType = eventType != null ? eventType : "UNKNOWN";
            this.details = details != null ? details : "";
            this.createdAt = createdAt;
            this.formattedTime = createdAt != null ? createdAt.format(DATE_TIME_FMT) : "";
        }
    }

    public static class AttemptProctorSummary {
        public final String examSessionId;
        public final Integer attemptId;
        public final String userId;
        public final Integer examId;
        public final String examTitle;
        public final int focusLostCount;
        public final int windowMinimizedCount;
        public final int copyPasteCount;
        public final int rightClickCount;
        public final int printScreenCount;
        public final int autoSubmitCount;
        public final int totalViolations;
        public final boolean autoSubmitted;
        public final RiskLevel riskLevel;
        public final String startTime;
        public final String endTime;

        public AttemptProctorSummary(String examSessionId, Integer attemptId, String userId,
                                     Integer examId, String examTitle, int focusLostCount,
                                     int windowMinimizedCount, int copyPasteCount,
                                     int rightClickCount, int printScreenCount,
                                     int autoSubmitCount, int totalViolations,
                                     boolean autoSubmitted, RiskLevel riskLevel,
                                     String startTime, String endTime) {
            this.examSessionId = examSessionId;
            this.attemptId = attemptId;
            this.userId = userId;
            this.examId = examId;
            this.examTitle = examTitle;
            this.focusLostCount = focusLostCount;
            this.windowMinimizedCount = windowMinimizedCount;
            this.copyPasteCount = copyPasteCount;
            this.rightClickCount = rightClickCount;
            this.printScreenCount = printScreenCount;
            this.autoSubmitCount = autoSubmitCount;
            this.totalViolations = totalViolations;
            this.autoSubmitted = autoSubmitted;
            this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.LOW;
            this.startTime = startTime != null ? startTime : "";
            this.endTime = endTime != null ? endTime : "";
        }
    }

    public static class ProctorSnapshot {
        public final int id;
        public final String examSessionId;
        public final String userId;
        public final Integer attemptId;
        public final LocalDateTime takenAt;
        public final String formattedTime;
        public final byte[] imageData;
        public final String flag;

        public ProctorSnapshot(int id, String examSessionId, String userId, Integer attemptId,
                               LocalDateTime takenAt, byte[] imageData, String flag) {
            this.id = id;
            this.examSessionId = examSessionId;
            this.userId = userId != null ? userId : "Unknown";
            this.attemptId = attemptId;
            this.takenAt = takenAt;
            this.formattedTime = takenAt != null ? takenAt.format(DATE_TIME_FMT) : "";
            this.imageData = imageData;
            this.flag = (flag != null && !flag.trim().isEmpty()) ? flag.trim() : "PERIODIC";
        }
    }

    /** Single-thread daemon executor ensuring serial chronological inserts and attempt linking */
    private static final ExecutorService ASYNC_WRITER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "proctor-log-writer");
            t.setDaemon(true);
            return t;
        }
    });

    private ProctorDAO() {
    }

    /**
     * Computes the RiskLevel based on the proctoring rules:
     * - Low: 0-1 violations
     * - Medium: 2-4 violations
     * - High: 5+ violations OR any AUTO_SUBMIT_PROCTOR
     */
    public static RiskLevel computeRiskLevel(int totalViolations, boolean hasAutoSubmit) {
        if (hasAutoSubmit || totalViolations >= 5) {
            return RiskLevel.HIGH;
        }
        if (totalViolations >= 2) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    public static boolean isViolationEvent(String eventType) {
        if (eventType == null) return false;
        return !EVENT_EXAM_STARTED.equalsIgnoreCase(eventType)
                && !EVENT_EXAM_SUBMITTED.equalsIgnoreCase(eventType);
    }

    /**
     * Logs an audit event asynchronously. Never blocks the EDT or exam execution.
     */
    public static void logEvent(final String examSessionId, final String userId,
                                final Integer examId, final Integer attemptId,
                                final String eventType, final String details) {
        if (examSessionId == null || examSessionId.trim().isEmpty()) {
            return;
        }
        ASYNC_WRITER.submit(() -> {
            String sql = "INSERT INTO proctor_events (exam_session_id, user_id, exam_id, attempt_id, event_type, details, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, NOW())";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                String safeSessId = examSessionId;
                if (safeSessId != null && safeSessId.length() > 36) {
                    safeSessId = safeSessId.substring(0, 36);
                }
                ps.setString(1, safeSessId);
                ps.setString(2, (userId != null && !userId.trim().isEmpty()) ? userId.trim() : "Unknown");
                if (examId != null && examId > 0) {
                    ps.setInt(3, examId);
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                if (attemptId != null && attemptId > 0) {
                    ps.setInt(4, attemptId);
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                ps.setString(5, (eventType != null && !eventType.trim().isEmpty()) ? eventType.trim() : "UNKNOWN");
                String safeDetails = details;
                if (safeDetails != null && safeDetails.length() > 250) {
                    safeDetails = safeDetails.substring(0, 250);
                }
                ps.setString(6, safeDetails);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ProctorDAO] Failed to write proctor log event: " + e.getMessage());
            }
        });
    }

    /**
     * Links all events recorded under the given examSessionId to the newly generated attemptId.
     * Queued on the same single-thread executor to guarantee all preceding logEvent tasks have inserted.
     */
    public static void linkSessionToAttempt(final String examSessionId, final int attemptId) {
        if (examSessionId == null || examSessionId.trim().isEmpty() || attemptId <= 0) {
            return;
        }
        ASYNC_WRITER.submit(() -> {
            String sqlEvents = "UPDATE proctor_events SET attempt_id = ? WHERE exam_session_id = ? AND attempt_id IS NULL";
            String sqlSnaps = "UPDATE proctor_snapshots SET attempt_id = ? WHERE exam_session_id = ? AND attempt_id IS NULL";
            try (Connection con = DBConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement(sqlEvents)) {
                    ps.setInt(1, attemptId);
                    ps.setString(2, examSessionId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(sqlSnaps)) {
                    ps.setInt(1, attemptId);
                    ps.setString(2, examSessionId);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                System.err.println("[ProctorDAO] Failed to link session " + examSessionId + " to attempt " + attemptId + ": " + e.getMessage());
            }
        });
    }

    /**
     * Saves a webcam proctor snapshot asynchronously. Never blocks the EDT or exam.
     */
    public static void saveSnapshot(final String examSessionId, final String userId,
                                    final byte[] imageData, final String flag) {
        if (examSessionId == null || examSessionId.trim().isEmpty() || imageData == null || imageData.length == 0) {
            return;
        }
        ASYNC_WRITER.submit(() -> {
            String sql = "INSERT INTO proctor_snapshots (exam_session_id, user_id, taken_at, image_data, flag) "
                    + "VALUES (?, ?, NOW(), ?, ?)";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                String safeSessId = examSessionId;
                if (safeSessId != null && safeSessId.length() > 36) {
                    safeSessId = safeSessId.substring(0, 36);
                }
                ps.setString(1, safeSessId);
                ps.setString(2, (userId != null && !userId.trim().isEmpty()) ? userId.trim() : "Unknown");
                ps.setBytes(3, imageData);
                ps.setString(4, (flag != null && !flag.trim().isEmpty()) ? flag.trim() : "PERIODIC");
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ProctorDAO] Failed to save webcam snapshot: " + e.getMessage());
            }
        });
    }

    /**
     * Helper to flush pending async writes (useful in test verifications).
     */
    public static boolean flush(long timeoutMs) {
        try {
            return ASYNC_WRITER.submit(() -> true).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieves filtered events list for Tab 1 of ProctorLogUI.
     */
    public static List<ProctorEvent> getEvents(String userId, Integer examId, String eventType,
                                               LocalDate fromDate, LocalDate toDate, Integer attemptId) {
        List<ProctorEvent> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT pe.id, pe.exam_session_id, pe.user_id, pe.exam_id, e.title AS exam_title, ")
           .append("pe.attempt_id, pe.event_type, pe.details, pe.created_at ")
           .append("FROM proctor_events pe ")
           .append("LEFT JOIN exams e ON pe.exam_id = e.id ")
           .append("WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (userId != null && !userId.trim().isEmpty()) {
            sql.append("AND pe.user_id LIKE ? ");
            params.add("%" + userId.trim() + "%");
        }
        if (examId != null && examId > 0) {
            sql.append("AND pe.exam_id = ? ");
            params.add(examId);
        }
        if (eventType != null && !eventType.trim().isEmpty() && !"ALL".equalsIgnoreCase(eventType)) {
            sql.append("AND pe.event_type = ? ");
            params.add(eventType.trim());
        }
        if (fromDate != null) {
            sql.append("AND pe.created_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append("AND pe.created_at <= ? ");
            params.add(Timestamp.valueOf(toDate.atTime(23, 59, 59)));
        }
        if (attemptId != null && attemptId > 0) {
            sql.append("AND pe.attempt_id = ? ");
            params.add(attemptId);
        }

        sql.append("ORDER BY pe.created_at DESC, pe.id DESC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String sId = rs.getString("exam_session_id");
                    String uId = rs.getString("user_id");
                    int exIdRaw = rs.getInt("exam_id");
                    Integer exId = rs.wasNull() ? null : exIdRaw;
                    String exTitle = rs.getString("exam_title");
                    int attIdRaw = rs.getInt("attempt_id");
                    Integer attId = rs.wasNull() ? null : attIdRaw;
                    String evType = rs.getString("event_type");
                    String details = rs.getString("details");
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : null;

                    list.add(new ProctorEvent(id, sId, uId, exId, exTitle, attId, evType, details, createdAt));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Retrieves aggregated summaries per attempt / session for Tab 2 of ProctorLogUI.
     * Sorted by Risk descending (HIGH -> MEDIUM -> LOW), then total violations descending, then start date descending.
     */
    public static List<AttemptProctorSummary> getAttemptSummaries(String userId, Integer examId,
                                                                 LocalDate fromDate, LocalDate toDate) {
        List<AttemptProctorSummary> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT pe.exam_session_id, pe.attempt_id, pe.user_id, pe.exam_id, e.title AS exam_title, ")
           .append("DATE_FORMAT(MIN(pe.created_at), '%Y-%m-%d %H:%i:%s') AS session_start, ")
           .append("DATE_FORMAT(MAX(pe.created_at), '%Y-%m-%d %H:%i:%s') AS session_end, ")
           .append("SUM(CASE WHEN pe.event_type = 'FOCUS_LOST' THEN 1 ELSE 0 END) AS cnt_focus, ")
           .append("SUM(CASE WHEN pe.event_type = 'WINDOW_MINIMIZED' THEN 1 ELSE 0 END) AS cnt_minimized, ")
           .append("SUM(CASE WHEN pe.event_type IN ('COPY_ATTEMPT','PASTE_ATTEMPT','CUT_ATTEMPT') THEN 1 ELSE 0 END) AS cnt_copypaste, ")
           .append("SUM(CASE WHEN pe.event_type = 'RIGHT_CLICK' THEN 1 ELSE 0 END) AS cnt_rightclick, ")
           .append("SUM(CASE WHEN pe.event_type = 'PRINT_SCREEN_KEY' THEN 1 ELSE 0 END) AS cnt_printscreen, ")
           .append("SUM(CASE WHEN pe.event_type = 'AUTO_SUBMIT_PROCTOR' THEN 1 ELSE 0 END) AS cnt_autosubmit, ")
           .append("SUM(CASE WHEN pe.event_type NOT IN ('EXAM_STARTED','EXAM_SUBMITTED') THEN 1 ELSE 0 END) AS total_violations ")
           .append("FROM proctor_events pe ")
           .append("LEFT JOIN exams e ON pe.exam_id = e.id ")
           .append("WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (userId != null && !userId.trim().isEmpty()) {
            sql.append("AND pe.user_id LIKE ? ");
            params.add("%" + userId.trim() + "%");
        }
        if (examId != null && examId > 0) {
            sql.append("AND pe.exam_id = ? ");
            params.add(examId);
        }
        if (fromDate != null) {
            sql.append("AND pe.created_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append("AND pe.created_at <= ? ");
            params.add(Timestamp.valueOf(toDate.atTime(23, 59, 59)));
        }

        sql.append("GROUP BY pe.exam_session_id, pe.attempt_id, pe.user_id, pe.exam_id, e.title ");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sId = rs.getString("exam_session_id");
                    int attIdRaw = rs.getInt("attempt_id");
                    Integer attId = rs.wasNull() ? null : attIdRaw;
                    String uId = rs.getString("user_id");
                    int exIdRaw = rs.getInt("exam_id");
                    Integer exId = rs.wasNull() ? null : exIdRaw;
                    String exTitle = rs.getString("exam_title");
                    if (exTitle == null || exTitle.trim().isEmpty()) {
                        exTitle = exId != null ? "Exam #" + exId : "Exam";
                    }
                    String startStr = rs.getString("session_start");
                    String endStr = rs.getString("session_end");
                    int focus = rs.getInt("cnt_focus");
                    int min = rs.getInt("cnt_minimized");
                    int cp = rs.getInt("cnt_copypaste");
                    int rc = rs.getInt("cnt_rightclick");
                    int psKey = rs.getInt("cnt_printscreen");
                    int autoSub = rs.getInt("cnt_autosubmit");
                    int violations = rs.getInt("total_violations");

                    boolean hasAutoSubmit = autoSub > 0;
                    RiskLevel risk = computeRiskLevel(violations, hasAutoSubmit);

                    list.add(new AttemptProctorSummary(
                            sId, attId, uId, exId, exTitle, focus, min, cp, rc, psKey,
                            autoSub, violations, hasAutoSubmit, risk, startStr, endStr
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Sort by Risk descending, then total violations descending, then start date descending
        list.sort((a, b) -> {
            int cmpRisk = Integer.compare(b.riskLevel.getSeverity(), a.riskLevel.getSeverity());
            if (cmpRisk != 0) return cmpRisk;
            int cmpViolations = Integer.compare(b.totalViolations, a.totalViolations);
            if (cmpViolations != 0) return cmpViolations;
            return b.startTime.compareTo(a.startTime);
        });

        return list;
    }

    /**
     * Builds a map of attemptId -> RiskSummary for all attempts recorded in proctor_events.
     * Used by AdminDashboard Recent Submissions, Subject Reports, and PerformanceAnalysisUI
     * to render the "Flags" risk pill.
     */
    public static Map<Integer, RiskSummary> getRiskByAttemptIds() {
        Map<Integer, RiskSummary> map = new HashMap<>();
        String sql = "SELECT pe.attempt_id, "
                + "SUM(CASE WHEN pe.event_type = 'AUTO_SUBMIT_PROCTOR' THEN 1 ELSE 0 END) AS cnt_autosubmit, "
                + "SUM(CASE WHEN pe.event_type NOT IN ('EXAM_STARTED','EXAM_SUBMITTED') THEN 1 ELSE 0 END) AS total_violations "
                + "FROM proctor_events pe "
                + "WHERE pe.attempt_id IS NOT NULL "
                + "GROUP BY pe.attempt_id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int attId = rs.getInt("attempt_id");
                int autoSub = rs.getInt("cnt_autosubmit");
                int violations = rs.getInt("total_violations");
                boolean hasAuto = autoSub > 0;
                RiskLevel risk = computeRiskLevel(violations, hasAuto);
                map.put(attId, new RiskSummary(risk, violations, hasAuto));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * Retrieves proctor snapshots filtered by attemptId, examSessionId, or student userId.
     */
    public static List<ProctorSnapshot> getSnapshots(Integer attemptId, String examSessionId, String userId) {
        List<ProctorSnapshot> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.exam_session_id, s.user_id, s.attempt_id, s.taken_at, s.image_data, s.flag "
                + "FROM proctor_snapshots s WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (attemptId != null && attemptId > 0) {
            sql.append("AND (s.attempt_id = ? OR s.exam_session_id IN (SELECT e.exam_session_id FROM proctor_events e WHERE e.attempt_id = ?)) ");
            params.add(attemptId);
            params.add(attemptId);
        }
        if (examSessionId != null && !examSessionId.trim().isEmpty()) {
            sql.append("AND s.exam_session_id = ? ");
            params.add(examSessionId.trim());
        }
        if (userId != null && !userId.trim().isEmpty()) {
            sql.append("AND s.user_id LIKE ? ");
            params.add("%" + userId.trim() + "%");
        }
        sql.append("ORDER BY s.taken_at ASC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else ps.setString(i + 1, String.valueOf(p));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String sId = rs.getString("exam_session_id");
                    String uId = rs.getString("user_id");
                    int aId = rs.getInt("attempt_id");
                    Integer att = rs.wasNull() ? null : aId;
                    Timestamp ts = rs.getTimestamp("taken_at");
                    LocalDateTime takenAt = ts != null ? ts.toLocalDateTime() : null;
                    byte[] img = rs.getBytes("image_data");
                    String flag = rs.getString("flag");
                    list.add(new ProctorSnapshot(id, sId, uId, att, takenAt, img, flag));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Returns list of distinct attempt IDs that have recorded webcam snapshots.
     */
    public static List<Integer> getDistinctAttemptIdsWithSnapshots() {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT DISTINCT COALESCE(s.attempt_id, e.attempt_id) AS att_id "
                + "FROM proctor_snapshots s "
                + "LEFT JOIN proctor_events e ON s.exam_session_id = e.exam_session_id AND e.attempt_id IS NOT NULL "
                + "WHERE s.attempt_id IS NOT NULL OR e.attempt_id IS NOT NULL "
                + "ORDER BY att_id DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("att_id");
                if (id > 0 && !list.contains(id)) list.add(id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static final int DEFAULT_RETENTION_DAYS = 30;
    private static int snapshotRetentionDays = DEFAULT_RETENTION_DAYS;

    public static int getSnapshotRetentionDays() {
        return snapshotRetentionDays;
    }

    public static void setSnapshotRetentionDays(int days) {
        snapshotRetentionDays = Math.max(1, days);
    }

    /**
     * Purges proctor snapshots older than retentionDays.
     * Called at startup and manually on demand by admins.
     */
    public static int purgeOldSnapshots(int retentionDays) {
        int days = Math.max(1, retentionDays);
        String sql = "DELETE FROM proctor_snapshots WHERE taken_at < NOW() - INTERVAL ? DAY";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ProctorDAO] Failed to purge old snapshots: " + e.getMessage());
            return 0;
        }
    }
}
