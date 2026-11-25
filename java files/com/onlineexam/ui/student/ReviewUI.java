package com.onlineexam.ui.student;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.DBConnection;
import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.data.AnswerEvaluator;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;
import java.time.LocalDateTime;

public class ReviewUI extends JFrame {

    private static final String[] OPTION_LETTERS = {"A", "B", "C", "D"};

    private enum FilterMode { ALL, CORRECT, WRONG, UNANSWERED }

    private static final class ReviewEntry {
        final int number;
        final int questionId;
        final String questionType;
        final String questionText;
        final String[] options;
        final Integer studentAns;
        final Set<Integer> studentMultiAns;
        final String studentTextAns;
        final int correctIndex;
        final Set<Integer> correctIndices;
        final List<String> acceptedAnswers;
        final boolean correct;
        final boolean wrong;
        final boolean unanswered;
        final boolean flagged;
        final String explanation;
        final String difficulty;
        final boolean hasImage;

        ReviewEntry(int number, int questionId, String questionType, String questionText, String[] options,
                    Integer studentAns, Set<Integer> studentMultiAns, String studentTextAns,
                    int correctIndex, Set<Integer> correctIndices, List<String> acceptedAnswers,
                    boolean flagged, String explanation, String difficulty, boolean hasImage) {
            this.number = number;
            this.questionId = questionId;
            this.questionType = ExamStore.normalizeQuestionType(questionType);
            this.questionText = questionText;
            this.options = options;
            this.studentAns = studentAns;
            this.studentMultiAns = studentMultiAns != null ? new LinkedHashSet<>(studentMultiAns) : new LinkedHashSet<>();
            this.studentTextAns = studentTextAns;
            this.correctIndex = correctIndex;
            this.correctIndices = correctIndices != null && !correctIndices.isEmpty()
                    ? new LinkedHashSet<>(correctIndices)
                    : new LinkedHashSet<>(Collections.singleton(correctIndex));
            this.acceptedAnswers = acceptedAnswers != null ? new ArrayList<>(acceptedAnswers) : new ArrayList<>();

            boolean answered = AnswerEvaluator.isAnsweredValues(
                    this.questionType, this.studentAns, this.studentMultiAns, this.studentTextAns);
            this.unanswered = !answered;
            this.correct = answered && AnswerEvaluator.isCorrectValues(
                    this.questionType, this.studentAns, this.studentMultiAns, this.studentTextAns,
                    this.correctIndex, this.correctIndices, this.acceptedAnswers);
            this.wrong = answered && !this.correct;
            this.flagged = flagged;
            this.explanation = (explanation == null || explanation.trim().isEmpty()) ? null : explanation.trim();
            this.difficulty = ExamStore.normalizeDifficulty(difficulty);
            this.hasImage = hasImage;
        }
    }

    private final List<ReviewEntry> allEntries = new ArrayList<>();
    private final FadeableScrollablePanel listPanel;
    private final JScrollPane scrollPane;
    private final JPanel filterBarWrap;
    private final JLabel headerTitleLabel;
    private final JLabel headerSubLabel;
    private String emptyDataMessage = "No exam answers available to review yet. Complete an exam first!";
    private final List<Timer> pendingStaggerTimers = new ArrayList<>();
    private boolean navigatedAway = false;

    // Current-session review constructor (falls back to latest MySQL attempt if session is fresh)
    public ReviewUI() {
        this(0, null, null, 0, 0, true);
    }

    // Historical MySQL attempt review constructor (preserves exact existing signature; returns to Performance History)
    public ReviewUI(int attemptId, String subjectName, String examDate, int score, int totalQuestions) {
        this(attemptId, subjectName, examDate, score, totalQuestions, false);
    }

    // Full constructor supporting return destination (Dashboard vs Performance History)
    public ReviewUI(int attemptId, String subjectName, String examDate, int score, int totalQuestions, boolean returnToDashboard) {
        final boolean useCurrentSession = (attemptId <= 0)
                && ExamStore.hasValidExamResultForCurrentUser()
                && ExamModule.getQuestionsList() != null
                && !ExamModule.getQuestionsList().isEmpty();

        final boolean goDashboard = returnToDashboard || attemptId <= 0;

        setTitle(attemptId > 0 ? ("Review Attempt #" + attemptId + " - " + subjectName) : "Review Answers - Online Exam System");
        setSize(980, 710);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.38f);

        // Frosted-white light top bar
        JPanel topBar = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(255, 255, 255, 220));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.GLASS_SLATE_BORDER);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(14, 28, 14, 28));

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);

        headerTitleLabel = new JLabel(attemptId > 0 ? ("Attempt Review \u2014 " + subjectName) : "Review Answers");
        headerTitleLabel.setFont(UITheme.font(Font.BOLD, 19f));
        headerTitleLabel.setForeground(UITheme.TEXT_PRIMARY);

        headerSubLabel = new JLabel(attemptId > 0
                ? (subjectName + "  \u2022  Score: " + score + "/" + totalQuestions + "  \u2022  " + examDate)
                : "Compare your submitted answers against the correct solutions");
        headerSubLabel.setFont(UITheme.font(Font.PLAIN, 12f));
        headerSubLabel.setForeground(UITheme.TEXT_SECONDARY);
        headerSubLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        headLeft.add(headerTitleLabel);
        headLeft.add(headerSubLabel);

        String backLabel = goDashboard ? "\u2190 Dashboard" : "\u2190 Performance History";
        Runnable goBack = () -> {
            navigatedAway = true;
            cancelPendingTimers();
            dispose();
            if (goDashboard) {
                new StudentDashboard();
            } else {
                new StudentPerformanceUI();
            }
        };

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                cancelPendingTimers();
                if (!navigatedAway) {
                    navigatedAway = true;
                    SwingUtilities.invokeLater(() -> {
                        if (goDashboard) {
                            new StudentDashboard();
                        } else {
                            new StudentPerformanceUI();
                        }
                    });
                }
            }
        });

        UITheme.ModernButton backTop = UITheme.secondaryButton(backLabel);
        backTop.addActionListener(e -> goBack.run());

        topBar.add(headLeft, BorderLayout.WEST);
        topBar.add(backTop, BorderLayout.EAST);

        // Filter Chips Bar Container: All / Correct / Wrong / Unanswered
        filterBarWrap = new JPanel(new BorderLayout());
        filterBarWrap.setOpaque(false);
        filterBarWrap.setBorder(new EmptyBorder(14, 28, 6, 28));

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(topBar, BorderLayout.NORTH);
        topSection.add(filterBarWrap, BorderLayout.SOUTH);

        listPanel = new FadeableScrollablePanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        listPanel.setBorder(new EmptyBorder(10, 28, 22, 28));

        scrollPane = new JScrollPane(
                listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(scrollPane);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 28, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.BORDER);
                g2.drawLine(0, 0, getWidth(), 0);
                g2.dispose();
            }
        };
        bottom.setOpaque(false);

        UITheme.ModernButton backBtn = UITheme.gradientPrimaryButton(goDashboard ? "Back to Dashboard" : "Back to History");
        backBtn.setPreferredSize(new Dimension(190, 40));
        backBtn.addActionListener(e -> goBack.run());
        bottom.add(backBtn);

        root.add(topSection, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        add(root);
        setVisible(true);

        if (useCurrentSession) {
            ExamConfig cfg = ExamStore.getLastCompletedExamConfig();
            if (cfg == null) {
                cfg = ExamStore.getCurrentExamConfig();
            }
            if (cfg != null && cfg.title != null && !cfg.title.trim().isEmpty()) {
                setTitle("Review Answers - " + cfg.title);
                headerTitleLabel.setText("Review Answers \u2014 " + cfg.title);
            }
            if (cfg != null && ExamConfig.SHOW_NEVER.equalsIgnoreCase(cfg.showAnswers)) {
                showHiddenAnswersNotice(cfg.getHiddenAnswersMessage());
            } else if (cfg != null && ExamConfig.SHOW_AFTER_EXAM_END.equalsIgnoreCase(cfg.showAnswers) && cfg.endAt != null) {
                final ExamConfig finalCfg = cfg;
                addEmptyMessage("Checking answer availability...");
                new SwingWorker<Boolean, Void>() {
                    @Override
                    protected Boolean doInBackground() {
                        LocalDateTime serverNow = DBConnection.getServerTime();
                        return finalCfg.areAnswersVisible(serverNow);
                    }

                    @Override
                    protected void done() {
                        if (!isDisplayable()) return;
                        boolean visible = false;
                        try {
                            visible = Boolean.TRUE.equals(get());
                        } catch (Exception ignored) {}
                        if (visible) {
                            populateCurrentSessionEntries();
                            initFilterAndRender();
                        } else {
                            showHiddenAnswersNotice(finalCfg.getHiddenAnswersMessage());
                        }
                    }
                }.execute();
            } else {
                populateCurrentSessionEntries();
                SwingUtilities.invokeLater(this::initFilterAndRender);
            }
        } else {
            addEmptyMessage("Loading review answers...");
            loadHistoricalEntriesAsync(attemptId, subjectName, examDate, score, totalQuestions);
        }
    }

    private void populateCurrentSessionEntries() {
        allEntries.clear();
        List<ExamStore.Question> qs = ExamModule.getQuestionsList();
        Set<Integer> flaggedSet = ExamModule.getLastExamFlaggedQuestions();
        if (flaggedSet == null) flaggedSet = Collections.emptySet();
        for (int i = 0; i < qs.size(); i++) {
            ExamStore.Question q = qs.get(i);
            Integer ans = ExamStore.studentAnswers.get(i);
            Set<Integer> multiAns = ExamStore.studentMultiAnswers.get(i);
            String textAns = ExamStore.studentTextAnswers.get(i);
            allEntries.add(new ReviewEntry(
                    i + 1, q.id, q.type, q.text, q.options,
                    ans, multiAns, textAns,
                    q.correctIndex, q.correctIndices, q.acceptedAnswers,
                    flaggedSet.contains(i), q.explanation, q.difficulty, q.hasImage
            ));
        }
    }

    private void showHiddenAnswersNotice(String message) {
        cancelPendingTimers();
        allEntries.clear();
        filterBarWrap.removeAll();
        filterBarWrap.setVisible(false);
        listPanel.removeAll();
        addEmptyMessage(message != null ? message : "Answers are not available for this exam.");
        listPanel.revalidate();
        listPanel.repaint();
    }

    private void loadHistoricalEntriesAsync(
            int requestedAttemptId, String subjectName, String examDate, int score, int totalQuestions) {
        // Rule 4: All DB access off the EDT
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private int resolvedId = requestedAttemptId;
            private String resolvedSubj = subjectName;
            private String resolvedExamTitle = subjectName;
            private String resolvedDate = examDate;
            private String resolvedMarksStr = score + "/" + totalQuestions;
            private String hiddenAnswersMessage = null;
            private final List<ReviewEntry> loaded = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                AttemptDAO.AttemptDetail attemptDetail = null;
                if (resolvedId <= 0) {
                    String uid = ExamStore.getCurrentUserId();
                    if (uid != null && !uid.trim().isEmpty()) {
                        List<AttemptDAO.AttemptDetail> past = AttemptDAO.getAttemptDetails(null, uid, false);
                        if (past != null && !past.isEmpty()) {
                            AttemptDAO.AttemptDetail chosen = past.get(0);
                            for (AttemptDAO.AttemptDetail d : past) {
                                if (!AttemptDAO.getAttemptReviewItems(d.id).isEmpty()) {
                                    chosen = d;
                                    break;
                                }
                            }
                            attemptDetail = chosen;
                            resolvedId = chosen.id;
                        }
                    }
                } else {
                    attemptDetail = AttemptDAO.getAttemptById(resolvedId);
                }

                if (attemptDetail != null) {
                    resolvedSubj = attemptDetail.subjectName;
                    resolvedExamTitle = (attemptDetail.examTitle != null && !attemptDetail.examTitle.trim().isEmpty())
                            ? attemptDetail.examTitle : attemptDetail.subjectName;
                    resolvedDate = attemptDetail.attemptedDate;
                    resolvedMarksStr = ExamConfig.formatMarks(attemptDetail.marksObtained) + "/" + ExamConfig.formatMarks(attemptDetail.totalMarks);

                    boolean visible = true;
                    if (ExamConfig.SHOW_NEVER.equalsIgnoreCase(attemptDetail.showAnswers)) {
                        visible = false;
                    } else if (ExamConfig.SHOW_AFTER_EXAM_END.equalsIgnoreCase(attemptDetail.showAnswers)
                            && attemptDetail.examEndAt != null) {
                        LocalDateTime serverNow = DBConnection.getServerTime();
                        visible = ExamConfig.areAnswersVisible(attemptDetail.showAnswers, attemptDetail.examEndAt, serverNow);
                    }
                    if (!visible) {
                        hiddenAnswersMessage = ExamConfig.getHiddenAnswersMessage(attemptDetail.showAnswers, attemptDetail.examEndAt);
                        return null;
                    }
                }

                if (resolvedId > 0) {
                    List<AttemptDAO.HistoricalReviewItem> items = AttemptDAO.getAttemptReviewItems(resolvedId);
                    for (int i = 0; i < items.size(); i++) {
                        AttemptDAO.HistoricalReviewItem item = items.get(i);
                        Integer studentZeroBased = item.selectedOption == null ? null : (item.selectedOption - 1);
                        int correctZeroBased = Math.max(0, Math.min(3, item.correctOption - 1));
                        Set<Integer> studentMulti = ExamStore.parseOneBasedOptionsToZeroIndices(item.selectedOptions);
                        Set<Integer> correctMulti = ExamStore.parseOneBasedOptionsToZeroIndices(item.correctOptions);
                        if (correctMulti.isEmpty()) {
                            correctMulti.add(correctZeroBased);
                        }
                        List<String> accepted = ExamStore.parseAcceptedAnswers(item.correctText);
                        loaded.add(new ReviewEntry(
                                i + 1, item.questionId, item.questionType, item.questionText, item.options,
                                studentZeroBased, studentMulti, item.answerText,
                                correctZeroBased, correctMulti, accepted,
                                false, item.explanation, item.difficulty, item.hasImage
                        ));
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                if (resolvedId > 0) {
                    String titleDisp = (resolvedExamTitle != null && !resolvedExamTitle.trim().isEmpty())
                            ? resolvedExamTitle : resolvedSubj;
                    setTitle("Review Attempt #" + resolvedId + " - " + titleDisp);
                    headerTitleLabel.setText("Attempt Review \u2014 " + titleDisp);
                    headerSubLabel.setText(resolvedSubj + "  \u2022  Marks: " + resolvedMarksStr + "  \u2022  " + resolvedDate);
                    emptyDataMessage = "Detailed per-question answers were not recorded for this older attempt. Take a new exam to inspect full question-by-question reviews.";
                }
                if (hiddenAnswersMessage != null) {
                    showHiddenAnswersNotice(hiddenAnswersMessage);
                    return;
                }
                filterBarWrap.setVisible(true);
                allEntries.clear();
                allEntries.addAll(loaded);
                initFilterAndRender();
            }
        };
        worker.execute();
    }

    private void initFilterAndRender() {
        int correctCount = 0;
        int wrongCount = 0;
        int unansweredCount = 0;
        for (ReviewEntry e : allEntries) {
            if (e.correct) correctCount++;
            else if (e.wrong) wrongCount++;
            else unansweredCount++;
        }

        filterBarWrap.removeAll();
        String[] chipLabels = {
                "All (" + allEntries.size() + ")",
                "Correct (" + correctCount + ")",
                "Wrong (" + wrongCount + ")",
                "Unanswered (" + unansweredCount + ")"
        };
        SlidingFilterBar filterBar = new SlidingFilterBar(chipLabels, idx -> {
            FilterMode[] modes = FilterMode.values();
            renderFilteredCards(modes[Math.max(0, Math.min(modes.length - 1, idx))], true);
        });
        filterBarWrap.add(filterBar, BorderLayout.WEST);
        filterBarWrap.revalidate();
        filterBarWrap.repaint();

        renderFilteredCards(FilterMode.ALL, false);
    }

    private void cancelPendingTimers() {
        for (Timer t : pendingStaggerTimers) {
            if (t != null && t.isRunning()) t.stop();
        }
        pendingStaggerTimers.clear();
    }

    private void renderFilteredCards(FilterMode mode, boolean withShortFade) {
        cancelPendingTimers();
        listPanel.removeAll();

        if (allEntries.isEmpty()) {
            addEmptyMessage(emptyDataMessage);
        } else {
            List<GlassPanel> cardsToAnimate = new ArrayList<>();
            int matched = 0;
            for (ReviewEntry entry : allEntries) {
                boolean include;
                switch (mode) {
                    case CORRECT:    include = entry.correct;    break;
                    case WRONG:      include = entry.wrong;      break;
                    case UNANSWERED: include = entry.unanswered; break;
                    default:         include = true;             break;
                }
                if (!include) continue;

                matched++;
                GlassPanel card = createReviewCard(entry);
                listPanel.add(card);
                listPanel.add(Box.createVerticalStrut(12));
                if (!withShortFade && cardsToAnimate.size() < 12) {
                    cardsToAnimate.add(card);
                }
            }

            if (matched == 0) {
                String modeName = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
                addEmptyMessage("No questions match the \"" + modeName + "\" filter.");
            } else if (!withShortFade) {
                staggerEnterCards(cardsToAnimate);
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
        if (withShortFade) {
            Anim.fadeIn(listPanel, 200);
        }
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
    }

    private void staggerEnterCards(List<GlassPanel> cards) {
        for (int i = 0; i < cards.size(); i++) {
            final GlassPanel card = cards.get(i);
            card.setAlpha(0f);
            card.setSlideOffset(0, 16);
            int delay = Math.max(16, i * 45);
            Timer st = new Timer(delay, e -> {
                ((Timer) e.getSource()).stop();
                if (isDisplayable()) {
                    Anim.slideIn(card, Anim.Direction.UP, 300, 16);
                }
            });
            st.setRepeats(false);
            pendingStaggerTimers.add(st);
            st.start();
        }
    }

    private void addEmptyMessage(String text) {
        GlassPanel emptyCard = new GlassPanel(16);
        emptyCard.setSpotlightEnabled(false);
        emptyCard.setLayout(new BorderLayout());
        emptyCard.setBorder(new EmptyBorder(40, 28, 40, 28));
        emptyCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        emptyCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel empty = new JLabel("<html><div style='text-align:center;'>" + text + "</div></html>", SwingConstants.CENTER);
        empty.setFont(UITheme.FONT_HEADING);
        empty.setForeground(UITheme.TEXT_MUTED);
        emptyCard.add(empty, BorderLayout.CENTER);
        listPanel.add(Box.createVerticalStrut(14));
        listPanel.add(emptyCard);
    }

    private GlassPanel createReviewCard(ReviewEntry entry) {
        boolean isUnanswered = entry.unanswered;
        boolean isCorrect = entry.correct;

        Color accentBar = isCorrect ? UITheme.SUCCESS : (isUnanswered ? UITheme.WARNING : UITheme.DANGER);

        GlassPanel card = new GlassPanel(16);
        card.setLeftAccentColor(accentBar);
        card.setShadowColor(isCorrect ? UITheme.SUCCESS : UITheme.PRIMARY);
        card.setSpotlightEnabled(false);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(18, 22, 20, 22));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 960));

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        JLabel qTitle = new JLabel("<html><b>Q" + entry.number + ".</b> "
                + (entry.questionText != null ? entry.questionText : "") + "</html>");
        qTitle.setFont(UITheme.font(Font.PLAIN, 15f));
        qTitle.setForeground(UITheme.TEXT_PRIMARY);

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badgeWrap.setOpaque(false);

        if (!ExamStore.TYPE_SINGLE.equals(entry.questionType)) {
            badgeWrap.add(UITheme.createBadge(
                    ExamStore.formatQuestionTypeLabel(entry.questionType), UITheme.VIOLET_SOFT, UITheme.VIOLET));
        }
        badgeWrap.add(UITheme.createDifficultyBadge(entry.difficulty));
        if (entry.flagged) {
            badgeWrap.add(UITheme.createBadge("Flagged", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
        }
        if (isUnanswered) {
            badgeWrap.add(UITheme.createBadge("Unanswered", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
        } else if (isCorrect) {
            badgeWrap.add(UITheme.createBadge("Correct", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
        } else {
            badgeWrap.add(UITheme.createBadge("Incorrect", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT));
        }

        topRow.add(qTitle, BorderLayout.CENTER);
        topRow.add(badgeWrap, BorderLayout.EAST);

        // Optional lazily-loaded image preview inside review card
        JPanel northWrap = new JPanel();
        northWrap.setLayout(new BoxLayout(northWrap, BoxLayout.Y_AXIS));
        northWrap.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        northWrap.add(topRow);
        if (entry.hasImage && entry.questionId > 0) {
            JPanel imgHolder = createLazyReviewImagePanel(entry.questionId);
            imgHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
            northWrap.add(Box.createVerticalStrut(8));
            northWrap.add(imgHolder);
        }

        JComponent answerView;
        if (ExamStore.TYPE_FILL_BLANK.equals(entry.questionType)) {
            JPanel fbPanel = new JPanel(new GridLayout(2, 1, 0, 8));
            fbPanel.setOpaque(false);

            Color yourBg = isUnanswered ? UITheme.WARNING_SOFT : (isCorrect ? UITheme.SUCCESS_SOFT : UITheme.DANGER_SOFT);
            Color yourBorder = isUnanswered ? UITheme.WARNING : (isCorrect ? UITheme.SUCCESS : UITheme.DANGER);
            Color yourFg = isUnanswered ? UITheme.WARNING_TEXT : (isCorrect ? UITheme.SUCCESS_TEXT : UITheme.DANGER_TEXT);
            String yourText = isUnanswered ? "Not answered" : entry.studentTextAns;
            String yourSuffix = isUnanswered ? "" : (isCorrect ? "  \u2713" : "  \u2717");

            UITheme.RoundedPanel yourRow = new UITheme.RoundedPanel(10, yourBg, yourBorder);
            yourRow.setLayout(new BorderLayout(8, 0));
            yourRow.setBorder(new EmptyBorder(9, 14, 9, 14));
            JLabel yourLbl = new JLabel("<html><b>Your Answer:</b>  " + escapeHtml(yourText) + yourSuffix + "</html>");
            yourLbl.setFont(UITheme.font(Font.PLAIN, 13f));
            yourLbl.setForeground(yourFg);
            yourRow.add(yourLbl, BorderLayout.CENTER);

            String acceptedJoined = entry.acceptedAnswers.isEmpty()
                    ? "-"
                    : String.join("  \u2022  ", entry.acceptedAnswers);
            UITheme.RoundedPanel accRow = new UITheme.RoundedPanel(10, UITheme.SUCCESS_SOFT, UITheme.SUCCESS);
            accRow.setLayout(new BorderLayout(8, 0));
            accRow.setBorder(new EmptyBorder(9, 14, 9, 14));
            JLabel accLbl = new JLabel("<html><b>Accepted Answer(s):</b>  " + escapeHtml(acceptedJoined) + "</html>");
            accLbl.setFont(UITheme.font(Font.BOLD, 13f));
            accLbl.setForeground(UITheme.SUCCESS_TEXT);
            accRow.add(accLbl, BorderLayout.CENTER);

            fbPanel.add(yourRow);
            fbPanel.add(accRow);
            answerView = fbPanel;
        } else if (ExamStore.TYPE_TRUE_FALSE.equals(entry.questionType)) {
            JPanel tfGrid = new JPanel(new GridLayout(1, 2, 12, 0));
            tfGrid.setOpaque(false);
            String[] tfTexts = {"True", "False"};
            for (int j = 0; j < 2; j++) {
                boolean isCorrectOpt = (j == entry.correctIndex);
                boolean isStudentOpt = (entry.studentAns != null && entry.studentAns == j);
                tfGrid.add(buildOptionRow(tfTexts[j], "", isCorrectOpt, isStudentOpt, false));
            }
            answerView = tfGrid;
        } else if (ExamStore.TYPE_MULTI.equals(entry.questionType)) {
            JPanel optionsGrid = new JPanel(new GridLayout(4, 1, 0, 7));
            optionsGrid.setOpaque(false);
            for (int j = 0; j < 4; j++) {
                String optText = (entry.options != null && j < entry.options.length && entry.options[j] != null)
                        ? entry.options[j] : "";
                boolean isCorrectOpt = entry.correctIndices.contains(j);
                boolean isStudentOpt = entry.studentMultiAns.contains(j);
                optionsGrid.add(buildOptionRow(optText, OPTION_LETTERS[j] + ")  ", isCorrectOpt, isStudentOpt, true));
            }
            answerView = optionsGrid;
        } else {
            JPanel optionsGrid = new JPanel(new GridLayout(4, 1, 0, 7));
            optionsGrid.setOpaque(false);
            for (int j = 0; j < 4; j++) {
                String optText = (entry.options != null && j < entry.options.length && entry.options[j] != null)
                        ? entry.options[j] : "";
                boolean isCorrectOpt = (j == entry.correctIndex);
                boolean isStudentOpt = (entry.studentAns != null && entry.studentAns == j);
                optionsGrid.add(buildOptionRow(optText, OPTION_LETTERS[j] + ")  ", isCorrectOpt, isStudentOpt, false));
            }
            answerView = optionsGrid;
        }

        card.add(northWrap, BorderLayout.NORTH);
        card.add(answerView, BorderLayout.CENTER);

        // Collapsible "Why this is correct" explanation box (ONLY shown when explanation exists)
        if (entry.explanation != null && !entry.explanation.isEmpty()) {
            card.add(new ExplanationBox(entry.explanation, listPanel), BorderLayout.SOUTH);
        }

        return card;
    }

    private UITheme.RoundedPanel buildOptionRow(
            String optText, String prefix, boolean isCorrectOpt, boolean isStudentOpt, boolean multiMode) {
        Color bg = new Color(248, 250, 252, 220);
        Color border = UITheme.BORDER;
        Color fg = UITheme.TEXT_SECONDARY;
        String tag = "";

        if (isCorrectOpt && isStudentOpt) {
            bg = UITheme.SUCCESS_SOFT;
            border = UITheme.SUCCESS;
            fg = UITheme.SUCCESS_TEXT;
            tag = "  (Your Answer \u2713)";
        } else if (isCorrectOpt) {
            bg = UITheme.SUCCESS_SOFT;
            border = UITheme.SUCCESS;
            fg = UITheme.SUCCESS_TEXT;
            tag = multiMode ? "  (Correct \u2014 Missed)" : "  (Correct Answer \u2713)";
        } else if (isStudentOpt) {
            bg = UITheme.DANGER_SOFT;
            border = UITheme.DANGER;
            fg = UITheme.DANGER_TEXT;
            tag = "  (Your Answer \u2717)";
        }

        String checkBoxGlyph = multiMode ? (isStudentOpt ? "\u2611  " : "\u2610  ") : "";
        UITheme.RoundedPanel row = new UITheme.RoundedPanel(10, bg, border);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(8, 13, 8, 13));

        JLabel optLabel = new JLabel("<html>" + checkBoxGlyph + prefix + escapeHtml(optText) + tag + "</html>");
        optLabel.setFont(UITheme.font((isCorrectOpt || isStudentOpt) ? Font.BOLD : Font.PLAIN, 13f));
        optLabel.setForeground(fg);

        row.add(optLabel, BorderLayout.CENTER);
        return row;
    }

    private static String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel createLazyReviewImagePanel(int questionId) {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        JLabel imgLbl = new JLabel("Loading image...");
        imgLbl.setFont(UITheme.font(Font.PLAIN, 11.5f));
        imgLbl.setForeground(UITheme.TEXT_MUTED);
        wrap.add(imgLbl);

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                byte[] bytes = QuestionDAO.getQuestionImage(questionId);
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return ImageIO.read(new ByteArrayInputStream(bytes));
                } catch (Exception ex) {
                    return null;
                }
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    BufferedImage full = get();
                    if (full == null) {
                        wrap.setVisible(false);
                        return;
                    }
                    double scale = Math.min(1.0, Math.min(380.0 / full.getWidth(), 180.0 / full.getHeight()));
                    int tw = Math.max(40, (int) Math.round(full.getWidth() * scale));
                    int th = Math.max(30, (int) Math.round(full.getHeight() * scale));
                    Image scaled = full.getScaledInstance(tw, th, Image.SCALE_SMOOTH);
                    imgLbl.setText(null);
                    imgLbl.setIcon(new ImageIcon(scaled));
                    imgLbl.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
                    imgLbl.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    imgLbl.setToolTipText("Click to zoom");
                    imgLbl.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            double zScale = Math.min(1.0, Math.min(820.0 / full.getWidth(), 540.0 / full.getHeight()));
                            int zw = Math.max(120, (int) Math.round(full.getWidth() * zScale));
                            int zh = Math.max(90, (int) Math.round(full.getHeight() * zScale));
                            JLabel zoomView = new JLabel(new ImageIcon(full.getScaledInstance(zw, zh, Image.SCALE_SMOOTH)));
                            zoomView.setHorizontalAlignment(SwingConstants.CENTER);
                            GlassDialog.showCustom(ReviewUI.this, "Question Image", null, zoomView, "Close",
                                    Math.max(420, zw + 80), Math.max(300, zh + 150));
                        }
                    });
                    listPanel.revalidate();
                    listPanel.repaint();
                } catch (Exception ignored) {
                    wrap.setVisible(false);
                }
            }
        }.execute();
        return wrap;
    }

    // ================= FADEABLE SCROLLABLE PANEL FOR FILTER TRANSITIONS =================

    private static class FadeableScrollablePanel extends UITheme.ScrollablePanel implements Anim.Fadeable {
        private float alpha = 1.0f;

        @Override public void setAlpha(float alpha) { this.alpha = Anim.clamp01(alpha); repaint(); }
        @Override public float getAlpha() { return alpha; }

        @Override
        public void paint(Graphics g) {
            if (alpha <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            if (alpha < 0.999f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            super.paint(g2);
            g2.dispose();
        }
    }

    // ================= COLLAPSIBLE LIGHT-BLUE "WHY THIS IS CORRECT" BOX WITH JAVA2D LIGHTBULB =================

    private static class ExplanationBox extends JPanel {
        private boolean expanded = false;
        private int currentBodyHeight = 0;
        private Timer heightTimer;

        private final JLabel toggleLabel;
        private final LightbulbIcon bulbIcon;
        private final JPanel bodyClipPanel;
        private final JTextArea explanationText;
        private final JComponent layoutRoot;

        ExplanationBox(String explanation, JComponent layoutRoot) {
            this.layoutRoot = layoutRoot;
            setOpaque(false);
            setLayout(new BorderLayout(0, 0));
            setBorder(new EmptyBorder(4, 0, 0, 0));

            JPanel headerRow = new JPanel(new BorderLayout(8, 0));
            headerRow.setOpaque(false);
            headerRow.setBorder(new EmptyBorder(9, 12, 9, 12));
            headerRow.setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);

            bulbIcon = new LightbulbIcon();
            JLabel heading = new JLabel("Why this is correct");
            heading.setFont(UITheme.font(Font.BOLD, 12.5f));
            heading.setForeground(new Color(3, 105, 161)); // Sky-700 #0369A1

            left.add(bulbIcon);
            left.add(heading);

            toggleLabel = new JLabel("Show explanation \u25BE");
            toggleLabel.setFont(UITheme.font(Font.BOLD, 11.5f));
            toggleLabel.setForeground(UITheme.SKY);

            headerRow.add(left, BorderLayout.WEST);
            headerRow.add(toggleLabel, BorderLayout.EAST);

            explanationText = new JTextArea(explanation);
            explanationText.setLineWrap(true);
            explanationText.setWrapStyleWord(true);
            explanationText.setEditable(false);
            explanationText.setFocusable(false);
            explanationText.setOpaque(false);
            explanationText.setFont(UITheme.font(Font.PLAIN, 13f));
            explanationText.setForeground(new Color(15, 23, 42));
            explanationText.setBorder(new EmptyBorder(0, 14, 12, 14));

            bodyClipPanel = new JPanel(new BorderLayout()) {
                @Override
                public Dimension getPreferredSize() {
                    int w = Math.max(200, ExplanationBox.this.getWidth() - 4);
                    return new Dimension(w, currentBodyHeight);
                }

                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, currentBodyHeight);
                }
            };
            bodyClipPanel.setOpaque(false);
            bodyClipPanel.add(explanationText, BorderLayout.NORTH);

            add(headerRow, BorderLayout.NORTH);
            add(bodyClipPanel, BorderLayout.CENTER);

            headerRow.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleExpand();
                }
            });
        }

        private int computeExpandedHeight() {
            int availW = Math.max(260, getWidth() - 32);
            explanationText.setSize(availW, Short.MAX_VALUE);
            Dimension pref = explanationText.getPreferredSize();
            return Math.max(36, pref.height + 6);
        }

        private void toggleExpand() {
            expanded = !expanded;
            toggleLabel.setText(expanded ? "Hide explanation \u25B4" : "Show explanation \u25BE");
            bulbIcon.setGlowing(expanded);

            if (heightTimer != null && heightTimer.isRunning()) {
                heightTimer.stop();
            }

            final int startH = currentBodyHeight;
            final int targetH = expanded ? computeExpandedHeight() : 0;

            heightTimer = Anim.animate(240, p -> {
                float eased = Anim.easeOutCubic(p);
                currentBodyHeight = Math.round(startH + (targetH - startH) * eased);
                bodyClipPanel.revalidate();
                revalidate();
                if (layoutRoot != null) {
                    layoutRoot.revalidate();
                    layoutRoot.repaint();
                }
            }, () -> {
                currentBodyHeight = targetH;
                bodyClipPanel.revalidate();
                revalidate();
                if (layoutRoot != null) {
                    layoutRoot.revalidate();
                    layoutRoot.repaint();
                }
            });
        }

        @Override
        public void removeNotify() {
            if (heightTimer != null) {
                heightTimer.stop();
            }
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            Shape box = new RoundRectangle2D.Float(0.5f, 4.5f, w - 1f, h - 5f, 12f, 12f);

            // Light-blue tinted explanation panel
            g2.setColor(new Color(239, 246, 255, 235));
            g2.fill(box);

            g2.setColor(new Color(125, 211, 252, 180));
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(box);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Small crisp Java2D lightbulb icon (glows softly when explanation is expanded).
     */
    private static class LightbulbIcon extends JComponent {
        private boolean glowing = false;

        LightbulbIcon() {
            setPreferredSize(new Dimension(20, 20));
            setMinimumSize(new Dimension(20, 20));
            setMaximumSize(new Dimension(20, 20));
            setOpaque(false);
        }

        void setGlowing(boolean glowing) {
            this.glowing = glowing;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - 1.5f;

            if (glowing) {
                g2.setColor(new Color(251, 191, 36, 95));
                g2.fillOval((int) (cx - 7), (int) (cy - 7), 14, 14);
            }

            Color stroke = glowing ? new Color(217, 119, 6) : new Color(2, 132, 199);
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Bulb dome
            g2.draw(new Arc2D.Float(cx - 5f, cy - 5.2f, 10f, 10f, -40, 260, Arc2D.OPEN));
            // Neck tapers into screw base
            g2.draw(new Line2D.Float(cx - 3.6f, cy + 3.2f, cx - 2.6f, cy + 6.4f));
            g2.draw(new Line2D.Float(cx + 3.6f, cy + 3.2f, cx + 2.6f, cy + 6.4f));
            // Screw base bars
            g2.draw(new Line2D.Float(cx - 2.6f, cy + 6.4f, cx + 2.6f, cy + 6.4f));
            g2.draw(new Line2D.Float(cx - 2.0f, cy + 8.6f, cx + 2.0f, cy + 8.6f));

            // Tiny light rays when expanded
            if (glowing) {
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Float(cx, cy - 8.2f, cx, cy - 6.9f));
                g2.draw(new Line2D.Float(cx - 7.8f, cy - 2.5f, cx - 6.6f, cy - 2.0f));
                g2.draw(new Line2D.Float(cx + 7.8f, cy - 2.5f, cx + 6.6f, cy - 2.0f));
            }

            g2.dispose();
        }
    }

    // ================= ANIMATED SLIDING FILTER CHIPS (All / Correct / Wrong / Unanswered) =================

    private static class SlidingFilterBar extends JPanel {
        private final JButton[] buttons;
        private int selectedIndex = 0;
        private float pillX = 4f;
        private float pillW = 80f;
        private boolean initialized = false;
        private Timer slideTimer;

        SlidingFilterBar(String[] labels, java.util.function.IntConsumer onSelect) {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
            setBorder(new EmptyBorder(2, 4, 2, 4));

            buttons = new JButton[labels.length];
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                JButton btn = new JButton(labels[i]);
                btn.setFont(UITheme.font(i == 0 ? Font.BOLD : Font.PLAIN, 12.5f));
                btn.setForeground(i == 0 ? Color.WHITE : UITheme.TEXT_SECONDARY);
                btn.setFocusPainted(false);
                btn.setBorderPainted(false);
                btn.setContentAreaFilled(false);
                btn.setOpaque(false);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                btn.setBorder(new EmptyBorder(7, 15, 7, 15));

                btn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) {
                        if (selectedIndex != idx) btn.setForeground(UITheme.PRIMARY);
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        if (selectedIndex != idx) btn.setForeground(UITheme.TEXT_SECONDARY);
                    }
                });

                btn.addActionListener(e -> {
                    if (selectedIndex == idx) return;
                    selectIndex(idx);
                    if (onSelect != null) onSelect.accept(idx);
                });

                buttons[i] = btn;
                add(btn);
            }
        }

        private void selectIndex(int idx) {
            selectedIndex = idx;
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setFont(UITheme.font(i == idx ? Font.BOLD : Font.PLAIN, 12.5f));
                buttons[i].setForeground(i == idx ? Color.WHITE : UITheme.TEXT_SECONDARY);
            }
            Rectangle r = buttons[idx].getBounds();
            if (r.width <= 0) return;

            if (!initialized) {
                pillX = r.x;
                pillW = r.width;
                initialized = true;
                repaint();
                return;
            }

            if (slideTimer != null && slideTimer.isRunning()) slideTimer.stop();
            final float startX = pillX;
            final float startW = pillW;
            final float endX = r.x;
            final float endW = r.width;
            slideTimer = Anim.animate(240, p -> {
                float e = Anim.easeInOutCubic(p);
                pillX = startX + (endX - startX) * e;
                pillW = startW + (endW - startW) * e;
                repaint();
            }, () -> {
                pillX = endX;
                pillW = endW;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(255, 255, 255, 200));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g2.setColor(UITheme.GLASS_SLATE_BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 14, 14));

            if (!initialized && buttons.length > 0 && buttons[selectedIndex].getWidth() > 0) {
                Rectangle r = buttons[selectedIndex].getBounds();
                pillX = r.x;
                pillW = r.width;
                initialized = true;
            }

            if (initialized && buttons.length > 0) {
                Rectangle r = buttons[selectedIndex].getBounds();
                float py = r.y;
                float ph = r.height;
                UITheme.paintColoredShadow(g2, pillX, py, pillW, ph, 10f, UITheme.PRIMARY, 3, 2);
                g2.setPaint(UITheme.createPrimaryGradient(pillX, py, pillX + pillW, py + ph));
                g2.fill(new RoundRectangle2D.Float(pillX, py, pillW, ph, 10, 10));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
