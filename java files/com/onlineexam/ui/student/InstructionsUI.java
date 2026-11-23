package com.onlineexam.ui.student;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import com.onlineexam.dao.ExamDAO;
import com.onlineexam.dao.WebcamProctorService;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

public class InstructionsUI extends JFrame {

    private final DashboardComponents.TransitionContainer contentWrap;
    private final ExamConfig activeConfig;
    private boolean navigating = false;

    public InstructionsUI() {
        ExamConfig cfg = ExamStore.getCurrentExamConfig();
        int subjectId = (cfg != null && cfg.subjectId > 0) ? cfg.subjectId : ExamStore.getSelectedSubject();
        if (cfg == null && subjectId > 0) {
            List<ExamConfig> subjectExams = ExamDAO.getExamsForSubject(subjectId);
            if (!subjectExams.isEmpty()) {
                cfg = subjectExams.get(0);
                ExamStore.setSelectedExam(cfg);
            }
        }
        this.activeConfig = cfg;
        if (cfg != null && cfg.requireWebcam) {
            new Thread(WebcamProctorService::isCameraDetected, "Webcam-Prewarm").start();
        }

        String subjectName = (cfg != null && cfg.subjectName != null)
                ? cfg.subjectName : ExamStore.getSubjectName(subjectId);
        String examTitle = (cfg != null && cfg.title != null && !cfg.title.trim().isEmpty())
                ? cfg.title : (subjectName + " Examination");
        int availableForSubject = ExamStore.removeDuplicateQuestions(ExamStore.getQuestionsBySubject(subjectId)).size();
        int requestedQuestions = cfg != null ? cfg.questionCount : ExamStore.getExamQuestionCount();
        int totalQuestions = availableForSubject > 0 ? Math.min(requestedQuestions, availableForSubject) : requestedQuestions;
        int durationMin = cfg != null ? cfg.getEffectiveDurationMinutes(totalQuestions) : Math.max(1, totalQuestions);
        double marksPerQ = cfg != null ? cfg.marksPerQuestion : 1.0;
        double negMark = cfg != null ? cfg.negativeMarking : 0.0;
        double passPct = cfg != null ? cfg.passPercentage : 40.0;
        String showPolicy = cfg != null ? cfg.showAnswers : ExamConfig.SHOW_IMMEDIATELY;

        setTitle("Exam Instructions - " + examTitle);
        setSize(960, 700);
        setMinimumSize(new Dimension(800, 610));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.42f);

        // Clean white top bar
        JPanel topBar = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.BORDER);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(14, 28, 14, 28));

        JPanel titleGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleGroup.setOpaque(false);

        JComponent shieldBadge = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setPaint(UITheme.createPrimaryGradient(0, 0, 38, 38));
                g2.fill(new RoundRectangle2D.Float(0, 0, 38, 38, 11, 11));
                Icon shield = UITheme.createSwingIcon(UITheme.IconType.SHIELD, Color.WHITE, 20);
                shield.paintIcon(this, g2, 9, 9);
                g2.dispose();
            }
        };
        shieldBadge.setPreferredSize(new Dimension(38, 38));

        JPanel titleStack = new JPanel(new GridLayout(2, 1, 0, 2));
        titleStack.setOpaque(false);
        JLabel hdrTitle = new JLabel("Examination Briefing \u2014 " + examTitle);
        hdrTitle.setFont(UITheme.font(Font.BOLD, 19f));
        hdrTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel hdrSub = new JLabel("Candidate: " + ExamStore.getCurrentDisplayName()
                + " (" + ExamStore.getCurrentUserId() + ")"
                + "  \u2022  Subject: " + subjectName
                + "  \u2022  Review all rules before starting");
        hdrSub.setFont(UITheme.font(Font.PLAIN, 12f));
        hdrSub.setForeground(UITheme.TEXT_SECONDARY);
        titleStack.add(hdrTitle);
        titleStack.add(hdrSub);
        titleGroup.add(shieldBadge);
        titleGroup.add(titleStack);

        topBar.add(titleGroup, BorderLayout.WEST);

        contentWrap = new DashboardComponents.TransitionContainer(new GridBagLayout());
        contentWrap.setBorder(new EmptyBorder(20, 28, 22, 28));

        GlassPanel card = new GlassPanel(18);
        card.setShadowColor(UITheme.PRIMARY);
        card.setSpotlightEnabled(false);
        card.setLayout(new BorderLayout(0, 20));
        card.setBorder(new EmptyBorder(26, 30, 24, 30));
        card.setPreferredSize(new Dimension(820, 550));

        // Top 4 Staggered Bento Spec Tiles
        JPanel metricsRow = new JPanel(new GridLayout(1, 4, 12, 0));
        metricsRow.setOpaque(false);

        DashboardComponents.BentoCard t1 = createSpecTile("SUBJECT", subjectName, UITheme.IconType.BOOK, UITheme.PRIMARY, UITheme.PRIMARY_SOFT);
        DashboardComponents.BentoCard t2 = createSpecTile("QUESTIONS", totalQuestions + " MCQs", UITheme.IconType.LIST, UITheme.VIOLET, UITheme.VIOLET_SOFT);
        DashboardComponents.BentoCard t3 = createSpecTile("DURATION", durationMin + " Minutes", UITheme.IconType.CLOCK, UITheme.SKY, UITheme.SKY_SOFT);
        DashboardComponents.BentoCard t4 = createSpecTile("PASS MARK", ExamConfig.formatMarks(passPct) + "% Minimum", UITheme.IconType.CHECKLIST, UITheme.SUCCESS, UITheme.SUCCESS_SOFT);

        metricsRow.add(t1);
        metricsRow.add(t2);
        metricsRow.add(t3);
        metricsRow.add(t4);

        // Rules list reflecting actual per-exam settings
        JPanel rulesPanel = new JPanel();
        rulesPanel.setLayout(new BoxLayout(rulesPanel, BoxLayout.Y_AXIS));
        rulesPanel.setOpaque(false);

        JLabel rulesHeading = new JLabel("Examination Rules & Marking Scheme");
        rulesHeading.setFont(UITheme.font(Font.BOLD, 15.5f));
        rulesHeading.setForeground(UITheme.TEXT_PRIMARY);
        rulesHeading.setBorder(new EmptyBorder(2, 0, 10, 0));
        rulesPanel.add(rulesHeading);

        String markingRule = (negMark > 0.0)
                ? ("Marking: +" + ExamConfig.formatMarks(marksPerQ) + " mark(s) for each correct answer, -"
                + ExamConfig.formatMarks(negMark) + " mark(s) deducted per wrong answer. Unanswered questions are never penalised.")
                : ("Marking: +" + ExamConfig.formatMarks(marksPerQ) + " mark(s) per correct answer. No negative marking.");

        String answersRule;
        if (ExamConfig.SHOW_NEVER.equalsIgnoreCase(showPolicy)) {
            answersRule = "Answer Review: Detailed answers and explanations are hidden for this examination.";
        } else if (ExamConfig.SHOW_AFTER_EXAM_END.equalsIgnoreCase(showPolicy) && cfg != null && cfg.endAt != null) {
            answersRule = "Answer Review: Available after " + cfg.endAt.format(ExamConfig.DISPLAY_DT) + ".";
        } else {
            answersRule = "Answer Review: Detailed solutions and explanations are available immediately after submission.";
        }

        String[] rules = {
                "Each question presents 4 randomized multiple-choice options with 1 correct answer.",
                markingRule,
                answersRule,
                "Use the Question Navigator or keyboard shortcuts (A-D select, Left/Right navigate, F flag).",
                "A 5-minute warning pulses before time expires; the exam auto-submits at 00:00.",
                "Window-focus proctoring is active: switching away 3 times triggers automatic submission."
        };

        for (int i = 0; i < rules.length; i++) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(5, 0, 5, 0));

            JLabel num = UITheme.createBadge(String.valueOf(i + 1), UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
            num.setPreferredSize(new Dimension(28, 24));

            JLabel text = new JLabel("<html>" + rules[i] + "</html>");
            text.setFont(UITheme.font(Font.PLAIN, 13f));
            text.setForeground(UITheme.TEXT_SECONDARY);

            row.add(num, BorderLayout.WEST);
            row.add(text, BorderLayout.CENTER);
            rulesPanel.add(row);
        }

        // Bottom Acknowledgement Pill + Action Buttons
        JPanel footerPanel = new JPanel(new BorderLayout(0, 14));
        footerPanel.setOpaque(false);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.GLASS_SLATE_BORDER),
                new EmptyBorder(14, 0, 0, 0)
        ));

        UITheme.RoundedPanel agreePill = new UITheme.RoundedPanel(10, UITheme.PRIMARY_SOFT, Anim.withAlpha(UITheme.PRIMARY, 60));
        agreePill.setLayout(new BorderLayout());
        agreePill.setBorder(new EmptyBorder(8, 12, 8, 12));

        JCheckBox agreeCheck = new JCheckBox("I have read and understood the examination rules, marking scheme, and proctoring policy.");
        agreeCheck.setFont(UITheme.font(Font.BOLD, 12.5f));
        agreeCheck.setForeground(UITheme.TEXT_PRIMARY);
        agreeCheck.setOpaque(false);
        agreeCheck.setFocusPainted(false);
        agreeCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));
        agreePill.add(agreeCheck, BorderLayout.CENTER);

        UITheme.ModernButton backBtn = UITheme.secondaryButton("\u2190 Back to Exams");
        backBtn.setPreferredSize(new Dimension(165, 42));
        backBtn.addActionListener(e -> navigateTo(SubjectSelectionUI::new));

        UITheme.ModernButton startBtn = UITheme.gradientPrimaryButton("Begin Examination \u2192");
        startBtn.setFont(UITheme.font(Font.BOLD, 14f));
        startBtn.setPreferredSize(new Dimension(212, 42));
        startBtn.setEnabled(false);
        startBtn.addActionListener(e -> verifyRetakeAndStart(startBtn));

        agreeCheck.addActionListener(e -> {
            boolean ok = agreeCheck.isSelected();
            startBtn.setEnabled(ok);
            agreePill.setFillColor(ok ? UITheme.SUCCESS_SOFT : UITheme.PRIMARY_SOFT);
            agreePill.setBorderColor(ok ? UITheme.SUCCESS : Anim.withAlpha(UITheme.PRIMARY, 60));
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnRow.setOpaque(false);
        btnRow.add(backBtn);
        btnRow.add(startBtn);

        footerPanel.add(agreePill, BorderLayout.NORTH);
        footerPanel.add(btnRow, BorderLayout.SOUTH);

        card.add(metricsRow, BorderLayout.NORTH);
        card.add(rulesPanel, BorderLayout.CENTER);
        card.add(footerPanel, BorderLayout.SOUTH);

        contentWrap.add(card);

        root.add(topBar, BorderLayout.NORTH);
        root.add(contentWrap, BorderLayout.CENTER);

        add(root);
        setVisible(true);

        List<GlassPanel> stagger = new ArrayList<>();
        stagger.add(t1);
        stagger.add(t2);
        stagger.add(t3);
        stagger.add(t4);
        SwingUtilities.invokeLater(() -> {
            Anim.slideIn(card, Anim.Direction.UP, 360, 18);
            DashboardComponents.staggerRiseFade(stagger, 60);
        });
    }

    private void verifyRetakeAndStart(UITheme.ModernButton startBtn) {
        if (navigating) return;
        startBtn.setEnabled(false);
        startBtn.setText("Checking...");

        SwingWorker<ExamDAO.ExamAvailabilityResult, Void> worker = new SwingWorker<ExamDAO.ExamAvailabilityResult, Void>() {
            @Override
            protected ExamDAO.ExamAvailabilityResult doInBackground() {
                if (activeConfig != null && activeConfig.id > 0) {
                    return ExamDAO.canStart(ExamStore.getCurrentUserId(), activeConfig.id);
                }
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    ExamDAO.ExamAvailabilityResult res = get();
                    if (res == null || res.canStart) {
                        ExamConfig freshest = (res != null && res.exam != null) ? res.exam : activeConfig;
                        if (freshest != null && freshest.requireWebcam) {
                            boolean consentGiven = WebcamProctorService.showConsentDialog(InstructionsUI.this, freshest.title);
                            if (!consentGiven) {
                                startBtn.setEnabled(true);
                                startBtn.setText("Begin Examination \u2192");
                                boolean hasCam = WebcamProctorService.isCameraDetected();
                                String reason = hasCam
                                        ? "You declined the proctoring consent. Webcam proctoring is mandatory for this exam."
                                        : "No active webcam detected. A functional camera is mandatory for this proctored exam.";
                                UITheme.showToast(InstructionsUI.this, "Cannot Start Exam", reason, UITheme.ToastType.WARNING);
                                return;
                            }
                        }
                        navigateTo(() -> {
                            ExamStore.resetAttemptState();
                            if (freshest != null) {
                                ExamStore.setSelectedExam(freshest);
                            }
                            new ExamModule();
                        });
                    } else {
                        startBtn.setText(res.isAttemptLimitReached()
                                ? ("Attempts " + res.attemptsUsed + "/" + res.maxAttempts)
                                : "Exam Unavailable");
                        UITheme.showToast(
                                InstructionsUI.this,
                                "Cannot Start Exam",
                                res.reason,
                                UITheme.ToastType.WARNING
                        );
                    }
                } catch (Exception ex) {
                    startBtn.setEnabled(true);
                    startBtn.setText("Begin Examination \u2192");
                    UITheme.showToast(InstructionsUI.this, "Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        };
        worker.execute();
    }

    private void navigateTo(Runnable nextScreen) {
        if (navigating) return;
        navigating = true;
        contentWrap.transitionOutAndRun(() -> {
            dispose();
            if (nextScreen != null) nextScreen.run();
        });
    }

    private DashboardComponents.BentoCard createSpecTile(
            String label, String value, UITheme.IconType iconType, Color accent, Color softBg) {
        DashboardComponents.BentoCard tile = new DashboardComponents.BentoCard(12);
        tile.setShadowColor(accent);
        tile.setLeftAccentColor(accent);
        tile.setLayout(new BorderLayout(10, 0));
        tile.setBorder(new EmptyBorder(12, 14, 14, 12));

        UITheme.VectorIcon icon = new UITheme.VectorIcon(iconType, accent, softBg, 32);

        JPanel stack = new JPanel(new GridLayout(2, 1, 0, 2));
        stack.setOpaque(false);

        JLabel l = new JLabel(label);
        l.setFont(UITheme.font(Font.BOLD, 10f));
        l.setForeground(UITheme.TEXT_MUTED);

        JLabel v = new JLabel(value);
        v.setFont(UITheme.font(Font.BOLD, 14f));
        v.setForeground(UITheme.TEXT_PRIMARY);

        stack.add(l);
        stack.add(v);

        tile.add(icon, BorderLayout.WEST);
        tile.add(stack, BorderLayout.CENTER);
        return tile;
    }
}
