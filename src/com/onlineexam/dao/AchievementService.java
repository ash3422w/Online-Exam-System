package com.onlineexam.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Service and DAO for Achievements, Badges, and Daily Streaks.
 * Evaluates rules against MySQL exam_attempts and user_achievements.
 */
public final class AchievementService {

    private AchievementService() {
    }

    // ================= DATA MODELS =================

    public static class StreakInfo {
        public final int currentStreak;
        public final int longestStreak;
        public final boolean attemptedToday;

        public StreakInfo(int currentStreak, int longestStreak, boolean attemptedToday) {
            this.currentStreak = Math.max(0, currentStreak);
            this.longestStreak = Math.max(this.currentStreak, Math.max(0, longestStreak));
            this.attemptedToday = attemptedToday;
        }

        public static StreakInfo empty() {
            return new StreakInfo(0, 0, false);
        }
    }

    public static class Achievement {
        public final String code;
        public final String name;
        public final String description;
        public final String iconKey;
        public final int sortOrder;
        public final boolean earned;
        public final Timestamp earnedAt;
        public final Integer attemptId;
        public final String progressHint;
        public final double progressPct; // 0.0 to 100.0

        public Achievement(String code, String name, String description, String iconKey, int sortOrder,
                           boolean earned, Timestamp earnedAt, Integer attemptId,
                           String progressHint, double progressPct) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.iconKey = iconKey != null ? iconKey : "trophy";
            this.sortOrder = sortOrder;
            this.earned = earned;
            this.earnedAt = earnedAt;
            this.attemptId = attemptId;
            this.progressHint = progressHint != null ? progressHint : "";
            this.progressPct = Math.max(0.0, Math.min(100.0, progressPct));
        }

        public String getFormattedEarnedDate() {
            if (earnedAt == null) return "";
            try {
                LocalDateTime ldt = earnedAt.toLocalDateTime();
                return ldt.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
            } catch (Exception ex) {
                return earnedAt.toString();
            }
        }
    }

    private static class EarnedRecord {
        final Timestamp earnedAt;
        final Integer attemptId;

        EarnedRecord(Timestamp earnedAt, Integer attemptId) {
            this.earnedAt = earnedAt;
            this.attemptId = attemptId;
        }
    }

    // ================= STREAK CALCULATION =================

    /**
     * Computes streaks from DISTINCT DATE(attempted_at) in exam_attempts using DB server time.
     * currentStreak is alive if the last attempt was today or yesterday (server time).
     */
    public static StreakInfo getStreakInfo(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return StreakInfo.empty();
        }
        String normalizedUser = userId.trim();

        String serverDatesSql = "SELECT CURDATE() AS server_today, (CURDATE() - INTERVAL 1 DAY) AS server_yesterday";
        String distinctDatesSql = "SELECT DISTINCT DATE(attempted_at) AS attempt_date "
                + "FROM exam_attempts WHERE user_id = ? ORDER BY attempt_date DESC";

        try (Connection con = DBConnection.getConnection()) {
            LocalDate serverToday = null;
            LocalDate serverYesterday = null;

            try (PreparedStatement ps = con.prepareStatement(serverDatesSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date dtToday = rs.getDate("server_today");
                    Date dtYest = rs.getDate("server_yesterday");
                    if (dtToday != null) serverToday = dtToday.toLocalDate();
                    if (dtYest != null) serverYesterday = dtYest.toLocalDate();
                }
            }

            if (serverToday == null) {
                serverToday = LocalDate.now();
                serverYesterday = serverToday.minusDays(1);
            }

            TreeSet<LocalDate> dates = new TreeSet<>();
            try (PreparedStatement ps = con.prepareStatement(distinctDatesSql)) {
                ps.setString(1, normalizedUser);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Date d = rs.getDate("attempt_date");
                        if (d != null) {
                            dates.add(d.toLocalDate());
                        }
                    }
                }
            }

            if (dates.isEmpty()) {
                return StreakInfo.empty();
            }

            boolean attemptedToday = dates.contains(serverToday);

            // Compute current alive streak
            int currentStreak = 0;
            if (dates.contains(serverToday)) {
                currentStreak = 1;
                LocalDate check = serverToday.minusDays(1);
                while (dates.contains(check)) {
                    currentStreak++;
                    check = check.minusDays(1);
                }
            } else if (dates.contains(serverYesterday)) {
                currentStreak = 1;
                LocalDate check = serverYesterday.minusDays(1);
                while (dates.contains(check)) {
                    currentStreak++;
                    check = check.minusDays(1);
                }
            }

            // Compute longest streak across all historical dates
            int longest = 0;
            int run = 0;
            LocalDate prev = null;
            for (LocalDate d : dates) {
                if (prev == null) {
                    run = 1;
                } else if (d.equals(prev.plusDays(1))) {
                    run++;
                } else {
                    run = 1;
                }
                if (run > longest) {
                    longest = run;
                }
                prev = d;
            }
            longest = Math.max(longest, currentStreak);

            return new StreakInfo(currentStreak, longest, attemptedToday);
        } catch (SQLException e) {
            e.printStackTrace();
            return StreakInfo.empty();
        }
    }

    // ================= RULE EVALUATION AFTER ATTEMPT =================

    /**
     * Checks all achievement rules using SQL against exam_attempts, awards newly earned ones,
     * and returns the list of newly earned achievements.
     */
    public static List<Achievement> evaluateAfterAttempt(String userId, int attemptId) {
        if (userId == null || userId.trim().isEmpty() || attemptId <= 0) {
            return Collections.emptyList();
        }
        String normalizedUser = userId.trim();

        List<Achievement> newlyEarned = new ArrayList<>();

        try (Connection con = DBConnection.getConnection()) {
            // 1. Fetch already earned achievement codes
            Set<String> alreadyEarned = new HashSet<>();
            String alreadySql = "SELECT code FROM user_achievements WHERE user_id = ?";
            try (PreparedStatement ps = con.prepareStatement(alreadySql)) {
                ps.setString(1, normalizedUser);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        alreadyEarned.add(rs.getString("code"));
                    }
                }
            }

            // If all 11 badges are already unlocked, nothing more to award
            if (alreadyEarned.size() >= 11) {
                return Collections.emptyList();
            }

            // 2. Fetch current attempt details
            String attemptSql = "SELECT a.*, e.duration_minutes, e.pass_percentage AS exam_pass_pct, "
                    + "e.subject_id AS exam_subj_id "
                    + "FROM exam_attempts a "
                    + "LEFT JOIN exams e ON a.exam_id = e.id "
                    + "WHERE a.id = ?";
            double currentMarks = 0;
            double currentTotalMarks = 20;
            double currentPct = 0;
            int currentScore = 0;
            int currentTotalQuestions = 20;
            int currentTimeTaken = 0;
            Integer currentExamId = null;
            int currentSubjId = 0;
            Integer currentDurationMinutes = null;
            Double currentPassPct = 40.0;
            boolean currentPassed = false;

            try (PreparedStatement ps = con.prepareStatement(attemptSql)) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentScore = rs.getInt("score");
                        currentTotalQuestions = rs.getInt("total_questions");
                        if (currentTotalQuestions <= 0) currentTotalQuestions = 20;
                        currentTimeTaken = rs.getInt("time_taken_seconds");
                        currentSubjId = rs.getInt("subject_id");

                        int eid = rs.getInt("exam_id");
                        if (!rs.wasNull()) currentExamId = eid;

                        int dur = rs.getInt("duration_minutes");
                        if (!rs.wasNull()) currentDurationMinutes = dur;

                        double passP = rs.getDouble("exam_pass_pct");
                        if (!rs.wasNull()) currentPassPct = passP;

                        java.math.BigDecimal mo = rs.getBigDecimal("marks_obtained");
                        java.math.BigDecimal tm = rs.getBigDecimal("total_marks");

                        currentMarks = (mo != null) ? mo.doubleValue() : currentScore;
                        currentTotalMarks = (tm != null && tm.doubleValue() > 0) ? tm.doubleValue() : currentTotalQuestions;
                        currentPct = Math.min(100.0, Math.max(0.0, (Math.max(0.0, currentMarks) * 100.0) / currentTotalMarks));

                        int pVal = rs.getInt("passed");
                        if (!rs.wasNull()) {
                            currentPassed = (pVal == 1);
                        } else {
                            currentPassed = (currentPct >= currentPassPct);
                        }
                    }
                }
            }

            // 3. Evaluate each achievement rule if not already earned
            Set<String> qualifiedCodes = new HashSet<>();

            // Rule 1: FIRST_EXAM (Finish your first exam)
            if (!alreadyEarned.contains("FIRST_EXAM")) {
                qualifiedCodes.add("FIRST_EXAM");
            }

            // Rule 2: FIRST_90 (Score 90% or more on an exam)
            if (!alreadyEarned.contains("FIRST_90")) {
                if (currentPct >= 90.0) {
                    qualifiedCodes.add("FIRST_90");
                } else {
                    String checkSql = "SELECT 1 FROM exam_attempts WHERE user_id = ? AND "
                            + "(CASE WHEN total_marks > 0 THEN (marks_obtained * 100.0 / total_marks) "
                            + "ELSE (score * 100.0 / total_questions) END) >= 90.0 LIMIT 1";
                    try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                        ps.setString(1, normalizedUser);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) qualifiedCodes.add("FIRST_90");
                        }
                    }
                }
            }

            // Rule 3: PERFECT_SCORE (100% score)
            if (!alreadyEarned.contains("PERFECT_SCORE")) {
                if (currentPct >= 99.99 || currentScore == currentTotalQuestions) {
                    qualifiedCodes.add("PERFECT_SCORE");
                } else {
                    String checkSql = "SELECT 1 FROM exam_attempts WHERE user_id = ? AND "
                            + "(CASE WHEN total_marks > 0 THEN (marks_obtained * 100.0 / total_marks) "
                            + "ELSE (score * 100.0 / total_questions) END) >= 99.99 LIMIT 1";
                    try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                        ps.setString(1, normalizedUser);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) qualifiedCodes.add("PERFECT_SCORE");
                        }
                    }
                }
            }

            // Rule 4: FIVE_IN_WEEK (5 exams in 7 days)
            if (!alreadyEarned.contains("FIVE_IN_WEEK")) {
                String checkSql = "SELECT COUNT(*) FROM exam_attempts WHERE user_id = ? AND attempted_at >= NOW() - INTERVAL 7 DAY";
                try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= 5) {
                            qualifiedCodes.add("FIVE_IN_WEEK");
                        }
                    }
                }
            }

            // Rules 5, 6, 7: Daily streaks (STREAK_3, STREAK_7, STREAK_30)
            if (!alreadyEarned.contains("STREAK_3") || !alreadyEarned.contains("STREAK_7") || !alreadyEarned.contains("STREAK_30")) {
                StreakInfo streak = getStreakInfo(normalizedUser);
                int maxStreak = Math.max(streak.currentStreak, streak.longestStreak);
                if (!alreadyEarned.contains("STREAK_3") && maxStreak >= 3) {
                    qualifiedCodes.add("STREAK_3");
                }
                if (!alreadyEarned.contains("STREAK_7") && maxStreak >= 7) {
                    qualifiedCodes.add("STREAK_7");
                }
                if (!alreadyEarned.contains("STREAK_30") && maxStreak >= 30) {
                    qualifiedCodes.add("STREAK_30");
                }
            }

            // Rule 8: ALL_SUBJECTS (Attempt every subject)
            if (!alreadyEarned.contains("ALL_SUBJECTS")) {
                String subjCheckSql = "SELECT "
                        + "(SELECT COUNT(DISTINCT subject_id) FROM exam_attempts WHERE user_id = ?) AS user_subjs, "
                        + "(SELECT COUNT(*) FROM subjects) AS total_subjs";
                try (PreparedStatement ps = con.prepareStatement(subjCheckSql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int uSubjs = rs.getInt("user_subjs");
                            int totSubjs = rs.getInt("total_subjs");
                            if (totSubjs > 0 && uSubjs >= totSubjs) {
                                qualifiedCodes.add("ALL_SUBJECTS");
                            }
                        }
                    }
                }
            }

            // Rule 9: COMEBACK (Improve by 20+ percentage points on a retake of the same exam)
            if (!alreadyEarned.contains("COMEBACK")) {
                String comebackSql = "SELECT 1 FROM exam_attempts a_prev "
                        + "WHERE a_prev.user_id = ? "
                        + "AND ((? IS NOT NULL AND a_prev.exam_id = ?) OR (? IS NULL AND a_prev.subject_id = ?)) "
                        + "AND a_prev.id < ? "
                        + "AND (? - (CASE WHEN a_prev.total_marks > 0 THEN (a_prev.marks_obtained * 100.0 / a_prev.total_marks) "
                        + "ELSE (a_prev.score * 100.0 / a_prev.total_questions) END)) >= 20.0 LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(comebackSql)) {
                    ps.setString(1, normalizedUser);
                    if (currentExamId != null) {
                        ps.setInt(2, currentExamId);
                        ps.setInt(3, currentExamId);
                        ps.setNull(4, java.sql.Types.INTEGER);
                        ps.setInt(5, currentSubjId);
                    } else {
                        ps.setNull(2, java.sql.Types.INTEGER);
                        ps.setNull(3, java.sql.Types.INTEGER);
                        ps.setNull(4, java.sql.Types.INTEGER);
                        ps.setInt(5, currentSubjId);
                    }
                    ps.setInt(6, attemptId);
                    ps.setDouble(7, currentPct);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            qualifiedCodes.add("COMEBACK");
                        }
                    }
                }
            }

            // Rule 10: SPEED_RUNNER (80%+ using under half the allocated time)
            if (!alreadyEarned.contains("SPEED_RUNNER")) {
                int allocatedSeconds = (currentDurationMinutes != null && currentDurationMinutes > 0)
                        ? (currentDurationMinutes * 60)
                        : (currentTotalQuestions * 60);
                if (currentPct >= 80.0 && currentTimeTaken > 0 && currentTimeTaken <= (allocatedSeconds / 2.0)) {
                    qualifiedCodes.add("SPEED_RUNNER");
                }
            }

            // Rule 11: CONSISTENT (Pass 5 exams in a row)
            if (!alreadyEarned.contains("CONSISTENT")) {
                String historySql = "SELECT score, total_questions, marks_obtained, total_marks, passed "
                        + "FROM exam_attempts WHERE user_id = ? ORDER BY attempted_at ASC, id ASC";
                try (PreparedStatement ps = con.prepareStatement(historySql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        int consecutive = 0;
                        int maxConsecutive = 0;
                        while (rs.next()) {
                            int sc = rs.getInt("score");
                            int tq = rs.getInt("total_questions");
                            if (tq <= 0) tq = 20;
                            java.math.BigDecimal mo = rs.getBigDecimal("marks_obtained");
                            java.math.BigDecimal tm = rs.getBigDecimal("total_marks");
                            double m = mo != null ? mo.doubleValue() : sc;
                            double tot = (tm != null && tm.doubleValue() > 0) ? tm.doubleValue() : tq;
                            double p = (m * 100.0) / tot;
                            int passVal = rs.getInt("passed");
                            boolean pass = !rs.wasNull() ? (passVal == 1) : (p >= 40.0);
                            if (pass) {
                                consecutive++;
                                if (consecutive > maxConsecutive) maxConsecutive = consecutive;
                            } else {
                                consecutive = 0;
                            }
                        }
                        if (maxConsecutive >= 5) {
                            qualifiedCodes.add("CONSISTENT");
                        }
                    }
                }
            }

            // 4. Award qualified achievements into user_achievements
            if (!qualifiedCodes.isEmpty()) {
                String insertSql = "INSERT IGNORE INTO user_achievements (user_id, code, attempt_id, earned_at) VALUES (?, ?, ?, NOW())";
                String fetchMetaSql = "SELECT code, name, description, icon_key, sort_order FROM achievements WHERE code = ?";

                try (PreparedStatement insPs = con.prepareStatement(insertSql);
                     PreparedStatement metaPs = con.prepareStatement(fetchMetaSql)) {
                    for (String code : qualifiedCodes) {
                        insPs.setString(1, normalizedUser);
                        insPs.setString(2, code);
                        insPs.setInt(3, attemptId);
                        int rows = insPs.executeUpdate();
                        if (rows > 0) {
                            // Fetch metadata to build returned Achievement object
                            metaPs.setString(1, code);
                            try (ResultSet metaRs = metaPs.executeQuery()) {
                                if (metaRs.next()) {
                                    Achievement ach = new Achievement(
                                            metaRs.getString("code"),
                                            metaRs.getString("name"),
                                            metaRs.getString("description"),
                                            metaRs.getString("icon_key"),
                                            metaRs.getInt("sort_order"),
                                            true,
                                            new Timestamp(System.currentTimeMillis()),
                                            attemptId,
                                            "Unlocked Just Now!",
                                            100.0
                                    );
                                    newlyEarned.add(ach);
                                    System.out.println("[AchievementService] Awarded badge '" + ach.name + "' (" + ach.code + ") to " + normalizedUser);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return newlyEarned;
    }

    // ================= PROGRESS & CATALOG =================

    /**
     * Returns all achievements sorted by sort_order, with earned status, earned timestamp,
     * progress hint (e.g. "3/5 exams this week", "2/3 days", "3/4 subjects"), and progress percentage.
     */
    public static List<Achievement> getProgress(String userId) {
        List<Achievement> list = new ArrayList<>();
        String normalizedUser = (userId != null) ? userId.trim() : "";

        String catalogSql = "SELECT code, name, description, icon_key, sort_order FROM achievements ORDER BY sort_order ASC";
        String userEarnedSql = "SELECT code, earned_at, attempt_id FROM user_achievements WHERE user_id = ?";

        try (Connection con = DBConnection.getConnection()) {
            Map<String, EarnedRecord> earnedMap = new HashMap<>();
            if (!normalizedUser.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement(userEarnedSql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            earnedMap.put(
                                    rs.getString("code"),
                                    new EarnedRecord(rs.getTimestamp("earned_at"), rs.getInt("attempt_id"))
                            );
                        }
                    }
                }
            }

            // Pre-calculate user aggregate stats for locked progress hints
            StreakInfo streak = getStreakInfo(normalizedUser);
            int maxStreak = Math.max(streak.currentStreak, streak.longestStreak);

            int totalExams = 0;
            double bestPct = 0.0;
            int examsLast7Days = 0;
            int userSubjs = 0;
            int totalSubjs = 0;
            int consecutivePasses = 0;

            if (!normalizedUser.isEmpty()) {
                String statsSql = "SELECT "
                        + "COUNT(*) AS total_attempts, "
                        + "COALESCE(MAX(CASE WHEN total_marks > 0 THEN (marks_obtained * 100.0 / total_marks) "
                        + "ELSE (score * 100.0 / total_questions) END), 0.0) AS max_pct "
                        + "FROM exam_attempts WHERE user_id = ?";
                try (PreparedStatement ps = con.prepareStatement(statsSql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            totalExams = rs.getInt("total_attempts");
                            bestPct = rs.getDouble("max_pct");
                        }
                    }
                }

                String weekSql = "SELECT COUNT(*) FROM exam_attempts WHERE user_id = ? AND attempted_at >= NOW() - INTERVAL 7 DAY";
                try (PreparedStatement ps = con.prepareStatement(weekSql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) examsLast7Days = rs.getInt(1);
                    }
                }

                String subjSql = "SELECT "
                        + "(SELECT COUNT(DISTINCT subject_id) FROM exam_attempts WHERE user_id = ?) AS user_subjs, "
                        + "(SELECT COUNT(*) FROM subjects) AS total_subjs";
                try (PreparedStatement ps = con.prepareStatement(subjSql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userSubjs = rs.getInt("user_subjs");
                            totalSubjs = rs.getInt("total_subjs");
                        }
                    }
                }

                String passHistorySql = "SELECT score, total_questions, marks_obtained, total_marks, passed "
                        + "FROM exam_attempts WHERE user_id = ? ORDER BY attempted_at DESC, id DESC LIMIT 10";
                try (PreparedStatement ps = con.prepareStatement(passHistorySql)) {
                    ps.setString(1, normalizedUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int sc = rs.getInt("score");
                            int tq = rs.getInt("total_questions");
                            if (tq <= 0) tq = 20;
                            java.math.BigDecimal mo = rs.getBigDecimal("marks_obtained");
                            java.math.BigDecimal tm = rs.getBigDecimal("total_marks");
                            double m = mo != null ? mo.doubleValue() : sc;
                            double tot = (tm != null && tm.doubleValue() > 0) ? tm.doubleValue() : tq;
                            double p = (m * 100.0) / tot;
                            int passVal = rs.getInt("passed");
                            boolean pass = !rs.wasNull() ? (passVal == 1) : (p >= 40.0);
                            if (pass) {
                                consecutivePasses++;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(catalogSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    String name = rs.getString("name");
                    String desc = rs.getString("description");
                    String iconKey = rs.getString("icon_key");
                    int sortOrder = rs.getInt("sort_order");

                    EarnedRecord earnedRec = earnedMap.get(code);
                    boolean isEarned = (earnedRec != null);
                    Timestamp earnedAt = isEarned ? earnedRec.earnedAt : null;
                    Integer attemptId = isEarned ? earnedRec.attemptId : null;

                    String hint;
                    double pct;

                    if (isEarned) {
                        hint = "Unlocked on " + (earnedAt != null
                                ? earnedAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
                                : "Completed");
                        pct = 100.0;
                    } else {
                        switch (code) {
                            case "FIRST_EXAM":
                                hint = totalExams + "/1 exams completed";
                                pct = totalExams >= 1 ? 100.0 : 0.0;
                                break;
                            case "FIRST_90":
                                hint = String.format(Locale.ENGLISH, "Best: %.0f%% / 90%%", bestPct);
                                pct = Math.min(100.0, (bestPct / 90.0) * 100.0);
                                break;
                            case "PERFECT_SCORE":
                                hint = String.format(Locale.ENGLISH, "Best: %.0f%% / 100%%", bestPct);
                                pct = Math.min(100.0, bestPct);
                                break;
                            case "FIVE_IN_WEEK":
                                hint = examsLast7Days + "/5 exams this week";
                                pct = Math.min(100.0, (examsLast7Days / 5.0) * 100.0);
                                break;
                            case "STREAK_3":
                                hint = Math.min(3, maxStreak) + "/3 days";
                                pct = Math.min(100.0, (maxStreak / 3.0) * 100.0);
                                break;
                            case "STREAK_7":
                                hint = Math.min(7, maxStreak) + "/7 days";
                                pct = Math.min(100.0, (maxStreak / 7.0) * 100.0);
                                break;
                            case "STREAK_30":
                                hint = Math.min(30, maxStreak) + "/30 days";
                                pct = Math.min(100.0, (maxStreak / 30.0) * 100.0);
                                break;
                            case "ALL_SUBJECTS":
                                hint = userSubjs + "/" + totalSubjs + " subjects";
                                pct = totalSubjs > 0 ? Math.min(100.0, (userSubjs * 100.0) / totalSubjs) : 0.0;
                                break;
                            case "COMEBACK":
                                hint = "Score +20% higher on a retake";
                                pct = 0.0;
                                break;
                            case "SPEED_RUNNER":
                                hint = "Score 80%+ in < 50% time";
                                pct = 0.0;
                                break;
                            case "CONSISTENT":
                                hint = Math.min(5, consecutivePasses) + "/5 consecutive passes";
                                pct = Math.min(100.0, (consecutivePasses / 5.0) * 100.0);
                                break;
                            default:
                                hint = "In progress";
                                pct = 0.0;
                                break;
                        }
                    }

                    list.add(new Achievement(code, name, desc, iconKey, sortOrder, isEarned, earnedAt, attemptId, hint, pct));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    /**
     * Returns the latest earned badges for a student, ordered by earned_at DESC.
     */
    public static List<Achievement> getLatestEarnedBadges(String userId, int limit) {
        if (userId == null || userId.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        String normalizedUser = userId.trim();
        List<Achievement> list = new ArrayList<>();

        String sql = "SELECT a.code, a.name, a.description, a.icon_key, a.sort_order, "
                + "ua.earned_at, ua.attempt_id "
                + "FROM user_achievements ua "
                + "JOIN achievements a ON ua.code = a.code "
                + "WHERE ua.user_id = ? "
                + "ORDER BY ua.earned_at DESC, ua.id DESC LIMIT ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, normalizedUser);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Achievement(
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("icon_key"),
                            rs.getInt("sort_order"),
                            true,
                            rs.getTimestamp("earned_at"),
                            rs.getInt("attempt_id"),
                            "Earned",
                            100.0
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Returns the total count of badges earned by a user.
     */
    public static int getTotalEarnedCount(String userId) {
        if (userId == null || userId.trim().isEmpty()) return 0;
        String sql = "SELECT COUNT(*) FROM user_achievements WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
