package com.onlineexam.ui.admin;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.ProctorDAO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerformanceAnalysisUI extends JFrame {

    private final JComboBox<SubjectOption> subjectFilterBox;
    private final JLabel statsLabel;
    private final AnimatedPodiumPanel podiumPanel;
    private final UITheme.SubjectBarChartPanel chartPanel;
    private final DifficultyAccuracyPanel difficultyPanel;
    private final AnimatedLeaderboardList leaderboardList;
    private final JPanel attemptsContainer;
    private List<AttemptDAO.StudentLeaderboardEntry> latestLeaderboard = new ArrayList<>();

    public PerformanceAnalysisUI() {
        setTitle("Student Performance Analysis & Ranking");
        setSize(1160, 780);
        setMinimumSize(new Dimension(960, 660));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.42f);

        // Clean white header (no dark mode toggle)
        JPanel header = new JPanel(new BorderLayout(16, 0)) {
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
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(14, 24, 14, 24));

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);
        JLabel title = new JLabel("Performance Analysis & Leaderboard");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Animated Top-3 podium, subject bar analytics with hover tooltips, and live MySQL rankings");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));
        headLeft.add(title);
        headLeft.add(sub);
        header.add(headLeft, BorderLayout.WEST);

        // Controls Strip
        JPanel controlsPanel = new JPanel(new BorderLayout(12, 8)) {
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
        controlsPanel.setOpaque(false);
        controlsPanel.setBorder(new EmptyBorder(10, 24, 10, 24));

        JPanel filterLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterLeft.setOpaque(false);

        JLabel filterLabel = UITheme.createFieldLabel("Filter by Subject:");

        subjectFilterBox = UITheme.createComboBox(null);
        subjectFilterBox.setPreferredSize(new Dimension(180, 36));
        subjectFilterBox.addItem(new SubjectOption(null, "All Subjects"));
        for (Map.Entry<Integer, String> entry : AttemptDAO.getAvailableSubjects().entrySet()) {
            subjectFilterBox.addItem(new SubjectOption(entry.getKey(), entry.getValue()));
        }

        UITheme.ModernButton refreshBtn = UITheme.gradientPrimaryButton("Refresh");
        refreshBtn.setPreferredSize(new Dimension(105, 36));

        UITheme.ModernButton exportCsvBtn = UITheme.successButton("Export CSV");
        exportCsvBtn.setPreferredSize(new Dimension(122, 36));
        exportCsvBtn.addActionListener(e -> exportLeaderboardCsv());

        filterLeft.add(filterLabel);
        filterLeft.add(subjectFilterBox);
        filterLeft.add(refreshBtn);
        filterLeft.add(exportCsvBtn);

        statsLabel = UITheme.createBadge("Loading...", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
        statsLabel.setFont(UITheme.font(Font.BOLD, 12f));

        JPanel statsWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        statsWrap.setOpaque(false);
        statsWrap.add(statsLabel);

        controlsPanel.add(filterLeft, BorderLayout.WEST);
        controlsPanel.add(statsWrap, BorderLayout.EAST);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(header, BorderLayout.NORTH);
        topSection.add(controlsPanel, BorderLayout.SOUTH);

        // Top Visual Analytics Row: Animated Rising Podium + Subject Bar Chart + Difficulty Accuracy Card
        JPanel visualRow = new JPanel(new GridLayout(1, 3, 14, 0));
        visualRow.setOpaque(false);

        DashboardComponents.BentoCard podiumCard = new DashboardComponents.BentoCard(16);
        podiumCard.setShadowColor(UITheme.PRIMARY);
        podiumCard.setLayout(new BorderLayout(0, 8));
        podiumCard.setBorder(new EmptyBorder(14, 16, 12, 16));

        JPanel podHead = new JPanel(new BorderLayout());
        podHead.setOpaque(false);
        JLabel podiumTitle = new JLabel("Top 3 Performers");
        podiumTitle.setFont(UITheme.font(Font.BOLD, 14f));
        podiumTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel podBadge = UITheme.createBadge("Gold \u2022 Silver \u2022 Bronze", UITheme.GOLD_SOFT, UITheme.WARNING_TEXT);
        podHead.add(podiumTitle, BorderLayout.WEST);
        podHead.add(podBadge, BorderLayout.EAST);

        podiumPanel = new AnimatedPodiumPanel();
        podiumCard.add(podHead, BorderLayout.NORTH);
        podiumCard.add(podiumPanel, BorderLayout.CENTER);

        DashboardComponents.BentoCard chartCard = new DashboardComponents.BentoCard(16);
        chartCard.setShadowColor(UITheme.VIOLET);
        chartCard.setLayout(new BorderLayout(0, 6));
        chartCard.setBorder(new EmptyBorder(14, 16, 12, 16));

        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.setOpaque(false);
        JLabel chartTitle = new JLabel("Avg. Score % by Subject");
        chartTitle.setFont(UITheme.font(Font.BOLD, 14f));
        chartTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel passLegend = UITheme.createBadge("Hover Bars", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT);
        chartHeader.add(chartTitle, BorderLayout.WEST);
        chartHeader.add(passLegend, BorderLayout.EAST);

        chartPanel = new UITheme.SubjectBarChartPanel();
        chartPanel.setPreferredSize(new Dimension(340, 195));
        chartCard.add(chartHeader, BorderLayout.NORTH);
        chartCard.add(chartPanel, BorderLayout.CENTER);

        DashboardComponents.BentoCard diffCard = new DashboardComponents.BentoCard(16);
        diffCard.setShadowColor(UITheme.SUCCESS);
        diffCard.setLayout(new BorderLayout(0, 8));
        diffCard.setBorder(new EmptyBorder(14, 16, 12, 16));

        JPanel diffHead = new JPanel(new BorderLayout());
        diffHead.setOpaque(false);
        JLabel diffTitle = new JLabel("Difficulty-Wise Correct %");
        diffTitle.setFont(UITheme.font(Font.BOLD, 14f));
        diffTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel diffBadge = UITheme.createBadge("Accuracy", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT);
        diffHead.add(diffTitle, BorderLayout.WEST);
        diffHead.add(diffBadge, BorderLayout.EAST);

        difficultyPanel = new DifficultyAccuracyPanel();
        diffCard.add(diffHead, BorderLayout.NORTH);
        diffCard.add(difficultyPanel, BorderLayout.CENTER);

        visualRow.add(podiumCard);
        visualRow.add(chartCard);
        visualRow.add(diffCard);

        // Tab 1: Clean Interactive Animated List (Ranks 4+, or full leaderboard list with smooth reorder!)
        GlassPanel leaderboardCard = new GlassPanel(16);
        leaderboardCard.setSpotlightEnabled(false);
        leaderboardCard.setLayout(new BorderLayout(0, 8));
        leaderboardCard.setBorder(new EmptyBorder(14, 16, 16, 16));

        JLabel listNote = new JLabel("Ranks 4+ (with smooth reordering when subject filter changes)");
        listNote.setFont(UITheme.font(Font.PLAIN, 11.5f));
        listNote.setForeground(UITheme.TEXT_MUTED);
        leaderboardCard.add(listNote, BorderLayout.NORTH);

        leaderboardList = new AnimatedLeaderboardList();
        JScrollPane lbScroll = UITheme.styleScrollPane(leaderboardList);
        lbScroll.setOpaque(false);
        lbScroll.getViewport().setOpaque(false);
        leaderboardCard.add(lbScroll, BorderLayout.CENTER);

        // Tab 2: Exam Attempts Table Card
        attemptsContainer = new GlassPanel(16);
        ((GlassPanel) attemptsContainer).setSpotlightEnabled(false);
        attemptsContainer.setLayout(new BorderLayout());
        attemptsContainer.setBorder(new EmptyBorder(14, 16, 16, 16));

        JTabbedPane tabs = new UITheme.ModernTabbedPane();
        tabs.addTab("Leaderboard Rankings", leaderboardCard);
        tabs.addTab("Exam Attempts Analysis", attemptsContainer);

        JPanel contentWrap = new JPanel(new BorderLayout(0, 16));
        contentWrap.setOpaque(false);
        contentWrap.setBorder(new EmptyBorder(16, 22, 18, 22));
        contentWrap.add(visualRow, BorderLayout.NORTH);
        contentWrap.add(tabs, BorderLayout.CENTER);

        subjectFilterBox.addActionListener(e -> loadPerformanceData(false));
        refreshBtn.addActionListener(e -> loadPerformanceData(true));

        root.add(topSection, BorderLayout.NORTH);
        root.add(contentWrap, BorderLayout.CENTER);

        add(root);
        loadPerformanceData(false);
        setVisible(true);
    }

    private void loadPerformanceData(boolean showToast) {
        SubjectOption selected = (SubjectOption) subjectFilterBox.getSelectedItem();
        Integer subjectId = selected == null ? null : selected.subjectId;
        String subjectLabel = selected == null ? "All Subjects" : selected.name;

        List<AttemptDAO.StudentLeaderboardEntry> leaderboard = AttemptDAO.getStudentLeaderboard(subjectId);
        this.latestLeaderboard = leaderboard;
        List<AttemptDAO.AttemptDetail> attempts = AttemptDAO.getAttemptDetails(subjectId, null, true);

        updateSummaryBanner(leaderboard, attempts);
        podiumPanel.setTopThree(leaderboard);
        updateChartData(subjectId);
        loadDifficultyStatsAsync(subjectId);

        // Ranks 4+ go into the animated reorder list (if <= 3 total, show full leaderboard so list isn't blank)
        List<AttemptDAO.StudentLeaderboardEntry> listRows;
        if (leaderboard.size() > 3) {
            listRows = new ArrayList<>(leaderboard.subList(3, leaderboard.size()));
        } else {
            listRows = new ArrayList<>(leaderboard);
        }
        leaderboardList.setEntries(listRows, leaderboard.size() <= 3 && !leaderboard.isEmpty());

        renderAttemptsTable(attempts, subjectLabel);

        if (showToast) {
            UITheme.showToast(this, "Analytics Refreshed", "Loaded latest MySQL data for " + subjectLabel + ".", UITheme.ToastType.SUCCESS);
        }
    }

    private void exportLeaderboardCsv() {
        if (latestLeaderboard == null || latestLeaderboard.isEmpty()) {
            UITheme.showToast(this, "Nothing to Export", "No leaderboard rows available for this filter.", UITheme.ToastType.WARNING);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Student Leaderboard as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("student_leaderboard.csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("Rank,User ID,Exams Attempted,Average Score,Highest Score,Lowest Score,Average Percentage,Grade,Status");
            writer.newLine();
            for (AttemptDAO.StudentLeaderboardEntry e : latestLeaderboard) {
                writer.write(String.format("%d,\"%s\",%d,%.2f,%d,%d,%.2f%%,%s,%s",
                        e.rank,
                        e.userId.replace("\"", "\"\""),
                        e.totalAttempts,
                        e.averageScore,
                        e.highestScore,
                        e.lowestScore,
                        e.averagePercentage,
                        e.grade,
                        e.status));
                writer.newLine();
            }
            UITheme.showToast(this, "CSV Exported", "Saved " + file.getName() + " successfully.", UITheme.ToastType.SUCCESS);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateChartData(Integer selectedSubjectId) {
        Map<String, Double> averages = new LinkedHashMap<>();
        Map<Integer, String> subjects = AttemptDAO.getAvailableSubjects();
        for (Map.Entry<Integer, String> entry : subjects.entrySet()) {
            if (selectedSubjectId != null && !selectedSubjectId.equals(entry.getKey())) {
                continue;
            }
            List<AttemptDAO.AttemptDetail> subAttempts =
                    AttemptDAO.getAttemptDetails(entry.getKey(), null, false);
            if (!subAttempts.isEmpty()) {
                double sum = 0.0;
                for (AttemptDAO.AttemptDetail d : subAttempts) {
                    sum += d.percentage;
                }
                averages.put(entry.getValue(), AttemptDAO.roundTwoDecimals(sum / subAttempts.size()));
            } else {
                averages.put(entry.getValue(), 0.0);
            }
        }
        chartPanel.setData(averages);
    }

    private void updateSummaryBanner(
            List<AttemptDAO.StudentLeaderboardEntry> leaderboard,
            List<AttemptDAO.AttemptDetail> attempts) {
        if (attempts.isEmpty()) {
            statsLabel.setText("Students: 0  \u2022  Attempts: 0  \u2022  Pass Mark: 40%");
            return;
        }
        double totalPct = 0.0;
        int passed = 0;
        for (AttemptDAO.AttemptDetail d : attempts) {
            totalPct += d.percentage;
            if ("Pass".equalsIgnoreCase(d.status)) {
                passed++;
            }
        }
        double avgPct = AttemptDAO.roundTwoDecimals(totalPct / attempts.size());
        double passRate = AttemptDAO.roundTwoDecimals((passed * 100.0) / attempts.size());

        statsLabel.setText(String.format(
                "Students: %d  \u2022  Attempts: %d  \u2022  Avg: %.2f%%  \u2022  Pass Rate: %.2f%%",
                leaderboard.size(), attempts.size(), avgPct, passRate));
    }

    private void renderAttemptsTable(
            List<AttemptDAO.AttemptDetail> attempts, String subjectLabel) {
        attemptsContainer.removeAll();

        if (attempts.isEmpty()) {
            JLabel empty = new JLabel(
                    "No exam attempts recorded for " + subjectLabel + " yet.", SwingConstants.CENTER);
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(UITheme.TEXT_MUTED);
            attemptsContainer.add(empty, BorderLayout.CENTER);
            attemptsContainer.revalidate();
            attemptsContainer.repaint();
            return;
        }

        Map<Integer, ProctorDAO.RiskSummary> riskMap = null;
        try {
            riskMap = ProctorDAO.getRiskByAttemptIds();
        } catch (Exception ignored) {}

        String[] columns = {
                "Rank", "User ID", "Exam", "Marks", "Percentage",
                "Grade", "Status", "Flags", "Time Taken", "Submission", "Exam Date"
        };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (AttemptDAO.AttemptDetail attempt : attempts) {
            String examDisp = (attempt.examTitle != null && !attempt.examTitle.trim().isEmpty())
                    ? attempt.examTitle : attempt.subjectName;
            ProctorDAO.RiskSummary rs = riskMap != null ? riskMap.get(attempt.id) : null;
            String riskStr = rs != null ? rs.riskLevel.getDisplay() : "Low";

            model.addRow(new Object[]{
                    "#" + attempt.rank,
                    attempt.userId,
                    examDisp,
                    ExamConfig.formatMarks(attempt.marksObtained) + " / " + ExamConfig.formatMarks(attempt.totalMarks),
                    String.format("%.2f%%", attempt.percentage),
                    attempt.grade,
                    attempt.status,
                    riskStr,
                    ExamStore.formatDuration(attempt.timeTakenSeconds),
                    attempt.submissionType,
                    attempt.attemptedDate
            });
        }

        JTable table = new JTable(model);
        UITheme.styleTable(table);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, column);

                if (c instanceof JLabel) {
                    JLabel lbl = (JLabel) c;
                    lbl.setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
                    lbl.setBorder(new EmptyBorder(0, 8, 0, 8));
                }

                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                c.setFont(UITheme.font((column == 0 || column == 5) ? Font.BOLD : Font.PLAIN, 13f));

                if (!isSelected && value != null) {
                    String text = value.toString();
                    if (column == 0) {
                        if ("#1".equals(text)) c.setForeground(UITheme.WARNING_TEXT);
                        else if ("#2".equals(text)) c.setForeground(UITheme.PRIMARY);
                        else if ("#3".equals(text)) c.setForeground(UITheme.VIOLET);
                        else c.setForeground(UITheme.TEXT_SECONDARY);
                    } else if (column == 5) {
                        if ("A".equals(text) || "B".equals(text)) c.setForeground(UITheme.SUCCESS_TEXT);
                        else if ("C".equals(text) || "D".equals(text)) c.setForeground(UITheme.WARNING_TEXT);
                        else c.setForeground(UITheme.DANGER_TEXT);
                    }
                }
                return c;
            }
        });

        table.getColumnModel().getColumn(6).setCellRenderer(UITheme.createStatusPillRenderer());

        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
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
                if (row >= 0 && row < attempts.size()) {
                    AttemptDAO.AttemptDetail att = attempts.get(row);
                    if (col == 7 || e.getClickCount() == 2) {
                        new ProctorLogUI(att.id);
                    }
                }
            }
        });
        attemptsContainer.add(UITheme.styleScrollPane(table), BorderLayout.CENTER);
        attemptsContainer.revalidate();
        attemptsContainer.repaint();
    }

    // ================= ANIMATED TOP-3 PODIUM (COLUMNS RISE WITH easeOutBack + MEDALS) =================

    private static class AnimatedPodiumPanel extends JPanel {
        private AttemptDAO.StudentLeaderboardEntry first, second, third;
        private float[] colProgress = {0f, 0f, 0f}; // [0]=#2 Silver, [1]=#1 Gold, [2]=#3 Bronze
        private final Timer[] colTimers = new Timer[3];

        AnimatedPodiumPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(480, 195));
        }

        void setTopThree(List<AttemptDAO.StudentLeaderboardEntry> leaderboard) {
            first = (leaderboard != null && !leaderboard.isEmpty()) ? leaderboard.get(0) : null;
            second = (leaderboard != null && leaderboard.size() > 1) ? leaderboard.get(1) : null;
            third = (leaderboard != null && leaderboard.size() > 2) ? leaderboard.get(2) : null;

            for (int i = 0; i < 3; i++) {
                if (colTimers[i] != null && colTimers[i].isRunning()) colTimers[i].stop();
                colProgress[i] = 0f;
            }

            // Staggered rise: #1 Gold first, #2 Silver, #3 Bronze with easeOutBack!
            int[] order = {1, 0, 2};
            for (int step = 0; step < 3; step++) {
                final int idx = order[step];
                int delay = 40 + step * 95;
                Timer starter = new Timer(delay, e -> {
                    ((Timer) e.getSource()).stop();
                    colTimers[idx] = Anim.animate(560, p -> {
                        colProgress[idx] = Anim.easeOutBack(p);
                        repaint();
                    }, () -> {
                        colProgress[idx] = 1f;
                        repaint();
                    });
                });
                starter.setRepeats(false);
                starter.start();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();

            if (first == null) {
                g2.setFont(UITheme.FONT_BODY);
                g2.setColor(UITheme.TEXT_MUTED);
                String msg = "No ranked students yet.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
                g2.dispose();
                return;
            }

            int gap = 14;
            int colW = Math.min(138, (w - gap * 4) / 3);
            int totalW = colW * 3 + gap * 2;
            int startX = (w - totalW) / 2;
            int baseY = h - 10;

            AttemptDAO.StudentLeaderboardEntry[] entries = {second, first, third};
            int[] targetHeights = {Math.round(h * 0.44f), Math.round(h * 0.58f), Math.round(h * 0.36f)};
            Color[] medalColors = {
                    new Color(148, 163, 184), // Silver
                    new Color(245, 158, 11),  // Gold
                    new Color(205, 127, 50)   // Bronze
            };
            Color[] colTops = {
                    new Color(224, 242, 254), // Soft Sky/Silver
                    new Color(254, 243, 199), // Soft Gold
                    new Color(252, 231, 243)  // Soft Rose/Bronze
            };
            Color[] colBottoms = {
                    new Color(241, 245, 249),
                    new Color(255, 251, 235),
                    new Color(248, 250, 252)
            };
            String[] ranks = {"2", "1", "3"};

            for (int i = 0; i < 3; i++) {
                int x = startX + i * (colW + gap);
                float prog = Math.max(0f, colProgress[i]);
                int colH = Math.max(12, Math.round(targetHeights[i] * prog));
                int topY = baseY - colH;

                // Colored soft shadow beneath podium pedestal
                UITheme.paintColoredShadow(g2, x, topY, colW, colH, 12f, medalColors[i], 3, 2);

                // Pedestal column
                Shape ped = new RoundRectangle2D.Float(x, topY, colW, colH, 12, 12);
                g2.setPaint(new LinearGradientPaint(x, topY, x, baseY,
                        new float[]{0f, 1f}, new Color[]{colTops[i], colBottoms[i]}));
                g2.fill(ped);

                g2.setColor(Anim.withAlpha(medalColors[i], 130));
                g2.setStroke(new BasicStroke(1.3f));
                g2.draw(ped);

                // Gold / Silver / Bronze Medal Medallion centered at top of pedestal
                float medalR = (i == 1) ? 16f : 14f;
                float mcx = x + colW / 2f;
                float mcy = topY - 2f;
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Float(mcx - medalR - 2, mcy - medalR - 2, (medalR + 2) * 2, (medalR + 2) * 2));
                g2.setColor(medalColors[i]);
                g2.fill(new Ellipse2D.Float(mcx - medalR, mcy - medalR, medalR * 2, medalR * 2));

                g2.setFont(UITheme.font(Font.BOLD, (i == 1) ? 13f : 11.5f));
                g2.setColor(Color.WHITE);
                FontMetrics mfm = g2.getFontMetrics();
                g2.drawString(ranks[i], mcx - mfm.stringWidth(ranks[i]) / 2f, mcy + (mfm.getAscent() - mfm.getDescent()) / 2f);

                // Student Name + Score floating cleanly above/inside pedestal
                AttemptDAO.StudentLeaderboardEntry entry = entries[i];
                if (entry != null) {
                    String name = entry.userId.length() > 13 ? entry.userId.substring(0, 12) + "\u2026" : entry.userId;
                    g2.setFont(UITheme.font(Font.BOLD, 12.5f));
                    g2.setColor(UITheme.TEXT_PRIMARY);
                    FontMetrics nfm = g2.getFontMetrics();
                    g2.drawString(name, x + (colW - nfm.stringWidth(name)) / 2f, topY - medalR - 8f);

                    String pct = String.format("%.1f%%", entry.averagePercentage);
                    g2.setFont(UITheme.font(Font.BOLD, 14f));
                    g2.setColor(UITheme.TEXT_PRIMARY);
                    FontMetrics pfm = g2.getFontMetrics();
                    g2.drawString(pct, x + (colW - pfm.stringWidth(pct)) / 2f, topY + medalR + 22f);

                    String gr = "Grade " + entry.grade;
                    g2.setFont(UITheme.font(Font.PLAIN, 11f));
                    g2.setColor(UITheme.TEXT_SECONDARY);
                    FontMetrics gfm = g2.getFontMetrics();
                    g2.drawString(gr, x + (colW - gfm.stringWidth(gr)) / 2f, topY + medalR + 38f);
                } else {
                    g2.setFont(UITheme.font(Font.PLAIN, 11f));
                    g2.setColor(UITheme.TEXT_MUTED);
                    String open = "Open Slot";
                    FontMetrics ofm = g2.getFontMetrics();
                    g2.drawString(open, x + (colW - ofm.stringWidth(open)) / 2f, topY + medalR + 24f);
                }
            }

            g2.dispose();
        }
    }

    // ================= ANIMATED REORDERING LEADERBOARD LIST (RANKS 4+ WITH HOVER HIGHLIGHT) =================

    private static class AnimatedLeaderboardList extends JPanel {
        private static final int ROW_H = 50;
        private static final int ROW_GAP = 6;

        private static final class AnimatedRow {
            final AttemptDAO.StudentLeaderboardEntry entry;
            float startY;
            float currentY;
            float targetY;
            float alpha;

            AnimatedRow(AttemptDAO.StudentLeaderboardEntry entry, float startY, float targetY, float startAlpha) {
                this.entry = entry;
                this.startY = startY;
                this.currentY = startY;
                this.targetY = targetY;
                this.alpha = startAlpha;
            }
        }

        private List<AnimatedRow> rows = new ArrayList<>();
        private final Map<String, Float> previousYByUser = new HashMap<>();
        private int hoveredRow = -1;
        private boolean showingTopInline = false;
        private Timer reorderTimer;

        AnimatedLeaderboardList() {
            setOpaque(false);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = rowIndexAt(e.getY());
                    if (idx != hoveredRow) {
                        hoveredRow = idx;
                        setCursor(idx >= 0 ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoveredRow != -1) {
                        hoveredRow = -1;
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
        }

        private int rowIndexAt(int y) {
            for (int i = 0; i < rows.size(); i++) {
                float ry = rows.get(i).currentY;
                if (y >= ry && y <= ry + ROW_H) return i;
            }
            return -1;
        }

        void setEntries(List<AttemptDAO.StudentLeaderboardEntry> entries, boolean showingTopInline) {
            this.showingTopInline = showingTopInline;
            if (reorderTimer != null && reorderTimer.isRunning()) {
                reorderTimer.stop();
            }

            // Snapshot current visual Y positions by userId for smooth reordering
            previousYByUser.clear();
            for (AnimatedRow r : rows) {
                previousYByUser.put(r.entry.userId, r.currentY);
            }

            List<AnimatedRow> next = new ArrayList<>();
            int topOffset = 6;
            for (int i = 0; i < entries.size(); i++) {
                AttemptDAO.StudentLeaderboardEntry e = entries.get(i);
                float targetY = topOffset + i * (ROW_H + ROW_GAP);
                Float prevY = previousYByUser.get(e.userId);
                float startY = (prevY != null) ? prevY : (targetY + 24f);
                float startAlpha = (prevY != null) ? 1f : 0f;
                next.add(new AnimatedRow(e, startY, targetY, startAlpha));
            }
            this.rows = next;
            this.hoveredRow = -1;

            int prefH = Math.max(140, topOffset + rows.size() * (ROW_H + ROW_GAP) + 12);
            setPreferredSize(new Dimension(800, prefH));
            revalidate();

            reorderTimer = Anim.animate(320, p -> {
                float ease = Anim.easeOutCubic(p);
                for (AnimatedRow r : rows) {
                    r.currentY = r.startY + (r.targetY - r.startY) * ease;
                    r.alpha = Math.min(1f, r.alpha + (1f - r.alpha) * ease);
                }
                repaint();
            }, () -> {
                for (AnimatedRow r : rows) {
                    r.currentY = r.targetY;
                    r.alpha = 1f;
                }
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            if (rows.isEmpty()) {
                g2.setFont(UITheme.FONT_BODY);
                g2.setColor(UITheme.TEXT_MUTED);
                String msg = "No additional ranked students for this filter.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, 64);
                g2.dispose();
                return;
            }

            for (int i = 0; i < rows.size(); i++) {
                AnimatedRow r = rows.get(i);
                AttemptDAO.StudentLeaderboardEntry e = r.entry;
                boolean isHov = (i == hoveredRow);

                Graphics2D rg = (Graphics2D) g2.create();
                if (r.alpha < 0.999f) {
                    rg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Anim.clamp01(r.alpha)));
                }

                float y = r.currentY;
                Shape card = new RoundRectangle2D.Float(4, y, w - 8, ROW_H, 12, 12);

                // Hover highlight in light indigo, subtle zebra striping otherwise
                if (isHov) {
                    rg.setColor(UITheme.PRIMARY_SOFT);
                } else {
                    rg.setColor(i % 2 == 0 ? Color.WHITE : new Color(249, 250, 253));
                }
                rg.fill(card);

                rg.setColor(isHov ? Anim.withAlpha(UITheme.PRIMARY, 110) : UITheme.BORDER);
                rg.setStroke(new BasicStroke(1f));
                rg.draw(card);

                // Rank pill
                String rankStr = "#" + e.rank;
                rg.setColor(isHov ? UITheme.PRIMARY : new Color(241, 245, 249));
                rg.fill(new RoundRectangle2D.Float(16, y + 12, 42, 26, 8, 8));
                rg.setFont(UITheme.font(Font.BOLD, 12f));
                rg.setColor(isHov ? Color.WHITE : UITheme.TEXT_SECONDARY);
                FontMetrics rfm = rg.getFontMetrics();
                rg.drawString(rankStr, 16 + (42 - rfm.stringWidth(rankStr)) / 2, y + 29);

                // Mini initials avatar
                String init = e.userId.length() >= 2 ? e.userId.substring(0, 2).toUpperCase() : e.userId.toUpperCase();
                rg.setColor(UITheme.PRIMARY_SOFT);
                rg.fill(new Ellipse2D.Float(70, y + 10, 30, 30));
                rg.setFont(UITheme.font(Font.BOLD, 11f));
                rg.setColor(UITheme.PRIMARY);
                FontMetrics afm = rg.getFontMetrics();
                rg.drawString(init, 70 + (30 - afm.stringWidth(init)) / 2, y + 29);

                // User ID & attempts meta
                rg.setFont(UITheme.font(Font.BOLD, 13.5f));
                rg.setColor(UITheme.TEXT_PRIMARY);
                rg.drawString(e.userId, 112, y + 23);

                rg.setFont(UITheme.font(Font.PLAIN, 11f));
                rg.setColor(UITheme.TEXT_MUTED);
                rg.drawString(e.totalAttempts + " exam" + (e.totalAttempts == 1 ? "" : "s")
                        + "  \u2022  High: " + e.highestScore + "  \u2022  Low: " + e.lowestScore, 112, y + 39);

                // Right-aligned metrics: Avg %, Grade, Status pill
                int rightX = w - 24;
                boolean pass = "Pass".equalsIgnoreCase(e.status);

                // Status pill
                int pillW = 60;
                int pillX = rightX - pillW;
                rg.setColor(pass ? UITheme.SUCCESS_SOFT : UITheme.DANGER_SOFT);
                rg.fill(new RoundRectangle2D.Float(pillX, y + 13, pillW, 24, 8, 8));
                rg.setFont(UITheme.font(Font.BOLD, 11.5f));
                rg.setColor(pass ? UITheme.SUCCESS_TEXT : UITheme.DANGER_TEXT);
                FontMetrics sfm = rg.getFontMetrics();
                rg.drawString(e.status, pillX + (pillW - sfm.stringWidth(e.status)) / 2, y + 29);

                // Grade
                String grStr = "Grade " + e.grade;
                rg.setFont(UITheme.font(Font.BOLD, 12.5f));
                rg.setColor(UITheme.TEXT_SECONDARY);
                rg.drawString(grStr, pillX - 82, y + 30);

                // Avg %
                String pctStr = String.format("%.2f%%", e.averagePercentage);
                rg.setFont(UITheme.font(Font.BOLD, 14f));
                rg.setColor(UITheme.PRIMARY);
                rg.drawString(pctStr, pillX - 166, y + 30);

                rg.dispose();
            }

            if (showingTopInline) {
                g2.setFont(UITheme.font(Font.PLAIN, 11f));
                g2.setColor(UITheme.TEXT_MUTED);
                g2.drawString("Showing all ranked students (ranks 4+ will separate automatically when more students complete exams).", 8, getHeight() - 4);
            }

            g2.dispose();
        }
    }

    private void loadDifficultyStatsAsync(Integer subjectId) {
        new SwingWorker<Map<String, AttemptDAO.DifficultyAccuracyStat>, Void>() {
            @Override
            protected Map<String, AttemptDAO.DifficultyAccuracyStat> doInBackground() {
                return AttemptDAO.getDifficultyAccuracyStats(subjectId);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    difficultyPanel.setStats(get());
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ================= DIFFICULTY-WISE CORRECT % ANIMATED BAR PANEL =================

    private static class DifficultyAccuracyPanel extends JComponent {
        private static final String[] LEVELS = {
                ExamStore.DIFFICULTY_EASY, ExamStore.DIFFICULTY_MEDIUM, ExamStore.DIFFICULTY_HARD
        };
        private static final Color[] FILL_COLORS = {
                UITheme.SUCCESS, UITheme.WARNING, UITheme.DANGER
        };
        private static final Color[] SOFT_COLORS = {
                UITheme.SUCCESS_SOFT, UITheme.WARNING_SOFT, UITheme.DANGER_SOFT
        };
        private static final Color[] TEXT_COLORS = {
                UITheme.SUCCESS_TEXT, UITheme.WARNING_TEXT, UITheme.DANGER_TEXT
        };

        private Map<String, AttemptDAO.DifficultyAccuracyStat> stats = new LinkedHashMap<>();
        private float animProgress = 1f;
        private Timer animTimer;

        DifficultyAccuracyPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(280, 195));
        }

        void setStats(Map<String, AttemptDAO.DifficultyAccuracyStat> newStats) {
            this.stats = newStats != null ? newStats : new LinkedHashMap<>();
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
            animProgress = 0f;
            animTimer = Anim.animate(480, p -> {
                animProgress = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                animProgress = 1f;
                repaint();
            });
        }

        @Override
        public void removeNotify() {
            if (animTimer != null) animTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int totalAnsweredAll = 0;
            for (String lvl : LEVELS) {
                AttemptDAO.DifficultyAccuracyStat s = stats.get(lvl);
                if (s != null) totalAnsweredAll += s.totalCount;
            }

            if (totalAnsweredAll == 0) {
                g2.setFont(UITheme.font(Font.PLAIN, 12.5f));
                g2.setColor(UITheme.TEXT_MUTED);
                String msg = "No per-question answer data recorded yet.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, Math.max(8, (w - fm.stringWidth(msg)) / 2), h / 2);
                g2.dispose();
                return;
            }

            int padX = 8;
            int barW = Math.max(80, w - padX * 2);
            int rowH = Math.max(46, (h - 14) / 3);

            for (int i = 0; i < LEVELS.length; i++) {
                String lvl = LEVELS[i];
                AttemptDAO.DifficultyAccuracyStat st = stats.get(lvl);
                int correct = st != null ? st.correctCount : 0;
                int total = st != null ? st.totalCount : 0;
                double pct = st != null ? st.correctPercentage : 0.0;

                int y = 6 + i * rowH;

                // Colored pill label on left
                g2.setFont(UITheme.font(Font.BOLD, 11f));
                FontMetrics fmTag = g2.getFontMetrics();
                int pillW = fmTag.stringWidth(lvl) + 16;
                g2.setColor(SOFT_COLORS[i]);
                g2.fill(new RoundRectangle2D.Float(padX, y, pillW, 20, 8, 8));
                g2.setColor(TEXT_COLORS[i]);
                g2.drawString(lvl, padX + 8, y + 14);

                // Right readout: "78.5% (11/14)" or "No data"
                String rightText = total > 0
                        ? String.format("%.1f%% (%d/%d)", pct, correct, total)
                        : "No answers";
                g2.setFont(UITheme.font(Font.BOLD, 12f));
                g2.setColor(total > 0 ? UITheme.TEXT_PRIMARY : UITheme.TEXT_MUTED);
                FontMetrics fmR = g2.getFontMetrics();
                g2.drawString(rightText, w - padX - fmR.stringWidth(rightText), y + 14);

                // Track bar
                int barY = y + 26;
                int barH = 11;
                g2.setColor(new Color(241, 245, 249));
                g2.fill(new RoundRectangle2D.Float(padX, barY, barW, barH, barH, barH));

                if (total > 0 && pct > 0.0) {
                    float fillW = (float) (barW * (pct / 100.0) * animProgress);
                    g2.setColor(FILL_COLORS[i]);
                    g2.fill(new RoundRectangle2D.Float(padX, barY, Math.max(barH, fillW), barH, barH, barH));
                }
            }

            g2.dispose();
        }
    }

    private static class SubjectOption {
        final Integer subjectId;
        final String name;

        SubjectOption(Integer subjectId, String name) {
            this.subjectId = subjectId;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
