package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

import com.onlineexam.dao.UserDAO;

public class SignupUI extends JFrame {

    private boolean transitioning = false;

    public SignupUI() {
        setTitle("Student Registration - Online Examination System");
        setSize(940, 640);
        setMinimumSize(new Dimension(840, 580));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Full-window animated Pastel Aurora Light background
        AuroraBackground root = new AuroraBackground(new GridBagLayout());
        root.setBorder(new EmptyBorder(26, 36, 26, 36));

        JPanel stage = new JPanel(new GridBagLayout());
        stage.setOpaque(false);

        // ================= FLOATING LIGHT BRAND & ENROLLMENT HIGHLIGHTS =================
        JPanel brandCol = new JPanel();
        brandCol.setLayout(new BoxLayout(brandCol, BoxLayout.Y_AXIS));
        brandCol.setOpaque(false);
        brandCol.setPreferredSize(new Dimension(330, 420));
        brandCol.setBorder(new EmptyBorder(12, 0, 12, 16));

        JPanel crestRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        crestRow.setOpaque(false);
        crestRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComponent crestBadge = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                UITheme.paintColoredShadow(g2, 2, 2, 40, 40, 12, UITheme.PRIMARY, 3, 2);
                g2.setPaint(UITheme.createPrimaryGradient(2, 2, 42, 42));
                g2.fill(new RoundRectangle2D.Float(2, 2, 40, 40, 12, 12));
                Icon cap = UITheme.createSwingIcon(UITheme.IconType.CAP, Color.WHITE, 22);
                cap.paintIcon(this, g2, 11, 11);
                g2.dispose();
            }
        };
        crestBadge.setPreferredSize(new Dimension(46, 48));

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);

        JLabel eyebrow = new JLabel("STUDENT ENROLLMENT");
        eyebrow.setFont(UITheme.font(Font.BOLD, 10.5f));
        eyebrow.setForeground(UITheme.PRIMARY);

        JLabel sysTitle = new JLabel("Join the Portal");
        sysTitle.setFont(UITheme.font(Font.BOLD, 22f));
        sysTitle.setForeground(UITheme.TEXT_PRIMARY);

        titleStack.add(eyebrow);
        titleStack.add(Box.createVerticalStrut(2));
        titleStack.add(sysTitle);

        crestRow.add(crestBadge);
        crestRow.add(titleStack);

        JLabel tagline = new JLabel("<html><div style='line-height:1.5;'>Create your personal student account to access timed MCQ assessments, track score progression, and review past answers.</div></html>");
        tagline.setFont(UITheme.font(Font.PLAIN, 13f));
        tagline.setForeground(UITheme.TEXT_SECONDARY);
        tagline.setBorder(new EmptyBorder(18, 2, 20, 4));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] perks = {
                "Instant access to Java, DBMS, SQL & Aptitude exams",
                "SHA-256 encrypted account credentials in MySQL",
                "Personal score trends, leaderboard rank & PDF reports"
        };
        AuthComponents.FadePanel[] perkRows = new AuthComponents.FadePanel[perks.length];
        JPanel perkStack = new JPanel();
        perkStack.setLayout(new BoxLayout(perkStack, BoxLayout.Y_AXIS));
        perkStack.setOpaque(false);
        perkStack.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < perks.length; i++) {
            AuthComponents.FadePanel row = new AuthComponents.FadePanel(new BorderLayout(10, 0));
            row.setBorder(new EmptyBorder(6, 2, 6, 4));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JComponent checkBadge = new UITheme.VectorIcon(
                    UITheme.IconType.CHECK, UITheme.PRIMARY, UITheme.PRIMARY_SOFT, 22);
            JLabel txt = new JLabel(perks[i]);
            txt.setFont(UITheme.font(Font.PLAIN, 12.5f));
            txt.setForeground(UITheme.TEXT_PRIMARY);

            row.add(checkBadge, BorderLayout.WEST);
            row.add(txt, BorderLayout.CENTER);
            perkRows[i] = row;
            perkStack.add(row);
        }

        brandCol.add(crestRow);
        brandCol.add(tagline);
        brandCol.add(perkStack);

        // ================= FROSTED-WHITE GLASS REGISTRATION CARD (~420px) =================
        GlassPanel card = new GlassPanel(16);
        card.setShadowColor(UITheme.PRIMARY);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(24, 32, 24, 32));
        card.setPreferredSize(new Dimension(420, 545));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel heading = new JLabel("Create Student Account");
        heading.setFont(UITheme.font(Font.BOLD, 21f));
        heading.setForeground(UITheme.TEXT_PRIMARY);
        gbc.insets = new Insets(0, 0, 4, 0);
        card.add(heading, gbc);

        JLabel sub = new JLabel("Join with your full name, user ID, and secure password");
        sub.setFont(UITheme.font(Font.PLAIN, 12.5f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 14, 0);
        card.add(sub, gbc);

        AuthComponents.FloatingInputField nameField = new AuthComponents.FloatingInputField("Full Name (Optional)", false);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        card.add(nameField, gbc);

        AuthComponents.FloatingInputField userField = new AuthComponents.FloatingInputField("Student User ID", false);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        card.add(userField, gbc);

        AuthComponents.FloatingInputField passField = new AuthComponents.FloatingInputField("Password (min 8 characters)", true);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 4, 0);
        card.add(passField, gbc);

        PasswordStrengthMeter passMeter = new PasswordStrengthMeter();
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        card.add(passMeter, gbc);

        AuthComponents.FloatingInputField confirmField = new AuthComponents.FloatingInputField("Confirm Password", true);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 4, 0);
        card.add(confirmField, gbc);

        // Inline fading validation feedback
        AuthComponents.InlineValidationLabel validationLabel = new AuthComponents.InlineValidationLabel();
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        card.add(validationLabel, gbc);

        // Live subtle inline validation for password strength and confirmation
        Runnable liveCheck = () -> {
            String p = passField.getInputText();
            String c = confirmField.getInputText();
            passMeter.setPassword(p);
            if (!p.isEmpty() && p.length() < 8) {
                validationLabel.showError("Password must be at least 8 characters.");
            } else if (!c.isEmpty() && !p.equals(c)) {
                validationLabel.showError("Passwords do not match yet.");
            } else {
                validationLabel.clear();
            }
        };
        userField.addDocumentChangeListener(validationLabel::clear);
        passField.addDocumentChangeListener(liveCheck);
        confirmField.addDocumentChangeListener(liveCheck);

        AuthComponents.RippleGradientButton createBtn = new AuthComponents.RippleGradientButton("Create Account");
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        card.add(createBtn, gbc);

        JButton backBtn = UITheme.createLinkButton("Already registered?", "Sign in instead");
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(backBtn, gbc);

        // ================= EXACT SIGNUP LOGIC WITH FULL NAME & 8-CHAR RULE =================
        Runnable doRegister = () -> {
            if (transitioning) return;

            String fullName = nameField.getInputText().trim();
            String username = userField.getInputText().trim();
            String password = passField.getInputText().trim();
            String confirm = confirmField.getInputText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                if (username.isEmpty()) userField.flashError();
                if (password.isEmpty()) passField.flashError();
                validationLabel.showError("User ID and Password are required.");
                AuthComponents.shakeCard(card);
                return;
            }
            if (password.length() < 8) {
                passField.flashError();
                validationLabel.showError("Password must be at least 8 characters.");
                AuthComponents.shakeCard(card);
                return;
            }
            if (!password.equals(confirm)) {
                confirmField.flashError();
                validationLabel.showError("Passwords do not match.");
                AuthComponents.shakeCard(card);
                return;
            }

            createBtn.setEnabled(false);
            createBtn.setText("Creating account...");

            new SwingWorker<UserDAO.SignupStatus, Void>() {
                @Override
                protected UserDAO.SignupStatus doInBackground() {
                    return UserDAO.registerUser(username, password, "student", fullName);
                }

                @Override
                protected void done() {
                    try {
                        UserDAO.SignupStatus status = get();
                        if (status == UserDAO.SignupStatus.USER_EXISTS) {
                            createBtn.setEnabled(true);
                            createBtn.setText("Create Account \u2192");
                            userField.flashError();
                            validationLabel.showError("That User ID is already taken.");
                            AuthComponents.shakeCard(card);
                            return;
                        }
                        if (status != UserDAO.SignupStatus.SUCCESS) {
                            createBtn.setEnabled(true);
                            createBtn.setText("Create Account \u2192");
                            validationLabel.showError("Database error. Check MySQL connection.");
                            AuthComponents.shakeCard(card);
                            return;
                        }

                        transitioning = true;
                        validationLabel.showSuccess("Account created! Redirecting to Sign In...");

                        Timer delay = new Timer(520, ev -> {
                            ((Timer) ev.getSource()).stop();
                            AuthComponents.scaleFadeOut(card, () -> {
                                dispose();
                                new LoginUI();
                            });
                        });
                        delay.setRepeats(false);
                        delay.start();
                    } catch (Exception ex) {
                        createBtn.setEnabled(true);
                        createBtn.setText("Create Account \u2192");
                        validationLabel.showError("Database error: " + ex.getMessage());
                        AuthComponents.shakeCard(card);
                    }
                }
            }.execute();
        };

        createBtn.addActionListener(e -> doRegister.run());
        confirmField.addActionListener(e -> doRegister.run());
        backBtn.addActionListener(e -> {
            if (transitioning) return;
            transitioning = true;
            AuthComponents.scaleFadeOut(card, () -> {
                dispose();
                new LoginUI();
            });
        });

        GridBagConstraints sg = new GridBagConstraints();
        sg.gridx = 0;
        sg.gridy = 0;
        sg.insets = new Insets(0, 0, 0, 34);
        sg.anchor = GridBagConstraints.CENTER;
        stage.add(brandCol, sg);

        sg.gridx = 1;
        sg.insets = new Insets(0, 0, 0, 0);
        stage.add(card, sg);

        root.add(stage);
        add(root);
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            Anim.slideIn(card, Anim.Direction.UP, 420, 22);
            AuthComponents.staggerFadeIn(perkRows, 100, 110);
        });
    }
}
