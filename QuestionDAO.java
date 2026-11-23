package com.onlineexam.dao;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;

public class QuestionDAO {

    public static final int MAX_EXPLANATION_LENGTH = 1000;
    public static final int MAX_QUESTIONS_PER_SUBJECT = 30;
    public static final long MAX_IMAGE_FILE_BYTES = 2L * 1024L * 1024L; // 2 MB
    public static final int MAX_IMAGE_WIDTH_PX = 1000;

    // Never select image_data in bulk queries; use `image_data IS NOT NULL AS has_image`
    private static final String BULK_SELECT_COLS =
            "id, subject_id, question, option1, option2, option3, option4, correct_option, "
            + "explanation, difficulty, question_type, correct_options, correct_text, "
            + "(image_data IS NOT NULL) AS has_image";

    private static final int IMAGE_CACHE_MAX = 40;
    private static final Object IMAGE_CACHE_LOCK = new Object();
    private static final Map<Integer, byte[]> IMAGE_CACHE = new LinkedHashMap<Integer, byte[]>(IMAGE_CACHE_MAX + 4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
            return size() > IMAGE_CACHE_MAX;
        }
    };

    /**
     * Lazily loads image bytes for a single question id, backed by a small LRU cache.
     */
    public static byte[] getQuestionImage(int questionId) {
        if (questionId <= 0) return null;
        synchronized (IMAGE_CACHE_LOCK) {
            if (IMAGE_CACHE.containsKey(questionId)) {
                return IMAGE_CACHE.get(questionId);
            }
        }
        byte[] bytes = null;
        String sql = "SELECT image_data FROM questions WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bytes = rs.getBytes("image_data");
                    if (bytes != null && bytes.length == 0) {
                        bytes = null;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        synchronized (IMAGE_CACHE_LOCK) {
            IMAGE_CACHE.put(questionId, bytes);
        }
        return bytes;
    }

    public static void evictImageCache(int questionId) {
        synchronized (IMAGE_CACHE_LOCK) {
            IMAGE_CACHE.remove(questionId);
        }
    }

    public static void clearImageCache() {
        synchronized (IMAGE_CACHE_LOCK) {
            IMAGE_CACHE.clear();
        }
    }

    /**
     * Validates an image file (rejects > 2 MB), downscales proportionally to max 1000 px wide,
     * and encodes as PNG (if alpha/PNG) or JPEG.
     */
    public static byte[] prepareQuestionImage(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("Selected image file does not exist.");
        }
        if (file.length() > MAX_IMAGE_FILE_BYTES) {
            throw new IllegalArgumentException("Image file exceeds the 2 MB maximum size limit.");
        }
        BufferedImage original = ImageIO.read(file);
        if (original == null) {
            throw new IllegalArgumentException("Unsupported or unreadable image format. Please choose a PNG or JPEG image.");
        }

        boolean keepPng = original.getColorModel().hasAlpha()
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".png");
        BufferedImage processed = original;
        if (original.getWidth() > MAX_IMAGE_WIDTH_PX) {
            int newW = MAX_IMAGE_WIDTH_PX;
            int newH = Math.max(1, (int) Math.round(original.getHeight() * (MAX_IMAGE_WIDTH_PX / (double) original.getWidth())));
            int imgType = keepPng ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage scaled = new BufferedImage(newW, newH, imgType);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!keepPng) {
                g2.setColor(java.awt.Color.WHITE);
                g2.fillRect(0, 0, newW, newH);
            }
            g2.drawImage(original, 0, 0, newW, newH, null);
            g2.dispose();
            processed = scaled;
        } else if (!keepPng && original.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = rgb.createGraphics();
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, original.getWidth(), original.getHeight());
            g2.drawImage(original, 0, 0, null);
            g2.dispose();
            processed = rgb;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String format = keepPng ? "png" : "jpg";
        if (!ImageIO.write(processed, format, baos)) {
            baos.reset();
            ImageIO.write(processed, "png", baos);
        }
        return baos.toByteArray();
    }

    public static void loadQuestionsFromDB() {
        String sql = "SELECT " + BULK_SELECT_COLS + " FROM questions";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ExamStore.clearQuestions();

            while (rs.next()) {
                ExamStore.addQuestion(mapQuestion(rs));
            }

            System.out.println(
                    "Questions loaded from DB: " + ExamStore.getQuestionCount()
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static ExamStore.Question getQuestionById(int questionId) {
        if (questionId <= 0) return null;
        String sql = "SELECT " + BULK_SELECT_COLS + " FROM questions WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapQuestion(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<ExamStore.Question> getRandomQuestionsBySubject(int subjectId, int count) {
        int limit = count > 0 ? count : ExamStore.DEFAULT_EXAM_QUESTION_COUNT;
        List<ExamStore.Question> selected = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        String sql = "SELECT " + BULK_SELECT_COLS + " FROM questions WHERE subject_id = ? ORDER BY RAND()";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, subjectId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && selected.size() < limit) {
                    ExamStore.Question q = mapQuestion(rs);
                    if (q.text == null) {
                        continue;
                    }
                    String key = q.subjectId + "|" + q.text.trim().toLowerCase(Locale.ROOT);
                    if (seenKeys.add(key)) {
                        selected.add(q);
                    }
                }
            }

            Collections.shuffle(selected);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return selected;
    }

    /**
     * Draws randomized unique questions for an exam, honouring its Easy/Medium/Hard percentage mix
     * using the Largest-Remainder Method with Medium-first nearest-difficulty backfill.
     * Falls back to legacy random selection when no mix (0/0/0) is configured.
     */
    public static List<ExamStore.Question> getRandomQuestionsForExam(ExamConfig cfg) {
        if (cfg == null) {
            return new ArrayList<>();
        }
        int requestedCount = cfg.questionCount > 0 ? cfg.questionCount : ExamStore.DEFAULT_EXAM_QUESTION_COUNT;
        if (!cfg.hasDifficultyMix()) {
            return getRandomQuestionsBySubject(cfg.subjectId, requestedCount);
        }

        List<ExamStore.Question> easyPool = new ArrayList<>();
        List<ExamStore.Question> mediumPool = new ArrayList<>();
        List<ExamStore.Question> hardPool = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        String sql = "SELECT " + BULK_SELECT_COLS + " FROM questions WHERE subject_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cfg.subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExamStore.Question q = mapQuestion(rs);
                    if (q.text == null || q.text.trim().isEmpty()) {
                        continue;
                    }
                    String key = q.subjectId + "|" + q.text.trim().toLowerCase(Locale.ROOT);
                    if (!seenKeys.add(key)) {
                        continue;
                    }
                    String diff = ExamStore.normalizeDifficulty(q.difficulty);
                    if (ExamStore.DIFFICULTY_EASY.equals(diff)) {
                        easyPool.add(q);
                    } else if (ExamStore.DIFFICULTY_HARD.equals(diff)) {
                        hardPool.add(q);
                    } else {
                        mediumPool.add(q);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int totalUnique = easyPool.size() + mediumPool.size() + hardPool.size();
        if (totalUnique == 0) {
            return new ArrayList<>();
        }

        Collections.shuffle(easyPool);
        Collections.shuffle(mediumPool);
        Collections.shuffle(hardPool);

        int drawTotal = Math.min(requestedCount, totalUnique);
        int[] targets = ExamConfig.computeDifficultyTargets(drawTotal, cfg.easyPct, cfg.mediumPct, cfg.hardPct);

        List<ExamStore.Question> chosen = new ArrayList<>(drawTotal);
        int easyIdx = Math.min(targets[0], easyPool.size());
        int medIdx = Math.min(targets[1], mediumPool.size());
        int hardIdx = Math.min(targets[2], hardPool.size());

        chosen.addAll(easyPool.subList(0, easyIdx));
        chosen.addAll(mediumPool.subList(0, medIdx));
        chosen.addAll(hardPool.subList(0, hardIdx));

        int shortEasy = targets[0] - easyIdx;
        int shortMed = targets[1] - medIdx;
        int shortHard = targets[2] - hardIdx;
        int backfilled = 0;

        // Backfill EASY shortfall: Medium first, then Hard
        while (shortEasy > 0) {
            if (medIdx < mediumPool.size()) {
                chosen.add(mediumPool.get(medIdx++));
                shortEasy--;
                backfilled++;
            } else if (hardIdx < hardPool.size()) {
                chosen.add(hardPool.get(hardIdx++));
                shortEasy--;
                backfilled++;
            } else {
                break;
            }
        }

        // Backfill HARD shortfall: Medium first, then Easy
        while (shortHard > 0) {
            if (medIdx < mediumPool.size()) {
                chosen.add(mediumPool.get(medIdx++));
                shortHard--;
                backfilled++;
            } else if (easyIdx < easyPool.size()) {
                chosen.add(easyPool.get(easyIdx++));
                shortHard--;
                backfilled++;
            } else {
                break;
            }
        }

        // Backfill MEDIUM shortfall: Easy or Hard (pick from whichever has more left)
        while (shortMed > 0) {
            int remEasy = easyPool.size() - easyIdx;
            int remHard = hardPool.size() - hardIdx;
            if (remEasy <= 0 && remHard <= 0) {
                break;
            }
            if (remEasy >= remHard) {
                chosen.add(easyPool.get(easyIdx++));
            } else {
                chosen.add(hardPool.get(hardIdx++));
            }
            shortMed--;
            backfilled++;
        }

        Collections.shuffle(chosen);

        int actualEasy = 0, actualMed = 0, actualHard = 0;
        for (ExamStore.Question q : chosen) {
            String d = ExamStore.normalizeDifficulty(q.difficulty);
            if (ExamStore.DIFFICULTY_EASY.equals(d)) actualEasy++;
            else if (ExamStore.DIFFICULTY_HARD.equals(d)) actualHard++;
            else actualMed++;
        }
        System.out.printf(
                "[QuestionDAO] Difficulty mix for '%s' (%d/%d/%d%% -> target %d/%d/%d): chosen EASY=%d, MEDIUM=%d, HARD=%d (total=%d%s)%n",
                cfg.title != null ? cfg.title : ("Exam#" + cfg.id),
                cfg.easyPct, cfg.mediumPct, cfg.hardPct,
                targets[0], targets[1], targets[2],
                actualEasy, actualMed, actualHard,
                chosen.size(),
                backfilled > 0 ? (", backfilled=" + backfilled) : ""
        );
        return chosen;
    }

    public static class AddQuestionResult {
        public final boolean success;
        public final boolean limitReached;
        public final String message;

        public AddQuestionResult(boolean success, boolean limitReached, String message) {
            this.success = success;
            this.limitReached = limitReached;
            this.message = message;
        }
    }

    public static AddQuestionResult addQuestion(
            int subjectId, String questionText, String[] options, int correctOptionOneBased) {
        return addQuestion(subjectId, questionText, options, correctOptionOneBased, null, ExamStore.DIFFICULTY_MEDIUM);
    }

    public static AddQuestionResult addQuestion(
            int subjectId, String questionText, String[] options,
            int correctOptionOneBased, String explanation) {
        return addQuestion(subjectId, questionText, options, correctOptionOneBased, explanation, ExamStore.DIFFICULTY_MEDIUM);
    }

    public static AddQuestionResult addQuestion(
            int subjectId, String questionText, String[] options,
            int correctOptionOneBased, String explanation, String difficulty) {
        return addQuestion(buildSingleQuestion(subjectId, questionText, options, correctOptionOneBased, explanation, difficulty), null);
    }

    private static ExamStore.Question buildSingleQuestion(
            int subjectId, String questionText, String[] options,
            int correctOptionOneBased, String explanation, String difficulty) {
        ExamStore.Question q = new ExamStore.Question(
                0, subjectId, questionText, options, correctOptionOneBased - 1,
                new int[]{1, 2, 3, 4}, explanation, difficulty);
        q.setType(ExamStore.TYPE_SINGLE);
        return q;
    }

    /**
     * Inserts a question of ANY supported type (SINGLE, MULTI, TRUE_FALSE, FILL_BLANK)
     * with an optional downscaled PNG/JPEG image while satisfying existing NOT NULL/CHECK constraints.
     */
    public static AddQuestionResult addQuestion(ExamStore.Question q, byte[] imageBytes) {
        String validationErr = validateQuestionModel(q);
        if (validationErr != null) {
            return new AddQuestionResult(false, false, validationErr);
        }

        NormalizedDbFields fields = normalizeForDb(q);
        String checkSql = "SELECT COUNT(*) FROM questions WHERE subject_id = ?";
        String insertSql = "INSERT INTO questions(subject_id, question, option1, option2, option3, option4, "
                + "correct_option, explanation, difficulty, question_type, correct_options, correct_text, image_data) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement check = con.prepareStatement(checkSql)) {

            check.setInt(1, q.subjectId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= MAX_QUESTIONS_PER_SUBJECT) {
                    return new AddQuestionResult(false, true,
                            "Maximum " + MAX_QUESTIONS_PER_SUBJECT + " questions allowed per subject.");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, q.subjectId);
                ps.setString(2, fields.questionText);
                ps.setString(3, fields.opt1);
                ps.setString(4, fields.opt2);
                ps.setString(5, fields.opt3);
                ps.setString(6, fields.opt4);
                ps.setInt(7, fields.correctOption);
                if (fields.explanation == null) ps.setNull(8, Types.LONGVARCHAR);
                else ps.setString(8, fields.explanation);
                ps.setString(9, fields.difficulty);
                ps.setString(10, fields.type);
                if (fields.correctOptions == null) ps.setNull(11, Types.VARCHAR);
                else ps.setString(11, fields.correctOptions);
                if (fields.correctText == null) ps.setNull(12, Types.VARCHAR);
                else ps.setString(12, fields.correctText);
                if (imageBytes == null || imageBytes.length == 0) ps.setNull(13, Types.LONGVARBINARY);
                else ps.setBytes(13, imageBytes);

                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        q.id = keys.getInt(1);
                        if (imageBytes != null && imageBytes.length > 0) {
                            synchronized (IMAGE_CACHE_LOCK) {
                                IMAGE_CACHE.put(q.id, imageBytes);
                            }
                        }
                    }
                }
            }

            loadQuestionsFromDB();
            return new AddQuestionResult(true, false,
                    "Added " + ExamStore.formatQuestionTypeLabel(fields.type) + " question to "
                    + ExamStore.getSubjectName(q.subjectId) + " bank.");

        } catch (SQLException ex) {
            ex.printStackTrace();
            return new AddQuestionResult(false, false, "Database error: " + ex.getMessage());
        }
    }

    /**
     * Updates a question of ANY supported type (SINGLE, MULTI, TRUE_FALSE, FILL_BLANK).
     * When updateImage is true, replaces image_data (or sets NULL if imageBytes is null).
     * When updateImage is false, leaves existing image_data untouched.
     */
    public static boolean updateQuestion(ExamStore.Question q, byte[] imageBytes, boolean updateImage) {
        if (q == null || q.id <= 0 || validateQuestionModel(q) != null) {
            return false;
        }
        NormalizedDbFields fields = normalizeForDb(q);
        String sql = updateImage
                ? ("UPDATE questions SET subject_id = ?, question = ?, option1 = ?, option2 = ?, option3 = ?, option4 = ?, "
                + "correct_option = ?, explanation = ?, difficulty = ?, question_type = ?, correct_options = ?, correct_text = ?, image_data = ? "
                + "WHERE id = ?")
                : ("UPDATE questions SET subject_id = ?, question = ?, option1 = ?, option2 = ?, option3 = ?, option4 = ?, "
                + "correct_option = ?, explanation = ?, difficulty = ?, question_type = ?, correct_options = ?, correct_text = ? "
                + "WHERE id = ?");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, q.subjectId);
            ps.setString(2, fields.questionText);
            ps.setString(3, fields.opt1);
            ps.setString(4, fields.opt2);
            ps.setString(5, fields.opt3);
            ps.setString(6, fields.opt4);
            ps.setInt(7, fields.correctOption);
            if (fields.explanation == null) ps.setNull(8, Types.LONGVARCHAR);
            else ps.setString(8, fields.explanation);
            ps.setString(9, fields.difficulty);
            ps.setString(10, fields.type);
            if (fields.correctOptions == null) ps.setNull(11, Types.VARCHAR);
            else ps.setString(11, fields.correctOptions);
            if (fields.correctText == null) ps.setNull(12, Types.VARCHAR);
            else ps.setString(12, fields.correctText);

            if (updateImage) {
                if (imageBytes == null || imageBytes.length == 0) ps.setNull(13, Types.LONGVARBINARY);
                else ps.setBytes(13, imageBytes);
                ps.setInt(14, q.id);
            } else {
                ps.setInt(13, q.id);
            }

            int affected = ps.executeUpdate();
            if (affected > 0) {
                if (updateImage) {
                    evictImageCache(q.id);
                }
                loadQuestionsFromDB();
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static final class NormalizedDbFields {
        String questionText;
        String opt1, opt2, opt3, opt4;
        int correctOption;
        String explanation;
        String difficulty;
        String type;
        String correctOptions;
        String correctText;
    }

    private static String validateQuestionModel(ExamStore.Question q) {
        if (q == null || q.text == null || q.text.trim().isEmpty()) {
            return "Question text is required.";
        }
        String t = ExamStore.normalizeQuestionType(q.type);
        if (ExamStore.TYPE_SINGLE.equals(t)) {
            if (q.options == null || q.options.length < 4) return "All 4 options are required.";
            for (int i = 0; i < 4; i++) {
                if (q.options[i] == null || q.options[i].trim().isEmpty()) {
                    return "Option " + (i + 1) + " is required.";
                }
            }
            if (q.correctIndex < 0 || q.correctIndex > 3) return "Select a valid correct option (1-4).";
        } else if (ExamStore.TYPE_MULTI.equals(t)) {
            if (q.options == null || q.options.length < 4) return "All 4 options are required.";
            for (int i = 0; i < 4; i++) {
                if (q.options[i] == null || q.options[i].trim().isEmpty()) {
                    return "Option " + (i + 1) + " is required.";
                }
            }
            if (q.correctIndices == null || q.correctIndices.isEmpty()) {
                return "Select at least one correct option for a Multi-Select question.";
            }
        } else if (ExamStore.TYPE_TRUE_FALSE.equals(t)) {
            if (q.correctIndex != 0 && q.correctIndex != 1) {
                return "Select True or False as the correct answer.";
            }
        } else if (ExamStore.TYPE_FILL_BLANK.equals(t)) {
            if (q.acceptedAnswers == null || q.acceptedAnswers.isEmpty()) {
                return "Provide at least one accepted answer for a Fill-in-the-Blank question.";
            }
        }
        return null;
    }

    private static NormalizedDbFields normalizeForDb(ExamStore.Question q) {
        NormalizedDbFields f = new NormalizedDbFields();
        f.questionText = q.text.trim();
        f.explanation = normalizeExplanation(q.explanation);
        f.difficulty = ExamStore.normalizeDifficulty(q.difficulty);
        f.type = ExamStore.normalizeQuestionType(q.type);

        if (ExamStore.TYPE_TRUE_FALSE.equals(f.type)) {
            f.opt1 = "True";
            f.opt2 = "False";
            f.opt3 = "";
            f.opt4 = "";
            f.correctOption = (q.correctIndex == 1) ? 2 : 1;
            f.correctOptions = null;
            f.correctText = null;
        } else if (ExamStore.TYPE_FILL_BLANK.equals(f.type)) {
            f.opt1 = "";
            f.opt2 = "";
            f.opt3 = "";
            f.opt4 = "";
            f.correctOption = 1; // placeholder satisfying existing CHECK/NOT NULL constraints
            f.correctOptions = null;
            f.correctText = ExamStore.formatAcceptedAnswers(q.acceptedAnswers);
        } else if (ExamStore.TYPE_MULTI.equals(f.type)) {
            f.opt1 = q.options[0].trim();
            f.opt2 = q.options[1].trim();
            f.opt3 = q.options[2].trim();
            f.opt4 = q.options[3].trim();
            List<Integer> sorted = new ArrayList<>(q.correctIndices);
            Collections.sort(sorted);
            f.correctOption = sorted.isEmpty() ? 1 : (sorted.get(0) + 1);
            f.correctOptions = ExamStore.formatZeroIndicesToOneBasedCsv(q.correctIndices, null);
            f.correctText = null;
        } else {
            f.opt1 = q.options[0].trim();
            f.opt2 = q.options[1].trim();
            f.opt3 = q.options[2].trim();
            f.opt4 = q.options[3].trim();
            f.correctOption = Math.max(1, Math.min(4, q.correctIndex + 1));
            f.correctOptions = null;
            f.correctText = null;
        }
        return f;
    }

    private static AddQuestionResult addQuestionLegacyInternal(
            int subjectId, String questionText, String[] options,
            int correctOptionOneBased, String explanation, String difficulty) {
        return addQuestion(buildSingleQuestion(subjectId, questionText, options, correctOptionOneBased, explanation, difficulty), null);
    }

    public static boolean deleteQuestion(int questionId) {
        if (questionId <= 0) {
            return false;
        }
        String sql = "DELETE FROM questions WHERE id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                evictImageCache(questionId);
                loadQuestionsFromDB();
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Preserved 5-arg overload (leaves existing explanation intact)
    public static boolean updateQuestion(
            int questionId, int subjectId, String questionText,
            String[] options, int correctOptionOneBased) {
        if (questionId <= 0 || questionText == null || questionText.trim().isEmpty()
                || options == null || options.length < 4
                || correctOptionOneBased < 1 || correctOptionOneBased > 4) {
            return false;
        }

        String sql = "UPDATE questions SET subject_id = ?, question = ?, "
                + "option1 = ?, option2 = ?, option3 = ?, option4 = ?, correct_option = ? "
                + "WHERE id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.setString(2, questionText.trim());
            ps.setString(3, options[0].trim());
            ps.setString(4, options[1].trim());
            ps.setString(5, options[2].trim());
            ps.setString(6, options[3].trim());
            ps.setInt(7, correctOptionOneBased);
            ps.setInt(8, questionId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                evictImageCache(questionId);
                loadQuestionsFromDB();
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Preserved 6-arg overload (leaves existing difficulty intact)
    public static boolean updateQuestion(
            int questionId, int subjectId, String questionText,
            String[] options, int correctOptionOneBased, String explanation) {
        if (questionId <= 0 || questionText == null || questionText.trim().isEmpty()
                || options == null || options.length < 4
                || correctOptionOneBased < 1 || correctOptionOneBased > 4) {
            return false;
        }

        String normalizedExp = normalizeExplanation(explanation);
        String sql = "UPDATE questions SET subject_id = ?, question = ?, "
                + "option1 = ?, option2 = ?, option3 = ?, option4 = ?, correct_option = ?, explanation = ? "
                + "WHERE id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.setString(2, questionText.trim());
            ps.setString(3, options[0].trim());
            ps.setString(4, options[1].trim());
            ps.setString(5, options[2].trim());
            ps.setString(6, options[3].trim());
            ps.setInt(7, correctOptionOneBased);
            if (normalizedExp == null) {
                ps.setNull(8, Types.LONGVARCHAR);
            } else {
                ps.setString(8, normalizedExp);
            }
            ps.setInt(9, questionId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                evictImageCache(questionId);
                loadQuestionsFromDB();
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // New 7-arg overload supporting explanation + difficulty update
    public static boolean updateQuestion(
            int questionId, int subjectId, String questionText,
            String[] options, int correctOptionOneBased, String explanation, String difficulty) {
        if (questionId <= 0 || questionText == null || questionText.trim().isEmpty()
                || options == null || options.length < 4
                || correctOptionOneBased < 1 || correctOptionOneBased > 4) {
            return false;
        }

        String normalizedExp = normalizeExplanation(explanation);
        String normalizedDiff = ExamStore.normalizeDifficulty(difficulty);
        String sql = "UPDATE questions SET subject_id = ?, question = ?, "
                + "option1 = ?, option2 = ?, option3 = ?, option4 = ?, correct_option = ?, explanation = ?, difficulty = ? "
                + "WHERE id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.setString(2, questionText.trim());
            ps.setString(3, options[0].trim());
            ps.setString(4, options[1].trim());
            ps.setString(5, options[2].trim());
            ps.setString(6, options[3].trim());
            ps.setInt(7, correctOptionOneBased);
            if (normalizedExp == null) {
                ps.setNull(8, Types.LONGVARCHAR);
            } else {
                ps.setString(8, normalizedExp);
            }
            ps.setString(9, normalizedDiff);
            ps.setInt(10, questionId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                evictImageCache(questionId);
                loadQuestionsFromDB();
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static class CsvImportResult {
        public final boolean success;
        public final int importedCount;
        public final int skippedCount;
        public final String message;
        public final List<String> warnings;
        public final List<String> rowErrors;

        public CsvImportResult(boolean success, int importedCount, String message) {
            this(success, importedCount, 0, message, Collections.emptyList(), Collections.emptyList());
        }

        public CsvImportResult(boolean success, int importedCount, String message, List<String> warnings) {
            this(success, importedCount, 0, message, warnings, Collections.emptyList());
        }

        public CsvImportResult(boolean success, int importedCount, int skippedCount, String message,
                               List<String> warnings, List<String> rowErrors) {
            this.success = success;
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
            this.message = message;
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
            this.rowErrors = rowErrors != null ? new ArrayList<>(rowErrors) : new ArrayList<>();
        }
    }

    /**
     * Bulk-imports MCQ rows from CSV.
     * Legacy format: subject,question,option1,option2,option3,option4,correct_option(1-4)[,explanation][,difficulty]
     * Extended optional header columns (any order): type/question_type, correct_options, correct_text/accepted_answers, explanation, difficulty.
     * Row-level errors are collected and reported without aborting valid rows in the file.
     */
    public static CsvImportResult importQuestionsFromCSV(File csvFile) {
        if (csvFile == null || !csvFile.exists()) {
            return new CsvImportResult(false, 0, "Selected CSV file does not exist.");
        }

        List<Integer> rowLineNumbers = new ArrayList<>();
        List<String[]> parsedRows = new ArrayList<>();
        List<String> rowErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int explanationColIdx = -1;
        int difficultyColIdx = -1;
        int typeColIdx = -1;
        int correctOptionsColIdx = -1;
        int correctTextColIdx = -1;
        boolean hasHeader = false;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] cols = parseCsvLine(trimmed);
                if (lineNum == 1 && cols.length >= 2 && cols[0].equalsIgnoreCase("subject")) {
                    hasHeader = true;
                    for (int c = 0; c < cols.length; c++) {
                        String hdr = cols[c].trim().toLowerCase(Locale.ROOT);
                        if ("explanation".equals(hdr)) explanationColIdx = c;
                        else if ("difficulty".equals(hdr)) difficultyColIdx = c;
                        else if ("type".equals(hdr) || "question_type".equals(hdr)) typeColIdx = c;
                        else if ("correct_options".equals(hdr)) correctOptionsColIdx = c;
                        else if ("correct_text".equals(hdr) || "accepted_answers".equals(hdr)) correctTextColIdx = c;
                    }
                    continue;
                }
                if (cols.length < 7) {
                    rowErrors.add("Line " + lineNum + ": has only " + cols.length + " columns (expected at least 7).");
                    continue;
                }
                rowLineNumbers.add(lineNum);
                parsedRows.add(cols);
            }
        } catch (IOException e) {
            return new CsvImportResult(false, 0, "Failed to read CSV file: " + e.getMessage());
        }

        if (parsedRows.isEmpty()) {
            String msg = rowErrors.isEmpty()
                    ? "No question rows found in CSV file."
                    : ("No valid rows found (" + rowErrors.size() + " row error(s)).");
            return new CsvImportResult(false, 0, rowErrors.size(), msg, warnings, rowErrors);
        }

        Map<String, Integer> subjectLookup = new HashMap<>();
        for (Map.Entry<Integer, String> entry : AttemptDAO.getAvailableSubjects().entrySet()) {
            subjectLookup.put(entry.getValue().trim().toLowerCase(Locale.ROOT), entry.getKey());
        }

        List<ExamStore.Question> validQuestions = new ArrayList<>();
        for (int i = 0; i < parsedRows.size(); i++) {
            String[] cols = parsedRows.get(i);
            int lineNo = rowLineNumbers.get(i);

            String subjKey = cols[0].trim().toLowerCase(Locale.ROOT);
            Integer subjectId = subjectLookup.get(subjKey);
            if (subjectId == null) {
                try {
                    subjectId = Integer.parseInt(subjKey);
                } catch (NumberFormatException ex) {
                    rowErrors.add("Line " + lineNo + ": unknown subject '" + cols[0].trim() + "'.");
                    continue;
                }
            }

            String qText = cols[1].trim();
            if (qText.isEmpty()) {
                rowErrors.add("Line " + lineNo + ": empty question text.");
                continue;
            }

            String explanation = null;
            String rawDifficulty = null;
            String rawType = null;
            String rawCorrectOpts = null;
            String rawCorrectText = null;

            if (hasHeader) {
                if (explanationColIdx >= 0 && explanationColIdx < cols.length) explanation = normalizeExplanation(cols[explanationColIdx]);
                if (difficultyColIdx >= 0 && difficultyColIdx < cols.length) rawDifficulty = cols[difficultyColIdx];
                if (typeColIdx >= 0 && typeColIdx < cols.length) rawType = cols[typeColIdx];
                if (correctOptionsColIdx >= 0 && correctOptionsColIdx < cols.length) rawCorrectOpts = cols[correctOptionsColIdx];
                if (correctTextColIdx >= 0 && correctTextColIdx < cols.length) rawCorrectText = cols[correctTextColIdx];
            } else {
                if (cols.length == 8) {
                    String c7 = cols[7].trim();
                    if (ExamStore.isValidDifficulty(c7)) rawDifficulty = c7;
                    else if (ExamStore.isValidQuestionType(c7)) rawType = c7;
                    else explanation = normalizeExplanation(c7);
                } else if (cols.length == 9) {
                    explanation = normalizeExplanation(cols[7]);
                    rawDifficulty = cols[8];
                } else if (cols.length >= 10) {
                    explanation = normalizeExplanation(cols[7]);
                    rawDifficulty = cols[8];
                    rawType = cols[9];
                    if (cols.length >= 11) rawCorrectOpts = cols[10];
                    if (cols.length >= 12) rawCorrectText = cols[11];
                }
            }

            String qType = ExamStore.TYPE_SINGLE;
            if (rawType != null && !rawType.trim().isEmpty()) {
                if (ExamStore.isValidQuestionType(rawType)) {
                    qType = ExamStore.normalizeQuestionType(rawType);
                } else {
                    rowErrors.add("Line " + lineNo + ": invalid question type '" + rawType.trim() + "'.");
                    continue;
                }
            }

            String difficulty = ExamStore.DIFFICULTY_MEDIUM;
            if (rawDifficulty != null && !rawDifficulty.trim().isEmpty()) {
                if (ExamStore.isValidDifficulty(rawDifficulty)) {
                    difficulty = ExamStore.normalizeDifficulty(rawDifficulty);
                } else {
                    warnings.add("Line " + lineNo + ": invalid difficulty '" + rawDifficulty.trim() + "' defaulted to MEDIUM.");
                    difficulty = ExamStore.DIFFICULTY_MEDIUM;
                }
            }

            ExamStore.Question q = new ExamStore.Question(
                    0, subjectId, qText,
                    new String[]{cols[2].trim(), cols[3].trim(), cols[4].trim(), cols[5].trim()},
                    0, new int[]{1, 2, 3, 4}, explanation, difficulty
            );
            q.setType(qType);

            if (ExamStore.TYPE_MULTI.equals(qType)) {
                String multiSpec = (rawCorrectOpts != null && !rawCorrectOpts.trim().isEmpty())
                        ? rawCorrectOpts.trim() : cols[6].trim();
                Set<Integer> indices = ExamStore.parseOneBasedOptionsToZeroIndices(multiSpec);
                if (indices.isEmpty()) {
                    rowErrors.add("Line " + lineNo + ": MULTI requires valid correct_options (e.g. '1,3').");
                    continue;
                }
                q.setCorrectIndices(indices);
            } else if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
                String c6 = cols[6].trim();
                int tfIdx;
                if ("1".equals(c6) || "true".equalsIgnoreCase(c6) || "t".equalsIgnoreCase(c6)) tfIdx = 0;
                else if ("2".equals(c6) || "false".equalsIgnoreCase(c6) || "f".equalsIgnoreCase(c6)) tfIdx = 1;
                else {
                    rowErrors.add("Line " + lineNo + ": TRUE_FALSE requires correct_option 1/True or 2/False.");
                    continue;
                }
                q.options = new String[]{"True", "False", "", ""};
                q.correctIndex = tfIdx;
                q.setCorrectIndices(Collections.singleton(tfIdx));
            } else if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
                String txtSpec = (rawCorrectText != null && !rawCorrectText.trim().isEmpty())
                        ? rawCorrectText.trim() : cols[6].trim();
                List<String> accepted = ExamStore.parseAcceptedAnswers(txtSpec);
                if (accepted.isEmpty()) {
                    rowErrors.add("Line " + lineNo + ": FILL_BLANK requires at least one accepted answer in correct_text.");
                    continue;
                }
                q.options = new String[]{"", "", "", ""};
                q.correctIndex = 0;
                q.setAcceptedAnswers(accepted);
            } else {
                int correctOpt;
                try {
                    correctOpt = Integer.parseInt(cols[6].trim());
                } catch (NumberFormatException ex) {
                    rowErrors.add("Line " + lineNo + ": invalid correct_option '" + cols[6].trim() + "'.");
                    continue;
                }
                if (correctOpt < 1 || correctOpt > 4) {
                    rowErrors.add("Line " + lineNo + ": correct_option must be 1..4.");
                    continue;
                }
                q.correctIndex = correctOpt - 1;
                q.setCorrectIndices(Collections.singleton(correctOpt - 1));
            }

            String valErr = validateQuestionModel(q);
            if (valErr != null) {
                rowErrors.add("Line " + lineNo + ": " + valErr);
                continue;
            }
            validQuestions.add(q);
        }

        if (validQuestions.isEmpty()) {
            return new CsvImportResult(false, 0, rowErrors.size(),
                    "0 rows imported; " + rowErrors.size() + " row(s) had errors.", warnings, rowErrors);
        }

        String insertSql = "INSERT INTO questions(subject_id, question, option1, option2, option3, option4, "
                + "correct_option, explanation, difficulty, question_type, correct_options, correct_text) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            boolean prevAuto = con.getAutoCommit();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                for (ExamStore.Question q : validQuestions) {
                    NormalizedDbFields f = normalizeForDb(q);
                    ps.setInt(1, q.subjectId);
                    ps.setString(2, f.questionText);
                    ps.setString(3, f.opt1);
                    ps.setString(4, f.opt2);
                    ps.setString(5, f.opt3);
                    ps.setString(6, f.opt4);
                    ps.setInt(7, f.correctOption);
                    if (f.explanation == null) ps.setNull(8, Types.LONGVARCHAR);
                    else ps.setString(8, f.explanation);
                    ps.setString(9, f.difficulty);
                    ps.setString(10, f.type);
                    if (f.correctOptions == null) ps.setNull(11, Types.VARCHAR);
                    else ps.setString(11, f.correctOptions);
                    if (f.correctText == null) ps.setNull(12, Types.VARCHAR);
                    else ps.setString(12, f.correctText);
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (SQLException ex) {
                try { con.rollback(); } catch (SQLException rbEx) { ex.addSuppressed(rbEx); }
                return new CsvImportResult(false, 0, rowErrors.size(),
                        "Import rolled back: " + ex.getMessage(), warnings, rowErrors);
            } finally {
                try { con.setAutoCommit(prevAuto); } catch (SQLException ignored) {}
            }

            loadQuestionsFromDB();
            StringBuilder msg = new StringBuilder("Imported ").append(validQuestions.size()).append(" question(s)");
            if (!rowErrors.isEmpty()) {
                msg.append(", skipped ").append(rowErrors.size()).append(" invalid row(s)");
            }
            if (!warnings.isEmpty()) {
                msg.append(" (").append(warnings.size()).append(" warning(s))");
            }
            msg.append(".");
            return new CsvImportResult(true, validQuestions.size(), rowErrors.size(), msg.toString(), warnings, rowErrors);

        } catch (SQLException e) {
            return new CsvImportResult(false, 0, rowErrors.size(), "Database error: " + e.getMessage(), warnings, rowErrors);
        }
    }

    private static String normalizeExplanation(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_EXPLANATION_LENGTH) {
            return trimmed.substring(0, MAX_EXPLANATION_LENGTH);
        }
        return trimmed;
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private static ExamStore.Question mapQuestion(ResultSet rs) throws SQLException {
        int questionId = extractQuestionId(rs);
        String question = rs.getString("question");

        String[] options = {
                safeString(rs.getString("option1")),
                safeString(rs.getString("option2")),
                safeString(rs.getString("option3")),
                safeString(rs.getString("option4"))
        };

        int rawCorrectOption = rs.getInt("correct_option");
        int correctIndex = (rawCorrectOption >= 1 && rawCorrectOption <= 4)
                ? (rawCorrectOption - 1)
                : 0;
        int subjectId = rs.getInt("subject_id");
        String explanation = extractOptionalString(rs, "explanation");
        String difficulty = ExamStore.normalizeDifficulty(extractOptionalString(rs, "difficulty"));
        String qType = ExamStore.normalizeQuestionType(extractOptionalString(rs, "question_type"));
        String correctOptsCsv = extractOptionalString(rs, "correct_options");
        String correctText = extractOptionalString(rs, "correct_text");
        boolean hasImg = extractOptionalBoolean(rs, "has_image");

        ExamStore.Question q = new ExamStore.Question(questionId, subjectId, question, options, correctIndex,
                new int[]{1, 2, 3, 4}, explanation, difficulty);
        q.setType(qType);
        if (ExamStore.TYPE_MULTI.equals(qType)) {
            Set<Integer> multiSet = ExamStore.parseOneBasedOptionsToZeroIndices(correctOptsCsv);
            if (multiSet.isEmpty()) {
                multiSet.add(correctIndex);
            }
            q.setCorrectIndices(multiSet);
        } else if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            q.setAcceptedAnswers(ExamStore.parseAcceptedAnswers(correctText));
        } else if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
            if (q.options[0].isEmpty()) q.options[0] = "True";
            if (q.options[1].isEmpty()) q.options[1] = "False";
            int tfIdx = (rawCorrectOption == 2) ? 1 : 0;
            q.correctIndex = tfIdx;
            q.setCorrectIndices(Collections.singleton(tfIdx));
        }
        q.setHasImage(hasImg);
        return q;
    }

    private static boolean extractOptionalBoolean(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            if (columnName.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return rs.getInt(i) != 0;
            }
        }
        return false;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static String extractOptionalString(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            if (columnName.equalsIgnoreCase(meta.getColumnLabel(i))) {
                String val = rs.getString(i);
                return (val == null || val.trim().isEmpty()) ? null : val.trim();
            }
        }
        return null;
    }

    private static int extractQuestionId(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String colName = meta.getColumnLabel(i);
            if ("id".equalsIgnoreCase(colName)
                    || "question_id".equalsIgnoreCase(colName)
                    || "qid".equalsIgnoreCase(colName)
                    || "q_id".equalsIgnoreCase(colName)) {
                return rs.getInt(i);
            }
        }
        return 0;
    }
}
