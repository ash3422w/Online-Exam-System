package com.onlineexam.ui.student;

import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.CertificateService;
import com.onlineexam.data.CertificatePdfBuilder;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.RadarChartPanel;
import com.onlineexam.ui.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentPerformanceUI extends JFrame {

    private final JComboBox<SubjectOption> subjectFilterBox;
    private final JLabel totalExamsValue;
    private final JLabel avgScoreValue;
    private final JLabel highestScoreValue;
    private final JLabel lowestScoreValue;
    private final JLabel gradeStatusValue;
    private final JLabel rankValue;
    private final UITheme.StudentTrendChartPanel trendChart;
    private final RadarChartPanel radarChart;
    private final JPanel tableContainer;
    private List<AttemptDAO.AttemptDetail> currentAttempts = new ArrayList<>();
    private JTable historyTable;

    public StudentPerformanceUI() {
        String currentUser = ExamStore.getCurrentDisplayName();
        String currentUid = ExamStore.getCurrentUserId();
        setTitle("My Performance History - " + (currentUser.isEmpty() ? "Student" : currentUser));
        setSize(1120, 770);
        setMinimumSize(new Dimension(920, 640));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.42f);

        // Clean white top header (no dark mode toggle)
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

        JLabel title = new JLabel("My Performance History");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel("Student: " + currentUser + " (" + currentUid + ")"
                + "  \u2022  Personal scores, progression chart, subject strengths radar, and attempt log");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));

        headLeft.add(title);
        headLeft.add(sub);

        UITheme.ModernButton backTop = UITheme.secondaryButton("\u2190 Dashboard");
        backTop.addActionListener(e -> {
            dispose();
            new StudentDashboard();
        });
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        headerRight.setOpaque(false);
        headerRight.add(backTop);

        header.add(headLeft, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);

        // Filter Strip
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(255, 255, 255, 185));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.GLASS_SLATE_BORDER);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        filterPanel.setOpaque(false);
        filterPanel.setBorder(new EmptyBorder(0, 16, 0, 16));

        JLabel filterLabel = UITheme.createFieldLabel("Filter by Subject:");

        subjectFilterBox = UITheme.createComboBox(null);
        subjectFilterBox.setPreferredSize(new Dimension(180, 36));
        subjectFilterBox.addItem(new SubjectOption(null, "All Subjects"));
        for (Map.Entry<Integer, String> entry : AttemptDAO.getAvailableSubjects().entrySet()) {
            subjectFilterBox.addItem(new SubjectOption(entry.getKey(), entry.getValue()));
        }

        UITheme.ModernButton refreshBtn = UITheme.gradientPrimaryButton("Refresh");
        refreshBtn.setPreferredSize(new Dimension(105, 36));

        UITheme.ModernButton reviewAttemptBtn = UITheme.secondaryButton("Review Selected Attempt");
        reviewAttemptBtn.setPreferredSize(new Dimension(196, 36));
        reviewAttemptBtn.addActionListener(e -> openSelectedAttemptReview());

        UITheme.ModernButton certBtn = UITheme.secondaryButton("Download Certificate");
        certBtn.setPreferredSize(new Dimension(190, 36));
        certBtn.addActionListener(e -> downloadCertificateForSelectedAttempt());

        filterPanel.add(filterLabel);
        filterPanel.add(subjectFilterBox);
        filterPanel.add(refreshBtn);
        filterPanel.add(reviewAttemptBtn);
        filterPanel.add(certBtn);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(header, BorderLayout.NORTH);
        topSection.add(filterPanel, BorderLayout.SOUTH);

        // 6 KPI Cards Row
        JPanel cardsRow = new JPanel(new GridLayout(1, 6, 12, 0));
        cardsRow.setOpaque(false);

        totalExamsValue = new JLabel("0", SwingConstants.CENTER);
        avgScoreValue = new JLabel("0.00 (0.00%)", SwingConstants.CENTER);
        highestScoreValue = new JLabel("-", SwingConstants.CENTER);
        lowestScoreValue = new JLabel("-", SwingConstants.CENTER);
        gradeStatusValue = new JLabel("-", SwingConstants.CENTER);
        rankValue = new JLabel("N/A", SwingConstants.CENTER);

        cardsRow.add(createMetricCard("TOTAL EXAMS", totalExamsValue, UITheme.PRIMARY));
        cardsRow.add(createMetricCard("AVERAGE", avgScoreValue, UITheme.VIOLET));
        cardsRow.add(createMetricCard("HIGHEST", highestScoreValue, UITheme.SUCCESS));
        cardsRow.add(createMetricCard("LOWEST", lowestScoreValue, UITheme.WARNING));
        cardsRow.add(createMetricCard("GRADE / STATUS", gradeStatusValue, UITheme.SKY));
        cardsRow.add(createMetricCard("RANK", rankValue, UITheme.PINK));

        // Visual Analytics Split: Trend Line Chart (Left) + Subject Strengths Radar Chart (Right)
        JPanel chartsRow = new JPanel(new GridBagLayout());
        chartsRow.setOpaque(false);
        GridBagConstraints cg = new GridBagConstraints();
        cg.fill = GridBagConstraints.BOTH;
        cg.gridy = 0;
        cg.weighty = 1.0;

        DashboardComponents.BentoCard trendCard = new DashboardComponents.BentoCard(16);
        trendCard.setShadowColor(UITheme.PRIMARY);
        trendCard.setLayout(new BorderLayout(0, 6));
        trendCard.setBorder(new EmptyBorder(14, 18, 12, 18));

        JPanel trendHeader = new JPanel(new BorderLayout());
        trendHeader.setOpaque(false);
        JLabel trendTitle = new JLabel("Score % Progression Trend");
        trendTitle.setFont(UITheme.font(Font.BOLD, 14f));
        trendTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel trendHint = UITheme.createBadge("Hover Points for Details", UITheme.PRIMARY_SOFT, UITheme.PRIMARY);
        trendHeader.add(trendTitle, BorderLayout.WEST);
        trendHeader.add(trendHint, BorderLayout.EAST);

        trendChart = new UITheme.StudentTrendChartPanel();
        trendChart.setPreferredSize(new Dimension(540, 195));
        trendCard.add(trendHeader, BorderLayout.NORTH);
        trendCard.add(trendChart, BorderLayout.CENTER);

        DashboardComponents.BentoCard radarCard = new DashboardComponents.BentoCard(16);
        radarCard.setShadowColor(UITheme.VIOLET);
        radarCard.setLayout(new BorderLayout(0, 6));
        radarCard.setBorder(new EmptyBorder(14, 18, 12, 18));

        JPanel radarHeader = new JPanel(new BorderLayout());
        radarHeader.setOpaque(false);
        JLabel radarTitle = new JLabel("Subject Strengths");
        radarTitle.setFont(UITheme.font(Font.BOLD, 14f));
        radarTitle.setForeground(UITheme.TEXT_PRIMARY);
        JLabel radarTag = UITheme.createBadge("Radar", UITheme.VIOLET_SOFT, UITheme.VIOLET);
        radarHeader.add(radarTitle, BorderLayout.WEST);
        radarHeader.add(radarTag, BorderLayout.EAST);

        radarChart = new RadarChartPanel();
        radarChart.setPreferredSize(new Dimension(280, 195));
        radarCard.add(radarHeader, BorderLayout.NORTH);
        radarCard.add(radarChart, BorderLayout.CENTER);

        cg.gridx = 0;
        cg.weightx = 0.64;
        cg.insets = new Insets(0, 0, 0, 14);
        chartsRow.add(trendCard, cg);

        cg.gridx = 1;
        cg.weightx = 0.36;
        cg.insets = new Insets(0, 0, 0, 0);
        chartsRow.add(radarCard, cg);

        JPanel upperStack = new JPanel(new BorderLayout(0, 14));
        upperStack.setOpaque(false);
        upperStack.add(cardsRow, BorderLayout.NORTH);
        upperStack.add(chartsRow, BorderLayout.CENTER);

        GlassPanel tableCard = new GlassPanel(16);
        tableCard.setSpotlightEnabled(false);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(14, 16, 16, 16));

        tableContainer = new JPanel(new BorderLayout());
        tableContainer.setOpaque(false);
        tableCard.add(tableContainer, BorderLayout.CENTER);

        JPanel centerSection = new JPanel(new BorderLayout(0, 14));
        centerSection.setOpaque(false);
        centerSection.setBorder(new EmptyBorder(14, 22, 18, 22));
        centerSection.add(upperStack, BorderLayout.NORTH);
        centerSection.add(tableCard, BorderLayout.CENTER);

        subjectFilterBox.addActionListener(e -> loadStudentPerformance(false));
        refreshBtn.addActionListener(e -> loadStudentPerformance(true));

        root.add(topSection, BorderLayout.NORTH);
        root.add(centerSection, BorderLayout.CENTER);

        add(root);
        loadStudentPerformance(false);
        setVisible(true);
    }

    private JPanel createMetricCard(String labelText, JLabel valueLabel, Color accent) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(14);
        card.setShadowColor(accent);
        card.setLeftAccentColor(accent);
        card.setLayout(new BorderLayout(0, 5));
        card.setBorder(new EmptyBorder(12, 12, 14, 10));

        JLabel title = new JLabel(labelText, SwingConstants.CENTER);
        title.setFont(UITheme.font(Font.BOLD, 10f));
        title.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(UITheme.font(Font.BOLD, 15f));
        valueLabel.setForeground(UITheme.TEXT_PRIMARY);

        card.add(title, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private static class PerformancePayload {
        final AttemptDAO.StudentPerformanceSummary summary;
        final List<AttemptDAO.AttemptDetail> attempts;
        final Map<String, Double> strengths;
        final String subjectLabel;
        final boolean showToast;

        PerformancePayload(
                AttemptDAO.StudentPerformanceSummary summary,
                List<AttemptDAO.AttemptDetail> attempts,
                Map<String, Double> strengths,
                String subjectLabel,
                boolean showToast) {
            this.summary = summary;
            this.attempts = attempts;
            this.strengths = strengths;
            this.subjectLabel = subjectLabel;
            this.showToast = showToast;
        }
    }

    private void loadStudentPerformance(boolean showToast) {
        String currentUser = ExamStore.getCurrentUserId();
        SubjectOption selected = (SubjectOption) subjectFilterBox.getSelectedItem();
        Integer subjectId = selected == null ? null : selected.subjectId;
        String subjectLabel = selected == null ? "All Subjects" : selected.name;

        new SwingWorker<PerformancePayload, Void>() {
            @Override
            protected PerformancePayload doInBackground() {
                AttemptDAO.StudentPerformanceSummary summary =
                        AttemptDAO.getStudentPerformanceSummary(currentUser, subjectId);
                List<AttemptDAO.AttemptDetail> attempts =
                        AttemptDAO.getAttemptDetails(subjectId, currentUser, false);

                Map<String, Double> strengths = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : AttemptDAO.getAvailableSubjects().entrySet()) {
                    List<AttemptDAO.AttemptDetail> subAttempts =
                            AttemptDAO.getAttemptDetails(entry.getKey(), currentUser, false);
                    if (!subAttempts.isEmpty()) {
                        double sum = 0.0;
                        for (AttemptDAO.AttemptDetail d : subAttempts) sum += d.percentage;
                        strengths.put(entry.getValue(), AttemptDAO.roundTwoDecimals(sum / subAttempts.size()));
                    } else {
                        strengths.put(entry.getValue(), 0.0);
                    }
                }
                return new PerformancePayload(summary, attempts, strengths, subjectLabel, showToast);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    PerformancePayload p = get();
                    if (p != null) {
                        applyPerformancePayload(p);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    private void applyPerformancePayload(PerformancePayload p) {
        AttemptDAO.StudentPerformanceSummary summary = p.summary;
        List<AttemptDAO.AttemptDetail> attempts = p.attempts;

        if (!summary.hasAttempts || attempts.isEmpty()) {
            totalExamsValue.setText("0");
            avgScoreValue.setText("0.00 (0.00%)");
            highestScoreValue.setText("-");
            lowestScoreValue.setText("-");
            gradeStatusValue.setText("No Attempts");
            gradeStatusValue.setForeground(UITheme.TEXT_MUTED);
            rankValue.setText("N/A");
            trendChart.setData(new ArrayList<>(), new ArrayList<>());
        } else {
            totalExamsValue.setText(String.valueOf(summary.totalAttempts));
            avgScoreValue.setText(String.format("%.2f (%.2f%%)", summary.averageScore, summary.averagePercentage));
            highestScoreValue.setText(String.valueOf(summary.highestScore));
            lowestScoreValue.setText(String.valueOf(summary.lowestScore));
            gradeStatusValue.setText(summary.grade + " / " + summary.status);
            gradeStatusValue.setForeground(
                    "Pass".equalsIgnoreCase(summary.status)
                            ? UITheme.SUCCESS_TEXT
                            : UITheme.DANGER_TEXT
            );
            rankValue.setText("#" + summary.rank + " of " + summary.totalRankedStudents);

            List<String> xLabels = new ArrayList<>();
            List<Double> yValues = new ArrayList<>();
            int maxPoints = Math.min(10, attempts.size());
            for (int i = maxPoints - 1; i >= 0; i--) {
                AttemptDAO.AttemptDetail d = attempts.get(i);
                xLabels.add("#" + (maxPoints - i) + " " + d.subjectName);
                yValues.add(d.percentage);
            }
            trendChart.setData(xLabels, yValues);
        }

        radarChart.setData(p.strengths);
        renderHistoryTable(attempts, p.subjectLabel);

        if (p.showToast) {
            UITheme.showToast(this, "History Updated", "Synced latest attempts for " + p.subjectLabel + ".", UITheme.ToastType.SUCCESS);
        }
    }

    private void openSelectedAttemptReview() {
        if (historyTable == null || currentAttempts.isEmpty()) {
            UITheme.showToast(this, "No Attempts", "Take an exam first to review past answers.", UITheme.ToastType.INFO);
            return;
        }
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= currentAttempts.size()) {
            UITheme.showToast(this, "Select an Attempt", "Click any exam row in the table, then click Review.", UITheme.ToastType.WARNING);
            return;
        }
        AttemptDAO.AttemptDetail d = currentAttempts.get(row);
        dispose();
        new ReviewUI(d.id, d.subjectName, d.attemptedDate, d.score, d.totalQuestions);
    }

    private void downloadCertificateForSelectedAttempt() {
        if (historyTable == null || currentAttempts.isEmpty()) {
            UITheme.showToast(this, "No Attempts", "Take an exam first to download a certificate.", UITheme.ToastType.INFO);
            return;
        }
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= currentAttempts.size()) {
            UITheme.showToast(this, "Select an Attempt", "Click a passed exam row in the table first.", UITheme.ToastType.WARNING);
            return;
        }
        AttemptDAO.AttemptDetail d = currentAttempts.get(row);
        if (!d.passed) {
            UITheme.showToast(this, "Not Eligible", "Certificates are only issued for passed examinations.", UITheme.ToastType.WARNING);
            return;
        }

        if (!CertificatePdfBuilder.isAvailable()) {
            UITheme.showToast(this, "PDF Library Missing", "OpenPDF and ZXing libraries were not detected.", UITheme.ToastType.WARNING);
            return;
        }

        String studentDisplayName = ExamStore.getCurrentDisplayName();
        if (studentDisplayName == null || studentDisplayName.trim().isEmpty()) {
            studentDisplayName = (d.userId != null && !d.userId.trim().isEmpty()) ? d.userId : "Student";
        }
        String examTitle = (d.examTitle != null && !d.examTitle.trim().isEmpty()) ? d.examTitle : (d.subjectName + " Exam");
        String safeExam = examTitle.replaceAll("[^a-zA-Z0-9_-]", "_");
        String safeName = studentDisplayName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String defaultFileName = "Certificate_" + safeExam + "_" + safeName + ".pdf";

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

        final String finalStudentName = studentDisplayName;
        final String marksStr = ExamConfig.formatMarks(d.marksObtained) + " / " + ExamConfig.formatMarks(d.totalMarks);
        new SwingWorker<CertificateService.CertificateRecord, Void>() {
            @Override
            protected CertificateService.CertificateRecord doInBackground() throws Exception {
                CertificateService.CertificateRecord rec = CertificateService.getOrCreateCertificate(d.id);
                if (rec == null) {
                    throw new IllegalStateException("Attempt is ineligible or certificate could not be created.");
                }

                String formattedDate = rec.getFormattedIssueDate();
                if (formattedDate == null || formattedDate.trim().isEmpty()) {
                    formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
                }

                CertificatePdfBuilder.buildCertificate(
                        targetFile,
                        rec.certCode,
                        finalStudentName,
                        examTitle,
                        d.subjectName,
                        marksStr,
                        String.format(Locale.ENGLISH, "%.1f%%", d.percentage),
                        d.grade,
                        formattedDate
                );
                return rec;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    CertificateService.CertificateRecord rec = get();
                    UITheme.showToast(StudentPerformanceUI.this, "Certificate Downloaded", "Saved to " + targetFile.getName(), UITheme.ToastType.SUCCESS);

                    int choice = JOptionPane.showConfirmDialog(
                            StudentPerformanceUI.this,
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
                    UITheme.showToast(StudentPerformanceUI.this, "Generation Failed", "Could not create PDF: " + ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        }.execute();

        progressDialog.setVisible(true);
    }

    private void renderHistoryTable(List<AttemptDAO.AttemptDetail> attempts, String subjectLabel) {
        this.currentAttempts = attempts == null ? new ArrayList<>() : attempts;
        this.historyTable = null;
        tableContainer.removeAll();

        if (attempts.isEmpty()) {
            String emptyMsg = "All Subjects".equals(subjectLabel)
                    ? "You have not attempted any exams yet."
                    : "You have not attempted any " + subjectLabel + " exams yet.";
            JLabel empty = new JLabel(emptyMsg, SwingConstants.CENTER);
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(UITheme.TEXT_MUTED);
            tableContainer.add(empty, BorderLayout.CENTER);
            tableContainer.revalidate();
            tableContainer.repaint();
            return;
        }

        String[] columns = {
                "S.No", "Exam", "Marks", "Percentage", "Grade",
                "Status", "Time Taken", "Submission", "Exam Date"
        };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        int rowNo = 1;
        for (AttemptDAO.AttemptDetail attempt : attempts) {
            String examDisp = (attempt.examTitle != null && !attempt.examTitle.trim().isEmpty())
                    ? attempt.examTitle : attempt.subjectName;
            model.addRow(new Object[]{
                    rowNo++,
                    examDisp,
                    ExamConfig.formatMarks(attempt.marksObtained) + " / " + ExamConfig.formatMarks(attempt.totalMarks),
                    String.format("%.2f%%", attempt.percentage),
                    attempt.grade,
                    attempt.status,
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
                    ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                }

                c.setBackground(UITheme.tableRowBackground(tbl, row, isSelected));
                c.setForeground(UITheme.TEXT_PRIMARY);
                c.setFont(UITheme.font(column == 4 ? Font.BOLD : Font.PLAIN, 13f));

                if (!isSelected && column == 4 && value != null) {
                    String text = value.toString();
                    if ("A".equals(text) || "B".equals(text)) {
                        c.setForeground(UITheme.SUCCESS_TEXT);
                    } else if ("C".equals(text) || "D".equals(text)) {
                        c.setForeground(UITheme.WARNING_TEXT);
                    } else {
                        c.setForeground(UITheme.DANGER_TEXT);
                    }
                }
                return c;
            }
        });

        table.getColumnModel().getColumn(5).setCellRenderer(UITheme.createStatusPillRenderer());

        this.historyTable = table;
        table.setToolTipText("Double-click to review answers, or select and click 'Download Certificate' for passed exams");
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedAttemptReview();
                }
            }
        });

        tableContainer.add(UITheme.styleScrollPane(table), BorderLayout.CENTER);
        tableContainer.revalidate();
        tableContainer.repaint();
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
