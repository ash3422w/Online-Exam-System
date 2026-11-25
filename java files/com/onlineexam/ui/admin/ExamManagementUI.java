package com.onlineexam.ui.admin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.DBConnection;
import com.onlineexam.dao.ExamDAO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

public class ExamManagementUI extends JFrame {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final List<ExamConfig> loadedExams = new ArrayList<>();
    private final Map<Integer, String> subjectsMap = new LinkedHashMap<>();
    private final Map<Integer, Integer> uniqueCountsBySubject = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Integer>> uniqueByDiffBySubject = new LinkedHashMap<>();
    private final UITheme.ModernButton toggleActiveBtn;
    private final UITheme.ModernButton editBtn;
    private final JLabel summaryBadge;

    private static final class SubjectItem {
        final int id;
        final String name;

        SubjectItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public ExamManagementUI() {
        setTitle("Exam Management - Online Exam System");
        setSize(1160, 700);
        setMinimumSize(new Dimension(960, 580));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

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
        topBar.setBorder(new EmptyBorder(14, 24, 14, 24));

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);

        JLabel title = new JLabel("Exams & Assessment Rules");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel("Configure per-exam question counts, duration, marking, negative marking, retake limits, and answer visibility");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));

        headLeft.add(title);
        headLeft.add(sub);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        topRight.setOpaque(false);

        summaryBadge = UITheme.createBadge("Loading...", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
        UITheme.ModernButton closeBtn = UITheme.secondaryButton("\u2190 Close");
        closeBtn.addActionListener(e -> dispose());

        topRight.add(summaryBadge);
        topRight.add(closeBtn);

        topBar.add(headLeft, BorderLayout.WEST);
        topBar.add(topRight, BorderLayout.EAST);

        // Main Workspace
        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(16, 24, 20, 24));

        // Action Toolbar Card
        GlassPanel toolbar = new GlassPanel(14);
        toolbar.setSpotlightEnabled(false);
        toolbar.setShadowColor(UITheme.PRIMARY);
        toolbar.setLayout(new BorderLayout(12, 0));
        toolbar.setBorder(new EmptyBorder(11, 16, 11, 16));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftActions.setOpaque(false);

        UITheme.ModernButton createBtn = UITheme.gradientPrimaryButton("+ Create Exam");
        createBtn.addActionListener(e -> openExamEditor(null));

        editBtn = UITheme.secondaryButton("Edit Selected");
        editBtn.setEnabled(false);
        editBtn.addActionListener(e -> {
            ExamConfig sel = getSelectedExam();
            if (sel != null) openExamEditor(sel);
        });

        toggleActiveBtn = UITheme.secondaryButton("Deactivate");
        toggleActiveBtn.setEnabled(false);
        toggleActiveBtn.addActionListener(e -> toggleSelectedExamActive());

        leftActions.add(createBtn);
        leftActions.add(editBtn);
        leftActions.add(toggleActiveBtn);

        UITheme.ModernButton refreshBtn = UITheme.secondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadExamsAsync());

        toolbar.add(leftActions, BorderLayout.WEST);
        toolbar.add(refreshBtn, BorderLayout.EAST);

        // Table Glass Card
        GlassPanel tableCard = new GlassPanel(16);
        tableCard.setSpotlightEnabled(false);
        tableCard.setShadowColor(UITheme.PRIMARY);
        tableCard.setLayout(new BorderLayout(0, 10));
        tableCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        String[] cols = {
                "ID", "Exam Title", "Subject", "Qs", "Mix (E/M/H)", "Duration",
                "Marks/Q", "Neg", "Pass %", "Max Attempts", "Schedule", "Show Answers", "Webcam", "Status"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        table = new JTable(tableModel);
        UITheme.styleTable(table);
        table.setRowHeight(38);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setToolTipText("Double-click any exam to edit its configuration");

        int[] widths = {36, 160, 75, 36, 80, 68, 54, 44, 54, 76, 140, 96, 68, 72};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
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
                c.setFont(UITheme.font(column == 1 ? Font.BOLD : Font.PLAIN, 12.5f));
                return c;
            }
        });

        // Webcam requirement column renderer (Column 12)
        table.getColumnModel().getColumn(12).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String txt = value == null ? "Off" : value.toString();
                boolean isReq = "Required".equalsIgnoreCase(txt);
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 7));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                cell.add(UITheme.createBadge(
                        txt,
                        isReq ? UITheme.VIOLET_SOFT : UITheme.BG_SUBTLE,
                        isReq ? UITheme.VIOLET : UITheme.TEXT_MUTED
                ));
                return cell;
            }
        });

        // Status pill column renderer (Column 13)
        table.getColumnModel().getColumn(13).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String txt = value == null ? "" : value.toString();
                boolean isActive = "Active".equalsIgnoreCase(txt);
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 7));
                cell.setOpaque(true);
                cell.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                cell.add(UITheme.createBadge(
                        txt,
                        isActive ? UITheme.SUCCESS_SOFT : UITheme.DANGER_SOFT,
                        isActive ? UITheme.SUCCESS_TEXT : UITheme.DANGER_TEXT
                ));
                return cell;
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonStates();
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ExamConfig sel = getSelectedExam();
                    if (sel != null) {
                        openExamEditor(sel);
                    }
                }
            }
        });

        tableCard.add(UITheme.styleScrollPane(table), BorderLayout.CENTER);

        workspace.add(toolbar, BorderLayout.NORTH);
        workspace.add(tableCard, BorderLayout.CENTER);

        root.add(topBar, BorderLayout.NORTH);
        root.add(workspace, BorderLayout.CENTER);
        add(root);
        setVisible(true);

        loadExamsAsync();
    }

    private ExamConfig getSelectedExam() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= loadedExams.size()) {
            return null;
        }
        return loadedExams.get(row);
    }

    private void updateActionButtonStates() {
        ExamConfig sel = getSelectedExam();
        boolean hasSel = sel != null;
        editBtn.setEnabled(hasSel);
        toggleActiveBtn.setEnabled(hasSel);
        if (hasSel) {
            toggleActiveBtn.setText(sel.active ? "Deactivate" : "Activate");
        } else {
            toggleActiveBtn.setText("Deactivate");
        }
    }

    private void loadExamsAsync() {
        summaryBadge.setText("Loading...");
        new SwingWorker<Void, Void>() {
            private List<ExamConfig> exams;
            private Map<Integer, String> subjects;
            private Map<Integer, Integer> uniqueCounts = new LinkedHashMap<>();
            private Map<Integer, Map<String, Integer>> uniqueByDiff = new LinkedHashMap<>();

            @Override
            protected Void doInBackground() {
                exams = ExamDAO.getAllExams();
                subjects = AttemptDAO.getAvailableSubjects();
                for (Integer sid : subjects.keySet()) {
                    Map<String, Integer> byDiff = ExamDAO.countUniqueQuestionsByDifficulty(sid);
                    uniqueByDiff.put(sid, byDiff);
                    int total = byDiff.getOrDefault(ExamStore.DIFFICULTY_EASY, 0)
                            + byDiff.getOrDefault(ExamStore.DIFFICULTY_MEDIUM, 0)
                            + byDiff.getOrDefault(ExamStore.DIFFICULTY_HARD, 0);
                    uniqueCounts.put(sid, total);
                }
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                loadedExams.clear();
                if (exams != null) loadedExams.addAll(exams);

                subjectsMap.clear();
                if (subjects != null) subjectsMap.putAll(subjects);

                uniqueCountsBySubject.clear();
                uniqueCountsBySubject.putAll(uniqueCounts);
                uniqueByDiffBySubject.clear();
                uniqueByDiffBySubject.putAll(uniqueByDiff);

                tableModel.setRowCount(0);
                int activeCount = 0;
                for (ExamConfig cfg : loadedExams) {
                    if (cfg.active) activeCount++;
                    String durStr = (cfg.durationMinutes == null)
                            ? (cfg.questionCount + "m (Auto)")
                            : (cfg.durationMinutes + " min");
                    String attStr = (cfg.maxAttempts == null) ? "Unlimited" : String.valueOf(cfg.maxAttempts);
                    String mixStr = cfg.hasDifficultyMix()
                            ? (cfg.easyPct + "/" + cfg.mediumPct + "/" + cfg.hardPct + "%")
                            : "Random";
                    String schedStr;
                    if (cfg.startAt == null && cfg.endAt == null) {
                        schedStr = "Always Open";
                    } else if (cfg.startAt != null && cfg.endAt != null) {
                        schedStr = cfg.startAt.format(ExamConfig.SPINNER_DT) + " \u2192 " + cfg.endAt.format(ExamConfig.TIME_HM);
                    } else if (cfg.startAt != null) {
                        schedStr = "From " + cfg.startAt.format(ExamConfig.SPINNER_DT);
                    } else {
                        schedStr = "Until " + cfg.endAt.format(ExamConfig.SPINNER_DT);
                    }
                    tableModel.addRow(new Object[]{
                            cfg.id,
                            cfg.title,
                            cfg.subjectName,
                            cfg.questionCount,
                            mixStr,
                            durStr,
                            ExamConfig.formatMarks(cfg.marksPerQuestion),
                            cfg.negativeMarking > 0 ? ("-" + ExamConfig.formatMarks(cfg.negativeMarking)) : "0",
                            ExamConfig.formatMarks(cfg.passPercentage) + "%",
                            attStr,
                            schedStr,
                            formatShowPolicy(cfg.showAnswers),
                            cfg.requireWebcam ? "Required" : "Off",
                            cfg.active ? "Active" : "Inactive"
                    });
                }
                summaryBadge.setText(activeCount + " Active / " + loadedExams.size() + " Total");
                updateActionButtonStates();
            }
        }.execute();
    }

    private static String formatShowPolicy(String policy) {
        if (ExamConfig.SHOW_NEVER.equalsIgnoreCase(policy)) return "Never";
        if (ExamConfig.SHOW_AFTER_EXAM_END.equalsIgnoreCase(policy)) return "After Exam End";
        return "Immediately";
    }

    private void toggleSelectedExamActive() {
        ExamConfig sel = getSelectedExam();
        if (sel == null) return;
        boolean newState = !sel.active;
        String verb = newState ? "Activate" : "Deactivate";

        boolean confirmed = GlassDialog.showConfirm(
                this,
                verb + " Exam — " + sel.title,
                newState
                        ? "Make \"" + sel.title + "\" visible to students again?"
                        : "Deactivate \"" + sel.title + "\"? Existing student attempts will remain intact, but students will no longer be able to start new attempts.",
                verb,
                "Cancel",
                !newState
        );
        if (!confirmed) return;

        toggleActiveBtn.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return ExamDAO.setActive(sel.id, newState);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                boolean ok = false;
                try { ok = Boolean.TRUE.equals(get()); } catch (Exception ignored) {}
                if (ok) {
                    UITheme.showToast(ExamManagementUI.this,
                            newState ? "Exam Activated" : "Exam Deactivated",
                            "\"" + sel.title + "\" updated.",
                            UITheme.ToastType.SUCCESS);
                    loadExamsAsync();
                } else {
                    UITheme.showToast(ExamManagementUI.this, "Update Failed",
                            "Could not update exam status in MySQL.", UITheme.ToastType.ERROR);
                    updateActionButtonStates();
                }
            }
        }.execute();
    }

    private void openExamEditor(ExamConfig existing) {
        if (subjectsMap.isEmpty()) {
            UITheme.showToast(this, "No Subjects", "No subjects found in database yet.", UITheme.ToastType.WARNING);
            return;
        }
        boolean isEdit = existing != null;

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        // Title
        UITheme.RoundedTextField titleField = UITheme.createTextField("e.g. Java Quiz 1");
        if (isEdit && existing.title != null) {
            titleField.setText(existing.title);
        }

        // Subject combo
        List<SubjectItem> subjectItems = new ArrayList<>();
        SubjectItem initialSubject = null;
        for (Map.Entry<Integer, String> entry : subjectsMap.entrySet()) {
            SubjectItem item = new SubjectItem(entry.getKey(), entry.getValue());
            subjectItems.add(item);
            if (isEdit && entry.getKey() == existing.subjectId) {
                initialSubject = item;
            }
        }
        JComboBox<SubjectItem> subjectCombo = UITheme.createComboBox(subjectItems.toArray(new SubjectItem[0]));
        if (initialSubject != null) {
            subjectCombo.setSelectedItem(initialSubject);
        }

        JLabel availBadge = UITheme.createBadge("", UITheme.SKY_SOFT, UITheme.SKY);

        // Description
        JTextArea descArea = new JTextArea(isEdit && existing.description != null ? existing.description : "", 2, 24);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(UITheme.FONT_BODY);
        descArea.setForeground(UITheme.TEXT_PRIMARY);
        descArea.setBackground(UITheme.BG_CARD);
        descArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
        descScroll.setPreferredSize(new Dimension(260, 56));

        // Numeric JSpinners
        int initQs = isEdit ? Math.max(1, existing.questionCount) : 20;
        JSpinner qCountSpinner = createStyledSpinner(new SpinnerNumberModel(initQs, 1, 500, 1));

        int initDur = (isEdit && existing.durationMinutes != null) ? existing.durationMinutes : 0;
        JSpinner durationSpinner = createStyledSpinner(new SpinnerNumberModel(initDur, 0, 600, 1));

        double initMpq = isEdit ? existing.marksPerQuestion : 1.0;
        JSpinner mpqSpinner = createStyledSpinner(new SpinnerNumberModel(initMpq, 0.25, 100.0, 0.25));

        double initNeg = isEdit ? existing.negativeMarking : 0.0;
        JSpinner negSpinner = createStyledSpinner(new SpinnerNumberModel(initNeg, 0.0, 100.0, 0.25));

        double initPass = isEdit ? existing.passPercentage : 40.0;
        JSpinner passSpinner = createStyledSpinner(new SpinnerNumberModel(initPass, 0.0, 100.0, 1.0));

        int initMaxAtt = (isEdit && existing.maxAttempts != null) ? existing.maxAttempts : 0;
        JSpinner maxAttSpinner = createStyledSpinner(new SpinnerNumberModel(initMaxAtt, 0, 100, 1));

        // Show Answers Combo
        String[] policies = {ExamConfig.SHOW_IMMEDIATELY, ExamConfig.SHOW_AFTER_EXAM_END, ExamConfig.SHOW_NEVER};
        JComboBox<String> showAnswersCombo = UITheme.createComboBox(policies);
        if (isEdit && existing.showAnswers != null) {
            showAnswersCombo.setSelectedItem(existing.showAnswers.toUpperCase());
        }

        // Schedule window pickers (SpinnerDateModel, format dd-MM-yyyy HH:mm) + "No schedule" checkbox
        boolean initNoSchedule = !isEdit || (existing.startAt == null && existing.endAt == null);
        JCheckBox noScheduleCheck = new JCheckBox("No schedule (always open)", initNoSchedule);
        noScheduleCheck.setFont(UITheme.font(Font.BOLD, 12f));
        noScheduleCheck.setForeground(UITheme.TEXT_PRIMARY);
        noScheduleCheck.setOpaque(false);
        noScheduleCheck.setFocusPainted(false);
        noScheduleCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));

        LocalDateTime fallbackStart = LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0);
        LocalDateTime initStartLdt = (isEdit && existing.startAt != null) ? existing.startAt : fallbackStart;
        LocalDateTime initEndLdt = (isEdit && existing.endAt != null) ? existing.endAt : initStartLdt.plusHours(1);

        JSpinner startSpinner = createDateSpinner(toDate(initStartLdt));
        JSpinner endSpinner = createDateSpinner(toDate(initEndLdt));
        startSpinner.setEnabled(!initNoSchedule);
        endSpinner.setEnabled(!initNoSchedule);

        noScheduleCheck.addActionListener(e -> {
            boolean unscheduled = noScheduleCheck.isSelected();
            startSpinner.setEnabled(!unscheduled);
            endSpinner.setEnabled(!unscheduled);
        });

        JLabel attemptWarnBadge = UITheme.createBadge("", UITheme.WARNING_SOFT, UITheme.WARNING_TEXT);
        attemptWarnBadge.setVisible(false);

        final int[] existingAttemptCount = {0};
        // Preload DB server time (for default schedule pickers) & existing attempt count off the EDT
        new SwingWorker<Void, Void>() {
            private LocalDateTime serverNow;
            private int totalAtt = 0;

            @Override
            protected Void doInBackground() {
                if (initNoSchedule) {
                    serverNow = DBConnection.getServerTime();
                }
                if (isEdit) {
                    totalAtt = ExamDAO.countTotalAttemptsForExam(existing.id);
                }
                return null;
            }

            @Override
            protected void done() {
                existingAttemptCount[0] = totalAtt;
                if (isEdit && totalAtt > 0) {
                    attemptWarnBadge.setText(totalAtt + " attempt(s) recorded");
                    attemptWarnBadge.setVisible(true);
                }
                if (initNoSchedule && serverNow != null && noScheduleCheck.isSelected()) {
                    LocalDateTime defStart = serverNow.plusMinutes(5).withSecond(0).withNano(0);
                    startSpinner.setValue(toDate(defStart));
                    endSpinner.setValue(toDate(defStart.plusHours(1)));
                }
            }
        }.execute();

        // Difficulty Mix Controls (three linked spinners totaling 100% + live per-bucket availability)
        boolean initUseMix = isEdit && existing.hasDifficultyMix();
        int initEasyPct = initUseMix ? existing.easyPct : 30;
        int initMedPct = initUseMix ? existing.mediumPct : 50;
        int initHardPct = initUseMix ? existing.hardPct : 20;

        JCheckBox useMixCheck = new JCheckBox("Use difficulty mix (Easy + Medium + Hard = 100%)", initUseMix);
        useMixCheck.setFont(UITheme.font(Font.BOLD, 12f));
        useMixCheck.setForeground(UITheme.TEXT_PRIMARY);
        useMixCheck.setOpaque(false);
        useMixCheck.setFocusPainted(false);
        useMixCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JSpinner easyPctSpinner = createStyledSpinner(new SpinnerNumberModel(initEasyPct, 0, 100, 5));
        JSpinner medPctSpinner = createStyledSpinner(new SpinnerNumberModel(initMedPct, 0, 100, 5));
        JSpinner hardPctSpinner = createStyledSpinner(new SpinnerNumberModel(initHardPct, 0, 100, 5));
        easyPctSpinner.setPreferredSize(new Dimension(74, 30));
        medPctSpinner.setPreferredSize(new Dimension(74, 30));
        hardPctSpinner.setPreferredSize(new Dimension(74, 30));
        easyPctSpinner.setEnabled(initUseMix);
        medPctSpinner.setEnabled(initUseMix);
        hardPctSpinner.setEnabled(initUseMix);

        JLabel easyAvailLbl = new JLabel("Easy: 0 available");
        JLabel medAvailLbl = new JLabel("Medium: 0 available");
        JLabel hardAvailLbl = new JLabel("Hard: 0 available");
        easyAvailLbl.setFont(UITheme.font(Font.BOLD, 11.5f));
        medAvailLbl.setFont(UITheme.font(Font.BOLD, 11.5f));
        hardAvailLbl.setFont(UITheme.font(Font.BOLD, 11.5f));

        JLabel mixWarnLbl = new JLabel(" ");
        mixWarnLbl.setFont(UITheme.font(Font.BOLD, 11.5f));
        mixWarnLbl.setForeground(UITheme.WARNING_TEXT);

        final int[] currentAvailUnique = {0};
        final int[] currentAvailByDiff = {0, 0, 0}; // [EASY, MEDIUM, HARD]

        Runnable updateMixAvailabilityUI = () -> {
            int qCnt = ((Number) qCountSpinner.getValue()).intValue();
            boolean useMix = useMixCheck.isSelected();
            int ePct = ((Number) easyPctSpinner.getValue()).intValue();
            int mPct = ((Number) medPctSpinner.getValue()).intValue();
            int hPct = ((Number) hardPctSpinner.getValue()).intValue();
            int[] needs = useMix ? ExamConfig.computeDifficultyTargets(qCnt, ePct, mPct, hPct) : new int[]{0, 0, 0};

            if (useMix) {
                easyAvailLbl.setText("Easy: " + currentAvailByDiff[0] + " available, needs " + needs[0]);
                medAvailLbl.setText("Medium: " + currentAvailByDiff[1] + " available, needs " + needs[1]);
                hardAvailLbl.setText("Hard: " + currentAvailByDiff[2] + " available, needs " + needs[2]);

                easyAvailLbl.setForeground(currentAvailByDiff[0] < needs[0] ? UITheme.DANGER_TEXT : UITheme.SUCCESS_TEXT);
                medAvailLbl.setForeground(currentAvailByDiff[1] < needs[1] ? UITheme.DANGER_TEXT : UITheme.WARNING_TEXT);
                hardAvailLbl.setForeground(currentAvailByDiff[2] < needs[2] ? UITheme.DANGER_TEXT : UITheme.TEXT_SECONDARY);

                List<String> shortBuckets = new ArrayList<>();
                if (currentAvailByDiff[0] < needs[0]) shortBuckets.add("Easy (" + currentAvailByDiff[0] + "/" + needs[0] + ")");
                if (currentAvailByDiff[1] < needs[1]) shortBuckets.add("Medium (" + currentAvailByDiff[1] + "/" + needs[1] + ")");
                if (currentAvailByDiff[2] < needs[2]) shortBuckets.add("Hard (" + currentAvailByDiff[2] + "/" + needs[2] + ")");

                if (!shortBuckets.isEmpty()) {
                    mixWarnLbl.setText("Warning: short on " + String.join(", ", shortBuckets)
                            + " \u2014 shortfall will backfill from Medium/other difficulties.");
                } else {
                    mixWarnLbl.setText("Mix ready (" + ePct + "/" + mPct + "/" + hPct + "% = "
                            + needs[0] + "/" + needs[1] + "/" + needs[2] + " questions).");
                    mixWarnLbl.setForeground(UITheme.SUCCESS_TEXT);
                }
                if (!shortBuckets.isEmpty()) {
                    mixWarnLbl.setForeground(UITheme.WARNING_TEXT);
                }
            } else {
                easyAvailLbl.setText("Easy: " + currentAvailByDiff[0] + " available");
                medAvailLbl.setText("Medium: " + currentAvailByDiff[1] + " available");
                hardAvailLbl.setText("Hard: " + currentAvailByDiff[2] + " available");
                easyAvailLbl.setForeground(UITheme.TEXT_MUTED);
                medAvailLbl.setForeground(UITheme.TEXT_MUTED);
                hardAvailLbl.setForeground(UITheme.TEXT_MUTED);
                mixWarnLbl.setText("Random draw across all difficulties (0/0/0 legacy mode).");
                mixWarnLbl.setForeground(UITheme.TEXT_MUTED);
            }
        };

        // Linked 3-spinner rebalance so Easy + Medium + Hard always equals 100
        final boolean[] rebalancing = {false};
        final JSpinner[] mixSpinners = {easyPctSpinner, medPctSpinner, hardPctSpinner};
        for (int idx = 0; idx < 3; idx++) {
            final int changedIdx = idx;
            mixSpinners[idx].addChangeListener(e -> {
                if (rebalancing[0]) return;
                rebalancing[0] = true;
                try {
                    int val = Math.max(0, Math.min(100, ((Number) mixSpinners[changedIdx].getValue()).intValue()));
                    int remaining = 100 - val;
                    int otherA = (changedIdx == 0) ? 1 : (changedIdx == 1 ? 0 : 1);
                    int otherB = 3 - changedIdx - otherA;
                    int curA = Math.max(0, ((Number) mixSpinners[otherA].getValue()).intValue());
                    int curB = Math.max(0, ((Number) mixSpinners[otherB].getValue()).intValue());
                    int sumOther = curA + curB;
                    int newA;
                    int newB;
                    if (sumOther <= 0) {
                        newA = remaining;
                        newB = 0;
                    } else {
                        newA = (int) Math.round(remaining * (curA / (double) sumOther));
                        newA = Math.max(0, Math.min(remaining, newA));
                        newB = remaining - newA;
                    }
                    mixSpinners[changedIdx].setValue(val);
                    mixSpinners[otherA].setValue(newA);
                    mixSpinners[otherB].setValue(newB);
                } finally {
                    rebalancing[0] = false;
                }
                updateMixAvailabilityUI.run();
            });
        }

        useMixCheck.addActionListener(e -> {
            boolean enabled = useMixCheck.isSelected();
            easyPctSpinner.setEnabled(enabled);
            medPctSpinner.setEnabled(enabled);
            hardPctSpinner.setEnabled(enabled);
            if (enabled) {
                int sum = ((Number) easyPctSpinner.getValue()).intValue()
                        + ((Number) medPctSpinner.getValue()).intValue()
                        + ((Number) hardPctSpinner.getValue()).intValue();
                if (sum != 100) {
                    rebalancing[0] = true;
                    easyPctSpinner.setValue(30);
                    medPctSpinner.setValue(50);
                    hardPctSpinner.setValue(20);
                    rebalancing[0] = false;
                }
            }
            updateMixAvailabilityUI.run();
        });
        qCountSpinner.addChangeListener(e -> updateMixAvailabilityUI.run());

        // Track live available unique question count (total + per difficulty) for selected subject
        Runnable refreshAvailLabel = () -> {
            SubjectItem item = (SubjectItem) subjectCombo.getSelectedItem();
            if (item == null) return;
            int cached = uniqueCountsBySubject.getOrDefault(item.id, 0);
            currentAvailUnique[0] = cached;
            Map<String, Integer> cachedDiff = uniqueByDiffBySubject.get(item.id);
            if (cachedDiff != null) {
                currentAvailByDiff[0] = cachedDiff.getOrDefault(ExamStore.DIFFICULTY_EASY, 0);
                currentAvailByDiff[1] = cachedDiff.getOrDefault(ExamStore.DIFFICULTY_MEDIUM, 0);
                currentAvailByDiff[2] = cachedDiff.getOrDefault(ExamStore.DIFFICULTY_HARD, 0);
            }
            availBadge.setText(cached + " unique Qs available");
            updateMixAvailabilityUI.run();

            new SwingWorker<Map<String, Integer>, Void>() {
                @Override
                protected Map<String, Integer> doInBackground() {
                    return ExamDAO.countUniqueQuestionsByDifficulty(item.id);
                }

                @Override
                protected void done() {
                    try {
                        Map<String, Integer> freshMap = get();
                        if (freshMap == null) return;
                        int freshTotal = freshMap.getOrDefault(ExamStore.DIFFICULTY_EASY, 0)
                                + freshMap.getOrDefault(ExamStore.DIFFICULTY_MEDIUM, 0)
                                + freshMap.getOrDefault(ExamStore.DIFFICULTY_HARD, 0);
                        uniqueCountsBySubject.put(item.id, freshTotal);
                        uniqueByDiffBySubject.put(item.id, freshMap);
                        SubjectItem selNow = (SubjectItem) subjectCombo.getSelectedItem();
                        if (selNow != null && selNow.id == item.id) {
                            currentAvailUnique[0] = freshTotal;
                            currentAvailByDiff[0] = freshMap.getOrDefault(ExamStore.DIFFICULTY_EASY, 0);
                            currentAvailByDiff[1] = freshMap.getOrDefault(ExamStore.DIFFICULTY_MEDIUM, 0);
                            currentAvailByDiff[2] = freshMap.getOrDefault(ExamStore.DIFFICULTY_HARD, 0);
                            availBadge.setText(freshTotal + " unique Qs available");
                            updateMixAvailabilityUI.run();
                        }
                    } catch (Exception ignored) {}
                }
            }.execute();
        };
        subjectCombo.addActionListener(e -> refreshAvailLabel.run());
        refreshAvailLabel.run();

        // Layout rows
        int row = 0;
        addFieldRow(form, g, row++, "Exam Title *", titleField, "Subject *", subjectCombo);

        g.gridy = row++;
        g.gridx = 0; g.gridwidth = 1; g.weightx = 0.0;
        form.add(fieldLabel("Description"), g);
        g.gridx = 1; g.gridwidth = 2; g.weightx = 1.0;
        form.add(descScroll, g);
        g.gridx = 3; g.gridwidth = 1; g.weightx = 0.0;
        form.add(availBadge, g);
        g.gridwidth = 1;

        addFieldRow(form, g, row++, "Question Count *", qCountSpinner,
                "Duration (min, 0=Auto)", durationSpinner);
        addFieldRow(form, g, row++, "Marks / Question *", mpqSpinner,
                "Negative Marking (wrong)", negSpinner);
        addFieldRow(form, g, row++, "Pass Percentage (%) *", passSpinner,
                "Max Attempts (0=Unlim.)", maxAttSpinner);

        g.gridy = row++;
        g.gridx = 0; g.weightx = 0.0;
        form.add(fieldLabel("Show Answers Policy"), g);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1.0;
        form.add(showAnswersCombo, g);
        g.gridwidth = 1;

        g.gridy = row++;
        g.gridx = 0; g.weightx = 0.0;
        form.add(fieldLabel("Schedule Window"), g);
        JPanel schedTopRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        schedTopRow.setOpaque(false);
        schedTopRow.add(noScheduleCheck);
        schedTopRow.add(attemptWarnBadge);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1.0;
        form.add(schedTopRow, g);
        g.gridwidth = 1;

        addFieldRow(form, g, row++, "Start (dd-MM-yyyy HH:mm)", startSpinner,
                "End (dd-MM-yyyy HH:mm)", endSpinner);

        // Difficulty Mix Card Row
        UITheme.RoundedPanel mixBox = new UITheme.RoundedPanel(10, UITheme.BG_SUBTLE, UITheme.BORDER);
        mixBox.setLayout(new GridBagLayout());
        mixBox.setBorder(new EmptyBorder(8, 12, 8, 12));
        GridBagConstraints mg = new GridBagConstraints();
        mg.insets = new Insets(3, 5, 3, 8);
        mg.anchor = GridBagConstraints.WEST;
        mg.fill = GridBagConstraints.HORIZONTAL;

        mg.gridy = 0; mg.gridx = 0; mg.gridwidth = 4; mg.weightx = 1.0;
        mixBox.add(useMixCheck, mg);
        mg.gridwidth = 1;

        mg.gridy = 1;
        mg.gridx = 0; mg.weightx = 0.0; mixBox.add(UITheme.createDifficultyBadge(ExamStore.DIFFICULTY_EASY), mg);
        mg.gridx = 1; mg.weightx = 0.0; mixBox.add(easyPctSpinner, mg);
        mg.gridx = 2; mg.weightx = 0.0; mixBox.add(new JLabel("%"), mg);
        mg.gridx = 3; mg.weightx = 1.0; mixBox.add(easyAvailLbl, mg);

        mg.gridy = 2;
        mg.gridx = 0; mg.weightx = 0.0; mixBox.add(UITheme.createDifficultyBadge(ExamStore.DIFFICULTY_MEDIUM), mg);
        mg.gridx = 1; mg.weightx = 0.0; mixBox.add(medPctSpinner, mg);
        mg.gridx = 2; mg.weightx = 0.0; mixBox.add(new JLabel("%"), mg);
        mg.gridx = 3; mg.weightx = 1.0; mixBox.add(medAvailLbl, mg);

        mg.gridy = 3;
        mg.gridx = 0; mg.weightx = 0.0; mixBox.add(UITheme.createDifficultyBadge(ExamStore.DIFFICULTY_HARD), mg);
        mg.gridx = 1; mg.weightx = 0.0; mixBox.add(hardPctSpinner, mg);
        mg.gridx = 2; mg.weightx = 0.0; mixBox.add(new JLabel("%"), mg);
        mg.gridx = 3; mg.weightx = 1.0; mixBox.add(hardAvailLbl, mg);

        mg.gridy = 4; mg.gridx = 0; mg.gridwidth = 4; mg.weightx = 1.0;
        mixBox.add(mixWarnLbl, mg);

        g.gridy = row++;
        g.gridx = 0; g.gridwidth = 4; g.weightx = 1.0;
        form.add(mixBox, g);

        // Task 12: Webcam proctoring requirement checkbox
        JCheckBox requireWebcamCheck = new JCheckBox("Require Webcam Proctoring (periodic snapshots with consent)",
                isEdit && existing.requireWebcam);
        requireWebcamCheck.setFont(UITheme.font(Font.BOLD, 12f));
        requireWebcamCheck.setForeground(UITheme.TEXT_PRIMARY);
        requireWebcamCheck.setOpaque(false);
        requireWebcamCheck.setFocusPainted(false);
        requireWebcamCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));

        g.gridy = row++;
        g.gridx = 0; g.gridwidth = 4; g.weightx = 1.0;
        form.add(requireWebcamCheck, g);

        JLabel hintLbl = new JLabel("Schedule is enforced using MySQL server time. Unanswered questions never lose negative marks.");
        hintLbl.setFont(UITheme.font(Font.PLAIN, 11f));
        hintLbl.setForeground(UITheme.TEXT_MUTED);
        g.gridy = row++;
        g.gridx = 0; g.gridwidth = 4; g.weightx = 1.0;
        form.add(hintLbl, g);

        JLabel errorLbl = new JLabel(" ");
        errorLbl.setFont(UITheme.font(Font.BOLD, 12f));
        errorLbl.setForeground(UITheme.DANGER_TEXT);
        g.gridy = row;
        g.gridx = 0; g.gridwidth = 4;
        form.add(errorLbl, g);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);

        UITheme.ModernButton cancelBtn = UITheme.secondaryButton("Cancel");
        UITheme.ModernButton saveBtn = UITheme.gradientPrimaryButton(isEdit ? "Save Changes" : "Create Exam");
        saveBtn.setPreferredSize(new Dimension(150, 38));

        footer.add(cancelBtn);
        footer.add(saveBtn);

        JScrollPane formScroll = new JScrollPane(form,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(formScroll);

        GlassDialog dialog = new GlassDialog(
                this,
                isEdit ? ("Edit Exam #" + existing.id) : "Create New Exam",
                "Configure per-exam rules, difficulty mix & server-time schedule",
                formScroll,
                footer,
                780,
                630
        );

        cancelBtn.addActionListener(e -> dialog.closeAnimated(null));

        saveBtn.addActionListener(e -> {
            String examTitle = titleField.getText().trim();
            if (examTitle.isEmpty()) {
                errorLbl.setText("Exam title is required.");
                titleField.requestFocusInWindow();
                return;
            }
            if (examTitle.length() > 150) {
                errorLbl.setText("Exam title must be 150 characters or fewer.");
                return;
            }
            String desc = descArea.getText().trim();
            if (desc.length() > 500) {
                errorLbl.setText("Description must be 500 characters or fewer.");
                return;
            }

            SubjectItem selSubj = (SubjectItem) subjectCombo.getSelectedItem();
            if (selSubj == null) {
                errorLbl.setText("Please select a subject.");
                return;
            }

            int qCount = ((Number) qCountSpinner.getValue()).intValue();
            int availQs = currentAvailUnique[0];
            if (availQs <= 0) {
                errorLbl.setText("No questions exist in " + selSubj.name + " yet. Add questions first.");
                return;
            }
            if (qCount > availQs) {
                errorLbl.setText("Question count (" + qCount + ") exceeds available unique questions in "
                        + selSubj.name + " (" + availQs + ").");
                return;
            }

            int durVal = ((Number) durationSpinner.getValue()).intValue();
            double mpq = ((Number) mpqSpinner.getValue()).doubleValue();
            double neg = ((Number) negSpinner.getValue()).doubleValue();
            double passPct = ((Number) passSpinner.getValue()).doubleValue();
            int maxAttVal = ((Number) maxAttSpinner.getValue()).intValue();

            if (mpq <= 0) {
                errorLbl.setText("Marks per question must be greater than 0.");
                return;
            }
            if (neg < 0 || neg > mpq) {
                errorLbl.setText("Negative marking must be between 0 and Marks/Question (" + ExamConfig.formatMarks(mpq) + ").");
                return;
            }
            if (passPct < 0 || passPct > 100) {
                errorLbl.setText("Pass percentage must be between 0 and 100.");
                return;
            }

            LocalDateTime parsedStart = null;
            LocalDateTime parsedEnd = null;
            if (!noScheduleCheck.isSelected()) {
                Date startVal = (Date) startSpinner.getValue();
                Date endVal = (Date) endSpinner.getValue();
                parsedStart = toLocalDateTime(startVal).withSecond(0).withNano(0);
                parsedEnd = toLocalDateTime(endVal).withSecond(0).withNano(0);
                if (!parsedEnd.isAfter(parsedStart)) {
                    errorLbl.setText("End time must be strictly later than Start time.");
                    return;
                }
            }

            if (isEdit) {
                boolean scheduleChanged = !Objects.equals(
                        existing.startAt != null ? existing.startAt.withSecond(0).withNano(0) : null,
                        parsedStart)
                        || !Objects.equals(
                        existing.endAt != null ? existing.endAt.withSecond(0).withNano(0) : null,
                        parsedEnd);
                if (scheduleChanged && existingAttemptCount[0] > 0) {
                    boolean proceed = GlassDialog.showConfirm(
                            dialog,
                            "Schedule Change Warning",
                            "This exam already has " + existingAttemptCount[0]
                                    + " recorded student attempt(s). Changing the schedule window may affect student access and answer visibility.\n\nDo you want to save the updated schedule?",
                            "Save Schedule",
                            "Cancel",
                            false
                    );
                    if (!proceed) {
                        return;
                    }
                }
            }

            errorLbl.setText(" ");
            ExamConfig cfg = new ExamConfig();
            if (isEdit) {
                cfg.id = existing.id;
                cfg.active = existing.active;
            } else {
                cfg.active = true;
            }
            cfg.subjectId = selSubj.id;
            cfg.subjectName = selSubj.name;
            cfg.title = examTitle;
            cfg.description = desc.isEmpty() ? null : desc;
            cfg.questionCount = qCount;
            cfg.durationMinutes = durVal > 0 ? durVal : null;
            cfg.marksPerQuestion = mpq;
            cfg.negativeMarking = neg;
            cfg.passPercentage = passPct;
            cfg.maxAttempts = maxAttVal > 0 ? maxAttVal : null;
            cfg.showAnswers = (String) showAnswersCombo.getSelectedItem();
            cfg.startAt = parsedStart;
            cfg.endAt = parsedEnd;
            if (useMixCheck.isSelected()) {
                cfg.easyPct = ((Number) easyPctSpinner.getValue()).intValue();
                cfg.mediumPct = ((Number) medPctSpinner.getValue()).intValue();
                cfg.hardPct = ((Number) hardPctSpinner.getValue()).intValue();
            } else {
                cfg.easyPct = 0;
                cfg.mediumPct = 0;
                cfg.hardPct = 0;
            }
            cfg.requireWebcam = requireWebcamCheck.isSelected();

            saveBtn.setEnabled(false);
            saveBtn.setText("Saving...");

            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    int liveUnique = ExamDAO.countUniqueQuestionsForSubject(cfg.subjectId);
                    if (cfg.questionCount > liveUnique) {
                        return null;
                    }
                    if (isEdit) {
                        return ExamDAO.update(cfg);
                    }
                    return ExamDAO.create(cfg) > 0;
                }

                @Override
                protected void done() {
                    Boolean ok = false;
                    try { ok = get(); } catch (Exception ignored) {}
                    if (ok == null) {
                        saveBtn.setEnabled(true);
                        saveBtn.setText(isEdit ? "Save Changes" : "Create Exam");
                        errorLbl.setText("Question count exceeds live unique question count in MySQL.");
                        return;
                    }
                    if (Boolean.TRUE.equals(ok)) {
                        dialog.closeAnimated(() -> {
                            UITheme.showToast(ExamManagementUI.this,
                                    isEdit ? "Exam Updated" : "Exam Created",
                                    "\"" + cfg.title + "\" saved to MySQL.",
                                    UITheme.ToastType.SUCCESS);
                            loadExamsAsync();
                        });
                    } else {
                        saveBtn.setEnabled(true);
                        saveBtn.setText(isEdit ? "Save Changes" : "Create Exam");
                        errorLbl.setText("Failed to save exam to MySQL. Please check connection.");
                    }
                }
            }.execute();
        });

        dialog.showAnimated();
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.BOLD, 12f));
        l.setForeground(UITheme.TEXT_SECONDARY);
        return l;
    }

    private void addFieldRow(
            JPanel form, GridBagConstraints g, int row,
            String lbl1, JComponent comp1, String lbl2, JComponent comp2) {
        g.gridy = row;
        g.gridwidth = 1;
        g.gridx = 0; g.weightx = 0.0;
        form.add(fieldLabel(lbl1), g);
        g.gridx = 1; g.weightx = 0.5;
        form.add(comp1, g);
        g.gridx = 2; g.weightx = 0.0;
        form.add(fieldLabel(lbl2), g);
        g.gridx = 3; g.weightx = 0.5;
        form.add(comp2, g);
    }

    private JSpinner createStyledSpinner(SpinnerNumberModel model) {
        JSpinner spinner = new JSpinner(model);
        spinner.setFont(UITheme.FONT_BODY);
        spinner.setPreferredSize(new Dimension(130, 34));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setFont(UITheme.FONT_BODY);
            tf.setForeground(UITheme.TEXT_PRIMARY);
            tf.setBackground(UITheme.BG_CARD);
        }
        return spinner;
    }

    private JSpinner createDateSpinner(Date initialDate) {
        SpinnerDateModel model = new SpinnerDateModel(initialDate, null, null, Calendar.MINUTE);
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "dd-MM-yyyy HH:mm");
        spinner.setEditor(editor);
        spinner.setFont(UITheme.FONT_BODY);
        spinner.setPreferredSize(new Dimension(168, 34));
        JFormattedTextField tf = editor.getTextField();
        tf.setFont(UITheme.FONT_BODY);
        tf.setForeground(UITheme.TEXT_PRIMARY);
        tf.setBackground(UITheme.BG_CARD);
        return spinner;
    }

    private static Date toDate(LocalDateTime ldt) {
        LocalDateTime safe = ldt != null ? ldt : LocalDateTime.now();
        return Date.from(safe.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return LocalDateTime.now();
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
