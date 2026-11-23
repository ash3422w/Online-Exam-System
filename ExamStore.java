package com.onlineexam.data;

import java.util.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExamStore {

    // ================= SUBJECT CONSTANTS =================
    public static final int JAVA = 1;
    public static final int DBMS = 2;
    public static final int SQL = 3;
    public static final int APTITUDE = 4;

    // ================= IN-MEMORY QUESTIONS =================
    private static final List<Question> questions = new ArrayList<>();

    // ✅ ADD QUESTION
    public static void addQuestion(Question q) {
        questions.add(q);
    }

    // ✅ GET ALL QUESTIONS
    public static List<Question> getQuestions() {
        return questions;
    }

    // ✅ CLEAR QUESTIONS
    public static void clearQuestions() {
        questions.clear();
    }

    // ✅ GET TOTAL COUNT
    public static int getQuestionCount() {
        return questions.size();
    }

    // ================= SELECTED SUBJECT =================
    private static int selectedSubjectId = 0;
    private static int selectedExamId = 0;
    private static ExamConfig currentExamConfig = null;

    public static void setSelectedExam(ExamConfig config) {
        if (config == null) {
            selectedExamId = 0;
            currentExamConfig = null;
            return;
        }
        selectedExamId = config.id;
        currentExamConfig = config;
        if (config.subjectId > 0) {
            selectedSubjectId = config.subjectId;
        }
    }

    public static void setSelectedExamId(int examId) {
        selectedExamId = examId;
    }

    public static int getSelectedExamId() {
        return selectedExamId;
    }

    public static ExamConfig getCurrentExamConfig() {
        return currentExamConfig;
    }

    public static void setSelectedSubject(int subjectId) {
        selectedSubjectId = subjectId;
    }

    public static int getSelectedSubject() {
        return selectedSubjectId;
    }

    // ================= GET SUBJECT NAME =================
    public static String getSubjectName(int id) {
        switch (id) {
            case 1: return "Java";
            case 2: return "DBMS";
            case 3: return "SQL";
            case 4: return "Aptitude";
            default: return "Unknown";
        }
    }

    // ================= SUBJECT FILTER =================
    public static List<Question> getQuestionsBySubject(int subjectId) {
        List<Question> filtered = new ArrayList<>();
        for (Question q : questions) {
            if (q.subjectId == subjectId) {
                filtered.add(q);
            }
        }
        return filtered;
    }

    // ================= EXAM CONFIGURATION =================
    public static final int DEFAULT_EXAM_QUESTION_COUNT = 20;
    private static int examQuestionCount = DEFAULT_EXAM_QUESTION_COUNT;

    public static int getExamQuestionCount() {
        return examQuestionCount;
    }

    public static void setExamQuestionCount(int count) {
        if (count > 0) {
            examQuestionCount = count;
        }
    }

    public static List<Question> removeDuplicateQuestions(List<Question> source) {
        List<Question> unique = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return unique;
        }
        Set<String> seenKeys = new HashSet<>();
        for (Question q : source) {
            if (q == null || q.text == null) {
                continue;
            }
            String key = q.subjectId + "|" + q.text.trim().toLowerCase(Locale.ROOT);
            if (seenKeys.add(key)) {
                unique.add(q);
            }
        }
        return unique;
    }

    public static int getExamQuestionCountForSubject(int subjectId) {
        int available = removeDuplicateQuestions(getQuestionsBySubject(subjectId)).size();
        return Math.min(examQuestionCount, available);
    }

    public static List<Question> generateRandomExamQuestions(int subjectId) {
        return generateRandomExamQuestions(subjectId, examQuestionCount);
    }

    public static List<Question> generateRandomExamQuestions(int subjectId, int requestedCount) {
        int limit = requestedCount > 0 ? requestedCount : DEFAULT_EXAM_QUESTION_COUNT;
        List<Question> uniquePool = removeDuplicateQuestions(getQuestionsBySubject(subjectId));
        Collections.shuffle(uniquePool);
        int selectCount = Math.min(limit, uniquePool.size());
        List<Question> selected = new ArrayList<>(uniquePool.subList(0, selectCount));
        Collections.shuffle(selected);
        return selected;
    }

    public static List<Question> shuffleAllQuestionOptions(List<Question> source) {
        List<Question> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Question q : source) {
            result.add(shuffleQuestionOptions(q));
        }
        return result;
    }

    public static Question shuffleQuestionOptions(Question q) {
        if (q == null) {
            return null;
        }
        String normType = normalizeQuestionType(q.type);
        // TRUE_FALSE keeps True/False order; FILL_BLANK has nothing to shuffle
        if (TYPE_TRUE_FALSE.equals(normType) || TYPE_FILL_BLANK.equals(normType)
                || q.options == null || q.options.length != 4) {
            String[] optsCopy = q.options != null ? Arrays.copyOf(q.options, q.options.length) : new String[]{"", "", "", ""};
            int[] origCopy = (q.originalOptionNumbers != null && q.originalOptionNumbers.length == 4)
                    ? Arrays.copyOf(q.originalOptionNumbers, 4)
                    : new int[]{1, 2, 3, 4};
            Question copy = new Question(
                    q.id, q.subjectId, q.text, optsCopy, q.correctIndex, origCopy, q.explanation, q.difficulty);
            copy.setType(normType);
            copy.setCorrectIndices(q.correctIndices);
            copy.setAcceptedAnswers(q.acceptedAnswers);
            copy.setHasImage(q.hasImage);
            copy.setImageBytes(q.imageBytes);
            return copy;
        }

        // SINGLE and MULTI: shuffle options and remap originalOptionNumbers, correctIndex & correctIndices
        List<Integer> order = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
        Collections.shuffle(order);

        String[] shuffledOptions = new String[4];
        int[] origNumbers = new int[4];
        int newCorrectIndex = 0;
        Set<Integer> newCorrectIndices = new LinkedHashSet<>();

        for (int newIdx = 0; newIdx < 4; newIdx++) {
            int oldIdx = order.get(newIdx);
            shuffledOptions[newIdx] = q.options[oldIdx];
            origNumbers[newIdx] = (q.originalOptionNumbers != null && oldIdx < q.originalOptionNumbers.length)
                    ? q.originalOptionNumbers[oldIdx]
                    : (oldIdx + 1);
            if (oldIdx == q.correctIndex) {
                newCorrectIndex = newIdx;
            }
            if (q.correctIndices != null && q.correctIndices.contains(oldIdx)) {
                newCorrectIndices.add(newIdx);
            }
        }
        if (newCorrectIndices.isEmpty()) {
            newCorrectIndices.add(newCorrectIndex);
        }
        Question shuffled = new Question(
                q.id, q.subjectId, q.text, shuffledOptions, newCorrectIndex, origNumbers, q.explanation, q.difficulty);
        shuffled.setType(normType);
        shuffled.setCorrectIndices(newCorrectIndices);
        shuffled.setAcceptedAnswers(q.acceptedAnswers);
        shuffled.setHasImage(q.hasImage);
        shuffled.setImageBytes(q.imageBytes);
        return shuffled;
    }

    // ================= EXAM DATA =================
    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_MULTI = "MULTI";
    public static final String TYPE_TRUE_FALSE = "TRUE_FALSE";
    public static final String TYPE_FILL_BLANK = "FILL_BLANK";

    public static String normalizeQuestionType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return TYPE_SINGLE;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (TYPE_MULTI.equals(u) || "MULTIPLE".equals(u) || "MULTI_SELECT".equals(u)) return TYPE_MULTI;
        if (TYPE_TRUE_FALSE.equals(u) || "TF".equals(u) || "TRUEFALSE".equals(u)) return TYPE_TRUE_FALSE;
        if (TYPE_FILL_BLANK.equals(u) || "FILL".equals(u) || "BLANK".equals(u) || "FILL_IN_BLANK".equals(u)) return TYPE_FILL_BLANK;
        return TYPE_SINGLE;
    }

    public static boolean isValidQuestionType(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return TYPE_SINGLE.equals(u) || TYPE_MULTI.equals(u) || TYPE_TRUE_FALSE.equals(u) || TYPE_FILL_BLANK.equals(u);
    }

    public static String formatQuestionTypeLabel(String type) {
        String t = normalizeQuestionType(type);
        if (TYPE_MULTI.equals(t)) return "MULTI-SELECT";
        if (TYPE_TRUE_FALSE.equals(t)) return "TRUE / FALSE";
        if (TYPE_FILL_BLANK.equals(t)) return "FILL IN BLANK";
        return "SINGLE CHOICE";
    }

    public static Set<Integer> parseOneBasedOptionsToZeroIndices(String csv) {
        Set<Integer> out = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            return out;
        }
        String[] parts = csv.split("[,;\\s]+");
        List<Integer> sorted = new ArrayList<>();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            try {
                int oneBased = Integer.parseInt(p.trim());
                if (oneBased >= 1 && oneBased <= 4 && !sorted.contains(oneBased - 1)) {
                    sorted.add(oneBased - 1);
                }
            } catch (NumberFormatException ignored) {}
        }
        Collections.sort(sorted);
        out.addAll(sorted);
        return out;
    }

    public static String formatZeroIndicesToOneBasedCsv(Set<Integer> zeroIndices, int[] originalOptionNumbers) {
        if (zeroIndices == null || zeroIndices.isEmpty()) {
            return null;
        }
        List<Integer> oneBased = new ArrayList<>();
        for (Integer idx : zeroIndices) {
            if (idx == null || idx < 0 || idx > 3) continue;
            int orig = (originalOptionNumbers != null && idx < originalOptionNumbers.length)
                    ? originalOptionNumbers[idx]
                    : (idx + 1);
            if (!oneBased.contains(orig)) {
                oneBased.add(orig);
            }
        }
        if (oneBased.isEmpty()) return null;
        Collections.sort(oneBased);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < oneBased.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(oneBased.get(i));
        }
        return sb.toString();
    }

    public static List<String> parseAcceptedAnswers(String pipeSeparated) {
        List<String> out = new ArrayList<>();
        if (pipeSeparated == null || pipeSeparated.trim().isEmpty()) {
            return out;
        }
        for (String part : pipeSeparated.split("\\|")) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public static String formatAcceptedAnswers(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String a : answers) {
            if (a == null) continue;
            String trimmed = a.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append("|");
            sb.append(trimmed);
        }
        if (sb.length() == 0) return null;
        String res = sb.toString();
        return res.length() > 500 ? res.substring(0, 500) : res;
    }

    public static final String DIFFICULTY_EASY = "EASY";
    public static final String DIFFICULTY_MEDIUM = "MEDIUM";
    public static final String DIFFICULTY_HARD = "HARD";

    public static String normalizeDifficulty(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DIFFICULTY_MEDIUM;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (DIFFICULTY_EASY.equals(u)) return DIFFICULTY_EASY;
        if (DIFFICULTY_HARD.equals(u)) return DIFFICULTY_HARD;
        return DIFFICULTY_MEDIUM;
    }

    public static boolean isValidDifficulty(String raw) {
        if (raw == null) return false;
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return DIFFICULTY_EASY.equals(u) || DIFFICULTY_MEDIUM.equals(u) || DIFFICULTY_HARD.equals(u);
    }

    public static final String SUBMISSION_MANUAL = "Manual";
    public static final String SUBMISSION_AUTOMATIC = "Automatic";

    public static Map<Integer, Integer> studentAnswers = new HashMap<>();
    public static Map<Integer, Set<Integer>> studentMultiAnswers = new HashMap<>();
    public static Map<Integer, String> studentTextAnswers = new HashMap<>();
    public static int score = 0;
    private static String currentUserId = "";
    private static String currentUserRole = "";
    private static volatile String sessionToken = null;
    private static volatile boolean currentlyInExam = false;
    private static final List<Question> currentExamQuestions = new ArrayList<>();
    private static boolean examCompletedInSession = false;
    private static String lastExamUserId = "";
    private static int lastExamTimeTakenSeconds = 0;
    private static String lastExamSubmissionType = "";
    private static double lastExamMarksObtained = 0.0;
    private static double lastExamTotalMarks = 0.0;
    private static int lastExamWrongCount = 0;
    private static double lastExamNegativeMarksLost = 0.0;
    private static boolean lastExamPassed = false;
    private static ExamConfig lastCompletedExamConfig = null;

    /**
     * Resets in-progress attempt answers/score while keeping the selected exam config active.
     */
    public static void resetAttemptState() {
        studentAnswers.clear();
        studentMultiAnswers.clear();
        studentTextAnswers.clear();
        score = 0;
        currentExamQuestions.clear();
        examCompletedInSession = false;
        lastExamUserId = "";
        lastExamTimeTakenSeconds = 0;
        lastExamSubmissionType = "";
        lastExamMarksObtained = 0.0;
        lastExamTotalMarks = 0.0;
        lastExamWrongCount = 0;
        lastExamNegativeMarksLost = 0.0;
        lastExamPassed = false;
        lastCompletedExamConfig = null;
    }

    // Task 10: Newly earned achievements from the latest exam attempt
    public static List<com.onlineexam.dao.AchievementService.Achievement> lastNewAchievements = new ArrayList<>();

    // Task 11: ID of the latest saved attempt in MySQL
    private static int lastSavedAttemptId = -1;

    public static void setLastSavedAttemptId(int id) {
        lastSavedAttemptId = id;
    }

    public static int getLastSavedAttemptId() {
        return lastSavedAttemptId;
    }

    public static void resetExam() {
        resetAttemptState();
        selectedExamId = 0;
        currentExamConfig = null;
        lastSavedAttemptId = -1;
        if (lastNewAchievements != null) {
            lastNewAchievements.clear();
        }
    }

    // ================= SESSION MANAGEMENT =================
    private static String currentUserFullName = "";

    public static void setCurrentUserFullName(String fullName) {
        currentUserFullName = fullName == null ? "" : fullName.trim();
    }

    public static String getCurrentUserFullName() {
        return currentUserFullName;
    }

    /**
     * Returns the student's full name if set, otherwise falls back to username.
     */
    public static String getCurrentDisplayName() {
        if (currentUserFullName != null && !currentUserFullName.trim().isEmpty()) {
            return currentUserFullName.trim();
        }
        return (currentUserId != null && !currentUserId.trim().isEmpty()) ? currentUserId.trim() : "Student";
    }

    public static void startSession(String userId, String role) {
        clearSession();
        currentUserId = userId == null ? "" : userId.trim();
        currentUserRole = role == null ? "" : role.trim();
    }

    public static void clearSession() {
        resetExam();
        selectedSubjectId = 0;
        currentUserId = "";
        currentUserRole = "";
        currentUserFullName = "";
        sessionToken = null;
        currentlyInExam = false;
    }

    public static void setSessionToken(String token) {
        sessionToken = token;
    }

    public static String getSessionToken() {
        return sessionToken;
    }

    public static void setCurrentlyInExam(boolean inExam) {
        currentlyInExam = inExam;
    }

    public static boolean isCurrentlyInExam() {
        return currentlyInExam;
    }

    public static void setLastExamScoringDetails(
            double marksObtained, double totalMarks, int wrongCount,
            double negativeMarksLost, boolean passed, ExamConfig examConfig) {
        lastExamMarksObtained = marksObtained;
        lastExamTotalMarks = totalMarks;
        lastExamWrongCount = Math.max(0, wrongCount);
        lastExamNegativeMarksLost = Math.max(0.0, negativeMarksLost);
        lastExamPassed = passed;
        lastCompletedExamConfig = examConfig;
    }

    public static double getLastExamMarksObtained() {
        return lastExamMarksObtained;
    }

    public static double getLastExamTotalMarks() {
        return lastExamTotalMarks;
    }

    public static int getLastExamWrongCount() {
        return lastExamWrongCount;
    }

    public static double getLastExamNegativeMarksLost() {
        return lastExamNegativeMarksLost;
    }

    public static boolean isLastExamPassed() {
        return lastExamPassed;
    }

    public static ExamConfig getLastCompletedExamConfig() {
        return lastCompletedExamConfig;
    }

    public static String getCurrentUserRole() {
        return currentUserRole;
    }

    public static void setCurrentExamQuestions(List<Question> examQuestions) {
        currentExamQuestions.clear();
        if (examQuestions != null) {
            currentExamQuestions.addAll(examQuestions);
        }
    }

    public static List<Question> getCurrentExamQuestions() {
        return new ArrayList<>(currentExamQuestions);
    }

    public static void markExamCompleted() {
        examCompletedInSession = !currentUserId.isEmpty() && !currentExamQuestions.isEmpty();
        lastExamUserId = examCompletedInSession ? currentUserId : "";
    }

    public static void setLastExamSubmissionDetails(int timeTakenSeconds, String submissionType) {
        lastExamTimeTakenSeconds = Math.max(0, timeTakenSeconds);
        lastExamSubmissionType = (submissionType == null || submissionType.trim().isEmpty())
                ? SUBMISSION_MANUAL
                : submissionType.trim();
    }

    public static int getLastExamTimeTakenSeconds() {
        return lastExamTimeTakenSeconds;
    }

    public static String getLastExamSubmissionType() {
        return lastExamSubmissionType;
    }

    public static String formatDuration(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static boolean hasValidExamResultForCurrentUser() {
        return examCompletedInSession
                && !currentUserId.isEmpty()
                && currentUserId.equals(lastExamUserId)
                && selectedSubjectId > 0
                && !currentExamQuestions.isEmpty();
    }

    // ================= CURRENT STUDENT =================
    public static void setCurrentUserId(String userId) {
        currentUserId = userId == null ? "" : userId.trim();
    }

    public static String getCurrentUserId() {
        return currentUserId;
    }

    // ================= STUDENT COUNT =================
    private static int studentsAttempted = 0;
    private static final List<AttemptRecord> attemptRecords = new ArrayList<>();

    public static void incrementAttempt() {
        studentsAttempted++;
    }

    public static int getStudentsAttempted() {
        return studentsAttempted;
    }

    public static void recordAttempt(String userId, int subjectId, int scoreValue) {
        recordAttempt(userId, subjectId, scoreValue, 0, SUBMISSION_MANUAL);
    }

    public static void recordAttempt(
            String userId, int subjectId, int scoreValue, int timeTakenSeconds, String submissionType) {
        recordAttempt(userId, subjectId, scoreValue,
                Math.max(scoreValue, DEFAULT_EXAM_QUESTION_COUNT), timeTakenSeconds, submissionType);
    }

    public static void recordAttempt(
            String userId, int subjectId, int scoreValue, int totalQuestions,
            int timeTakenSeconds, String submissionType) {
        incrementAttempt();
        attemptRecords.add(new AttemptRecord(
                userId, subjectId, scoreValue, totalQuestions, getCurrentDateTime(), timeTakenSeconds, submissionType));
    }

    public static void clearAttemptRecords() {
        attemptRecords.clear();
        studentsAttempted = 0;
    }

    public static void addAttemptRecordFromDB(String userId, int subjectId, int scoreValue) {
        addAttemptRecordFromDB(userId, subjectId, scoreValue,
                Math.max(scoreValue, DEFAULT_EXAM_QUESTION_COUNT), "-", 0, SUBMISSION_MANUAL);
    }

    public static void addAttemptRecordFromDB(String userId, int subjectId, int scoreValue, String attemptedDate) {
        addAttemptRecordFromDB(userId, subjectId, scoreValue,
                Math.max(scoreValue, DEFAULT_EXAM_QUESTION_COUNT), attemptedDate, 0, SUBMISSION_MANUAL);
    }

    public static void addAttemptRecordFromDB(
            String userId, int subjectId, int scoreValue, String attemptedDate,
            int timeTakenSeconds, String submissionType) {
        addAttemptRecordFromDB(userId, subjectId, scoreValue,
                Math.max(scoreValue, DEFAULT_EXAM_QUESTION_COUNT), attemptedDate, timeTakenSeconds, submissionType);
    }

    public static void addAttemptRecordFromDB(
            String userId, int subjectId, int scoreValue, int totalQuestions, String attemptedDate,
            int timeTakenSeconds, String submissionType) {
        attemptRecords.add(new AttemptRecord(
                userId, subjectId, scoreValue, totalQuestions, attemptedDate, timeTakenSeconds, submissionType));
        studentsAttempted = attemptRecords.size();
    }

    private static String getCurrentDateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return LocalDateTime.now().format(formatter);
    }

    public static List<AttemptRecord> getAttemptRecords() {
        return new ArrayList<>(attemptRecords);
    }

    public static List<AttemptRecord> getAttemptRecordsBySubject(int subjectId) {
        List<AttemptRecord> filtered = new ArrayList<>();
        for (AttemptRecord record : attemptRecords) {
            if (record.subjectId == subjectId) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    // ================= QUESTION MODEL =================
    public static class Question {
        public int id;
        public int subjectId;
        public String text;
        public String[] options;
        public int correctIndex;
        public int[] originalOptionNumbers = new int[]{1, 2, 3, 4};
        public String explanation = null;
        public String difficulty = DIFFICULTY_MEDIUM;
        public String type = TYPE_SINGLE;
        public Set<Integer> correctIndices = new LinkedHashSet<>();
        public List<String> acceptedAnswers = new ArrayList<>();
        public boolean hasImage = false;
        public transient byte[] imageBytes = null;

        public Question(int subjectId, String text,
                        String[] options, int correctIndex) {
            this.subjectId = subjectId;
            this.text = text;
            this.options = options;
            this.id = 0;
            this.correctIndex = correctIndex;
            if (correctIndex >= 0 && correctIndex < 4) {
                this.correctIndices.add(correctIndex);
            }
        }

        public Question(int id, int subjectId, String text,
                        String[] options, int correctIndex) {
            this(id, subjectId, text, options, correctIndex, new int[]{1, 2, 3, 4});
        }

        public Question(int id, int subjectId, String text,
                        String[] options, int correctIndex, int[] originalOptionNumbers) {
            this(id, subjectId, text, options, correctIndex, originalOptionNumbers, null);
        }

        public Question(int id, int subjectId, String text,
                        String[] options, int correctIndex, int[] originalOptionNumbers, String explanation) {
            this(id, subjectId, text, options, correctIndex, originalOptionNumbers, explanation, DIFFICULTY_MEDIUM);
        }

        public Question(int id, int subjectId, String text,
                        String[] options, int correctIndex, int[] originalOptionNumbers,
                        String explanation, String difficulty) {
            this.id = id;
            this.subjectId = subjectId;
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
            this.originalOptionNumbers = (originalOptionNumbers != null && originalOptionNumbers.length == 4)
                    ? originalOptionNumbers
                    : new int[]{1, 2, 3, 4};
            this.explanation = (explanation == null || explanation.trim().isEmpty()) ? null : explanation.trim();
            this.difficulty = normalizeDifficulty(difficulty);
            this.correctIndices.clear();
            if (correctIndex >= 0 && correctIndex < 4) {
                this.correctIndices.add(correctIndex);
            }
        }

        public Question setExplanation(String explanation) {
            this.explanation = (explanation == null || explanation.trim().isEmpty()) ? null : explanation.trim();
            return this;
        }

        public Question setDifficulty(String difficulty) {
            this.difficulty = normalizeDifficulty(difficulty);
            return this;
        }

        public Question setType(String type) {
            this.type = normalizeQuestionType(type);
            return this;
        }

        public Question setCorrectIndices(Collection<Integer> indices) {
            this.correctIndices.clear();
            if (indices != null) {
                List<Integer> sorted = new ArrayList<>();
                for (Integer idx : indices) {
                    if (idx != null && idx >= 0 && idx < 4 && !sorted.contains(idx)) {
                        sorted.add(idx);
                    }
                }
                Collections.sort(sorted);
                this.correctIndices.addAll(sorted);
                if (!sorted.isEmpty()) {
                    this.correctIndex = sorted.get(0);
                }
            }
            return this;
        }

        public Question setAcceptedAnswers(List<String> answers) {
            this.acceptedAnswers.clear();
            if (answers != null) {
                for (String a : answers) {
                    if (a != null && !a.trim().isEmpty()) {
                        this.acceptedAnswers.add(a.trim());
                    }
                }
            }
            return this;
        }

        public Question setHasImage(boolean hasImage) {
            this.hasImage = hasImage;
            return this;
        }

        public Question setImageBytes(byte[] imageBytes) {
            this.imageBytes = imageBytes;
            if (imageBytes != null && imageBytes.length > 0) {
                this.hasImage = true;
            }
            return this;
        }
    }

    public static class AttemptRecord {
        public String userId;
        public int subjectId;
        public int score;
        public int totalQuestions;
        public String attemptedDate;
        public int timeTakenSeconds;
        public String submissionType;

        public AttemptRecord(String userId, int subjectId, int score) {
            this(userId, subjectId, score, Math.max(score, DEFAULT_EXAM_QUESTION_COUNT), "-", 0, SUBMISSION_MANUAL);
        }

        public AttemptRecord(String userId, int subjectId, int score, String attemptedDate) {
            this(userId, subjectId, score, Math.max(score, DEFAULT_EXAM_QUESTION_COUNT), attemptedDate, 0, SUBMISSION_MANUAL);
        }

        public AttemptRecord(
                String userId, int subjectId, int score, String attemptedDate,
                int timeTakenSeconds, String submissionType) {
            this(userId, subjectId, score, Math.max(score, DEFAULT_EXAM_QUESTION_COUNT),
                    attemptedDate, timeTakenSeconds, submissionType);
        }

        public AttemptRecord(
                String userId, int subjectId, int score, int totalQuestions, String attemptedDate,
                int timeTakenSeconds, String submissionType) {
            this.userId = (userId == null || userId.trim().isEmpty()) ? "Unknown" : userId.trim();
            this.subjectId = subjectId;
            this.score = score;
            this.totalQuestions = Math.max(score, totalQuestions > 0 ? totalQuestions : DEFAULT_EXAM_QUESTION_COUNT);
            this.attemptedDate = (attemptedDate == null || attemptedDate.trim().isEmpty()) ? "-" : attemptedDate.trim();
            this.timeTakenSeconds = Math.max(0, timeTakenSeconds);
            this.submissionType = (submissionType == null || submissionType.trim().isEmpty())
                    ? SUBMISSION_MANUAL
                    : submissionType.trim();
        }
    }
}
