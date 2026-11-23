package com.onlineexam.data;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single authoritative evaluator for all question types (SINGLE, MULTI, TRUE_FALSE, FILL_BLANK).
 * Used consistently by ExamModule scoring (including negative marking),
 * AttemptDAO.buildAttemptAnswerRecords, and ReviewUI.
 */
public final class AnswerEvaluator {

    private AnswerEvaluator() {
    }

    /**
     * Normalizes a FILL_BLANK response: trims, collapses internal whitespace to a single space,
     * and converts to lowercase.
     */
    public static String normalizeBlank(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Returns true if the student provided a non-empty answer for the question.
     * Unanswered questions are NEVER penalized by negative marking.
     */
    public static boolean isAnswered(
            ExamStore.Question q,
            Integer singleChoiceIndex,
            Set<Integer> multiChoiceIndices,
            String textAnswer) {
        if (q == null) {
            return false;
        }
        String type = ExamStore.normalizeQuestionType(q.type);
        if (ExamStore.TYPE_MULTI.equals(type)) {
            return multiChoiceIndices != null && !multiChoiceIndices.isEmpty();
        }
        if (ExamStore.TYPE_FILL_BLANK.equals(type)) {
            return !normalizeBlank(textAnswer).isEmpty();
        }
        // SINGLE and TRUE_FALSE
        return singleChoiceIndex != null && singleChoiceIndex >= 0;
    }

    /**
     * Evaluates correctness for a question:
     * - SINGLE / TRUE_FALSE: exact 0-based index match
     * - MULTI: all-or-nothing (selected set must exactly equal correctIndices)
     * - FILL_BLANK: normalized text matches any accepted answer
     */
    public static boolean isCorrect(
            ExamStore.Question q,
            Integer singleChoiceIndex,
            Set<Integer> multiChoiceIndices,
            String textAnswer) {
        if (q == null) {
            return false;
        }
        String type = ExamStore.normalizeQuestionType(q.type);
        if (ExamStore.TYPE_MULTI.equals(type)) {
            if (multiChoiceIndices == null || multiChoiceIndices.isEmpty()) {
                return false;
            }
            Set<Integer> expected = q.correctIndices;
            if (expected == null || expected.isEmpty()) {
                return multiChoiceIndices.size() == 1 && multiChoiceIndices.contains(q.correctIndex);
            }
            return multiChoiceIndices.equals(expected);
        }
        if (ExamStore.TYPE_FILL_BLANK.equals(type)) {
            String normalizedInput = normalizeBlank(textAnswer);
            if (normalizedInput.isEmpty() || q.acceptedAnswers == null || q.acceptedAnswers.isEmpty()) {
                return false;
            }
            for (String accepted : q.acceptedAnswers) {
                String normAcc = normalizeBlank(accepted);
                if (!normAcc.isEmpty() && normalizedInput.equals(normAcc)) {
                    return true;
                }
            }
            return false;
        }
        // SINGLE and TRUE_FALSE
        return singleChoiceIndex != null && singleChoiceIndex == q.correctIndex;
    }

    /**
     * Returns true if the student gave a non-empty WRONG answer (including partially correct MULTI).
     * Used directly for negative marking deductions.
     */
    public static boolean isWrong(
            ExamStore.Question q,
            Integer singleChoiceIndex,
            Set<Integer> multiChoiceIndices,
            String textAnswer) {
        return isAnswered(q, singleChoiceIndex, multiChoiceIndices, textAnswer)
                && !isCorrect(q, singleChoiceIndex, multiChoiceIndices, textAnswer);
    }

    // ================= SESSION MAP CONVENIENCE OVERLOADS =================

    public static boolean isAnswered(
            ExamStore.Question q, int questionIndex,
            Map<Integer, Integer> singleMap,
            Map<Integer, Set<Integer>> multiMap,
            Map<Integer, String> textMap) {
        Integer s = singleMap != null ? singleMap.get(questionIndex) : null;
        Set<Integer> m = multiMap != null ? multiMap.get(questionIndex) : null;
        String t = textMap != null ? textMap.get(questionIndex) : null;
        return isAnswered(q, s, m, t);
    }

    public static boolean isCorrect(
            ExamStore.Question q, int questionIndex,
            Map<Integer, Integer> singleMap,
            Map<Integer, Set<Integer>> multiMap,
            Map<Integer, String> textMap) {
        Integer s = singleMap != null ? singleMap.get(questionIndex) : null;
        Set<Integer> m = multiMap != null ? multiMap.get(questionIndex) : null;
        String t = textMap != null ? textMap.get(questionIndex) : null;
        return isCorrect(q, s, m, t);
    }

    public static boolean isWrong(
            ExamStore.Question q, int questionIndex,
            Map<Integer, Integer> singleMap,
            Map<Integer, Set<Integer>> multiMap,
            Map<Integer, String> textMap) {
        Integer s = singleMap != null ? singleMap.get(questionIndex) : null;
        Set<Integer> m = multiMap != null ? multiMap.get(questionIndex) : null;
        String t = textMap != null ? textMap.get(questionIndex) : null;
        return isWrong(q, s, m, t);
    }

    public static boolean isSessionAnswered(ExamStore.Question q, int questionIndex) {
        return isAnswered(q, questionIndex,
                ExamStore.studentAnswers, ExamStore.studentMultiAnswers, ExamStore.studentTextAnswers);
    }

    public static boolean isSessionCorrect(ExamStore.Question q, int questionIndex) {
        return isCorrect(q, questionIndex,
                ExamStore.studentAnswers, ExamStore.studentMultiAnswers, ExamStore.studentTextAnswers);
    }

    public static boolean isSessionWrong(ExamStore.Question q, int questionIndex) {
        return isWrong(q, questionIndex,
                ExamStore.studentAnswers, ExamStore.studentMultiAnswers, ExamStore.studentTextAnswers);
    }

    // ================= DIRECT VALUE EVALUATOR FOR HISTORICAL REVIEW =================

    public static boolean isAnsweredValues(
            String type, Integer singleZeroIdx, Collection<Integer> multiZeroIdx, String textAnswer) {
        String t = ExamStore.normalizeQuestionType(type);
        if (ExamStore.TYPE_MULTI.equals(t)) {
            return multiZeroIdx != null && !multiZeroIdx.isEmpty();
        }
        if (ExamStore.TYPE_FILL_BLANK.equals(t)) {
            return !normalizeBlank(textAnswer).isEmpty();
        }
        return singleZeroIdx != null && singleZeroIdx >= 0;
    }

    public static boolean isCorrectValues(
            String type,
            Integer singleZeroIdx, Set<Integer> multiZeroIdx, String textAnswer,
            int correctZeroIdx, Set<Integer> correctZeroIndices, List<String> acceptedAnswers) {
        String t = ExamStore.normalizeQuestionType(type);
        if (ExamStore.TYPE_MULTI.equals(t)) {
            if (multiZeroIdx == null || multiZeroIdx.isEmpty()
                    || correctZeroIndices == null || correctZeroIndices.isEmpty()) {
                return false;
            }
            return multiZeroIdx.equals(correctZeroIndices);
        }
        if (ExamStore.TYPE_FILL_BLANK.equals(t)) {
            String normIn = normalizeBlank(textAnswer);
            if (normIn.isEmpty() || acceptedAnswers == null || acceptedAnswers.isEmpty()) {
                return false;
            }
            for (String acc : acceptedAnswers) {
                String normAcc = normalizeBlank(acc);
                if (!normAcc.isEmpty() && normIn.equals(normAcc)) {
                    return true;
                }
            }
            return false;
        }
        return singleZeroIdx != null && singleZeroIdx == correctZeroIdx;
    }
}
