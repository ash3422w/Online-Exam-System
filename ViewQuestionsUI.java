package com.onlineexam.ui.admin;

import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.AuthComponents;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ViewQuestionsUI extends JFrame {

    private static final String[] OPTION_LETTERS = {"A", "B", "C", "D"};
    private static final int[] SUBJECTS = {
            ExamStore.JAVA,
            ExamStore.DBMS,
            ExamStore.SQL,
            ExamStore.APTITUDE
    };

    private final JTabbedPane tabbedPane;
    private final UITheme.RoundedTextField searchField;
    private final JComboBox<String> difficultyFilterBox;

    public ViewQuestionsUI() {
        setTitle("Question Bank Management - Online Exam System");
        setSize(980, 680);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        QuestionDAO.loadQuestionsFromDB();

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.38f);

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
        JLabel title = new JLabel("Question Bank");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        JLabel sub = new JLabel("Browse, search, import CSV, edit, or delete multiple-choice questions by subject");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));
        headLeft.add(title);
        headLeft.add(sub);

        tabbedPane = new UITheme.ModernTabbedPane();

        UITheme.ModernButton importCsvBtn = UITheme.successButton("Import CSV");
        importCsvBtn.addActionListener(e -> handleCsvImport());

        UITheme.ModernButton refreshBtn = UITheme.secondaryButton("Refresh");
        refreshBtn.addActionListener(e -> {
            QuestionDAO.loadQuestionsFromDB();
            rebuildTabs(tabbedPane.getSelectedIndex());
            UITheme.showToast(this, "Question Bank Synced", "Loaded " + ExamStore.getQuestionCount() + " questions from MySQL.", UITheme.ToastType.INFO);
        });

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        headerRight.setOpaque(false);
        headerRight.add(importCsvBtn);
        headerRight.add(refreshBtn);

        header.add(headLeft, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);

        // Live Search Bar inside subtle frosted card
        GlassPanel searchCard = new GlassPanel(12);
        searchCard.setSpotlightEnabled(false);
        searchCard.setShadowEnabled(false);
        searchCard.setLayout(new BorderLayout(10, 0));
        searchCard.setBorder(new EmptyBorder(8, 12, 8, 12));

        searchField = UITheme.createTextField("Search questions or options across all subjects...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuildTabs(tabbedPane.getSelectedIndex()); }
            @Override public void removeUpdate(DocumentEvent e) { rebuildTabs(tabbedPane.getSelectedIndex()); }
            @Override public void changedUpdate(DocumentEvent e) { rebuildTabs(tabbedPane.getSelectedIndex()); }
        });

        difficultyFilterBox = UITheme.createComboBox(new String[]{
                "All Difficulties", ExamStore.DIFFICULTY_EASY, ExamStore.DIFFICULTY_MEDIUM, ExamStore.DIFFICULTY_HARD
        });
        difficultyFilterBox.setPreferredSize(new Dimension(165, 34));
        difficultyFilterBox.addActionListener(e -> rebuildTabs(tabbedPane.getSelectedIndex()));

        JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterRight.setOpaque(false);
        JLabel diffLbl = UITheme.createFieldLabel("Difficulty:");
        filterRight.add(diffLbl);
        filterRight.add(difficultyFilterBox);

        searchCard.add(searchField, BorderLayout.CENTER);
        searchCard.add(filterRight, BorderLayout.EAST);

        rebuildTabs(0);

        JPanel bodyWrap = new JPanel(new BorderLayout(0, 12));
        bodyWrap.setOpaque(false);
        bodyWrap.setBorder(new EmptyBorder(16, 22, 18, 22));
        bodyWrap.add(searchCard, BorderLayout.NORTH);
        bodyWrap.add(tabbedPane, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(bodyWrap, BorderLayout.CENTER);

        add(root);
        setVisible(true);
    }

    private void rebuildTabs(int selectIndex) {
        String query = (searchField == null || searchField.getText() == null)
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);

        tabbedPane.removeAll();
        for (int subjectId : SUBJECTS) {
            String subjectName = ExamStore.getSubjectName(subjectId);
            List<ExamStore.Question> all = ExamStore.getQuestionsBySubject(subjectId);
            List<ExamStore.Question> filtered = filterQuestions(all, query);
            tabbedPane.addTab(subjectName + " (" + filtered.size() + ")", buildSubjectTab(subjectName, filtered));
        }
        if (selectIndex >= 0 && selectIndex < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(selectIndex);
        }
    }

    private List<ExamStore.Question> filterQuestions(List<ExamStore.Question> source, String query) {
        String selectedDiff = difficultyFilterBox != null ? (String) difficultyFilterBox.getSelectedItem() : "All Difficulties";
        boolean filterByDiff = selectedDiff != null && !"All Difficulties".equalsIgnoreCase(selectedDiff);

        if (query.isEmpty() && !filterByDiff) {
            return source;
        }
        List<ExamStore.Question> out = new ArrayList<>();
        for (ExamStore.Question q : source) {
            if (filterByDiff && !selectedDiff.equalsIgnoreCase(ExamStore.normalizeDifficulty(q.difficulty))) {
                continue;
            }
            if (query.isEmpty()) {
                out.add(q);
                continue;
            }
            boolean matches = q.text != null && q.text.toLowerCase(Locale.ROOT).contains(query);
            if (!matches && q.options != null) {
                for (String opt : q.options) {
                    if (opt != null && opt.toLowerCase(Locale.ROOT).contains(query)) {
                        matches = true;
                        break;
                    }
                }
            }
            if (matches) {
                out.add(q);
            }
        }
        return out;
    }

    private void handleCsvImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Questions CSV (subject,question,option1,option2,option3,option4,correct_option)");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        SwingWorker<QuestionDAO.CsvImportResult, Void> worker = new SwingWorker<QuestionDAO.CsvImportResult, Void>() {
            @Override
            protected QuestionDAO.CsvImportResult doInBackground() {
                return QuestionDAO.importQuestionsFromCSV(file);
            }

            @Override
            protected void done() {
                try {
                    QuestionDAO.CsvImportResult result = get();
                    if (result.success) {
                        rebuildTabs(tabbedPane.getSelectedIndex());
                        boolean hasIssues = !result.warnings.isEmpty() || !result.rowErrors.isEmpty();
                        UITheme.showToast(ViewQuestionsUI.this, "CSV Import Completed", result.message,
                                hasIssues ? UITheme.ToastType.WARNING : UITheme.ToastType.SUCCESS);
                        if (hasIssues) {
                            StringBuilder sb = new StringBuilder(result.message).append("\n");
                            if (!result.rowErrors.isEmpty()) {
                                sb.append("\nSkipped Rows (").append(result.rowErrors.size()).append("):\n");
                                int maxE = Math.min(12, result.rowErrors.size());
                                for (int i = 0; i < maxE; i++) {
                                    sb.append("\u2022 ").append(result.rowErrors.get(i)).append("\n");
                                }
                                if (result.rowErrors.size() > maxE) {
                                    sb.append("... and ").append(result.rowErrors.size() - maxE).append(" more.\n");
                                }
                            }
                            if (!result.warnings.isEmpty()) {
                                sb.append("\nWarnings (").append(result.warnings.size()).append("):\n");
                                int maxW = Math.min(12, result.warnings.size());
                                for (int i = 0; i < maxW; i++) {
                                    sb.append("\u2022 ").append(result.warnings.get(i)).append("\n");
                                }
                                if (result.warnings.size() > maxW) {
                                    sb.append("... and ").append(result.warnings.size() - maxW).append(" more.");
                                }
                            }
                            JOptionPane.showMessageDialog(ViewQuestionsUI.this, sb.toString(),
                                    "CSV Import Report", JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        StringBuilder errDetail = new StringBuilder(result.message);
                        if (!result.rowErrors.isEmpty()) {
                            errDetail.append("\n\nRow Errors:\n");
                            int maxE = Math.min(12, result.rowErrors.size());
                            for (int i = 0; i < maxE; i++) {
                                errDetail.append("\u2022 ").append(result.rowErrors.get(i)).append("\n");
                            }
                        }
                        errDetail.append("\nExpected CSV format:\nsubject,question,option1,option2,option3,option4,correct_option(1-4)[,explanation][,difficulty][,type][,correct_options][,correct_text]");
                        JOptionPane.showMessageDialog(
                                ViewQuestionsUI.this,
                                errDetail.toString(),
                                "CSV Import Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception ex) {
                    UITheme.showToast(ViewQuestionsUI.this, "CSV Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        };
        worker.execute();
    }

    private JPanel buildSubjectTab(String subjectName, List<ExamStore.Question> questions) {
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);

        if (questions.isEmpty()) {
            JLabel empty = new JLabel("No questions added for " + subjectName + " yet.", SwingConstants.CENTER);
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(UITheme.TEXT_MUTED);
            container.add(empty, BorderLayout.CENTER);
            return container;
        }

        UITheme.ScrollablePanel listPanel = new UITheme.ScrollablePanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        listPanel.setBorder(new EmptyBorder(12, 8, 14, 12));

        int qNo = 1;
        for (ExamStore.Question q : questions) {
            listPanel.add(createQuestionCard(qNo++, q));
            listPanel.add(Box.createVerticalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(
                listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(scroll);

        container.add(scroll, BorderLayout.CENTER);
        return container;
    }

    private JPanel createQuestionCard(int qNo, ExamStore.Question q) {
        GlassPanel card = new GlassPanel(14);
        card.setSpotlightEnabled(false);
        card.setLeftAccentColor(UITheme.PRIMARY);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 20, 18, 20));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 285));

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        JLabel title = new JLabel("<html><b>Q" + qNo + ".</b> "
                + (q.text != null ? q.text : "") + "</html>");
        title.setFont(UITheme.font(Font.PLAIN, 15f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionRow.setOpaque(false);

        String qType = ExamStore.normalizeQuestionType(q.type);
        if (!ExamStore.TYPE_SINGLE.equals(qType)) {
            actionRow.add(UITheme.createBadge(ExamStore.formatQuestionTypeLabel(qType), UITheme.VIOLET_SOFT, UITheme.VIOLET));
        }
        actionRow.add(UITheme.createDifficultyBadge(q.difficulty));
        if (q.hasImage) {
            actionRow.add(UITheme.createBadge("Image", UITheme.PINK_SOFT, UITheme.PINK));
        }
        if (q.explanation != null && !q.explanation.trim().isEmpty()) {
            actionRow.add(UITheme.createBadge("Explanation", UITheme.SKY_SOFT, UITheme.SKY));
        }

        UITheme.ModernButton editBtn = UITheme.secondaryButton("Edit");
        editBtn.setFont(UITheme.font(Font.BOLD, 12f));
        editBtn.setBorder(new EmptyBorder(5, 12, 5, 12));
        editBtn.setEnabled(q.id > 0);
        editBtn.addActionListener(e -> openEditDialog(q));

        UITheme.ModernButton deleteBtn = UITheme.dangerButton("Delete");
        deleteBtn.setFont(UITheme.font(Font.BOLD, 12f));
        deleteBtn.setBorder(new EmptyBorder(5, 12, 5, 12));
        deleteBtn.setEnabled(q.id > 0);
        deleteBtn.addActionListener(e -> confirmAndDelete(q));

        actionRow.add(editBtn);
        actionRow.add(deleteBtn);

        topRow.add(title, BorderLayout.CENTER);
        topRow.add(actionRow, BorderLayout.EAST);

        JComponent answerSummary;
        if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            String acc = q.acceptedAnswers != null && !q.acceptedAnswers.isEmpty()
                    ? String.join("  \u2022  ", q.acceptedAnswers) : "(none)";
            UITheme.RoundedPanel fbBox = new UITheme.RoundedPanel(10, UITheme.SUCCESS_SOFT, UITheme.SUCCESS);
            fbBox.setLayout(new BorderLayout());
            fbBox.setBorder(new EmptyBorder(8, 12, 8, 12));
            JLabel fbLbl = new JLabel("<html><b>Accepted Answer(s):</b>  " + acc + "</html>");
            fbLbl.setFont(UITheme.font(Font.BOLD, 13f));
            fbLbl.setForeground(UITheme.SUCCESS_TEXT);
            fbBox.add(fbLbl, BorderLayout.CENTER);
            answerSummary = fbBox;
        } else if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
            JPanel tfGrid = new JPanel(new GridLayout(1, 2, 10, 8));
            tfGrid.setOpaque(false);
            String[] tfLabels = {"True", "False"};
            for (int i = 0; i < 2; i++) {
                boolean isCorrect = (i == q.correctIndex);
                UITheme.RoundedPanel optBox = new UITheme.RoundedPanel(
                        10,
                        isCorrect ? UITheme.SUCCESS_SOFT : new Color(248, 250, 252, 230),
                        isCorrect ? UITheme.SUCCESS : UITheme.BORDER
                );
                optBox.setLayout(new BorderLayout());
                optBox.setBorder(new EmptyBorder(7, 12, 7, 12));
                JLabel optLabel = new JLabel("<html>" + tfLabels[i] + (isCorrect ? "  \u2713" : "") + "</html>");
                optLabel.setFont(UITheme.font(isCorrect ? Font.BOLD : Font.PLAIN, 13f));
                optLabel.setForeground(isCorrect ? UITheme.SUCCESS_TEXT : UITheme.TEXT_SECONDARY);
                optBox.add(optLabel, BorderLayout.CENTER);
                tfGrid.add(optBox);
            }
            answerSummary = tfGrid;
        } else {
            JPanel optionsGrid = new JPanel(new GridLayout(2, 2, 10, 8));
            optionsGrid.setOpaque(false);
            if (q.options != null) {
                for (int i = 0; i < q.options.length; i++) {
                    boolean isCorrect = ExamStore.TYPE_MULTI.equals(qType)
                            ? (q.correctIndices != null && q.correctIndices.contains(i))
                            : (i == q.correctIndex);
                    String letter = i < OPTION_LETTERS.length ? OPTION_LETTERS[i] : String.valueOf(i + 1);
                    String optText = q.options[i] != null ? q.options[i] : "";

                    UITheme.RoundedPanel optBox = new UITheme.RoundedPanel(
                            10,
                            isCorrect ? UITheme.SUCCESS_SOFT : new Color(248, 250, 252, 230),
                            isCorrect ? UITheme.SUCCESS : UITheme.BORDER
                    );
                    optBox.setLayout(new BorderLayout());
                    optBox.setBorder(new EmptyBorder(7, 12, 7, 12));

                    JLabel optLabel = new JLabel("<html>" + letter + ") " + optText + (isCorrect ? "  \u2713" : "") + "</html>");
                    optLabel.setFont(UITheme.font(isCorrect ? Font.BOLD : Font.PLAIN, 13f));
                    optLabel.setForeground(isCorrect ? UITheme.SUCCESS_TEXT : UITheme.TEXT_SECONDARY);
                    optBox.add(optLabel, BorderLayout.CENTER);
                    optionsGrid.add(optBox);
                }
            }
            answerSummary = optionsGrid;
        }

        card.add(topRow, BorderLayout.NORTH);
        card.add(answerSummary, BorderLayout.CENTER);
        return card;
    }

    private void confirmAndDelete(ExamStore.Question q) {
        boolean confirmed = GlassDialog.showConfirm(
                this,
                "Delete Question",
                "Delete this question permanently from MySQL?\n\n\"" + (q.text != null ? q.text : "") + "\"",
                "Delete",
                "Cancel",
                true
        );
        if (!confirmed) {
            return;
        }

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return QuestionDAO.deleteQuestion(q.id);
            }

            @Override
            protected void done() {
                try {
                    if (Boolean.TRUE.equals(get())) {
                        rebuildTabs(tabbedPane.getSelectedIndex());
                        UITheme.showToast(ViewQuestionsUI.this, "Question Deleted", "Removed question #" + q.id + " from MySQL.", UITheme.ToastType.WARNING);
                    } else {
                        UITheme.showToast(ViewQuestionsUI.this, "Database Error", "Failed to delete question from MySQL.", UITheme.ToastType.ERROR);
                    }
                } catch (Exception ex) {
                    UITheme.showToast(ViewQuestionsUI.this, "Database Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        };
        worker.execute();
    }

    private void openEditDialog(ExamStore.Question q) {
        showEditQuestionDialog(this, q, () -> rebuildTabs(tabbedPane.getSelectedIndex()));
    }

    public static void showEditQuestionDialog(Component owner, ExamStore.Question q, Runnable onSaved) {
        if (q == null) return;
        Window ownerWin = owner instanceof Window ? (Window) owner : SwingUtilities.getWindowAncestor(owner);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);

        String initType = ExamStore.normalizeQuestionType(q.type);

        // Top row: Question Type + Difficulty
        JPanel metaRow = new JPanel(new GridLayout(1, 2, 14, 0));
        metaRow.setOpaque(false);
        metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JPanel typeWrap = new JPanel(new BorderLayout(8, 0));
        typeWrap.setOpaque(false);
        JComboBox<String> typeBox = UITheme.createComboBox(AddQuestionUI.QUESTION_TYPES);
        typeBox.setSelectedItem(initType);
        typeWrap.add(UITheme.createFieldLabel("Type"), BorderLayout.WEST);
        typeWrap.add(typeBox, BorderLayout.CENTER);

        JPanel diffWrap = new JPanel(new BorderLayout(8, 0));
        diffWrap.setOpaque(false);
        JComboBox<String> diffBox = UITheme.createComboBox(new String[]{
                ExamStore.DIFFICULTY_EASY, ExamStore.DIFFICULTY_MEDIUM, ExamStore.DIFFICULTY_HARD
        });
        diffBox.setSelectedItem(ExamStore.normalizeDifficulty(q.difficulty));
        diffWrap.add(UITheme.createFieldLabel("Difficulty"), BorderLayout.WEST);
        diffWrap.add(diffBox, BorderLayout.CENTER);

        metaRow.add(typeWrap);
        metaRow.add(diffWrap);

        AuthComponents.FloatingInputField qField = new AuthComponents.FloatingInputField("Question Text", false);
        qField.setInputText(q.text != null ? q.text : "");
        qField.setAlignmentX(Component.LEFT_ALIGNMENT);
        qField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        // Swappable Answer Area
        CardLayout cardLayout = new CardLayout();
        JPanel answerCards = new JPanel(cardLayout);
        answerCards.setOpaque(false);
        answerCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] labels = {"Option 1 (A)", "Option 2 (B)", "Option 3 (C)", "Option 4 (D)"};

        // 1. SINGLE
        JPanel singlePanel = new JPanel();
        singlePanel.setLayout(new BoxLayout(singlePanel, BoxLayout.Y_AXIS));
        singlePanel.setOpaque(false);
        AuthComponents.FloatingInputField[] singleOptFields = new AuthComponents.FloatingInputField[4];
        for (int i = 0; i < 4; i++) {
            String val = (q.options != null && i < q.options.length && q.options[i] != null) ? q.options[i] : "";
            singleOptFields[i] = new AuthComponents.FloatingInputField(labels[i], false);
            singleOptFields[i].setInputText(val);
            singleOptFields[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            singleOptFields[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            singlePanel.add(singleOptFields[i]);
            singlePanel.add(Box.createVerticalStrut(7));
        }
        JPanel singleCorrectRow = new JPanel(new BorderLayout(10, 0));
        singleCorrectRow.setOpaque(false);
        singleCorrectRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        singleCorrectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JComboBox<String> correctBox = UITheme.createComboBox(new String[]{"Option 1", "Option 2", "Option 3", "Option 4"});
        correctBox.setSelectedIndex(Math.max(0, Math.min(3, q.correctIndex)));
        singleCorrectRow.add(UITheme.createFieldLabel("Correct Answer"), BorderLayout.WEST);
        singleCorrectRow.add(correctBox, BorderLayout.CENTER);
        singlePanel.add(singleCorrectRow);

        // 2. MULTI
        JPanel multiPanel = new JPanel();
        multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS));
        multiPanel.setOpaque(false);
        AuthComponents.FloatingInputField[] multiOptFields = new AuthComponents.FloatingInputField[4];
        JCheckBox[] multiChecks = new JCheckBox[4];
        for (int i = 0; i < 4; i++) {
            String val = (q.options != null && i < q.options.length && q.options[i] != null) ? q.options[i] : "";
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            multiOptFields[i] = new AuthComponents.FloatingInputField(labels[i], false);
            multiOptFields[i].setInputText(val);
            multiChecks[i] = new JCheckBox("Correct", q.correctIndices != null && q.correctIndices.contains(i));
            multiChecks[i].setFont(UITheme.font(Font.BOLD, 12f));
            multiChecks[i].setForeground(UITheme.SUCCESS_TEXT);
            multiChecks[i].setOpaque(false);
            multiChecks[i].setFocusPainted(false);
            row.add(multiOptFields[i], BorderLayout.CENTER);
            row.add(multiChecks[i], BorderLayout.EAST);
            multiPanel.add(row);
            multiPanel.add(Box.createVerticalStrut(7));
        }

        // 3. TRUE_FALSE
        JPanel tfPanel = new JPanel(new BorderLayout());
        tfPanel.setOpaque(false);
        UITheme.RoundedPanel tfCard = new UITheme.RoundedPanel(10, UITheme.BG_SUBTLE, UITheme.BORDER);
        tfCard.setLayout(new FlowLayout(FlowLayout.LEFT, 22, 14));
        JRadioButton tfTrue = new JRadioButton("True", q.correctIndex != 1);
        JRadioButton tfFalse = new JRadioButton("False", q.correctIndex == 1);
        for (JRadioButton rb : new JRadioButton[]{tfTrue, tfFalse}) {
            rb.setFont(UITheme.font(Font.BOLD, 14f));
            rb.setForeground(UITheme.TEXT_PRIMARY);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
        }
        ButtonGroup tfGroup = new ButtonGroup();
        tfGroup.add(tfTrue);
        tfGroup.add(tfFalse);
        tfCard.add(UITheme.createFieldLabel("Correct Statement:"));
        tfCard.add(tfTrue);
        tfCard.add(tfFalse);
        tfPanel.add(tfCard, BorderLayout.NORTH);

        // 4. FILL_BLANK
        JPanel fillPanel = new JPanel(new BorderLayout(0, 5));
        fillPanel.setOpaque(false);
        JTextArea fillArea = new JTextArea(4, 34);
        fillArea.setLineWrap(true);
        fillArea.setWrapStyleWord(true);
        fillArea.setFont(UITheme.font(Font.PLAIN, 13f));
        fillArea.setForeground(UITheme.TEXT_PRIMARY);
        fillArea.setBackground(Color.WHITE);
        fillArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        if (q.acceptedAnswers != null && !q.acceptedAnswers.isEmpty()) {
            fillArea.setText(String.join(" | ", q.acceptedAnswers));
        }
        JScrollPane fillScroll = new JScrollPane(fillArea);
        fillScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
        fillScroll.setPreferredSize(new Dimension(500, 88));
        fillPanel.add(UITheme.createFieldLabel("Accepted Answers (separate with | or new lines):"), BorderLayout.NORTH);
        fillPanel.add(fillScroll, BorderLayout.CENTER);

        answerCards.add(singlePanel, ExamStore.TYPE_SINGLE);
        answerCards.add(multiPanel, ExamStore.TYPE_MULTI);
        answerCards.add(tfPanel, ExamStore.TYPE_TRUE_FALSE);
        answerCards.add(fillPanel, ExamStore.TYPE_FILL_BLANK);
        cardLayout.show(answerCards, initType);

        typeBox.addActionListener(e -> {
            String t = (String) typeBox.getSelectedItem();
            if (t != null) {
                cardLayout.show(answerCards, t);
                form.revalidate();
                form.repaint();
            }
        });

        // Optional image picker (loads existing image lazily if present)
        AddQuestionUI.ImagePickerPanel imagePicker = new AddQuestionUI.ImagePickerPanel(ownerWin);
        imagePicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        imagePicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        if (q.hasImage && q.id > 0) {
            imagePicker.loadExistingImageAsync(q.id);
        }

        // Multi-line Explanation (optional) + character counter (max 1000)
        JPanel expHeader = new JPanel(new BorderLayout());
        expHeader.setOpaque(false);
        expHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        expHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel expCounter = new JLabel("0 / " + QuestionDAO.MAX_EXPLANATION_LENGTH);
        expCounter.setFont(UITheme.font(Font.BOLD, 11f));
        expCounter.setForeground(UITheme.TEXT_MUTED);
        expHeader.add(UITheme.createFieldLabel("Explanation (optional)"), BorderLayout.WEST);
        expHeader.add(expCounter, BorderLayout.EAST);

        JTextArea expArea = AddQuestionUI.createExplanationArea(expCounter);
        expArea.setText(q.explanation != null ? q.explanation : "");

        JScrollPane expScroll = new JScrollPane(expArea);
        expScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        expScroll.setPreferredSize(new Dimension(500, 70));
        expScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        expScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
        expScroll.getViewport().setBackground(Color.WHITE);
        UITheme.applyModernScrollBars(expScroll);

        AuthComponents.InlineValidationLabel errLbl = new AuthComponents.InlineValidationLabel();
        errLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(metaRow);
        form.add(Box.createVerticalStrut(10));
        form.add(qField);
        form.add(Box.createVerticalStrut(9));
        form.add(answerCards);
        form.add(Box.createVerticalStrut(10));
        form.add(imagePicker);
        form.add(Box.createVerticalStrut(10));
        form.add(expHeader);
        form.add(Box.createVerticalStrut(4));
        form.add(expScroll);
        form.add(Box.createVerticalStrut(6));
        form.add(errLbl);

        JScrollPane formScroll = new JScrollPane(form,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(formScroll);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);

        UITheme.ModernButton cancelBtn = UITheme.secondaryButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(112, 38));

        UITheme.ModernButton saveBtn = UITheme.gradientPrimaryButton("Save Changes");
        saveBtn.setPreferredSize(new Dimension(142, 38));

        footer.add(cancelBtn);
        footer.add(saveBtn);

        GlassDialog dialog = new GlassDialog(
                ownerWin,
                "Edit Question #" + q.id,
                "Update type, difficulty, answers, optional image, or explanation",
                formScroll,
                footer,
                640,
                660
        );

        qField.addDocumentChangeListener(errLbl::clear);
        for (AuthComponents.FloatingInputField f : singleOptFields) f.addDocumentChangeListener(errLbl::clear);
        for (AuthComponents.FloatingInputField f : multiOptFields) f.addDocumentChangeListener(errLbl::clear);

        cancelBtn.addActionListener(e -> dialog.closeAnimated(null));
        saveBtn.addActionListener(e -> {
            String newText = qField.getInputText().trim();
            if (newText.isEmpty()) {
                qField.flashError();
                errLbl.showError("Question text cannot be empty.");
                AuthComponents.shakeCard(dialog.getCard());
                return;
            }

            String newType = ExamStore.normalizeQuestionType((String) typeBox.getSelectedItem());
            String newDifficulty = ExamStore.normalizeDifficulty((String) diffBox.getSelectedItem());
            String newExplanation = expArea.getText().trim();

            ExamStore.Question updated = new ExamStore.Question(
                    q.id, q.subjectId, newText, new String[]{"", "", "", ""}, 0,
                    new int[]{1, 2, 3, 4}, newExplanation, newDifficulty
            );
            updated.setType(newType);

            if (ExamStore.TYPE_SINGLE.equals(newType)) {
                String[] newOpts = new String[4];
                boolean missing = false;
                for (int i = 0; i < 4; i++) {
                    newOpts[i] = singleOptFields[i].getInputText().trim();
                    if (newOpts[i].isEmpty()) {
                        singleOptFields[i].flashError();
                        missing = true;
                    }
                }
                if (missing) {
                    errLbl.showError("All 4 answer options are required.");
                    AuthComponents.shakeCard(dialog.getCard());
                    return;
                }
                updated.options = newOpts;
                updated.correctIndex = correctBox.getSelectedIndex();
                updated.setCorrectIndices(Collections.singleton(updated.correctIndex));
            } else if (ExamStore.TYPE_MULTI.equals(newType)) {
                String[] newOpts = new String[4];
                boolean missing = false;
                Set<Integer> selCorrect = new LinkedHashSet<>();
                for (int i = 0; i < 4; i++) {
                    newOpts[i] = multiOptFields[i].getInputText().trim();
                    if (newOpts[i].isEmpty()) {
                        multiOptFields[i].flashError();
                        missing = true;
                    }
                    if (multiChecks[i].isSelected()) selCorrect.add(i);
                }
                if (missing) {
                    errLbl.showError("All 4 answer options are required for Multi-Select.");
                    AuthComponents.shakeCard(dialog.getCard());
                    return;
                }
                if (selCorrect.isEmpty()) {
                    errLbl.showError("Check at least one correct option.");
                    AuthComponents.shakeCard(dialog.getCard());
                    return;
                }
                updated.options = newOpts;
                updated.setCorrectIndices(selCorrect);
            } else if (ExamStore.TYPE_TRUE_FALSE.equals(newType)) {
                updated.options = new String[]{"True", "False", "", ""};
                int tfIdx = tfTrue.isSelected() ? 0 : 1;
                updated.correctIndex = tfIdx;
                updated.setCorrectIndices(Collections.singleton(tfIdx));
            } else if (ExamStore.TYPE_FILL_BLANK.equals(newType)) {
                List<String> acc = AddQuestionUI.parseAcceptedAnswersInput(fillArea.getText());
                if (acc.isEmpty()) {
                    errLbl.showError("Enter at least one accepted answer.");
                    AuthComponents.shakeCard(dialog.getCard());
                    return;
                }
                updated.options = new String[]{"", "", "", ""};
                updated.correctIndex = 0;
                updated.setAcceptedAnswers(acc);
            }

            final boolean updateImg = imagePicker.isModified();
            final byte[] imgBytes = imagePicker.getImageBytes();

            saveBtn.setEnabled(false);
            saveBtn.setText("Saving...");

            SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return QuestionDAO.updateQuestion(updated, imgBytes, updateImg);
                }

                @Override
                protected void done() {
                    saveBtn.setEnabled(true);
                    saveBtn.setText("Save Changes");
                    try {
                        if (Boolean.TRUE.equals(get())) {
                            dialog.closeAnimated(() -> {
                                if (onSaved != null) {
                                    onSaved.run();
                                }
                                if (ownerWin instanceof JFrame) {
                                    UITheme.showToast((JFrame) ownerWin, "Question Updated",
                                            "Saved changes to question #" + q.id + ".", UITheme.ToastType.SUCCESS);
                                }
                            });
                        } else {
                            errLbl.showError("Failed to update question in MySQL.");
                            AuthComponents.shakeCard(dialog.getCard());
                        }
                    } catch (Exception ex) {
                        errLbl.showError("Database error: " + ex.getMessage());
                        AuthComponents.shakeCard(dialog.getCard());
                    }
                }
            };
            worker.execute();
        });

        dialog.showAnimated();
    }
}
