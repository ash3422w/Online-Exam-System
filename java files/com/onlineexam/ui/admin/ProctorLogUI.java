package com.onlineexam.ui.admin;

import com.onlineexam.dao.ExamDAO;
import com.onlineexam.dao.ProctorDAO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ProctorLogUI extends JFrame {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JTabbedPane tabbedPane;
    private final JTextField studentField;
    private final JComboBox<FilterItem<Integer>> examBox;
    private final JComboBox<String> eventTypeBox;
    private final JTextField fromDateField;
    private final JTextField toDateField;
    private final JPanel attemptFilterPill;
    private final JLabel attemptFilterLabel;

    private Integer filterAttemptId;
    private boolean initializingFilters = true;

    // Tab 1: Event Log
    private final DefaultTableModel eventModel;
    private final JTable eventTable;
    private final TableRowSorter<DefaultTableModel> eventSorter;
    private List<ProctorDAO.ProctorEvent> currentEvents = new ArrayList<>();

    // Tab 2: By Attempt
    private final DefaultTableModel attemptModel;
    private final JTable attemptTable;
    private final TableRowSorter<DefaultTableModel> attemptSorter;
    private List<ProctorDAO.AttemptProctorSummary> currentSummaries = new ArrayList<>();

    // Tab 2 KPIs
    private final JLabel kpiTotalSessionsVal;
    private final JLabel kpiHighRiskVal;
    private final JLabel kpiMedRiskVal;
    private final JLabel kpiLowRiskVal;
    private final JLabel kpiAutoSubmitVal;

    // Tab 3: Snapshots
    private final JComboBox<FilterItem<Integer>> snapshotAttemptBox;
    private final JLabel snapshotCountBadge;
    private final JPanel snapshotGridPanel;
    private final JSpinner retentionSpinner;
    private List<ProctorDAO.ProctorSnapshot> currentSnapshots = new ArrayList<>();

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

    public ProctorLogUI() {
        this(null);
    }

    public ProctorLogUI(int attemptId) {
        this(Integer.valueOf(attemptId));
    }

    public ProctorLogUI(Integer initialAttemptId) {
        this.filterAttemptId = (initialAttemptId != null && initialAttemptId > 0) ? initialAttemptId : null;

        setTitle("Proctoring Audit Log & Security Events - Online Exam System");
        setSize(1240, 790);
        setMinimumSize(new Dimension(1020, 660));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.48f);

        // ================= TOP BAR =================
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

        JPanel titleBlock = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleBlock.setOpaque(false);

        JComponent crest = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setPaint(UITheme.createPrimaryGradient(0, 0, 36, 36));
                g2.fill(new RoundRectangle2D.Float(0, 0, 36, 36, 10, 10));
                Icon icon = UITheme.createSwingIcon(UITheme.IconType.CHECKLIST, Color.WHITE, 20);
                icon.paintIcon(this, g2, 8, 8);
                g2.dispose();
            }
        };
        crest.setPreferredSize(new Dimension(36, 36));

        JPanel titleStack = new JPanel(new GridLayout(2, 1, 0, 2));
        titleStack.setOpaque(false);
        JLabel titleLbl = new JLabel("Proctoring & Audit Trail");
        titleLbl.setFont(UITheme.font(Font.BOLD, 18f));
        titleLbl.setForeground(UITheme.TEXT_PRIMARY);

        JLabel subLbl = new JLabel("Audit window focus losses, minimization, copy/paste attempts, right-clicks, and auto-submits");
        subLbl.setFont(UITheme.font(Font.PLAIN, 12f));
        subLbl.setForeground(UITheme.TEXT_SECONDARY);
        titleStack.add(titleLbl);
        titleStack.add(subLbl);

        titleBlock.add(crest);
        titleBlock.add(titleStack);
        topBar.add(titleBlock, BorderLayout.WEST);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topActions.setOpaque(false);

        UITheme.ModernButton exportCsvBtn = UITheme.secondaryButton("Export CSV");
        exportCsvBtn.setFont(UITheme.font(Font.BOLD, 12.5f));
        exportCsvBtn.setPreferredSize(new Dimension(116, 36));
        exportCsvBtn.addActionListener(e -> exportActiveTabCsv());

        UITheme.ModernButton refreshBtn = UITheme.secondaryButton("Refresh");
        refreshBtn.setFont(UITheme.font(Font.BOLD, 12.5f));
        refreshBtn.setPreferredSize(new Dimension(96, 36));
        refreshBtn.addActionListener(e -> reloadData());

        UITheme.ModernButton closeBtn = UITheme.secondaryButton("\u2190 Close");
        closeBtn.setFont(UITheme.font(Font.BOLD, 12.5f));
        closeBtn.setPreferredSize(new Dimension(96, 36));
        closeBtn.addActionListener(e -> dispose());

        topActions.add(exportCsvBtn);
        topActions.add(refreshBtn);
        topActions.add(closeBtn);
        topBar.add(topActions, BorderLayout.EAST);

        // ================= FILTER STRIP =================
        GlassPanel filterPanel = new GlassPanel(14);
        filterPanel.setSpotlightEnabled(false);
        filterPanel.setShadowColor(UITheme.PRIMARY);
        filterPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterPanel.setBorder(new EmptyBorder(8, 14, 8, 14));

        studentField = new JTextField(10);
        studentField.setFont(UITheme.font(Font.PLAIN, 12.5f));
        studentField.setToolTipText("Filter by Student ID");
        studentField.addActionListener(e -> reloadData());

        examBox = new JComboBox<>();
        examBox.setFont(UITheme.font(Font.PLAIN, 12.5f));
        examBox.setPreferredSize(new Dimension(180, 32));
        examBox.addItem(new FilterItem<>(null, "All Exams"));
        try {
            List<ExamConfig> allExams = ExamDAO.getAllExams();
            for (ExamConfig ec : allExams) {
                examBox.addItem(new FilterItem<>(ec.id, ec.title));
            }
        } catch (Exception ignored) {}
        examBox.addActionListener(e -> {
            if (!initializingFilters) reloadData();
        });

        String[] eventTypes = {
                "All Events",
                ProctorDAO.EVENT_FOCUS_LOST,
                ProctorDAO.EVENT_WINDOW_MINIMIZED,
                ProctorDAO.EVENT_COPY_ATTEMPT,
                ProctorDAO.EVENT_PASTE_ATTEMPT,
                ProctorDAO.EVENT_CUT_ATTEMPT,
                ProctorDAO.EVENT_RIGHT_CLICK,
                ProctorDAO.EVENT_PRINT_SCREEN_KEY,
                ProctorDAO.EVENT_AUTO_SUBMIT_PROCTOR,
                ProctorDAO.EVENT_CAMERA_LOST,
                ProctorDAO.EVENT_EXAM_STARTED,
                ProctorDAO.EVENT_EXAM_SUBMITTED
        };
        eventTypeBox = new JComboBox<>(eventTypes);
        eventTypeBox.setFont(UITheme.font(Font.PLAIN, 12.5f));
        eventTypeBox.setPreferredSize(new Dimension(170, 32));
        eventTypeBox.addActionListener(e -> {
            if (!initializingFilters) reloadData();
        });

        fromDateField = new JTextField(8);
        fromDateField.setFont(UITheme.font(Font.PLAIN, 12f));
        fromDateField.setToolTipText("From date (YYYY-MM-DD)");
        fromDateField.addActionListener(e -> reloadData());

        toDateField = new JTextField(8);
        toDateField.setFont(UITheme.font(Font.PLAIN, 12f));
        toDateField.setToolTipText("To date (YYYY-MM-DD)");
        toDateField.addActionListener(e -> reloadData());

        // Attempt filter badge pill
        attemptFilterPill = new UITheme.RoundedPanel(10, UITheme.PRIMARY_SOFT, Anim.withAlpha(UITheme.PRIMARY, 90));
        attemptFilterPill.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        attemptFilterLabel = new JLabel("Attempt #" + (filterAttemptId != null ? filterAttemptId : ""));
        attemptFilterLabel.setFont(UITheme.font(Font.BOLD, 12f));
        attemptFilterLabel.setForeground(UITheme.PRIMARY);
        JButton clearAttemptFilterBtn = new JButton("\u2715");
        clearAttemptFilterBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        clearAttemptFilterBtn.setForeground(UITheme.TEXT_MUTED);
        clearAttemptFilterBtn.setContentAreaFilled(false);
        clearAttemptFilterBtn.setBorderPainted(false);
        clearAttemptFilterBtn.setFocusPainted(false);
        clearAttemptFilterBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearAttemptFilterBtn.setToolTipText("Clear attempt filter");
        clearAttemptFilterBtn.addActionListener(e -> {
            filterAttemptId = null;
            attemptFilterPill.setVisible(false);
            reloadData();
        });
        attemptFilterPill.add(attemptFilterLabel);
        attemptFilterPill.add(clearAttemptFilterBtn);
        attemptFilterPill.setVisible(filterAttemptId != null);

        UITheme.ModernButton applyFilterBtn = UITheme.gradientPrimaryButton("Filter");
        applyFilterBtn.setFont(UITheme.font(Font.BOLD, 12f));
        applyFilterBtn.setPreferredSize(new Dimension(80, 32));
        applyFilterBtn.addActionListener(e -> reloadData());

        UITheme.ModernButton resetBtn = UITheme.secondaryButton("Reset");
        resetBtn.setFont(UITheme.font(Font.BOLD, 12f));
        resetBtn.setPreferredSize(new Dimension(80, 32));
        resetBtn.addActionListener(e -> {
            initializingFilters = true;
            studentField.setText("");
            examBox.setSelectedIndex(0);
            eventTypeBox.setSelectedIndex(0);
            fromDateField.setText("");
            toDateField.setText("");
            filterAttemptId = null;
            attemptFilterPill.setVisible(false);
            initializingFilters = false;
            reloadData();
        });

        filterPanel.add(new JLabel("Student:"));
        filterPanel.add(studentField);
        filterPanel.add(new JLabel("Exam:"));
        filterPanel.add(examBox);
        filterPanel.add(new JLabel("Event:"));
        filterPanel.add(eventTypeBox);
        filterPanel.add(new JLabel("From:"));
        filterPanel.add(fromDateField);
        filterPanel.add(new JLabel("To:"));
        filterPanel.add(toDateField);
        filterPanel.add(attemptFilterPill);
        filterPanel.add(applyFilterBtn);
        filterPanel.add(resetBtn);

        // ================= TABS =================
        tabbedPane = new UITheme.ModernTabbedPane();

        // ---------------- TAB 1: EVENT LOG TABLE ----------------
        String[] eventCols = {"Time", "Student ID", "Exam", "Attempt #", "Event Type", "Details"};
        eventModel = new DefaultTableModel(eventCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        eventTable = new JTable(eventModel);
        UITheme.styleTable(eventTable);
        eventSorter = new TableRowSorter<>(eventModel);
        eventTable.setRowSorter(eventSorter);
        eventTable.setRowHeight(34);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        eventTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        eventTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        eventTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        eventTable.getColumnModel().getColumn(4).setPreferredWidth(160);
        eventTable.getColumnModel().getColumn(5).setPreferredWidth(450);

        eventTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel) {
                    JLabel lbl = (JLabel) c;
                    lbl.setHorizontalAlignment((column == 0 || column == 3 || column == 4) ? SwingConstants.CENTER : SwingConstants.LEFT);
                    lbl.setBorder(new EmptyBorder(0, 10, 0, 10));
                }
                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                c.setFont(UITheme.font(column == 1 ? Font.BOLD : Font.PLAIN, 12.5f));
                return c;
            }
        });

        eventTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String ev = value != null ? value.toString() : "";
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));

                if (ProctorDAO.EVENT_AUTO_SUBMIT_PROCTOR.equalsIgnoreCase(ev)) {
                    cell.add(UITheme.createBadge(ev, UITheme.DANGER_SOFT, UITheme.DANGER_TEXT));
                } else if (ProctorDAO.EVENT_CAMERA_LOST.equalsIgnoreCase(ev)) {
                    cell.add(UITheme.createBadge(ev, UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
                } else if (ProctorDAO.EVENT_FOCUS_LOST.equalsIgnoreCase(ev)
                        || ProctorDAO.EVENT_WINDOW_MINIMIZED.equalsIgnoreCase(ev)) {
                    cell.add(UITheme.createBadge(ev, UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
                } else if (ProctorDAO.EVENT_COPY_ATTEMPT.equalsIgnoreCase(ev)
                        || ProctorDAO.EVENT_PASTE_ATTEMPT.equalsIgnoreCase(ev)
                        || ProctorDAO.EVENT_CUT_ATTEMPT.equalsIgnoreCase(ev)) {
                    cell.add(UITheme.createBadge(ev, UITheme.PINK_SOFT, UITheme.PINK_HOVER));
                } else if (ProctorDAO.EVENT_RIGHT_CLICK.equalsIgnoreCase(ev)
                        || ProctorDAO.EVENT_PRINT_SCREEN_KEY.equalsIgnoreCase(ev)) {
                    cell.add(UITheme.createBadge(ev, UITheme.VIOLET_SOFT, UITheme.VIOLET));
                } else {
                    cell.add(UITheme.createBadge(ev, UITheme.PRIMARY_SOFT, UITheme.PRIMARY));
                }
                return cell;
            }
        });

        JPanel eventTabPanel = new JPanel(new BorderLayout(0, 8));
        eventTabPanel.setOpaque(false);
        eventTabPanel.setBorder(new EmptyBorder(12, 14, 12, 14));
        eventTabPanel.add(UITheme.styleScrollPane(eventTable), BorderLayout.CENTER);

        // ---------------- TAB 2: BY ATTEMPT SUMMARY TABLE + KPIS ----------------
        JPanel attemptTabPanel = new JPanel(new BorderLayout(0, 12));
        attemptTabPanel.setOpaque(false);
        attemptTabPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // KPI Strip
        JPanel kpiStrip = new JPanel(new GridLayout(1, 5, 12, 0));
        kpiStrip.setOpaque(false);
        kpiStrip.setPreferredSize(new Dimension(1000, 78));

        kpiTotalSessionsVal = new JLabel("0", SwingConstants.CENTER);
        kpiHighRiskVal = new JLabel("0", SwingConstants.CENTER);
        kpiMedRiskVal = new JLabel("0", SwingConstants.CENTER);
        kpiLowRiskVal = new JLabel("0", SwingConstants.CENTER);
        kpiAutoSubmitVal = new JLabel("0", SwingConstants.CENTER);

        kpiStrip.add(buildMiniKpi("Total Sessions", kpiTotalSessionsVal, UITheme.PRIMARY, UITheme.PRIMARY_SOFT));
        kpiStrip.add(buildMiniKpi("High Risk (5+ / Auto)", kpiHighRiskVal, UITheme.DANGER_TEXT, UITheme.DANGER_SOFT));
        kpiStrip.add(buildMiniKpi("Medium Risk (2-4)", kpiMedRiskVal, UITheme.WARNING_TEXT, UITheme.WARNING_SOFT));
        kpiStrip.add(buildMiniKpi("Low Risk (0-1)", kpiLowRiskVal, UITheme.SUCCESS_TEXT, UITheme.SUCCESS_SOFT));
        kpiStrip.add(buildMiniKpi("Auto-Submitted", kpiAutoSubmitVal, UITheme.PINK_HOVER, UITheme.PINK_SOFT));

        attemptTabPanel.add(kpiStrip, BorderLayout.NORTH);

        String[] attemptCols = {
                "Attempt #", "Student ID", "Exam", "Focus Lost", "Minimized",
                "Copy/Paste", "Right-Click", "PrintScreen", "Auto-Submit", "Violations",
                "Risk Level", "Start Time", "End Time"
        };
        attemptModel = new DefaultTableModel(attemptCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        attemptTable = new JTable(attemptModel);
        UITheme.styleTable(attemptTable);
        attemptSorter = new TableRowSorter<>(attemptModel);
        attemptTable.setRowSorter(attemptSorter);
        attemptTable.setRowHeight(34);

        attemptTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel) {
                    JLabel lbl = (JLabel) c;
                    lbl.setHorizontalAlignment((column == 1 || column == 2) ? SwingConstants.LEFT : SwingConstants.CENTER);
                    lbl.setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                c.setFont(UITheme.font((column == 1 || column == 9) ? Font.BOLD : Font.PLAIN, 12.5f));
                return c;
            }
        });

        // Risk badge renderer
        attemptTable.getColumnModel().getColumn(10).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String risk = value != null ? value.toString() : "Low";
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));

                if ("High".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("High Risk", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT));
                } else if ("Medium".equalsIgnoreCase(risk)) {
                    cell.add(UITheme.createBadge("Medium Risk", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT));
                } else {
                    cell.add(UITheme.createBadge("Low Risk", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));
                }
                return cell;
            }
        });

        // Double-click on an attempt row drills down into Tab 1 with that attempt's events!
        attemptTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && attemptTable.getSelectedRow() >= 0) {
                    int modelRow = attemptTable.convertRowIndexToModel(attemptTable.getSelectedRow());
                    if (modelRow >= 0 && modelRow < currentSummaries.size()) {
                        ProctorDAO.AttemptProctorSummary s = currentSummaries.get(modelRow);
                        drillDownToAttempt(s.attemptId);
                    }
                }
            }
        });

        JLabel drillHint = new JLabel("Double-click any attempt row to view detailed event history in Tab 1");
        drillHint.setFont(UITheme.font(Font.PLAIN, 11.5f));
        drillHint.setForeground(UITheme.TEXT_MUTED);

        JPanel attemptCenter = new JPanel(new BorderLayout(0, 6));
        attemptCenter.setOpaque(false);
        attemptCenter.add(UITheme.styleScrollPane(attemptTable), BorderLayout.CENTER);
        attemptCenter.add(drillHint, BorderLayout.SOUTH);

        attemptTabPanel.add(attemptCenter, BorderLayout.CENTER);

        // ---------------- TAB 3: SNAPSHOTS THUMBNAIL GRID ----------------
        JPanel snapshotsTabPanel = new JPanel(new BorderLayout(0, 10));
        snapshotsTabPanel.setOpaque(false);
        snapshotsTabPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel snapHeader = new JPanel(new BorderLayout(12, 0));
        snapHeader.setOpaque(false);

        JPanel snapLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        snapLeft.setOpaque(false);

        JLabel snapAttLbl = UITheme.createFieldLabel("Filter by Attempt:");
        snapshotAttemptBox = new JComboBox<>();
        snapshotAttemptBox.setFont(UITheme.font(Font.PLAIN, 12.5f));
        snapshotAttemptBox.setPreferredSize(new Dimension(200, 32));
        snapshotAttemptBox.addItem(new FilterItem<>(null, "All Captured Attempts"));
        snapshotAttemptBox.addActionListener(e -> {
            if (!initializingFilters) reloadSnapshotsOnly();
        });

        snapshotCountBadge = UITheme.createBadge("0 Snapshots", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);

        snapLeft.add(snapAttLbl);
        snapLeft.add(snapshotAttemptBox);
        snapLeft.add(snapshotCountBadge);

        JPanel snapRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        snapRight.setOpaque(false);

        JLabel retLbl = UITheme.createFieldLabel("Retention:");
        retentionSpinner = new JSpinner(new SpinnerNumberModel(ProctorDAO.getSnapshotRetentionDays(), 1, 365, 1));
        retentionSpinner.setPreferredSize(new Dimension(60, 30));

        JLabel daysLbl = new JLabel("days");
        daysLbl.setFont(UITheme.font(Font.BOLD, 12f));
        daysLbl.setForeground(UITheme.TEXT_SECONDARY);

        UITheme.ModernButton purgeBtn = UITheme.secondaryButton("Purge Old Snapshots");
        purgeBtn.setPreferredSize(new Dimension(170, 32));
        purgeBtn.setToolTipText("Deletes snapshots older than the specified retention days");
        purgeBtn.addActionListener(e -> {
            int days = ((Number) retentionSpinner.getValue()).intValue();
            ProctorDAO.setSnapshotRetentionDays(days);
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() {
                    return ProctorDAO.purgeOldSnapshots(days);
                }
                @Override
                protected void done() {
                    try {
                        int count = get();
                        UITheme.showToast(ProctorLogUI.this, "Retention Purge Completed",
                                "Purged " + count + " snapshot(s) older than " + days + " days.",
                                UITheme.ToastType.SUCCESS);
                        reloadData();
                    } catch (Exception ex) {
                        UITheme.showToast(ProctorLogUI.this, "Purge Failed", ex.getMessage(), UITheme.ToastType.ERROR);
                    }
                }
            }.execute();
        });

        snapRight.add(retLbl);
        snapRight.add(retentionSpinner);
        snapRight.add(daysLbl);
        snapRight.add(purgeBtn);

        snapHeader.add(snapLeft, BorderLayout.WEST);
        snapHeader.add(snapRight, BorderLayout.EAST);

        snapshotGridPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 16));
        snapshotGridPanel.setOpaque(false);

        JScrollPane snapScroll = UITheme.styleScrollPane(snapshotGridPanel);
        snapScroll.getVerticalScrollBar().setUnitIncrement(16);

        snapshotsTabPanel.add(snapHeader, BorderLayout.NORTH);
        snapshotsTabPanel.add(snapScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Event Log", eventTabPanel);
        tabbedPane.addTab("By Attempt", attemptTabPanel);
        tabbedPane.addTab("Snapshots", snapshotsTabPanel);

        // Content wrapper
        JPanel workspace = new JPanel(new BorderLayout(0, 12));
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(12, 28, 16, 28));
        workspace.add(filterPanel, BorderLayout.NORTH);
        workspace.add(tabbedPane, BorderLayout.CENTER);

        root.add(topBar, BorderLayout.NORTH);
        root.add(workspace, BorderLayout.CENTER);

        add(root);
        initializingFilters = false;
        reloadData();
        setVisible(true);
    }

    private JPanel buildMiniKpi(String label, JLabel valLabel, Color accent, Color bg) {
        UITheme.RoundedPanel p = new UITheme.RoundedPanel(12, bg, UITheme.GLASS_SLATE_BORDER);
        p.setLayout(new BorderLayout(0, 3));
        p.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel l = new JLabel(label);
        l.setFont(UITheme.font(Font.BOLD, 11f));
        l.setForeground(accent);

        valLabel.setFont(UITheme.font(Font.BOLD, 22f));
        valLabel.setForeground(accent);

        p.add(l, BorderLayout.NORTH);
        p.add(valLabel, BorderLayout.CENTER);
        return p;
    }

    private void drillDownToAttempt(Integer attemptId) {
        if (attemptId == null || attemptId <= 0) {
            UITheme.showToast(this, "Notice", "This session was interrupted before completion (no attempt #).", UITheme.ToastType.INFO);
            return;
        }
        this.filterAttemptId = attemptId;
        attemptFilterLabel.setText("Attempt #" + attemptId);
        attemptFilterPill.setVisible(true);
        tabbedPane.setSelectedIndex(0); // Switch to Event Log
        reloadData();
    }

    private void reloadData() {
        String student = studentField.getText().trim();
        FilterItem<Integer> selExam = (FilterItem<Integer>) examBox.getSelectedItem();
        Integer examId = selExam != null ? selExam.value : null;
        String evType = (String) eventTypeBox.getSelectedItem();
        if ("All Events".equalsIgnoreCase(evType)) evType = null;

        LocalDate fromDate = null;
        String fromStr = fromDateField.getText().trim();
        if (!fromStr.isEmpty()) {
            try {
                fromDate = LocalDate.parse(fromStr, ISO_DATE);
            } catch (Exception ignored) {}
        }

        LocalDate toDate = null;
        String toStr = toDateField.getText().trim();
        if (!toStr.isEmpty()) {
            try {
                toDate = LocalDate.parse(toStr, ISO_DATE);
            } catch (Exception ignored) {}
        }

        final String fUser = student.isEmpty() ? null : student;
        final Integer fExamId = examId;
        final String fEvType = evType;
        final LocalDate fFrom = fromDate;
        final LocalDate fTo = toDate;
        final Integer fAttemptId = filterAttemptId;

        new SwingWorker<Void, Void>() {
            private List<ProctorDAO.ProctorEvent> events;
            private List<ProctorDAO.AttemptProctorSummary> summaries;
            private List<ProctorDAO.ProctorSnapshot> snapshots;
            private List<Integer> distinctAtts;

            @Override
            protected Void doInBackground() {
                events = ProctorDAO.getEvents(fUser, fExamId, fEvType, fFrom, fTo, fAttemptId);
                summaries = ProctorDAO.getAttemptSummaries(fUser, fExamId, fFrom, fTo);
                snapshots = ProctorDAO.getSnapshots(fAttemptId, null, fUser);
                distinctAtts = ProctorDAO.getDistinctAttemptIdsWithSnapshots();
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                currentEvents = events != null ? events : new ArrayList<>();
                currentSummaries = summaries != null ? summaries : new ArrayList<>();
                currentSnapshots = snapshots != null ? snapshots : new ArrayList<>();

                // Populate Tab 1: Event Log
                eventModel.setRowCount(0);
                for (ProctorDAO.ProctorEvent pe : currentEvents) {
                    eventModel.addRow(new Object[]{
                            pe.formattedTime,
                            pe.userId,
                            pe.examTitle,
                            pe.attemptId != null ? ("#" + pe.attemptId) : "-",
                            pe.eventType,
                            pe.details
                    });
                }

                // Populate Tab 2: By Attempt
                attemptModel.setRowCount(0);
                int totalSessions = currentSummaries.size();
                int highRisk = 0;
                int medRisk = 0;
                int lowRisk = 0;
                int autoSubmits = 0;

                for (ProctorDAO.AttemptProctorSummary s : currentSummaries) {
                    if (s.riskLevel == ProctorDAO.RiskLevel.HIGH) highRisk++;
                    else if (s.riskLevel == ProctorDAO.RiskLevel.MEDIUM) medRisk++;
                    else lowRisk++;
                    if (s.autoSubmitted) autoSubmits++;

                    attemptModel.addRow(new Object[]{
                            s.attemptId != null ? ("#" + s.attemptId) : ("(Session " + s.examSessionId.substring(0, Math.min(8, s.examSessionId.length())) + ")"),
                            s.userId,
                            s.examTitle,
                            s.focusLostCount,
                            s.windowMinimizedCount,
                            s.copyPasteCount,
                            s.rightClickCount,
                            s.printScreenCount,
                            s.autoSubmitCount > 0 ? ("Yes (" + s.autoSubmitCount + ")") : "No",
                            s.totalViolations,
                            s.riskLevel.getDisplay(),
                            s.startTime,
                            s.endTime
                    });
                }

                // Update KPIs
                Anim.countUp(kpiTotalSessionsVal, 0, totalSessions, 500, "%d");
                Anim.countUp(kpiHighRiskVal, 0, highRisk, 500, "%d");
                Anim.countUp(kpiMedRiskVal, 0, medRisk, 500, "%d");
                Anim.countUp(kpiLowRiskVal, 0, lowRisk, 500, "%d");
                Anim.countUp(kpiAutoSubmitVal, 0, autoSubmits, 500, "%d");

                // Populate Tab 3: Snapshots
                FilterItem<Integer> prevSel = (FilterItem<Integer>) snapshotAttemptBox.getSelectedItem();
                Integer prevVal = prevSel != null ? prevSel.value : null;

                snapshotAttemptBox.removeAllItems();
                snapshotAttemptBox.addItem(new FilterItem<>(null, "All Captured Attempts"));
                FilterItem<Integer> toSelect = null;
                if (distinctAtts != null) {
                    for (Integer aId : distinctAtts) {
                        FilterItem<Integer> itm = new FilterItem<>(aId, "Attempt #" + aId);
                        snapshotAttemptBox.addItem(itm);
                        if (java.util.Objects.equals(aId, prevVal) || java.util.Objects.equals(aId, fAttemptId)) {
                            toSelect = itm;
                        }
                    }
                }
                if (toSelect != null) {
                    snapshotAttemptBox.setSelectedItem(toSelect);
                }

                renderSnapshotsGrid(currentSnapshots);
            }
        }.execute();
    }

    private void reloadSnapshotsOnly() {
        String student = studentField.getText().trim();
        final String fUser = student.isEmpty() ? null : student;
        FilterItem<Integer> selAtt = (FilterItem<Integer>) snapshotAttemptBox.getSelectedItem();
        final Integer fAttemptId = (selAtt != null && selAtt.value != null) ? selAtt.value : filterAttemptId;

        new SwingWorker<List<ProctorDAO.ProctorSnapshot>, Void>() {
            @Override
            protected List<ProctorDAO.ProctorSnapshot> doInBackground() {
                return ProctorDAO.getSnapshots(fAttemptId, null, fUser);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    currentSnapshots = get();
                    renderSnapshotsGrid(currentSnapshots);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    private void renderSnapshotsGrid(List<ProctorDAO.ProctorSnapshot> snapshots) {
        snapshotGridPanel.removeAll();
        if (snapshots == null || snapshots.isEmpty()) {
            JPanel emptyPanel = new JPanel();
            emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
            emptyPanel.setOpaque(false);
            emptyPanel.setBorder(new EmptyBorder(60, 40, 60, 40));

            JLabel emptyTitle = new JLabel("No Webcam Snapshots Found", SwingConstants.CENTER);
            emptyTitle.setFont(UITheme.font(Font.BOLD, 16f));
            emptyTitle.setForeground(UITheme.TEXT_SECONDARY);
            emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel emptySub = new JLabel("Snapshots are captured every 60-90s for exams with 'Require Webcam' enabled.", SwingConstants.CENTER);
            emptySub.setFont(UITheme.font(Font.PLAIN, 12f));
            emptySub.setForeground(UITheme.TEXT_MUTED);
            emptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

            emptyPanel.add(emptyTitle);
            emptyPanel.add(Box.createVerticalStrut(6));
            emptyPanel.add(emptySub);
            snapshotGridPanel.add(emptyPanel);
            snapshotCountBadge.setText("0 Snapshots");
            snapshotGridPanel.revalidate();
            snapshotGridPanel.repaint();
            return;
        }

        snapshotCountBadge.setText(snapshots.size() + " Snapshot" + (snapshots.size() == 1 ? "" : "s"));

        for (ProctorDAO.ProctorSnapshot snap : snapshots) {
            GlassPanel card = new GlassPanel(12);
            card.setLayout(new BorderLayout(0, 6));
            card.setPreferredSize(new Dimension(190, 185));
            card.setBorder(new EmptyBorder(8, 8, 8, 8));
            card.setCursor(new Cursor(Cursor.HAND_CURSOR));

            BufferedImage loadedImg = null;
            if (snap.imageData != null && snap.imageData.length > 0) {
                try {
                    loadedImg = ImageIO.read(new ByteArrayInputStream(snap.imageData));
                } catch (Exception ignored) {}
            }
            final BufferedImage finalImg = loadedImg;

            JPanel thumbView = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.configureGraphics(g2);
                    g2.setColor(new Color(24, 24, 27));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    if (finalImg != null) {
                        g2.drawImage(finalImg, 0, 0, getWidth(), getHeight(), null);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.setFont(UITheme.font(Font.PLAIN, 10f));
                        g2.drawString("No Preview", getWidth() / 2 - 25, getHeight() / 2);
                    }
                    g2.setColor(UITheme.GLASS_SLATE_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                }
            };
            thumbView.setPreferredSize(new Dimension(174, 115));
            thumbView.setOpaque(false);

            JPanel infoStack = new JPanel(new GridLayout(2, 1, 0, 2));
            infoStack.setOpaque(false);

            JPanel topInfo = new JPanel(new BorderLayout());
            topInfo.setOpaque(false);
            JLabel timeLbl = new JLabel(snap.formattedTime.length() > 11 ? snap.formattedTime.substring(11) : snap.formattedTime);
            timeLbl.setFont(UITheme.font(Font.BOLD, 11.5f));
            timeLbl.setForeground(UITheme.TEXT_PRIMARY);

            JLabel flagBadge = UITheme.createBadge(snap.flag, UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
            topInfo.add(timeLbl, BorderLayout.WEST);
            topInfo.add(flagBadge, BorderLayout.EAST);

            String userTxt = snap.userId + (snap.attemptId != null ? " (Att #" + snap.attemptId + ")" : "");
            JLabel userLbl = new JLabel(userTxt);
            userLbl.setFont(UITheme.font(Font.PLAIN, 10.5f));
            userLbl.setForeground(UITheme.TEXT_SECONDARY);

            infoStack.add(topInfo);
            infoStack.add(userLbl);

            card.add(thumbView, BorderLayout.CENTER);
            card.add(infoStack, BorderLayout.SOUTH);

            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openSnapshotEnlargement(snap, finalImg);
                }
            });

            snapshotGridPanel.add(card);
        }

        snapshotGridPanel.revalidate();
        snapshotGridPanel.repaint();
    }

    private void openSnapshotEnlargement(ProctorDAO.ProctorSnapshot snap, BufferedImage img) {
        if (snap == null) return;
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(10, 16, 10, 16));

        int dispW = 440;
        int dispH = 330;
        if (img != null) {
            dispW = Math.max(320, img.getWidth());
            dispH = Math.max(240, img.getHeight());
            if (dispW > 560) {
                dispH = (int) Math.round((double) dispH * 560 / dispW);
                dispW = 560;
            }
        }
        final int targetW = dispW;
        final int targetH = dispH;

        JPanel imgContainer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(24, 24, 27));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (img != null) {
                    g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.drawString("Image Not Available", getWidth() / 2 - 50, getHeight() / 2);
                }
                g2.setColor(UITheme.GLASS_SLATE_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        imgContainer.setPreferredSize(new Dimension(targetW, targetH));
        imgContainer.setMaximumSize(new Dimension(targetW, targetH));
        imgContainer.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(imgContainer);
        content.add(Box.createVerticalStrut(14));

        GlassPanel metaCard = new GlassPanel(10);
        metaCard.setLayout(new GridLayout(3, 2, 10, 6));
        metaCard.setBorder(new EmptyBorder(10, 14, 10, 14));
        metaCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        metaCard.setMaximumSize(new Dimension(targetW, 90));

        metaCard.add(createMetaField("Student ID:", snap.userId));
        metaCard.add(createMetaField("Attempt #:", snap.attemptId != null ? String.valueOf(snap.attemptId) : "In-Progress"));
        metaCard.add(createMetaField("Captured At:", snap.formattedTime));
        metaCard.add(createMetaField("Flag Type:", snap.flag));
        metaCard.add(createMetaField("Session ID:", snap.examSessionId.length() > 18 ? snap.examSessionId.substring(0, 18) + "..." : snap.examSessionId));
        metaCard.add(createMetaField("Resolution:", (img != null ? (img.getWidth() + "x" + img.getHeight() + " px") : "320x240")));

        content.add(metaCard);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        UITheme.ModernButton closeBtn = UITheme.secondaryButton("Close");
        footer.add(closeBtn);

        GlassDialog dialog = new GlassDialog(
                this,
                "Proctor Snapshot - " + snap.userId,
                "Captured during exam session " + snap.examSessionId,
                content,
                footer,
                Math.max(540, targetW + 60),
                targetH + 210
        );
        closeBtn.addActionListener(e -> dialog.closeAnimated(null));
        dialog.setVisible(true);
    }

    private static JPanel createMetaField(String label, String value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(UITheme.font(Font.BOLD, 11.5f));
        l.setForeground(UITheme.TEXT_MUTED);
        JLabel v = new JLabel(value != null ? value : "-");
        v.setFont(UITheme.font(Font.PLAIN, 12f));
        v.setForeground(UITheme.TEXT_PRIMARY);
        p.add(l);
        p.add(v);
        return p;
    }

    private void exportActiveTabCsv() {
        int activeTab = tabbedPane.getSelectedIndex();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Proctoring Data as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        String defaultName = (activeTab == 0) ? "proctor_event_log.csv"
                : (activeTab == 1 ? "proctor_attempt_summaries.csv" : "proctor_webcam_snapshots.csv");
        chooser.setSelectedFile(new File(defaultName));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM
            w.write('\ufeff');

            if (activeTab == 0) {
                // Event Log CSV
                w.write("Time,Student ID,Exam,Attempt ID,Event Type,Details");
                w.newLine();
                for (ProctorDAO.ProctorEvent pe : currentEvents) {
                    w.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                            pe.formattedTime,
                            pe.userId.replace("\"", "\"\""),
                            pe.examTitle.replace("\"", "\"\""),
                            pe.attemptId != null ? pe.attemptId.toString() : "",
                            pe.eventType.replace("\"", "\"\""),
                            pe.details.replace("\"", "\"\"")));
                    w.newLine();
                }
            } else if (activeTab == 1) {
                // By Attempt CSV
                w.write("Attempt ID,Session ID,Student ID,Exam,Focus Lost,Minimized,Copy/Paste,Right-Click,PrintScreen,Auto-Submit,Total Violations,Risk Level,Session Start,Session End");
                w.newLine();
                for (ProctorDAO.AttemptProctorSummary s : currentSummaries) {
                    w.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%d,%d,%s,%d,\"%s\",\"%s\",\"%s\"",
                            s.attemptId != null ? s.attemptId.toString() : "",
                            s.examSessionId,
                            s.userId.replace("\"", "\"\""),
                            s.examTitle.replace("\"", "\"\""),
                            s.focusLostCount,
                            s.windowMinimizedCount,
                            s.copyPasteCount,
                            s.rightClickCount,
                            s.printScreenCount,
                            s.autoSubmitted ? "YES" : "NO",
                            s.totalViolations,
                            s.riskLevel.getDisplay(),
                            s.startTime,
                            s.endTime));
                    w.newLine();
                }
            } else {
                // Snapshots CSV
                w.write("ID,Session ID,Student ID,Attempt ID,Taken At,Flag,Image Size (bytes)");
                w.newLine();
                for (ProctorDAO.ProctorSnapshot s : currentSnapshots) {
                    w.write(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d",
                            s.id,
                            s.examSessionId,
                            s.userId.replace("\"", "\"\""),
                            s.attemptId != null ? s.attemptId.toString() : "",
                            s.formattedTime,
                            s.flag,
                            s.imageData != null ? s.imageData.length : 0));
                    w.newLine();
                }
            }
            UITheme.showToast(this, "Export Succeeded", "Saved data to " + file.getName() + ".", UITheme.ToastType.SUCCESS);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
