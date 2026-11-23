package com.onlineexam.ui.student;

import com.onlineexam.dao.AchievementService;
import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.UserDAO;
import com.onlineexam.data.ExamConfig;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuthComponents;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.PasswordStrengthMeter;
import com.onlineexam.ui.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Student profile management panel:
 * - Avatar with initials on a deterministic username-hash gradient
 * - Display name, username, and "Member since" timestamp
 * - Edit full name card with instant UI sync and toast
 * - Change password card with live PBKDF2 strength meter and validation
 * - Personal bests: best % per subject, total exams, average, best rank, longest streak
 */
public class ProfilePanel extends JPanel {

    private final String username;
    private final Runnable onBackAction;

    private JLabel avatarLabel;
    private JLabel displayNameHeaderLabel;
    private JLabel memberSinceLabel;
    private JTextField fullNameField;
    private AvatarCircle avatarCircle;

    private JPasswordField currentPassField;
    private JPasswordField newPassField;
    private JPasswordField confirmPassField;
    private PasswordStrengthMeter passMeter;
    private AuthComponents.InlineValidationLabel passValidationLabel;
    private UITheme.ModernButton updatePassBtn;

    // Personal bests labels
    private JLabel totalExamsVal;
    private JLabel avgScoreVal;
    private JLabel bestRankVal;
    private JLabel streakVal;
    private JLabel longestStreakVal;
    private JLabel badgesEarnedVal;
    private JPanel subjectBestsPanel;

    public ProfilePanel(Runnable onBackAction) {
        this.username = ExamStore.getCurrentUserId();
        this.onBackAction = onBackAction;

        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(4, 8, 16, 8));

        // Top Navigation Bar (Back button + title)
        JPanel navBar = new JPanel(new BorderLayout());
        navBar.setOpaque(false);

        if (onBackAction != null) {
            UITheme.ModernButton backBtn = UITheme.secondaryButton("\u2190 Back to Dashboard");
            backBtn.setFont(UITheme.font(Font.BOLD, 12.5f));
            backBtn.setPreferredSize(new Dimension(160, 36));
            backBtn.addActionListener(e -> onBackAction.run());
            navBar.add(backBtn, BorderLayout.WEST);
        }

        JLabel pageTitle = new JLabel("My Profile & Account Settings");
        pageTitle.setFont(UITheme.font(Font.BOLD, 20f));
        pageTitle.setForeground(UITheme.TEXT_PRIMARY);
        navBar.add(pageTitle, onBackAction != null ? BorderLayout.CENTER : BorderLayout.WEST);
        if (onBackAction != null) {
            pageTitle.setBorder(new EmptyBorder(0, 14, 0, 0));
        }

        // Header Profile Glass Card
        GlassPanel headerCard = buildHeaderCard();

        // 2-Column Content Grid
        JPanel grid = new JPanel(new GridLayout(1, 2, 16, 0));
        grid.setOpaque(false);

        JPanel leftCol = new JPanel(new BorderLayout(0, 16));
        leftCol.setOpaque(false);
        leftCol.add(buildEditNameCard(), BorderLayout.NORTH);
        leftCol.add(buildPersonalBestsCard(), BorderLayout.CENTER);

        JPanel rightCol = new JPanel(new BorderLayout());
        rightCol.setOpaque(false);
        rightCol.add(buildChangePasswordCard(), BorderLayout.CENTER);

        grid.add(leftCol);
        grid.add(rightCol);

        JPanel centerWrap = new JPanel(new BorderLayout(0, 16));
        centerWrap.setOpaque(false);
        centerWrap.add(headerCard, BorderLayout.NORTH);
        centerWrap.add(grid, BorderLayout.CENTER);

        JScrollPane scrollPane = UITheme.styleScrollPane(centerWrap);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(navBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        loadProfileData();
    }

    private GlassPanel buildHeaderCard() {
        GlassPanel card = new GlassPanel(16);
        card.setShadowColor(UITheme.PRIMARY);
        card.setLayout(new BorderLayout(18, 0));
        card.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        left.setOpaque(false);

        avatarCircle = new AvatarCircle(username, ExamStore.getCurrentDisplayName());
        avatarCircle.setPreferredSize(new Dimension(68, 68));

        JPanel textStack = new JPanel();
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.setOpaque(false);

        displayNameHeaderLabel = new JLabel(ExamStore.getCurrentDisplayName());
        displayNameHeaderLabel.setFont(UITheme.font(Font.BOLD, 22f));
        displayNameHeaderLabel.setForeground(UITheme.TEXT_PRIMARY);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        badgeRow.setOpaque(false);
        badgeRow.add(UITheme.createBadge("@" + username, UITheme.PRIMARY_SOFT, UITheme.PRIMARY));
        badgeRow.add(UITheme.createBadge("Student Account", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT));

        memberSinceLabel = new JLabel("Member since: Loading...");
        memberSinceLabel.setFont(UITheme.font(Font.PLAIN, 12f));
        memberSinceLabel.setForeground(UITheme.TEXT_MUTED);
        memberSinceLabel.setBorder(new EmptyBorder(2, 2, 0, 0));

        textStack.add(displayNameHeaderLabel);
        textStack.add(badgeRow);
        textStack.add(memberSinceLabel);

        left.add(avatarCircle);
        left.add(textStack);
        card.add(left, BorderLayout.WEST);

        return card;
    }

    private DashboardComponents.BentoCard buildEditNameCard() {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(UITheme.PRIMARY);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JLabel title = new JLabel("Personal Information");
        title.setFont(UITheme.font(Font.BOLD, 15f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        head.add(title, BorderLayout.WEST);
        head.add(new UITheme.VectorIcon(UITheme.IconType.USER, UITheme.PRIMARY, UITheme.PRIMARY_SOFT, 26), BorderLayout.EAST);

        JLabel sub = new JLabel("Your full name will appear on official exam transcripts and certificates.");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);

        JPanel form = new JPanel(new BorderLayout(10, 0));
        form.setOpaque(false);

        fullNameField = new JTextField();
        fullNameField.setFont(UITheme.font(Font.PLAIN, 13.5f));
        fullNameField.setPreferredSize(new Dimension(220, 38));
        fullNameField.setText(ExamStore.getCurrentUserFullName());

        UITheme.ModernButton saveBtn = UITheme.gradientPrimaryButton("Save Name");
        saveBtn.setFont(UITheme.font(Font.BOLD, 12.5f));
        saveBtn.setPreferredSize(new Dimension(110, 38));
        saveBtn.addActionListener(e -> saveFullName());

        form.add(fullNameField, BorderLayout.CENTER);
        form.add(saveBtn, BorderLayout.EAST);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(sub);
        stack.add(Box.createVerticalStrut(10));
        stack.add(form);

        card.add(head, BorderLayout.NORTH);
        card.add(stack, BorderLayout.CENTER);
        return card;
    }

    private DashboardComponents.BentoCard buildChangePasswordCard() {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(UITheme.VIOLET);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 20, 18, 20));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JLabel title = new JLabel("Change Password");
        title.setFont(UITheme.font(Font.BOLD, 15f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        head.add(title, BorderLayout.WEST);
        head.add(new UITheme.VectorIcon(UITheme.IconType.CHECKLIST, UITheme.VIOLET, UITheme.VIOLET_SOFT, 26), BorderLayout.EAST);

        JLabel sub = new JLabel("Passwords are automatically upgraded to salted PBKDF2 with 120,000 iterations.");
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setOpaque(false);

        currentPassField = createPasswordField();
        newPassField = createPasswordField();
        confirmPassField = createPasswordField();

        passMeter = new PasswordStrengthMeter();

        passValidationLabel = new AuthComponents.InlineValidationLabel();

        newPassField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String np = new String(newPassField.getPassword());
                passMeter.setPassword(np);
                validateFieldsLive();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        confirmPassField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() { validateFieldsLive(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        updatePassBtn = UITheme.gradientPrimaryButton("Update Password");
        updatePassBtn.setFont(UITheme.font(Font.BOLD, 13f));
        updatePassBtn.setPreferredSize(new Dimension(160, 40));
        updatePassBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        updatePassBtn.addActionListener(e -> executePasswordChange());

        fields.add(createFieldRow("Current Password", currentPassField));
        fields.add(Box.createVerticalStrut(10));
        fields.add(createFieldRow("New Password (min 8 characters)", newPassField));
        fields.add(Box.createVerticalStrut(4));
        fields.add(passMeter);
        fields.add(Box.createVerticalStrut(8));
        fields.add(createFieldRow("Confirm New Password", confirmPassField));
        fields.add(Box.createVerticalStrut(8));
        fields.add(passValidationLabel);
        fields.add(Box.createVerticalStrut(10));
        fields.add(updatePassBtn);

        JPanel contentStack = new JPanel(new BorderLayout(0, 10));
        contentStack.setOpaque(false);
        contentStack.add(sub, BorderLayout.NORTH);
        contentStack.add(fields, BorderLayout.CENTER);

        card.add(head, BorderLayout.NORTH);
        card.add(contentStack, BorderLayout.CENTER);
        return card;
    }

    private DashboardComponents.BentoCard buildPersonalBestsCard() {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(16);
        card.setShadowColor(UITheme.SUCCESS);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        JLabel title = new JLabel("Personal Bests & Records");
        title.setFont(UITheme.font(Font.BOLD, 15f));
        title.setForeground(UITheme.TEXT_PRIMARY);
        head.add(title, BorderLayout.WEST);
        head.add(new UITheme.VectorIcon(UITheme.IconType.AWARD, UITheme.SUCCESS, UITheme.SUCCESS_SOFT, 26), BorderLayout.EAST);

        // 5 KPI Stats (Total Exams, Average %, Current Streak, Longest Streak, Badges)
        JPanel kpiGrid = new JPanel(new GridLayout(1, 5, 8, 0));
        kpiGrid.setOpaque(false);

        totalExamsVal = new JLabel("0", SwingConstants.CENTER);
        avgScoreVal = new JLabel("0.0%", SwingConstants.CENTER);
        streakVal = new JLabel("0 Days", SwingConstants.CENTER);
        longestStreakVal = new JLabel("0 Days", SwingConstants.CENTER);
        badgesEarnedVal = new JLabel("0 / 11", SwingConstants.CENTER);

        kpiGrid.add(createMiniKpi("Total Exams", totalExamsVal, UITheme.PRIMARY, UITheme.PRIMARY_SOFT));
        kpiGrid.add(createMiniKpi("Average %", avgScoreVal, UITheme.SKY, UITheme.SKY_SOFT));
        kpiGrid.add(createMiniKpi("Current Streak", streakVal, UITheme.WARNING_TEXT, UITheme.WARNING_SOFT));
        kpiGrid.add(createMiniKpi("Best Streak", longestStreakVal, UITheme.GOLD, UITheme.GOLD_SOFT));
        kpiGrid.add(createMiniKpi("Badges", badgesEarnedVal, UITheme.VIOLET, UITheme.VIOLET_SOFT));

        // Subject breakdown
        subjectBestsPanel = new JPanel();
        subjectBestsPanel.setLayout(new BoxLayout(subjectBestsPanel, BoxLayout.Y_AXIS));
        subjectBestsPanel.setOpaque(false);

        JPanel stack = new JPanel(new BorderLayout(0, 12));
        stack.setOpaque(false);
        stack.add(kpiGrid, BorderLayout.NORTH);
        stack.add(subjectBestsPanel, BorderLayout.CENTER);

        card.add(head, BorderLayout.NORTH);
        card.add(stack, BorderLayout.CENTER);
        return card;
    }

    private JPanel createMiniKpi(String label, JLabel val, Color fg, Color bg) {
        UITheme.RoundedPanel p = new UITheme.RoundedPanel(10, bg, UITheme.GLASS_SLATE_BORDER);
        p.setLayout(new BorderLayout(0, 2));
        p.setBorder(new EmptyBorder(8, 6, 8, 6));

        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(UITheme.font(Font.BOLD, 10.5f));
        l.setForeground(fg);

        val.setFont(UITheme.font(Font.BOLD, 16f));
        val.setForeground(fg);

        p.add(l, BorderLayout.NORTH);
        p.add(val, BorderLayout.CENTER);
        return p;
    }

    private JPasswordField createPasswordField() {
        JPasswordField pf = new JPasswordField();
        pf.setFont(UITheme.font(Font.PLAIN, 13.5f));
        pf.setPreferredSize(new Dimension(300, 36));
        pf.putClientProperty("JPasswordField.showRevealButton", true);
        return pf;
    }

    private JPanel createFieldRow(String labelText, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UITheme.font(Font.BOLD, 12f));
        lbl.setForeground(UITheme.TEXT_SECONDARY);
        row.add(lbl, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void validateFieldsLive() {
        String np = new String(newPassField.getPassword());
        String cp = new String(confirmPassField.getPassword());

        if (!np.isEmpty() && np.length() < 8) {
            passValidationLabel.showError("New password must be at least 8 characters.");
        } else if (!cp.isEmpty() && !np.equals(cp)) {
            passValidationLabel.showError("Passwords do not match yet.");
        } else {
            passValidationLabel.clear();
        }
    }

    private void saveFullName() {
        String newName = fullNameField.getText().trim();
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return UserDAO.updateFullName(username, newName);
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        ExamStore.setCurrentUserFullName(newName);
                        displayNameHeaderLabel.setText(ExamStore.getCurrentDisplayName());
                        avatarCircle.updateInitials(ExamStore.getCurrentDisplayName());
                        UITheme.showToast(ProfilePanel.this, "Profile Updated", "Your full name has been saved.", UITheme.ToastType.SUCCESS);
                    } else {
                        UITheme.showToast(ProfilePanel.this, "Error", "Failed to update full name.", UITheme.ToastType.ERROR);
                    }
                } catch (Exception ex) {
                    UITheme.showToast(ProfilePanel.this, "Error", ex.getMessage(), UITheme.ToastType.ERROR);
                }
            }
        }.execute();
    }

    private void executePasswordChange() {
        String oldP = new String(currentPassField.getPassword()).trim();
        String newP = new String(newPassField.getPassword()).trim();
        String confP = new String(confirmPassField.getPassword()).trim();

        if (oldP.isEmpty()) {
            passValidationLabel.showError("Please enter your current password.");
            return;
        }
        if (newP.length() < 8) {
            passValidationLabel.showError("New password must be at least 8 characters.");
            return;
        }
        if (!newP.equals(confP)) {
            passValidationLabel.showError("New password and confirmation do not match.");
            return;
        }

        updatePassBtn.setEnabled(false);
        updatePassBtn.setText("Updating...");

        new SwingWorker<UserDAO.ChangePasswordResult, Void>() {
            @Override
            protected UserDAO.ChangePasswordResult doInBackground() {
                return UserDAO.changePassword(username, oldP, newP);
            }

            @Override
            protected void done() {
                updatePassBtn.setEnabled(true);
                updatePassBtn.setText("Update Password");
                try {
                    UserDAO.ChangePasswordResult res = get();
                    if (res != null && res.success) {
                        currentPassField.setText("");
                        newPassField.setText("");
                        confirmPassField.setText("");
                        passMeter.setPassword("");
                        passValidationLabel.showSuccess("Password changed successfully! Stored in PBKDF2.");
                        UITheme.showToast(ProfilePanel.this, "Security Updated", "Your password has been securely updated.", UITheme.ToastType.SUCCESS);
                    } else {
                        String msg = res != null ? res.message : "Failed to change password.";
                        passValidationLabel.showError(msg);
                    }
                } catch (Exception ex) {
                    passValidationLabel.showError("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void loadProfileData() {
        new SwingWorker<Void, Void>() {
            private UserDAO.UserProfile profile;
            private List<AttemptDAO.AttemptDetail> attempts;
            private AttemptDAO.StudentPerformanceSummary summary;
            private AchievementService.StreakInfo streak;
            private int totalBadges = 0;

            @Override
            protected Void doInBackground() {
                profile = UserDAO.getProfile(username);
                attempts = AttemptDAO.getAttemptDetails(null, username, false);
                summary = AttemptDAO.getStudentPerformanceSummary(username);
                streak = AchievementService.getStreakInfo(username);
                totalBadges = AchievementService.getTotalEarnedCount(username);
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;

                if (profile != null) {
                    if (profile.fullName != null && !profile.fullName.isEmpty()) {
                        fullNameField.setText(profile.fullName);
                        displayNameHeaderLabel.setText(profile.fullName);
                        avatarCircle.updateInitials(profile.fullName);
                    }
                    if (profile.createdAt != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH);
                        memberSinceLabel.setText("Member since: " + sdf.format(profile.createdAt));
                    } else {
                        memberSinceLabel.setText("Member since: November 2026");
                    }
                }

                // Render Personal Bests & Records
                if (summary != null && summary.hasAttempts) {
                    totalExamsVal.setText(String.valueOf(summary.totalAttempts));
                    avgScoreVal.setText(String.format("%.1f%%", summary.averagePercentage));
                }

                // Render real streak and badge counts (Task 10)
                if (streak != null) {
                    streakVal.setText(streak.currentStreak + (streak.currentStreak == 1 ? " Day" : " Days"));
                    longestStreakVal.setText(streak.longestStreak + (streak.longestStreak == 1 ? " Day" : " Days"));
                } else {
                    streakVal.setText("0 Days");
                    longestStreakVal.setText("0 Days");
                }
                badgesEarnedVal.setText(totalBadges + " / 11");

                // Calculate subject personal best percentages
                Map<String, Double> subjectBests = new HashMap<>();
                if (attempts != null) {
                    for (AttemptDAO.AttemptDetail a : attempts) {
                        String sName = a.subjectName != null ? a.subjectName : "Subject";
                        Double prev = subjectBests.get(sName);
                        if (prev == null || a.percentage > prev) {
                            subjectBests.put(sName, a.percentage);
                        }
                    }
                }

                subjectBestsPanel.removeAll();
                if (subjectBests.isEmpty()) {
                    JLabel noExams = new JLabel("No exams completed yet. Take your first exam to establish personal records!");
                    noExams.setFont(UITheme.font(Font.PLAIN, 12f));
                    noExams.setForeground(UITheme.TEXT_MUTED);
                    subjectBestsPanel.add(noExams);
                } else {
                    JLabel sbTitle = new JLabel("Subject Bests (Highest Score % Achieved):");
                    sbTitle.setFont(UITheme.font(Font.BOLD, 12f));
                    sbTitle.setForeground(UITheme.TEXT_SECONDARY);
                    sbTitle.setBorder(new EmptyBorder(6, 0, 8, 0));
                    subjectBestsPanel.add(sbTitle);

                    Color[] barColors = {UITheme.PRIMARY, UITheme.VIOLET, UITheme.SKY, UITheme.PINK};
                    int cIdx = 0;
                    for (Map.Entry<String, Double> entry : subjectBests.entrySet()) {
                        Color col = barColors[cIdx % barColors.length];
                        JPanel barRow = UITheme.createProgressBarRow(
                                entry.getKey(), (int) Math.round(entry.getValue()), 100, col);
                        barRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                        subjectBestsPanel.add(barRow);
                        subjectBestsPanel.add(Box.createVerticalStrut(6));
                        cIdx++;
                    }
                }
                subjectBestsPanel.revalidate();
                subjectBestsPanel.repaint();
            }
        }.execute();
    }

    /**
     * Circular avatar with initials drawn on a deterministic gradient derived from username hash.
     */
    private static class AvatarCircle extends JComponent {
        private final String username;
        private String initials;
        private final Color gradColor1;
        private final Color gradColor2;

        AvatarCircle(String username, String displayName) {
            this.username = username != null ? username : "";
            this.initials = computeInitials(displayName);

            int h = Math.abs(this.username.hashCode());
            float h1 = (h % 360) / 360.0f;
            float h2 = ((h + 45) % 360) / 360.0f;
            this.gradColor1 = Color.getHSBColor(h1, 0.65f, 0.88f);
            this.gradColor2 = Color.getHSBColor(h2, 0.75f, 0.72f);
        }

        void updateInitials(String displayName) {
            this.initials = computeInitials(displayName);
            repaint();
        }

        private String computeInitials(String name) {
            if (name == null || name.trim().isEmpty()) {
                name = username;
            }
            name = name.trim();
            String[] parts = name.split("\\s+");
            if (parts.length >= 2) {
                return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
            } else if (name.length() >= 2) {
                return name.substring(0, 2).toUpperCase();
            } else if (name.length() == 1) {
                return name.toUpperCase();
            }
            return "U";
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h) - 4;
            int x = (w - size) / 2;
            int y = (h - size) / 2;

            Shape circle = new Ellipse2D.Float(x, y, size, size);

            // Subtle drop shadow
            UITheme.paintColoredShadow(g2, x, y, size, size, size, gradColor1, 3, 2);

            // Vibrant deterministic gradient
            GradientPaint gp = new GradientPaint(x, y, gradColor1, x + size, y + size, gradColor2);
            g2.setPaint(gp);
            g2.fill(circle);

            // Clean white border ring
            g2.setColor(new Color(255, 255, 255, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(circle);

            // Initials text
            g2.setColor(Color.WHITE);
            g2.setFont(UITheme.font(Font.BOLD, size * 0.40f));
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (size - fm.stringWidth(initials)) / 2;
            int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(initials, tx, ty);

            g2.dispose();
        }
    }
}
