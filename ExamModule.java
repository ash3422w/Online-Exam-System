package com.onlineexam.ui.student;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.datatransfer.StringSelection;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.onlineexam.dao.AchievementService;
import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.ExamDAO;
import com.onlineexam.dao.ProctorDAO;
import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.dao.SessionDAO;
import com.onlineexam.dao.WebcamProctorService;
import com.onlineexam.data.AnswerEvaluator;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.LoginUI;
import com.onlineexam.ui.UITheme;

public class ExamModule extends JFrame {

    private static final int FIVE_MINUTE_WARNING_SECONDS = 5 * 60;
    private static final int MAX_FOCUS_VIOLATIONS = 3;
    private static final String[] OPTION_LETTERS = {"A", "B", "C", "D"};

    private JLabel questionNumberBadge;
    private JLabel difficultyBadge;
    private JLabel typeHintBadge;
    private JLabel questionLabel;
    private QuestionImageBanner imageBanner;
    private JLabel progressLabel;
    private JLabel proctorLabel;
    private JLabel proctorWarnLabel;
    private JLabel statAnsweredVal, statFlaggedVal, statRemainingVal;
    private JLabel webcamIndicatorBadge;
    private WebcamProctorService.ProctoringSession webcamSession;

    private CircularTimerWidget timerWidget;
    private SegmentedProgressBar segmentedProgress;
    private EdgeDangerGlowOverlay edgeGlowOverlay;
    private QuestionSlideContainer questionSlideContainer;
    private CardLayout answerCardLayout;
    private JPanel answerCardsPanel;
    private OptionCard[] optionCards;
    private OptionCard[] tfCards;
    private NoPasteFillBlankField fillBlankField;
    private boolean updatingFillBlank = false;
    private int imageRequestToken = 0;
    private NavPaletteButton[] paletteButtons;
    private WiggleFlagButton flagBtn;
    private UITheme.ModernButton prevBtn, nextBtn, submitBtn;

    private List<ExamStore.Question> questions;
    private final Set<Integer> flaggedQuestions = new HashSet<>();
    private ExamConfig examConfig;

    private int index = 0;
    private int selectedOptionIndex = -1;
    private int totalTimeSeconds;
    private int timeLeft;
    private int focusViolations = 0;
    private int proctorWarningCount = 0;
    private final String examSessionId = UUID.randomUUID().toString();
    private KeyEventDispatcher keyEventDispatcher;
    private AWTEventListener rightClickListener;
    private boolean modalDialogOpen = false;
    private boolean autoSubmittedByProctor = false;
    private javax.swing.Timer timer;
    private javax.swing.Timer slideTransitionTimer;
    private javax.swing.Timer palettePulseTimer;
    private float palettePulsePhase = 0f;
    private boolean fiveMinuteWarningShown = false;
    private final AtomicBoolean submitted = new AtomicBoolean(false);
    private JPanel activeConfirmOverlay = null;

    private static Set<Integer> lastExamFlaggedQuestions = new HashSet<>();

    public static List<ExamStore.Question> getQuestionsList() {
        return ExamStore.getCurrentExamQuestions();
    }

    public static Set<Integer> getLastExamFlaggedQuestions() {
        return new HashSet<>(lastExamFlaggedQuestions);
    }

    public ExamModule() {
        ExamConfig activeCfg = ExamStore.getCurrentExamConfig();
        int subjectId = (activeCfg != null && activeCfg.subjectId > 0)
                ? activeCfg.subjectId : ExamStore.getSelectedSubject();
        if (activeCfg == null && subjectId > 0) {
            List<ExamConfig> subjectExams = ExamDAO.getExamsForSubject(subjectId);
            if (!subjectExams.isEmpty()) {
                activeCfg = subjectExams.get(0);
            }
        }
        this.examConfig = activeCfg;

        // SECURITY GATE: always re-verify DB server-time window & attempt limits before loading the paper
        ExamDAO.ExamAvailabilityResult startCheck = null;
        if (activeCfg != null && activeCfg.id > 0) {
            startCheck = ExamDAO.canStart(ExamStore.getCurrentUserId(), activeCfg.id);
            if (!startCheck.canStart) {
                JOptionPane.showMessageDialog(
                        null,
                        startCheck.reason,
                        "Exam Unavailable",
                        JOptionPane.WARNING_MESSAGE
                );
                dispose();
                new SubjectSelectionUI();
                return;
            }
            if (startCheck.exam != null) {
                activeCfg = startCheck.exam;
                this.examConfig = activeCfg;
            }
        }

        // Set in_exam = 1 on the active session row before loading the paper
        String curUid = ExamStore.getCurrentUserId();
        String curTok = ExamStore.getSessionToken();
        if (curUid != null && !curUid.isEmpty() && curTok != null && !curTok.isEmpty()) {
            boolean marked = SessionDAO.setInExam(curUid, curTok, activeCfg != null ? activeCfg.id : null, true);
            if (!marked) {
                JOptionPane.showMessageDialog(
                        null,
                        "Your session is no longer valid. Please sign in again.",
                        "Session Expired",
                        JOptionPane.WARNING_MESSAGE
                );
                dispose();
                new LoginUI();
                return;
            }
            ExamStore.setCurrentlyInExam(true);
        }

        // Reset in-progress attempt state while preserving the selected exam config
        ExamStore.resetAttemptState();
        if (activeCfg != null) {
            ExamStore.setSelectedExam(activeCfg);
        }

        String subjectName = (activeCfg != null && activeCfg.subjectName != null)
                ? activeCfg.subjectName : ExamStore.getSubjectName(subjectId);
        String examTitle = (activeCfg != null && activeCfg.title != null && !activeCfg.title.trim().isEmpty())
                ? activeCfg.title : (subjectName + " Examination");

        int questionLimit = (activeCfg != null && activeCfg.questionCount > 0)
                ? activeCfg.questionCount : ExamStore.getExamQuestionCount();
        questions = (activeCfg != null)
                ? QuestionDAO.getRandomQuestionsForExam(activeCfg)
                : QuestionDAO.getRandomQuestionsBySubject(subjectId, questionLimit);
        if (questions == null || questions.isEmpty()) {
            questions = ExamStore.generateRandomExamQuestions(subjectId, questionLimit);
        }

        if (questions == null || questions.isEmpty()) {
            clearExamSessionState();
            JOptionPane.showMessageDialog(this, "No questions available for this subject!");
            dispose();
            new SubjectSelectionUI();
            return;
        }

        // Shuffle A/B/C/D options per question (preserving original DB option mapping & explanation)
        questions = ExamStore.shuffleAllQuestionOptions(questions);
        ExamStore.setCurrentExamQuestions(questions);

        // Task 8: Log EXAM_STARTED immediately under UUID examSessionId so audit survives crashes
        Integer initialExamId = (activeCfg != null && activeCfg.id > 0) ? activeCfg.id : null;
        ProctorDAO.logEvent(examSessionId, ExamStore.getCurrentUserId(), initialExamId, null,
                ProctorDAO.EVENT_EXAM_STARTED, "Exam started (" + questions.size() + " questions)");

        int configuredDurationSec = (activeCfg != null)
                ? activeCfg.getEffectiveDurationSeconds(questions.size())
                : (questions.size() * 60);

        String endAtCapNotice = null;
        if (startCheck != null && activeCfg != null && activeCfg.endAt != null && startCheck.remainingSeconds >= 0) {
            long untilEndSec = startCheck.remainingSeconds;
            if (untilEndSec < configuredDurationSec) {
                totalTimeSeconds = (int) Math.max(1L, untilEndSec);
                int limitedMins = (int) Math.max(1L, Math.round(totalTimeSeconds / 60.0));
                endAtCapNotice = "This exam closes at " + activeCfg.endAt.format(ExamConfig.TIME_HM)
                        + ", your time is limited to " + limitedMins + " minute" + (limitedMins == 1 ? "" : "s");
            } else {
                totalTimeSeconds = configuredDurationSec;
            }
        } else {
            totalTimeSeconds = configuredDurationSec;
        }
        timeLeft = totalTimeSeconds;

        setTitle("Online Exam - " + examTitle);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                cleanupProctorInterceptors();
                clearExamSessionState();
            }
        });

        // Calm Pastel Aurora Light background at low intensity
        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.28f);

        // ================= TOP FROSTED LIGHT BAR + SEGMENTED PROGRESS =================
        JPanel topBar = new JPanel(new BorderLayout(18, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(255, 255, 255, 224));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.GLASS_SLATE_BORDER);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(12, 32, 12, 32));

        // Left: Subject crest + title + shortcuts
        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleLeft.setOpaque(false);

        JComponent crest = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setPaint(UITheme.createPrimaryGradient(0, 0, 36, 36));
                g2.fill(new RoundRectangle2D.Float(0, 0, 36, 36, 10, 10));
                Icon cap = UITheme.createSwingIcon(UITheme.IconType.CAP, Color.WHITE, 20);
                cap.paintIcon(this, g2, 8, 8);
                g2.dispose();
            }
        };
        crest.setPreferredSize(new Dimension(36, 36));

        JPanel titleStack = new JPanel(new GridLayout(2, 1, 0, 2));
        titleStack.setOpaque(false);
        JLabel examHeading = new JLabel(examTitle);
        examHeading.setFont(UITheme.font(Font.BOLD, 17.5f));
        examHeading.setForeground(UITheme.TEXT_PRIMARY);
        JLabel examSub = new JLabel("Candidate: " + ExamStore.getCurrentDisplayName()
                + " (" + ExamStore.getCurrentUserId() + ")"
                + "  \u2022  Shortcuts: A-D select  \u2022  \u2190 / \u2192 navigate  \u2022  F flag");
        examSub.setFont(UITheme.font(Font.PLAIN, 12f));
        examSub.setForeground(UITheme.TEXT_SECONDARY);
        titleStack.add(examHeading);
        titleStack.add(examSub);

        titleLeft.add(crest);
        titleLeft.add(titleStack);
        topBar.add(titleLeft, BorderLayout.WEST);

        // Right: Proctor pill + Question counter + Circular Draining Timer
        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightStatus.setOpaque(false);

        proctorLabel = UITheme.createBadge(
                "Focus Alerts: 0/" + MAX_FOCUS_VIOLATIONS,
                UITheme.SUCCESS_SOFT,
                UITheme.SUCCESS_TEXT
        );
        proctorLabel.setFont(UITheme.font(Font.BOLD, 12f));
        proctorLabel.setBorder(new EmptyBorder(6, 12, 6, 12));

        proctorWarnLabel = UITheme.createBadge(
                "Warnings: 0",
                UITheme.WARNING_SOFT,
                UITheme.WARNING_TEXT
        );
        proctorWarnLabel.setFont(UITheme.font(Font.BOLD, 12f));
        proctorWarnLabel.setBorder(new EmptyBorder(6, 12, 6, 12));
        proctorWarnLabel.setVisible(false);

        progressLabel = UITheme.createBadge(
                "Question 1 of " + questions.size(),
                UITheme.PRIMARY_SOFT,
                UITheme.PRIMARY
        );
        progressLabel.setFont(UITheme.font(Font.BOLD, 12.5f));
        progressLabel.setBorder(new EmptyBorder(6, 12, 6, 12));

        timerWidget = new CircularTimerWidget(totalTimeSeconds);

        // Task 12: Webcam proctoring indicator & background snapshot session
        if (examConfig != null && examConfig.requireWebcam) {
            webcamIndicatorBadge = UITheme.createBadge(
                    "\u25CF Camera Active",
                    UITheme.SUCCESS_SOFT,
                    UITheme.SUCCESS_TEXT
            );
            webcamIndicatorBadge.setFont(UITheme.font(Font.BOLD, 12f));
            webcamIndicatorBadge.setBorder(new EmptyBorder(6, 12, 6, 12));
            webcamIndicatorBadge.setToolTipText("Webcam proctoring is active: periodic snapshots are recorded.");
            rightStatus.add(webcamIndicatorBadge);

            webcamSession = new WebcamProctorService.ProctoringSession(
                    examSessionId,
                    ExamStore.getCurrentUserId(),
                    examConfig.id,
                    status -> {
                        if (webcamIndicatorBadge != null) {
                            if ("Camera Active".equalsIgnoreCase(status)) {
                                webcamIndicatorBadge.setText("\u25CF Camera Active");
                                webcamIndicatorBadge.setBackground(UITheme.SUCCESS_SOFT);
                                webcamIndicatorBadge.setForeground(UITheme.SUCCESS_TEXT);
                            } else {
                                webcamIndicatorBadge.setText("\u25B2 Camera Lost");
                                webcamIndicatorBadge.setBackground(UITheme.WARNING_SOFT);
                                webcamIndicatorBadge.setForeground(UITheme.WARNING_TEXT);
                            }
                            webcamIndicatorBadge.revalidate();
                            webcamIndicatorBadge.repaint();
                        }
                    }
            );
            webcamSession.start();
        }

        rightStatus.add(proctorWarnLabel);
        rightStatus.add(proctorLabel);
        rightStatus.add(progressLabel);
        rightStatus.add(timerWidget);
        topBar.add(rightStatus, BorderLayout.EAST);

        // Segmented Progress Bar (each segment fills when answered)
        segmentedProgress = new SegmentedProgressBar(questions.size());
        JPanel segWrap = new JPanel(new BorderLayout());
        segWrap.setOpaque(false);
        segWrap.setBorder(new EmptyBorder(10, 32, 0, 32));
        segWrap.add(segmentedProgress, BorderLayout.CENTER);

        JPanel topWrap = new JPanel(new BorderLayout());
        topWrap.setOpaque(false);
        topWrap.add(topBar, BorderLayout.NORTH);

        JPanel centerBanners = new JPanel();
        centerBanners.setLayout(new BoxLayout(centerBanners, BoxLayout.Y_AXIS));
        centerBanners.setOpaque(false);

        // Task 8: Calm dismissible monitoring banner
        JPanel proctorBannerWrap = new JPanel(new BorderLayout());
        proctorBannerWrap.setOpaque(false);
        proctorBannerWrap.setBorder(new EmptyBorder(6, 32, 0, 32));

        UITheme.RoundedPanel proctorBanner = new UITheme.RoundedPanel(
                10, new Color(239, 246, 255), new Color(191, 219, 254));
        proctorBanner.setLayout(new BorderLayout(10, 0));
        proctorBanner.setBorder(new EmptyBorder(6, 14, 6, 10));

        JPanel bannerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bannerLeft.setOpaque(false);
        bannerLeft.add(new UITheme.VectorIcon(UITheme.IconType.CHECKLIST, UITheme.PRIMARY, Color.WHITE, 18));
        JLabel monitorLbl = new JLabel("This exam is monitored. Leaving the window or copying is recorded.");
        monitorLbl.setFont(UITheme.font(Font.PLAIN, 12.5f));
        monitorLbl.setForeground(new Color(30, 58, 138));
        bannerLeft.add(monitorLbl);

        JButton dismissBannerBtn = new JButton("\u2715");
        dismissBannerBtn.setFont(UITheme.font(Font.BOLD, 12f));
        dismissBannerBtn.setForeground(new Color(100, 116, 139));
        dismissBannerBtn.setContentAreaFilled(false);
        dismissBannerBtn.setBorderPainted(false);
        dismissBannerBtn.setFocusPainted(false);
        dismissBannerBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        dismissBannerBtn.setToolTipText("Dismiss notice");
        dismissBannerBtn.addActionListener(e -> {
            proctorBannerWrap.setVisible(false);
            topWrap.revalidate();
            topWrap.repaint();
        });

        proctorBanner.add(bannerLeft, BorderLayout.CENTER);
        proctorBanner.add(dismissBannerBtn, BorderLayout.EAST);
        proctorBannerWrap.add(proctorBanner, BorderLayout.CENTER);
        centerBanners.add(proctorBannerWrap);

        if (endAtCapNotice != null) {
            JPanel noticeWrap = new JPanel(new BorderLayout());
            noticeWrap.setOpaque(false);
            noticeWrap.setBorder(new EmptyBorder(6, 32, 0, 32));

            UITheme.RoundedPanel noticeBanner = new UITheme.RoundedPanel(
                    10, UITheme.WARNING_SOFT, Anim.withAlpha(UITheme.WARNING, 150));
            noticeBanner.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 6));
            noticeBanner.add(new UITheme.VectorIcon(UITheme.IconType.CLOCK, UITheme.WARNING_TEXT, Color.WHITE, 22));
            JLabel capLbl = new JLabel(endAtCapNotice);
            capLbl.setFont(UITheme.font(Font.BOLD, 13f));
            capLbl.setForeground(UITheme.WARNING_TEXT);
            noticeBanner.add(capLbl);

            noticeWrap.add(noticeBanner, BorderLayout.CENTER);
            centerBanners.add(noticeWrap);
        }

        topWrap.add(centerBanners, BorderLayout.CENTER);
        topWrap.add(segWrap, BorderLayout.SOUTH);

        root.add(topWrap, BorderLayout.NORTH);

        // ================= FOCUS WORKSPACE: CENTERED QUESTION GLASS PANEL + RIGHT NAVIGATOR =================
        JPanel workspace = new JPanel(new BorderLayout(24, 0));
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(18, 36, 18, 36));

        // Large centered white GlassPanel for the single active question
        GlassPanel examCard = new GlassPanel(18);
        examCard.setShadowColor(UITheme.PRIMARY);
        examCard.setSpotlightEnabled(false); // Calm focus surface while reading
        examCard.setLayout(new BorderLayout());
        examCard.setBorder(new EmptyBorder(28, 36, 28, 36));

        questionSlideContainer = new QuestionSlideContainer(new BorderLayout(0, 22));

        // Top row of question card: Q# badge + Flag wiggle button
        JPanel cardTopRow = new JPanel(new BorderLayout(12, 0));
        cardTopRow.setOpaque(false);

        questionNumberBadge = UITheme.createBadge("QUESTION 01", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
        questionNumberBadge.setFont(UITheme.font(Font.BOLD, 11.5f));
        questionNumberBadge.setBorder(new EmptyBorder(5, 12, 5, 12));

        difficultyBadge = UITheme.createDifficultyBadge(ExamStore.DIFFICULTY_MEDIUM);
        difficultyBadge.setFont(UITheme.font(Font.BOLD, 11f));
        difficultyBadge.setBorder(new EmptyBorder(5, 10, 5, 10));

        typeHintBadge = UITheme.createBadge("Select all that apply", UITheme.VIOLET_SOFT, UITheme.VIOLET);
        typeHintBadge.setFont(UITheme.font(Font.BOLD, 11f));
        typeHintBadge.setBorder(new EmptyBorder(5, 10, 5, 10));
        typeHintBadge.setVisible(false);

        JPanel badgeLeftWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        badgeLeftWrap.setOpaque(false);
        badgeLeftWrap.add(questionNumberBadge);
        badgeLeftWrap.add(difficultyBadge);
        badgeLeftWrap.add(typeHintBadge);

        flagBtn = new WiggleFlagButton();
        flagBtn.addActionListener(e -> toggleFlagCurrentQuestion());

        JPanel flagRightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        flagRightWrap.setOpaque(false);
        flagRightWrap.add(flagBtn);

        cardTopRow.add(badgeLeftWrap, BorderLayout.WEST);
        cardTopRow.add(flagRightWrap, BorderLayout.EAST);

        // Optional scaled question image (fits within 560x280, click-to-zoom lightbox)
        imageBanner = new QuestionImageBanner(560, 280, this::openImageLightbox);
        imageBanner.setVisible(false);

        // Big, highly readable question text
        questionLabel = new JLabel();
        questionLabel.setFont(UITheme.font(Font.BOLD, 21f));
        questionLabel.setForeground(UITheme.TEXT_PRIMARY);
        questionLabel.setBorder(new EmptyBorder(4, 2, 10, 2));

        JPanel headerCenterStack = new JPanel();
        headerCenterStack.setLayout(new BoxLayout(headerCenterStack, BoxLayout.Y_AXIS));
        headerCenterStack.setOpaque(false);
        imageBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerCenterStack.add(imageBanner);
        headerCenterStack.add(questionLabel);

        JPanel questionHeaderBlock = new JPanel(new BorderLayout(0, 10));
        questionHeaderBlock.setOpaque(false);
        questionHeaderBlock.add(cardTopRow, BorderLayout.NORTH);
        questionHeaderBlock.add(headerCenterStack, BorderLayout.CENTER);

        // Card 1: 4 Selectable Option Cards (SINGLE radio-style OR MULTI checkbox-style)
        JPanel optionsPanel = new JPanel(new GridLayout(4, 1, 0, 12));
        optionsPanel.setOpaque(false);

        optionCards = new OptionCard[4];
        for (int i = 0; i < 4; i++) {
            final int optionIdx = i;
            OptionCard card = new OptionCard(OPTION_LETTERS[i]);
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!modalDialogOpen) {
                        selectOption(optionIdx);
                    }
                }
            });
            optionCards[i] = card;
            optionsPanel.add(card);
        }

        // Card 2: TRUE_FALSE — two large prominent cards
        JPanel tfOuter = new JPanel(new GridBagLayout());
        tfOuter.setOpaque(false);
        JPanel tfGrid = new JPanel(new GridLayout(1, 2, 22, 0));
        tfGrid.setOpaque(false);
        tfGrid.setPreferredSize(new Dimension(620, 128));
        tfCards = new OptionCard[2];
        String[] tfLetters = {"T", "F"};
        String[] tfLabels = {"True", "False"};
        for (int i = 0; i < 2; i++) {
            final int optIdx = i;
            OptionCard tfCard = new OptionCard(tfLetters[i]);
            tfCard.setOptionText(tfLabels[i]);
            tfCard.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!modalDialogOpen) {
                        selectOption(optIdx);
                    }
                }
            });
            tfCards[i] = tfCard;
            tfGrid.add(tfCard);
        }
        GridBagConstraints tfc = new GridBagConstraints();
        tfc.gridx = 0;
        tfc.gridy = 0;
        tfc.weightx = 1.0;
        tfc.fill = GridBagConstraints.HORIZONTAL;
        tfOuter.add(tfGrid, tfc);

        // Card 3: FILL_BLANK — rounded text field with placeholder and paste disabled
        JPanel fillOuter = new JPanel(new GridBagLayout());
        fillOuter.setOpaque(false);
        JPanel fillStack = new JPanel();
        fillStack.setLayout(new BoxLayout(fillStack, BoxLayout.Y_AXIS));
        fillStack.setOpaque(false);

        JLabel fillPrompt = new JLabel("Enter your answer below (copy-paste is disabled):");
        fillPrompt.setFont(UITheme.font(Font.BOLD, 13.5f));
        fillPrompt.setForeground(UITheme.TEXT_SECONDARY);
        fillPrompt.setAlignmentX(Component.LEFT_ALIGNMENT);

        fillBlankField = new NoPasteFillBlankField("Type your answer here...");
        fillBlankField.setAlignmentX(Component.LEFT_ALIGNMENT);
        fillBlankField.setPreferredSize(new Dimension(560, 54));
        fillBlankField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        fillBlankField.getDocument().addDocumentListener(new DocumentListener() {
            private void syncText() {
                if (updatingFillBlank || submitted.get()) return;
                saveAnswer();
                refreshPaletteStyles();
            }
            @Override public void insertUpdate(DocumentEvent e) { syncText(); }
            @Override public void removeUpdate(DocumentEvent e) { syncText(); }
            @Override public void changedUpdate(DocumentEvent e) { syncText(); }
        });

        JLabel fillHint = new JLabel("Case-insensitive matching; extra leading/trailing spaces are ignored.");
        fillHint.setFont(UITheme.font(Font.PLAIN, 12f));
        fillHint.setForeground(UITheme.TEXT_MUTED);
        fillHint.setBorder(new EmptyBorder(8, 2, 0, 0));
        fillHint.setAlignmentX(Component.LEFT_ALIGNMENT);

        fillStack.add(fillPrompt);
        fillStack.add(Box.createVerticalStrut(10));
        fillStack.add(fillBlankField);
        fillStack.add(fillHint);

        GridBagConstraints fbc = new GridBagConstraints();
        fbc.gridx = 0;
        fbc.gridy = 0;
        fbc.weightx = 1.0;
        fbc.fill = GridBagConstraints.HORIZONTAL;
        fillOuter.add(fillStack, fbc);

        answerCardLayout = new CardLayout();
        answerCardsPanel = new JPanel(answerCardLayout);
        answerCardsPanel.setOpaque(false);
        answerCardsPanel.add(optionsPanel, "OPTIONS");
        answerCardsPanel.add(tfOuter, "TRUE_FALSE");
        answerCardsPanel.add(fillOuter, "FILL_BLANK");

        questionSlideContainer.add(questionHeaderBlock, BorderLayout.NORTH);
        questionSlideContainer.add(answerCardsPanel, BorderLayout.CENTER);
        examCard.add(questionSlideContainer, BorderLayout.CENTER);

        // Center the focus card cleanly with comfortable max width
        JPanel focusCenterWrap = new JPanel(new GridBagLayout());
        focusCenterWrap.setOpaque(false);
        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 0;
        fc.gridy = 0;
        fc.weightx = 1.0;
        fc.weighty = 1.0;
        fc.fill = GridBagConstraints.BOTH;
        fc.insets = new Insets(4, 14, 4, 14);
        focusCenterWrap.add(examCard, fc);

        // Right: Light Glass Question Navigator Sidebar
        GlassPanel sidebar = new GlassPanel(18);
        sidebar.setShadowColor(UITheme.PRIMARY);
        sidebar.setSpotlightEnabled(false);
        sidebar.setLayout(new BorderLayout(0, 16));
        sidebar.setBorder(new EmptyBorder(20, 18, 20, 18));
        sidebar.setPreferredSize(new Dimension(286, 520));

        JPanel sideHeader = new JPanel();
        sideHeader.setLayout(new BoxLayout(sideHeader, BoxLayout.Y_AXIS));
        sideHeader.setOpaque(false);

        JLabel sideTitle = new JLabel("Question Navigator");
        sideTitle.setFont(UITheme.font(Font.BOLD, 15.5f));
        sideTitle.setForeground(UITheme.TEXT_PRIMARY);
        sideTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sideSub = new JLabel("Click any number to jump directly");
        sideSub.setFont(UITheme.font(Font.PLAIN, 11.5f));
        sideSub.setForeground(UITheme.TEXT_MUTED);
        sideSub.setBorder(new EmptyBorder(2, 0, 14, 0));
        sideSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 3 Mini Live Stat Pills
        JPanel miniStats = new JPanel(new GridLayout(1, 3, 8, 0));
        miniStats.setOpaque(false);
        miniStats.setAlignmentX(Component.LEFT_ALIGNMENT);

        statAnsweredVal = new JLabel("0", SwingConstants.CENTER);
        statFlaggedVal = new JLabel("0", SwingConstants.CENTER);
        statRemainingVal = new JLabel(String.valueOf(questions.size()), SwingConstants.CENTER);

        miniStats.add(createMiniStat("DONE", statAnsweredVal, UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
        miniStats.add(createMiniStat("FLAG", statFlaggedVal, UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
        miniStats.add(createMiniStat("LEFT", statRemainingVal, new Color(241, 245, 249), UITheme.TEXT_SECONDARY));

        sideHeader.add(sideTitle);
        sideHeader.add(sideSub);
        sideHeader.add(miniStats);

        // 4-Column Palette Grid with gentle pulsing current button
        JPanel paletteGrid = new JPanel(new GridLayout(0, 4, 10, 10));
        paletteGrid.setOpaque(false);

        paletteButtons = new NavPaletteButton[questions.size()];
        for (int i = 0; i < questions.size(); i++) {
            final int qIdx = i;
            NavPaletteButton pBtn = new NavPaletteButton(String.valueOf(i + 1));
            pBtn.addActionListener(e -> {
                if (!modalDialogOpen) {
                    jumpToQuestion(qIdx);
                }
            });
            paletteButtons[i] = pBtn;
            paletteGrid.add(pBtn);
        }

        JPanel paletteWrap = new JPanel(new BorderLayout());
        paletteWrap.setOpaque(false);
        paletteWrap.add(paletteGrid, BorderLayout.NORTH);

        JScrollPane paletteScroll = new JScrollPane(
                paletteWrap,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        paletteScroll.setBorder(BorderFactory.createEmptyBorder());
        paletteScroll.setOpaque(false);
        paletteScroll.getViewport().setOpaque(false);
        UITheme.applyModernScrollBars(paletteScroll);

        // Legend footer
        JPanel legend = new JPanel(new GridLayout(2, 2, 8, 6));
        legend.setOpaque(false);
        legend.add(UITheme.createBadge("Current", UITheme.PRIMARY, Color.WHITE));
        legend.add(UITheme.createBadge("Answered", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
        legend.add(UITheme.createBadge("Flagged", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
        legend.add(UITheme.createBadge("Unanswered", new Color(241, 245, 249), UITheme.TEXT_SECONDARY));

        sidebar.add(sideHeader, BorderLayout.NORTH);
        sidebar.add(paletteScroll, BorderLayout.CENTER);
        sidebar.add(legend, BorderLayout.SOUTH);

        workspace.add(focusCenterWrap, BorderLayout.CENTER);
        workspace.add(sidebar, BorderLayout.EAST);

        root.add(workspace, BorderLayout.CENTER);

        // ================= BOTTOM FROSTED ACTION BAR =================
        JPanel bottom = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(255, 255, 255, 224));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.GLASS_SLATE_BORDER);
                g2.drawLine(0, 0, getWidth(), 0);
                g2.dispose();
            }
        };
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(14, 40, 14, 40));

        prevBtn = UITheme.secondaryButton("\u2190 Previous");
        prevBtn.setFont(UITheme.font(Font.BOLD, 14f));
        prevBtn.setPreferredSize(new Dimension(156, 44));
        prevBtn.addActionListener(e -> {
            if (!modalDialogOpen) previousQuestion();
        });

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightBtns.setOpaque(false);

        nextBtn = UITheme.gradientPrimaryButton("Next Question \u2192");
        nextBtn.setFont(UITheme.font(Font.BOLD, 14f));
        nextBtn.setPreferredSize(new Dimension(186, 44));
        nextBtn.addActionListener(e -> {
            if (!modalDialogOpen) nextQuestion();
        });

        submitBtn = UITheme.successButton("Submit Exam");
        submitBtn.setFont(UITheme.font(Font.BOLD, 14f));
        submitBtn.setPreferredSize(new Dimension(168, 44));
        submitBtn.addActionListener(e -> {
            if (!modalDialogOpen) handleManualSubmit();
        });

        rightBtns.add(nextBtn);
        rightBtns.add(submitBtn);

        bottom.add(prevBtn, BorderLayout.WEST);
        bottom.add(rightBtns, BorderLayout.EAST);

        root.add(bottom, BorderLayout.SOUTH);

        add(root);

        // Install soft red screen-edge glow overlay (activates automatically in last 5 minutes)
        edgeGlowOverlay = new EdgeDangerGlowOverlay();
        getLayeredPane().add(edgeGlowOverlay, JLayeredPane.PALETTE_LAYER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutOverlays();
            }
        });

        // Gentle pulse timer for the current navigator button
        palettePulseTimer = new Timer(33, e -> {
            if (!isShowing()) return;
            palettePulsePhase += 0.09f;
            if (palettePulsePhase > (float) (Math.PI * 2.0)) {
                palettePulsePhase -= (float) (Math.PI * 2.0);
            }
            if (paletteButtons != null && index >= 0 && index < paletteButtons.length) {
                paletteButtons[index].setPulsePhase(palettePulsePhase);
            }
        });
        palettePulseTimer.setCoalesce(true);
        palettePulseTimer.start();

        installKeyboardShortcuts();
        installFocusProctoring();
        installProctorInterceptors();

        startTimer();
        loadQuestionContent(false);

        setVisible(true);
        SwingUtilities.invokeLater(this::layoutOverlays);
    }

    private void layoutOverlays() {
        Dimension d = getLayeredPane().getSize();
        if (edgeGlowOverlay != null) {
            edgeGlowOverlay.setBounds(0, 0, d.width, d.height);
        }
        if (activeConfirmOverlay != null) {
            activeConfirmOverlay.setBounds(0, 0, d.width, d.height);
            activeConfirmOverlay.revalidate();
        }
    }

    private void installFocusProctoring() {
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                if (submitted.get() || modalDialogOpen || !isDisplayable()) {
                    return;
                }
                Window opposite = e.getOppositeWindow();
                if (opposite != null && opposite.getOwner() == ExamModule.this) {
                    return;
                }

                focusViolations++;
                proctorLabel.setText("Focus Alerts: " + focusViolations + "/" + MAX_FOCUS_VIOLATIONS);

                Integer exId = (examConfig != null && examConfig.id > 0) ? examConfig.id : null;
                ProctorDAO.logEvent(examSessionId, ExamStore.getCurrentUserId(), exId, null,
                        ProctorDAO.EVENT_FOCUS_LOST,
                        "Window lost focus (" + focusViolations + "/" + MAX_FOCUS_VIOLATIONS + ") on Q" + (index + 1));

                if (focusViolations >= MAX_FOCUS_VIOLATIONS) {
                    proctorLabel.setBackground(UITheme.DANGER);
                    proctorLabel.setForeground(Color.WHITE);
                    autoSubmittedByProctor = true;
                    ProctorDAO.logEvent(examSessionId, ExamStore.getCurrentUserId(), exId, null,
                            ProctorDAO.EVENT_AUTO_SUBMIT_PROCTOR,
                            "Auto-submitted after " + MAX_FOCUS_VIOLATIONS + " window-focus violations");
                    finishExam(true);
                } else {
                    proctorLabel.setBackground(UITheme.WARNING_SOFT);
                    proctorLabel.setForeground(UITheme.WARNING_TEXT);
                    UITheme.showToast(
                            ExamModule.this,
                            "Proctoring Warning (" + focusViolations + "/" + MAX_FOCUS_VIOLATIONS + ")",
                            "Do not switch away from the exam window. Auto-submit at " + MAX_FOCUS_VIOLATIONS + ".",
                            UITheme.ToastType.ERROR
                    );
                }
            }
        });

        addWindowStateListener(new WindowStateListener() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                if (submitted.get() || modalDialogOpen || !isDisplayable()) {
                    return;
                }
                boolean wasIconified = (e.getOldState() & Frame.ICONIFIED) != 0;
                boolean nowIconified = (e.getNewState() & Frame.ICONIFIED) != 0;
                if (!wasIconified && nowIconified) {
                    Integer exId = (examConfig != null && examConfig.id > 0) ? examConfig.id : null;
                    ProctorDAO.logEvent(examSessionId, ExamStore.getCurrentUserId(), exId, null,
                            ProctorDAO.EVENT_WINDOW_MINIMIZED, "Exam window minimized on Q" + (index + 1));
                }
            }
        });
    }

    private void installProctorInterceptors() {
        final long[] lastPrintScreenMs = {0L};
        final long[] lastRightClickMs = {0L};

        keyEventDispatcher = e -> {
            if (submitted.get() || !isDisplayable() || !isVisible()) {
                return false;
            }
            // Only intercept events originating inside THIS ExamModule window (or its owned dialogs)
            Component src = e.getComponent();
            Window srcWin = (src instanceof Window) ? (Window) src : (src != null ? SwingUtilities.getWindowAncestor(src) : null);
            if (srcWin != ExamModule.this && (srcWin == null || srcWin.getOwner() != ExamModule.this)) {
                return false;
            }

            int code = e.getKeyCode();

            // Intercept PrintScreen (Windows often delivers KEY_RELEASED only)
            if (code == KeyEvent.VK_PRINTSCREEN
                    && (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_RELEASED)) {
                long now = System.currentTimeMillis();
                if (now - lastPrintScreenMs[0] > 400L) {
                    lastPrintScreenMs[0] = now;
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null);
                    } catch (Exception ignored) {}
                    recordProctorWarning(
                            ProctorDAO.EVENT_PRINT_SCREEN_KEY,
                            "PrintScreen pressed on Q" + (index + 1) + "; clipboard cleared",
                            "Screenshots are disabled during the exam"
                    );
                }
                e.consume();
                return true;
            }

            if (e.getID() == KeyEvent.KEY_PRESSED) {
                boolean ctrl = e.isControlDown() || e.isMetaDown();
                boolean shift = e.isShiftDown();

                // Copy: Ctrl+C, Ctrl+Insert
                if ((ctrl && code == KeyEvent.VK_C) || (ctrl && code == KeyEvent.VK_INSERT)) {
                    recordProctorWarning(
                            ProctorDAO.EVENT_COPY_ATTEMPT,
                            "Blocked copy shortcut on Q" + (index + 1),
                            "Copying is disabled during the exam"
                    );
                    e.consume();
                    return true;
                }

                // Cut: Ctrl+X, Shift+Delete
                if ((ctrl && code == KeyEvent.VK_X) || (shift && code == KeyEvent.VK_DELETE)) {
                    recordProctorWarning(
                            ProctorDAO.EVENT_CUT_ATTEMPT,
                            "Blocked cut shortcut on Q" + (index + 1),
                            "Cutting is disabled during the exam"
                    );
                    e.consume();
                    return true;
                }

                // Paste: Ctrl+V, Shift+Insert
                if ((ctrl && code == KeyEvent.VK_V) || (shift && code == KeyEvent.VK_INSERT)) {
                    recordProctorWarning(
                            ProctorDAO.EVENT_PASTE_ATTEMPT,
                            "Blocked paste shortcut on Q" + (index + 1),
                            "Pasting is disabled during the exam"
                    );
                    e.consume();
                    return true;
                }
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);

        rightClickListener = event -> {
            if (submitted.get() || !isDisplayable() || !isVisible()) {
                return;
            }
            if (!(event instanceof MouseEvent)) {
                return;
            }
            MouseEvent me = (MouseEvent) event;
            if (!SwingUtilities.isRightMouseButton(me) && !me.isPopupTrigger()) {
                return;
            }
            Object srcObj = me.getSource();
            if (!(srcObj instanceof Component)) {
                return;
            }
            Component comp = (Component) srcObj;
            Window srcWin = (comp instanceof Window) ? (Window) comp : SwingUtilities.getWindowAncestor(comp);
            if (srcWin != ExamModule.this && (srcWin == null || srcWin.getOwner() != ExamModule.this)) {
                return;
            }
            me.consume();
            if (me.getID() == MouseEvent.MOUSE_PRESSED || me.isPopupTrigger()) {
                long now = System.currentTimeMillis();
                if (now - lastRightClickMs[0] > 350L) {
                    lastRightClickMs[0] = now;
                    recordProctorWarning(
                            ProctorDAO.EVENT_RIGHT_CLICK,
                            "Blocked right-click on Q" + (index + 1),
                            "Right-click is disabled during the exam"
                    );
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(rightClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void recordProctorWarning(String eventType, String details, String toastMessage) {
        proctorWarningCount++;
        if (proctorWarnLabel != null) {
            proctorWarnLabel.setText("Warnings: " + proctorWarningCount);
            proctorWarnLabel.setVisible(true);
        }
        Integer exId = (examConfig != null && examConfig.id > 0) ? examConfig.id : null;
        ProctorDAO.logEvent(examSessionId, ExamStore.getCurrentUserId(), exId, null, eventType, details);
        UITheme.showToast(ExamModule.this, "Exam Notice", toastMessage, UITheme.ToastType.WARNING);
    }

    private void cleanupProctorInterceptors() {
        if (webcamSession != null) {
            webcamSession.stop();
            webcamSession = null;
        }
        if (keyEventDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
            keyEventDispatcher = null;
        }
        if (rightClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(rightClickListener);
            rightClickListener = null;
        }
    }

    @Override
    public void dispose() {
        cleanupProctorInterceptors();
        if (timer != null) timer.stop();
        if (palettePulseTimer != null) palettePulseTimer.stop();
        if (slideTransitionTimer != null) slideTransitionTimer.stop();
        if (timerWidget != null) timerWidget.stopAnimations();
        super.dispose();
    }

    private JPanel createMiniStat(String tag, JLabel valLabel, Color bg, Color fg) {
        UITheme.RoundedPanel box = new UITheme.RoundedPanel(10, bg, UITheme.GLASS_SLATE_BORDER);
        box.setLayout(new BorderLayout(0, 2));
        box.setBorder(new EmptyBorder(8, 6, 8, 6));

        JLabel l = new JLabel(tag, SwingConstants.CENTER);
        l.setFont(UITheme.font(Font.BOLD, 10f));
        l.setForeground(fg);

        valLabel.setFont(UITheme.font(Font.BOLD, 16f));
        valLabel.setForeground(fg);

        box.add(l, BorderLayout.NORTH);
        box.add(valLabel, BorderLayout.CENTER);
        return box;
    }

    private boolean isTextInputFocused() {
        return fillBlankField != null && fillBlankField.isShowing() && fillBlankField.isFocusOwner();
    }

    private void installKeyboardShortcuts() {
        JRootPane rp = getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();

        int[] optionKeys = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4};
        int[] letterKeys = {KeyEvent.VK_A, KeyEvent.VK_B, KeyEvent.VK_C, KeyEvent.VK_D};
        for (int i = 0; i < 4; i++) {
            final int optIdx = i;
            String keyName = "selectOption" + i;
            im.put(KeyStroke.getKeyStroke(optionKeys[i], 0), keyName);
            im.put(KeyStroke.getKeyStroke(letterKeys[i], 0), keyName);
            am.put(keyName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!modalDialogOpen && !isTextInputFocused()) selectOption(optIdx);
                }
            });
        }

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevQ");
        am.put("prevQ", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!modalDialogOpen && !isTextInputFocused()) previousQuestion();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextQ");
        am.put("nextQ", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!modalDialogOpen && !isTextInputFocused()) nextQuestion();
            }
        });

        // F shortcut toggles flag with wiggle animation
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "toggleFlag");
        am.put("toggleFlag", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!modalDialogOpen && !isTextInputFocused()) toggleFlagCurrentQuestion();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeModal");
        am.put("closeModal", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (modalDialogOpen && activeConfirmOverlay != null) {
                    dismissSubmitModal();
                }
            }
        });
    }

    private void selectOption(int optionIdx) {
        if (submitted.get() || questions == null || index < 0 || index >= questions.size()) {
            return;
        }
        ExamStore.Question q = questions.get(index);
        String qType = ExamStore.normalizeQuestionType(q.type);

        if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            return;
        }

        if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
            if (optionIdx < 0 || optionIdx > 1) return;
            selectedOptionIndex = optionIdx;
            saveAnswer();
            for (int i = 0; i < 2; i++) {
                tfCards[i].setSelected(i == optionIdx, i == optionIdx);
            }
            refreshPaletteStyles();
            return;
        }

        if (ExamStore.TYPE_MULTI.equals(qType)) {
            if (optionIdx < 0 || optionIdx >= optionCards.length) return;
            Set<Integer> currentSet = ExamStore.studentMultiAnswers.computeIfAbsent(index, k -> new LinkedHashSet<>());
            boolean nowSelected;
            if (currentSet.contains(optionIdx)) {
                currentSet.remove(optionIdx);
                nowSelected = false;
            } else {
                currentSet.add(optionIdx);
                nowSelected = true;
            }
            if (currentSet.isEmpty()) {
                ExamStore.studentMultiAnswers.remove(index);
            }
            optionCards[optionIdx].setSelected(nowSelected, nowSelected);
            refreshPaletteStyles();
            return;
        }

        // Default: SINGLE
        if (optionIdx < 0 || optionIdx >= optionCards.length) {
            return;
        }
        selectedOptionIndex = optionIdx;
        saveAnswer();
        for (int i = 0; i < optionCards.length; i++) {
            optionCards[i].setSelected(i == optionIdx, i == optionIdx);
        }
        refreshPaletteStyles();
    }

    private void toggleFlagCurrentQuestion() {
        if (submitted.get()) {
            return;
        }
        if (flaggedQuestions.contains(index)) {
            flaggedQuestions.remove(index);
            UITheme.showToast(this, "Flag Removed", "Question " + (index + 1) + " unmarked.", UITheme.ToastType.INFO);
        } else {
            flaggedQuestions.add(index);
            UITheme.showToast(this, "Flagged for Review", "Question " + (index + 1) + " pinned in navigator.", UITheme.ToastType.WARNING);
        }
        refreshFlagButton();
        flagBtn.wiggle();
        refreshPaletteStyles();
    }

    private void refreshFlagButton() {
        boolean isFlagged = flaggedQuestions.contains(index);
        flagBtn.setFlagged(isFlagged);
    }

    private Set<Integer> getAnsweredIndices() {
        Set<Integer> answeredSet = new HashSet<>();
        if (questions == null) return answeredSet;
        for (int i = 0; i < questions.size(); i++) {
            if (AnswerEvaluator.isSessionAnswered(questions.get(i), i)) {
                answeredSet.add(i);
            }
        }
        return answeredSet;
    }

    private void refreshPaletteStyles() {
        if (paletteButtons == null) {
            return;
        }
        Set<Integer> answeredSet = getAnsweredIndices();
        int answered = answeredSet.size();
        statAnsweredVal.setText(String.valueOf(answered));
        statFlaggedVal.setText(String.valueOf(flaggedQuestions.size()));
        statRemainingVal.setText(String.valueOf(Math.max(0, questions.size() - answered)));

        for (int i = 0; i < paletteButtons.length; i++) {
            NavPaletteButton.State state;
            if (i == index) {
                state = NavPaletteButton.State.CURRENT;
            } else if (flaggedQuestions.contains(i)) {
                state = NavPaletteButton.State.FLAGGED;
            } else if (answeredSet.contains(i)) {
                state = NavPaletteButton.State.ANSWERED;
            } else {
                state = NavPaletteButton.State.UNANSWERED;
            }
            paletteButtons[i].setState(state);
        }
        if (segmentedProgress != null) {
            segmentedProgress.updateStates(index, answeredSet, flaggedQuestions);
        }
    }

    private void loadQuestionContent(boolean animateCheckmark) {
        if (submitted.get()) {
            return;
        }
        if (index < 0) {
            index = 0;
        }
        if (index >= questions.size()) {
            finishExam(false);
            return;
        }

        ExamStore.Question q = questions.get(index);
        String qType = ExamStore.normalizeQuestionType(q.type);

        questionNumberBadge.setText(String.format("QUESTION %02d OF %02d", index + 1, questions.size()));
        String diff = ExamStore.normalizeDifficulty(q.difficulty);
        difficultyBadge.setText(diff);
        if (ExamStore.DIFFICULTY_EASY.equals(diff)) {
            difficultyBadge.setBackground(UITheme.SUCCESS_SOFT);
            difficultyBadge.setForeground(UITheme.SUCCESS_TEXT);
        } else if (ExamStore.DIFFICULTY_HARD.equals(diff)) {
            difficultyBadge.setBackground(UITheme.DANGER_SOFT);
            difficultyBadge.setForeground(UITheme.DANGER_TEXT);
        } else {
            difficultyBadge.setBackground(UITheme.WARNING_SOFT);
            difficultyBadge.setForeground(UITheme.WARNING_TEXT);
        }
        difficultyBadge.repaint();

        if (ExamStore.TYPE_MULTI.equals(qType)) {
            typeHintBadge.setText("Select all that apply");
            typeHintBadge.setBackground(UITheme.VIOLET_SOFT);
            typeHintBadge.setForeground(UITheme.VIOLET);
            typeHintBadge.setVisible(true);
        } else if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
            typeHintBadge.setText("True / False");
            typeHintBadge.setBackground(UITheme.SKY_SOFT);
            typeHintBadge.setForeground(UITheme.SKY);
            typeHintBadge.setVisible(true);
        } else if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            typeHintBadge.setText("Fill in the Blank");
            typeHintBadge.setBackground(UITheme.PINK_SOFT);
            typeHintBadge.setForeground(UITheme.PINK);
            typeHintBadge.setVisible(true);
        } else {
            typeHintBadge.setVisible(false);
        }

        progressLabel.setText("Question " + (index + 1) + " of " + questions.size());
        questionLabel.setText("<html><div style='line-height:1.38;'>" + (q.text != null ? q.text : "") + "</div></html>");

        // Lazy image load above question text
        loadQuestionImageAsync(q);

        if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
            Integer savedSelection = ExamStore.studentAnswers.get(index);
            selectedOptionIndex = (savedSelection != null && (savedSelection == 0 || savedSelection == 1)) ? savedSelection : -1;
            tfCards[0].setOptionText("True");
            tfCards[1].setOptionText("False");
            for (int i = 0; i < 2; i++) {
                tfCards[i].setMultiMode(false);
                tfCards[i].setSelected(i == selectedOptionIndex, animateCheckmark && i == selectedOptionIndex);
            }
            answerCardLayout.show(answerCardsPanel, "TRUE_FALSE");
        } else if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            selectedOptionIndex = -1;
            String savedTxt = ExamStore.studentTextAnswers.get(index);
            updatingFillBlank = true;
            fillBlankField.setText(savedTxt != null ? savedTxt : "");
            updatingFillBlank = false;
            answerCardLayout.show(answerCardsPanel, "FILL_BLANK");
            SwingUtilities.invokeLater(() -> fillBlankField.requestFocusInWindow());
        } else if (ExamStore.TYPE_MULTI.equals(qType)) {
            selectedOptionIndex = -1;
            Set<Integer> savedMulti = ExamStore.studentMultiAnswers.get(index);
            for (int i = 0; i < 4; i++) {
                String optText = (q.options != null && i < q.options.length && q.options[i] != null)
                        ? q.options[i] : "";
                boolean sel = savedMulti != null && savedMulti.contains(i);
                optionCards[i].setMultiMode(true);
                optionCards[i].setOptionText(optText);
                optionCards[i].setSelected(sel, animateCheckmark && sel);
            }
            answerCardLayout.show(answerCardsPanel, "OPTIONS");
        } else {
            Integer savedSelection = ExamStore.studentAnswers.get(index);
            selectedOptionIndex = (savedSelection != null && savedSelection >= 0 && savedSelection < 4) ? savedSelection : -1;
            for (int i = 0; i < 4; i++) {
                String optText = (q.options != null && i < q.options.length && q.options[i] != null)
                        ? q.options[i] : "";
                optionCards[i].setMultiMode(false);
                optionCards[i].setOptionText(optText);
                optionCards[i].setSelected(i == selectedOptionIndex, animateCheckmark && i == selectedOptionIndex);
            }
            answerCardLayout.show(answerCardsPanel, "OPTIONS");
        }

        prevBtn.setEnabled(index > 0);
        nextBtn.setVisible(index < questions.size() - 1);

        refreshFlagButton();
        refreshPaletteStyles();
    }

    private void loadQuestionImageAsync(ExamStore.Question q) {
        final int token = ++imageRequestToken;
        if (q == null || !q.hasImage) {
            imageBanner.clearImage();
            imageBanner.setVisible(false);
            return;
        }
        if (q.imageBytes != null && q.imageBytes.length > 0) {
            imageBanner.setImageBytes(q.imageBytes);
            imageBanner.setVisible(true);
            return;
        }
        imageBanner.setLoading();
        imageBanner.setVisible(true);
        final int qId = q.id;
        new SwingWorker<byte[], Void>() {
            @Override
            protected byte[] doInBackground() {
                return QuestionDAO.getQuestionImage(qId);
            }

            @Override
            protected void done() {
                if (!isDisplayable() || token != imageRequestToken) return;
                try {
                    byte[] bytes = get();
                    if (bytes != null && bytes.length > 0) {
                        q.setImageBytes(bytes);
                        imageBanner.setImageBytes(bytes);
                        imageBanner.setVisible(true);
                    } else {
                        imageBanner.clearImage();
                        imageBanner.setVisible(false);
                    }
                    questionSlideContainer.revalidate();
                    questionSlideContainer.repaint();
                } catch (Exception ignored) {
                    imageBanner.clearImage();
                    imageBanner.setVisible(false);
                }
            }
        }.execute();
    }

    private void openImageLightbox(BufferedImage fullImage) {
        if (fullImage == null || submitted.get()) return;
        boolean prevModal = modalDialogOpen;
        modalDialogOpen = true;
        try {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int maxW = Math.min(920, screen.width - 180);
            int maxH = Math.min(620, screen.height - 200);
            double scale = Math.min(1.0, Math.min(maxW / (double) fullImage.getWidth(), maxH / (double) fullImage.getHeight()));
            int dispW = Math.max(120, (int) Math.round(fullImage.getWidth() * scale));
            int dispH = Math.max(90, (int) Math.round(fullImage.getHeight() * scale));

            JComponent imgView = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.configureGraphics(g2);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int x = (getWidth() - dispW) / 2;
                    int y = (getHeight() - dispH) / 2;
                    g2.drawImage(fullImage, x, y, dispW, dispH, null);
                    g2.dispose();
                }
            };
            imgView.setPreferredSize(new Dimension(dispW + 16, dispH + 16));
            GlassDialog.showCustom(
                    this,
                    "Question Diagram Preview",
                    "Click Close or press Esc to return to your exam",
                    imgView,
                    "Close",
                    Math.max(460, dispW + 84),
                    Math.max(320, dispH + 165)
            );
        } finally {
            modalDialogOpen = prevModal;
        }
    }

    /**
     * Smooth directional question slide + fade transition (250ms easeOutCubic).
     * forward = true: old slides out left, new slides in from right.
     * forward = false: old slides out right, new slides in from left.
     */
    private void transitionToQuestion(int targetIndex, boolean forward) {
        if (submitted.get() || targetIndex < 0 || targetIndex >= questions.size() || targetIndex == index) {
            return;
        }
        saveAnswer();
        index = targetIndex;

        if (slideTransitionTimer != null && slideTransitionTimer.isRunning()) {
            slideTransitionTimer.stop();
        }

        final int outSign = forward ? -1 : 1;
        final int inStart = forward ? 42 : -42;

        // Quick slide-out of previous question (110ms), swap content, then 250ms easeOutCubic slide-in
        slideTransitionTimer = Anim.animate(110, p -> {
            float e = Anim.easeOutCubic(p);
            questionSlideContainer.setSlideOffset(Math.round(outSign * 34f * e), 0);
            questionSlideContainer.setAlpha(1f - e);
        }, () -> {
            loadQuestionContent(false);
            questionSlideContainer.setSlideOffset(inStart, 0);
            questionSlideContainer.setAlpha(0f);
            slideTransitionTimer = Anim.animate(250, p2 -> {
                float e2 = Anim.easeOutCubic(p2);
                questionSlideContainer.setSlideOffset(Math.round(inStart * (1f - e2)), 0);
                questionSlideContainer.setAlpha(e2);
            }, () -> {
                questionSlideContainer.setSlideOffset(0, 0);
                questionSlideContainer.setAlpha(1f);
            });
        });
    }

    private void nextQuestion() {
        if (submitted.get()) {
            return;
        }
        saveAnswer();
        if (index < questions.size() - 1) {
            transitionToQuestion(index + 1, true);
        }
    }

    private void previousQuestion() {
        if (submitted.get() || index <= 0) {
            return;
        }
        saveAnswer();
        transitionToQuestion(index - 1, false);
    }

    private void jumpToQuestion(int targetIndex) {
        if (submitted.get() || targetIndex < 0 || targetIndex >= questions.size() || targetIndex == index) {
            return;
        }
        transitionToQuestion(targetIndex, targetIndex > index);
    }

    // ================= CUSTOM WHITE GLASS SUBMIT CONFIRMATION MODAL =================

    private void handleManualSubmit() {
        if (submitted.get() || modalDialogOpen) {
            return;
        }

        saveAnswer();

        int answeredCount = getAnsweredIndices().size();
        int totalCount = questions.size();
        int unansweredCount = Math.max(0, totalCount - answeredCount);
        int flaggedCount = flaggedQuestions.size();

        showGlassSubmitModal(answeredCount, unansweredCount, flaggedCount, totalCount);
    }

    private void showGlassSubmitModal(int answered, int unanswered, int flagged, int total) {
        modalDialogOpen = true;

        final float[] scrimAlpha = {0f};
        JPanel overlay = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                // Soft airy white/light-slate frosted veil (Light Theme Only, never black)
                g2.setColor(new Color(241, 245, 249, Math.round(175 * scrimAlpha[0])));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(148, 163, 184, Math.round(40 * scrimAlpha[0])));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        overlay.setOpaque(false);
        // Absorb clicks on backdrop so underlying controls cannot be clicked while modal is open
        overlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Consume click
            }
        });

        GlassPanel modalCard = new GlassPanel(18);
        modalCard.setFillColor(new Color(255, 255, 255, 246));
        modalCard.setShadowColor(UITheme.PRIMARY);
        modalCard.setSpotlightEnabled(false);
        modalCard.setLayout(new BorderLayout(0, 18));
        modalCard.setBorder(new EmptyBorder(26, 30, 24, 30));
        modalCard.setPreferredSize(new Dimension(470, 310));
        modalCard.setAlpha(0f);
        modalCard.setScale(0.90f);

        // Header
        JPanel headRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headRow.setOpaque(false);
        headRow.add(new UITheme.VectorIcon(UITheme.IconType.CHECKLIST, UITheme.PRIMARY, UITheme.PRIMARY_SOFT, 38));

        JPanel headText = new JPanel(new GridLayout(2, 1, 0, 2));
        headText.setOpaque(false);
        JLabel title = new JLabel("Submit Examination?");
        title.setFont(UITheme.font(Font.BOLD, 19f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Review your completion summary before final submission");
        sub.setFont(UITheme.font(Font.PLAIN, 12.5f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        headText.add(title);
        headText.add(sub);
        headRow.add(headText);

        // 3 Summary Stat Tiles: Answered / Unanswered / Flagged
        JPanel statsGrid = new JPanel(new GridLayout(1, 3, 12, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(createModalStatTile("ANSWERED", answered + " / " + total, UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
        statsGrid.add(createModalStatTile("UNANSWERED", String.valueOf(unanswered),
                unanswered > 0 ? UITheme.DANGER_SOFT : new Color(241, 245, 249),
                unanswered > 0 ? UITheme.DANGER_TEXT : UITheme.TEXT_SECONDARY));
        statsGrid.add(createModalStatTile("FLAGGED", String.valueOf(flagged),
                flagged > 0 ? UITheme.WARNING_SOFT : new Color(241, 245, 249),
                flagged > 0 ? UITheme.WARNING_TEXT : UITheme.TEXT_SECONDARY));

        JLabel note = new JLabel(unanswered > 0
                ? ("You still have " + unanswered + " unanswered question" + (unanswered == 1 ? "" : "s") + ". You can go back or submit now.")
                : "All questions answered! Ready to calculate your final score.");
        note.setFont(UITheme.font(Font.PLAIN, 12.5f));
        note.setForeground(unanswered > 0 ? UITheme.WARNING_TEXT : UITheme.SUCCESS_TEXT);

        JPanel centerBody = new JPanel(new BorderLayout(0, 14));
        centerBody.setOpaque(false);
        centerBody.add(statsGrid, BorderLayout.CENTER);
        centerBody.add(note, BorderLayout.SOUTH);

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);

        UITheme.ModernButton cancelBtn = UITheme.secondaryButton("Continue Exam");
        cancelBtn.setPreferredSize(new Dimension(148, 40));
        cancelBtn.addActionListener(e -> dismissSubmitModal());

        UITheme.ModernButton confirmBtn = UITheme.gradientPrimaryButton("Confirm & Submit");
        confirmBtn.setPreferredSize(new Dimension(168, 40));
        confirmBtn.addActionListener(e -> {
            dismissSubmitModalImmediate();
            finishExam(false);
        });

        btnRow.add(cancelBtn);
        btnRow.add(confirmBtn);

        modalCard.add(headRow, BorderLayout.NORTH);
        modalCard.add(centerBody, BorderLayout.CENTER);
        modalCard.add(btnRow, BorderLayout.SOUTH);

        overlay.add(modalCard);
        overlay.putClientProperty("modalCard", modalCard);
        overlay.putClientProperty("scrimAlpha", scrimAlpha);

        activeConfirmOverlay = overlay;
        Dimension d = getLayeredPane().getSize();
        overlay.setBounds(0, 0, d.width, d.height);
        getLayeredPane().add(overlay, JLayeredPane.MODAL_LAYER);
        getLayeredPane().moveToFront(overlay);
        overlay.revalidate();

        // Smooth fade + scale-in (easeOutBack)
        Anim.animate(240, p -> {
            scrimAlpha[0] = Anim.easeOutCubic(p);
            modalCard.setAlpha(Anim.easeOutCubic(p));
            modalCard.setScale(0.90f + 0.10f * Anim.easeOutBack(p));
            overlay.repaint();
        }, () -> {
            scrimAlpha[0] = 1f;
            modalCard.setAlpha(1f);
            modalCard.setScale(1f);
            overlay.repaint();
        });
    }

    private JPanel createModalStatTile(String label, String value, Color bg, Color fg) {
        UITheme.RoundedPanel tile = new UITheme.RoundedPanel(12, bg, UITheme.GLASS_SLATE_BORDER);
        tile.setLayout(new BorderLayout(0, 4));
        tile.setBorder(new EmptyBorder(12, 10, 12, 10));

        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(UITheme.font(Font.BOLD, 10f));
        l.setForeground(fg);

        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(UITheme.font(Font.BOLD, 20f));
        v.setForeground(fg);

        tile.add(l, BorderLayout.NORTH);
        tile.add(v, BorderLayout.CENTER);
        return tile;
    }

    private void dismissSubmitModal() {
        if (activeConfirmOverlay == null) {
            modalDialogOpen = false;
            return;
        }
        JPanel overlay = activeConfirmOverlay;
        GlassPanel modalCard = (GlassPanel) overlay.getClientProperty("modalCard");
        float[] scrimAlpha = (float[]) overlay.getClientProperty("scrimAlpha");
        activeConfirmOverlay = null;

        Anim.animate(170, p -> {
            float inv = 1f - Anim.easeOutCubic(p);
            if (scrimAlpha != null) scrimAlpha[0] = inv;
            if (modalCard != null) {
                modalCard.setAlpha(inv);
                modalCard.setScale(0.94f + 0.06f * inv);
            }
            overlay.repaint();
        }, () -> {
            getLayeredPane().remove(overlay);
            getLayeredPane().repaint();
            modalDialogOpen = false;
        });
    }

    private void dismissSubmitModalImmediate() {
        if (activeConfirmOverlay != null) {
            getLayeredPane().remove(activeConfirmOverlay);
            getLayeredPane().repaint();
            activeConfirmOverlay = null;
        }
        modalDialogOpen = false;
    }

    // ================= UNCHANGED ANSWER SAVING, TIMER, SCORING & TRANSACTION FLOW =================

    private void saveAnswer() {
        if (questions == null || index < 0 || index >= questions.size()) {
            return;
        }
        ExamStore.Question q = questions.get(index);
        String qType = ExamStore.normalizeQuestionType(q.type);
        if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            String txt = fillBlankField != null ? fillBlankField.getText() : "";
            if (txt == null || txt.trim().isEmpty()) {
                ExamStore.studentTextAnswers.remove(index);
            } else {
                ExamStore.studentTextAnswers.put(index, txt);
            }
        } else if (ExamStore.TYPE_MULTI.equals(qType)) {
            // Multi selections are kept live in ExamStore.studentMultiAnswers
        } else {
            if (selectedOptionIndex >= 0 && selectedOptionIndex < 4) {
                ExamStore.studentAnswers.put(index, selectedOptionIndex);
            }
        }
    }

    private void startTimer() {
        timer = new javax.swing.Timer(1000, e -> {
            if (submitted.get()) {
                timer.stop();
                return;
            }

            timeLeft--;
            if (timeLeft < 0) {
                timeLeft = 0;
            }

            boolean inFinalFive = timeLeft <= FIVE_MINUTE_WARNING_SECONDS && timeLeft > 0;
            timerWidget.updateTime(timeLeft, inFinalFive);
            edgeGlowOverlay.setActive(inFinalFive);

            if (inFinalFive && !fiveMinuteWarningShown && totalTimeSeconds > FIVE_MINUTE_WARNING_SECONDS) {
                fiveMinuteWarningShown = true;
                UITheme.showToast(
                        this,
                        "5 Minutes Remaining!",
                        "Please review flagged questions and submit before 00:00.",
                        UITheme.ToastType.WARNING
                );
            }

            if (timeLeft <= 0) {
                finishExam(true);
            }
        });
        timer.start();
    }

    private void clearExamSessionState() {
        ExamStore.setCurrentlyInExam(false);
        String uid = ExamStore.getCurrentUserId();
        String tok = ExamStore.getSessionToken();
        if (uid != null && !uid.isEmpty() && tok != null && !tok.isEmpty()) {
            new Thread(() -> {
                try {
                    SessionDAO.setInExam(uid, tok, null, false);
                } catch (Exception ignored) {}
            }, "clear-in-exam").start();
        }
    }

    private void finishExam(boolean autoSubmitted) {
        if (!submitted.compareAndSet(false, true)) {
            return;
        }

        if (timer != null) {
            timer.stop();
        }
        if (palettePulseTimer != null) {
            palettePulseTimer.stop();
        }
        if (timerWidget != null) {
            timerWidget.stopAnimations();
        }
        if (edgeGlowOverlay != null) {
            edgeGlowOverlay.setActive(false);
        }
        dismissSubmitModalImmediate();

        if (prevBtn != null) prevBtn.setEnabled(false);
        if (nextBtn != null) nextBtn.setEnabled(false);
        if (submitBtn != null) submitBtn.setEnabled(false);
        if (flagBtn != null) flagBtn.setEnabled(false);

        cleanupProctorInterceptors();
        clearExamSessionState();

        saveAnswer();

        int timeTakenSeconds = autoSubmitted
                ? totalTimeSeconds
                : Math.max(0, totalTimeSeconds - Math.max(0, timeLeft));
        String submissionType = autoSubmitted
                ? ExamStore.SUBMISSION_AUTOMATIC
                : ExamStore.SUBMISSION_MANUAL;

        ExamStore.score = 0;
        int wrongCount = 0;
        for (int i = 0; i < questions.size(); i++) {
            ExamStore.Question q = questions.get(i);
            if (AnswerEvaluator.isSessionCorrect(q, i)) {
                ExamStore.score++;
            } else if (AnswerEvaluator.isSessionWrong(q, i)) {
                wrongCount++;
            }
        }

        double marksPerQ = (examConfig != null && examConfig.marksPerQuestion > 0)
                ? examConfig.marksPerQuestion : 1.0;
        double negMark = (examConfig != null && examConfig.negativeMarking > 0)
                ? examConfig.negativeMarking : 0.0;
        double passMark = (examConfig != null) ? examConfig.passPercentage : AttemptDAO.PASS_PERCENTAGE;

        double negativeMarksLost = AttemptDAO.roundTwoDecimals(wrongCount * negMark);
        double marksObtained = AttemptDAO.roundTwoDecimals(ExamStore.score * marksPerQ - negativeMarksLost);
        double totalMarks = AttemptDAO.roundTwoDecimals(questions.size() * marksPerQ);
        double percentage = AttemptDAO.calculatePercentage(Math.max(0.0, marksObtained), totalMarks);
        boolean passed = "Pass".equalsIgnoreCase(AttemptDAO.calculatePassStatus(percentage, passMark));
        Integer examIdParam = (examConfig != null && examConfig.id > 0) ? examConfig.id : null;

        List<AttemptDAO.AttemptAnswerRecord> answerRecords =
                AttemptDAO.buildAttemptAnswerRecords(questions, ExamStore.studentAnswers);

        int savedAttemptId = AttemptDAO.saveAttemptWithAnswersReturningId(
                ExamStore.getCurrentUserId(),
                ExamStore.getSelectedSubject(),
                ExamStore.score,
                questions.size(),
                timeTakenSeconds,
                submissionType,
                answerRecords,
                examIdParam,
                marksObtained,
                totalMarks,
                passed
        );
        boolean saved = savedAttemptId > 0;

        if (saved) {
            ExamStore.setLastSavedAttemptId(savedAttemptId);
            ProctorDAO.linkSessionToAttempt(examSessionId, savedAttemptId);
            ProctorDAO.logEvent(
                    examSessionId,
                    ExamStore.getCurrentUserId(),
                    examIdParam,
                    savedAttemptId,
                    ProctorDAO.EVENT_EXAM_SUBMITTED,
                    "Exam submitted (" + submissionType + "). Score=" + ExamStore.score + "/" + questions.size()
                            + ", Marks=" + marksObtained + "/" + totalMarks + ", Result=" + (passed ? "PASS" : "FAIL")
            );

            // Task 10: evaluate achievements off-EDT and store in ExamStore.lastNewAchievements
            final int finalAttemptId = savedAttemptId;
            final String finalUserId = ExamStore.getCurrentUserId();
            java.util.concurrent.CompletableFuture<List<AchievementService.Achievement>> achFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            AchievementService.evaluateAfterAttempt(finalUserId, finalAttemptId));
            try {
                ExamStore.lastNewAchievements = achFuture.get(600, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                achFuture.thenAccept(list -> {
                    if (list != null) ExamStore.lastNewAchievements = list;
                });
            }
        }

        ExamStore.setLastExamSubmissionDetails(timeTakenSeconds, submissionType);
        ExamStore.setLastExamScoringDetails(marksObtained, totalMarks, wrongCount, negativeMarksLost, passed, examConfig);
        lastExamFlaggedQuestions = new HashSet<>(flaggedQuestions);
        ExamStore.markExamCompleted();

        if (saved) {
            ExamStore.recordAttempt(
                    ExamStore.getCurrentUserId(),
                    ExamStore.getSelectedSubject(),
                    ExamStore.score,
                    questions.size(),
                    timeTakenSeconds,
                    submissionType
            );
        }

        if (autoSubmitted) {
            modalDialogOpen = true;
            String autoMsg = autoSubmittedByProctor
                    ? "Your exam was submitted automatically after " + MAX_FOCUS_VIOLATIONS + " window-focus violations."
                    : "Time is up! Your exam has been submitted automatically.";
            String autoTitle = autoSubmittedByProctor ? "Proctoring Auto-Submit" : "Time Expired";
            JOptionPane.showMessageDialog(
                    this,
                    autoMsg,
                    autoTitle,
                    autoSubmittedByProctor ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE
            );
        }

        if (!saved) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to save exam attempt to database. Transaction was rolled back. Result is shown for current session only.",
                    "Database Transaction Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        dispose();
        new ResultUI();
    }

    // =========================================================================
    // CUSTOM EXAM VISUAL COMPONENTS (JAVA2D + 16ms TIMER ANIMATIONS)
    // =========================================================================

    /**
     * Slide + fade container for question transitions.
     */
    private static class QuestionSlideContainer extends JPanel implements Anim.Fadeable, Anim.Slideable {
        private float alpha = 1.0f;
        private int dx = 0;
        private int dy = 0;

        QuestionSlideContainer(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override public void setAlpha(float alpha) { this.alpha = Anim.clamp01(alpha); repaint(); }
        @Override public float getAlpha() { return alpha; }
        @Override public void setSlideOffset(int dx, int dy) { this.dx = dx; this.dy = dy; repaint(); }

        @Override
        public void paint(Graphics g) {
            if (alpha <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            if (dx != 0 || dy != 0) g2.translate(dx, dy);
            if (alpha < 0.999f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paint(g2);
            g2.dispose();
        }
    }

    /**
     * Selectable option card (replaces radio buttons):
     * - Hover: soft indigo tint
     * - Selected: Indigo->Violet gradient border + light indigo fill + self-drawing checkmark with bounce
     */
    private static class OptionCard extends JPanel {
        private final String letter;
        private final JLabel textLabel;
        private boolean selected = false;
        private boolean multiMode = false;
        private float hoverProgress = 0f;
        private float checkProgress = 0f;   // 0 -> 1 draws checkmark stroke
        private float badgeScale = 0f;      // 0 -> 1 with easeOutBack bounce
        private Timer hoverTimer;
        private Timer checkTimer;

        OptionCard(String letter) {
            this.letter = letter;
            setOpaque(false);
            setLayout(new BorderLayout(16, 0));
            setBorder(new EmptyBorder(14, 62, 14, 54));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            textLabel = new JLabel();
            textLabel.setFont(UITheme.font(Font.PLAIN, 16.5f));
            textLabel.setForeground(UITheme.TEXT_PRIMARY);
            add(textLabel, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { animateHover(1f); }
                @Override public void mouseExited(MouseEvent e) { animateHover(0f); }
            });
        }

        void setOptionText(String text) {
            textLabel.setText("<html>" + (text == null ? "" : text) + "</html>");
        }

        void setMultiMode(boolean multiMode) {
            if (this.multiMode != multiMode) {
                this.multiMode = multiMode;
                repaint();
            }
        }

        void setSelected(boolean selected, boolean animateBounce) {
            this.selected = selected;
            if (checkTimer != null && checkTimer.isRunning()) {
                checkTimer.stop();
            }
            if (selected) {
                textLabel.setFont(UITheme.font(Font.BOLD, 16.5f));
                textLabel.setForeground(UITheme.TEXT_PRIMARY);
                if (animateBounce) {
                    checkProgress = 0f;
                    badgeScale = 0.2f;
                    checkTimer = Anim.animate(320, p -> {
                        badgeScale = Anim.easeOutBack(Math.min(1f, p * 1.25f));
                        checkProgress = Anim.easeOutCubic(Math.max(0f, (p - 0.12f) / 0.88f));
                        repaint();
                    }, () -> {
                        badgeScale = 1f;
                        checkProgress = 1f;
                        repaint();
                    });
                } else {
                    badgeScale = 1f;
                    checkProgress = 1f;
                    repaint();
                }
            } else {
                textLabel.setFont(UITheme.font(Font.PLAIN, 16.5f));
                textLabel.setForeground(UITheme.TEXT_PRIMARY);
                badgeScale = 0f;
                checkProgress = 0f;
                repaint();
            }
        }

        private void animateHover(float target) {
            if (hoverTimer != null && hoverTimer.isRunning()) hoverTimer.stop();
            final float start = hoverProgress;
            hoverTimer = Anim.animate(140, p -> {
                hoverProgress = start + (target - start) * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                hoverProgress = target;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float arc = 14f;
            Shape cardShape = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, arc, arc);

            // Fill: crisp white -> subtle slate tint on hover -> light indigo fill when selected
            if (selected) {
                g2.setColor(UITheme.PRIMARY_SOFT);
            } else if (hoverProgress > 0.01f) {
                g2.setColor(new Color(248, 250, 252));
            } else {
                g2.setColor(Color.WHITE);
            }
            g2.fill(cardShape);

            // Border: Indigo -> Violet gradient when selected, subtle indigo on hover, hairline slate default
            if (selected) {
                g2.setPaint(UITheme.createPrimaryGradient(1f, 1f, w - 1f, h - 1f));
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new RoundRectangle2D.Float(1.2f, 1.2f, w - 2.4f, h - 2.4f, arc, arc));
            } else {
                Color bCol = hoverProgress > 0.01f
                        ? Anim.withAlpha(UITheme.PRIMARY, Math.round(32 + 90 * hoverProgress))
                        : UITheme.BORDER;
                g2.setColor(bCol);
                g2.setStroke(new BasicStroke(1.1f));
                g2.draw(cardShape);
            }

            // Left Letter Pill (A, B, C, D)
            int pillSize = 34;
            int px = 16;
            int py = (h - pillSize) / 2;
            Shape pill = new RoundRectangle2D.Float(px, py, pillSize, pillSize, 10, 10);
            if (selected) {
                g2.setPaint(UITheme.createPrimaryGradient(px, py, px + pillSize, py + pillSize));
                g2.fill(pill);
                g2.setColor(Color.WHITE);
            } else {
                g2.setColor(hoverProgress > 0.01f ? UITheme.PRIMARY_SOFT : new Color(241, 245, 249));
                g2.fill(pill);
                g2.setColor(hoverProgress > 0.01f ? UITheme.PRIMARY : UITheme.TEXT_SECONDARY);
            }
            g2.setFont(UITheme.font(Font.BOLD, 13.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, px + (pillSize - fm.stringWidth(letter)) / 2, py + (pillSize + fm.getAscent() - fm.getDescent()) / 2);

            // Right Checkbox (MULTI) or Self-Drawing Animated Checkmark Circle (SINGLE / TRUE_FALSE)
            float cx = w - 28f;
            float cy = h / 2f;
            if (multiMode && (!selected || badgeScale <= 0.01f)) {
                float boxR = 11f;
                Shape box = new RoundRectangle2D.Float(cx - boxR, cy - boxR, boxR * 2f, boxR * 2f, 6f, 6f);
                g2.setColor(Color.WHITE);
                g2.fill(box);
                g2.setColor(hoverProgress > 0.01f ? UITheme.PRIMARY : UITheme.BORDER_STRONG);
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(box);
            }
            if (selected && badgeScale > 0.01f) {
                float r = 12f * badgeScale;

                g2.setPaint(UITheme.createPrimaryGradient(cx - r, cy - r, cx + r, cy + r));
                if (multiMode) {
                    g2.fill(new RoundRectangle2D.Float(cx - r, cy - r, r * 2f, r * 2f, 6f, 6f));
                } else {
                    g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
                }

                if (checkProgress > 0.01f) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    // 2-segment progressive checkmark stroke
                    float x0 = cx - 5.0f, y0 = cy + 0.3f;
                    float x1 = cx - 1.3f, y1 = cy + 4.0f;
                    float x2 = cx + 5.5f, y2 = cy - 3.6f;

                    Path2D check = new Path2D.Float();
                    check.moveTo(x0, y0);
                    if (checkProgress <= 0.42f) {
                        float t = checkProgress / 0.42f;
                        check.lineTo(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t);
                    } else {
                        check.lineTo(x1, y1);
                        float t = (checkProgress - 0.42f) / 0.58f;
                        check.lineTo(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t);
                    }
                    g2.draw(check);
                }
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Rounded text field for FILL_BLANK questions with placeholder and paste completely disabled.
     */
    private static class NoPasteFillBlankField extends JTextField {
        private final String placeholder;

        NoPasteFillBlankField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setFont(UITheme.font(Font.PLAIN, 17f));
            setForeground(UITheme.TEXT_PRIMARY);
            setCaretColor(UITheme.PRIMARY);
            setBorder(new EmptyBorder(12, 18, 12, 18));

            // Disable context menu, drag/drop, and copy/cut/paste
            setComponentPopupMenu(null);
            setDragEnabled(false);
            setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return false;
                }
                @Override
                public boolean canImport(JComponent comp, java.awt.datatransfer.DataFlavor[] transferFlavors) {
                    return false;
                }
                @Override
                public int getSourceActions(JComponent c) {
                    return NONE;
                }
                @Override
                public boolean importData(TransferSupport support) {
                    return false;
                }
            });
            InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
            int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutMask), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, KeyEvent.SHIFT_DOWN_MASK), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, KeyEvent.CTRL_DOWN_MASK), "none");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, KeyEvent.SHIFT_DOWN_MASK), "none");
        }

        @Override
        public void paste() {
            // Intentionally disabled for exam security
        }

        @Override
        public void copy() {
            // Intentionally disabled for exam security
        }

        @Override
        public void cut() {
            // Intentionally disabled for exam security
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            int w = getWidth();
            int h = getHeight();
            Shape box = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 14f, 14f);

            g2.setColor(Color.WHITE);
            g2.fill(box);

            if (isFocusOwner()) {
                g2.setPaint(UITheme.createPrimaryGradient(1f, 1f, w - 1f, h - 1f));
                g2.setStroke(new BasicStroke(2.0f));
            } else {
                g2.setColor(UITheme.BORDER_STRONG);
                g2.setStroke(new BasicStroke(1.2f));
            }
            g2.draw(box);

            g2.dispose();
            super.paintComponent(g);

            if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
                Graphics2D g3 = (Graphics2D) g.create();
                UITheme.configureGraphics(g3);
                g3.setFont(UITheme.font(Font.PLAIN, 16f));
                g3.setColor(UITheme.TEXT_MUTED);
                Insets ins = getInsets();
                FontMetrics fm = g3.getFontMetrics();
                g3.drawString(placeholder, ins.left, (h + fm.getAscent() - fm.getDescent()) / 2);
                g3.dispose();
            }
        }
    }

    /**
     * Displays a lazily-loaded question image scaled within maxWidth x maxHeight with click-to-zoom.
     */
    private static class QuestionImageBanner extends JPanel {
        private final int maxW;
        private final int maxH;
        private final java.util.function.Consumer<BufferedImage> onZoom;
        private BufferedImage fullImage;
        private boolean loading = false;
        private boolean hovered = false;

        QuestionImageBanner(int maxW, int maxH, java.util.function.Consumer<BufferedImage> onZoom) {
            this.maxW = maxW;
            this.maxH = maxH;
            this.onZoom = onZoom;
            setOpaque(false);
            setBorder(new EmptyBorder(4, 0, 8, 0));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText("Click to zoom image");

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (fullImage != null && onZoom != null) {
                        onZoom.accept(fullImage);
                    }
                }
            });
        }

        void setLoading() {
            this.loading = true;
            this.fullImage = null;
            setPreferredSize(new Dimension(240, 72));
            setMaximumSize(new Dimension(maxW + 24, 80));
            revalidate();
            repaint();
        }

        void clearImage() {
            this.loading = false;
            this.fullImage = null;
            setPreferredSize(new Dimension(0, 0));
            setMaximumSize(new Dimension(0, 0));
            revalidate();
            repaint();
        }

        void setImageBytes(byte[] bytes) {
            this.loading = false;
            try {
                this.fullImage = (bytes != null && bytes.length > 0)
                        ? ImageIO.read(new ByteArrayInputStream(bytes))
                        : null;
            } catch (Exception ex) {
                this.fullImage = null;
            }
            if (fullImage == null) {
                clearImage();
                return;
            }
            Dimension scaled = computeFittedSize(fullImage.getWidth(), fullImage.getHeight());
            setPreferredSize(new Dimension(scaled.width + 18, scaled.height + 20));
            setMaximumSize(new Dimension(maxW + 24, maxH + 24));
            revalidate();
            repaint();
        }

        private Dimension computeFittedSize(int iw, int ih) {
            double scale = Math.min(1.0, Math.min(maxW / (double) Math.max(1, iw), maxH / (double) Math.max(1, ih)));
            return new Dimension(Math.max(40, (int) Math.round(iw * scale)), Math.max(40, (int) Math.round(ih * scale)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            if (loading) {
                Shape box = new RoundRectangle2D.Float(2, 4, 220, 56, 12, 12);
                g2.setColor(UITheme.BG_SUBTLE);
                g2.fill(box);
                g2.setColor(UITheme.BORDER);
                g2.draw(box);
                g2.setFont(UITheme.font(Font.PLAIN, 12f));
                g2.setColor(UITheme.TEXT_MUTED);
                g2.drawString("Loading diagram...", 18, 36);
                g2.dispose();
                return;
            }

            if (fullImage != null) {
                Dimension d = computeFittedSize(fullImage.getWidth(), fullImage.getHeight());
                int bx = 2;
                int by = 4;
                int bw = d.width + 12;
                int bh = d.height + 12;
                Shape frame = new RoundRectangle2D.Float(bx, by, bw, bh, 12f, 12f);

                g2.setColor(Color.WHITE);
                g2.fill(frame);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(fullImage, bx + 6, by + 6, d.width, d.height, null);

                g2.setColor(hovered ? UITheme.PRIMARY : UITheme.BORDER);
                g2.setStroke(new BasicStroke(hovered ? 1.6f : 1.0f));
                g2.draw(frame);

                if (hovered) {
                    String hint = "Click to zoom";
                    g2.setFont(UITheme.font(Font.BOLD, 11f));
                    FontMetrics fm = g2.getFontMetrics();
                    int pw = fm.stringWidth(hint) + 16;
                    int ph = 22;
                    int px = bx + bw - pw - 8;
                    int py = by + bh - ph - 8;
                    g2.setColor(new Color(255, 255, 255, 232));
                    g2.fill(new RoundRectangle2D.Float(px, py, pw, ph, 10, 10));
                    g2.setColor(UITheme.PRIMARY);
                    g2.drawString(hint, px + 8, py + 15);
                }
            }

            g2.dispose();
        }
    }

    /**
     * Circular draining timer ring: shifts Indigo -> Amber -> Red and pulses in the final 5 minutes.
     */
    private static class CircularTimerWidget extends JComponent {
        private final int totalSeconds;
        private int remainingSeconds;
        private boolean urgent = false;
        private float pulsePhase = 0f;
        private final Timer pulseTimer;

        CircularTimerWidget(int totalSeconds) {
            this.totalSeconds = Math.max(1, totalSeconds);
            this.remainingSeconds = this.totalSeconds;
            setPreferredSize(new Dimension(134, 48));
            setOpaque(false);

            pulseTimer = new Timer(Anim.TICK_MS, e -> {
                if (!urgent || !isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                pulsePhase += 0.12f;
                if (pulsePhase > (float) (Math.PI * 2.0)) {
                    pulsePhase -= (float) (Math.PI * 2.0);
                }
                repaint();
            });
            pulseTimer.setCoalesce(true);
        }

        void updateTime(int remaining, boolean inFinalFive) {
            this.remainingSeconds = Math.max(0, remaining);
            this.urgent = inFinalFive;
            if (urgent && !pulseTimer.isRunning() && isShowing()) {
                pulseTimer.start();
            } else if (!urgent && pulseTimer.isRunning()) {
                pulseTimer.stop();
                pulsePhase = 0f;
            }
            repaint();
        }

        void stopAnimations() {
            pulseTimer.stop();
        }

        private Color resolveRingColor(double ratio) {
            if (urgent || ratio <= 0.25) {
                return UITheme.DANGER;
            }
            if (ratio <= 0.50) {
                // Smooth shift Indigo -> Amber as ratio goes 0.50 -> 0.25
                float t = (float) ((0.50 - ratio) / 0.25);
                return blend(UITheme.PRIMARY, UITheme.WARNING, t);
            }
            return UITheme.PRIMARY;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            double ratio = Math.max(0.0, Math.min(1.0, remainingSeconds / (double) totalSeconds));
            Color ringColor = resolveRingColor(ratio);

            float pulse = urgent ? (0.5f + 0.5f * (float) Math.sin(pulsePhase)) : 0f;

            // Pill backdrop
            Color bg = urgent
                    ? Anim.withAlpha(UITheme.DANGER, Math.round(22 + 24 * pulse))
                    : new Color(248, 250, 252, 230);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(1, 2, w - 2, h - 4, 12, 12));
            g2.setColor(urgent ? Anim.withAlpha(UITheme.DANGER, Math.round(90 + 110 * pulse)) : UITheme.BORDER);
            g2.setStroke(new BasicStroke(urgent ? 1.5f : 1f));
            g2.draw(new RoundRectangle2D.Float(1.5f, 2.5f, w - 3f, h - 5f, 12, 12));

            // Circular draining ring on left
            float ringSize = 30f;
            float rx = 11f;
            float ry = (h - ringSize) / 2f;

            if (urgent) {
                float scale = 1.0f + 0.08f * pulse;
                float rcx = rx + ringSize / 2f;
                float rcy = ry + ringSize / 2f;
                g2.translate(rcx, rcy);
                g2.scale(scale, scale);
                g2.translate(-rcx, -rcy);
            }

            g2.setColor(Anim.withAlpha(ringColor, 36));
            g2.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(rx, ry, ringSize, ringSize));

            g2.setColor(ringColor);
            g2.draw(new Arc2D.Double(rx, ry, ringSize, ringSize, 90, -(ratio * 360.0), Arc2D.OPEN));

            // Reset transform before drawing crisp MM:SS text
            g2.dispose();
            Graphics2D gText = (Graphics2D) g.create();
            UITheme.configureGraphics(gText);

            gText.setFont(UITheme.font(Font.BOLD, 9.5f));
            gText.setColor(urgent ? UITheme.DANGER_TEXT : UITheme.TEXT_MUTED);
            gText.drawString("TIME LEFT", 50, 17);

            gText.setFont(UITheme.font(Font.BOLD, 16f));
            gText.setColor(urgent ? UITheme.DANGER_TEXT : UITheme.TEXT_PRIMARY);
            gText.drawString(ExamStore.formatDuration(remainingSeconds), 50, 36);

            gText.dispose();
        }
    }

    /**
     * Soft pulsing red vignette glow along the screen edges during the final 5 minutes.
     */
    private static class EdgeDangerGlowOverlay extends JComponent {
        private boolean active = false;
        private float pulse = 0f;
        private float fade = 0f;
        private final Timer animTimer;

        EdgeDangerGlowOverlay() {
            setOpaque(false);
            animTimer = new Timer(Anim.TICK_MS, e -> {
                if (!isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                if (active) {
                    fade = Math.min(1f, fade + 0.05f);
                    pulse += 0.08f;
                    if (pulse > (float) (Math.PI * 2.0)) pulse -= (float) (Math.PI * 2.0);
                } else {
                    fade = Math.max(0f, fade - 0.06f);
                    if (fade <= 0.005f) {
                        ((Timer) e.getSource()).stop();
                    }
                }
                repaint();
            });
            animTimer.setCoalesce(true);
        }

        void setActive(boolean active) {
            this.active = active;
            if (active && !animTimer.isRunning() && isShowing()) {
                animTimer.start();
            }
        }

        // Mouse-transparent overlay so clicks always pass through to the exam UI
        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (fade <= 0.01f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float breathe = 0.55f + 0.45f * (float) Math.sin(pulse);
            int peakAlpha = Math.round(44f * fade * breathe);
            int depth = 46;

            Color red = Anim.withAlpha(UITheme.DANGER, peakAlpha);
            Color clear = Anim.withAlpha(UITheme.DANGER, 0);

            // Top, Bottom, Left, Right soft edge bands
            g2.setPaint(new GradientPaint(0, 0, red, 0, depth, clear));
            g2.fillRect(0, 0, w, depth);

            g2.setPaint(new GradientPaint(0, h, red, 0, h - depth, clear));
            g2.fillRect(0, h - depth, w, depth);

            g2.setPaint(new GradientPaint(0, 0, red, depth, 0, clear));
            g2.fillRect(0, 0, depth, h);

            g2.setPaint(new GradientPaint(w, 0, red, w - depth, 0, clear));
            g2.fillRect(w - depth, 0, depth, h);

            g2.dispose();
        }
    }

    /**
     * Segmented progress bar across the top: each segment fills when its question is answered.
     */
    private static class SegmentedProgressBar extends JComponent {
        private final int count;
        private int currentIdx = 0;
        private Set<Integer> answered = new HashSet<>();
        private Set<Integer> flagged = new HashSet<>();

        SegmentedProgressBar(int count) {
            this.count = Math.max(1, count);
            setPreferredSize(new Dimension(400, 10));
            setOpaque(false);
        }

        void updateStates(int currentIdx, Set<Integer> answeredSet, Set<Integer> flaggedSet) {
            this.currentIdx = currentIdx;
            this.answered = answeredSet == null ? new HashSet<>() : new HashSet<>(answeredSet);
            this.flagged = flaggedSet == null ? new HashSet<>() : new HashSet<>(flaggedSet);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float gap = 5f;
            float segW = Math.max(6f, (w - gap * (count - 1)) / count);
            float barH = 6f;
            float y = (h - barH) / 2f;

            for (int i = 0; i < count; i++) {
                float x = i * (segW + gap);
                boolean isAns = answered.contains(i);
                boolean isFlag = flagged.contains(i);
                boolean isCur = (i == currentIdx);

                Shape seg = new RoundRectangle2D.Float(x, y, segW, barH, barH, barH);
                if (isAns) {
                    g2.setPaint(UITheme.createPrimaryGradient(x, y, x + segW, y));
                    g2.fill(seg);
                } else if (isFlag) {
                    g2.setColor(UITheme.WARNING);
                    g2.fill(seg);
                } else {
                    g2.setColor(new Color(203, 213, 225, 165));
                    g2.fill(seg);
                }

                if (isCur) {
                    g2.setColor(UITheme.PRIMARY);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(new RoundRectangle2D.Float(x - 0.5f, y - 1.2f, segW + 1f, barH + 2.4f, barH + 2f, barH + 2f));
                }
            }
            g2.dispose();
        }
    }

    /**
     * Navigator palette button: answered (green tint), flagged (amber), current (indigo + gentle pulse), unanswered (light gray).
     */
    private static class NavPaletteButton extends JButton {
        enum State { CURRENT, ANSWERED, FLAGGED, UNANSWERED }

        private State state = State.UNANSWERED;
        private boolean hovered = false;
        private float pulsePhase = 0f;

        NavPaletteButton(String text) {
            super(text);
            setFont(UITheme.font(Font.BOLD, 13f));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(44, 44));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            });
        }

        void setState(State state) {
            this.state = state;
            repaint();
        }

        void setPulsePhase(float phase) {
            this.pulsePhase = phase;
            if (state == State.CURRENT) {
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int pad = 4;
            float size = Math.min(w, h) - pad * 2f;
            float x = (w - size) / 2f;
            float y = (h - size) / 2f;

            // Gentle indigo pulse ring for current question
            if (state == State.CURRENT) {
                float p = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
                float expand = 2.5f + 2.5f * p;
                int haloAlpha = Math.round(70 - 38 * p);
                g2.setColor(Anim.withAlpha(UITheme.PRIMARY, haloAlpha));
                g2.fill(new RoundRectangle2D.Float(x - expand, y - expand, size + expand * 2f, size + expand * 2f, 14, 14));
            }

            Shape shape = new RoundRectangle2D.Float(x, y, size, size, 11, 11);
            Color fg;

            switch (state) {
                case CURRENT:
                    g2.setPaint(UITheme.createPrimaryGradient(x, y, x + size, y + size));
                    g2.fill(shape);
                    fg = Color.WHITE;
                    break;
                case FLAGGED:
                    g2.setColor(UITheme.WARNING_SOFT);
                    g2.fill(shape);
                    g2.setColor(UITheme.WARNING);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(shape);
                    fg = UITheme.WARNING_TEXT;
                    break;
                case ANSWERED:
                    g2.setColor(UITheme.SUCCESS_SOFT);
                    g2.fill(shape);
                    g2.setColor(Anim.withAlpha(UITheme.SUCCESS, 140));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(shape);
                    fg = UITheme.SUCCESS_TEXT;
                    break;
                default:
                    g2.setColor(hovered ? UITheme.PRIMARY_SOFT : new Color(241, 245, 249));
                    g2.fill(shape);
                    g2.setColor(hovered ? UITheme.PRIMARY : UITheme.BORDER);
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(shape);
                    fg = hovered ? UITheme.PRIMARY : UITheme.TEXT_SECONDARY;
                    break;
            }

            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);

            g2.dispose();
        }
    }

    /**
     * Flag button that wiggles smoothly when toggled.
     */
    private static class WiggleFlagButton extends JButton {
        private boolean flagged = false;
        private boolean hovered = false;
        private float wiggleAngleRad = 0f;
        private Timer wiggleTimer;

        WiggleFlagButton() {
            super("Flag for Review");
            setFont(UITheme.font(Font.BOLD, 12f));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 14, 7, 14));
            setPreferredSize(new Dimension(148, 34));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            });
            updateStyle();
        }

        void setFlagged(boolean flagged) {
            this.flagged = flagged;
            updateStyle();
            repaint();
        }

        private void updateStyle() {
            setText(flagged ? "Flagged" : "Flag for Review");
            Color iconCol = flagged ? UITheme.WARNING_TEXT : UITheme.TEXT_SECONDARY;
            setIcon(UITheme.createSwingIcon(UITheme.IconType.FLAG, iconCol, 14));
            setIconTextGap(7);
            setForeground(flagged ? UITheme.WARNING_TEXT : UITheme.TEXT_SECONDARY);
        }

        void wiggle() {
            if (wiggleTimer != null && wiggleTimer.isRunning()) {
                wiggleTimer.stop();
            }
            wiggleTimer = Anim.animate(340, p -> {
                double damped = Math.sin(p * Math.PI * 5.0) * (1.0 - p);
                wiggleAngleRad = (float) (damped * 0.14); // ~8 degrees damped wiggle
                repaint();
            }, () -> {
                wiggleAngleRad = 0f;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            if (Math.abs(wiggleAngleRad) > 0.001f) {
                g2.rotate(wiggleAngleRad, w / 2.0, h / 2.0);
            }

            Shape pill = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 10, 10);
            if (flagged) {
                g2.setColor(UITheme.WARNING_SOFT);
                g2.fill(pill);
                g2.setColor(UITheme.WARNING);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(pill);
            } else {
                g2.setColor(hovered ? UITheme.WARNING_SOFT : new Color(241, 245, 249));
                g2.fill(pill);
                g2.setColor(hovered ? UITheme.WARNING : UITheme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(pill);
            }

            super.paintComponent(g2);
            g2.dispose();
        }
    }

    private static Color blend(Color a, Color b, float t) {
        float x = Anim.clamp01(t);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * x),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * x),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * x)
        );
    }
}
