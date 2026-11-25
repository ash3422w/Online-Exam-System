package com.onlineexam.ui.student;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.onlineexam.dao.AchievementService;
import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.DBConnection;
import com.onlineexam.dao.ExamDAO;
import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.dao.SessionHeartbeat;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.LoginUI;
import com.onlineexam.ui.UITheme;

public class StudentDashboard extends JFrame {

    private final String studentId;
    private final DashboardComponents.TransitionContainer workspaceContainer;
    private final DashboardComponents.FrostedSidebar sidebar;
    private JPanel dashboardTopHeader;
    private JScrollPane dashboardScroll;
    private final JLabel greetingLabel;
    private final JLabel subGreetingLabel;
    private List<AttemptDAO.AttemptDetail> loadedAttempts = new ArrayList<>();
    private boolean navigating = false;
    private LocalDateTime serverTimeAtSync;
    private long syncMonoNanos;
    private final List<UpcomingSlot> upcomingSlots = new ArrayList<>();
    private javax.swing.Timer upcomingCountdownTimer;
    private javax.swing.Timer serverSyncTimer;

    // Task 10: Top bar streak chip with flame animation
    private JComponent streakChip;
    private JLabel streakTextLabel;
    private JComponent streakFlameGlyph;
    private javax.swing.Timer streakFlickerTimer;
    private float streakFlickerScale = 1.0f;
    private int currentStreakCount = 0;

    private static final class UpcomingSlot {
        final ExamConfig exam;
        final JLabel countdownBadge;
        final UITheme.RoundedPanel tile;
        boolean opened = false;

        UpcomingSlot(ExamConfig exam, JLabel countdownBadge, UITheme.RoundedPanel tile) {
            this.exam = exam;
            this.countdownBadge = countdownBadge;
            this.tile = tile;
        }
    }

    private static final class DashboardData {
        final AttemptDAO.StudentPerformanceSummary summary;
        final List<AttemptDAO.AttemptDetail> myAttempts;
        final List<ExamConfig> activeExams;
        final List<ExamConfig> upcomingExams;
        final LocalDateTime serverNow;
        final Map<Integer, Integer> attemptCountsByExam;
        final Map<Integer, Integer> availableQuestionsBySubject;
        final Map<String, Double> bestBySubject;
        final Map<Integer, Double> bestByExam;
        final AchievementService.StreakInfo streakInfo;
        final List<AchievementService.Achievement> latestBadges;

        DashboardData(
                AttemptDAO.StudentPerformanceSummary summary,
                List<AttemptDAO.AttemptDetail> myAttempts,
                List<ExamConfig> activeExams,
                List<ExamConfig> upcomingExams,
                LocalDateTime serverNow,
                Map<Integer, Integer> attemptCountsByExam,
                Map<Integer, Integer> availableQuestionsBySubject,
                Map<String, Double> bestBySubject,
                Map<Integer, Double> bestByExam,
                AchievementService.StreakInfo streakInfo,
                List<AchievementService.Achievement> latestBadges) {
            this.summary = summary;
            this.myAttempts = myAttempts;
            this.activeExams = activeExams;
            this.upcomingExams = upcomingExams;
            this.serverNow = serverNow;
            this.attemptCountsByExam = attemptCountsByExam;
            this.availableQuestionsBySubject = availableQuestionsBySubject;
            this.bestBySubject = bestBySubject;
            this.bestByExam = bestByExam;
            this.streakInfo = streakInfo;
            this.latestBadges = latestBadges;
        }
    }

    public StudentDashboard() {
        this.studentId = ExamStore.getCurrentUserId();
        String displayStudent = ExamStore.getCurrentDisplayName();

        setTitle("Student Portal - Online Examination System");
        setSize(1220, 760);
        setMinimumSize(new Dimension(1020, 660));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Full-window animated Pastel Aurora Light mesh-gradient background
        AuroraBackground root = new AuroraBackground(new BorderLayout());

        // ================= LIGHT FROSTED-WHITE SIDEBAR =================
        this.sidebar = new DashboardComponents.FrostedSidebar(
                "Student Portal",
                displayStudent,
                "Candidate Account",
                () -> navigateTo(() -> SessionHeartbeat.logoutAsync(LoginUI::new))
        );

        sidebar.addNavItem(UITheme.IconType.DASHBOARD, "Dashboard", true, this::showDashboardView);
        sidebar.addNavItem(UITheme.IconType.PLAY, "Start New Exam", false, () -> navigateTo(SubjectSelectionUI::new));
        sidebar.addNavItem(UITheme.IconType.AWARD, "Latest Exam Result", false, () -> {
            if (ExamStore.hasValidExamResultForCurrentUser()) {
                navigateTo(ResultUI::new);
                return;
            }
            List<AttemptDAO.AttemptDetail> attempts = getLatestAttemptsForUser();
            if (attempts != null && !attempts.isEmpty()) {
                AttemptDAO.AttemptDetail latest = attempts.get(0);
                navigateTo(() -> new ResultUI(latest));
                return;
            }
            sidebar.setActiveIndex(0);
            UITheme.showToast(this, "No Exam Results Yet", "Complete an exam first to view your score result.", UITheme.ToastType.INFO);
        });
        sidebar.addNavItem(UITheme.IconType.CHECKLIST, "Review Latest Answers", false, () -> {
            if (ExamStore.hasValidExamResultForCurrentUser()) {
                navigateTo(ReviewUI::new);
                return;
            }
            List<AttemptDAO.AttemptDetail> attempts = getLatestAttemptsForUser();
            if (attempts != null && !attempts.isEmpty()) {
                final AttemptDAO.AttemptDetail chosen = attempts.get(0);
                navigateTo(() -> new ReviewUI(chosen.id, chosen.subjectName, chosen.attemptedDate, chosen.score, chosen.totalQuestions, true));
                return;
            }
            sidebar.setActiveIndex(0);
            UITheme.showToast(this, "No Attempts Yet", "Complete an exam first to review your submitted answers.", UITheme.ToastType.INFO);
        });
        sidebar.addNavItem(UITheme.IconType.CHART, "Performance History", false, () -> navigateTo(StudentPerformanceUI::new));
        sidebar.addNavItem(UITheme.IconType.TROPHY, "Achievements", false, this::showAchievementsView);
        sidebar.addNavItem(UITheme.IconType.USER, "My Profile", false, this::showProfileView);

        // ================= MAIN WORKSPACE AREA =================
        workspaceContainer = new DashboardComponents.TransitionContainer(new BorderLayout(0, 14));
        workspaceContainer.setBorder(new EmptyBorder(20, 24, 18, 24));

        // Top Greeting Row (fades in smoothly)
        JPanel topHeader = new JPanel(new BorderLayout(16, 0));
        topHeader.setOpaque(false);

        JPanel greetStack = new JPanel();
        greetStack.setLayout(new BoxLayout(greetStack, BoxLayout.Y_AXIS));
        greetStack.setOpaque(false);

        greetingLabel = new JLabel(DashboardComponents.timeOfDayGreeting(displayStudent));
        greetingLabel.setFont(UITheme.font(Font.BOLD, 24f));
        greetingLabel.setForeground(UITheme.TEXT_PRIMARY);

        subGreetingLabel = new JLabel("Track your progress, launch timed assessments, or double-click any attempt to review answers.");
        subGreetingLabel.setFont(UITheme.font(Font.PLAIN, 12.5f));
        subGreetingLabel.setForeground(UITheme.TEXT_SECONDARY);
        subGreetingLabel.setBorder(new EmptyBorder(3, 0, 0, 0));

        greetStack.add(greetingLabel);
        greetStack.add(subGreetingLabel);

        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        topBtns.setOpaque(false);

        streakChip = buildStreakChip();

        UITheme.ModernButton histBtn = UITheme.secondaryButton("Full History");
        histBtn.addActionListener(e -> navigateTo(StudentPerformanceUI::new));

        UITheme.ModernButton startExamBtn = UITheme.gradientPrimaryButton("Start New Exam \u2192");
        startExamBtn.addActionListener(e -> navigateTo(SubjectSelectionUI::new));

        topBtns.add(streakChip);
        topBtns.add(histBtn);
        topBtns.add(startExamBtn);

        topHeader.add(greetStack, BorderLayout.WEST);
        topHeader.add(topBtns, BorderLayout.EAST);

        // Show skeleton shimmer placeholders immediately while SwingWorker loads MySQL data
        this.dashboardTopHeader = topHeader;
        JPanel skeletonGrid = buildSkeletonBentoGrid();
        workspaceContainer.add(topHeader, BorderLayout.NORTH);
        workspaceContainer.add(skeletonGrid, BorderLayout.CENTER);

        root.add(sidebar, BorderLayout.WEST);
        root.add(workspaceContainer, BorderLayout.CENTER);

        add(root);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopDashboardTimers();
            }
        });
        setVisible(true);

        // Fade in greeting immediately & start non-blocking SwingWorker data load
        SwingUtilities.invokeLater(() -> {
            Anim.fadeIn(greetingLabel, 480);
            Anim.fadeIn(subGreetingLabel, 560);
            loadDashboardDataAsync(skeletonGrid);
        });
    }

    private void stopDashboardTimers() {
        if (upcomingCountdownTimer != null && upcomingCountdownTimer.isRunning()) {
            upcomingCountdownTimer.stop();
        }
        if (serverSyncTimer != null && serverSyncTimer.isRunning()) {
            serverSyncTimer.stop();
        }
        stopStreakFlicker();
    }

    @Override
    public void dispose() {
        stopDashboardTimers();
        super.dispose();
    }

    private void syncServerClock(LocalDateTime dbNow) {
        if (dbNow != null) {
            serverTimeAtSync = dbNow;
            syncMonoNanos = System.nanoTime();
        }
    }

    private LocalDateTime getEstimatedServerNow() {
        if (serverTimeAtSync == null) return null;
        return serverTimeAtSync.plusNanos(Math.max(0L, System.nanoTime() - syncMonoNanos));
    }

    private List<AttemptDAO.AttemptDetail> getLatestAttemptsForUser() {
        if (loadedAttempts != null && !loadedAttempts.isEmpty()) {
            return loadedAttempts;
        }
        loadedAttempts = AttemptDAO.getAttemptDetails(null, studentId, false);
        return loadedAttempts;
    }

    private void showAchievementsView() {
        if (sidebar != null) {
            sidebar.setActiveIndex(5);
        }
        workspaceContainer.removeAll();
        AchievementsPanel achievementsPanel = new AchievementsPanel(this::showDashboardView);
        workspaceContainer.add(achievementsPanel, BorderLayout.CENTER);
        workspaceContainer.revalidate();
        workspaceContainer.repaint();
    }

    private void showProfileView() {
        if (sidebar != null) {
            sidebar.setActiveIndex(6);
        }
        workspaceContainer.removeAll();
        ProfilePanel profilePanel = new ProfilePanel(this::showDashboardView);
        workspaceContainer.add(profilePanel, BorderLayout.CENTER);
        workspaceContainer.revalidate();
        workspaceContainer.repaint();
    }

    private void showDashboardView() {
        if (sidebar != null) {
            sidebar.setActiveIndex(0);
        }
        workspaceContainer.removeAll();
        if (dashboardTopHeader != null) {
            greetingLabel.setText(DashboardComponents.timeOfDayGreeting(ExamStore.getCurrentDisplayName()));
            workspaceContainer.add(dashboardTopHeader, BorderLayout.NORTH);
        }
        if (dashboardScroll != null) {
            workspaceContainer.add(dashboardScroll, BorderLayout.CENTER);
        }
        workspaceContainer.revalidate();
        workspaceContainer.repaint();
    }

    private void navigateTo(Runnable openNextFrame) {
        if (navigating) return;
        navigating = true;
        stopDashboardTimers();
        workspaceContainer.transitionOutAndRun(() -> {
            dispose();
            if (openNextFrame != null) {
                openNextFrame.run();
            }
        });
    }

    // ================= SKELETON SHIMMER PLACEHOLDER GRID =================

    private JPanel buildSkeletonBentoGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Row 0: Hero Skeleton + Sparkline Skeleton
        gbc.gridy = 0;
        gbc.weighty = 0.28;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 0.50;
        grid.add(new DashboardComponents.SkeletonShimmerPanel(4), gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.50;
        grid.add(new DashboardComponents.SkeletonShimmerPanel(4), gbc);

        // Row 1: 4 KPI Skeleton Tiles
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.25;
        gbc.weighty = 0.20;
        for (int i = 0; i < 4; i++) {
            gbc.gridx = i;
            grid.add(new DashboardComponents.SkeletonShimmerPanel(3), gbc);
        }

        // Row 2: Available Exams Skeleton + Recent Attempts Skeleton
        gbc.gridy = 2;
        gbc.weighty = 0.52;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 0.44;
        grid.add(new DashboardComponents.SkeletonShimmerPanel(5), gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.56;
        grid.add(new DashboardComponents.SkeletonShimmerPanel(5), gbc);

        return grid;
    }

    // ================= ASYNC SWINGWORKER DATA LOADER =================

    private void loadDashboardDataAsync(JPanel skeletonGrid) {
        SwingWorker<DashboardData, Void> worker = new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() {
                if (ExamStore.getQuestionCount() == 0) {
                    QuestionDAO.loadQuestionsFromDB();
                }
                AttemptDAO.StudentPerformanceSummary summary =
                        AttemptDAO.getStudentPerformanceSummary(studentId, null);
                List<AttemptDAO.AttemptDetail> myAttempts =
                        AttemptDAO.getAttemptDetails(null, studentId, false);
                LocalDateTime serverNow = DBConnection.getServerTime();
                List<ExamConfig> activeExams = ExamDAO.getActiveExams();
                List<ExamConfig> upcomingExams = ExamDAO.getUpcomingExams(3);
                Map<Integer, Integer> attemptCountsByExam = ExamDAO.getAttemptCountsByUser(studentId);

                Map<Integer, Integer> availBySubj = new LinkedHashMap<>();
                for (ExamConfig cfg : activeExams) {
                    if (!availBySubj.containsKey(cfg.subjectId)) {
                        availBySubj.put(cfg.subjectId, ExamStore.getQuestionsBySubject(cfg.subjectId).size());
                    }
                }

                Map<String, Double> bestBySubject = new HashMap<>();
                Map<Integer, Double> bestByExam = new HashMap<>();
                for (AttemptDAO.AttemptDetail d : myAttempts) {
                    if (d.subjectName != null) {
                        bestBySubject.merge(d.subjectName, d.percentage, Math::max);
                    }
                    if (d.examId != null && d.examId > 0) {
                        bestByExam.merge(d.examId, d.percentage, Math::max);
                    }
                }
                AchievementService.StreakInfo streakInfo = AchievementService.getStreakInfo(studentId);
                List<AchievementService.Achievement> latestBadges = AchievementService.getLatestEarnedBadges(studentId, 3);

                return new DashboardData(
                        summary, myAttempts, activeExams, upcomingExams, serverNow,
                        attemptCountsByExam, availBySubj, bestBySubject, bestByExam,
                        streakInfo, latestBadges);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    DashboardData data = get();
                    syncServerClock(data.serverNow);
                    loadedAttempts = data.myAttempts != null ? data.myAttempts : new ArrayList<>();
                    renderLoadedBentoGrid(skeletonGrid, data);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    UITheme.showToast(StudentDashboard.this, "Load Error", "Failed to load dashboard data from MySQL.", UITheme.ToastType.ERROR);
                }
            }
        };
        worker.execute();
    }

    // ================= POPULATED BENTO GRID (GridBagLayout) =================

    private void renderLoadedBentoGrid(JPanel skeletonGrid, DashboardData data) {
        updateStreakChip(data.streakInfo);
        List<GlassPanel> animatedCards = new ArrayList<>();

        UITheme.ScrollablePanel bentoGrid = new UITheme.ScrollablePanel(new GridBagLayout());
        bentoGrid.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(6, 6, 6, 6);

        // ---- 1. LARGE HERO CARD: "Next Exam" with animated Indigo->Violet->Pink border ----
        DashboardComponents.AnimatedBorderHeroCard heroCard = buildNextExamHeroCard(data);
        animatedCards.add(heroCard);

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 0.50;
        gbc.weighty = 0.28;
        bentoGrid.add(heroCard, gbc);

        // ---- 2. TREND SPARKLINE CARD (draws line first, then area fades in) ----
        DashboardComponents.SparklinePanel sparkline = new DashboardComponents.SparklinePanel();
        DashboardComponents.BentoCard sparkCard = buildSparklineCard(data, sparkline);
        animatedCards.add(sparkCard);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.50;
        bentoGrid.add(sparkCard, gbc);

        // ---- 3. 4 KPI TILES WITH COUNT-UP NUMBERS & COLORED ICON BADGES ----
        JLabel kpiExamsVal = new JLabel("0");
        JLabel kpiAvgVal = new JLabel(data.summary.hasAttempts ? "0.0%" : "-");
        JLabel kpiHighVal = new JLabel(data.summary.hasAttempts ? "0" : "-");
        JLabel kpiRankVal = new JLabel(data.summary.hasAttempts ? "#0" : "N/A");

        DashboardComponents.BentoCard kpi1 = buildKpiTile(
                "EXAMS COMPLETED", kpiExamsVal,
                data.summary.hasAttempts ? "Recorded in MySQL" : "Take your first exam",
                UITheme.PRIMARY, UITheme.PRIMARY_SOFT, UITheme.IconType.BOOK);

        DashboardComponents.BentoCard kpi2 = buildKpiTile(
                "AVERAGE SCORE", kpiAvgVal,
                data.summary.hasAttempts ? ("Grade " + data.summary.grade + " \u2022 " + data.summary.status) : "Pass mark: 40%",
                UITheme.VIOLET, UITheme.VIOLET_SOFT, UITheme.IconType.CHART);

        DashboardComponents.BentoCard kpi3 = buildKpiTile(
                "HIGHEST SCORE", kpiHighVal,
                data.summary.hasAttempts ? ("Lowest score: " + data.summary.lowestScore) : "No high score yet",
                UITheme.SKY, UITheme.SKY_SOFT, UITheme.IconType.CHECKLIST);

        DashboardComponents.BentoCard kpi4 = buildKpiTile(
                "CLASS RANK", kpiRankVal,
                data.summary.hasAttempts ? ("Out of " + data.summary.totalRankedStudents + " students") : "Unranked",
                UITheme.PINK, UITheme.PINK_SOFT, UITheme.IconType.TROPHY);

        animatedCards.add(kpi1);
        animatedCards.add(kpi2);
        animatedCards.add(kpi3);
        animatedCards.add(kpi4);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.25;
        gbc.weighty = 0.19;
        gbc.gridx = 0; bentoGrid.add(kpi1, gbc);
        gbc.gridx = 1; bentoGrid.add(kpi2, gbc);
        gbc.gridx = 2; bentoGrid.add(kpi3, gbc);
        gbc.gridx = 3; bentoGrid.add(kpi4, gbc);

        // ---- 3.5. UPCOMING EXAMS STRIP (next 3 scheduled exams with live DB-server countdowns) ----
        DashboardComponents.BentoCard upcomingStrip = buildUpcomingExamsStrip(data);
        animatedCards.add(upcomingStrip);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        bentoGrid.add(upcomingStrip, gbc);

        // ---- 3.8. RECENT BADGES STRIP (Task 10: latest 3 earned badges) ----
        DashboardComponents.BentoCard badgesStrip = buildRecentBadgesStrip(data);
        animatedCards.add(badgesStrip);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        bentoGrid.add(badgesStrip, gbc);

        // ---- 4. AVAILABLE EXAMS CARD (with Circular Personal Best % Rings) ----
        DashboardComponents.BentoCard examsCard = buildAvailableExamsCard(data);
        animatedCards.add(examsCard);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 0.44;
        gbc.weighty = 0.50;
        bentoGrid.add(examsCard, gbc);

        // ---- 5. RECENT ATTEMPTS TABLE CARD (with row hover highlight & double-click review) ----
        DashboardComponents.BentoCard attemptsCard = buildRecentAttemptsCard(data.myAttempts);
        animatedCards.add(attemptsCard);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.56;
        bentoGrid.add(attemptsCard, gbc);

        JScrollPane scroll = new JScrollPane(
                bentoGrid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(scroll);

        this.dashboardScroll = scroll;
        workspaceContainer.remove(skeletonGrid);
        workspaceContainer.add(scroll, BorderLayout.CENTER);
        workspaceContainer.revalidate();
        workspaceContainer.repaint();

        // Staggered Bento Entrance (each card rises 20px and fades in, 60ms apart)
        DashboardComponents.staggerRiseFade(animatedCards, 60);

        // KPI Count-Up Animations
        Anim.countUp(kpiExamsVal, 0, data.summary.totalAttempts, 700, "%d");
        if (data.summary.hasAttempts) {
            Anim.countUp(kpiAvgVal, 0.0, data.summary.averagePercentage, 820, "%.1f%%");
            Anim.countUp(kpiHighVal, 0, data.summary.highestScore, 740, "%d");
            Anim.countUp(kpiRankVal, Math.max(1, data.summary.totalRankedStudents), data.summary.rank, 760, "#%d");
        }

        // Feed sparkline data slightly after card rise begins so self-draw + area-fade shines
        Timer sparkStart = new Timer(220, e -> {
            ((Timer) e.getSource()).stop();
            List<String> xLabels = new ArrayList<>();
            List<Double> yVals = new ArrayList<>();
            int maxPts = Math.min(8, data.myAttempts.size());
            for (int i = maxPts - 1; i >= 0; i--) {
                AttemptDAO.AttemptDetail d = data.myAttempts.get(i);
                xLabels.add(d.subjectName);
                yVals.add(d.percentage);
            }
            sparkline.setData(xLabels, yVals);
        });
        sparkStart.setRepeats(false);
        sparkStart.start();
    }

    private DashboardComponents.AnimatedBorderHeroCard buildNextExamHeroCard(DashboardData data) {
        DashboardComponents.AnimatedBorderHeroCard hero = new DashboardComponents.AnimatedBorderHeroCard();
        hero.setLayout(new BorderLayout(16, 10));
        hero.setBorder(new EmptyBorder(18, 22, 20, 22));
        hero.setShadowColor(UITheme.PRIMARY);

        // Pick recommended next active exam: prefer an OPEN unattempted exam with attempts left,
        // otherwise lowest personal best among OPEN exams with attempts left.
        ExamConfig recommended = null;
        double lowestBest = Double.MAX_VALUE;
        LocalDateTime now = getEstimatedServerNow();
        for (ExamConfig cfg : data.activeExams) {
            int used = data.attemptCountsByExam.getOrDefault(cfg.id, 0);
            ExamDAO.ExamAvailabilityResult avail = ExamDAO.evaluateAvailability(cfg, now, used, true);
            if (!avail.canStart) {
                continue;
            }
            Double best = data.bestByExam.containsKey(cfg.id)
                    ? data.bestByExam.get(cfg.id)
                    : data.bestBySubject.get(cfg.subjectName);
            if (best == null) {
                recommended = cfg;
                break;
            }
            if (best < lowestBest) {
                lowestBest = best;
                recommended = cfg;
            }
        }
        if (recommended == null && !data.activeExams.isEmpty()) {
            recommended = data.activeExams.get(0);
        }

        final ExamConfig targetExam = recommended;
        int availQs = targetExam != null ? data.availableQuestionsBySubject.getOrDefault(targetExam.subjectId, 0) : 0;
        int qCount = targetExam != null ? Math.min(targetExam.questionCount, Math.max(0, availQs)) : 0;
        int durMins = targetExam != null ? targetExam.getEffectiveDurationMinutes(qCount > 0 ? qCount : targetExam.questionCount) : 20;
        int usedAttempts = targetExam != null ? data.attemptCountsByExam.getOrDefault(targetExam.id, 0) : 0;
        ExamDAO.ExamAvailabilityResult heroAvail = targetExam != null
                ? ExamDAO.evaluateAvailability(targetExam, now, usedAttempts, true)
                : null;
        boolean limitReached = heroAvail != null && heroAvail.isAttemptLimitReached();
        boolean heroBlocked = heroAvail != null && !heroAvail.canStart;
        Double currentBest = targetExam != null
                ? (data.bestByExam.containsKey(targetExam.id)
                ? data.bestByExam.get(targetExam.id)
                : data.bestBySubject.get(targetExam.subjectName))
                : null;

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tagRow.setOpaque(false);
        tagRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel badge = UITheme.createBadge("NEXT EXAM", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
        JLabel statusPill = UITheme.createBadge(
                currentBest == null ? "Recommended" : String.format("Personal Best: %.0f%%", currentBest),
                currentBest == null ? UITheme.VIOLET_SOFT : UITheme.SUCCESS_SOFT,
                currentBest == null ? UITheme.VIOLET : UITheme.SUCCESS_TEXT
        );
        tagRow.add(badge);
        tagRow.add(statusPill);
        if (targetExam != null && targetExam.maxAttempts != null) {
            tagRow.add(UITheme.createBadge(
                    "Attempts " + usedAttempts + "/" + targetExam.maxAttempts,
                    limitReached ? UITheme.DANGER_SOFT : UITheme.SKY_SOFT,
                    limitReached ? UITheme.DANGER_TEXT : UITheme.SKY
            ));
        }

        JLabel subjTitle = new JLabel(targetExam != null ? targetExam.title : "Explore Available Exams");
        subjTitle.setFont(UITheme.font(Font.BOLD, 21f));
        subjTitle.setForeground(UITheme.TEXT_PRIMARY);
        subjTitle.setBorder(new EmptyBorder(10, 0, 4, 0));
        subjTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        String negText = (targetExam != null && targetExam.negativeMarking > 0.0)
                ? ("-" + ExamConfig.formatMarks(targetExam.negativeMarking) + " neg")
                : "No negative marking";
        String metaText = targetExam != null
                ? (targetExam.subjectName + "  \u2022  " + (qCount > 0 ? qCount : targetExam.questionCount)
                + " Qs  \u2022  " + durMins + " min  \u2022  Pass " + ExamConfig.formatMarks(targetExam.passPercentage) + "%  \u2022  " + negText)
                : "Select an exam to start your timed MCQ examination";
        JLabel meta = new JLabel(metaText);
        meta.setFont(UITheme.font(Font.PLAIN, 12.5f));
        meta.setForeground(UITheme.TEXT_SECONDARY);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(tagRow);
        left.add(subjTitle);
        left.add(meta);

        JPanel rightActions = new JPanel(new GridBagLayout());
        rightActions.setOpaque(false);

        String heroBtnText = "Start Exam \u2192";
        if (heroBlocked && heroAvail != null) {
            if (heroAvail.status == ExamDAO.ExamAvailability.UPCOMING) {
                heroBtnText = "Starts Soon";
            } else if (heroAvail.status == ExamDAO.ExamAvailability.CLOSED) {
                heroBtnText = "Closed";
            } else if (limitReached) {
                heroBtnText = "Attempts " + usedAttempts + "/" + targetExam.maxAttempts;
            } else {
                heroBtnText = "Unavailable";
            }
        }
        UITheme.ModernButton startBtn = heroBlocked
                ? UITheme.secondaryButton(heroBtnText)
                : UITheme.gradientPrimaryButton(heroBtnText);
        startBtn.setPreferredSize(new Dimension(heroBlocked ? 172 : 156, 42));
        startBtn.setEnabled(!heroBlocked);
        startBtn.addActionListener(e -> {
            if (targetExam != null) {
                launchExam(targetExam, usedAttempts);
            } else {
                navigateTo(SubjectSelectionUI::new);
            }
        });
        rightActions.add(startBtn);

        hero.add(left, BorderLayout.CENTER);
        hero.add(rightActions, BorderLayout.EAST);
        return hero;
    }

    private DashboardComponents.BentoCard buildUpcomingExamsStrip(DashboardData data) {
        DashboardComponents.BentoCard strip = new DashboardComponents.BentoCard(14);
        strip.setShadowColor(UITheme.WARNING);
        strip.setLeftAccentColor(UITheme.WARNING);
        strip.setLayout(new BorderLayout(12, 8));
        strip.setBorder(new EmptyBorder(12, 18, 12, 18));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel headLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headLeft.setOpaque(false);
        headLeft.add(new UITheme.VectorIcon(UITheme.IconType.CLOCK, UITheme.WARNING_TEXT, UITheme.WARNING_SOFT, 26));
        JLabel title = new JLabel("Upcoming Exams");
        title.setFont(UITheme.font(Font.BOLD, 14f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        headLeft.add(title);
        headLeft.add(UITheme.createBadge("Server Time Synced", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));

        JButton viewAll = UITheme.createLinkButton("", "View All Schedules \u2192");
        viewAll.addActionListener(e -> navigateTo(SubjectSelectionUI::new));

        header.add(headLeft, BorderLayout.WEST);
        header.add(viewAll, BorderLayout.EAST);
        strip.add(header, BorderLayout.NORTH);

        upcomingSlots.clear();
        stopDashboardTimers();

        List<ExamConfig> upcoming = data.upcomingExams != null ? data.upcomingExams : new ArrayList<>();
        if (upcoming.isEmpty()) {
            JLabel none = new JLabel("No upcoming scheduled exams right now — all active exams are open or already completed.");
            none.setFont(UITheme.font(Font.PLAIN, 12f));
            none.setForeground(UITheme.TEXT_MUTED);
            none.setBorder(new EmptyBorder(2, 4, 2, 0));
            strip.add(none, BorderLayout.CENTER);
            return strip;
        }

        JPanel cardsRow = new JPanel(new GridLayout(1, Math.min(3, upcoming.size()), 12, 0));
        cardsRow.setOpaque(false);
        LocalDateTime now = getEstimatedServerNow();

        for (int i = 0; i < Math.min(3, upcoming.size()); i++) {
            ExamConfig cfg = upcoming.get(i);
            UITheme.RoundedPanel tile = new UITheme.RoundedPanel(10, new Color(255, 251, 235, 220), Anim.withAlpha(UITheme.WARNING, 110));
            tile.setLayout(new BorderLayout(8, 4));
            tile.setBorder(new EmptyBorder(9, 12, 9, 12));

            JPanel textStack = new JPanel(new GridLayout(2, 1, 0, 1));
            textStack.setOpaque(false);
            JLabel tLbl = new JLabel(cfg.title);
            tLbl.setFont(UITheme.font(Font.BOLD, 12.5f));
            tLbl.setForeground(UITheme.TEXT_PRIMARY);
            String startWhen = cfg.startAt != null ? cfg.startAt.format(ExamConfig.DISPLAY_DT) : "-";
            JLabel metaLbl = new JLabel(cfg.subjectName + " \u2022 " + startWhen);
            metaLbl.setFont(UITheme.font(Font.PLAIN, 11f));
            metaLbl.setForeground(UITheme.TEXT_SECONDARY);
            textStack.add(tLbl);
            textStack.add(metaLbl);

            long remSec = (cfg.startAt != null && now != null && now.isBefore(cfg.startAt))
                    ? Math.max(0L, Duration.between(now, cfg.startAt).getSeconds())
                    : 0L;
            JLabel cdBadge = UITheme.createBadge(
                    remSec > 0 ? ("Starts in " + ExamConfig.formatCountdown(remSec, false)) : "Open Now \u2192",
                    remSec > 0 ? UITheme.WARNING_SOFT : UITheme.SUCCESS_SOFT,
                    remSec > 0 ? UITheme.WARNING_TEXT : UITheme.SUCCESS_TEXT
            );

            JPanel rightBadgeWrap = new JPanel(new GridBagLayout());
            rightBadgeWrap.setOpaque(false);
            rightBadgeWrap.add(cdBadge);

            tile.add(textStack, BorderLayout.CENTER);
            tile.add(rightBadgeWrap, BorderLayout.EAST);

            UpcomingSlot slot = new UpcomingSlot(cfg, cdBadge, tile);
            slot.opened = (remSec <= 0);
            tile.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (slot.opened) {
                        int used = data.attemptCountsByExam.getOrDefault(cfg.id, 0);
                        launchExam(cfg, used);
                    } else {
                        navigateTo(SubjectSelectionUI::new);
                    }
                }
            });
            tile.setCursor(new Cursor(Cursor.HAND_CURSOR));
            upcomingSlots.add(slot);
            cardsRow.add(tile);
        }

        strip.add(cardsRow, BorderLayout.CENTER);

        // Live 1-second countdown timer + 60-second server clock offset refresh
        upcomingCountdownTimer = new javax.swing.Timer(1000, e -> tickUpcomingStrip());
        upcomingCountdownTimer.setCoalesce(true);
        upcomingCountdownTimer.start();

        serverSyncTimer = new javax.swing.Timer(60_000, e -> refreshDashboardServerTimeAsync());
        serverSyncTimer.setCoalesce(true);
        serverSyncTimer.start();

        return strip;
    }

    // ================= RECENT BADGES STRIP (TASK 10) =================

    private DashboardComponents.BentoCard buildRecentBadgesStrip(DashboardData data) {
        DashboardComponents.BentoCard strip = new DashboardComponents.BentoCard(16);
        strip.setShadowColor(UITheme.GOLD);
        strip.setLayout(new BorderLayout(0, 10));
        strip.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel headLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headLeft.setOpaque(false);

        JLabel icon = new JLabel(UITheme.createSwingIcon(UITheme.IconType.MEDAL, UITheme.GOLD, 16));
        JLabel title = new JLabel("RECENT BADGES & ACHIEVEMENTS");
        title.setFont(UITheme.font(Font.BOLD, 12f));
        title.setForeground(UITheme.TEXT_SECONDARY);
        headLeft.add(icon);
        headLeft.add(title);

        JButton viewAll = UITheme.createLinkButton("", "View All Badges \u2192");
        viewAll.addActionListener(e -> showAchievementsView());

        header.add(headLeft, BorderLayout.WEST);
        header.add(viewAll, BorderLayout.EAST);
        strip.add(header, BorderLayout.NORTH);

        List<AchievementService.Achievement> badges = (data.latestBadges != null) ? data.latestBadges : new ArrayList<>();
        if (badges.isEmpty()) {
            JPanel emptyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
            emptyRow.setOpaque(false);
            JLabel noBadges = new JLabel("No badges unlocked yet — complete exams or achieve milestone scores to earn badges!");
            noBadges.setFont(UITheme.font(Font.PLAIN, 12f));
            noBadges.setForeground(UITheme.TEXT_MUTED);
            JButton browseBtn = UITheme.secondaryButton("Browse Badges");
            browseBtn.setPreferredSize(new Dimension(130, 28));
            browseBtn.setFont(UITheme.font(Font.BOLD, 11f));
            browseBtn.addActionListener(e -> showAchievementsView());
            emptyRow.add(noBadges);
            emptyRow.add(browseBtn);
            strip.add(emptyRow, BorderLayout.CENTER);
            return strip;
        }

        JPanel cardsRow = new JPanel(new GridLayout(1, 3, 12, 0));
        cardsRow.setOpaque(false);

        for (int i = 0; i < 3; i++) {
            if (i < badges.size()) {
                AchievementService.Achievement b = badges.get(i);
                UITheme.RoundedPanel tile = new UITheme.RoundedPanel(12, new Color(255, 255, 255, 240), new Color(251, 191, 36, 120));
                tile.setLayout(new BorderLayout(10, 0));
                tile.setBorder(new EmptyBorder(10, 12, 10, 12));
                tile.setCursor(new Cursor(Cursor.HAND_CURSOR));

                final UITheme.IconType iconType = UITheme.parseIconKey(b.iconKey);
                JComponent glyph = new JComponent() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        UITheme.configureGraphics(g2);
                        int sz = Math.min(getWidth(), getHeight());
                        g2.setColor(new Color(254, 243, 199));
                        g2.fill(new Ellipse2D.Float(0, 0, sz, sz));
                        g2.setColor(new Color(245, 158, 11, 180));
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.draw(new Ellipse2D.Float(0, 0, sz, sz));
                        UITheme.drawIcon(g2, iconType, sz / 2, sz / 2, new Color(180, 83, 9), sz, 0.22f);
                        g2.dispose();
                    }
                };
                glyph.setPreferredSize(new Dimension(42, 42));
                glyph.setMinimumSize(new Dimension(42, 42));
                tile.add(glyph, BorderLayout.WEST);

                JPanel tStack = new JPanel(new GridLayout(2, 1, 0, 2));
                tStack.setOpaque(false);
                JLabel nameL = new JLabel(b.name);
                nameL.setFont(UITheme.font(Font.BOLD, 12.5f));
                nameL.setForeground(UITheme.TEXT_PRIMARY);

                JLabel subL = new JLabel("Unlocked " + b.getFormattedEarnedDate());
                subL.setFont(UITheme.font(Font.PLAIN, 11f));
                subL.setForeground(UITheme.TEXT_MUTED);

                tStack.add(nameL);
                tStack.add(subL);
                tile.add(tStack, BorderLayout.CENTER);

                tile.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        showAchievementsView();
                    }
                });
                cardsRow.add(tile);
            } else {
                UITheme.RoundedPanel tile = new UITheme.RoundedPanel(12, new Color(248, 250, 252, 180), new Color(226, 232, 240));
                tile.setLayout(new BorderLayout());
                tile.setBorder(new EmptyBorder(10, 12, 10, 12));
                JLabel lockedPlaceholder = new JLabel("Locked Badge Slot", SwingConstants.CENTER);
                lockedPlaceholder.setFont(UITheme.font(Font.PLAIN, 11.5f));
                lockedPlaceholder.setForeground(UITheme.TEXT_MUTED);
                tile.add(lockedPlaceholder, BorderLayout.CENTER);
                cardsRow.add(tile);
            }
        }

        strip.add(cardsRow, BorderLayout.CENTER);
        return strip;
    }

    // ================= TOP BAR STREAK CHIP (TASK 10) =================

    private JComponent buildStreakChip() {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                int w = getWidth();
                int h = getHeight();
                boolean alive = (currentStreakCount > 0);
                Color bg = alive ? new Color(254, 243, 199) : new Color(241, 245, 249);
                Color border = alive ? new Color(251, 191, 36) : new Color(226, 232, 240);
                Shape shape = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16);
                g2.setColor(bg);
                g2.fill(shape);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(shape);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.setToolTipText("Daily Study Streak — Click to view Achievements");

        streakFlameGlyph = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                int sz = Math.min(getWidth(), getHeight());
                float scale = (currentStreakCount > 0) ? streakFlickerScale : 1.0f;
                g2.translate((1f - scale) * sz / 2f, (1f - scale) * sz / 2f);
                g2.scale(scale, scale);
                Color flameColor = (currentStreakCount > 0) ? new Color(217, 119, 6) : new Color(148, 163, 184);
                UITheme.drawIcon(g2, UITheme.IconType.FLAME, sz / 2, sz / 2, flameColor, sz, 0.14f);
                g2.dispose();
            }
        };
        streakFlameGlyph.setPreferredSize(new Dimension(18, 18));
        streakFlameGlyph.setMinimumSize(new Dimension(18, 18));

        streakTextLabel = new JLabel("0-day streak");
        streakTextLabel.setFont(UITheme.font(Font.BOLD, 12f));
        streakTextLabel.setForeground(UITheme.TEXT_MUTED);

        chip.add(streakFlameGlyph);
        chip.add(streakTextLabel);

        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAchievementsView();
            }
        });

        return chip;
    }

    private void updateStreakChip(AchievementService.StreakInfo info) {
        if (info == null) return;
        currentStreakCount = info.currentStreak;
        if (currentStreakCount > 0) {
            streakTextLabel.setText(currentStreakCount + (currentStreakCount == 1 ? "-day streak" : "-day streak"));
            streakTextLabel.setForeground(new Color(180, 83, 9));
            startStreakFlicker();
        } else {
            streakTextLabel.setText("0-day streak");
            streakTextLabel.setForeground(UITheme.TEXT_MUTED);
            stopStreakFlicker();
        }
        if (streakChip != null) {
            streakChip.repaint();
        }
    }

    private void startStreakFlicker() {
        if (streakFlickerTimer != null && streakFlickerTimer.isRunning()) {
            return;
        }
        streakFlickerTimer = new javax.swing.Timer(75, e -> {
            if (!isShowing()) {
                stopStreakFlicker();
                return;
            }
            streakFlickerScale = 0.88f + 0.12f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            if (streakFlameGlyph != null) {
                streakFlameGlyph.repaint();
            }
        });
        streakFlickerTimer.start();
    }

    private void stopStreakFlicker() {
        if (streakFlickerTimer != null) {
            streakFlickerTimer.stop();
            streakFlickerTimer = null;
        }
        streakFlickerScale = 1.0f;
        if (streakFlameGlyph != null) {
            streakFlameGlyph.repaint();
        }
    }

    private void tickUpcomingStrip() {
        if (!isDisplayable() || upcomingSlots.isEmpty()) return;
        LocalDateTime now = getEstimatedServerNow();
        if (now == null) return;

        for (UpcomingSlot slot : upcomingSlots) {
            if (slot.exam.startAt == null) continue;
            if (now.isBefore(slot.exam.startAt)) {
                long rem = Math.max(1L, Duration.between(now, slot.exam.startAt).getSeconds());
                slot.countdownBadge.setText("Starts in " + ExamConfig.formatCountdown(rem, false));
                slot.opened = false;
            } else if (!slot.opened) {
                slot.opened = true;
                slot.countdownBadge.setText("Open Now \u2192");
                slot.countdownBadge.setBackground(UITheme.SUCCESS_SOFT);
                slot.countdownBadge.setForeground(UITheme.SUCCESS_TEXT);
                slot.tile.setFillColor(UITheme.SUCCESS_SOFT);
                slot.tile.setBorderColor(UITheme.SUCCESS);
                slot.tile.repaint();
                refreshDashboardServerTimeAsync();
            }
        }
    }

    private void refreshDashboardServerTimeAsync() {
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
                        tickUpcomingStrip();
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private DashboardComponents.BentoCard buildSparklineCard(
            DashboardData data, DashboardComponents.SparklinePanel sparkline) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(UITheme.VIOLET);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(15, 20, 14, 18));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        JLabel title = new JLabel("Score Progression Trend");
        title.setFont(UITheme.font(Font.BOLD, 14.5f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel badge = UITheme.createBadge(
                data.myAttempts.isEmpty() ? "No Data Yet" : ("Last " + Math.min(8, data.myAttempts.size()) + " Exams"),
                UITheme.VIOLET_SOFT,
                UITheme.VIOLET
        );

        head.add(title, BorderLayout.WEST);
        head.add(badge, BorderLayout.EAST);

        card.add(head, BorderLayout.NORTH);
        card.add(sparkline, BorderLayout.CENTER);
        return card;
    }

    private DashboardComponents.BentoCard buildKpiTile(
            String labelText, JLabel valueLabel, String subText,
            Color accent, Color softBg, UITheme.IconType iconType) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(14);
        card.setShadowColor(accent);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(16, 20, 18, 18));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UITheme.font(Font.BOLD, 11f));
        lbl.setForeground(UITheme.TEXT_SECONDARY);

        top.add(lbl, BorderLayout.WEST);
        top.add(new UITheme.VectorIcon(iconType, accent, softBg, 32), BorderLayout.EAST);

        valueLabel.setFont(UITheme.font(Font.BOLD, 28f));
        valueLabel.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel(subText);
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_MUTED);

        card.add(top, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(sub, BorderLayout.SOUTH);
        return card;
    }

    private DashboardComponents.BentoCard buildAvailableExamsCard(DashboardData data) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(UITheme.PRIMARY);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 20, 18, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        JLabel t = new JLabel("Available Exams");
        t.setFont(UITheme.font(Font.BOLD, 15f));
        t.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Personal best % rings, timing, and remaining attempt limits");
        sub.setFont(UITheme.font(Font.PLAIN, 11.5f));
        sub.setForeground(UITheme.TEXT_MUTED);
        titles.add(t);
        titles.add(sub);

        JButton allSubjLink = UITheme.createLinkButton("", "All Exams \u2192");
        allSubjLink.addActionListener(e -> navigateTo(SubjectSelectionUI::new));

        head.add(titles, BorderLayout.WEST);
        head.add(allSubjLink, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
        grid.setOpaque(false);

        int maxShown = Math.min(6, data.activeExams.size());
        LocalDateTime now = getEstimatedServerNow();
        for (int i = 0; i < maxShown; i++) {
            ExamConfig cfg = data.activeExams.get(i);
            int availQs = data.availableQuestionsBySubject.getOrDefault(cfg.subjectId, 0);
            int qCount = Math.min(cfg.questionCount, Math.max(0, availQs));
            int durMins = cfg.getEffectiveDurationMinutes(qCount > 0 ? qCount : cfg.questionCount);
            int used = data.attemptCountsByExam.getOrDefault(cfg.id, 0);
            ExamDAO.ExamAvailabilityResult avail = ExamDAO.evaluateAvailability(cfg, now, used, true);
            boolean limitReached = avail.isAttemptLimitReached();
            boolean blocked = !avail.canStart;
            Double best = data.bestByExam.containsKey(cfg.id)
                    ? data.bestByExam.get(cfg.id)
                    : data.bestBySubject.get(cfg.subjectName);

            DashboardComponents.BentoCard subCard = new DashboardComponents.BentoCard(12);
            subCard.setShadowColor(blocked ? UITheme.TEXT_MUTED : UITheme.PRIMARY);
            subCard.setLayout(new BorderLayout(10, 8));
            subCard.setBorder(new EmptyBorder(12, 14, 14, 14));
            if (!blocked) {
                subCard.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            DashboardComponents.CircularBestRing ring = new DashboardComponents.CircularBestRing(best);

            JPanel info = new JPanel(new GridLayout(3, 1, 0, 1));
            info.setOpaque(false);
            JLabel nameLbl = new JLabel(cfg.title);
            nameLbl.setFont(UITheme.font(Font.BOLD, 13f));
            nameLbl.setForeground(UITheme.TEXT_PRIMARY);
            JLabel metaLbl = new JLabel(cfg.subjectName + " \u2022 " + (qCount > 0 ? qCount : cfg.questionCount) + " Qs \u2022 " + durMins + "m");
            metaLbl.setFont(UITheme.font(Font.PLAIN, 11f));
            metaLbl.setForeground(UITheme.TEXT_MUTED);
            String attMeta;
            if (avail.status == ExamDAO.ExamAvailability.UPCOMING) {
                attMeta = "Starts in " + ExamConfig.formatCountdown(avail.remainingSeconds, false);
            } else if (avail.status == ExamDAO.ExamAvailability.CLOSED) {
                attMeta = "Closed";
            } else if (cfg.maxAttempts != null) {
                attMeta = "Attempts: " + used + "/" + cfg.maxAttempts + " \u2022 Pass " + ExamConfig.formatMarks(cfg.passPercentage) + "%";
            } else {
                attMeta = "Pass " + ExamConfig.formatMarks(cfg.passPercentage) + "%" + (cfg.negativeMarking > 0 ? (" \u2022 -" + ExamConfig.formatMarks(cfg.negativeMarking)) : "");
            }
            JLabel attLbl = new JLabel(attMeta);
            attLbl.setFont(UITheme.font(Font.PLAIN, 10.5f));
            attLbl.setForeground(avail.status == ExamDAO.ExamAvailability.UPCOMING
                    ? UITheme.WARNING_TEXT
                    : (blocked ? UITheme.DANGER_TEXT : UITheme.TEXT_SECONDARY));
            info.add(nameLbl);
            info.add(metaLbl);
            info.add(attLbl);

            JPanel topPart = new JPanel(new BorderLayout(10, 0));
            topPart.setOpaque(false);
            topPart.add(ring, BorderLayout.WEST);
            topPart.add(info, BorderLayout.CENTER);

            String btnTxt = "Start Exam";
            if (avail.status == ExamDAO.ExamAvailability.UPCOMING) {
                btnTxt = "Upcoming";
            } else if (avail.status == ExamDAO.ExamAvailability.CLOSED) {
                btnTxt = "Closed";
            } else if (limitReached) {
                btnTxt = "Attempts " + used + "/" + cfg.maxAttempts;
            }
            UITheme.ModernButton startBtn = UITheme.secondaryButton(btnTxt);
            startBtn.setFont(UITheme.font(Font.BOLD, 11.5f));
            startBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
            startBtn.setEnabled(!blocked);
            startBtn.addActionListener(e -> launchExam(cfg, used));

            if (!blocked) {
                subCard.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        launchExam(cfg, used);
                    }
                });
            }

            subCard.add(topPart, BorderLayout.CENTER);
            subCard.add(startBtn, BorderLayout.SOUTH);
            grid.add(subCard);
        }

        card.add(head, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private void launchExam(ExamConfig cfg, int cachedUsedAttempts) {
        if (cfg == null) {
            navigateTo(SubjectSelectionUI::new);
            return;
        }
        new SwingWorker<ExamDAO.ExamAvailabilityResult, Void>() {
            private int qCount = 0;

            @Override
            protected ExamDAO.ExamAvailabilityResult doInBackground() {
                ExamDAO.ExamAvailabilityResult res = ExamDAO.canStart(studentId, cfg.id);
                if (res.canStart) {
                    if (ExamStore.getQuestionsBySubject(cfg.subjectId).isEmpty()) {
                        QuestionDAO.loadQuestionsFromDB();
                    }
                    qCount = ExamStore.getQuestionsBySubject(cfg.subjectId).size();
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
                        UITheme.showToast(StudentDashboard.this, "Cannot Start Exam",
                                res.reason, UITheme.ToastType.WARNING);
                        return;
                    }
                    if (qCount <= 0) {
                        UITheme.showToast(StudentDashboard.this, "No Questions",
                                "No questions available for " + cfg.subjectName + " yet.", UITheme.ToastType.WARNING);
                        return;
                    }
                    ExamStore.setSelectedExam(res.exam != null ? res.exam : cfg);
                    navigateTo(InstructionsUI::new);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    private DashboardComponents.BentoCard buildRecentAttemptsCard(List<AttemptDAO.AttemptDetail> myAttempts) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(UITheme.SKY);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 20, 18, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        JLabel t = new JLabel("Recent Attempts");
        t.setFont(UITheme.font(Font.BOLD, 15f));
        t.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Double-click any row to inspect your submitted answers");
        sub.setFont(UITheme.font(Font.PLAIN, 11.5f));
        sub.setForeground(UITheme.TEXT_MUTED);
        titles.add(t);
        titles.add(sub);

        JButton viewAllLink = UITheme.createLinkButton("", "View All \u2192");
        viewAllLink.addActionListener(e -> navigateTo(StudentPerformanceUI::new));

        head.add(titles, BorderLayout.WEST);
        head.add(viewAllLink, BorderLayout.EAST);

        card.add(head, BorderLayout.NORTH);
        card.add(buildMyAttemptsTable(myAttempts), BorderLayout.CENTER);
        return card;
    }

    private Component buildMyAttemptsTable(List<AttemptDAO.AttemptDetail> myAttempts) {
        if (myAttempts == null || myAttempts.isEmpty()) {
            JLabel empty = new JLabel("No exam attempts yet. Pick any subject to begin!", SwingConstants.CENTER);
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(UITheme.TEXT_MUTED);
            return empty;
        }

        String[] columns = {"Exam", "Marks", "Percentage", "Grade", "Status", "Time", "Date"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        int limit = Math.min(15, myAttempts.size());
        for (int i = 0; i < limit; i++) {
            AttemptDAO.AttemptDetail d = myAttempts.get(i);
            String examCol = (d.examTitle != null && !d.examTitle.trim().isEmpty()) ? d.examTitle : d.subjectName;
            model.addRow(new Object[]{
                    examCol,
                    ExamConfig.formatMarks(d.marksObtained) + " / " + ExamConfig.formatMarks(d.totalMarks),
                    String.format("%.1f%%", d.percentage),
                    d.grade,
                    d.status,
                    ExamStore.formatDuration(d.timeTakenSeconds),
                    d.attemptedDate
            });
        }

        final int[] hoveredRow = {-1};

        JTable table = new JTable(model);
        UITheme.styleTable(table);
        table.setRowHeight(40);
        table.setToolTipText("Double-click any row to review your saved answers");

        // Row hover tracking
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r != hoveredRow[0]) {
                    hoveredRow[0] = r;
                    table.setCursor(r >= 0 ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredRow[0] != -1) {
                    hoveredRow[0] = -1;
                    table.setCursor(Cursor.getDefaultCursor());
                    table.repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < myAttempts.size()) {
                        AttemptDAO.AttemptDetail d = myAttempts.get(row);
                        navigateTo(() -> new ReviewUI(d.id, d.subjectName, d.attemptedDate, d.score, d.totalQuestions, true));
                    }
                }
            }
        });

        Color hoverTint = new Color(238, 242, 255); // Light Indigo Hover Highlight #EEF2FF

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel) {
                    JLabel lbl = (JLabel) c;
                    lbl.setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
                    lbl.setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                if (isSelected) {
                    c.setBackground(UITheme.PRIMARY_SOFT);
                    c.setForeground(UITheme.TEXT_PRIMARY);
                } else if (row == hoveredRow[0]) {
                    c.setBackground(hoverTint);
                    c.setForeground(UITheme.PRIMARY);
                } else {
                    c.setBackground(row % 2 == 0 ? UITheme.BG_CARD : UITheme.BG_SUBTLE);
                    c.setForeground(UITheme.TEXT_PRIMARY);
                }
                if (column == 3) {
                    c.setFont(UITheme.font(Font.BOLD, 12.5f));
                }
                return c;
            }
        });

        // Status column pill renderer that also respects hovered row highlight
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String text = value == null ? "" : value.toString();
                boolean pass = "Pass".equalsIgnoreCase(text);
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
                cell.setOpaque(true);
                if (isSelected || row == hoveredRow[0]) {
                    cell.setBackground(hoverTint);
                } else {
                    cell.setBackground(row % 2 == 0 ? UITheme.BG_CARD : UITheme.BG_SUBTLE);
                }
                JLabel pill = UITheme.createBadge(
                        pass ? "Pass" : "Fail",
                        pass ? UITheme.SUCCESS_SOFT : UITheme.DANGER_SOFT,
                        pass ? UITheme.SUCCESS_TEXT : UITheme.DANGER_TEXT
                );
                pill.setBorder(new EmptyBorder(2, 10, 2, 10));
                cell.add(pill);
                return cell;
            }
        });

        return UITheme.styleScrollPane(table);
    }
}
