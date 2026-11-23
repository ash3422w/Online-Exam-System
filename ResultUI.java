package com.onlineexam.ui.student;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.CertificateService;
import com.onlineexam.dao.DBConnection;
import com.onlineexam.data.CertificatePdfBuilder;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.ConfettiPanel;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

public class ResultUI extends JFrame {

    private ConfettiPanel confettiOverlay;
    private boolean navigatedAway = false;

    /**
     * Shows the current-session exam result; if none exists in memory yet,
     * gracefully falls back to the student's latest recorded MySQL attempt.
     */
    public ResultUI() {
        this(resolveInitialAttempt());
    }

    private static AttemptDAO.AttemptDetail resolveInitialAttempt() {
        if (ExamStore.hasValidExamResultForCurrentUser()) {
            return null; // Use live current-session ExamStore data
        }
        String uid = ExamStore.getCurrentUserId();
        if (uid != null && !uid.trim().isEmpty()) {
            List<AttemptDAO.AttemptDetail> list = AttemptDAO.getAttemptDetails(null, uid, false);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }

    /**
     * Overloaded constructor to display a specific recorded MySQL attempt (e.g. Latest Exam Result).
     */
    public ResultUI(AttemptDAO.AttemptDetail dbAttempt) {
        setTitle("Exam Result - Online Exam System");
        setSize(940, 710);
        setMinimumSize(new Dimension(800, 620));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (confettiOverlay != null) {
                    confettiOverlay.stop();
                }
                if (!navigatedAway) {
                    navigatedAway = true;
                    SwingUtilities.invokeLater(StudentDashboard::new);
                }
            }
        });

        final boolean useSession = (dbAttempt == null) && ExamStore.hasValidExamResultForCurrentUser();
        final boolean hasValidResult = useSession || (dbAttempt != null);

        final int scoreVal;
        final int totalQuestions;
        final int subjectId;
        final String subjectName;
        final String examTitle;
        final double marksObtained;
        final double totalMarks;
        final double negativeMarksLost;
        final double passMark;
        final String showPolicy;
        final LocalDateTime examEndAt;
        final String studentId = ExamStore.getCurrentUserId();
        final double percentage;
        final String grade;
        final String passStatus;
        final int timeTakenSeconds;
        final String submissionType;
        final String completedDateStr;
        final int historicalAttemptId;

        if (useSession) {
            scoreVal = ExamStore.score;
            totalQuestions = ExamStore.getCurrentExamQuestions().size();
            subjectId = ExamStore.getSelectedSubject();
            subjectName = ExamStore.getSubjectName(subjectId);
            ExamConfig cfg = ExamStore.getLastCompletedExamConfig();
            if (cfg == null) {
                cfg = ExamStore.getCurrentExamConfig();
            }
            examTitle = (cfg != null && cfg.title != null && !cfg.title.trim().isEmpty())
                    ? cfg.title.trim() : (subjectName + " Exam");
            double sessTotalMarks = ExamStore.getLastExamTotalMarks();
            totalMarks = sessTotalMarks > 0 ? sessTotalMarks : totalQuestions;
            marksObtained = sessTotalMarks > 0 ? ExamStore.getLastExamMarksObtained() : scoreVal;
            negativeMarksLost = ExamStore.getLastExamNegativeMarksLost();
            passMark = cfg != null ? cfg.passPercentage : AttemptDAO.PASS_PERCENTAGE;
            showPolicy = (cfg != null && cfg.showAnswers != null) ? cfg.showAnswers : ExamConfig.SHOW_IMMEDIATELY;
            examEndAt = cfg != null ? cfg.endAt : null;

            percentage = AttemptDAO.calculatePercentage(marksObtained, totalMarks);
            grade = AttemptDAO.calculateGrade(percentage);
            passStatus = AttemptDAO.calculatePassStatus(percentage, passMark);
            timeTakenSeconds = ExamStore.getLastExamTimeTakenSeconds();
            submissionType = ExamStore.getLastExamSubmissionType();
            completedDateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
            historicalAttemptId = 0;
        } else if (dbAttempt != null) {
            scoreVal = dbAttempt.score;
            totalQuestions = dbAttempt.totalQuestions;
            subjectId = dbAttempt.subjectId;
            subjectName = dbAttempt.subjectName != null ? dbAttempt.subjectName : ExamStore.getSubjectName(subjectId);
            examTitle = (dbAttempt.examTitle != null && !dbAttempt.examTitle.trim().isEmpty())
                    ? dbAttempt.examTitle.trim() : (subjectName + " Exam");
            marksObtained = dbAttempt.marksObtained;
            totalMarks = dbAttempt.totalMarks > 0 ? dbAttempt.totalMarks : totalQuestions;
            double rawEarned = scoreVal * (dbAttempt.marksPerQuestion > 0 ? dbAttempt.marksPerQuestion : 1.0);
            negativeMarksLost = Math.max(0.0, Math.round((rawEarned - marksObtained) * 100.0) / 100.0);
            passMark = dbAttempt.passPercentage > 0 ? dbAttempt.passPercentage : AttemptDAO.PASS_PERCENTAGE;
            showPolicy = dbAttempt.showAnswers != null ? dbAttempt.showAnswers : ExamConfig.SHOW_IMMEDIATELY;
            examEndAt = dbAttempt.examEndAt;

            percentage = dbAttempt.percentage;
            grade = dbAttempt.grade != null ? dbAttempt.grade : AttemptDAO.calculateGrade(percentage);
            passStatus = dbAttempt.status != null ? dbAttempt.status : AttemptDAO.calculatePassStatus(percentage, passMark);
            timeTakenSeconds = dbAttempt.timeTakenSeconds;
            submissionType = dbAttempt.submissionType != null ? dbAttempt.submissionType : ExamStore.SUBMISSION_MANUAL;
            completedDateStr = dbAttempt.attemptedDate != null ? dbAttempt.attemptedDate : "-";
            historicalAttemptId = dbAttempt.id;
        } else {
            scoreVal = 0;
            totalQuestions = 0;
            subjectId = 0;
            subjectName = "-";
            examTitle = "Exam Result";
            marksObtained = 0.0;
            totalMarks = 0.0;
            negativeMarksLost = 0.0;
            passMark = AttemptDAO.PASS_PERCENTAGE;
            showPolicy = ExamConfig.SHOW_IMMEDIATELY;
            examEndAt = null;
            percentage = 0.0;
            grade = "-";
            passStatus = "";
            timeTakenSeconds = 0;
            submissionType = "";
            completedDateStr = "";
            historicalAttemptId = 0;
        }

        final boolean passed = hasValidResult && "Pass".equalsIgnoreCase(passStatus);
        final String marksStr = ExamConfig.formatMarks(marksObtained) + " / " + ExamConfig.formatMarks(totalMarks);
        final String passMarkStr = ExamConfig.formatMarks(passMark) + "%";

        // Full-window Pastel Aurora Light background
        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(passed ? 0.85f : 0.40f);

        // Frosted-white light top bar
        JPanel topBar = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(255, 255, 255, 218));
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

        JLabel titleLbl = new JLabel(hasValidResult ? ("Assessment Result \u2014 " + examTitle) : "Exam Result");
        titleLbl.setFont(UITheme.font(Font.BOLD, 19f));
        titleLbl.setForeground(UITheme.TEXT_PRIMARY);

        JLabel subLbl = new JLabel(hasValidResult
                ? ("Subject: " + subjectName + "  \u2022  Candidate: " + studentId + "  \u2022  Completed: " + completedDateStr)
                : "Session Result Summary");
        subLbl.setFont(UITheme.font(Font.PLAIN, 12f));
        subLbl.setForeground(UITheme.TEXT_SECONDARY);
        subLbl.setBorder(new EmptyBorder(2, 0, 0, 0));

        headLeft.add(titleLbl);
        headLeft.add(subLbl);

        UITheme.ModernButton dashTopBtn = UITheme.secondaryButton("\u2190 Back to Dashboard");
        dashTopBtn.addActionListener(e -> {
            navigatedAway = true;
            dispose();
            new StudentDashboard();
        });

        topBar.add(headLeft, BorderLayout.WEST);
        topBar.add(dashTopBtn, BorderLayout.EAST);

        // Centered Frosted-White Main Result Card
        GlassPanel mainCard = new GlassPanel(18);
        mainCard.setShadowColor(passed ? UITheme.PRIMARY : UITheme.WARNING);
        mainCard.setTopAccentColor(passed ? UITheme.PRIMARY : UITheme.WARNING);
        mainCard.setSpotlightEnabled(false);
        mainCard.setLayout(new BorderLayout(0, 16));
        mainCard.setBorder(new EmptyBorder(24, 30, 24, 30));
        mainCard.setPreferredSize(new Dimension(820, 600));

        if (!hasValidResult) {
            JPanel emptyWrap = new JPanel(new GridBagLayout());
            emptyWrap.setOpaque(false);
            JLabel empty = new JLabel("No exam attempted yet. Start an exam from your dashboard to see results here.");
            empty.setFont(UITheme.FONT_HEADING);
            empty.setForeground(UITheme.TEXT_MUTED);
            emptyWrap.add(empty);
            mainCard.add(emptyWrap, BorderLayout.CENTER);
        } else {
            boolean auto = ExamStore.SUBMISSION_AUTOMATIC.equalsIgnoreCase(submissionType);
            String submissionDisplay = auto ? "Automatic (Time Up)" : "Manual";
            String timeTakenStr = ExamStore.formatDuration(timeTakenSeconds);

            // ---- TOP HERO SECTION: Circular Ring (Left) + Status, Grade Badge, Stat Chips (Right) ----
            JPanel heroSplit = new JPanel(new BorderLayout(28, 0));
            heroSplit.setOpaque(false);

            JPanel ringColumn = new JPanel();
            ringColumn.setLayout(new BoxLayout(ringColumn, BoxLayout.Y_AXIS));
            ringColumn.setOpaque(false);

            AnimatedScoreRing scoreRing = new AnimatedScoreRing(percentage, marksStr + " marks", passed);
            scoreRing.setAlignmentX(Component.CENTER_ALIGNMENT);

            JComponent gradeBadge = passed
                    ? new GoldShimmerBadge("Grade " + grade + "  \u2022  PASSED")
                    : UITheme.createBadge("Grade " + grade + "  \u2022  KEEP GOING", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT);
            if (!(gradeBadge instanceof GoldShimmerBadge)) {
                gradeBadge.setFont(UITheme.font(Font.BOLD, 12.5f));
                gradeBadge.setBorder(new EmptyBorder(6, 14, 6, 14));
            }
            gradeBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

            ringColumn.add(scoreRing);
            ringColumn.add(Box.createVerticalStrut(8));
            ringColumn.add(gradeBadge);

            JPanel rightInfo = new JPanel();
            rightInfo.setLayout(new BoxLayout(rightInfo, BoxLayout.Y_AXIS));
            rightInfo.setOpaque(false);

            JPanel headlineRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            headlineRow.setOpaque(false);
            headlineRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel headline = new JLabel(passed ? "Outstanding Work!" : "Good Effort \u2014 Keep Practicing");
            headline.setFont(UITheme.font(Font.BOLD, 22f));
            headline.setForeground(passed ? UITheme.TEXT_PRIMARY : UITheme.WARNING_TEXT);
            headlineRow.add(headline);

            if (negativeMarksLost > 0.0001) {
                JLabel negBadge = UITheme.createBadge(
                        "-" + ExamConfig.formatMarks(negativeMarksLost) + " negative marks",
                        UITheme.DANGER_SOFT, UITheme.DANGER_TEXT);
                headlineRow.add(negBadge);
            }

            JLabel encourager = new JLabel(passed
                    ? ("<html>You passed <b>" + examTitle + "</b> (" + subjectName + ") above the <b>" + passMarkStr + "</b> pass mark.</html>")
                    : ("<html>You scored below the <b>" + passMarkStr + "</b> pass mark in <b>" + examTitle + "</b> (" + subjectName + "). Keep practicing and try again!</html>"));
            encourager.setFont(UITheme.font(Font.PLAIN, 13f));
            encourager.setForeground(UITheme.TEXT_SECONDARY);
            encourager.setBorder(new EmptyBorder(4, 0, 12, 0));
            encourager.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel chipsGrid = new JPanel(new GridLayout(2, 2, 12, 12));
            chipsGrid.setOpaque(false);
            chipsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

            String marksSub = scoreVal + "/" + totalQuestions + " correct"
                    + (negativeMarksLost > 0.0001 ? ("  \u2022  -" + ExamConfig.formatMarks(negativeMarksLost) + " neg") : "");
            GlassPanel chip1 = buildStatChip("MARKS OBTAINED", marksStr, marksSub, UITheme.PRIMARY);
            GlassPanel chip2 = buildStatChip("PERCENTAGE", String.format("%.2f%%", percentage),
                    "Pass mark: " + passMarkStr,
                    passed ? UITheme.VIOLET : UITheme.WARNING_TEXT);
            GlassPanel chip3 = buildStatChip("TIME TAKEN", timeTakenStr, null, UITheme.SKY);
            GlassPanel chip4 = buildStatChip("SUBMISSION", submissionDisplay, null,
                    auto ? UITheme.WARNING_TEXT : UITheme.SUCCESS_TEXT);

            GlassPanel[] chips = {chip1, chip2, chip3, chip4};
            for (GlassPanel c : chips) {
                c.setAlpha(0f);
                c.setScale(0.80f);
                chipsGrid.add(c);
            }

            rightInfo.add(headlineRow);
            rightInfo.add(encourager);
            rightInfo.add(chipsGrid);

            heroSplit.add(ringColumn, BorderLayout.WEST);
            heroSplit.add(rightInfo, BorderLayout.CENTER);

            // ---- MIDDLE: CERTIFICATE PREVIEW CARD + RANK BANNER + OPTIONAL ANSWER-VISIBILITY NOTICE ----
            JPanel middleStack = new JPanel();
            middleStack.setLayout(new BoxLayout(middleStack, BoxLayout.Y_AXIS));
            middleStack.setOpaque(false);

            Runnable doPrint = () -> printScoreCertificate(
                    examTitle, subjectName, marksStr, scoreVal, totalQuestions, negativeMarksLost,
                    percentage, passMarkStr, grade, passStatus, timeTakenStr, submissionType, studentId
            );

            final int explicitAttemptId = (dbAttempt != null) ? dbAttempt.id : -1;
            Runnable doDownloadCertificate = () -> downloadCertificatePdf(
                    examTitle, subjectName, marksStr, scoreVal, totalQuestions,
                    percentage, grade, passed, studentId, explicitAttemptId, doPrint
            );

            CertificatePreviewCard certPreview = new CertificatePreviewCard(
                    studentId, examTitle + " (" + subjectName + ")", marksStr,
                    String.format("%.1f%%", percentage), grade, passed,
                    doDownloadCertificate
            );
            certPreview.setAlignmentX(Component.LEFT_ALIGNMENT);

            GlassPanel rankBanner = new GlassPanel(12);
            rankBanner.setShadowEnabled(false);
            rankBanner.setSpotlightEnabled(false);
            rankBanner.setFillColor(passed ? UITheme.PRIMARY_SOFT : UITheme.WARNING_SOFT);
            rankBanner.setBorderColor(passed ? Anim.withAlpha(UITheme.PRIMARY, 70) : Anim.withAlpha(UITheme.WARNING, 90));
            rankBanner.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 7));
            rankBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            rankBanner.setPreferredSize(new Dimension(700, 42));
            rankBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
            rankBanner.setAlpha(0f);
            rankBanner.setSlideOffset(0, 18);

            rankBanner.add(new UITheme.VectorIcon(
                    UITheme.IconType.TROPHY,
                    passed ? UITheme.PRIMARY : UITheme.WARNING_TEXT,
                    Color.WHITE,
                    22
            ));
            JLabel rankLbl = new JLabel("Score saved to " + subjectName + " leaderboard");
            rankLbl.setFont(UITheme.font(Font.BOLD, 13f));
            rankLbl.setForeground(passed ? UITheme.PRIMARY : UITheme.WARNING_TEXT);
            rankBanner.add(rankLbl);

            // Hidden answers info banner (shown only when show_answers hides answers)
            JLabel hiddenAnswersLbl = new JLabel("", SwingConstants.CENTER);
            hiddenAnswersLbl.setFont(UITheme.font(Font.BOLD, 12.5f));
            hiddenAnswersLbl.setForeground(UITheme.TEXT_SECONDARY);
            hiddenAnswersLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            hiddenAnswersLbl.setVisible(false);

            middleStack.add(Box.createVerticalStrut(4));
            middleStack.add(certPreview);
            middleStack.add(Box.createVerticalStrut(10));
            middleStack.add(rankBanner);
            middleStack.add(Box.createVerticalStrut(8));
            middleStack.add(hiddenAnswersLbl);

            // ---- BOTTOM ACTION BUTTONS ----
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
            btnPanel.setOpaque(false);

            UITheme.ModernButton reviewBtn = passed
                    ? UITheme.secondaryButton("Review Answers")
                    : UITheme.gradientPrimaryButton("Review Your Answers \u2192");
            reviewBtn.setPreferredSize(new Dimension(passed ? 165 : 215, 42));
            reviewBtn.addActionListener(e -> {
                navigatedAway = true;
                dispose();
                if (historicalAttemptId > 0) {
                    new ReviewUI(historicalAttemptId, subjectName, completedDateStr, scoreVal, totalQuestions, true);
                } else {
                    new ReviewUI();
                }
            });

            // Immediate check for NEVER policy; AFTER_EXAM_END checked against DB server time off-EDT
            final boolean isNeverPolicy = ExamConfig.SHOW_NEVER.equalsIgnoreCase(showPolicy);
            final boolean needsServerTimeCheck = ExamConfig.SHOW_AFTER_EXAM_END.equalsIgnoreCase(showPolicy) && examEndAt != null;
            if (isNeverPolicy) {
                reviewBtn.setVisible(false);
                hiddenAnswersLbl.setText(ExamConfig.getHiddenAnswersMessage(showPolicy, examEndAt));
                hiddenAnswersLbl.setVisible(true);
            } else if (needsServerTimeCheck) {
                reviewBtn.setEnabled(false);
                reviewBtn.setText("Checking availability...");
            }

            UITheme.ModernButton downloadCertBtn = passed
                    ? UITheme.gradientPrimaryButton("Download Certificate")
                    : UITheme.secondaryButton("Download Certificate");
            downloadCertBtn.setPreferredSize(new Dimension(190, 42));
            if (!passed) {
                downloadCertBtn.setEnabled(false);
                downloadCertBtn.setToolTipText("Certificates are only issued for passed examinations.");
            } else {
                downloadCertBtn.addActionListener(e -> doDownloadCertificate.run());
            }

            UITheme.ModernButton backBtn = UITheme.secondaryButton("Dashboard");
            backBtn.setPreferredSize(new Dimension(135, 42));
            backBtn.addActionListener(e -> {
                navigatedAway = true;
                dispose();
                new StudentDashboard();
            });

            btnPanel.add(reviewBtn);
            btnPanel.add(downloadCertBtn);
            btnPanel.add(backBtn);

            mainCard.add(heroSplit, BorderLayout.NORTH);
            mainCard.add(middleStack, BorderLayout.CENTER);
            mainCard.add(btnPanel, BorderLayout.SOUTH);

            // Load subject rank & DB server time (for AFTER_EXAM_END) off the EDT
            new SwingWorker<Void, Void>() {
                private AttemptDAO.StudentPerformanceSummary subjSummary;
                private LocalDateTime serverTime;

                @Override
                protected Void doInBackground() {
                    subjSummary = AttemptDAO.getStudentPerformanceSummary(studentId, subjectId > 0 ? subjectId : null);
                    if (needsServerTimeCheck) {
                        serverTime = DBConnection.getServerTime();
                    }
                    return null;
                }

                @Override
                protected void done() {
                    if (!isDisplayable()) return;
                    if (subjSummary != null && subjSummary.hasAttempts && subjSummary.rank > 0) {
                        String msg = "You ranked #" + subjSummary.rank + " in " + subjectName
                                + (subjSummary.totalRankedStudents > 1
                                ? (" out of " + subjSummary.totalRankedStudents + " students") : "");
                        rankLbl.setText(msg);
                    }
                    if (needsServerTimeCheck) {
                        boolean visible = ExamConfig.areAnswersVisible(showPolicy, examEndAt, serverTime);
                        if (visible) {
                            reviewBtn.setText(passed ? "Review Answers" : "Review Your Answers \u2192");
                            reviewBtn.setEnabled(true);
                            reviewBtn.setVisible(true);
                        } else {
                            reviewBtn.setVisible(false);
                            hiddenAnswersLbl.setText(ExamConfig.getHiddenAnswersMessage(showPolicy, examEndAt));
                            hiddenAnswersLbl.setVisible(true);
                        }
                        btnPanel.revalidate();
                        btnPanel.repaint();
                    }
                }
            }.execute();

            final boolean isPass = passed;
            SwingUtilities.invokeLater(() -> {
                scoreRing.startAnimation();

                for (int i = 0; i < chips.length; i++) {
                    final GlassPanel chip = chips[i];
                    Timer st = new Timer(220 + i * 95, ev -> {
                        ((Timer) ev.getSource()).stop();
                        Anim.animate(320, p -> {
                            chip.setAlpha(Anim.easeOutCubic(p));
                            chip.setScale(0.80f + 0.20f * Anim.easeOutBack(p));
                        }, () -> {
                            chip.setAlpha(1f);
                            chip.setScale(1f);
                        });
                    });
                    st.setRepeats(false);
                    st.start();
                }

                Timer rankStart = new Timer(780, ev -> {
                    ((Timer) ev.getSource()).stop();
                    Anim.slideIn(rankBanner, Anim.Direction.UP, 400, 18);
                });
                rankStart.setRepeats(false);
                rankStart.start();

                if (isPass) {
                    launchConfetti();
                }

                // Task 10: Celebration toast per unlocked badge after score ring animation
                Timer achCelebrationTimer = new Timer(1350, ev -> {
                    ((Timer) ev.getSource()).stop();
                    if (ExamStore.lastNewAchievements != null && !ExamStore.lastNewAchievements.isEmpty()) {
                        int delay = 0;
                        for (com.onlineexam.dao.AchievementService.Achievement ach : ExamStore.lastNewAchievements) {
                            UITheme.showAchievementToast(ResultUI.this, ach.name, ach.description, ach.iconKey, delay);
                            delay += 600;
                        }
                    }
                });
                achCelebrationTimer.setRepeats(false);
                achCelebrationTimer.start();
            });
        }

        JPanel centerWrap = new JPanel(new GridBagLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(18, 24, 18, 24));
        centerWrap.add(mainCard);

        JScrollPane scroll = new JScrollPane(
                centerWrap,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(scroll);

        root.add(topBar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        add(root);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (confettiOverlay != null) {
                    Dimension d = getLayeredPane().getSize();
                    confettiOverlay.setBounds(0, 0, d.width, d.height);
                }
            }
        });

        setVisible(true);
    }

    private void launchConfetti() {
        confettiOverlay = new ConfettiPanel();
        Dimension d = getLayeredPane().getSize();
        confettiOverlay.setBounds(0, 0, d.width, d.height);
        confettiOverlay.setOnFinished(() -> {
            if (confettiOverlay != null) {
                getLayeredPane().remove(confettiOverlay);
                getLayeredPane().repaint();
                confettiOverlay = null;
            }
        });
        getLayeredPane().add(confettiOverlay, JLayeredPane.POPUP_LAYER);
        getLayeredPane().moveToFront(confettiOverlay);
        confettiOverlay.startBurst();
    }

    private GlassPanel buildStatChip(String label, String value, String subText, Color accent) {
        GlassPanel chip = new GlassPanel(12);
        chip.setShadowEnabled(false);
        chip.setSpotlightEnabled(false);
        chip.setFillColor(Color.WHITE);
        chip.setBorderColor(UITheme.BORDER);
        chip.setLeftAccentColor(accent);
        chip.setLayout(new BorderLayout(0, 4));
        chip.setBorder(new EmptyBorder(12, 16, 12, 14));

        JLabel l = new JLabel(label);
        l.setFont(UITheme.font(Font.BOLD, 10.5f));
        l.setForeground(UITheme.TEXT_SECONDARY);

        JLabel v = new JLabel(value);
        v.setFont(UITheme.font(Font.BOLD, 17f));
        v.setForeground(accent);

        chip.add(l, BorderLayout.NORTH);
        chip.add(v, BorderLayout.CENTER);
        if (subText != null && !subText.isEmpty()) {
            JLabel s = new JLabel(subText);
            s.setFont(UITheme.font(Font.PLAIN, 11.5f));
            s.setForeground(UITheme.TEXT_MUTED);
            chip.add(s, BorderLayout.SOUTH);
        }
        return chip;
    }

    // ================= PRINT / PDF CERTIFICATE LOGIC =================

    private void printScoreCertificate(
            String examTitle, String subjectName, String marksStr, int scoreVal, int totalQuestions,
            double negativeMarksLost, double percentage, String passMarkStr,
            String grade, String status, String timeTaken, String subType, String studentId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a"));

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Exam Result - " + studentId + " - " + examTitle);
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) graphics;
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            int w = (int) pageFormat.getImageableWidth();

            g2.setColor(new Color(22, 32, 50));
            g2.setStroke(new BasicStroke(2.2f));
            g2.drawRoundRect(18, 18, w - 36, 480, 10, 10);
            g2.setColor(new Color(27, 77, 137));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(24, 24, w - 48, 468, 6, 6);

            g2.setColor(new Color(22, 32, 50));
            g2.fillRect(25, 25, w - 50, 74);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 19));
            g2.drawString("ONLINE EXAMINATION RESULT CERTIFICATE", 46, 58);

            g2.setColor(new Color(186, 210, 240));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.drawString("Official Assessment Transcript  \u2022  Issued on " + dateStr, 46, 80);

            g2.setColor(new Color(15, 23, 42));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            int y = 136;
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            rows.add(new String[]{"Candidate Name:", ExamStore.getCurrentDisplayName()});
            rows.add(new String[]{"Student ID:", studentId});
            rows.add(new String[]{"Exam:", examTitle});
            rows.add(new String[]{"Subject:", subjectName});
            rows.add(new String[]{"Marks:", marksStr + "  (" + scoreVal + "/" + totalQuestions + " correct)"});
            if (negativeMarksLost > 0.0001) {
                rows.add(new String[]{"Negative Deduction:", "-" + ExamConfig.formatMarks(negativeMarksLost) + " marks"});
            }
            rows.add(new String[]{"Percentage:", String.format("%.2f%%  (Pass Mark: %s)", percentage, passMarkStr)});
            rows.add(new String[]{"Grade:", "Grade " + grade});
            rows.add(new String[]{"Result Status:", status.toUpperCase()});
            rows.add(new String[]{"Time Taken:", timeTaken});
            rows.add(new String[]{"Submission Mode:", subType});

            for (String[] r : rows) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                g2.drawString(r[0], 60, y);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                g2.drawString(r[1], 215, y);
                y += 30;
            }
            return Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            try {
                job.print();
                UITheme.showToast(this, "Report Sent", "Score certificate printed / saved.", UITheme.ToastType.SUCCESS);
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ================= REAL PDF CERTIFICATE DOWNLOAD (TASK 11) =================

    private void downloadCertificatePdf(
            String examTitle, String subjectName, String marksStr, int scoreVal, int totalQuestions,
            double percentage, String grade, boolean passed, String studentId, int explicitAttemptId, Runnable fallbackPrint) {

        if (!passed) {
            UITheme.showToast(this, "Certificate Ineligible", "Official certificates are only issued for passed examinations.", UITheme.ToastType.WARNING);
            return;
        }

        // Check if OpenPDF and ZXing core libraries are on classpath
        if (!CertificatePdfBuilder.isAvailable()) {
            UITheme.showToast(this, "PDF Library Missing", "OpenPDF/ZXing library not found. Falling back to system print.", UITheme.ToastType.WARNING);
            if (fallbackPrint != null) fallbackPrint.run();
            return;
        }

        int targetAttemptId = explicitAttemptId;
        if (targetAttemptId <= 0) {
            targetAttemptId = ExamStore.getLastSavedAttemptId();
        }
        if (targetAttemptId <= 0) {
            List<AttemptDAO.AttemptDetail> list = AttemptDAO.getAttemptDetails(null, studentId, false);
            if (list != null && !list.isEmpty()) {
                targetAttemptId = list.get(0).id;
            }
        }

        if (targetAttemptId <= 0) {
            UITheme.showToast(this, "Attempt Not Found", "Could not locate attempt record in MySQL database.", UITheme.ToastType.ERROR);
            return;
        }

        final int finalAttemptId = targetAttemptId;
        String studentDisplayName = ExamStore.getCurrentDisplayName();
        String cleanExam = examTitle.replaceAll("[^a-zA-Z0-9_-]", "_");
        String cleanStudent = studentDisplayName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String defaultFileName = "Certificate_" + cleanExam + "_" + cleanStudent + ".pdf";

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Examination Certificate PDF");
        chooser.setSelectedFile(new File(defaultFileName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        int userSelection = chooser.showSaveDialog(this);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File chosenFile = chooser.getSelectedFile();
        if (chosenFile == null) return;
        if (!chosenFile.getName().toLowerCase(Locale.ENGLISH).endsWith(".pdf")) {
            chosenFile = new File(chosenFile.getParentFile(), chosenFile.getName() + ".pdf");
        }
        final File targetFile = chosenFile;

        // Progress Dialog
        final JDialog progressDialog = new JDialog(this, "Generating Certificate", true);
        progressDialog.setUndecorated(true);
        progressDialog.setSize(320, 100);
        progressDialog.setLocationRelativeTo(this);

        JPanel pPanel = new JPanel(new BorderLayout(12, 12));
        pPanel.setBackground(Color.WHITE);
        pPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.PRIMARY, 2),
                new EmptyBorder(16, 20, 16, 20)
        ));
        JLabel pText = new JLabel("Creating verifiable PDF certificate...", SwingConstants.CENTER);
        pText.setFont(UITheme.font(Font.BOLD, 12.5f));
        pText.setForeground(UITheme.TEXT_PRIMARY);

        JProgressBar pBar = new JProgressBar();
        pBar.setIndeterminate(true);
        pPanel.add(pText, BorderLayout.NORTH);
        pPanel.add(pBar, BorderLayout.CENTER);
        progressDialog.setContentPane(pPanel);

        new SwingWorker<CertificateService.CertificateRecord, Void>() {
            @Override
            protected CertificateService.CertificateRecord doInBackground() throws Exception {
                CertificateService.CertificateRecord rec = CertificateService.getOrCreateCertificate(finalAttemptId);
                if (rec == null) {
                    throw new IllegalStateException("Attempt is ineligible or certificate could not be created.");
                }

                String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
                CertificatePdfBuilder.buildCertificate(
                        targetFile,
                        rec.certCode,
                        studentDisplayName,
                        examTitle,
                        subjectName,
                        marksStr,
                        String.format(Locale.ENGLISH, "%.1f%%", percentage),
                        grade,
                        formattedDate
                );
                return rec;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    CertificateService.CertificateRecord rec = get();
                    UITheme.showToast(ResultUI.this, "Certificate Downloaded", "Saved to " + targetFile.getName(), UITheme.ToastType.SUCCESS);

                    int choice = JOptionPane.showConfirmDialog(
                            ResultUI.this,
                            "Certificate " + rec.certCode + " successfully generated!\nSaved to:\n"
                                    + targetFile.getAbsolutePath() + "\n\nWould you like to open the PDF now?",
                            "Certificate Ready",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    if (choice == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(targetFile);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    UITheme.showToast(ResultUI.this, "Generation Failed", "Could not create PDF: " + ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        }.execute();

        progressDialog.setVisible(true);
    }

    // =========================================================================
    // CUSTOM RESULT VISUAL COMPONENTS
    // =========================================================================

    private static class AnimatedScoreRing extends JComponent {
        private final double targetPct;
        private double currentPct = 0.0;
        private final String subCaption;
        private final boolean passed;

        AnimatedScoreRing(double percentage, String subCaption, boolean passed) {
            this.targetPct = Math.max(0.0, Math.min(100.0, percentage));
            this.subCaption = subCaption;
            this.passed = passed;
            setPreferredSize(new Dimension(196, 196));
            setMinimumSize(new Dimension(196, 196));
            setMaximumSize(new Dimension(196, 196));
            setOpaque(false);
        }

        void startAnimation() {
            currentPct = 0.0;
            Anim.animate(1200, p -> {
                currentPct = targetPct * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                currentPct = targetPct;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int size = Math.min(getWidth(), getHeight()) - 24;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            g2.setColor(passed ? Anim.withAlpha(UITheme.PRIMARY, 16) : Anim.withAlpha(UITheme.WARNING, 22));
            g2.fill(new Ellipse2D.Float(x + 8, y + 8, size - 16, size - 16));

            g2.setColor(passed ? Anim.withAlpha(UITheme.PRIMARY, 32) : Anim.withAlpha(UITheme.WARNING, 45));
            g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(x, y, size, size));

            if (currentPct > 0.1) {
                double extent = -(currentPct / 100.0) * 360.0;
                if (passed) {
                    g2.setPaint(UITheme.createPrimaryGradient(x, y, x + size, y + size));
                } else {
                    g2.setPaint(new LinearGradientPaint(x, y, x + size, y + size,
                            new float[]{0f, 1f}, new Color[]{UITheme.WARNING, new Color(251, 191, 36)}));
                }
                g2.draw(new Arc2D.Double(x, y, size, size, 90, extent, Arc2D.OPEN));
            }

            String pctText = String.format("%.0f%%", currentPct);
            g2.setFont(UITheme.font(Font.BOLD, 36f));
            g2.setColor(UITheme.TEXT_PRIMARY);
            FontMetrics fmPct = g2.getFontMetrics();
            int px = (getWidth() - fmPct.stringWidth(pctText)) / 2;
            int py = getHeight() / 2 + 6;
            g2.drawString(pctText, px, py);

            String marksText = subCaption != null ? subCaption : "";
            g2.setFont(UITheme.font(Font.PLAIN, 12f));
            g2.setColor(UITheme.TEXT_SECONDARY);
            FontMetrics fmM = g2.getFontMetrics();
            g2.drawString(marksText, (getWidth() - fmM.stringWidth(marksText)) / 2, py + 22);

            g2.dispose();
        }
    }

    private static class GoldShimmerBadge extends JComponent {
        private final String text;
        private float sweep = -0.5f;
        private final Timer shimmerTimer;

        GoldShimmerBadge(String text) {
            this.text = text;
            setPreferredSize(new Dimension(172, 32));
            setMaximumSize(new Dimension(190, 32));
            setOpaque(false);

            shimmerTimer = new Timer(Anim.TICK_MS, e -> {
                if (!isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                sweep += 0.022f;
                if (sweep > 1.8f) {
                    sweep = -0.6f;
                }
                repaint();
            });
            shimmerTimer.setCoalesce(true);

            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        if (!shimmerTimer.isRunning()) shimmerTimer.start();
                    } else {
                        shimmerTimer.stop();
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (isShowing() && !shimmerTimer.isRunning()) shimmerTimer.start();
        }

        @Override
        public void removeNotify() {
            shimmerTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            Shape pill = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h, h);

            g2.setPaint(new LinearGradientPaint(0, 0, w, h,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(254, 243, 199), new Color(253, 230, 138), new Color(254, 243, 199)}));
            g2.fill(pill);

            if (sweep >= -0.3f && sweep <= 1.3f) {
                Shape oldClip = g2.getClip();
                g2.clip(pill);
                float cx = sweep * w;
                g2.setPaint(new LinearGradientPaint(cx - 28f, 0, cx + 28f, h,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 210), new Color(255, 255, 255, 0)}));
                g2.fill(pill);
                g2.setClip(oldClip);
            }

            g2.setColor(new Color(217, 119, 6, 140));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(pill);

            g2.setFont(UITheme.font(Font.BOLD, 12f));
            g2.setColor(new Color(146, 64, 14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);

            g2.dispose();
        }
    }

    private static class CertificatePreviewCard extends JPanel {
        private float shineProgress = -1f;
        private boolean hovered = false;
        private Timer shineTimer;

        CertificatePreviewCard(
                String studentId, String subject, String scoreStr, String pctStr,
                String grade, boolean passed, Runnable onDownloadClick) {
            setOpaque(false);
            setLayout(new BorderLayout(16, 0));
            setBorder(new EmptyBorder(14, 18, 14, 18));
            setMinimumSize(new Dimension(420, 78));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
            setPreferredSize(new Dimension(700, 82));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
            left.setOpaque(false);
            left.add(new UITheme.VectorIcon(
                    UITheme.IconType.AWARD,
                    passed ? UITheme.PRIMARY : UITheme.WARNING_TEXT,
                    passed ? UITheme.PRIMARY_SOFT : UITheme.WARNING_SOFT,
                    40
            ));

            JPanel textStack = new JPanel(new GridLayout(2, 1, 0, 2));
            textStack.setOpaque(false);

            JLabel t = new JLabel("Official Score Certificate \u2014 " + subject);
            t.setFont(UITheme.font(Font.BOLD, 13.5f));
            t.setForeground(UITheme.TEXT_PRIMARY);

            String displayName = ExamStore.getCurrentDisplayName();
            JLabel meta = new JLabel("Issued to " + displayName + " (" + studentId + ")  \u2022  Score: " + scoreStr
                    + " (" + pctStr + ")  \u2022  Grade " + grade);
            meta.setFont(UITheme.font(Font.PLAIN, 12f));
            meta.setForeground(UITheme.TEXT_SECONDARY);

            textStack.add(t);
            textStack.add(meta);
            left.add(textStack);

            JLabel actionHint = passed
                    ? UITheme.createBadge("Click to Download PDF", UITheme.PRIMARY_SOFT, UITheme.PRIMARY)
                    : UITheme.createBadge("Pass Required for Certificate", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT);
            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            right.add(actionHint);

            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    triggerShineSweep();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (passed && onDownloadClick != null) onDownloadClick.run();
                }
            });
        }

        private void triggerShineSweep() {
            if (shineTimer != null && shineTimer.isRunning()) {
                shineTimer.stop();
            }
            shineProgress = -0.25f;
            shineTimer = Anim.animate(480, p -> {
                shineProgress = -0.25f + 1.55f * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                shineProgress = -1f;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            Shape box = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 14f, 14f);

            g2.setPaint(new LinearGradientPaint(0, 0, w, h,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 235), new Color(238, 242, 255, 210)}));
            g2.fill(box);

            g2.setColor(Anim.withAlpha(UITheme.PRIMARY, hovered ? 95 : 42));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g2.draw(new RoundRectangle2D.Float(6f, 6f, w - 12f, h - 12f, 10f, 10f));

            if (shineProgress > -0.3f && shineProgress < 1.35f) {
                Shape oldClip = g2.getClip();
                g2.clip(box);
                float sx = shineProgress * w;
                Path2D ribbon = new Path2D.Float();
                ribbon.moveTo(sx - 45f, 0);
                ribbon.lineTo(sx + 25f, 0);
                ribbon.lineTo(sx - 20f, h);
                ribbon.lineTo(sx - 90f, h);
                ribbon.closePath();
                g2.setPaint(new LinearGradientPaint(sx - 65f, 0, sx + 15f, h,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 195), new Color(255, 255, 255, 0)}));
                g2.fill(ribbon);
                g2.setClip(oldClip);
            }

            g2.setStroke(new BasicStroke(hovered ? 1.4f : 1f));
            if (hovered) {
                g2.setPaint(UITheme.createPrimaryGradient(0, 0, w, h));
            } else {
                g2.setColor(UITheme.BORDER);
            }
            g2.draw(box);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
