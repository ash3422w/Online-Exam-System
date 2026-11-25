package com.onlineexam.ui.admin;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.AuthComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

public class AddQuestionUI extends JFrame {

    private static final int MAX_EXPLANATION_CHARS = QuestionDAO.MAX_EXPLANATION_LENGTH;
    static final String[] QUESTION_TYPES = {
            ExamStore.TYPE_SINGLE,
            ExamStore.TYPE_MULTI,
            ExamStore.TYPE_TRUE_FALSE,
            ExamStore.TYPE_FILL_BLANK
    };

    private final AuthComponents.FloatingInputField questionField;
    private final JComboBox<String> subjectBox;
    private final JComboBox<String> difficultyBox;
    private final JComboBox<String> typeBox;

    // Swappable type-specific answer controls
    private final CardLayout answerCardLayout;
    private final JPanel answerCardsPanel;
    private final AuthComponents.FloatingInputField[] singleOptionFields;
    private final JComboBox<String> singleCorrectBox;
    private final AuthComponents.FloatingInputField[] multiOptionFields;
    private final JCheckBox[] multiCorrectChecks;
    private final JRadioButton tfTrueRadio;
    private final JRadioButton tfFalseRadio;
    private final JTextArea fillAcceptedArea;

    // Optional image chooser + explanation
    private final ImagePickerPanel imagePicker;
    private final JTextArea explanationArea;
    private final JLabel charCounterLabel;
    private final AuthComponents.InlineValidationLabel validationLabel;
    private final UITheme.ModernButton addBtn;
    private final GlassPanel formCard;

    public AddQuestionUI() {
        setTitle("Add Question - Online Exam System");
        setSize(830, 840);
        setMinimumSize(new Dimension(720, 680));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout());
        root.setIntensity(0.45f);

        // Frosted-white light top bar
        JPanel header = new JPanel(new BorderLayout(16, 0)) {
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
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(14, 26, 14, 26));

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);

        JLabel title = new JLabel("Add New Question");
        title.setFont(UITheme.font(Font.BOLD, 20f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel("Create SINGLE, MULTI-select, TRUE/FALSE, or FILL_BLANK questions with optional image & explanation");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));

        headLeft.add(title);
        headLeft.add(sub);
        header.add(headLeft, BorderLayout.WEST);

        // Frosted-white GlassPanel Form Card
        formCard = new GlassPanel(16);
        formCard.setShadowColor(UITheme.PRIMARY);
        formCard.setSpotlightEnabled(false);
        formCard.setLayout(new GridBagLayout());
        formCard.setBorder(new EmptyBorder(20, 26, 22, 26));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Top row: Subject, Type, Difficulty
        gbc.gridy = 0;
        gbc.gridx = 0; gbc.weightx = 0.34; gbc.insets = new Insets(0, 6, 4, 8);
        formCard.add(UITheme.createFieldLabel("Subject"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.33; gbc.insets = new Insets(0, 8, 4, 8);
        formCard.add(UITheme.createFieldLabel("Question Type"), gbc);
        gbc.gridx = 2; gbc.weightx = 0.33; gbc.insets = new Insets(0, 8, 4, 6);
        formCard.add(UITheme.createFieldLabel("Difficulty"), gbc);

        subjectBox = UITheme.createComboBox(new String[]{"Java", "DBMS", "Aptitude", "SQL"});
        typeBox = UITheme.createComboBox(QUESTION_TYPES);
        typeBox.setSelectedItem(ExamStore.TYPE_SINGLE);
        difficultyBox = UITheme.createComboBox(new String[]{
                ExamStore.DIFFICULTY_EASY, ExamStore.DIFFICULTY_MEDIUM, ExamStore.DIFFICULTY_HARD
        });
        difficultyBox.setSelectedItem(ExamStore.DIFFICULTY_MEDIUM);

        gbc.gridy = 1;
        gbc.gridx = 0; gbc.insets = new Insets(0, 6, 12, 8);
        formCard.add(subjectBox, gbc);
        gbc.gridx = 1; gbc.insets = new Insets(0, 8, 12, 8);
        formCard.add(typeBox, gbc);
        gbc.gridx = 2; gbc.insets = new Insets(0, 8, 12, 6);
        formCard.add(difficultyBox, gbc);

        // Question Text spanning 3 columns
        questionField = new AuthComponents.FloatingInputField("Question Text", false);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 6, 10, 6);
        formCard.add(questionField, gbc);

        // Swappable Answer Input Cards per Question Type
        answerCardLayout = new CardLayout();
        answerCardsPanel = new JPanel(answerCardLayout);
        answerCardsPanel.setOpaque(false);

        // 1. SINGLE panel
        JPanel singlePanel = new JPanel();
        singlePanel.setLayout(new BoxLayout(singlePanel, BoxLayout.Y_AXIS));
        singlePanel.setOpaque(false);
        singleOptionFields = new AuthComponents.FloatingInputField[4];
        String[] optLabels = {"Option 1 (A)", "Option 2 (B)", "Option 3 (C)", "Option 4 (D)"};
        for (int i = 0; i < 4; i++) {
            singleOptionFields[i] = new AuthComponents.FloatingInputField(optLabels[i], false);
            singleOptionFields[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            singlePanel.add(singleOptionFields[i]);
            singlePanel.add(Box.createVerticalStrut(8));
        }
        JPanel singleAnsRow = new JPanel(new BorderLayout(10, 0));
        singleAnsRow.setOpaque(false);
        singleAnsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        singleCorrectBox = UITheme.createComboBox(new String[]{"Option 1", "Option 2", "Option 3", "Option 4"});
        singleAnsRow.add(UITheme.createFieldLabel("Correct Answer"), BorderLayout.WEST);
        singleAnsRow.add(singleCorrectBox, BorderLayout.CENTER);
        singlePanel.add(singleAnsRow);

        // 2. MULTI panel (4 options + Correct checkboxes)
        JPanel multiPanel = new JPanel();
        multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS));
        multiPanel.setOpaque(false);
        JLabel multiHint = new JLabel("Enter 4 options and check all correct answers:");
        multiHint.setFont(UITheme.font(Font.BOLD, 12f));
        multiHint.setForeground(UITheme.VIOLET);
        multiHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        multiPanel.add(multiHint);
        multiPanel.add(Box.createVerticalStrut(6));

        multiOptionFields = new AuthComponents.FloatingInputField[4];
        multiCorrectChecks = new JCheckBox[4];
        for (int i = 0; i < 4; i++) {
            JPanel rowPanel = new JPanel(new BorderLayout(10, 0));
            rowPanel.setOpaque(false);
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            multiOptionFields[i] = new AuthComponents.FloatingInputField(optLabels[i], false);
            multiCorrectChecks[i] = new JCheckBox("Correct");
            multiCorrectChecks[i].setFont(UITheme.font(Font.BOLD, 12f));
            multiCorrectChecks[i].setForeground(UITheme.SUCCESS_TEXT);
            multiCorrectChecks[i].setOpaque(false);
            multiCorrectChecks[i].setFocusPainted(false);
            multiCorrectChecks[i].setCursor(new Cursor(Cursor.HAND_CURSOR));
            rowPanel.add(multiOptionFields[i], BorderLayout.CENTER);
            rowPanel.add(multiCorrectChecks[i], BorderLayout.EAST);
            multiPanel.add(rowPanel);
            multiPanel.add(Box.createVerticalStrut(8));
        }

        // 3. TRUE_FALSE panel
        JPanel tfPanel = new JPanel(new BorderLayout(0, 8));
        tfPanel.setOpaque(false);
        UITheme.RoundedPanel tfCard = new UITheme.RoundedPanel(12, UITheme.BG_SUBTLE, UITheme.BORDER);
        tfCard.setLayout(new FlowLayout(FlowLayout.LEFT, 24, 16));
        JLabel tfLbl = UITheme.createFieldLabel("Correct Statement:");
        tfTrueRadio = new JRadioButton("True", true);
        tfFalseRadio = new JRadioButton("False", false);
        for (JRadioButton rb : new JRadioButton[]{tfTrueRadio, tfFalseRadio}) {
            rb.setFont(UITheme.font(Font.BOLD, 14f));
            rb.setForeground(UITheme.TEXT_PRIMARY);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
            rb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        ButtonGroup tfGroup = new ButtonGroup();
        tfGroup.add(tfTrueRadio);
        tfGroup.add(tfFalseRadio);
        tfCard.add(tfLbl);
        tfCard.add(tfTrueRadio);
        tfCard.add(tfFalseRadio);
        tfPanel.add(tfCard, BorderLayout.NORTH);

        // 4. FILL_BLANK panel
        JPanel fillPanel = new JPanel(new BorderLayout(0, 6));
        fillPanel.setOpaque(false);
        JLabel fillLbl = UITheme.createFieldLabel("Accepted Answers (separate with | or one per line; case-insensitive):");
        fillAcceptedArea = new JTextArea(4, 36);
        fillAcceptedArea.setLineWrap(true);
        fillAcceptedArea.setWrapStyleWord(true);
        fillAcceptedArea.setFont(UITheme.font(Font.PLAIN, 13.5f));
        fillAcceptedArea.setForeground(UITheme.TEXT_PRIMARY);
        fillAcceptedArea.setBackground(Color.WHITE);
        fillAcceptedArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane fillScroll = new JScrollPane(fillAcceptedArea);
        fillScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
        fillScroll.setPreferredSize(new Dimension(560, 96));
        JLabel fillExample = new JLabel("Example: polymorphism | runtime polymorphism");
        fillExample.setFont(UITheme.font(Font.PLAIN, 11.5f));
        fillExample.setForeground(UITheme.TEXT_MUTED);
        fillPanel.add(fillLbl, BorderLayout.NORTH);
        fillPanel.add(fillScroll, BorderLayout.CENTER);
        fillPanel.add(fillExample, BorderLayout.SOUTH);

        answerCardsPanel.add(singlePanel, ExamStore.TYPE_SINGLE);
        answerCardsPanel.add(multiPanel, ExamStore.TYPE_MULTI);
        answerCardsPanel.add(tfPanel, ExamStore.TYPE_TRUE_FALSE);
        answerCardsPanel.add(fillPanel, ExamStore.TYPE_FILL_BLANK);

        typeBox.addActionListener(e -> {
            String sel = (String) typeBox.getSelectedItem();
            if (sel != null) {
                answerCardLayout.show(answerCardsPanel, sel);
                formCard.revalidate();
                formCard.repaint();
            }
        });

        gbc.gridy = 3;
        gbc.insets = new Insets(2, 6, 10, 6);
        formCard.add(answerCardsPanel, gbc);

        // Optional Image Picker (preview + Remove button; <= 2 MB, max 1000px wide)
        imagePicker = new ImagePickerPanel(this);
        gbc.gridy = 4;
        gbc.insets = new Insets(2, 6, 10, 6);
        formCard.add(imagePicker, gbc);

        // Explanation (optional) multi-line area + live character counter (max 1000)
        JPanel expHeader = new JPanel(new BorderLayout());
        expHeader.setOpaque(false);
        expHeader.add(UITheme.createFieldLabel("Explanation (optional)"), BorderLayout.WEST);

        charCounterLabel = new JLabel("0 / " + MAX_EXPLANATION_CHARS);
        charCounterLabel.setFont(UITheme.font(Font.BOLD, 11f));
        charCounterLabel.setForeground(UITheme.TEXT_MUTED);
        expHeader.add(charCounterLabel, BorderLayout.EAST);

        gbc.gridy = 5;
        gbc.insets = new Insets(2, 6, 4, 6);
        formCard.add(expHeader, gbc);

        explanationArea = createExplanationArea(charCounterLabel);
        JScrollPane expScroll = new JScrollPane(explanationArea);
        expScroll.setPreferredSize(new Dimension(560, 74));
        expScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
        expScroll.getViewport().setBackground(Color.WHITE);
        UITheme.applyModernScrollBars(expScroll);

        gbc.gridy = 6;
        gbc.insets = new Insets(0, 6, 8, 6);
        formCard.add(expScroll, gbc);

        // Inline Fading Validation Label
        validationLabel = new AuthComponents.InlineValidationLabel();
        gbc.gridy = 7;
        gbc.insets = new Insets(0, 6, 6, 6);
        formCard.add(validationLabel, gbc);

        questionField.addDocumentChangeListener(validationLabel::clear);
        for (AuthComponents.FloatingInputField f : singleOptionFields) f.addDocumentChangeListener(validationLabel::clear);
        for (AuthComponents.FloatingInputField f : multiOptionFields) f.addDocumentChangeListener(validationLabel::clear);

        // Action buttons
        UITheme.ModernButton closeBtn = UITheme.secondaryButton("Close");
        closeBtn.setPreferredSize(new Dimension(126, 42));
        closeBtn.addActionListener(e -> dispose());

        addBtn = UITheme.gradientPrimaryButton("Save Question");
        addBtn.setPreferredSize(new Dimension(172, 42));
        addBtn.addActionListener(e -> addQuestion());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(closeBtn);
        btnPanel.add(addBtn);

        gbc.gridy = 8;
        gbc.insets = new Insets(4, 6, 0, 6);
        formCard.add(btnPanel, gbc);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(16, 26, 20, 26));
        wrap.add(formCard, BorderLayout.CENTER);

        JScrollPane pageScroll = new JScrollPane(
                wrap,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        pageScroll.setOpaque(false);
        pageScroll.getViewport().setOpaque(false);
        pageScroll.setBorder(BorderFactory.createEmptyBorder());
        UITheme.applyModernScrollBars(pageScroll);

        root.add(header, BorderLayout.NORTH);
        root.add(pageScroll, BorderLayout.CENTER);

        add(root);
        setVisible(true);
        SwingUtilities.invokeLater(() -> Anim.slideIn(formCard, Anim.Direction.UP, 380, 20));
    }

    static List<String> parseAcceptedAnswersInput(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        String[] parts = raw.split("[|\\r\\n]+");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static JTextArea createExplanationArea(JLabel counterLabel) {
        JTextArea area = new JTextArea(3, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UITheme.font(Font.PLAIN, 13f));
        area.setForeground(UITheme.TEXT_PRIMARY);
        area.setBackground(Color.WHITE);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));

        ((AbstractDocument) area.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                int avail = MAX_EXPLANATION_CHARS - fb.getDocument().getLength();
                if (avail <= 0) return;
                super.insertString(fb, offset, string.length() > avail ? string.substring(0, avail) : string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) {
                    super.replace(fb, offset, length, null, attrs);
                    return;
                }
                int avail = MAX_EXPLANATION_CHARS - (fb.getDocument().getLength() - length);
                if (avail <= 0) return;
                super.replace(fb, offset, length, text.length() > avail ? text.substring(0, avail) : text, attrs);
            }
        });

        Runnable updateCounter = () -> {
            int len = area.getText().length();
            counterLabel.setText(len + " / " + MAX_EXPLANATION_CHARS);
            if (len >= MAX_EXPLANATION_CHARS) {
                counterLabel.setForeground(UITheme.DANGER_TEXT);
            } else if (len >= 850) {
                counterLabel.setForeground(UITheme.WARNING_TEXT);
            } else {
                counterLabel.setForeground(UITheme.TEXT_MUTED);
            }
        };

        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCounter.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCounter.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCounter.run(); }
        });
        return area;
    }

    private void addQuestion() {
        String questionText = questionField.getInputText().trim();
        if (questionText.isEmpty()) {
            questionField.flashError();
            validationLabel.showError("Please enter the question text before saving.");
            AuthComponents.shakeCard(formCard);
            return;
        }

        int selectedIndex = subjectBox.getSelectedIndex();
        int subjectId;
        if (selectedIndex == 0) subjectId = ExamStore.JAVA;
        else if (selectedIndex == 1) subjectId = ExamStore.DBMS;
        else if (selectedIndex == 2) subjectId = ExamStore.APTITUDE;
        else subjectId = ExamStore.SQL;

        String qType = ExamStore.normalizeQuestionType((String) typeBox.getSelectedItem());
        String difficulty = ExamStore.normalizeDifficulty((String) difficultyBox.getSelectedItem());
        String explanation = explanationArea.getText().trim();

        ExamStore.Question q = new ExamStore.Question(
                0, subjectId, questionText, new String[]{"", "", "", ""}, 0,
                new int[]{1, 2, 3, 4}, explanation, difficulty
        );
        q.setType(qType);

        if (ExamStore.TYPE_SINGLE.equals(qType)) {
            String[] opts = new String[4];
            boolean missing = false;
            for (int i = 0; i < 4; i++) {
                opts[i] = singleOptionFields[i].getInputText().trim();
                if (opts[i].isEmpty()) {
                    singleOptionFields[i].flashError();
                    missing = true;
                }
            }
            if (missing) {
                validationLabel.showError("All 4 answer options are required.");
                AuthComponents.shakeCard(formCard);
                return;
            }
            q.options = opts;
            q.correctIndex = singleCorrectBox.getSelectedIndex();
            q.setCorrectIndices(Collections.singleton(q.correctIndex));
        } else if (ExamStore.TYPE_MULTI.equals(qType)) {
            String[] opts = new String[4];
            boolean missing = false;
            Set<Integer> selectedCorrect = new LinkedHashSet<>();
            for (int i = 0; i < 4; i++) {
                opts[i] = multiOptionFields[i].getInputText().trim();
                if (opts[i].isEmpty()) {
                    multiOptionFields[i].flashError();
                    missing = true;
                }
                if (multiCorrectChecks[i].isSelected()) {
                    selectedCorrect.add(i);
                }
            }
            if (missing) {
                validationLabel.showError("All 4 answer options are required for Multi-Select.");
                AuthComponents.shakeCard(formCard);
                return;
            }
            if (selectedCorrect.isEmpty()) {
                validationLabel.showError("Check at least one correct option for Multi-Select.");
                AuthComponents.shakeCard(formCard);
                return;
            }
            q.options = opts;
            q.setCorrectIndices(selectedCorrect);
        } else if (ExamStore.TYPE_TRUE_FALSE.equals(qType)) {
            q.options = new String[]{"True", "False", "", ""};
            int tfIdx = tfTrueRadio.isSelected() ? 0 : 1;
            q.correctIndex = tfIdx;
            q.setCorrectIndices(Collections.singleton(tfIdx));
        } else if (ExamStore.TYPE_FILL_BLANK.equals(qType)) {
            List<String> accepted = parseAcceptedAnswersInput(fillAcceptedArea.getText());
            if (accepted.isEmpty()) {
                validationLabel.showError("Enter at least one accepted answer for Fill-in-the-Blank.");
                AuthComponents.shakeCard(formCard);
                return;
            }
            q.options = new String[]{"", "", "", ""};
            q.correctIndex = 0;
            q.setAcceptedAnswers(accepted);
        }

        final byte[] imgBytes = imagePicker.getImageBytes();

        addBtn.setEnabled(false);
        addBtn.setText("Saving...");

        SwingWorker<QuestionDAO.AddQuestionResult, Void> worker = new SwingWorker<QuestionDAO.AddQuestionResult, Void>() {
            @Override
            protected QuestionDAO.AddQuestionResult doInBackground() {
                return QuestionDAO.addQuestion(q, imgBytes);
            }

            @Override
            protected void done() {
                addBtn.setEnabled(true);
                addBtn.setText("Save Question");
                try {
                    QuestionDAO.AddQuestionResult res = get();
                    if (!res.success) {
                        validationLabel.showError(res.message);
                        AuthComponents.shakeCard(formCard);
                        return;
                    }
                    validationLabel.showSuccess(res.message);
                    UITheme.showToast(AddQuestionUI.this, "Question Saved", res.message, UITheme.ToastType.SUCCESS);
                    questionField.setInputText("");
                    for (AuthComponents.FloatingInputField f : singleOptionFields) f.setInputText("");
                    for (AuthComponents.FloatingInputField f : multiOptionFields) f.setInputText("");
                    for (JCheckBox cb : multiCorrectChecks) cb.setSelected(false);
                    tfTrueRadio.setSelected(true);
                    fillAcceptedArea.setText("");
                    explanationArea.setText("");
                    imagePicker.clearImage();
                } catch (Exception ex) {
                    validationLabel.showError("Database error: " + ex.getMessage());
                    AuthComponents.shakeCard(formCard);
                }
            }
        };
        worker.execute();
    }

    // ================= REUSABLE OPTIONAL IMAGE PICKER WITH PREVIEW & REMOVE =================

    static class ImagePickerPanel extends UITheme.RoundedPanel {
        private final Component parentWindow;
        private final JLabel previewLabel;
        private final JLabel statusLabel;
        private final UITheme.ModernButton chooseBtn;
        private final UITheme.ModernButton removeBtn;
        private byte[] imageBytes = null;
        private boolean modified = false;

        ImagePickerPanel(Component parentWindow) {
            super(10, UITheme.BG_SUBTLE, UITheme.BORDER);
            this.parentWindow = parentWindow;
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));

            previewLabel = new JLabel("No image", SwingConstants.CENTER);
            previewLabel.setFont(UITheme.font(Font.PLAIN, 11f));
            previewLabel.setForeground(UITheme.TEXT_MUTED);
            previewLabel.setPreferredSize(new Dimension(112, 64));
            previewLabel.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));
            previewLabel.setOpaque(true);
            previewLabel.setBackground(Color.WHITE);

            JPanel centerInfo = new JPanel(new GridLayout(2, 1, 0, 2));
            centerInfo.setOpaque(false);
            JLabel titleLbl = UITheme.createFieldLabel("Question Image (optional)");
            statusLabel = new JLabel("PNG or JPEG, max 2 MB (auto-scaled to max 1000 px wide)");
            statusLabel.setFont(UITheme.font(Font.PLAIN, 11.5f));
            statusLabel.setForeground(UITheme.TEXT_MUTED);
            centerInfo.add(titleLbl);
            centerInfo.add(statusLabel);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
            btns.setOpaque(false);
            chooseBtn = UITheme.secondaryButton("Choose Image...");
            chooseBtn.setFont(UITheme.font(Font.BOLD, 11.5f));
            chooseBtn.setBorder(new EmptyBorder(6, 12, 6, 12));
            chooseBtn.addActionListener(e -> chooseImageFile());

            removeBtn = UITheme.dangerButton("Remove");
            removeBtn.setFont(UITheme.font(Font.BOLD, 11.5f));
            removeBtn.setBorder(new EmptyBorder(6, 10, 6, 10));
            removeBtn.setVisible(false);
            removeBtn.addActionListener(e -> {
                modified = true;
                clearImage();
            });

            btns.add(chooseBtn);
            btns.add(removeBtn);

            add(previewLabel, BorderLayout.WEST);
            add(centerInfo, BorderLayout.CENTER);
            add(btns, BorderLayout.EAST);
        }

        byte[] getImageBytes() {
            return imageBytes;
        }

        boolean isModified() {
            return modified;
        }

        void loadExistingImageAsync(int questionId) {
            statusLabel.setText("Loading existing image...");
            new SwingWorker<byte[], Void>() {
                @Override
                protected byte[] doInBackground() {
                    return QuestionDAO.getQuestionImage(questionId);
                }

                @Override
                protected void done() {
                    try {
                        byte[] bytes = get();
                        if (bytes != null && bytes.length > 0 && !modified) {
                            setPreviewBytes(bytes, "Existing image (" + Math.max(1, bytes.length / 1024) + " KB)");
                        } else if (!modified) {
                            clearImage();
                        }
                    } catch (Exception ignored) {
                        if (!modified) clearImage();
                    }
                }
            }.execute();
        }

        void clearImage() {
            this.imageBytes = null;
            previewLabel.setIcon(null);
            previewLabel.setText("No image");
            statusLabel.setText("PNG or JPEG, max 2 MB (auto-scaled to max 1000 px wide)");
            statusLabel.setForeground(UITheme.TEXT_MUTED);
            removeBtn.setVisible(false);
        }

        private void setPreviewBytes(byte[] bytes, String caption) {
            this.imageBytes = bytes;
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    double scale = Math.min(1.0, Math.min(106.0 / img.getWidth(), 58.0 / img.getHeight()));
                    int w = Math.max(16, (int) Math.round(img.getWidth() * scale));
                    int h = Math.max(16, (int) Math.round(img.getHeight() * scale));
                    previewLabel.setText(null);
                    previewLabel.setIcon(new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                }
            } catch (Exception ignored) {}
            statusLabel.setText(caption);
            statusLabel.setForeground(UITheme.SUCCESS_TEXT);
            removeBtn.setVisible(true);
        }

        private void chooseImageFile() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Question Image (max 2 MB)");
            chooser.setFileFilter(new FileNameExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg", "gif", "bmp"));
            if (chooser.showOpenDialog(parentWindow) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = chooser.getSelectedFile();
            if (file.length() > QuestionDAO.MAX_IMAGE_FILE_BYTES) {
                statusLabel.setText("Rejected: file exceeds 2 MB limit (" + (file.length() / (1024 * 1024)) + "+ MB).");
                statusLabel.setForeground(UITheme.DANGER_TEXT);
                if (parentWindow instanceof JFrame) {
                    UITheme.showToast((JFrame) parentWindow, "Image Too Large",
                            "Please select an image of 2 MB or smaller.", UITheme.ToastType.ERROR);
                }
                return;
            }

            chooseBtn.setEnabled(false);
            statusLabel.setText("Processing image...");
            new SwingWorker<byte[], Void>() {
                private String error;

                @Override
                protected byte[] doInBackground() {
                    try {
                        return QuestionDAO.prepareQuestionImage(file);
                    } catch (Exception ex) {
                        error = ex.getMessage();
                        return null;
                    }
                }

                @Override
                protected void done() {
                    chooseBtn.setEnabled(true);
                    try {
                        byte[] prepared = get();
                        if (prepared == null) {
                            statusLabel.setText(error != null ? error : "Could not process image.");
                            statusLabel.setForeground(UITheme.DANGER_TEXT);
                            return;
                        }
                        modified = true;
                        setPreviewBytes(prepared, file.getName() + " (" + Math.max(1, prepared.length / 1024) + " KB ready)");
                    } catch (Exception ex) {
                        statusLabel.setText("Image error: " + ex.getMessage());
                        statusLabel.setForeground(UITheme.DANGER_TEXT);
                    }
                }
            }.execute();
        }
    }
}
