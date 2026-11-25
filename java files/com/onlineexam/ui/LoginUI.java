package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

import com.onlineexam.dao.SessionDAO;
import com.onlineexam.dao.SessionHeartbeat;
import com.onlineexam.data.ExamStore;
import com.onlineexam.dao.UserDAO;
import com.onlineexam.ui.admin.AdminDashboard;
import com.onlineexam.ui.student.StudentDashboard;

public class LoginUI extends JFrame {

    private String selectedRole = "student";
    private boolean transitioning = false;

    private static class LoginTaskResult {
        final boolean success;
        final String token;
        final String errorMessage;

        LoginTaskResult(boolean success, String token, String errorMessage) {
            this.success = success;
            this.token = token;
            this.errorMessage = errorMessage;
        }
    }

    public LoginUI() {
        setTitle("Online Examination Management System - Sign In");
        setSize(940, 600);
        setMinimumSize(new Dimension(840, 550));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Full-window animated Pastel Aurora Light background
        AuroraBackground root = new AuroraBackground(new GridBagLayout());
        root.setBorder(new EmptyBorder(26, 36, 26, 36));

        // Centered horizontal composition: Floating Light Brand Info (Left) + Frosted Glass Login Card (Right, ~420px)
        JPanel stage = new JPanel(new GridBagLayout());
        stage.setOpaque(false);

        // ================= FLOATING LIGHT BRAND & FEATURE LIST =================
        JPanel brandCol = new JPanel();
        brandCol.setLayout(new BoxLayout(brandCol, BoxLayout.Y_AXIS));
        brandCol.setOpaque(false);
        brandCol.setPreferredSize(new Dimension(350, 440));
        brandCol.setBorder(new EmptyBorder(12, 0, 12, 16));

        // Brand Crest (Indigo -> Violet gradient tile + crisp white cap)
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

        JLabel eyebrow = new JLabel("ONLINE EXAMINATION PORTAL");
        eyebrow.setFont(UITheme.font(Font.BOLD, 10.5f));
        eyebrow.setForeground(UITheme.PRIMARY);

        JLabel sysTitle = new JLabel("Online Exam System");
        sysTitle.setFont(UITheme.font(Font.BOLD, 22f));
        sysTitle.setForeground(UITheme.TEXT_PRIMARY);

        titleStack.add(eyebrow);
        titleStack.add(Box.createVerticalStrut(2));
        titleStack.add(sysTitle);

        crestRow.add(crestBadge);
        crestRow.add(titleStack);

        JLabel tagline = new JLabel("<html><div style='line-height:1.5;'>Modern computer-based examination workspace with randomized assessments, live proctoring, and instant analytics.</div></html>");
        tagline.setFont(UITheme.font(Font.PLAIN, 13f));
        tagline.setForeground(UITheme.TEXT_SECONDARY);
        tagline.setBorder(new EmptyBorder(18, 2, 20, 4));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Staggered fade-in feature rows (dark text on light background, indigo check icons)
        String[] featureTexts = {
                "Randomized 20-question bank & option shuffling",
                "Timed exam with 5-minute alert & auto-submit",
                "Instant grading, class ranking & score certificates",
                "Atomic MySQL storage with full answer review"
        };
        AuthComponents.FadePanel[] featureRows = new AuthComponents.FadePanel[featureTexts.length];
        JPanel featureStack = new JPanel();
        featureStack.setLayout(new BoxLayout(featureStack, BoxLayout.Y_AXIS));
        featureStack.setOpaque(false);
        featureStack.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < featureTexts.length; i++) {
            AuthComponents.FadePanel row = new AuthComponents.FadePanel(new BorderLayout(10, 0));
            row.setBorder(new EmptyBorder(6, 2, 6, 4));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JComponent checkBadge = new UITheme.VectorIcon(
                    UITheme.IconType.CHECK, UITheme.PRIMARY, UITheme.PRIMARY_SOFT, 22);
            JLabel txt = new JLabel(featureTexts[i]);
            txt.setFont(UITheme.font(Font.PLAIN, 12.5f));
            txt.setForeground(UITheme.TEXT_PRIMARY);

            row.add(checkBadge, BorderLayout.WEST);
            row.add(txt, BorderLayout.CENTER);
            featureRows[i] = row;
            featureStack.add(row);
        }

        // Light Glass Live MySQL Status Pill
        int loadedQ = ExamStore.getQuestionCount();
        GlassPanel statusPill = new GlassPanel(12);
        statusPill.setSpotlightEnabled(false);
        statusPill.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
        statusPill.setBorder(new EmptyBorder(4, 10, 6, 12));
        statusPill.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusPill.setMaximumSize(new Dimension(280, 38));

        JComponent greenDot = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(Anim.withAlpha(UITheme.SUCCESS, 55));
                g2.fillOval(0, 1, 12, 12);
                g2.setColor(UITheme.SUCCESS);
                g2.fillOval(2, 3, 8, 8);
                g2.dispose();
            }
        };
        greenDot.setPreferredSize(new Dimension(12, 14));

        JLabel statusTxt = new JLabel(loadedQ > 0
                ? ("MySQL Online  \u2022  " + loadedQ + " Questions Ready")
                : "MySQL Database Connected");
        statusTxt.setFont(UITheme.font(Font.BOLD, 11.5f));
        statusTxt.setForeground(UITheme.TEXT_SECONDARY);
        statusPill.add(greenDot);
        statusPill.add(statusTxt);

        brandCol.add(crestRow);
        brandCol.add(tagline);
        brandCol.add(featureStack);
        brandCol.add(Box.createVerticalStrut(22));
        brandCol.add(statusPill);

        // ================= CENTERED FROSTED-WHITE GLASS LOGIN CARD (~420px) =================
        GlassPanel card = new GlassPanel(16);
        card.setShadowColor(UITheme.PRIMARY);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(26, 32, 26, 32));
        card.setPreferredSize(new Dimension(420, 495));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel heading = new JLabel("Sign in");
        heading.setFont(UITheme.font(Font.BOLD, 23f));
        heading.setForeground(UITheme.TEXT_PRIMARY);
        gbc.insets = new Insets(0, 0, 4, 0);
        card.add(heading, gbc);

        JLabel sub = new JLabel("Choose your role and enter your credentials");
        sub.setFont(UITheme.font(Font.PLAIN, 12.5f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 18, 0);
        card.add(sub, gbc);

        // Sliding Pill Role Toggle (easeInOutCubic, 250ms)
        AuthComponents.SlidingRoleToggle roleToggle = new AuthComponents.SlidingRoleToggle("Student", "Administrator");
        roleToggle.setOnRoleChanged(role -> selectedRole = role);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 16, 0);
        card.add(roleToggle, gbc);

        // Floating-label inputs with L->R gradient underline & Java2D eye toggle
        AuthComponents.FloatingInputField userField = new AuthComponents.FloatingInputField("User ID", false);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        card.add(userField, gbc);

        AuthComponents.FloatingInputField passField = new AuthComponents.FloatingInputField("Password", true);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        card.add(passField, gbc);

        // Inline fading error feedback
        AuthComponents.InlineValidationLabel errorLabel = new AuthComponents.InlineValidationLabel();
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        card.add(errorLabel, gbc);

        userField.addDocumentChangeListener(errorLabel::clear);
        passField.addDocumentChangeListener(errorLabel::clear);

        // Primary Indigo -> Violet Gradient Button with hover brighten, 0.97 press scale, cursor ripple
        AuthComponents.RippleGradientButton loginBtn = new AuthComponents.RippleGradientButton("Sign In");
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        card.add(loginBtn, gbc);

        JButton signupBtn = UITheme.createLinkButton("New student?", "Create an account");
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        card.add(signupBtn, gbc);

        // Task 11: Public certificate verification link (no login required)
        JButton verifyCertBtn = UITheme.createLinkButton("", "Verify a certificate \u2192");
        verifyCertBtn.setFont(UITheme.font(Font.PLAIN, 12f));
        verifyCertBtn.addActionListener(e -> new com.onlineexam.ui.admin.VerifyCertificateUI());
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        card.add(verifyCertBtn, gbc);

        // Compact Light Quick-Fill Demo Bar (convenience for testing; zero dark mode controls)
        JPanel demoBar = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(new Color(248, 250, 252));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(UITheme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 10, 10));
                g2.dispose();
            }
        };
        demoBar.setOpaque(false);
        demoBar.setBorder(new EmptyBorder(6, 12, 6, 8));

        JLabel demoLbl = new JLabel("Quick Fill Demo");
        demoLbl.setFont(UITheme.font(Font.BOLD, 11f));
        demoLbl.setForeground(UITheme.TEXT_MUTED);

        JPanel demoBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        demoBtns.setOpaque(false);

        UITheme.ModernButton fillStudent = UITheme.secondaryButton("Student");
        fillStudent.setFont(UITheme.font(Font.BOLD, 11f));
        fillStudent.setBorder(new EmptyBorder(3, 10, 3, 10));
        fillStudent.addActionListener(e -> {
            selectedRole = "student";
            roleToggle.setRole("student", false);
            userField.setInputText("student1");
            passField.setInputText("pass123");
            errorLabel.clear();
        });

        UITheme.ModernButton fillAdmin = UITheme.secondaryButton("Admin");
        fillAdmin.setFont(UITheme.font(Font.BOLD, 11f));
        fillAdmin.setBorder(new EmptyBorder(3, 10, 3, 10));
        fillAdmin.addActionListener(e -> {
            selectedRole = "admin";
            roleToggle.setRole("admin", false);
            userField.setInputText("admin");
            passField.setInputText("admin123");
            errorLabel.clear();
        });

        demoBtns.add(fillStudent);
        demoBtns.add(fillAdmin);
        demoBar.add(demoLbl, BorderLayout.WEST);
        demoBar.add(demoBtns, BorderLayout.EAST);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(demoBar, gbc);

        // ================= EXACT EXISTING LOGIN LOGIC & VALIDATION =================
        Runnable doLogin = () -> {
            if (transitioning) return;

            String role = selectedRole;
            String username = userField.getInputText().trim();
            String password = passField.getInputText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                if (username.isEmpty()) userField.flashError();
                if (password.isEmpty()) passField.flashError();
                errorLabel.showError("Please enter both User ID and Password.");
                AuthComponents.shakeCard(card);
                return;
            }

            loginBtn.setEnabled(false);
            loginBtn.setText("Signing in...");
            final String machineInfo = SessionDAO.buildLocalMachineInfo();

            new SwingWorker<LoginTaskResult, Void>() {
                @Override
                protected LoginTaskResult doInBackground() {
                    boolean valid = UserDAO.validateUser(username, password, role);
                    if (!valid) {
                        return new LoginTaskResult(false, null, "Invalid credentials or role mismatch.");
                    }
                    if ("admin".equals(role)) {
                        return new LoginTaskResult(true, null, null);
                    }
                    // Students only: enforce single-session & in-exam device lock
                    SessionDAO.LoginResult sRes = SessionDAO.tryLogin(username, machineInfo);
                    if (sRes == null || !sRes.isOk()) {
                        String msg = (sRes != null && sRes.message != null)
                                ? sRes.message : "Sign in failed. Please check your connection.";
                        return new LoginTaskResult(false, null, msg);
                    }
                    return new LoginTaskResult(true, sRes.token, null);
                }

                @Override
                protected void done() {
                    try {
                        LoginTaskResult res = get();
                        if (res == null || !res.success) {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("Sign In");
                            userField.flashError();
                            passField.flashError();
                            errorLabel.showError(res != null && res.errorMessage != null
                                    ? res.errorMessage : "Sign in failed.");
                            AuthComponents.shakeCard(card);
                            return;
                        }

                        transitioning = true;
                        ExamStore.startSession(username, role);

                        if (!"admin".equals(role)) {
                            ExamStore.setSessionToken(res.token);
                            SessionHeartbeat.start(username, res.token);
                            AuthComponents.scaleFadeOut(card, () -> {
                                dispose();
                                new StudentDashboard();
                            });
                        } else {
                            AuthComponents.scaleFadeOut(card, () -> {
                                dispose();
                                new AdminDashboard();
                            });
                        }
                    } catch (Exception ex) {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Sign In");
                        errorLabel.showError("Connection error: " + ex.getMessage());
                        AuthComponents.shakeCard(card);
                    }
                }
            }.execute();
        };

        loginBtn.addActionListener(e -> doLogin.run());
        userField.addActionListener(e -> doLogin.run());
        passField.addActionListener(e -> doLogin.run());
        signupBtn.addActionListener(e -> {
            if (transitioning) return;
            transitioning = true;
            AuthComponents.scaleFadeOut(card, () -> {
                dispose();
                new SignupUI();
            });
        });

        // Assemble stage
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

        // Smooth entrance animations on EDT
        SwingUtilities.invokeLater(() -> {
            Anim.slideIn(card, Anim.Direction.UP, 420, 22);
            AuthComponents.staggerFadeIn(featureRows, 100, 110);
        });
    }
}
