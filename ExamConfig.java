package com.onlineexam.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Per-exam configuration model backed by the `exams` table.
 */
public class ExamConfig {

    public static final String SHOW_IMMEDIATELY = "IMMEDIATELY";
    public static final String SHOW_AFTER_EXAM_END = "AFTER_EXAM_END";
    public static final String SHOW_NEVER = "NEVER";

    public static final DateTimeFormatter DISPLAY_DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    public static final DateTimeFormatter INPUT_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter SPINNER_DT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    public static final DateTimeFormatter TIME_HM = DateTimeFormatter.ofPattern("HH:mm");

    public int id;
    public int subjectId;
    public String subjectName;
    public String title;
    public String description;
    public int questionCount = ExamStore.DEFAULT_EXAM_QUESTION_COUNT;
    public Integer durationMinutes = null; // null = 1 minute per question (legacy rule)
    public double marksPerQuestion = 1.0;
    public double negativeMarking = 0.0;
    public double passPercentage = 40.0;
    public Integer maxAttempts = null;     // null = unlimited
    public String showAnswers = SHOW_IMMEDIATELY;
    public LocalDateTime startAt = null;
    public LocalDateTime endAt = null;
    public int easyPct = 0;
    public int mediumPct = 0;
    public int hardPct = 0;
    public boolean requireWebcam = false;
    public boolean active = true;
    public String createdAt;

    public ExamConfig() {
    }

    public boolean hasDifficultyMix() {
        int sum = easyPct + mediumPct + hardPct;
        return sum == 100 && (easyPct > 0 || mediumPct > 0 || hardPct > 0);
    }

    /**
     * Computes exact [easyCount, mediumCount, hardCount] using the Largest-Remainder Method
     * so counts always sum exactly to totalQuestions.
     */
    public static int[] computeDifficultyTargets(int totalQuestions, int easyPct, int mediumPct, int hardPct) {
        int n = Math.max(0, totalQuestions);
        int[] pcts = {Math.max(0, easyPct), Math.max(0, mediumPct), Math.max(0, hardPct)};
        int pctSum = pcts[0] + pcts[1] + pcts[2];
        if (n == 0 || pctSum <= 0) {
            return new int[]{0, 0, 0};
        }
        int[] floors = new int[3];
        double[] remainders = new double[3];
        int allocated = 0;
        for (int i = 0; i < 3; i++) {
            double exact = (n * (double) pcts[i]) / pctSum;
            floors[i] = (int) Math.floor(exact);
            remainders[i] = exact - floors[i];
            allocated += floors[i];
        }
        int leftover = n - allocated;
        // Tie-break order: Medium (1), Easy (0), Hard (2)
        int[] order = {1, 0, 2};
        for (int step = 0; step < leftover; step++) {
            int bestIdx = order[0];
            double bestRem = -1.0;
            for (int idx : order) {
                if (remainders[idx] > bestRem) {
                    bestRem = remainders[idx];
                    bestIdx = idx;
                }
            }
            floors[bestIdx]++;
            remainders[bestIdx] = -1.0;
        }
        return floors;
    }

    public int getEffectiveDurationMinutes(int actualQuestionCount) {
        if (durationMinutes != null && durationMinutes > 0) {
            return durationMinutes;
        }
        return Math.max(1, actualQuestionCount > 0 ? actualQuestionCount : questionCount);
    }

    public int getEffectiveDurationSeconds(int actualQuestionCount) {
        return getEffectiveDurationMinutes(actualQuestionCount) * 60;
    }

    public double getTotalMarks(int actualQuestionCount) {
        int q = actualQuestionCount > 0 ? actualQuestionCount : questionCount;
        return Math.round(q * marksPerQuestion * 100.0) / 100.0;
    }

    public boolean areAnswersVisible(LocalDateTime dbServerNow) {
        return areAnswersVisible(showAnswers, endAt, dbServerNow);
    }

    public static boolean areAnswersVisible(String showPolicy, LocalDateTime endAt, LocalDateTime dbServerNow) {
        if (showPolicy == null || showPolicy.trim().isEmpty()) {
            return true;
        }
        String policy = showPolicy.trim().toUpperCase();
        if (SHOW_NEVER.equals(policy)) {
            return false;
        }
        if (SHOW_AFTER_EXAM_END.equals(policy)) {
            if (endAt == null) {
                return true; // NULL end_at behaves like IMMEDIATELY
            }
            LocalDateTime now = dbServerNow != null ? dbServerNow : LocalDateTime.now();
            return !now.isBefore(endAt);
        }
        return true;
    }

    public String getHiddenAnswersMessage() {
        return getHiddenAnswersMessage(showAnswers, endAt);
    }

    public static String getHiddenAnswersMessage(String showPolicy, LocalDateTime endAt) {
        if (showPolicy != null && SHOW_AFTER_EXAM_END.equalsIgnoreCase(showPolicy.trim()) && endAt != null) {
            return "Answers will be available after " + endAt.format(DISPLAY_DT) + ".";
        }
        return "Answers are not available for this exam.";
    }

    public static String formatMarks(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((long) Math.rint(rounded));
        }
        String s = String.format(java.util.Locale.US, "%.2f", rounded);
        if (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String formatCountdown(long totalSeconds, boolean compactDays) {
        long s = Math.max(0L, totalSeconds);
        long days = s / 86400L;
        long hours = (s % 86400L) / 3600L;
        long mins = (s % 3600L) / 60L;
        long secs = s % 60L;
        if (days > 0) {
            if (compactDays) {
                return days + "d " + hours + "h";
            }
            return days + "d " + hours + "h " + mins + "m";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m " + secs + "s";
        }
        if (mins > 0) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }

    @Override
    public String toString() {
        return title != null ? title : ("Exam #" + id);
    }
}
