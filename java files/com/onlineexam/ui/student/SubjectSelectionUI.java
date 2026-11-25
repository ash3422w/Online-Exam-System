package com.onlineexam.ui.student;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.DBConnection;
import com.onlineexam.dao.ExamDAO;
import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

public class SubjectSelectionUI extends JFrame {

    private static final long URGENT_THRESHOLD_SECONDS = 10 * 60; // 10 minutes
    private static final int OFFSET_REFRESH_MS = 60_000;          // 60 seconds

    private final String currentUser;
    private final DashboardComponents.TransitionContainer contentWrap;
    private final UITheme.ScrollablePanel gridPanel;
    private final List<LiveExamCard> liveCards = new ArrayList<>();

    // Server clock synchronization state (immune to PC clock tampering)
    private LocalDateTime serverTimeAtSync;
    private long syncMonoNanos;
    @SuppressWarnings("unused")
    private long serverOffsetMs = 0L;

    private final javax.swing.Timer countdownTimer;
    private final javax.swing.Timer offsetRefreshTimer;
    private final javax.swing.Timer urgentPulseTimer;
    private float urgentPulsePhase = 0f;
    private boolean navigating = false;

    private static final class ExamListData {
        final List<ExamConfig> exams;
        final Map<Integer, Integer> attemptsByExam;
        final Map<Integer, Double> bestPctByExam;
        final Map<String, Double> bestPctBySubject;
        final LocalDateTime serverNow;

        ExamListData(List<ExamConfig> exams, Map<Integer, Integer> attemptsByExam,
                     Map<Integer, Double> bestPctByExam, Map<String, Double> bestPctBySubject,
                     LocalDateTime serverNow) {
            this.exams = exams;
            this.attemptsByExam = attemptsByExam;
            this.bestPctByExam = bestPctByExam;
            this.bestPctBySubject = bestPctBySubject;
            this.serverNow = serverNow;
        }
    }

    public SubjectSelectionUI() {
        this.currentUser = ExamStore.getCurrentUserId();

        setTitle("Available Exams - Online Exam System");
        setSize(1020, 690);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.55f);

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

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);

        JLabel title = new JLabel("Available Examinations");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel("Schedules are synchronized with database server time; cards unlock automatically when an exam opens");
        sub.setFont(UITheme.font(Font.PLAIN, 12.5f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));

        headLeft.add(title);
        headLeft.add(sub);

        UITheme.ModernButton backBtn = UITheme.secondaryButton("\u2190 Dashboard");
        backBtn.addActionListener(e -> navigateTo(StudentDashboard::new));

        topBar.add(headLeft, BorderLayout.WEST);
        topBar.add(backBtn, BorderLayout.EAST);

        contentWrap = new DashboardComponents.TransitionContainer(new BorderLayout());

        gridPanel = new UITheme.ScrollablePanel(new GridLayout(0, 2, 20, 20));
        gridPanel.setOpaque(false);
        gridPanel.setBorder(new EmptyBorder(24, 32, 28, 32));

        // Skeleton shimmer placeholders while SwingWorker loads exams off the EDT
        for (int i = 0; i < 4; i++) {
            gridPanel.add(new DashboardComponents.SkeletonShimmerPanel(4));
        }

        JScrollPane scrollPane = new JScrollPane(
                gridPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(scrollPane);

        contentWrap.add(scrollPane, BorderLayout.CENTER);

        root.add(topBar, BorderLayout.NORTH);
        root.add(contentWrap, BorderLayout.CENTER);
        add(root);

        countdownTimer = new javax.swing.Timer(1000, e -> tickCountdowns());
        countdownTimer.setCoalesce(true);

        offsetRefreshTimer = new javax.swing.Timer(OFFSET_REFRESH_MS, e -> refreshServerOffsetAsync());
        offsetRefreshTimer.setCoalesce(true);

        urgentPulseTimer = new javax.swing.Timer(45, e -> {
            if (!isShowing()) {
                ((javax.swing.Timer) e.getSource()).stop();
                return;
            }
            urgentPulsePhase += 0.14f;
            if (urgentPulsePhase > (float) (Math.PI * 2.0)) {
                urgentPulsePhase -= (float) (Math.PI * 2.0);
            }
            boolean anyUrgent = false;
            for (LiveExamCard c : liveCards) {
                if (c.isUrgentPulsing()) {
                    anyUrgent = true;
                    c.statusChip.setPulsePhase(urgentPulsePhase);
                }
            }
            if (!anyUrgent) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });
        urgentPulseTimer.setCoalesce(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopAllTimers();
            }
        });

        setVisible(true);
        loadExamsAsync();
    }

    private void stopAllTimers() {
        if (countdownTimer.isRunning()) countdownTimer.stop();
        if (offsetRefreshTimer.isRunning()) offsetRefreshTimer.stop();
        if (urgentPulseTimer.isRunning()) urgentPulseTimer.stop();
    }

    @Override
    public void dispose() {
        stopAllTimers();
        super.dispose();
    }

    private void syncServerClock(LocalDateTime dbNow) {
        if (dbNow == null) return;
        this.serverTimeAtSync = dbNow;
        this.syncMonoNanos = System.nanoTime();
        long serverEpochMs = dbNow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.serverOffsetMs = serverEpochMs - System.currentTimeMillis();
    }

    /**
     * Returns the current estimated DB server time using a monotonic elapsed delta
     * since the last DB server sync (immune to local PC clock changes).
     */
    private LocalDateTime getEstimatedServerNow() {
        if (serverTimeAtSync == null) {
            return null;
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - syncMonoNanos);
        return serverTimeAtSync.plusNanos(elapsedNanos);
    }

    private void refreshServerOffsetAsync() {
        new SwingWorker<LocalDateTime, Void>() {
            @Override
            protected LocalDateTime doInBackground() {
                return DBConnection.getServerTime();
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    LocalDateTime fresh = get();
                    if (fresh != null) {
                        syncServerClock(fresh);
                        tickCountdowns();
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadExamsAsync() {
        // Rule 4: All DB access off the EDT
        SwingWorker<ExamListData, Void> worker = new SwingWorker<ExamListData, Void>() {
            @Override
            protected ExamListData doInBackground() {
                if (ExamStore.getQuestionCount() == 0) {
                    QuestionDAO.loadQuestionsFromDB();
                }
                LocalDateTime serverNow = DBConnection.getServerTime();
                List<ExamConfig> exams = ExamDAO.getActiveExams();
                Map<Integer, Integer> attemptCounts = ExamDAO.getAttemptCountsByUser(currentUser);
                List<AttemptDAO.AttemptDetail> past = AttemptDAO.getAttemptDetails(null, currentUser, false);
                Map<Integer, Double> bestByExam = new HashMap<>();
                Map<String, Double> bestBySubject = new HashMap<>();
                for (AttemptDAO.AttemptDetail d : past) {
                    if (d.examId != null && d.examId > 0) {
                        bestByExam.merge(d.examId, d.percentage, Math::max);
                    }
                    if (d.subjectName != null) {
                        bestBySubject.merge(d.subjectName, d.percentage, Math::max);
                    }
                }
                return new ExamListData(exams, attemptCounts, bestByExam, bestBySubject, serverNow);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    ExamListData data = get();
                    syncServerClock(data.serverNow);
                    renderExams(data);
                    if (!countdownTimer.isRunning()) countdownTimer.start();
                    if (!offsetRefreshTimer.isRunning()) offsetRefreshTimer.start();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    UITheme.showToast(SubjectSelectionUI.this, "Load Error", "Failed to load exams from MySQL.", UITheme.ToastType.ERROR);
                }
            }
        };
        worker.execute();
    }

    private void renderExams(ExamListData data) {
        gridPanel.removeAll();
        liveCards.clear();
        List<GlassPanel> cardsToAnimate = new ArrayList<>();

        if (data.exams == null || data.exams.isEmpty()) {
            GlassPanel emptyCard = new GlassPanel(16);
            emptyCard.setSpotlightEnabled(false);
            emptyCard.setLayout(new BorderLayout());
            emptyCard.setBorder(new EmptyBorder(40, 24, 40, 24));
            JLabel empty = new JLabel("No active exams available right now.", SwingConstants.CENTER);
            empty.setFont(UITheme.FONT_HEADING);
            empty.setForeground(UITheme.TEXT_MUTED);
            emptyCard.add(empty, BorderLayout.CENTER);
            gridPanel.add(emptyCard);
        } else {
            Color[] accents = {UITheme.PRIMARY, UITheme.VIOLET, UITheme.SKY, UITheme.PINK};
            Color[] softs = {UITheme.PRIMARY_SOFT, UITheme.VIOLET_SOFT, UITheme.SKY_SOFT, UITheme.PINK_SOFT};
            LocalDateTime now = getEstimatedServerNow();

            for (int i = 0; i < data.exams.size(); i++) {
                ExamConfig cfg = data.exams.get(i);
                Color accent = accents[i % accents.length];
                Color soft = softs[i % softs.length];
                int used = data.attemptsByExam.getOrDefault(cfg.id, 0);
                Double best = data.bestPctByExam.containsKey(cfg.id)
                        ? data.bestPctByExam.get(cfg.id)
                        : data.bestPctBySubject.get(cfg.subjectName);

                LiveExamCard liveCard = buildExamCard(cfg, used, best, accent, soft, now);
                liveCards.add(liveCard);
                cardsToAnimate.add(liveCard.card);
                gridPanel.add(liveCard.card);
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
        DashboardComponents.staggerRiseFade(cardsToAnimate, 65);
        ensureUrgentPulseRunning();
    }

    private void tickCountdowns() {
        if (!isDisplayable() || liveCards.isEmpty()) return;
        LocalDateTime now = getEstimatedServerNow();
        if (now == null) return;

        boolean anyFlipped = false;
        for (LiveExamCard c : liveCards) {
            if (c.updateForServerTime(now, true)) {
                anyFlipped = true;
            }
        }
        if (anyFlipped) {
            // Confirm flipped state against fresh DB server time off the EDT
            refreshServerOffsetAsync();
        }
        ensureUrgentPulseRunning();
    }

    private void ensureUrgentPulseRunning() {
        for (LiveExamCard c : liveCards) {
            if (c.isUrgentPulsing()) {
                if (!urgentPulseTimer.isRunning()) {
                    urgentPulseTimer.start();
                }
                return;
            }
        }
    }

    private LiveExamCard buildExamCard(
            ExamConfig cfg, int attemptsUsed, Double personalBest,
            Color accent, Color softBg, LocalDateTime initialServerNow) {

        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(accent);
        card.setLeftAccentColor(accent);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(18, 22, 20, 22));

        // Top row: Icon + Title + Subject pill + Live Status Chip + Circular Personal Best Ring
        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JPanel leftHead = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftHead.setOpaque(false);
        leftHead.add(new UITheme.VectorIcon(UITheme.IconType.BOOK, accent, softBg, 38));

        JPanel nameStack = new JPanel();
        nameStack.setLayout(new BoxLayout(nameStack, BoxLayout.Y_AXIS));
        nameStack.setOpaque(false);

        JLabel titleLbl = new JLabel(cfg.title != null ? cfg.title : (cfg.subjectName + " Exam"));
        titleLbl.setFont(UITheme.font(Font.BOLD, 16.5f));
        titleLbl.setForeground(UITheme.TEXT_PRIMARY);

        JPanel subMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        subMeta.setOpaque(false);
        subMeta.add(UITheme.createBadge(cfg.subjectName, softBg, accent));

        LiveStatusChip statusChip = new LiveStatusChip();
        subMeta.add(statusChip);

        if (cfg.negativeMarking > 0.0) {
            subMeta.add(UITheme.createBadge(
                    "-" + ExamConfig.formatMarks(cfg.negativeMarking) + " Neg",
                    UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
        }
        nameStack.add(titleLbl);
        nameStack.add(subMeta);
        leftHead.add(nameStack);

        DashboardComponents.CircularBestRing ring = new DashboardComponents.CircularBestRing(personalBest);

        topRow.add(leftHead, BorderLayout.CENTER);
        topRow.add(ring, BorderLayout.EAST);

        // Middle: Description + spec pills (questions, duration, pass mark, attempts used / max)
        JPanel midPanel = new JPanel(new BorderLayout(0, 10));
        midPanel.setOpaque(false);

        String descText = (cfg.description != null && !cfg.description.trim().isEmpty())
                ? cfg.description.trim()
                : ("Timed " + cfg.subjectName + " assessment with randomized questions and shuffled options.");
        JLabel desc = new JLabel("<html><div style='line-height:1.4;'>" + descText + "</div></html>");
        desc.setFont(UITheme.font(Font.PLAIN, 12f));
        desc.setForeground(UITheme.TEXT_SECONDARY);

        int effDur = cfg.getEffectiveDurationMinutes(cfg.questionCount);
        boolean limitReached = cfg.maxAttempts != null && cfg.maxAttempts > 0 && attemptsUsed >= cfg.maxAttempts;
        String attemptsText = (cfg.maxAttempts != null && cfg.maxAttempts > 0)
                ? ("Attempts " + attemptsUsed + "/" + cfg.maxAttempts)
                : ("Attempts: " + attemptsUsed + " (Unlimited)");

        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        pills.setOpaque(false);
        pills.add(UITheme.createBadge(cfg.questionCount + " Qs", UITheme.PRIMARY_SOFT, UITheme.PRIMARY));
        pills.add(UITheme.createBadge(effDur + " Min", UITheme.SKY_SOFT, UITheme.SKY));
        pills.add(UITheme.createBadge("Pass " + ExamConfig.formatMarks(cfg.passPercentage) + "%",
                UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
        pills.add(UITheme.createBadge(
                attemptsText,
                limitReached ? UITheme.DANGER_SOFT : new Color(241, 245, 249),
                limitReached ? UITheme.DANGER_TEXT : UITheme.TEXT_SECONDARY
        ));

        midPanel.add(desc, BorderLayout.CENTER);
        midPanel.add(pills, BorderLayout.SOUTH);

        // Bottom: Dynamic Start Button
        UITheme.ModernButton startBtn = UITheme.gradientPrimaryButton("Start Exam \u2192");
        startBtn.setPreferredSize(new Dimension(220, 38));

        LiveExamCard holder = new LiveExamCard(cfg, attemptsUsed, accent, card, statusChip, startBtn);
        holder.updateForServerTime(initialServerNow, false);

        startBtn.addActionListener(e -> {
            if (holder.canClickStart()) {
                launchExam(cfg);
            } else {
                UITheme.showToast(SubjectSelectionUI.this, "Exam Unavailable", holder.getBlockedReason(), UITheme.ToastType.WARNING);
            }
        });

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (holder.canClickStart()) {
                    launchExam(cfg);
                } else {
                    UITheme.showToast(SubjectSelectionUI.this, "Exam Unavailable", holder.getBlockedReason(), UITheme.ToastType.WARNING);
                }
            }
        });

        card.add(topRow, BorderLayout.NORTH);
        card.add(midPanel, BorderLayout.CENTER);
        card.add(startBtn, BorderLayout.SOUTH);
        return holder;
    }

    private void launchExam(ExamConfig cfg) {
        if (navigating || cfg == null) return;

        // Authoritative check against DB server time + attempts off the EDT
        SwingWorker<ExamDAO.ExamAvailabilityResult, Void> checkWorker = new SwingWorker<ExamDAO.ExamAvailabilityResult, Void>() {
            private boolean hasQuestions = false;

            @Override
            protected ExamDAO.ExamAvailabilityResult doInBackground() {
                ExamDAO.ExamAvailabilityResult res = ExamDAO.canStart(currentUser, cfg.id);
                if (res.canStart) {
                    List<ExamStore.Question> qs = ExamStore.getQuestionsBySubject(cfg.subjectId);
                    if (qs.isEmpty()) {
                        QuestionDAO.loadQuestionsFromDB();
                        qs = ExamStore.getQuestionsBySubject(cfg.subjectId);
                    }
                    hasQuestions = !qs.isEmpty();
                }
                return res;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    ExamDAO.ExamAvailabilityResult res = get();
                    if (res.serverNow != null) {
                        syncServerClock(res.serverNow);
                    }
                    if (!res.canStart) {
                        UITheme.showToast(SubjectSelectionUI.this, "Cannot Start Exam", res.reason, UITheme.ToastType.WARNING);
                        tickCountdowns();
                        return;
                    }
                    if (!hasQuestions) {
                        UITheme.showToast(SubjectSelectionUI.this, "No Questions",
                                "No questions available for " + cfg.subjectName + " yet!", UITheme.ToastType.WARNING);
                        return;
                    }
                    ExamStore.setSelectedExam(res.exam != null ? res.exam : cfg);
                    navigateTo(InstructionsUI::new);
                } catch (Exception ex) {
                    UITheme.showToast(SubjectSelectionUI.this, "Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        };
        checkWorker.execute();
    }

    private void navigateTo(Runnable nextScreen) {
        if (navigating) return;
        navigating = true;
        stopAllTimers();
        contentWrap.transitionOutAndRun(() -> {
            dispose();
            if (nextScreen != null) nextScreen.run();
        });
    }

    // ================= LIVE EXAM CARD CONTROLLER =================

    private static final class LiveExamCard {
        final ExamConfig cfg;
        final int attemptsUsed;
        final Color defaultAccent;
        final DashboardComponents.BentoCard card;
        final LiveStatusChip statusChip;
        final UITheme.ModernButton startBtn;

        private ExamDAO.ExamAvailability lastStatus = null;
        private boolean lastCanStart = false;
        private String lastBlockedReason = "Checking availability...";
        private boolean urgentPulsing = false;

        LiveExamCard(ExamConfig cfg, int attemptsUsed, Color defaultAccent,
                     DashboardComponents.BentoCard card, LiveStatusChip statusChip,
                     UITheme.ModernButton startBtn) {
            this.cfg = cfg;
            this.attemptsUsed = attemptsUsed;
            this.defaultAccent = defaultAccent;
            this.card = card;
            this.statusChip = statusChip;
            this.startBtn = startBtn;
        }

        boolean canClickStart() {
            return lastCanStart;
        }

        String getBlockedReason() {
            return lastBlockedReason;
        }

        boolean isUrgentPulsing() {
            return urgentPulsing;
        }

        /**
         * Updates card state & countdown from the estimated DB server time.
         * Returns true if the availability status flipped (e.g. UPCOMING -> OPEN or OPEN -> CLOSED).
         */
        boolean updateForServerTime(LocalDateTime serverNow, boolean animateOnFlip) {
            ExamDAO.ExamAvailabilityResult res = ExamDAO.evaluateAvailability(cfg, serverNow, attemptsUsed, true);
            boolean statusChanged = (lastStatus != null && lastStatus != res.status);
            lastStatus = res.status;
            lastCanStart = res.canStart;
            lastBlockedReason = res.reason;

            boolean limitReached = res.isAttemptLimitReached();
            urgentPulsing = false;

            if (res.status == ExamDAO.ExamAvailability.UPCOMING) {
                String countdown = ExamConfig.formatCountdown(res.remainingSeconds, false);
                statusChip.configure("Starts in " + countdown, UITheme.WARNING_SOFT, UITheme.WARNING_TEXT, UITheme.WARNING, false);
                startBtn.setText("Starts in " + countdown);
                startBtn.setEnabled(false);
                card.setLeftAccentColor(UITheme.WARNING);
                card.setShadowColor(UITheme.WARNING);
                card.setCursor(Cursor.getDefaultCursor());
            } else if (res.status == ExamDAO.ExamAvailability.CLOSED) {
                String closedDate = cfg.endAt != null ? cfg.endAt.format(ExamConfig.DISPLAY_DT) : "";
                statusChip.configure("Closed on " + closedDate, new Color(241, 245, 249), UITheme.TEXT_MUTED, UITheme.BORDER, false);
                startBtn.setText("Closed");
                startBtn.setEnabled(false);
                card.setLeftAccentColor(UITheme.TEXT_MUTED);
                card.setShadowColor(UITheme.TEXT_MUTED);
                card.setCursor(Cursor.getDefaultCursor());
            } else if (res.status == ExamDAO.ExamAvailability.INACTIVE) {
                statusChip.configure("Inactive", new Color(241, 245, 249), UITheme.TEXT_MUTED, UITheme.BORDER, false);
                startBtn.setText("Unavailable");
                startBtn.setEnabled(false);
                card.setLeftAccentColor(UITheme.TEXT_MUTED);
                card.setShadowColor(UITheme.TEXT_MUTED);
                card.setCursor(Cursor.getDefaultCursor());
            } else if (limitReached) {
                String limText = "Attempts " + attemptsUsed + "/" + cfg.maxAttempts;
                statusChip.configure(limText, UITheme.DANGER_SOFT, UITheme.DANGER_TEXT, UITheme.DANGER, false);
                startBtn.setText(limText);
                startBtn.setEnabled(false);
                card.setLeftAccentColor(UITheme.TEXT_MUTED);
                card.setShadowColor(UITheme.TEXT_MUTED);
                card.setCursor(Cursor.getDefaultCursor());
            } else {
                // OPEN & allowed to start
                if (cfg.endAt != null && res.remainingSeconds >= 0) {
                    boolean urgent = res.remainingSeconds > 0 && res.remainingSeconds < URGENT_THRESHOLD_SECONDS;
                    urgentPulsing = urgent;
                    String countdown = ExamConfig.formatCountdown(res.remainingSeconds, !urgent);
                    if (urgent) {
                        statusChip.configure("Ends in " + countdown, UITheme.DANGER_SOFT, UITheme.DANGER_TEXT, UITheme.DANGER, true);
                    } else {
                        statusChip.configure("Ends in " + countdown, UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT, UITheme.SUCCESS, false);
                    }
                } else {
                    statusChip.configure("Open Now", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT, UITheme.SUCCESS, false);
                }
                startBtn.setText("Start Exam \u2192");
                startBtn.setEnabled(true);
                card.setLeftAccentColor(defaultAccent);
                card.setShadowColor(defaultAccent);
                card.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            if (statusChanged && animateOnFlip) {
                playStateFlipAnimation();
            }
            return statusChanged;
        }

        private void playStateFlipAnimation() {
            Anim.animate(320, p -> {
                float bounce = (float) Math.sin(p * Math.PI);
                card.setScale(1.0f + 0.025f * bounce);
                card.repaint();
            }, () -> {
                card.setScale(1.0f);
                card.repaint();
            });
        }
    }

    // ================= PULSING LIGHT-THEME STATUS CHIP =================

    private static class LiveStatusChip extends JLabel {
        private Color fill = UITheme.SUCCESS_SOFT;
        private Color border = UITheme.SUCCESS;
        private boolean pulsing = false;
        private float pulsePhase = 0f;

        LiveStatusChip() {
            setOpaque(false);
            setFont(UITheme.font(Font.BOLD, 11f));
            setBorder(new EmptyBorder(3, 9, 3, 9));
        }

        void configure(String text, Color fill, Color fg, Color border, boolean pulsing) {
            setText(text);
            this.fill = fill;
            this.border = border;
            this.pulsing = pulsing;
            setForeground(fg);
            repaint();
        }

        void setPulsePhase(float phase) {
            this.pulsePhase = phase;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            int w = getWidth();
            int h = getHeight();
            Shape pill = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, h, h);

            if (pulsing) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
                float t = pulse * 0.85f;
                int r = Math.round(UITheme.DANGER_SOFT.getRed() + (254 - UITheme.DANGER_SOFT.getRed()) * t);
                int gr = Math.round(UITheme.DANGER_SOFT.getGreen() + (202 - UITheme.DANGER_SOFT.getGreen()) * t);
                int b = Math.round(UITheme.DANGER_SOFT.getBlue() + (202 - UITheme.DANGER_SOFT.getBlue()) * t);
                g2.setColor(new Color(r, gr, b));
                g2.fill(pill);
                g2.setColor(Anim.withAlpha(UITheme.DANGER, Math.round(110 + 135 * pulse)));
                g2.setStroke(new BasicStroke(1.1f + 0.8f * pulse));
                g2.draw(pill);
            } else {
                g2.setColor(fill);
                g2.fill(pill);
                g2.setColor(Anim.withAlpha(border, 95));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(pill);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
