package com.onlineexam.ui.admin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.ProctorDAO;
import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.dao.SessionDAO;
import com.onlineexam.dao.UserDAO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.AuthComponents;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.LoginUI;
import com.onlineexam.ui.PasswordStrengthMeter;
import com.onlineexam.ui.UITheme;

public class AdminDashboard extends JFrame {

    private final List<AttemptDAO.AttemptDetail> allAttempts;
    private final List<AttemptDAO.AttemptDetail> displayedRecentAttempts = new ArrayList<>();
    private Map<Integer, ProctorDAO.RiskSummary> attemptRiskMap = new HashMap<>();
    private String lastSubmissionQuery = "";
    private DefaultTableModel submissionsModel;

    public AdminDashboard() {
        String adminId = ExamStore.getCurrentUserId();
        String displayAdmin = ExamStore.getCurrentDisplayName();

        setTitle("Admin Console - Online Examination System");
        setSize(1220, 760);
        setMinimumSize(new Dimension(1020, 650));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        if (ExamStore.getQuestionCount() == 0) {
            QuestionDAO.loadQuestionsFromDB();
        }
        this.allAttempts = AttemptDAO.getAttemptDetails(null, null, false);
        Map<Integer, String> subjectsMap = AttemptDAO.getAvailableSubjects();

        int totalQuestions = ExamStore.getQuestionCount();
        int subjectCount = subjectsMap.size();
        int totalAttempts = allAttempts.size();
        double avgPct = 0.0;
        int passedCount = 0;
        if (!allAttempts.isEmpty()) {
            double sum = 0.0;
            for (AttemptDAO.AttemptDetail d : allAttempts) {
                sum += d.percentage;
                if ("Pass".equalsIgnoreCase(d.status)) passedCount++;
            }
            avgPct = AttemptDAO.roundTwoDecimals(sum / allAttempts.size());
        }
        double passRate = totalAttempts > 0 ? AttemptDAO.roundTwoDecimals((passedCount * 100.0) / totalAttempts) : 0.0;

        // Full-window animated Pastel Aurora Light background
        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.55f);

        // ================= LIGHT FROSTED-WHITE SIDEBAR =================
        DashboardComponents.FrostedSidebar sidebar = new DashboardComponents.FrostedSidebar(
                "Admin Console",
                displayAdmin,
                "Administrator",
                () -> {
                    ExamStore.clearSession();
                    dispose();
                    new LoginUI();
                }
        );

        sidebar.addNavItem(UITheme.IconType.DASHBOARD, "Dashboard Overview", true, () -> {
            dispose();
            new AdminDashboard();
        });
        sidebar.addNavItem(UITheme.IconType.CHECKLIST, "Exams", false, () -> {
            sidebar.setActiveIndex(0);
            new ExamManagementUI();
        });
        sidebar.addNavItem(UITheme.IconType.PLUS, "Add New Question", false, () -> {
            sidebar.setActiveIndex(0);
            new AddQuestionUI();
        });
        sidebar.addNavItem(UITheme.IconType.BOOK, "Question Bank", false, () -> {
            sidebar.setActiveIndex(0);
            new ViewQuestionsUI();
        });
        sidebar.addNavItem(UITheme.IconType.USERS, "Students Attempted", false, () -> {
            sidebar.setActiveIndex(0);
            showStudentsAttemptedDetails();
        });
        sidebar.addNavItem(UITheme.IconType.CHART, "Performance & Ranking", false, () -> {
            sidebar.setActiveIndex(0);
            new PerformanceAnalysisUI();
        });
        sidebar.addNavItem(UITheme.IconType.LIST, "Question Analytics", false, () -> {
            sidebar.setActiveIndex(0);
            new QuestionAnalyticsUI();
        });
        sidebar.addNavItem(UITheme.IconType.FLAG, "Proctoring Log", false, () -> {
            sidebar.setActiveIndex(0);
            new ProctorLogUI();
        });
        sidebar.addNavItem(UITheme.IconType.AWARD, "Verify Certificate", false, () -> {
            sidebar.setActiveIndex(0);
            new com.onlineexam.ui.admin.VerifyCertificateUI();
        });
        sidebar.addNavItem(UITheme.IconType.USER, "Admin Profile", false, () -> {
            sidebar.setActiveIndex(0);
            showAdminProfileDialog();
        });

        // ================= RIGHT WORKSPACE =================
        JPanel mainArea = new JPanel(new BorderLayout());
        mainArea.setOpaque(false);

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
        topBar.setBorder(new EmptyBorder(14, 24, 14, 24));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        JLabel title = new JLabel("Administrator Overview");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Live examination metrics, question bank capacity, and student submissions");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));
        titleBlock.add(title);
        titleBlock.add(sub);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        topActions.setOpaque(false);

        UITheme.ModernButton refreshBtn = UITheme.secondaryButton("Refresh Data");
        refreshBtn.addActionListener(e -> {
            QuestionDAO.loadQuestionsFromDB();
            dispose();
            new AdminDashboard();
        });
        UITheme.ModernButton addBtn = UITheme.gradientPrimaryButton("+ Add Question");
        addBtn.addActionListener(e -> new AddQuestionUI());

        topActions.add(refreshBtn);
        topActions.add(addBtn);

        topBar.add(titleBlock, BorderLayout.WEST);
        topBar.add(topActions, BorderLayout.EAST);

        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(16, 22, 20, 22));

        // ---- TOP STACK: 4 BENTO KPI TILES (COUNT-UP) + LIVE ACTIVITY STRIP ----
        JPanel topStack = new JPanel(new BorderLayout(0, 12));
        topStack.setOpaque(false);

        JLabel kpiQVal = new JLabel("0");
        JLabel kpiAttVal = new JLabel("0");
        JLabel kpiAvgVal = new JLabel("0.0%");
        JLabel kpiPassVal = new JLabel("0.0%");

        DashboardComponents.BentoCard kpi1 = buildBentoKpi(
                "TOTAL QUESTIONS", kpiQVal, "Across " + subjectCount + " active subjects",
                UITheme.PRIMARY, UITheme.PRIMARY_SOFT, UITheme.IconType.BOOK);
        DashboardComponents.BentoCard kpi2 = buildBentoKpi(
                "TOTAL ATTEMPTS", kpiAttVal, "Stored in MySQL database",
                UITheme.VIOLET, UITheme.VIOLET_SOFT, UITheme.IconType.USERS);
        DashboardComponents.BentoCard kpi3 = buildBentoKpi(
                "AVERAGE SCORE", kpiAvgVal, "All subjects combined",
                UITheme.SKY, UITheme.SKY_SOFT, UITheme.IconType.CHART);
        DashboardComponents.BentoCard kpi4 = buildBentoKpi(
                "PASS RATE", kpiPassVal, passedCount + " of " + totalAttempts + " passed",
                UITheme.SUCCESS, UITheme.SUCCESS_SOFT, UITheme.IconType.CHECKLIST);

        JPanel metricsRow = new JPanel(new GridLayout(1, 4, 14, 0));
        metricsRow.setOpaque(false);
        metricsRow.add(kpi1);
        metricsRow.add(kpi2);
        metricsRow.add(kpi3);
        metricsRow.add(kpi4);

        // Live Activity Strip with Pulsing Green Dots
        LiveActivityStrip liveStrip = new LiveActivityStrip(allAttempts, totalQuestions);

        topStack.add(metricsRow, BorderLayout.NORTH);
        topStack.add(liveStrip, BorderLayout.SOUTH);

        // ---- LOWER SPLIT: SUBMISSIONS TABLE CARD (LEFT) + CAPACITY & CHART (RIGHT) ----
        JPanel lowerSplit = new JPanel(new BorderLayout(16, 0));
        lowerSplit.setOpaque(false);

        GlassPanel tableCard = new GlassPanel(16);
        tableCard.setSpotlightEnabled(false);
        tableCard.setLayout(new BorderLayout(0, 12));
        tableCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel tableHeader = new JPanel(new BorderLayout(12, 0));
        tableHeader.setOpaque(false);

        JPanel titleStack = new JPanel(new GridLayout(2, 1, 0, 2));
        titleStack.setOpaque(false);
        JLabel tblTitle = new JLabel("Recent Exam Submissions");
        tblTitle.setFont(UITheme.font(Font.BOLD, 15f));
        tblTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel tblSub = new JLabel("Live audit log of submitted assessments");
        tblSub.setFont(UITheme.font(Font.PLAIN, 11.5f));
        tblSub.setForeground(UITheme.TEXT_MUTED);
        titleStack.add(tblTitle);
        titleStack.add(tblSub);

        JPanel searchWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        searchWrap.setOpaque(false);
        UITheme.RoundedTextField searchInput = UITheme.createTextField("Filter student or subject...");
        searchInput.setPreferredSize(new Dimension(210, 34));
        searchInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { populateSubmissionsTable(searchInput.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { populateSubmissionsTable(searchInput.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { populateSubmissionsTable(searchInput.getText()); }
        });

        UITheme.ModernButton fullAnalyticsBtn = UITheme.secondaryButton("Full Analytics");
        fullAnalyticsBtn.setPreferredSize(new Dimension(118, 34));
        fullAnalyticsBtn.addActionListener(e -> new PerformanceAnalysisUI());

        searchWrap.add(searchInput);
        searchWrap.add(fullAnalyticsBtn);

        tableHeader.add(titleStack, BorderLayout.WEST);
        tableHeader.add(searchWrap, BorderLayout.EAST);

        tableCard.add(tableHeader, BorderLayout.NORTH);
        tableCard.add(buildRecentSubmissionsView(), BorderLayout.CENTER);

        // Right Column: Question Bank Capacity + Upgraded Bar Chart
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        rightCol.setPreferredSize(new Dimension(320, 420));

        DashboardComponents.BentoCard bankCard = new DashboardComponents.BentoCard(16);
        bankCard.setShadowColor(UITheme.PRIMARY);
        bankCard.setLayout(new BorderLayout(0, 12));
        bankCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        bankCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel bankHead = new JPanel(new BorderLayout());
        bankHead.setOpaque(false);
        JLabel bankTitle = new JLabel("Question Bank Capacity");
        bankTitle.setFont(UITheme.font(Font.BOLD, 14f));
        bankTitle.setForeground(UITheme.TEXT_PRIMARY);
        JButton manageLink = UITheme.createLinkButton("", "Manage \u2192");
        manageLink.addActionListener(e -> new ViewQuestionsUI());
        bankHead.add(bankTitle, BorderLayout.WEST);
        bankHead.add(manageLink, BorderLayout.EAST);

        JPanel barsPanel = new JPanel();
        barsPanel.setLayout(new BoxLayout(barsPanel, BoxLayout.Y_AXIS));
        barsPanel.setOpaque(false);

        Color[] barColors = {UITheme.PRIMARY, UITheme.VIOLET, UITheme.SKY, UITheme.PINK};
        int cIdx = 0;
        for (Map.Entry<Integer, String> entry : subjectsMap.entrySet()) {
            int count = ExamStore.getQuestionsBySubject(entry.getKey()).size();
            JPanel barRow = UITheme.createProgressBarRow(entry.getValue(), count, 30, barColors[cIdx % barColors.length]);
            barRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            barsPanel.add(barRow);
            barsPanel.add(Box.createVerticalStrut(10));
            cIdx++;
        }

        bankCard.add(bankHead, BorderLayout.NORTH);
        bankCard.add(barsPanel, BorderLayout.CENTER);

        DashboardComponents.BentoCard chartCard = new DashboardComponents.BentoCard(16);
        chartCard.setShadowColor(UITheme.VIOLET);
        chartCard.setLayout(new BorderLayout(0, 6));
        chartCard.setBorder(new EmptyBorder(14, 16, 12, 16));
        chartCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel chartTitle = new JLabel("Avg. Score by Subject");
        chartTitle.setFont(UITheme.font(Font.BOLD, 14f));
        chartTitle.setForeground(UITheme.TEXT_PRIMARY);

        UITheme.SubjectBarChartPanel miniChart = new UITheme.SubjectBarChartPanel();
        miniChart.setPreferredSize(new Dimension(280, 175));
        Map<String, Double> averages = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : subjectsMap.entrySet()) {
            double sum = 0.0;
            int cnt = 0;
            for (AttemptDAO.AttemptDetail d : allAttempts) {
                if (entry.getValue().equalsIgnoreCase(d.subjectName)) {
                    sum += d.percentage;
                    cnt++;
                }
            }
            averages.put(entry.getValue(), cnt > 0 ? AttemptDAO.roundTwoDecimals(sum / cnt) : 0.0);
        }
        miniChart.setData(averages);

        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(miniChart, BorderLayout.CENTER);

        rightCol.add(bankCard);
        rightCol.add(Box.createVerticalStrut(14));
        rightCol.add(chartCard);

        JPanel leftCol = new JPanel(new BorderLayout(0, 14));
        leftCol.setOpaque(false);
        leftCol.add(tableCard, BorderLayout.CENTER);
        GlassPanel sessionsCard = buildActiveSessionsCard();
        leftCol.add(sessionsCard, BorderLayout.SOUTH);

        lowerSplit.add(leftCol, BorderLayout.CENTER);
        lowerSplit.add(rightCol, BorderLayout.EAST);

        workspace.add(topStack, BorderLayout.NORTH);
        workspace.add(lowerSplit, BorderLayout.CENTER);

        mainArea.add(topBar, BorderLayout.NORTH);
        mainArea.add(workspace, BorderLayout.CENTER);

        root.add(sidebar, BorderLayout.WEST);
        root.add(mainArea, BorderLayout.CENTER);

        add(root);
        setVisible(true);

        // Staggered bento card entrance + KPI count-up animations
        List<GlassPanel> animated = new ArrayList<>();
        animated.add(kpi1);
        animated.add(kpi2);
        animated.add(kpi3);
        animated.add(kpi4);
        animated.add(liveStrip);
        animated.add(tableCard);
        animated.add(sessionsCard);
        animated.add(bankCard);
        animated.add(chartCard);

        final int fQ = totalQuestions;
        final int fAtt = totalAttempts;
        final double fAvg = avgPct;
        final double fPass = passRate;
        SwingUtilities.invokeLater(() -> {
            DashboardComponents.staggerRiseFade(animated, 55);
            Anim.countUp(kpiQVal, 0, fQ, 700, "%d");
            Anim.countUp(kpiAttVal, 0, fAtt, 720, "%d");
            Anim.countUp(kpiAvgVal, 0.0, fAvg, 820, "%.1f%%");
            Anim.countUp(kpiPassVal, 0.0, fPass, 840, "%.1f%%");
        });
    }

    private DashboardComponents.BentoCard buildBentoKpi(
            String labelText, JLabel valLabel, String subText,
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

        valLabel.setFont(UITheme.font(Font.BOLD, 28f));
        valLabel.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel(subText);
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_MUTED);

        card.add(top, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        card.add(sub, BorderLayout.SOUTH);
        return card;
    }

    private GlassPanel buildActiveSessionsCard() {
        GlassPanel card = new GlassPanel(16);
        card.setSpotlightEnabled(false);
        card.setShadowColor(UITheme.SKY);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(12, 18, 12, 18));
        card.setPreferredSize(new Dimension(620, 178));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);

        JPanel headLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headLeft.setOpaque(false);
        JLabel title = new JLabel("Active Student Sessions");
        title.setFont(UITheme.font(Font.BOLD, 14f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel countBadge = UITheme.createBadge("0 active", UITheme.SKY_SOFT, UITheme.SKY);
        headLeft.add(title);
        headLeft.add(countBadge);

        JPanel headBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headBtns.setOpaque(false);

        UITheme.ModernButton refreshBtn = UITheme.secondaryButton("Refresh");
        refreshBtn.setFont(UITheme.font(Font.BOLD, 11.5f));
        refreshBtn.setBorder(new EmptyBorder(4, 10, 4, 10));

        UITheme.ModernButton unlockBtn = UITheme.dangerButton("Force Unlock");
        unlockBtn.setFont(UITheme.font(Font.BOLD, 11.5f));
        unlockBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        unlockBtn.setEnabled(false);

        headBtns.add(refreshBtn);
        headBtns.add(unlockBtn);
        head.add(headLeft, BorderLayout.WEST);
        head.add(headBtns, BorderLayout.EAST);

        String[] cols = {"Student", "Machine / Device", "Status", "Last Heartbeat"};
        DefaultTableModel sessModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        final List<SessionDAO.ActiveSessionInfo> loadedSessions = new ArrayList<>();

        JTable sessTable = new JTable(sessModel);
        UITheme.styleTable(sessTable);
        sessTable.setRowHeight(32);
        sessTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessTable.getColumnModel().getColumn(0).setPreferredWidth(105);
        sessTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        sessTable.getColumnModel().getColumn(2).setPreferredWidth(105);
        sessTable.getColumnModel().getColumn(3).setPreferredWidth(140);

        sessTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel) {
                    JLabel lbl = (JLabel) c;
                    lbl.setHorizontalAlignment((column == 0 || column == 1) ? SwingConstants.LEFT : SwingConstants.CENTER);
                    lbl.setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                c.setFont(UITheme.font(column == 0 ? Font.BOLD : Font.PLAIN, 12f));
                return c;
            }
        });

        sessTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String txt = value != null ? value.toString() : "";
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                if ("In Exam".equalsIgnoreCase(txt)) {
                    cell.add(UITheme.createBadge("In Exam", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT));
                } else if ("Online".equalsIgnoreCase(txt)) {
                    cell.add(UITheme.createBadge("Online", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
                } else {
                    cell.add(UITheme.createBadge("Stale", new Color(241, 245, 249), UITheme.TEXT_MUTED));
                }
                return cell;
            }
        });

        sessTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                unlockBtn.setEnabled(sessTable.getSelectedRow() >= 0 && sessTable.getSelectedRow() < loadedSessions.size());
            }
        });

        Runnable reloadSessions = () -> new SwingWorker<List<SessionDAO.ActiveSessionInfo>, Void>() {
            @Override
            protected List<SessionDAO.ActiveSessionInfo> doInBackground() {
                return SessionDAO.listActiveSessions();
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    List<SessionDAO.ActiveSessionInfo> list = get();
                    loadedSessions.clear();
                    if (list != null) loadedSessions.addAll(list);
                    sessModel.setRowCount(0);
                    int aliveCount = 0;
                    for (SessionDAO.ActiveSessionInfo s : loadedSessions) {
                        if (s.alive) aliveCount++;
                        String statusStr = !s.alive ? "Stale" : (s.inExam ? "In Exam" : "Online");
                        String hbDisplay = s.lastHeartbeat + " (" + s.secondsSinceHeartbeat + "s ago)";
                        sessModel.addRow(new Object[]{s.userId, s.machineInfo, statusStr, hbDisplay});
                    }
                    countBadge.setText(aliveCount + " online / " + loadedSessions.size() + " total");
                    unlockBtn.setEnabled(false);
                } catch (Exception ignored) {}
            }
        }.execute();

        refreshBtn.addActionListener(e -> reloadSessions.run());
        unlockBtn.addActionListener(e -> {
            int row = sessTable.getSelectedRow();
            if (row < 0 || row >= loadedSessions.size()) return;
            SessionDAO.ActiveSessionInfo sel = loadedSessions.get(row);
            boolean confirmed = GlassDialog.showConfirm(
                    this,
                    "Force Unlock Session",
                    "Force unlock account \"" + sel.userId + "\" on " + sel.machineInfo
                            + "?\n\nThis immediately releases any active exam lock so the student can sign in on another device.",
                    "Force Unlock",
                    "Cancel",
                    true
            );
            if (!confirmed) return;

            unlockBtn.setEnabled(false);
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return SessionDAO.forceUnlock(sel.userId);
                }

                @Override
                protected void done() {
                    if (!isDisplayable()) return;
                    UITheme.showToast(AdminDashboard.this, "Account Unlocked",
                            "Released session lock for " + sel.userId + ".", UITheme.ToastType.SUCCESS);
                    reloadSessions.run();
                }
            }.execute();
        });

        card.add(head, BorderLayout.NORTH);
        card.add(UITheme.styleScrollPane(sessTable), BorderLayout.CENTER);
        reloadSessions.run();
        return card;
    }

    private Component buildRecentSubmissionsView() {
        if (allAttempts.isEmpty()) {
            JLabel empty = new JLabel("No exam submissions recorded in MySQL yet.", SwingConstants.CENTER);
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(UITheme.TEXT_MUTED);
            return empty;
        }

        try {
            attemptRiskMap = ProctorDAO.getRiskByAttemptIds();
        } catch (Exception ignored) {}

        String[] columns = {"Student ID", "Subject", "Score", "Percentage", "Grade", "Status", "Flags", "Time", "Date"};
        submissionsModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        populateSubmissionsTable("");

        JTable table = new JTable(submissionsModel);
        UITheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel) {
                    JLabel lbl = (JLabel) c;
                    lbl.setHorizontalAlignment(column == 0 || column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
                    lbl.setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                c.setFont(UITheme.font(column == 4 ? Font.BOLD : Font.PLAIN, 13f));
                return c;
            }
        });
        table.getColumnModel().getColumn(5).setCellRenderer(UITheme.createStatusPillRenderer());

        // Flags / Risk column renderer
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String risk = value != null ? value.toString() : "-";
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));

                if ("High".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("High", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT));
                } else if ("Medium".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("Medium", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
                } else if ("Low".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("Low", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
                } else {
                    cell.add(UITheme.createBadge("-", new Color(241, 245, 249), UITheme.TEXT_MUTED));
                }
                cell.setToolTipText("Click to view proctoring audit log");
                return cell;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && row < displayedRecentAttempts.size()) {
                    AttemptDAO.AttemptDetail d = displayedRecentAttempts.get(row);
                    if (col == 6 || e.getClickCount() == 2) {
                        new ProctorLogUI(d.id);
                    }
                }
            }
        });

        return UITheme.styleScrollPane(table);
    }

    private void populateSubmissionsTable(String query) {
        if (submissionsModel == null) return;
        this.lastSubmissionQuery = query;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        submissionsModel.setRowCount(0);
        displayedRecentAttempts.clear();

        List<AttemptDAO.AttemptDetail> filtered = new ArrayList<>();
        for (AttemptDAO.AttemptDetail d : allAttempts) {
            if (q.isEmpty()
                    || (d.userId != null && d.userId.toLowerCase(Locale.ROOT).contains(q))
                    || (d.subjectName != null && d.subjectName.toLowerCase(Locale.ROOT).contains(q))
                    || (d.examTitle != null && d.examTitle.toLowerCase(Locale.ROOT).contains(q))
                    || (d.status != null && d.status.toLowerCase(Locale.ROOT).contains(q))) {
                filtered.add(d);
            }
        }

        int limit = Math.min(30, filtered.size());
        for (int i = 0; i < limit; i++) {
            AttemptDAO.AttemptDetail d = filtered.get(i);
            displayedRecentAttempts.add(d);
            ProctorDAO.RiskSummary rs = attemptRiskMap != null ? attemptRiskMap.get(d.id) : null;
            String riskStr = rs != null ? rs.riskLevel.getDisplay() : "Low";

            submissionsModel.addRow(new Object[]{
                    d.userId,
                    d.subjectName,
                    ExamConfig.formatMarks(d.marksObtained) + " / " + ExamConfig.formatMarks(d.totalMarks),
                    String.format("%.1f%%", d.percentage),
                    d.grade,
                    d.status,
                    riskStr,
                    ExamStore.formatDuration(d.timeTakenSeconds),
                    d.attemptedDate
            });
        }
    }

    private void showStudentsAttemptedDetails() {
        AttemptDAO.loadAttemptsFromDB();

        int[] subjects = {
                ExamStore.JAVA,
                ExamStore.DBMS,
                ExamStore.SQL,
                ExamStore.APTITUDE
        };

        JTabbedPane tabbedPane = new UITheme.ModernTabbedPane();
        for (int subjectId : subjects) {
            tabbedPane.addTab(ExamStore.getSubjectName(subjectId), buildSubjectReportPanel(subjectId));
        }

        GlassDialog.showCustom(
                this,
                "Students Attempted Report",
                "Subject-wise breakdown of all recorded MySQL exam attempts",
                tabbedPane,
                "Close",
                780,
                520
        );
    }

    private void showAdminProfileDialog() {
        String adminUsername = ExamStore.getCurrentUserId();
        UserDAO.UserProfile profile = UserDAO.getProfile(adminUsername);

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(500, 440));

        // Header with Avatar + Details
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        head.setOpaque(false);

        JComponent avatar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                int size = 52;
                Shape circle = new Ellipse2D.Float(2, 2, size, size);
                g2.setPaint(new GradientPaint(0, 0, UITheme.PRIMARY, size, size, UITheme.VIOLET));
                g2.fill(circle);
                g2.setColor(Color.WHITE);
                g2.setFont(UITheme.font(Font.BOLD, 19f));
                FontMetrics fm = g2.getFontMetrics();
                String init = "AD";
                String disp = ExamStore.getCurrentDisplayName();
                if (disp != null && !disp.isEmpty()) {
                    init = disp.substring(0, Math.min(2, disp.length())).toUpperCase();
                }
                int tx = 2 + (size - fm.stringWidth(init)) / 2;
                int ty = 2 + (size + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(init, tx, ty);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(56, 56));

        JPanel nameStack = new JPanel();
        nameStack.setLayout(new BoxLayout(nameStack, BoxLayout.Y_AXIS));
        nameStack.setOpaque(false);
        JLabel nameLbl = new JLabel(ExamStore.getCurrentDisplayName());
        nameLbl.setFont(UITheme.font(Font.BOLD, 17f));
        nameLbl.setForeground(UITheme.TEXT_PRIMARY);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        badgeRow.setOpaque(false);
        badgeRow.add(UITheme.createBadge("@" + adminUsername, UITheme.PRIMARY_SOFT, UITheme.PRIMARY));
        badgeRow.add(UITheme.createBadge("Administrator", UITheme.VIOLET_SOFT, UITheme.VIOLET));

        nameStack.add(nameLbl);
        nameStack.add(badgeRow);

        head.add(avatar);
        head.add(nameStack);

        // Edit Full Name Row
        JPanel nameCard = new JPanel(new BorderLayout(8, 6));
        nameCard.setOpaque(false);
        nameCard.setBorder(BorderFactory.createTitledBorder("Full Name"));
        JTextField fullNameInput = new JTextField(profile != null && profile.fullName != null ? profile.fullName : "");
        fullNameInput.setFont(UITheme.font(Font.PLAIN, 13f));
        fullNameInput.setPreferredSize(new Dimension(280, 34));
        UITheme.ModernButton saveNameBtn = UITheme.secondaryButton("Save");
        saveNameBtn.setPreferredSize(new Dimension(80, 34));
        saveNameBtn.addActionListener(e -> {
            String newFn = fullNameInput.getText().trim();
            boolean ok = UserDAO.updateFullName(adminUsername, newFn);
            if (ok) {
                ExamStore.setCurrentUserFullName(newFn);
                nameLbl.setText(ExamStore.getCurrentDisplayName());
                avatar.repaint();
                UITheme.showToast(this, "Profile Saved", "Admin display name updated.", UITheme.ToastType.SUCCESS);
            }
        });
        nameCard.add(fullNameInput, BorderLayout.CENTER);
        nameCard.add(saveNameBtn, BorderLayout.EAST);

        // Change Password Section
        JPanel passCard = new JPanel();
        passCard.setLayout(new BoxLayout(passCard, BoxLayout.Y_AXIS));
        passCard.setOpaque(false);
        passCard.setBorder(BorderFactory.createTitledBorder("Change Password (Salted PBKDF2)"));

        JPasswordField curPass = new JPasswordField();
        curPass.setFont(UITheme.font(Font.PLAIN, 13f));
        curPass.putClientProperty("JPasswordField.showRevealButton", true);

        JPasswordField newPass = new JPasswordField();
        newPass.setFont(UITheme.font(Font.PLAIN, 13f));
        newPass.putClientProperty("JPasswordField.showRevealButton", true);

        JPasswordField confPass = new JPasswordField();
        confPass.setFont(UITheme.font(Font.PLAIN, 13f));
        confPass.putClientProperty("JPasswordField.showRevealButton", true);

        PasswordStrengthMeter meter = new PasswordStrengthMeter();

        AuthComponents.InlineValidationLabel valLbl = new AuthComponents.InlineValidationLabel();

        newPass.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                meter.setPassword(new String(newPass.getPassword()));
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        UITheme.ModernButton updatePassBtn = UITheme.gradientPrimaryButton("Update Password");
        updatePassBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        updatePassBtn.addActionListener(e -> {
            String op = new String(curPass.getPassword()).trim();
            String np = new String(newPass.getPassword()).trim();
            String cp = new String(confPass.getPassword()).trim();

            if (op.isEmpty()) {
                valLbl.showError("Current password is required.");
                return;
            }
            if (np.length() < 8) {
                valLbl.showError("New password must be at least 8 characters.");
                return;
            }
            if (!np.equals(cp)) {
                valLbl.showError("New passwords do not match.");
                return;
            }

            UserDAO.ChangePasswordResult res = UserDAO.changePassword(adminUsername, op, np);
            if (res.success) {
                curPass.setText("");
                newPass.setText("");
                confPass.setText("");
                meter.setPassword("");
                valLbl.showSuccess("Password changed successfully! Stored as PBKDF2.");
                UITheme.showToast(this, "Security Updated", "Admin password updated.", UITheme.ToastType.SUCCESS);
            } else {
                valLbl.showError(res.message);
            }
        });

        passCard.add(new JLabel("Current Password:"));
        passCard.add(curPass);
        passCard.add(Box.createVerticalStrut(6));
        passCard.add(new JLabel("New Password (min 8 chars):"));
        passCard.add(newPass);
        passCard.add(meter);
        passCard.add(Box.createVerticalStrut(4));
        passCard.add(new JLabel("Confirm New Password:"));
        passCard.add(confPass);
        passCard.add(Box.createVerticalStrut(6));
        passCard.add(valLbl);
        passCard.add(Box.createVerticalStrut(8));
        passCard.add(updatePassBtn);

        JPanel contentStack = new JPanel();
        contentStack.setLayout(new BoxLayout(contentStack, BoxLayout.Y_AXIS));
        contentStack.setOpaque(false);
        contentStack.add(nameCard);
        contentStack.add(Box.createVerticalStrut(10));
        contentStack.add(passCard);

        panel.add(head, BorderLayout.NORTH);
        panel.add(contentStack, BorderLayout.CENTER);

        GlassDialog.showCustom(
                this,
                "Admin Profile & Security",
                "Update your administrator name and password with PBKDF2 security",
                panel,
                "Close",
                540,
                480
        );
    }

    private JPanel buildSubjectReportPanel(int subjectId) {
        List<AttemptDAO.AttemptDetail> records = new ArrayList<>();
        for (AttemptDAO.AttemptDetail d : allAttempts) {
            if (d.subjectId == subjectId) {
                records.add(d);
            }
        }
        String subjectName = ExamStore.getSubjectName(subjectId);

        GlassPanel root = new GlassPanel(14);
        root.setSpotlightEnabled(false);
        root.setLayout(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Subject: " + subjectName);
        title.setFont(UITheme.font(Font.BOLD, 14f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel count = UITheme.createBadge("Total Attempts: " + records.size(), UITheme.PRIMARY_SOFT, UITheme.PRIMARY);

        header.add(title, BorderLayout.WEST);
        header.add(count, BorderLayout.EAST);

        if (records.isEmpty()) {
            JLabel emptyLabel = new JLabel("No students attempted this subject yet.", SwingConstants.CENTER);
            emptyLabel.setFont(UITheme.FONT_BODY);
            emptyLabel.setForeground(UITheme.TEXT_MUTED);
            root.add(header, BorderLayout.NORTH);
            root.add(emptyLabel, BorderLayout.CENTER);
            return root;
        }

        String[] columns = {"S.No", "User ID", "Marks", "Percentage", "Flags", "Exam Date"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        Map<Integer, ProctorDAO.RiskSummary> subRiskMap = null;
        try {
            subRiskMap = ProctorDAO.getRiskByAttemptIds();
        } catch (Exception ignored) {}

        int rowNo = 1;
        for (AttemptDAO.AttemptDetail record : records) {
            ProctorDAO.RiskSummary rs = subRiskMap != null ? subRiskMap.get(record.id) : null;
            String riskStr = rs != null ? rs.riskLevel.getDisplay() : "Low";
            model.addRow(new Object[]{
                    rowNo++,
                    record.userId,
                    ExamConfig.formatMarks(record.marksObtained) + " / " + ExamConfig.formatMarks(record.totalMarks),
                    String.format("%.1f%%", record.percentage),
                    riskStr,
                    record.attemptedDate
            });
        }

        JTable table = new JTable(model);
        UITheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel) {
                    ((JLabel) c).setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
                }
                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                if (!isSelected && (column == 2 || column == 3) && row >= 0 && row < records.size()) {
                    AttemptDAO.AttemptDetail rec = records.get(row);
                    if (rec.percentage >= 70.0) c.setForeground(UITheme.SUCCESS_TEXT);
                    else if (rec.passed) c.setForeground(UITheme.WARNING_TEXT);
                    else c.setForeground(UITheme.DANGER_TEXT);
                }
                return c;
            }
        });

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String risk = value != null ? value.toString() : "-";
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));

                if ("High".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("High", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT));
                } else if ("Medium".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("Medium", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
                } else if ("Low".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("Low", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
                } else {
                    cell.add(UITheme.createBadge("-", new Color(241, 245, 249), UITheme.TEXT_MUTED));
                }
                cell.setToolTipText("Click to view proctoring events");
                return cell;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && row < records.size()) {
                    AttemptDAO.AttemptDetail rec = records.get(row);
                    if (col == 4 || e.getClickCount() == 2) {
                        new ProctorLogUI(rec.id);
                    }
                }
            }
        });

        root.add(header, BorderLayout.NORTH);
        root.add(UITheme.styleScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    // ================= LIVE ACTIVITY STRIP WITH PULSING GREEN DOTS =================

    private static class LiveActivityStrip extends GlassPanel {
        private float pulsePhase = 0f;
        private final Timer pulseTimer;

        LiveActivityStrip(List<AttemptDAO.AttemptDetail> attempts, int totalQuestions) {
            super(12);
            setSpotlightEnabled(false);
            setShadowColor(UITheme.SUCCESS);
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(9, 16, 11, 16));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            left.setOpaque(false);
            left.add(createPulseDot());

            JLabel liveTag = UITheme.createBadge("LIVE ACTIVITY", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT);
            left.add(liveTag);

            String feedText;
            if (attempts != null && !attempts.isEmpty()) {
                AttemptDAO.AttemptDetail latest = attempts.get(0);
                feedText = "Latest submission: " + latest.userId + " scored "
                        + ExamConfig.formatMarks(latest.marksObtained) + "/" + ExamConfig.formatMarks(latest.totalMarks)
                        + String.format(" (%.1f%%) in %s", latest.percentage, latest.examTitle)
                        + "  \u2022  " + latest.attemptedDate;
            } else {
                feedText = "MySQL online and listening  \u2022  " + totalQuestions + " questions active in bank";
            }
            JLabel feedLbl = new JLabel(feedText);
            feedLbl.setFont(UITheme.font(Font.PLAIN, 12f));
            feedLbl.setForeground(UITheme.TEXT_SECONDARY);
            left.add(feedLbl);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            right.add(createPulseDot());
            JLabel dbLbl = new JLabel("MySQL Synced");
            dbLbl.setFont(UITheme.font(Font.BOLD, 11.5f));
            dbLbl.setForeground(UITheme.SUCCESS_TEXT);
            right.add(dbLbl);

            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);

            pulseTimer = new Timer(33, e -> {
                if (!isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                pulsePhase += 0.10f;
                if (pulsePhase > (float) (Math.PI * 2.0)) {
                    pulsePhase -= (float) (Math.PI * 2.0);
                }
                repaint();
            });
            pulseTimer.setCoalesce(true);

            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        if (!pulseTimer.isRunning()) pulseTimer.start();
                    } else {
                        pulseTimer.stop();
                    }
                }
            });
        }

        private JComponent createPulseDot() {
            JComponent dot = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.configureGraphics(g2);
                    float wave = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
                    float cx = getWidth() / 2f;
                    float cy = getHeight() / 2f;

                    float haloR = 4.5f + 3.5f * wave;
                    int haloAlpha = Math.round(95 * (1f - wave * 0.65f));
                    g2.setColor(Anim.withAlpha(UITheme.SUCCESS, haloAlpha));
                    g2.fill(new Ellipse2D.Float(cx - haloR, cy - haloR, haloR * 2f, haloR * 2f));

                    g2.setColor(UITheme.SUCCESS);
                    g2.fill(new Ellipse2D.Float(cx - 3.6f, cy - 3.6f, 7.2f, 7.2f));
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(18, 18));
            dot.setOpaque(false);
            return dot;
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (isShowing() && !pulseTimer.isRunning()) pulseTimer.start();
        }

        @Override
        public void removeNotify() {
            pulseTimer.stop();
            super.removeNotify();
        }
    }
}
