package com.onlineexam.dao;

import com.onlineexam.data.ExamStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL-aggregated question-level analytics DAO for administrators.
 * Computes ask counts, correctness %, skip counts, original-option distributions,
 * misleading distractors, and HARD / TOO_EASY / LOW_DATA flags directly via SQL GROUP BY.
 */
public class QuestionAnalyticsDAO {

    public static final double HARD_THRESHOLD_PCT = 20.0;
    public static final double TOO_EASY_THRESHOLD_PCT = 95.0;
    public static final int DEFAULT_MIN_SAMPLE = 5;

    public enum QuestionFlag {
        HARD("HARD"),
        TOO_EASY("TOO EASY"),
        OK("OK"),
        LOW_DATA("Low data");

        public final String displayLabel;

        QuestionFlag(String displayLabel) {
            this.displayLabel = displayLabel;
        }
    }

    public static class QuestionStat {
        public final int questionId;
        public final int subjectId;
        public final String subjectName;
        public final String questionText;
        public final boolean deleted;
        public final String difficulty;
        public final String questionType;
        public final String[] options;
        public final int correctOption;       // 1..4 original DB option number
        public final int timesAsked;
        public final int timesCorrect;
        public final int timesSkipped;
        public final double percentCorrect;
        public final int[] optionCounts;      // counts for ORIGINAL options 1..4
        public final int mostChosenWrongOption; // 1..4 or 0 if none / not applicable
        public final int mostChosenWrongCount;
        public final double mostChosenWrongPct;

        public QuestionStat(
                int questionId, int subjectId, String subjectName, String questionText, boolean deleted,
                String difficulty, String questionType, String[] options, int correctOption,
                int timesAsked, int timesCorrect, int timesSkipped, double percentCorrect,
                int[] optionCounts, int mostChosenWrongOption, int mostChosenWrongCount, double mostChosenWrongPct) {
            this.questionId = questionId;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.questionText = questionText;
            this.deleted = deleted;
            this.difficulty = difficulty;
            this.questionType = questionType;
            this.options = options;
            this.correctOption = correctOption;
            this.timesAsked = timesAsked;
            this.timesCorrect = timesCorrect;
            this.timesSkipped = timesSkipped;
            this.percentCorrect = percentCorrect;
            this.optionCounts = optionCounts;
            this.mostChosenWrongOption = mostChosenWrongOption;
            this.mostChosenWrongCount = mostChosenWrongCount;
            this.mostChosenWrongPct = mostChosenWrongPct;
        }

        public QuestionFlag evaluateFlag(int minSample) {
            int threshold = Math.max(1, minSample);
            if (timesAsked < threshold) {
                return QuestionFlag.LOW_DATA;
            }
            if (percentCorrect < HARD_THRESHOLD_PCT) {
                return QuestionFlag.HARD;
            }
            if (percentCorrect > TOO_EASY_THRESHOLD_PCT) {
                return QuestionFlag.TOO_EASY;
            }
            return QuestionFlag.OK;
        }

        public boolean supportsOptionDistribution() {
            return ExamStore.TYPE_SINGLE.equals(questionType) || ExamStore.TYPE_TRUE_FALSE.equals(questionType);
        }

        public String getMostChosenWrongLetter() {
            if (mostChosenWrongOption < 1 || mostChosenWrongOption > 4) return "-";
            return String.valueOf((char) ('A' + (mostChosenWrongOption - 1)));
        }
    }

    /**
     * Aggregates per-question statistics in SQL using LEFT JOINs so deleted questions
     * still appear as "Question #id (deleted)".
     */
    public static List<QuestionStat> getQuestionStats(Integer subjectId, Integer examId, String difficulty) {
        List<QuestionStat> results = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("a.question_id, ")
           .append("MAX(q.id) AS existing_q_id, ")
           .append("MAX(COALESCE(q.subject_id, ea.subject_id, 0)) AS subj_id, ")
           .append("MAX(s.subject_name) AS subj_name, ")
           .append("MAX(q.question) AS q_text, ")
           .append("MAX(q.difficulty) AS q_diff, ")
           .append("MAX(q.question_type) AS q_type, ")
           .append("MAX(q.option1) AS opt1, ")
           .append("MAX(q.option2) AS opt2, ")
           .append("MAX(q.option3) AS opt3, ")
           .append("MAX(q.option4) AS opt4, ")
           .append("MAX(COALESCE(q.correct_option, a.correct_option, 1)) AS corr_opt, ")
           .append("COUNT(*) AS times_asked, ")
           .append("SUM(CASE WHEN a.is_correct = 1 THEN 1 ELSE 0 END) AS times_correct, ")
           .append("SUM(CASE WHEN a.selected_option IS NULL ")
           .append("    AND (a.selected_options IS NULL OR TRIM(a.selected_options) = '') ")
           .append("    AND (a.answer_text IS NULL OR TRIM(a.answer_text) = '') ")
           .append("    THEN 1 ELSE 0 END) AS times_skipped, ")
           .append("ROUND(SUM(CASE WHEN a.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS percent_correct, ")
           .append("SUM(CASE WHEN a.selected_option = 1 THEN 1 ELSE 0 END) AS opt1_cnt, ")
           .append("SUM(CASE WHEN a.selected_option = 2 THEN 1 ELSE 0 END) AS opt2_cnt, ")
           .append("SUM(CASE WHEN a.selected_option = 3 THEN 1 ELSE 0 END) AS opt3_cnt, ")
           .append("SUM(CASE WHEN a.selected_option = 4 THEN 1 ELSE 0 END) AS opt4_cnt ")
           .append("FROM exam_attempt_answers a ")
           .append("LEFT JOIN questions q ON a.question_id = q.id ")
           .append("LEFT JOIN exam_attempts ea ON a.attempt_id = ea.id ")
           .append("LEFT JOIN subjects s ON COALESCE(q.subject_id, ea.subject_id) = s.id ")
           .append("WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (subjectId != null && subjectId > 0) {
            sql.append("AND COALESCE(q.subject_id, ea.subject_id) = ? ");
            params.add(subjectId);
        }
        if (examId != null && examId > 0) {
            sql.append("AND ea.exam_id = ? ");
            params.add(examId);
        }
        if (difficulty != null && !difficulty.trim().isEmpty() && !"ALL".equalsIgnoreCase(difficulty.trim())) {
            sql.append("AND COALESCE(UPPER(q.difficulty), 'MEDIUM') = ? ");
            params.add(difficulty.trim().toUpperCase(Locale.ROOT));
        }

        sql.append("GROUP BY a.question_id ")
           .append("ORDER BY percent_correct ASC, times_asked DESC, a.question_id ASC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int qid = rs.getInt("question_id");
                    rs.getInt("existing_q_id");
                    boolean deleted = rs.wasNull();

                    int subjId = rs.getInt("subj_id");
                    String subjName = rs.getString("subj_name");
                    if (subjName == null || subjName.trim().isEmpty()) {
                        subjName = subjId > 0 ? ExamStore.getSubjectName(subjId) : "Unknown";
                    }

                    String qText = rs.getString("q_text");
                    if (deleted || qText == null || qText.trim().isEmpty()) {
                        qText = "Question #" + qid + " (deleted)";
                    }

                    String qDiff = ExamStore.normalizeDifficulty(rs.getString("q_diff"));
                    String qType = ExamStore.normalizeQuestionType(rs.getString("q_type"));
                    int corrOpt = Math.max(1, Math.min(4, rs.getInt("corr_opt")));

                    String[] opts = {
                            safeText(rs.getString("opt1"), "Option A"),
                            safeText(rs.getString("opt2"), "Option B"),
                            safeText(rs.getString("opt3"), "Option C"),
                            safeText(rs.getString("opt4"), "Option D")
                    };

                    int asked = rs.getInt("times_asked");
                    int correct = rs.getInt("times_correct");
                    int skipped = rs.getInt("times_skipped");
                    double pctCorrect = rs.getDouble("percent_correct");

                    int[] optCounts = {
                            rs.getInt("opt1_cnt"),
                            rs.getInt("opt2_cnt"),
                            rs.getInt("opt3_cnt"),
                            rs.getInt("opt4_cnt")
                    };

                    int mostWrongOpt = 0;
                    int mostWrongCnt = 0;
                    double mostWrongPct = 0.0;
                    if (ExamStore.TYPE_SINGLE.equals(qType) || ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
                        int maxOpt = ExamStore.TYPE_TRUE_FALSE.equals(qType) ? 2 : 4;
                        for (int idx = 0; idx < maxOpt; idx++) {
                            int oneBased = idx + 1;
                            if (oneBased == corrOpt) continue;
                            if (optCounts[idx] > mostWrongCnt) {
                                mostWrongCnt = optCounts[idx];
                                mostWrongOpt = oneBased;
                            }
                        }
                        if (mostWrongCnt > 0 && asked > 0) {
                            mostWrongPct = AttemptDAO.roundTwoDecimals((mostWrongCnt * 100.0) / asked);
                        }
                    }

                    results.add(new QuestionStat(
                            qid, subjId, subjName, qText, deleted, qDiff, qType, opts, corrOpt,
                            asked, correct, skipped, pctCorrect, optCounts,
                            mostWrongOpt, mostWrongCnt, mostWrongPct
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    private static String safeText(String val, String fallback) {
        return (val == null || val.trim().isEmpty()) ? fallback : val.trim();
    }
}
