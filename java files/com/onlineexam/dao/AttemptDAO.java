package com.onlineexam.dao;

import com.onlineexam.data.AnswerEvaluator;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AttemptDAO {

    public static final double PASS_PERCENTAGE = 40.0;

    public static void initializeAttemptTable() {
        String attemptsSql = "CREATE TABLE IF NOT EXISTS exam_attempts ("
                + "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "user_id VARCHAR(100) NOT NULL, "
                + "subject_id INT NOT NULL, "
                + "score INT NOT NULL, "
                + "total_questions INT NOT NULL DEFAULT 20, "
                + "time_taken_seconds INT NOT NULL DEFAULT 0, "
                + "submission_type VARCHAR(20) NOT NULL DEFAULT 'Manual', "
                + "attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB";

        String answersSql = "CREATE TABLE IF NOT EXISTS exam_attempt_answers ("
                + "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "attempt_id INT NOT NULL, "
                + "question_id INT NOT NULL, "
                + "selected_option INT NULL, "
                + "correct_option INT NOT NULL, "
                + "is_correct BOOLEAN NOT NULL, "
                + "CONSTRAINT fk_exam_attempt_answers_attempt "
                + "FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB";

        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(attemptsSql)) {
                ps.executeUpdate();
            }
            ensureInnoDBEngine(con, "exam_attempts");
            ensureColumnExists(con, "exam_attempts", "total_questions",
                    "ALTER TABLE exam_attempts ADD COLUMN total_questions INT NOT NULL DEFAULT 20");
            ensureColumnExists(con, "exam_attempts", "time_taken_seconds",
                    "ALTER TABLE exam_attempts ADD COLUMN time_taken_seconds INT NOT NULL DEFAULT 0");
            ensureColumnExists(con, "exam_attempts", "submission_type",
                    "ALTER TABLE exam_attempts ADD COLUMN submission_type VARCHAR(20) NOT NULL DEFAULT 'Manual'");

            try (PreparedStatement psAnswers = con.prepareStatement(answersSql)) {
                psAnswers.executeUpdate();
            }
            ensureInnoDBEngine(con, "exam_attempt_answers");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void ensureInnoDBEngine(Connection con, String tableName) throws SQLException {
        String checkSql = "SELECT ENGINE FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement check = con.prepareStatement(checkSql)) {
            check.setString(1, tableName);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    String engine = rs.getString("ENGINE");
                    if (engine != null && !"InnoDB".equalsIgnoreCase(engine)) {
                        try (PreparedStatement alter = con.prepareStatement(
                                "ALTER TABLE " + tableName + " ENGINE=InnoDB")) {
                            alter.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private static void ensureColumnExists(
            Connection con, String tableName, String columnName, String alterSql) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement check = con.prepareStatement(checkSql)) {
            check.setString(1, tableName);
            check.setString(2, columnName);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement alter = con.prepareStatement(alterSql)) {
                        alter.executeUpdate();
                    }
                }
            }
        }
    }

    public static boolean saveAttempt(String userId, int subjectId, int score) {
        return saveAttempt(userId, subjectId, score, ExamStore.DEFAULT_EXAM_QUESTION_COUNT,
                0, ExamStore.SUBMISSION_MANUAL);
    }

    public static boolean saveAttempt(
            String userId, int subjectId, int score, int timeTakenSeconds, String submissionType) {
        int inferredTotal = Math.max(score, ExamStore.DEFAULT_EXAM_QUESTION_COUNT);
        return saveAttempt(userId, subjectId, score, inferredTotal, timeTakenSeconds, submissionType);
    }

    public static boolean saveAttempt(
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType) {
        String sql = "INSERT INTO exam_attempts(user_id, subject_id, score, total_questions, "
                + "time_taken_seconds, submission_type) VALUES (?, ?, ?, ?, ?, ?)";
        String normalizedType = (submissionType == null || submissionType.trim().isEmpty())
                ? ExamStore.SUBMISSION_MANUAL
                : submissionType.trim();
        int safeScore = Math.max(0, score);
        int safeTotal = Math.max(safeScore, totalQuestions > 0 ? totalQuestions : ExamStore.DEFAULT_EXAM_QUESTION_COUNT);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, (userId == null || userId.trim().isEmpty()) ? "Unknown" : userId.trim());
            ps.setInt(2, subjectId);
            ps.setInt(3, safeScore);
            ps.setInt(4, safeTotal);
            ps.setInt(5, Math.max(0, timeTakenSeconds));
            ps.setString(6, normalizedType);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<AttemptAnswerRecord> buildAttemptAnswerRecords(
            List<ExamStore.Question> questions, Map<Integer, Integer> studentAnswers) {
        return buildAttemptAnswerRecords(
                questions, studentAnswers, ExamStore.studentMultiAnswers, ExamStore.studentTextAnswers);
    }

    public static List<AttemptAnswerRecord> buildAttemptAnswerRecords(
            List<ExamStore.Question> questions,
            Map<Integer, Integer> singleAnswers,
            Map<Integer, java.util.Set<Integer>> multiAnswers,
            Map<Integer, String> textAnswers) {
        List<AttemptAnswerRecord> records = new ArrayList<>();
        if (questions == null) {
            return records;
        }
        for (int i = 0; i < questions.size(); i++) {
            ExamStore.Question q = questions.get(i);
            if (q == null) {
                continue;
            }
            String qType = ExamStore.normalizeQuestionType(q.type);
            Integer singleIdx = (singleAnswers == null) ? null : singleAnswers.get(i);
            java.util.Set<Integer> multiSet = (multiAnswers == null) ? null : multiAnswers.get(i);
            String textAns = (textAnswers == null) ? null : textAnswers.get(i);

            boolean isCorrect = AnswerEvaluator.isCorrect(q, singleIdx, multiSet, textAns);

            Integer selectedOption = null;
            String selectedOptionsCsv = null;
            String answerTextVal = null;
            int correctOption;

            if (ExamStore.TYPE_MULTI.equals(qType)) {
                // Map selected shuffled indices back to original DB 1-based option numbers
                selectedOptionsCsv = ExamStore.formatZeroIndicesToOneBasedCsv(multiSet, q.originalOptionNumbers);
                String origCorrectCsv = ExamStore.formatZeroIndicesToOneBasedCsv(q.correctIndices, q.originalOptionNumbers);
                if (origCorrectCsv != null && !origCorrectCsv.isEmpty()) {
                    String first = origCorrectCsv.split(",")[0].trim();
                    try {
                        correctOption = Math.max(1, Math.min(4, Integer.parseInt(first)));
                    } catch (NumberFormatException ex) {
                        correctOption = 1;
                    }
                } else {
                    correctOption = (q.originalOptionNumbers != null
                            && q.correctIndex >= 0 && q.correctIndex < q.originalOptionNumbers.length)
                            ? q.originalOptionNumbers[q.correctIndex]
                            : Math.max(1, Math.min(4, q.correctIndex + 1));
                }
            } else if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
                correctOption = 1;
                if (textAns != null && !textAns.trim().isEmpty()) {
                    String t = textAns.trim();
                    answerTextVal = t.length() > 500 ? t.substring(0, 500) : t;
                }
            } else {
                // SINGLE and TRUE_FALSE
                if (singleIdx != null && singleIdx >= 0) {
                    selectedOption = (q.originalOptionNumbers != null
                            && singleIdx < q.originalOptionNumbers.length)
                            ? q.originalOptionNumbers[singleIdx]
                            : (singleIdx + 1);
                }
                correctOption = (q.originalOptionNumbers != null
                        && q.correctIndex >= 0 && q.correctIndex < q.originalOptionNumbers.length)
                        ? q.originalOptionNumbers[q.correctIndex]
                        : Math.max(1, Math.min(4, q.correctIndex + 1));
            }

            int questionId = q.id > 0 ? q.id : (i + 1);
            records.add(new AttemptAnswerRecord(
                    0, 0, questionId, selectedOption, correctOption, isCorrect, selectedOptionsCsv, answerTextVal));
        }
        return records;
    }

    public static boolean saveAttemptWithAnswers(
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType,
            List<AttemptAnswerRecord> answerRecords) {
        return saveAttemptWithAnswers(
                userId, subjectId, score, totalQuestions,
                timeTakenSeconds, submissionType, answerRecords,
                null, null, null, null);
    }

    public static boolean saveAttemptWithAnswers(
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType,
            List<AttemptAnswerRecord> answerRecords,
            Integer examId, Double marksObtained, Double totalMarks, Boolean passed) {
        return saveAttemptWithAnswersReturningId(
                userId, subjectId, score, totalQuestions,
                timeTakenSeconds, submissionType, answerRecords,
                examId, marksObtained, totalMarks, passed) > 0;
    }

    public static int saveAttemptWithAnswersReturningId(
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType,
            List<AttemptAnswerRecord> answerRecords,
            Integer examId, Double marksObtained, Double totalMarks, Boolean passed) {
        try (Connection con = DBConnection.getConnection()) {
            return saveAttemptWithAnswersReturningId(
                    con, userId, subjectId, score, totalQuestions,
                    timeTakenSeconds, submissionType, answerRecords,
                    examId, marksObtained, totalMarks, passed);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static boolean saveAttemptWithAnswers(
            Connection con,
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType,
            List<AttemptAnswerRecord> answerRecords) throws SQLException {
        return saveAttemptWithAnswers(
                con, userId, subjectId, score, totalQuestions,
                timeTakenSeconds, submissionType, answerRecords,
                null, null, null, null);
    }

    public static boolean saveAttemptWithAnswers(
            Connection con,
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType,
            List<AttemptAnswerRecord> answerRecords,
            Integer examId, Double marksObtained, Double totalMarks, Boolean passed) throws SQLException {
        return saveAttemptWithAnswersReturningId(
                con, userId, subjectId, score, totalQuestions,
                timeTakenSeconds, submissionType, answerRecords,
                examId, marksObtained, totalMarks, passed) > 0;
    }

    public static int saveAttemptWithAnswersReturningId(
            Connection con,
            String userId, int subjectId, int score, int totalQuestions,
            int timeTakenSeconds, String submissionType,
            List<AttemptAnswerRecord> answerRecords,
            Integer examId, Double marksObtained, Double totalMarks, Boolean passed) throws SQLException {
        if (con == null) {
            throw new SQLException("Database connection is null.");
        }

        String attemptSql = "INSERT INTO exam_attempts(user_id, subject_id, score, total_questions, "
                + "time_taken_seconds, submission_type, exam_id, marks_obtained, total_marks, passed) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String answerSql = "INSERT INTO exam_attempt_answers(attempt_id, question_id, selected_option, "
                + "correct_option, is_correct, selected_options, answer_text) VALUES (?, ?, ?, ?, ?, ?, ?)";

        String normalizedUser = (userId == null || userId.trim().isEmpty()) ? "Unknown" : userId.trim();
        String normalizedType = (submissionType == null || submissionType.trim().isEmpty())
                ? ExamStore.SUBMISSION_MANUAL
                : submissionType.trim();
        int safeScore = Math.max(0, score);
        int safeTotal = Math.max(safeScore, totalQuestions > 0 ? totalQuestions : ExamStore.DEFAULT_EXAM_QUESTION_COUNT);

        boolean previousAutoCommit = con.getAutoCommit();
        con.setAutoCommit(false);

        try {
            int generatedAttemptId;

            try (PreparedStatement psAttempt = con.prepareStatement(attemptSql, Statement.RETURN_GENERATED_KEYS)) {
                psAttempt.setString(1, normalizedUser);
                psAttempt.setInt(2, subjectId);
                psAttempt.setInt(3, safeScore);
                psAttempt.setInt(4, safeTotal);
                psAttempt.setInt(5, Math.max(0, timeTakenSeconds));
                psAttempt.setString(6, normalizedType);
                if (examId == null || examId <= 0) {
                    psAttempt.setNull(7, Types.INTEGER);
                } else {
                    psAttempt.setInt(7, examId);
                }
                if (marksObtained == null) {
                    psAttempt.setNull(8, Types.DECIMAL);
                } else {
                    psAttempt.setBigDecimal(8, BigDecimal.valueOf(roundTwoDecimals(marksObtained)));
                }
                if (totalMarks == null) {
                    psAttempt.setNull(9, Types.DECIMAL);
                } else {
                    psAttempt.setBigDecimal(9, BigDecimal.valueOf(roundTwoDecimals(totalMarks)));
                }
                if (passed == null) {
                    psAttempt.setNull(10, Types.TINYINT);
                } else {
                    psAttempt.setInt(10, passed ? 1 : 0);
                }

                int insertedRows = psAttempt.executeUpdate();
                if (insertedRows != 1) {
                    throw new SQLException("Expected 1 inserted exam_attempts row, got: " + insertedRows);
                }

                try (ResultSet generatedKeys = psAttempt.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new SQLException("Failed to retrieve generated attempt ID.");
                    }
                    generatedAttemptId = generatedKeys.getInt(1);
                }
            }

            if (answerRecords != null && !answerRecords.isEmpty()) {
                try (PreparedStatement psAnswers = con.prepareStatement(answerSql)) {
                    for (AttemptAnswerRecord record : answerRecords) {
                        if (record == null) {
                            throw new SQLException("Null answer record encountered during exam submission.");
                        }
                        if (record.correctOption < 1 || record.correctOption > 4) {
                            throw new SQLException("Invalid correct option: " + record.correctOption);
                        }
                        if (record.selectedOption != null
                                && (record.selectedOption < 1 || record.selectedOption > 4)) {
                            throw new SQLException("Invalid selected option: " + record.selectedOption);
                        }

                        psAnswers.setInt(1, generatedAttemptId);
                        psAnswers.setInt(2, record.questionId);
                        if (record.selectedOption == null) {
                            psAnswers.setNull(3, Types.INTEGER);
                        } else {
                            psAnswers.setInt(3, record.selectedOption);
                        }
                        psAnswers.setInt(4, record.correctOption);
                        psAnswers.setBoolean(5, record.isCorrect);
                        if (record.selectedOptions == null || record.selectedOptions.trim().isEmpty()) {
                            psAnswers.setNull(6, Types.VARCHAR);
                        } else {
                            psAnswers.setString(6, record.selectedOptions.trim());
                        }
                        if (record.answerText == null || record.answerText.trim().isEmpty()) {
                            psAnswers.setNull(7, Types.VARCHAR);
                        } else {
                            psAnswers.setString(7, record.answerText.trim());
                        }
                        psAnswers.addBatch();
                    }

                    int[] batchResults = psAnswers.executeBatch();
                    for (int result : batchResults) {
                        if (result == Statement.EXECUTE_FAILED) {
                            throw new SQLException("Batch insert failed for exam_attempt_answers.");
                        }
                    }
                }
            }

            con.commit();
            return generatedAttemptId;

        } catch (SQLException | RuntimeException ex) {
            try {
                con.rollback();
            } catch (SQLException rollbackEx) {
                ex.addSuppressed(rollbackEx);
            }
            if (ex instanceof SQLException) {
                throw (SQLException) ex;
            }
            throw new SQLException("Exam submission transaction failed and was rolled back.", ex);
        } finally {
            try {
                con.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    public static List<AttemptAnswerRecord> getAttemptAnswers(int attemptId) {
        List<AttemptAnswerRecord> answers = new ArrayList<>();
        String sql = "SELECT id, attempt_id, question_id, selected_option, correct_option, is_correct "
                + "FROM exam_attempt_answers WHERE attempt_id = ? ORDER BY id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int aid = rs.getInt("attempt_id");
                    int qid = rs.getInt("question_id");
                    int selRaw = rs.getInt("selected_option");
                    Integer selectedOption = rs.wasNull() ? null : selRaw;
                    int correctOption = rs.getInt("correct_option");
                    boolean isCorrect = rs.getBoolean("is_correct");
                    answers.add(new AttemptAnswerRecord(id, aid, qid, selectedOption, correctOption, isCorrect));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return answers;
    }

    public static List<HistoricalReviewItem> getAttemptReviewItems(int attemptId) {
        List<HistoricalReviewItem> items = new ArrayList<>();
        String sql = "SELECT a.question_id, a.selected_option, a.correct_option, a.is_correct, "
                + "a.selected_options, a.answer_text, "
                + "q.question, q.option1, q.option2, q.option3, q.option4, q.explanation, q.difficulty, "
                + "q.question_type, q.correct_options, q.correct_text, (q.image_data IS NOT NULL) AS has_image "
                + "FROM exam_attempt_answers a "
                + "LEFT JOIN questions q ON a.question_id = q.id "
                + "WHERE a.attempt_id = ? "
                + "ORDER BY a.id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int qid = rs.getInt("question_id");
                    int selRaw = rs.getInt("selected_option");
                    Integer selectedOption = rs.wasNull() ? null : selRaw;
                    int correctOption = rs.getInt("correct_option");
                    boolean isCorrect = rs.getBoolean("is_correct");
                    String selectedOptions = rs.getString("selected_options");
                    String answerText = rs.getString("answer_text");
                    String qText = rs.getString("question");
                    if (qText == null || qText.trim().isEmpty()) {
                        qText = "Question #" + qid + " (no longer in question bank)";
                    }
                    String[] opts = {
                            rs.getString("option1") != null ? rs.getString("option1") : "",
                            rs.getString("option2") != null ? rs.getString("option2") : "",
                            rs.getString("option3") != null ? rs.getString("option3") : "",
                            rs.getString("option4") != null ? rs.getString("option4") : ""
                    };
                    String explanation = rs.getString("explanation");
                    if (explanation != null && explanation.trim().isEmpty()) {
                        explanation = null;
                    }
                    String difficulty = ExamStore.normalizeDifficulty(rs.getString("difficulty"));
                    String questionType = ExamStore.normalizeQuestionType(rs.getString("question_type"));
                    String correctOptions = rs.getString("correct_options");
                    String correctText = rs.getString("correct_text");
                    boolean hasImage = rs.getInt("has_image") != 0;
                    items.add(new HistoricalReviewItem(
                            qid, qText, opts, selectedOption, correctOption, isCorrect, explanation, difficulty,
                            questionType, selectedOptions, answerText, correctOptions, correctText, hasImage));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    /**
     * Computes difficulty-wise correct % across recorded attempt answers (optionally filtered by subject).
     */
    public static Map<String, DifficultyAccuracyStat> getDifficultyAccuracyStats(Integer subjectId) {
        Map<String, DifficultyAccuracyStat> stats = new LinkedHashMap<>();
        stats.put(ExamStore.DIFFICULTY_EASY, new DifficultyAccuracyStat(ExamStore.DIFFICULTY_EASY, 0, 0));
        stats.put(ExamStore.DIFFICULTY_MEDIUM, new DifficultyAccuracyStat(ExamStore.DIFFICULTY_MEDIUM, 0, 0));
        stats.put(ExamStore.DIFFICULTY_HARD, new DifficultyAccuracyStat(ExamStore.DIFFICULTY_HARD, 0, 0));

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(UPPER(q.difficulty), 'MEDIUM') AS diff, ")
           .append("SUM(CASE WHEN a.is_correct = 1 THEN 1 ELSE 0 END) AS correct_cnt, ")
           .append("COUNT(*) AS total_cnt ")
           .append("FROM exam_attempt_answers a ")
           .append("INNER JOIN exam_attempts ea ON a.attempt_id = ea.id ")
           .append("LEFT JOIN questions q ON a.question_id = q.id ");
        if (subjectId != null) {
            sql.append("WHERE ea.subject_id = ? ");
        }
        sql.append("GROUP BY COALESCE(UPPER(q.difficulty), 'MEDIUM')");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (subjectId != null) {
                ps.setInt(1, subjectId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String diff = ExamStore.normalizeDifficulty(rs.getString("diff"));
                    int correct = rs.getInt("correct_cnt");
                    int total = rs.getInt("total_cnt");
                    DifficultyAccuracyStat existing = stats.get(diff);
                    if (existing != null) {
                        stats.put(diff, new DifficultyAccuracyStat(diff, existing.correctCount + correct, existing.totalCount + total));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public static void loadAttemptsFromDB() {
        String sql = "SELECT user_id, subject_id, score, total_questions, time_taken_seconds, "
                + "submission_type, attempted_at FROM exam_attempts ORDER BY id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ExamStore.clearAttemptRecords();

            while (rs.next()) {
                String userId = rs.getString("user_id");
                int subjectId = rs.getInt("subject_id");
                int score = rs.getInt("score");
                int totalQuestions = resolveTotalQuestions(score, rs.getInt("total_questions"));
                int timeTakenSeconds = rs.getInt("time_taken_seconds");
                String submissionType = rs.getString("submission_type");
                Timestamp attemptedAt = rs.getTimestamp("attempted_at");
                String attemptedDate = formatDate(attemptedAt);
                ExamStore.addAttemptRecordFromDB(
                        userId, subjectId, score, totalQuestions, attemptedDate, timeTakenSeconds, submissionType);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= PERFORMANCE METRICS HELPERS =================

    public static int resolveTotalQuestions(int score, int storedTotalQuestions) {
        int baseTotal = storedTotalQuestions > 0 ? storedTotalQuestions : ExamStore.DEFAULT_EXAM_QUESTION_COUNT;
        return Math.max(Math.max(0, score), baseTotal);
    }

    public static double calculatePercentage(int score, int totalQuestions) {
        return calculatePercentage((double) score, (double) totalQuestions);
    }

    public static double calculatePercentage(double marks, double totalMarks) {
        if (totalMarks <= 0.0) {
            return 0.0;
        }
        double effectiveMarks = Math.max(0.0, marks);
        double raw = (effectiveMarks * 100.0) / totalMarks;
        double clamped = Math.min(100.0, Math.max(0.0, raw));
        return roundTwoDecimals(clamped);
    }

    public static double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static String calculateGrade(double percentage) {
        double rounded = roundTwoDecimals(percentage);
        if (rounded >= 90.0) {
            return "A";
        } else if (rounded >= 80.0) {
            return "B";
        } else if (rounded >= 70.0) {
            return "C";
        } else if (rounded >= 60.0) {
            return "D";
        }
        return "F";
    }

    public static String calculatePassStatus(double percentage) {
        return calculatePassStatus(percentage, PASS_PERCENTAGE);
    }

    public static String calculatePassStatus(double percentage, double passMark) {
        return roundTwoDecimals(percentage) >= roundTwoDecimals(passMark) ? "Pass" : "Fail";
    }

    public static void assignLeaderboardRanks(List<StudentLeaderboardEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        entries.sort(Comparator
                .comparingLong((StudentLeaderboardEntry e) -> Math.round(e.averagePercentage * 100.0)).reversed()
                .thenComparing(Comparator.comparingInt((StudentLeaderboardEntry e) -> e.highestScore).reversed())
                .thenComparing(Comparator.comparingDouble((StudentLeaderboardEntry e) -> e.averageScore).reversed())
                .thenComparing(Comparator.comparingInt((StudentLeaderboardEntry e) -> e.totalAttempts).reversed())
                .thenComparing(e -> e.userId, String.CASE_INSENSITIVE_ORDER));

        int currentRank = 0;
        long previousPctScaled = Long.MIN_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            StudentLeaderboardEntry entry = entries.get(i);
            long pctScaled = Math.round(entry.averagePercentage * 100.0);
            if (i == 0 || pctScaled != previousPctScaled) {
                currentRank = i + 1;
                previousPctScaled = pctScaled;
            }
            entry.rank = currentRank;
        }
    }

    public static void assignAttemptRanks(List<AttemptDetail> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return;
        }
        attempts.sort(Comparator
                .comparingLong((AttemptDetail a) -> Math.round(a.percentage * 100.0)).reversed()
                .thenComparing(Comparator.comparingInt((AttemptDetail a) -> a.score).reversed())
                .thenComparingInt(a -> a.timeTakenSeconds)
                .thenComparingInt(a -> a.id));

        int currentRank = 0;
        long previousPctScaled = Long.MIN_VALUE;
        for (int i = 0; i < attempts.size(); i++) {
            AttemptDetail attempt = attempts.get(i);
            long pctScaled = Math.round(attempt.percentage * 100.0);
            if (i == 0 || pctScaled != previousPctScaled) {
                currentRank = i + 1;
                previousPctScaled = pctScaled;
            }
            attempt.rank = currentRank;
        }
    }

    // ================= MYSQL PERFORMANCE QUERIES =================

    public static Map<Integer, String> getAvailableSubjects() {
        Map<Integer, String> subjects = new LinkedHashMap<>();
        String sql = "SELECT id, subject_name FROM subjects ORDER BY id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                subjects.put(rs.getInt("id"), rs.getString("subject_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (subjects.isEmpty()) {
            subjects.put(ExamStore.JAVA, ExamStore.getSubjectName(ExamStore.JAVA));
            subjects.put(ExamStore.DBMS, ExamStore.getSubjectName(ExamStore.DBMS));
            subjects.put(ExamStore.SQL, ExamStore.getSubjectName(ExamStore.SQL));
            subjects.put(ExamStore.APTITUDE, ExamStore.getSubjectName(ExamStore.APTITUDE));
        }
        return subjects;
    }

    public static List<StudentLeaderboardEntry> getStudentLeaderboard(Integer subjectId) {
        List<StudentLeaderboardEntry> entries = new ArrayList<>();
        boolean filterSubject = subjectId != null && subjectId > 0;

        String sql = "SELECT user_id, "
                + "COUNT(*) AS total_attempts, "
                + "AVG(COALESCE(marks_obtained, score)) AS avg_score, "
                + "MAX(ROUND(COALESCE(marks_obtained, score))) AS highest_score, "
                + "MIN(ROUND(COALESCE(marks_obtained, score))) AS lowest_score, "
                + "AVG(CASE "
                + "    WHEN COALESCE(total_marks, CASE WHEN total_questions > 0 THEN total_questions ELSE 20 END) > 0 "
                + "    THEN LEAST(100.0, GREATEST(0.0, (GREATEST(0.0, COALESCE(marks_obtained, score)) * 100.0) / "
                + "         COALESCE(total_marks, CASE WHEN total_questions > 0 THEN total_questions ELSE 20 END))) "
                + "    ELSE 0 "
                + "END) AS avg_percentage "
                + "FROM exam_attempts "
                + (filterSubject ? "WHERE subject_id = ? " : "")
                + "GROUP BY user_id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (filterSubject) {
                ps.setInt(1, subjectId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("user_id");
                    int totalAttempts = rs.getInt("total_attempts");
                    double avgScore = roundTwoDecimals(rs.getDouble("avg_score"));
                    int highestScore = rs.getInt("highest_score");
                    int lowestScore = rs.getInt("lowest_score");
                    double avgPct = roundTwoDecimals(Math.min(100.0, Math.max(0.0, rs.getDouble("avg_percentage"))));

                    entries.add(new StudentLeaderboardEntry(
                            userId,
                            totalAttempts,
                            avgScore,
                            highestScore,
                            lowestScore,
                            avgPct,
                            calculateGrade(avgPct),
                            calculatePassStatus(avgPct)
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        assignLeaderboardRanks(entries);
        return entries;
    }

    public static List<AttemptDetail> getAttemptDetails(Integer subjectId, String userId, boolean rankByPercentage) {
        List<AttemptDetail> details = new ArrayList<>();
        Map<Integer, String> subjectMap = getAvailableSubjects();

        boolean filterSubject = subjectId != null && subjectId > 0;
        boolean filterUser = userId != null && !userId.trim().isEmpty();

        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.user_id, a.subject_id, a.score, a.total_questions, a.time_taken_seconds, "
                        + "a.submission_type, a.attempted_at, a.exam_id, a.marks_obtained, a.total_marks, a.passed, "
                        + "e.title AS exam_title, e.pass_percentage AS exam_pass_pct, "
                        + "e.negative_marking AS exam_neg_mark, e.marks_per_question AS exam_mpq, "
                        + "e.show_answers AS exam_show_answers, e.end_at AS exam_end_at "
                        + "FROM exam_attempts a "
                        + "LEFT JOIN exams e ON a.exam_id = e.id "
                        + "WHERE 1=1");
        if (filterSubject) {
            sql.append(" AND a.subject_id = ?");
        }
        if (filterUser) {
            sql.append(" AND a.user_id = ?");
        }
        sql.append(" ORDER BY a.attempted_at DESC, a.id DESC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (filterSubject) {
                ps.setInt(paramIndex++, subjectId);
            }
            if (filterUser) {
                ps.setString(paramIndex, userId.trim());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String uid = rs.getString("user_id");
                    int sid = rs.getInt("subject_id");
                    int score = rs.getInt("score");
                    int totalQuestions = resolveTotalQuestions(score, rs.getInt("total_questions"));
                    int timeTaken = rs.getInt("time_taken_seconds");
                    String subType = rs.getString("submission_type");
                    String dateStr = formatDate(rs.getTimestamp("attempted_at"));
                    String subjectName = subjectMap.getOrDefault(sid, ExamStore.getSubjectName(sid));

                    int rawExamId = rs.getInt("exam_id");
                    Integer examId = rs.wasNull() ? null : rawExamId;

                    BigDecimal marksObj = rs.getBigDecimal("marks_obtained");
                    double marksObtained = marksObj != null ? marksObj.doubleValue() : score;

                    BigDecimal totalMarksObj = rs.getBigDecimal("total_marks");
                    double totalMarks = (totalMarksObj != null && totalMarksObj.doubleValue() > 0)
                            ? totalMarksObj.doubleValue()
                            : totalQuestions;

                    BigDecimal examPassObj = rs.getBigDecimal("exam_pass_pct");
                    double passMark = examPassObj != null ? examPassObj.doubleValue() : PASS_PERCENTAGE;

                    double pct = calculatePercentage(marksObtained, totalMarks);

                    int rawPassed = rs.getInt("passed");
                    boolean passedBool = rs.wasNull()
                            ? "Pass".equalsIgnoreCase(calculatePassStatus(pct, passMark))
                            : (rawPassed == 1);
                    String statusStr = passedBool ? "Pass" : "Fail";

                    String examTitle = rs.getString("exam_title");
                    if (examTitle == null || examTitle.trim().isEmpty()) {
                        examTitle = subjectName + " Exam";
                    }

                    BigDecimal negObj = rs.getBigDecimal("exam_neg_mark");
                    double negMark = negObj != null ? negObj.doubleValue() : 0.0;

                    BigDecimal mpqObj = rs.getBigDecimal("exam_mpq");
                    double mpq = mpqObj != null ? mpqObj.doubleValue() : 1.0;

                    String showAnswers = rs.getString("exam_show_answers");
                    if (showAnswers == null || showAnswers.trim().isEmpty()) {
                        showAnswers = ExamConfig.SHOW_IMMEDIATELY;
                    }
                    Timestamp endTs = rs.getTimestamp("exam_end_at");
                    LocalDateTime examEndAt = endTs != null ? endTs.toLocalDateTime() : null;

                    details.add(new AttemptDetail(
                            id,
                            uid,
                            sid,
                            subjectName,
                            score,
                            totalQuestions,
                            pct,
                            calculateGrade(pct),
                            statusStr,
                            timeTaken,
                            (subType == null || subType.trim().isEmpty()) ? ExamStore.SUBMISSION_MANUAL : subType.trim(),
                            dateStr,
                            examId,
                            examTitle,
                            marksObtained,
                            totalMarks,
                            passMark,
                            negMark,
                            mpq,
                            showAnswers,
                            examEndAt,
                            passedBool
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (rankByPercentage) {
            assignAttemptRanks(details);
        }
        return details;
    }

    public static AttemptDetail getAttemptById(int attemptId) {
        if (attemptId <= 0) return null;
        for (AttemptDetail d : getAttemptDetails(null, null, false)) {
            if (d.id == attemptId) {
                return d;
            }
        }
        return null;
    }

    public static StudentPerformanceSummary getStudentPerformanceSummary(String userId) {
        return getStudentPerformanceSummary(userId, null);
    }

    public static StudentPerformanceSummary getStudentPerformanceSummary(String userId, Integer subjectId) {
        String safeUserId = userId == null ? "" : userId.trim();
        if (safeUserId.isEmpty()) {
            return StudentPerformanceSummary.empty(safeUserId);
        }

        List<StudentLeaderboardEntry> leaderboard = getStudentLeaderboard(subjectId);
        for (StudentLeaderboardEntry entry : leaderboard) {
            if (entry.userId.equalsIgnoreCase(safeUserId)) {
                return new StudentPerformanceSummary(
                        entry.userId,
                        true,
                        entry.rank,
                        leaderboard.size(),
                        entry.totalAttempts,
                        entry.averageScore,
                        entry.highestScore,
                        entry.lowestScore,
                        entry.averagePercentage,
                        entry.grade,
                        entry.status
                );
            }
        }

        return StudentPerformanceSummary.empty(safeUserId);
    }

    private static String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }

        LocalDateTime dateTime = timestamp.toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy | hh:mm a");
        return dateTime.format(formatter);
    }

    // ================= DATA MODELS =================

    public static class StudentLeaderboardEntry {
        public int rank;
        public String userId;
        public int totalAttempts;
        public double averageScore;
        public int highestScore;
        public int lowestScore;
        public double averagePercentage;
        public String grade;
        public String status;

        public StudentLeaderboardEntry(
                String userId, int totalAttempts, double averageScore, int highestScore,
                int lowestScore, double averagePercentage, String grade, String status) {
            this.userId = (userId == null || userId.trim().isEmpty()) ? "Unknown" : userId.trim();
            this.totalAttempts = Math.max(0, totalAttempts);
            this.averageScore = averageScore;
            this.highestScore = highestScore;
            this.lowestScore = lowestScore;
            this.averagePercentage = averagePercentage;
            this.grade = grade;
            this.status = status;
        }
    }

    public static class AttemptDetail {
        public int rank;
        public int id;
        public String userId;
        public int subjectId;
        public String subjectName;
        public int score;
        public int totalQuestions;
        public double percentage;
        public String grade;
        public String status;
        public int timeTakenSeconds;
        public String submissionType;
        public String attemptedDate;
        public Integer examId;
        public String examTitle;
        public double marksObtained;
        public double totalMarks;
        public double passPercentage;
        public double negativeMarking;
        public double marksPerQuestion;
        public String showAnswers;
        public LocalDateTime examEndAt;
        public boolean passed;

        public AttemptDetail(
                int id, String userId, int subjectId, String subjectName, int score, int totalQuestions,
                double percentage, String grade, String status, int timeTakenSeconds,
                String submissionType, String attemptedDate) {
            this(id, userId, subjectId, subjectName, score, totalQuestions, percentage, grade, status,
                    timeTakenSeconds, submissionType, attemptedDate, null, subjectName + " Exam",
                    score, totalQuestions, PASS_PERCENTAGE, 0.0, 1.0, ExamConfig.SHOW_IMMEDIATELY, null,
                    "Pass".equalsIgnoreCase(status));
        }

        public AttemptDetail(
                int id, String userId, int subjectId, String subjectName, int score, int totalQuestions,
                double percentage, String grade, String status, int timeTakenSeconds,
                String submissionType, String attemptedDate,
                Integer examId, String examTitle, double marksObtained, double totalMarks,
                double passPercentage, double negativeMarking, double marksPerQuestion,
                String showAnswers, LocalDateTime examEndAt, boolean passed) {
            this.id = id;
            this.userId = (userId == null || userId.trim().isEmpty()) ? "Unknown" : userId.trim();
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.score = score;
            this.totalQuestions = totalQuestions;
            this.percentage = percentage;
            this.grade = grade;
            this.status = status;
            this.timeTakenSeconds = Math.max(0, timeTakenSeconds);
            this.submissionType = submissionType;
            this.attemptedDate = attemptedDate;
            this.examId = examId;
            this.examTitle = (examTitle == null || examTitle.trim().isEmpty()) ? (subjectName + " Exam") : examTitle.trim();
            this.marksObtained = marksObtained;
            this.totalMarks = totalMarks > 0 ? totalMarks : totalQuestions;
            this.passPercentage = passPercentage;
            this.negativeMarking = negativeMarking;
            this.marksPerQuestion = marksPerQuestion > 0 ? marksPerQuestion : 1.0;
            this.showAnswers = (showAnswers == null || showAnswers.trim().isEmpty()) ? ExamConfig.SHOW_IMMEDIATELY : showAnswers;
            this.examEndAt = examEndAt;
            this.passed = passed;
        }
    }

    public static class StudentPerformanceSummary {
        public String userId;
        public boolean hasAttempts;
        public int rank;
        public int totalRankedStudents;
        public int totalAttempts;
        public double averageScore;
        public int highestScore;
        public int lowestScore;
        public double averagePercentage;
        public String grade;
        public String status;

        public StudentPerformanceSummary(
                String userId, boolean hasAttempts, int rank, int totalRankedStudents,
                int totalAttempts, double averageScore, int highestScore, int lowestScore,
                double averagePercentage, String grade, String status) {
            this.userId = userId;
            this.hasAttempts = hasAttempts;
            this.rank = rank;
            this.totalRankedStudents = totalRankedStudents;
            this.totalAttempts = totalAttempts;
            this.averageScore = averageScore;
            this.highestScore = highestScore;
            this.lowestScore = lowestScore;
            this.averagePercentage = averagePercentage;
            this.grade = grade;
            this.status = status;
        }

        public static StudentPerformanceSummary empty(String userId) {
            return new StudentPerformanceSummary(
                    userId, false, 0, 0, 0, 0.0, 0, 0, 0.0, "-", "No Attempts");
        }
    }

    public static class AttemptAnswerRecord {
        public int id;
        public int attemptId;
        public int questionId;
        public Integer selectedOption;
        public int correctOption;
        public boolean isCorrect;
        public String selectedOptions;
        public String answerText;

        public AttemptAnswerRecord(
                int questionId, Integer selectedOption, int correctOption, boolean isCorrect) {
            this(0, 0, questionId, selectedOption, correctOption, isCorrect, null, null);
        }

        public AttemptAnswerRecord(
                int id, int attemptId, int questionId,
                Integer selectedOption, int correctOption, boolean isCorrect) {
            this(id, attemptId, questionId, selectedOption, correctOption, isCorrect, null, null);
        }

        public AttemptAnswerRecord(
                int id, int attemptId, int questionId,
                Integer selectedOption, int correctOption, boolean isCorrect,
                String selectedOptions, String answerText) {
            this.id = id;
            this.attemptId = attemptId;
            this.questionId = questionId;
            this.selectedOption = selectedOption;
            this.correctOption = correctOption;
            this.isCorrect = isCorrect;
            this.selectedOptions = selectedOptions;
            this.answerText = answerText;
        }
    }

    public static class DifficultyAccuracyStat {
        public final String difficulty;
        public final int correctCount;
        public final int totalCount;
        public final double correctPercentage;

        public DifficultyAccuracyStat(String difficulty, int correctCount, int totalCount) {
            this.difficulty = ExamStore.normalizeDifficulty(difficulty);
            this.correctCount = Math.max(0, correctCount);
            this.totalCount = Math.max(0, totalCount);
            this.correctPercentage = this.totalCount > 0
                    ? roundTwoDecimals((this.correctCount * 100.0) / this.totalCount)
                    : 0.0;
        }
    }

    public static class HistoricalReviewItem {
        public final int questionId;
        public final String questionText;
        public final String[] options;
        public final Integer selectedOption; // 1..4 or null
        public final int correctOption;      // 1..4
        public final boolean isCorrect;
        public final String explanation;
        public final String difficulty;
        public final String questionType;
        public final String selectedOptions; // "1,3" or null
        public final String answerText;      // fill-blank student answer or null
        public final String correctOptions;  // "1,3" or null
        public final String correctText;     // pipe-separated accepted answers or null
        public final boolean hasImage;

        public HistoricalReviewItem(
                int questionId, String questionText, String[] options,
                Integer selectedOption, int correctOption, boolean isCorrect) {
            this(questionId, questionText, options, selectedOption, correctOption, isCorrect,
                    null, ExamStore.DIFFICULTY_MEDIUM, ExamStore.TYPE_SINGLE, null, null, null, null, false);
        }

        public HistoricalReviewItem(
                int questionId, String questionText, String[] options,
                Integer selectedOption, int correctOption, boolean isCorrect, String explanation) {
            this(questionId, questionText, options, selectedOption, correctOption, isCorrect,
                    explanation, ExamStore.DIFFICULTY_MEDIUM, ExamStore.TYPE_SINGLE, null, null, null, null, false);
        }

        public HistoricalReviewItem(
                int questionId, String questionText, String[] options,
                Integer selectedOption, int correctOption, boolean isCorrect, String explanation, String difficulty) {
            this(questionId, questionText, options, selectedOption, correctOption, isCorrect,
                    explanation, difficulty, ExamStore.TYPE_SINGLE, null, null, null, null, false);
        }

        public HistoricalReviewItem(
                int questionId, String questionText, String[] options,
                Integer selectedOption, int correctOption, boolean isCorrect,
                String explanation, String difficulty, String questionType,
                String selectedOptions, String answerText, String correctOptions,
                String correctText, boolean hasImage) {
            this.questionId = questionId;
            this.questionText = questionText;
            this.options = options;
            this.selectedOption = selectedOption;
            this.correctOption = correctOption;
            this.isCorrect = isCorrect;
            this.explanation = (explanation == null || explanation.trim().isEmpty()) ? null : explanation.trim();
            this.difficulty = ExamStore.normalizeDifficulty(difficulty);
            this.questionType = ExamStore.normalizeQuestionType(questionType);
            this.selectedOptions = (selectedOptions == null || selectedOptions.trim().isEmpty()) ? null : selectedOptions.trim();
            this.answerText = (answerText == null || answerText.trim().isEmpty()) ? null : answerText.trim();
            this.correctOptions = (correctOptions == null || correctOptions.trim().isEmpty()) ? null : correctOptions.trim();
            this.correctText = (correctText == null || correctText.trim().isEmpty()) ? null : correctText.trim();
            this.hasImage = hasImage;
        }
    }
}
