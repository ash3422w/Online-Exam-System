package com.onlineexam.ui.admin;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.ExamDAO;
import com.onlineexam.dao.QuestionAnalyticsDAO;
import com.onlineexam.dao.QuestionDAO;
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
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class QuestionAnalyticsUI extends JFrame {

    private static final String[] OPTION_LETTERS = {"A", "B", "C", "D"};

    private final JComboBox<FilterItem<Integer>> subjectBox;
    private final JComboBox<FilterItem<Integer>> examBox;
    private final JComboBox<String> difficultyBox;
    private final JComboBox<String> flagFilterBox;
    private final JSpinner minSampleSpinner;

    private final JLabel kpiAnalyzedVal;
    private final JLabel kpiHardVal;
    private final JLabel kpiEasyVal;
    private final JLabel kpiAvgVal;

    private final CardLayout contentCardsLayout;
    private final JPanel contentCardsPanel;
    private final JLabel emptyTitleLabel;
    private final JLabel emptySubLabel;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final DetailCardPanel detailCard;

    private List<QuestionAnalyticsDAO.QuestionStat> rawStats = new ArrayList<>();
    private List<QuestionAnalyticsDAO.QuestionStat> filteredStats = new ArrayList<>();
    private boolean totalDbEmpty = true;
    private boolean initializingFilters = true;

    private float tableBarAnimProgress = 1f;
    private javax.swing.Timer tableBarAnimTimer;

    private static final class FilterItem<T> {
        final T value;
        final String label;

        FilterItem(T value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public QuestionAnalyticsUI() {
        setTitle("Question Analytics - Online Exam System");
        setSize(1240, 780);
        setMinimumSize(new Dimension(1020, 650));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.42f);

        // ================= TOP BAR =================
        JPanel topBar = new JPanel(new BorderLayout(16, 0)) {
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
        topBar.setBorder(new EmptyBorder(14, 24, 14, 24));

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);
        JLabel title = new JLabel("Question Analytics & Distractor Insights");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Identify overly hard questions, overly easy items, and misleading distractors from SQL attempt data");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));
        headLeft.add(title);
        headLeft.add(sub);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        topActions.setOpaque(false);

        UITheme.ModernButton exportBtn = UITheme.successButton("Export CSV");
        exportBtn.addActionListener(e -> exportFilteredCsv());

        UITheme.ModernButton refreshBtn = UITheme.secondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadAnalyticsAsync(true));

        UITheme.ModernButton closeBtn = UITheme.secondaryButton("\u2190 Close");
        closeBtn.addActionListener(e -> dispose());

        topActions.add(exportBtn);
        topActions.add(refreshBtn);
        topActions.add(closeBtn);

        topBar.add(headLeft, BorderLayout.WEST);
        topBar.add(topActions, BorderLayout.EAST);

        // ================= WORKSPACE =================
        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(14, 22, 18, 22));

        // ---- KPI STRIP (4 COUNT-UP BENTO CARDS) ----
        JPanel topStack = new JPanel(new BorderLayout(0, 12));
        topStack.setOpaque(false);

        kpiAnalyzedVal = new JLabel("0");
        kpiHardVal = new JLabel("0");
        kpiEasyVal = new JLabel("0");
        kpiAvgVal = new JLabel("0.0%");

        JPanel kpiStrip = new JPanel(new GridLayout(1, 4, 14, 0));
        kpiStrip.setOpaque(false);
        kpiStrip.add(buildKpiCard("QUESTIONS ANALYZED", kpiAnalyzedVal, "Matching active filters",
                UITheme.PRIMARY, UITheme.PRIMARY_SOFT, UITheme.IconType.BOOK));
        kpiStrip.add(buildKpiCard("FLAGGED HARD", kpiHardVal,
                "Below " + (int) QuestionAnalyticsDAO.HARD_THRESHOLD_PCT + "% correct",
                UITheme.DANGER, UITheme.DANGER_SOFT, UITheme.IconType.SHIELD));
        kpiStrip.add(buildKpiCard("FLAGGED TOO EASY", kpiEasyVal,
                "Above " + (int) QuestionAnalyticsDAO.TOO_EASY_THRESHOLD_PCT + "% correct",
                UITheme.SUCCESS, UITheme.SUCCESS_SOFT, UITheme.IconType.CHECKLIST));
        kpiStrip.add(buildKpiCard("AVERAGE CORRECTNESS", kpiAvgVal, "Across filtered questions",
                UITheme.VIOLET, UITheme.VIOLET_SOFT, UITheme.IconType.CHART));

        // ---- FILTER BAR ----
        GlassPanel filterBar = new GlassPanel(14);
        filterBar.setSpotlightEnabled(false);
        filterBar.setShadowEnabled(false);
        filterBar.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));
        filterBar.setBorder(new EmptyBorder(6, 12, 6, 12));

        subjectBox = UITheme.createComboBox(null);
        subjectBox.setPreferredSize(new Dimension(145, 34));
        subjectBox.addItem(new FilterItem<>(null, "All Subjects"));

        examBox = UITheme.createComboBox(null);
        examBox.setPreferredSize(new Dimension(185, 34));
        examBox.addItem(new FilterItem<>(null, "All Exams"));

        difficultyBox = UITheme.createComboBox(new String[]{
                "All Difficulties", ExamStore.DIFFICULTY_EASY, ExamStore.DIFFICULTY_MEDIUM, ExamStore.DIFFICULTY_HARD
        });
        difficultyBox.setPreferredSize(new Dimension(140, 34));

        flagFilterBox = UITheme.createComboBox(new String[]{
                "All Flags", "HARD (<20%)", "TOO EASY (>95%)", "OK", "Low data"
        });
        flagFilterBox.setPreferredSize(new Dimension(145, 34));

        minSampleSpinner = new JSpinner(new SpinnerNumberModel(QuestionAnalyticsDAO.DEFAULT_MIN_SAMPLE, 1, 200, 1));
        minSampleSpinner.setPreferredSize(new Dimension(68, 34));
        minSampleSpinner.setFont(UITheme.FONT_BODY);

        filterBar.add(UITheme.createFieldLabel("Subject:"));
        filterBar.add(subjectBox);
        filterBar.add(UITheme.createFieldLabel("Exam:"));
        filterBar.add(examBox);
        filterBar.add(UITheme.createFieldLabel("Difficulty:"));
        filterBar.add(difficultyBox);
        filterBar.add(UITheme.createFieldLabel("Flag:"));
        filterBar.add(flagFilterBox);
        filterBar.add(UITheme.createFieldLabel("Min Sample:"));
        filterBar.add(minSampleSpinner);

        topStack.add(kpiStrip, BorderLayout.NORTH);
        topStack.add(filterBar, BorderLayout.SOUTH);

        // ---- SORTABLE TABLE ----
        String[] cols = {"Question", "Subject", "Difficulty", "Asked", "Correct %", "Skipped", "Flag"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                if (col == 3 || col == 5) return Integer.class;
                if (col == 4) return Double.class;
                return String.class;
            }
        };

        table = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int viewRow = rowAtPoint(e.getPoint());
                int viewCol = columnAtPoint(e.getPoint());
                if (viewRow >= 0 && viewCol == 0) {
                    int modelRow = convertRowIndexToModel(viewRow);
                    if (modelRow >= 0 && modelRow < filteredStats.size()) {
                        String full = filteredStats.get(modelRow).questionText;
                        return "<html><div style='width:380px;'>" + escapeHtml(full) + "</div></html>";
                    }
                }
                return super.getToolTipText(e);
            }
        };
        UITheme.styleTable(table);
        table.setRowHeight(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(3, Comparator.comparingInt(o -> ((Number) o).intValue()));
        sorter.setComparator(4, Comparator.comparingDouble(o -> ((Number) o).doubleValue()));
        sorter.setComparator(5, Comparator.comparingInt(o -> ((Number) o).intValue()));
        table.setRowSorter(sorter);

        int[] widths = {280, 88, 86, 58, 145, 62, 92};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Default text renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
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
                c.setFont(UITheme.font(column == 3 ? Font.BOLD : Font.PLAIN, 12.5f));
                return c;
            }
        });
        table.setDefaultRenderer(Integer.class, table.getDefaultRenderer(Object.class));

        // Col 2: Colored Difficulty Badge renderer
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                cell.add(UITheme.createDifficultyBadge(value != null ? value.toString() : "MEDIUM"));
                return cell;
            }
        });

        // Col 4: Animated Correct % Bar Renderer (red < 20, amber 20..95, green > 95)
        table.getColumnModel().getColumn(4).setCellRenderer(new CorrectBarCellRenderer());

        // Col 6: Colored Flag Chip Renderer
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String label = value != null ? value.toString() : "OK";
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                cell.add(createFlagBadge(label));
                return cell;
            }
        });

        GlassPanel tableCard = new GlassPanel(16);
        tableCard.setSpotlightEnabled(false);
        tableCard.setShadowColor(UITheme.PRIMARY);
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel tableHint = new JLabel("Click any column header to sort  \u2022  Click any row to inspect option distribution & distractors");
        tableHint.setFont(UITheme.font(Font.PLAIN, 11.5f));
        tableHint.setForeground(UITheme.TEXT_MUTED);
        tableCard.add(tableHint, BorderLayout.NORTH);
        tableCard.add(UITheme.styleScrollPane(table), BorderLayout.CENTER);

        // ---- DETAIL CARD ----
        detailCard = new DetailCardPanel(this::openEditForStat);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = table.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    if (modelRow >= 0 && modelRow < filteredStats.size()) {
                        detailCard.showStat(filteredStats.get(modelRow), getMinSample());
                    }
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableCard, detailCard);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(10);
        splitPane.setResizeWeight(0.64);
        splitPane.setDividerLocation(740);

        // ---- EMPTY STATE PANEL ----
        GlassPanel emptyCard = new GlassPanel(18);
        emptyCard.setSpotlightEnabled(false);
        emptyCard.setShadowColor(UITheme.PRIMARY);
        emptyCard.setLayout(new GridBagLayout());
        emptyCard.setBorder(new EmptyBorder(32, 32, 32, 32));

        JPanel emptyStack = new JPanel();
        emptyStack.setLayout(new BoxLayout(emptyStack, BoxLayout.Y_AXIS));
        emptyStack.setOpaque(false);

        EmptyAnalyticsIllustration illus = new EmptyAnalyticsIllustration();
        illus.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptyTitleLabel = new JLabel("No attempts yet");
        emptyTitleLabel.setFont(UITheme.font(Font.BOLD, 20f));
        emptyTitleLabel.setForeground(UITheme.TEXT_PRIMARY);
        emptyTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptySubLabel = new JLabel("Once students submit exams, per-question difficulty flags and distractor charts will appear here.");
        emptySubLabel.setFont(UITheme.font(Font.PLAIN, 13f));
        emptySubLabel.setForeground(UITheme.TEXT_SECONDARY);
        emptySubLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptyStack.add(illus);
        emptyStack.add(Box.createVerticalStrut(14));
        emptyStack.add(emptyTitleLabel);
        emptyStack.add(Box.createVerticalStrut(6));
        emptyStack.add(emptySubLabel);
        emptyCard.add(emptyStack);

        contentCardsLayout = new CardLayout();
        contentCardsPanel = new JPanel(contentCardsLayout);
        contentCardsPanel.setOpaque(false);
        contentCardsPanel.add(emptyCard, "EMPTY");
        contentCardsPanel.add(splitPane, "DATA");

        workspace.add(topStack, BorderLayout.NORTH);
        workspace.add(contentCardsPanel, BorderLayout.CENTER);

        root.add(topBar, BorderLayout.NORTH);
        root.add(workspace, BorderLayout.CENTER);
        add(root);

        // Filter listeners
        subjectBox.addActionListener(e -> { if (!initializingFilters) loadAnalyticsAsync(false); });
        examBox.addActionListener(e -> { if (!initializingFilters) loadAnalyticsAsync(false); });
        difficultyBox.addActionListener(e -> { if (!initializingFilters) loadAnalyticsAsync(false); });
        flagFilterBox.addActionListener(e -> { if (!initializingFilters) applyClientFiltersAndRender(); });
        minSampleSpinner.addChangeListener(e -> { if (!initializingFilters) applyClientFiltersAndRender(); });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopAnimations();
            }
        });

        setVisible(true);
        loadFiltersAndInitialDataAsync();
    }

    private void stopAnimations() {
        if (tableBarAnimTimer != null && tableBarAnimTimer.isRunning()) {
            tableBarAnimTimer.stop();
        }
        detailCard.stopAnimations();
    }

    @Override
    public void dispose() {
        stopAnimations();
        super.dispose();
    }

    private int getMinSample() {
        return Math.max(1, ((Number) minSampleSpinner.getValue()).intValue());
    }

    private void loadFiltersAndInitialDataAsync() {
        new SwingWorker<Void, Void>() {
            private Map<Integer, String> subjects;
            private List<ExamConfig> exams;
            private List<QuestionAnalyticsDAO.QuestionStat> initialStats;

            @Override
            protected Void doInBackground() {
                subjects = AttemptDAO.getAvailableSubjects();
                exams = ExamDAO.getAllExams();
                initialStats = QuestionAnalyticsDAO.getQuestionStats(null, null, null);
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                initializingFilters = true;
                try {
                    if (subjects != null) {
                        for (Map.Entry<Integer, String> entry : subjects.entrySet()) {
                            subjectBox.addItem(new FilterItem<>(entry.getKey(), entry.getValue()));
                        }
                    }
                    if (exams != null) {
                        for (ExamConfig cfg : exams) {
                            examBox.addItem(new FilterItem<>(cfg.id, cfg.title + " (" + cfg.subjectName + ")"));
                        }
                    }
                } finally {
                    initializingFilters = false;
                }
                rawStats = initialStats != null ? initialStats : new ArrayList<>();
                totalDbEmpty = rawStats.isEmpty();
                applyClientFiltersAndRender();
            }
        }.execute();
    }

    private void loadAnalyticsAsync(boolean showToast) {
        @SuppressWarnings("unchecked")
        FilterItem<Integer> selSubj = (FilterItem<Integer>) subjectBox.getSelectedItem();
        @SuppressWarnings("unchecked")
        FilterItem<Integer> selExam = (FilterItem<Integer>) examBox.getSelectedItem();
        String selDiff = (String) difficultyBox.getSelectedItem();

        Integer subjId = selSubj != null ? selSubj.value : null;
        Integer examId = selExam != null ? selExam.value : null;
        String diffParam = (selDiff == null || selDiff.startsWith("All")) ? null : selDiff;

        new SwingWorker<List<QuestionAnalyticsDAO.QuestionStat>, Void>() {
            @Override
            protected List<QuestionAnalyticsDAO.QuestionStat> doInBackground() {
                return QuestionAnalyticsDAO.getQuestionStats(subjId, examId, diffParam);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    rawStats = get();
                    if (rawStats == null) rawStats = new ArrayList<>();
                    if (subjId == null && examId == null && diffParam == null) {
                        totalDbEmpty = rawStats.isEmpty();
                    }
                    applyClientFiltersAndRender();
                    if (showToast) {
                        UITheme.showToast(QuestionAnalyticsUI.this, "Analytics Refreshed",
                                "Analyzed " + filteredStats.size() + " question(s) from MySQL.", UITheme.ToastType.SUCCESS);
                    }
                } catch (Exception ex) {
                    UITheme.showToast(QuestionAnalyticsUI.this, "Load Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        }.execute();
    }

    private void applyClientFiltersAndRender() {
        int minSample = getMinSample();
        String flagChoice = (String) flagFilterBox.getSelectedItem();

        filteredStats = new ArrayList<>();
        int hardCount = 0;
        int easyCount = 0;
        double sumPct = 0.0;

        for (QuestionAnalyticsDAO.QuestionStat stat : rawStats) {
            QuestionAnalyticsDAO.QuestionFlag flag = stat.evaluateFlag(minSample);
            if (!matchesFlagFilter(flag, flagChoice)) {
                continue;
            }
            filteredStats.add(stat);
            if (flag == QuestionAnalyticsDAO.QuestionFlag.HARD) hardCount++;
            else if (flag == QuestionAnalyticsDAO.QuestionFlag.TOO_EASY) easyCount++;
            sumPct += stat.percentCorrect;
        }

        double avgPct = filteredStats.isEmpty() ? 0.0 : AttemptDAO.roundTwoDecimals(sumPct / filteredStats.size());

        // KPI Count-Up Animations
        Anim.countUp(kpiAnalyzedVal, 0, filteredStats.size(), 520, "%d");
        Anim.countUp(kpiHardVal, 0, hardCount, 540, "%d");
        Anim.countUp(kpiEasyVal, 0, easyCount, 540, "%d");
        Anim.countUp(kpiAvgVal, 0.0, avgPct, 620, "%.1f%%");

        if (filteredStats.isEmpty()) {
            if (totalDbEmpty && rawStats.isEmpty()) {
                emptyTitleLabel.setText("No attempts yet");
                emptySubLabel.setText("Once students submit exams, per-question difficulty flags and distractor charts will appear here.");
            } else {
                emptyTitleLabel.setText("No matching questions");
                emptySubLabel.setText("Try adjusting your subject, exam, difficulty, flag, or minimum sample filters.");
            }
            contentCardsLayout.show(contentCardsPanel, "EMPTY");
            return;
        }

        tableModel.setRowCount(0);
        for (QuestionAnalyticsDAO.QuestionStat s : filteredStats) {
            QuestionAnalyticsDAO.QuestionFlag flag = s.evaluateFlag(minSample);
            tableModel.addRow(new Object[]{
                    truncate(s.questionText, 62),
                    s.subjectName,
                    s.difficulty,
                    s.timesAsked,
                    s.percentCorrect,
                    s.timesSkipped,
                    flag.displayLabel
            });
        }

        contentCardsLayout.show(contentCardsPanel, "DATA");
        animateTableBars();

        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
            int modelIdx = table.convertRowIndexToModel(0);
            if (modelIdx >= 0 && modelIdx < filteredStats.size()) {
                detailCard.showStat(filteredStats.get(modelIdx), minSample);
            }
        }
    }

    private boolean matchesFlagFilter(QuestionAnalyticsDAO.QuestionFlag flag, String choice) {
        if (choice == null || choice.startsWith("All")) return true;
        if (choice.startsWith("HARD")) return flag == QuestionAnalyticsDAO.QuestionFlag.HARD;
        if (choice.startsWith("TOO EASY")) return flag == QuestionAnalyticsDAO.QuestionFlag.TOO_EASY;
        if ("OK".equalsIgnoreCase(choice)) return flag == QuestionAnalyticsDAO.QuestionFlag.OK;
        if ("Low data".equalsIgnoreCase(choice)) return flag == QuestionAnalyticsDAO.QuestionFlag.LOW_DATA;
        return true;
    }

    private void animateTableBars() {
        if (tableBarAnimTimer != null && tableBarAnimTimer.isRunning()) {
            tableBarAnimTimer.stop();
        }
        tableBarAnimProgress = 0f;
        tableBarAnimTimer = Anim.animate(420, p -> {
            tableBarAnimProgress = Anim.easeOutCubic(p);
            table.repaint();
        }, () -> {
            tableBarAnimProgress = 1f;
            table.repaint();
        });
    }

    private void openEditForStat(QuestionAnalyticsDAO.QuestionStat stat) {
        if (stat == null || stat.deleted || stat.questionId <= 0) {
            UITheme.showToast(this, "Cannot Edit", "This question has already been deleted from the database.", UITheme.ToastType.WARNING);
            return;
        }
        new SwingWorker<ExamStore.Question, Void>() {
            @Override
            protected ExamStore.Question doInBackground() {
                return QuestionDAO.getQuestionById(stat.questionId);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    ExamStore.Question fullQ = get();
                    if (fullQ == null) {
                        UITheme.showToast(QuestionAnalyticsUI.this, "Not Found",
                                "Question #" + stat.questionId + " no longer exists.", UITheme.ToastType.WARNING);
                        loadAnalyticsAsync(false);
                        return;
                    }
                    ViewQuestionsUI.showEditQuestionDialog(QuestionAnalyticsUI.this, fullQ, () -> loadAnalyticsAsync(false));
                } catch (Exception ex) {
                    UITheme.showToast(QuestionAnalyticsUI.this, "Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        }.execute();
    }

    private void exportFilteredCsv() {
        if (filteredStats == null || filteredStats.isEmpty()) {
            UITheme.showToast(this, "Nothing to Export", "No analyzed question rows to export.", UITheme.ToastType.WARNING);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Question Analytics as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("question_analytics.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }

        int minSample = getMinSample();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("Question ID,Question,Subject,Type,Difficulty,Times Asked,Times Correct,Times Skipped,Correct %,Flag,Most Chosen Wrong Option");
            writer.newLine();
            for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
                int modelRow = table.convertRowIndexToModel(viewRow);
                if (modelRow < 0 || modelRow >= filteredStats.size()) continue;
                QuestionAnalyticsDAO.QuestionStat s = filteredStats.get(modelRow);
                QuestionAnalyticsDAO.QuestionFlag flag = s.evaluateFlag(minSample);
                String wrongInfo = (s.supportsOptionDistribution() && s.mostChosenWrongOption > 0)
                        ? ("Option " + s.getMostChosenWrongLetter() + " (" + String.format("%.0f%%", s.mostChosenWrongPct) + ")")
                        : "-";
                writer.write(String.format("%d,%s,%s,%s,%s,%d,%d,%d,%.2f%%,%s,%s",
                        s.questionId,
                        csvQuote(s.questionText),
                        csvQuote(s.subjectName),
                        csvQuote(s.questionType),
                        csvQuote(s.difficulty),
                        s.timesAsked,
                        s.timesCorrect,
                        s.timesSkipped,
                        s.percentCorrect,
                        csvQuote(flag.displayLabel),
                        csvQuote(wrongInfo)
                ));
                writer.newLine();
            }
            UITheme.showToast(this, "CSV Exported", "Saved " + file.getName() + " successfully.", UITheme.ToastType.SUCCESS);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private DashboardComponents.BentoCard buildKpiCard(
            String labelText, JLabel valLabel, String subText,
            Color accent, Color softBg, UITheme.IconType iconType) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(15);
        card.setShadowColor(accent);
        card.setLeftAccentColor(accent);
        card.setLayout(new BorderLayout(0, 4));
        card.setBorder(new EmptyBorder(12, 16, 13, 14));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        JLabel l = new JLabel(labelText);
        l.setFont(UITheme.font(Font.BOLD, 10f));
        l.setForeground(UITheme.TEXT_MUTED);
        top.add(l, BorderLayout.WEST);
        top.add(new UITheme.VectorIcon(iconType, accent, softBg, 28), BorderLayout.EAST);

        valLabel.setFont(UITheme.font(Font.BOLD, 24f));
        valLabel.setForeground(UITheme.TEXT_PRIMARY);

        JLabel s = new JLabel(subText);
        s.setFont(UITheme.font(Font.PLAIN, 11f));
        s.setForeground(UITheme.TEXT_SECONDARY);

        card.add(top, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        card.add(s, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel createFlagBadge(String flagLabel) {
        if ("HARD".equalsIgnoreCase(flagLabel)) {
            return UITheme.createBadge("HARD", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT);
        }
        if ("TOO EASY".equalsIgnoreCase(flagLabel)) {
            return UITheme.createBadge("TOO EASY", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT);
        }
        if ("Low data".equalsIgnoreCase(flagLabel)) {
            return UITheme.createBadge("Low data", new Color(241, 245, 249), UITheme.TEXT_MUTED);
        }
        return UITheme.createBadge("OK", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLen ? clean : (clean.substring(0, maxLen - 1) + "\u2026");
    }

    private static String csvQuote(String raw) {
        if (raw == null) return "\"\"";
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private static String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ================= ANIMATED CORRECT % BAR CELL RENDERER =================

    private class CorrectBarCellRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private double pct = 0.0;

        CorrectBarCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.pct = (value instanceof Number) ? Math.max(0.0, Math.min(100.0, ((Number) value).doubleValue())) : 0.0;
            setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int padX = 10;
            int barH = 10;
            int barW = Math.max(36, w - padX * 2 - 46);
            int barY = (h - barH) / 2;

            Color barColor;
            Color txtColor;
            if (pct < QuestionAnalyticsDAO.HARD_THRESHOLD_PCT) {
                barColor = UITheme.DANGER;
                txtColor = UITheme.DANGER_TEXT;
            } else if (pct > QuestionAnalyticsDAO.TOO_EASY_THRESHOLD_PCT) {
                barColor = UITheme.SUCCESS;
                txtColor = UITheme.SUCCESS_TEXT;
            } else {
                barColor = UITheme.WARNING;
                txtColor = UITheme.WARNING_TEXT;
            }

            // Track
            g2.setColor(new Color(226, 232, 240));
            g2.fill(new RoundRectangle2D.Float(padX, barY, barW, barH, barH, barH));

            // Animated fill
            float fillW = (float) (barW * (pct / 100.0) * tableBarAnimProgress);
            if (fillW > 1f) {
                g2.setColor(barColor);
                g2.fill(new RoundRectangle2D.Float(padX, barY, Math.max(barH * 0.8f, fillW), barH, barH, barH));
            }

            // Percentage text on right
            String txt = String.format("%.0f%%", pct);
            g2.setFont(UITheme.font(Font.BOLD, 12f));
            g2.setColor(txtColor);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(txt, w - padX - fm.stringWidth(txt), (h + fm.getAscent() - fm.getDescent()) / 2);

            g2.dispose();
        }
    }

    // ================= RIGHT-HAND QUESTION DETAIL & OPTION DISTRIBUTION CARD =================

    private static class DetailCardPanel extends GlassPanel {
        private final java.util.function.Consumer<QuestionAnalyticsDAO.QuestionStat> onEditClick;
        private final JLabel titleLbl;
        private final JPanel badgeStrip;
        private final JLabel fullQuestionLbl;
        private final JLabel statAskedLbl;
        private final JLabel statCorrectLbl;
        private final JLabel statSkippedLbl;
        private final OptionDistributionPanel distPanel;
        private final UITheme.RoundedPanel distractorBanner;
        private final JLabel distractorLbl;
        private final UITheme.ModernButton editBtn;

        private QuestionAnalyticsDAO.QuestionStat currentStat;

        DetailCardPanel(java.util.function.Consumer<QuestionAnalyticsDAO.QuestionStat> onEditClick) {
            super(16);
            this.onEditClick = onEditClick;
            setSpotlightEnabled(false);
            setShadowColor(UITheme.VIOLET);
            setLayout(new BorderLayout(0, 12));
            setBorder(new EmptyBorder(16, 18, 16, 18));
            setMinimumSize(new Dimension(340, 360));

            JPanel topHead = new JPanel(new BorderLayout(8, 6));
            topHead.setOpaque(false);

            titleLbl = new JLabel("Question Detail");
            titleLbl.setFont(UITheme.font(Font.BOLD, 15f));
            titleLbl.setForeground(UITheme.TEXT_PRIMARY);

            badgeStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            badgeStrip.setOpaque(false);

            topHead.add(titleLbl, BorderLayout.NORTH);
            topHead.add(badgeStrip, BorderLayout.SOUTH);

            // Scrollable Center Stack
            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(false);

            fullQuestionLbl = new JLabel("<html><i>Select a question row on the left to inspect its option distribution and distractor analytics.</i></html>");
            fullQuestionLbl.setFont(UITheme.font(Font.BOLD, 13.5f));
            fullQuestionLbl.setForeground(UITheme.TEXT_PRIMARY);
            fullQuestionLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel statRow = new JPanel(new GridLayout(1, 3, 8, 0));
            statRow.setOpaque(false);
            statRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            statRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

            statAskedLbl = new JLabel("-", SwingConstants.CENTER);
            statCorrectLbl = new JLabel("-", SwingConstants.CENTER);
            statSkippedLbl = new JLabel("-", SwingConstants.CENTER);

            statRow.add(miniPill("ASKED", statAskedLbl, UITheme.PRIMARY_SOFT, UITheme.PRIMARY));
            statRow.add(miniPill("CORRECT", statCorrectLbl, UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
            statRow.add(miniPill("SKIPPED", statSkippedLbl, new Color(241, 245, 249), UITheme.TEXT_SECONDARY));

            JLabel distTitle = new JLabel("Chosen Option Distribution (Original DB Options)");
            distTitle.setFont(UITheme.font(Font.BOLD, 12f));
            distTitle.setForeground(UITheme.TEXT_SECONDARY);
            distTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

            distPanel = new OptionDistributionPanel();
            distPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            distractorBanner = new UITheme.RoundedPanel(10, UITheme.WARNING_SOFT, Anim.withAlpha(UITheme.WARNING, 140));
            distractorBanner.setLayout(new BorderLayout(8, 0));
            distractorBanner.setBorder(new EmptyBorder(9, 12, 9, 12));
            distractorBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
            distractorBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            distractorLbl = new JLabel("");
            distractorLbl.setFont(UITheme.font(Font.BOLD, 12f));
            distractorLbl.setForeground(UITheme.WARNING_TEXT);
            distractorBanner.add(distractorLbl, BorderLayout.CENTER);
            distractorBanner.setVisible(false);

            body.add(fullQuestionLbl);
            body.add(Box.createVerticalStrut(12));
            body.add(statRow);
            body.add(Box.createVerticalStrut(14));
            body.add(distTitle);
            body.add(Box.createVerticalStrut(6));
            body.add(distPanel);
            body.add(Box.createVerticalStrut(10));
            body.add(distractorBanner);

            JScrollPane scroll = new JScrollPane(body,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            UITheme.applyModernScrollBars(scroll);

            editBtn = UITheme.gradientPrimaryButton("Edit Question");
            editBtn.setPreferredSize(new Dimension(160, 38));
            editBtn.setEnabled(false);
            editBtn.addActionListener(e -> {
                if (currentStat != null && onEditClick != null) {
                    onEditClick.accept(currentStat);
                }
            });

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            footer.setOpaque(false);
            footer.add(editBtn);

            add(topHead, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
        }

        void stopAnimations() {
            distPanel.stopAnimation();
        }

        void showStat(QuestionAnalyticsDAO.QuestionStat stat, int minSample) {
            this.currentStat = stat;
            if (stat == null) {
                editBtn.setEnabled(false);
                return;
            }
            QuestionAnalyticsDAO.QuestionFlag flag = stat.evaluateFlag(minSample);
            titleLbl.setText("Question #" + stat.questionId + (stat.deleted ? " (Deleted)" : ""));

            badgeStrip.removeAll();
            badgeStrip.add(UITheme.createBadge(stat.subjectName, UITheme.PRIMARY_SOFT, UITheme.PRIMARY));
            badgeStrip.add(UITheme.createDifficultyBadge(stat.difficulty));
            badgeStrip.add(UITheme.createBadge(ExamStore.formatQuestionTypeLabel(stat.questionType), UITheme.VIOLET_SOFT, UITheme.VIOLET));
            badgeStrip.add(createFlagBadge(flag.displayLabel));
            badgeStrip.revalidate();
            badgeStrip.repaint();

            fullQuestionLbl.setText("<html><div style='line-height:1.4;'>" + escapeHtml(stat.questionText) + "</div></html>");
            statAskedLbl.setText(String.valueOf(stat.timesAsked));
            statCorrectLbl.setText(String.format("%.1f%% (%d)", stat.percentCorrect, stat.timesCorrect));
            statSkippedLbl.setText(String.valueOf(stat.timesSkipped));

            distPanel.setStat(stat);

            if (stat.supportsOptionDistribution() && stat.mostChosenWrongOption > 0 && stat.mostChosenWrongCount > 0) {
                long roundedPct = Math.round(stat.mostChosenWrongPct);
                String letter = stat.getMostChosenWrongLetter();
                String optLabel = ExamStore.TYPE_TRUE_FALSE.equals(stat.questionType)
                        ? (stat.mostChosenWrongOption == 1 ? "True" : "False")
                        : ("option " + letter);
                distractorLbl.setText("<html>" + roundedPct + "% chose " + optLabel
                        + " (" + stat.mostChosenWrongCount + "/" + stat.timesAsked + "), likely a misleading distractor</html>");
                distractorBanner.setVisible(true);
            } else {
                distractorBanner.setVisible(false);
            }

            editBtn.setEnabled(!stat.deleted);
            editBtn.setText(stat.deleted ? "Question Deleted" : "Edit Question");
            revalidate();
            repaint();
        }

        private static JPanel miniPill(String title, JLabel val, Color bg, Color fg) {
            UITheme.RoundedPanel box = new UITheme.RoundedPanel(10, bg, UITheme.GLASS_SLATE_BORDER);
            box.setLayout(new BorderLayout(0, 2));
            box.setBorder(new EmptyBorder(6, 6, 6, 6));
            JLabel t = new JLabel(title, SwingConstants.CENTER);
            t.setFont(UITheme.font(Font.BOLD, 9.5f));
            t.setForeground(fg);
            val.setFont(UITheme.font(Font.BOLD, 13.5f));
            val.setForeground(fg);
            box.add(t, BorderLayout.NORTH);
            box.add(val, BorderLayout.CENTER);
            return box;
        }
    }

    // ================= ANIMATED OPTION DISTRIBUTION BARS =================

    private static class OptionDistributionPanel extends JComponent {
        private QuestionAnalyticsDAO.QuestionStat stat;
        private float progress = 1f;
        private javax.swing.Timer animTimer;

        OptionDistributionPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(340, 200));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        }

        void setStat(QuestionAnalyticsDAO.QuestionStat stat) {
            this.stat = stat;
            if (stat == null || !stat.supportsOptionDistribution()) {
                setPreferredSize(new Dimension(340, 72));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            } else {
                int rows = ExamStore.TYPE_TRUE_FALSE.equals(stat.questionType) ? 2 : 4;
                int h = rows * 48 + 8;
                setPreferredSize(new Dimension(340, h));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 8));
            }
            revalidate();
            stopAnimation();
            progress = 0f;
            animTimer = Anim.animate(400, p -> {
                progress = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                progress = 1f;
                repaint();
            });
        }

        void stopAnimation() {
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
        }

        @Override
        public void removeNotify() {
            stopAnimation();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            if (stat == null) {
                g2.dispose();
                return;
            }

            if (!stat.supportsOptionDistribution()) {
                Shape box = new RoundRectangle2D.Float(1, 4, w - 2, Math.max(54, h - 10), 12, 12);
                g2.setColor(UITheme.BG_SUBTLE);
                g2.fill(box);
                g2.setColor(UITheme.BORDER);
                g2.draw(box);
                g2.setFont(UITheme.font(Font.BOLD, 12.5f));
                g2.setColor(UITheme.TEXT_MUTED);
                String msg = "Not available for this question type";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, Math.max(12, (w - fm.stringWidth(msg)) / 2), Math.max(34, h / 2 + 4));
                g2.dispose();
                return;
            }

            int numOpts = ExamStore.TYPE_TRUE_FALSE.equals(stat.questionType) ? 2 : 4;
            int asked = Math.max(1, stat.timesAsked);
            int rowH = 46;

            for (int i = 0; i < numOpts; i++) {
                int oneBased = i + 1;
                boolean isCorrect = (oneBased == stat.correctOption);
                boolean isTopWrong = (oneBased == stat.mostChosenWrongOption && stat.mostChosenWrongCount > 0);
                int cnt = stat.optionCounts[i];
                double pct = (cnt * 100.0) / asked;

                int y = 4 + i * rowH;

                Color fill = isCorrect ? UITheme.SUCCESS : (isTopWrong ? UITheme.DANGER : UITheme.PRIMARY);
                Color soft = isCorrect ? UITheme.SUCCESS_SOFT : (isTopWrong ? UITheme.DANGER_SOFT : new Color(241, 245, 249));
                Color fg = isCorrect ? UITheme.SUCCESS_TEXT : (isTopWrong ? UITheme.DANGER_TEXT : UITheme.TEXT_PRIMARY);

                String rawOpt = (stat.options != null && i < stat.options.length) ? stat.options[i] : ("Option " + OPTION_LETTERS[i]);
                String prefix = ExamStore.TYPE_TRUE_FALSE.equals(stat.questionType) ? "" : (OPTION_LETTERS[i] + ") ");
                String tag = isCorrect ? " \u2713" : (isTopWrong ? " (Top Wrong)" : "");
                String leftTxt = truncate(prefix + rawOpt, 34) + tag;

                g2.setFont(UITheme.font((isCorrect || isTopWrong) ? Font.BOLD : Font.PLAIN, 12f));
                g2.setColor(fg);
                g2.drawString(leftTxt, 2, y + 14);

                String rightTxt = String.format("%.0f%% (%d)", pct, cnt);
                g2.setFont(UITheme.font(Font.BOLD, 11.5f));
                FontMetrics fmR = g2.getFontMetrics();
                g2.drawString(rightTxt, w - 2 - fmR.stringWidth(rightTxt), y + 14);

                int barY = y + 22;
                int barH = 12;
                int barW = Math.max(40, w - 4);

                g2.setColor(soft);
                g2.fill(new RoundRectangle2D.Float(2, barY, barW, barH, barH, barH));

                if (pct > 0.0) {
                    float fw = (float) (barW * (pct / 100.0) * progress);
                    g2.setColor(fill);
                    g2.fill(new RoundRectangle2D.Float(2, barY, Math.max(barH * 0.7f, fw), barH, barH, barH));
                }
            }

            g2.dispose();
        }
    }

    // ================= FRIENDLY PASTEL EMPTY-STATE ILLUSTRATION =================

    private static class EmptyAnalyticsIllustration extends JComponent {
        EmptyAnalyticsIllustration() {
            setPreferredSize(new Dimension(150, 120));
            setMaximumSize(new Dimension(150, 120));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            // Soft pastel backdrop circle
            g2.setColor(UITheme.PRIMARY_SOFT);
            g2.fill(new Ellipse2D.Float(25, 10, 100, 100));

            // Mini analytics card
            Shape board = new RoundRectangle2D.Float(36, 20, 78, 84, 14, 14);
            g2.setColor(Color.WHITE);
            g2.fill(board);
            g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 110));
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(board);

            // Clipboard top clip
            g2.setPaint(UITheme.createPrimaryGradient(56, 14, 94, 26));
            g2.fill(new RoundRectangle2D.Float(57, 14, 36, 12, 6, 6));

            // Mini bar chart inside card
            g2.setColor(UITheme.SKY);
            g2.fill(new RoundRectangle2D.Float(48, 66, 12, 24, 5, 5));
            g2.setColor(UITheme.PRIMARY);
            g2.fill(new RoundRectangle2D.Float(66, 48, 12, 42, 5, 5));
            g2.setColor(UITheme.VIOLET);
            g2.fill(new RoundRectangle2D.Float(84, 58, 12, 32, 5, 5));

            // Floating pastel accent dots
            g2.setColor(UITheme.PINK);
            g2.fill(new Ellipse2D.Float(122, 24, 10, 10));
            g2.setColor(UITheme.SUCCESS);
            g2.fill(new Ellipse2D.Float(16, 72, 8, 8));

            g2.dispose();
        }
    }
}
