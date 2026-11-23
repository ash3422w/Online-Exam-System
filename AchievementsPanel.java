package com.onlineexam.ui.student;

import com.onlineexam.dao.AchievementService;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.DashboardComponents;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Achievements, Badges and Daily Streaks panel for students.
 * Displays current and longest streaks, summary KPIs with count-up animation,
 * and a grid of unlocked and locked badge cards with progress bars and hints.
 */
public class AchievementsPanel extends JPanel {

    private final String userId;
    private final Runnable onBackAction;

    private JLabel currentStreakVal;
    private JLabel longestStreakVal;
    private JLabel badgesUnlockedVal;
    private JLabel badgesPctLabel;

    private JPanel cardsGrid;
    private List<AchievementService.Achievement> loadedAchievements = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL | EARNED | LOCKED

    private JButton allFilterBtn;
    private JButton earnedFilterBtn;
    private JButton lockedFilterBtn;

    public AchievementsPanel(Runnable onBackAction) {
        this.userId = ExamStore.getCurrentUserId();
        this.onBackAction = onBackAction;

        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(6, 10, 16, 10));

        // Top Navigation Bar
        JPanel navBar = new JPanel(new BorderLayout(14, 0));
        navBar.setOpaque(false);

        if (onBackAction != null) {
            UITheme.ModernButton backBtn = UITheme.secondaryButton("\u2190 Back to Dashboard");
            backBtn.setFont(UITheme.font(Font.BOLD, 12.5f));
            backBtn.setPreferredSize(new Dimension(160, 36));
            backBtn.addActionListener(e -> onBackAction.run());
            navBar.add(backBtn, BorderLayout.WEST);
        }

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);

        JLabel pageTitle = new JLabel("Achievements & Badges");
        pageTitle.setFont(UITheme.font(Font.BOLD, 22f));
        pageTitle.setForeground(UITheme.TEXT_PRIMARY);

        JLabel subTitle = new JLabel("Earn badges for milestones, keep daily exam streaks alive, and prove mastery across subjects.");
        subTitle.setFont(UITheme.font(Font.PLAIN, 12.5f));
        subTitle.setForeground(UITheme.TEXT_SECONDARY);
        subTitle.setBorder(new EmptyBorder(2, 0, 0, 0));

        titleStack.add(pageTitle);
        titleStack.add(subTitle);
        navBar.add(titleStack, onBackAction != null ? BorderLayout.CENTER : BorderLayout.WEST);

        // Header KPI Strip
        JPanel headerKpis = buildHeaderKpiStrip();

        // Filter Bar (All / Unlocked / Locked)
        JPanel filterRow = buildFilterBar();

        // Cards Grid Container
        cardsGrid = new JPanel(new GridLayout(0, 3, 14, 14));
        cardsGrid.setOpaque(false);

        JPanel contentStack = new JPanel(new BorderLayout(0, 14));
        contentStack.setOpaque(false);
        contentStack.add(headerKpis, BorderLayout.NORTH);

        JPanel gridWrap = new JPanel(new BorderLayout(0, 10));
        gridWrap.setOpaque(false);
        gridWrap.add(filterRow, BorderLayout.NORTH);
        gridWrap.add(cardsGrid, BorderLayout.CENTER);

        contentStack.add(gridWrap, BorderLayout.CENTER);

        JScrollPane scrollPane = UITheme.styleScrollPane(contentStack);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(navBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        loadDataAsync();
    }

    private JPanel buildHeaderKpiStrip() {
        JPanel strip = new JPanel(new GridLayout(1, 3, 14, 0));
        strip.setOpaque(false);

        // 1. Current Streak
        currentStreakVal = new JLabel("0 Days");
        currentStreakVal.setFont(UITheme.font(Font.BOLD, 22f));
        currentStreakVal.setForeground(UITheme.TEXT_PRIMARY);

        DashboardComponents.BentoCard streakCard = createMiniKpiCard(
                "CURRENT STREAK", currentStreakVal, "Active consecutive days",
                UITheme.WARNING_TEXT, UITheme.WARNING_SOFT, UITheme.IconType.FLAME);

        // 2. Longest Streak
        longestStreakVal = new JLabel("0 Days");
        longestStreakVal.setFont(UITheme.font(Font.BOLD, 22f));
        longestStreakVal.setForeground(UITheme.TEXT_PRIMARY);

        DashboardComponents.BentoCard longestCard = createMiniKpiCard(
                "LONGEST STREAK", longestStreakVal, "All-time personal record",
                UITheme.GOLD, UITheme.GOLD_SOFT, UITheme.IconType.TROPHY);

        // 3. Badges Earned
        badgesUnlockedVal = new JLabel("0 / 11");
        badgesUnlockedVal.setFont(UITheme.font(Font.BOLD, 22f));
        badgesUnlockedVal.setForeground(UITheme.TEXT_PRIMARY);

        badgesPctLabel = new JLabel("0% Completed");
        badgesPctLabel.setFont(UITheme.font(Font.PLAIN, 12f));
        badgesPctLabel.setForeground(UITheme.TEXT_MUTED);

        DashboardComponents.BentoCard badgesCard = createMiniKpiCard(
                "BADGES UNLOCKED", badgesUnlockedVal, "0% Completed",
                UITheme.PRIMARY, UITheme.PRIMARY_SOFT, UITheme.IconType.MEDAL);

        strip.add(streakCard);
        strip.add(longestCard);
        strip.add(badgesCard);

        return strip;
    }

    private DashboardComponents.BentoCard createMiniKpiCard(String title, JLabel valLabel, String subtext,
                                                            Color accent, Color bgAccent, UITheme.IconType iconType) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(14);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        // Circular icon
        JComponent iconBadge = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                int sz = Math.min(getWidth(), getHeight());
                g2.setColor(bgAccent);
                g2.fill(new Ellipse2D.Float(0, 0, sz, sz));
                UITheme.drawIcon(g2, iconType, sz / 2, sz / 2, accent, sz, 0.24f);
                g2.dispose();
            }
        };
        iconBadge.setPreferredSize(new Dimension(46, 46));
        iconBadge.setMinimumSize(new Dimension(46, 46));
        card.add(iconBadge, BorderLayout.WEST);

        JPanel textStack = new JPanel();
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(UITheme.font(Font.BOLD, 10.5f));
        t.setForeground(UITheme.TEXT_MUTED);

        JLabel sub = new JLabel(subtext);
        sub.setFont(UITheme.font(Font.PLAIN, 11.5f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(2, 0, 0, 0));

        textStack.add(t);
        textStack.add(Box.createVerticalStrut(2));
        textStack.add(valLabel);
        textStack.add(sub);

        card.add(textStack, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setOpaque(false);

        allFilterBtn = createFilterButton("All (11)", true, () -> setFilter("ALL"));
        earnedFilterBtn = createFilterButton("Unlocked (0)", false, () -> setFilter("EARNED"));
        lockedFilterBtn = createFilterButton("Locked (11)", false, () -> setFilter("LOCKED"));

        bar.add(allFilterBtn);
        bar.add(earnedFilterBtn);
        bar.add(lockedFilterBtn);
        return bar;
    }

    private JButton createFilterButton(String text, boolean active, Runnable onClick) {
        JButton btn = new JButton(text);
        btn.setFont(UITheme.font(Font.BOLD, 12f));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        updateFilterButtonStyle(btn, active);
        btn.addActionListener(e -> onClick.run());
        return btn;
    }

    private void updateFilterButtonStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(UITheme.PRIMARY);
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(Color.WHITE);
            btn.setForeground(UITheme.TEXT_SECONDARY);
        }
    }

    private void setFilter(String filter) {
        this.currentFilter = filter;
        updateFilterButtonStyle(allFilterBtn, "ALL".equals(filter));
        updateFilterButtonStyle(earnedFilterBtn, "EARNED".equals(filter));
        updateFilterButtonStyle(lockedFilterBtn, "LOCKED".equals(filter));
        renderBadgeCards();
    }

    private void loadDataAsync() {
        new SwingWorker<DataPayload, Void>() {
            @Override
            protected DataPayload doInBackground() {
                AchievementService.StreakInfo streak = AchievementService.getStreakInfo(userId);
                List<AchievementService.Achievement> list = AchievementService.getProgress(userId);
                return new DataPayload(streak, list);
            }

            @Override
            protected void done() {
                try {
                    DataPayload payload = get();
                    loadedAchievements = payload.achievements != null ? payload.achievements : new ArrayList<>();

                    // Update Streak labels
                    int currStreak = payload.streak != null ? payload.streak.currentStreak : 0;
                    int longStreak = payload.streak != null ? payload.streak.longestStreak : 0;

                    currentStreakVal.setText(currStreak + (currStreak == 1 ? " Day" : " Days"));
                    longestStreakVal.setText(longStreak + (longStreak == 1 ? " Day" : " Days"));

                    // Count earned vs total
                    int earnedCount = 0;
                    for (AchievementService.Achievement a : loadedAchievements) {
                        if (a.earned) earnedCount++;
                    }
                    int totalCount = loadedAchievements.size();
                    final int finalEarned = earnedCount;

                    // Animate count-up for badges unlocked
                    Anim.animate(600, p -> {
                        int val = Math.round(finalEarned * Anim.easeOutCubic(p));
                        badgesUnlockedVal.setText(val + " / " + totalCount);
                    }, () -> {
                        badgesUnlockedVal.setText(finalEarned + " / " + totalCount);
                    });

                    allFilterBtn.setText("All (" + totalCount + ")");
                    earnedFilterBtn.setText("Unlocked (" + earnedCount + ")");
                    lockedFilterBtn.setText("Locked (" + (totalCount - earnedCount) + ")");

                    renderBadgeCards();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    private void renderBadgeCards() {
        cardsGrid.removeAll();

        List<AchievementService.Achievement> filtered = new ArrayList<>();
        for (AchievementService.Achievement a : loadedAchievements) {
            if ("ALL".equals(currentFilter)) {
                filtered.add(a);
            } else if ("EARNED".equals(currentFilter) && a.earned) {
                filtered.add(a);
            } else if ("LOCKED".equals(currentFilter) && !a.earned) {
                filtered.add(a);
            }
        }

        if (filtered.isEmpty()) {
            JPanel emptyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            emptyPanel.setOpaque(false);
            JLabel emptyLbl = new JLabel("No achievements match the current filter.");
            emptyLbl.setFont(UITheme.font(Font.PLAIN, 13f));
            emptyLbl.setForeground(UITheme.TEXT_MUTED);
            emptyPanel.add(emptyLbl);
            cardsGrid.add(emptyPanel);
        } else {
            for (AchievementService.Achievement ach : filtered) {
                cardsGrid.add(buildBadgeCard(ach));
            }
        }

        cardsGrid.revalidate();
        cardsGrid.repaint();
    }

    private JComponent buildBadgeCard(AchievementService.Achievement ach) {
        DashboardComponents.BentoCard card = new DashboardComponents.BentoCard(14);
        card.setLayout(new BorderLayout(14, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        final UITheme.IconType iconType = UITheme.parseIconKey(ach.iconKey);
        final boolean earned = ach.earned;

        // Badge Icon Graphic (52x52)
        JComponent badgeCircle = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                int sz = Math.min(getWidth(), getHeight());

                if (earned) {
                    // Vibrant golden circle with dual ring
                    g2.setPaint(new LinearGradientPaint(0, 0, sz, sz,
                            new float[]{0f, 1f},
                            new Color[]{new Color(254, 243, 199), new Color(253, 230, 138)}));
                    g2.fill(new Ellipse2D.Float(2, 2, sz - 4, sz - 4));
                    g2.setColor(new Color(245, 158, 11, 200));
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.draw(new Ellipse2D.Float(2, 2, sz - 4, sz - 4));
                    // Vector glyph
                    UITheme.drawIcon(g2, iconType, sz / 2, sz / 2, new Color(180, 83, 9), sz, 0.24f);
                } else {
                    // Muted grayscale circle
                    g2.setColor(new Color(241, 245, 249));
                    g2.fill(new Ellipse2D.Float(2, 2, sz - 4, sz - 4));
                    g2.setColor(new Color(203, 213, 225));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(new Ellipse2D.Float(2, 2, sz - 4, sz - 4));
                    // Vector glyph in muted slate
                    UITheme.drawIcon(g2, iconType, sz / 2, sz / 2, new Color(148, 163, 184), sz, 0.24f);
                }
                g2.dispose();
            }
        };
        badgeCircle.setPreferredSize(new Dimension(52, 52));
        badgeCircle.setMinimumSize(new Dimension(52, 52));

        card.add(badgeCircle, BorderLayout.WEST);

        // Center Content Stack
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JLabel nameLbl = new JLabel(ach.name);
        nameLbl.setFont(UITheme.font(Font.BOLD, 14f));
        nameLbl.setForeground(earned ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY);

        JLabel descLbl = new JLabel("<html>" + ach.description + "</html>");
        descLbl.setFont(UITheme.font(Font.PLAIN, 12f));
        descLbl.setForeground(earned ? UITheme.TEXT_SECONDARY : UITheme.TEXT_MUTED);
        descLbl.setBorder(new EmptyBorder(3, 0, 6, 0));

        center.add(nameLbl);
        center.add(descLbl);

        if (!earned) {
            // Rounded Progress Bar
            JComponent progressBar = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.configureGraphics(g2);
                    int w = getWidth();
                    int h = getHeight();

                    // Track
                    g2.setColor(new Color(226, 232, 240));
                    g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));

                    // Fill
                    float fillW = (float) (w * (ach.progressPct / 100.0));
                    if (fillW > 0.1f) {
                        g2.setColor(UITheme.PRIMARY);
                        g2.fill(new RoundRectangle2D.Float(0, 0, Math.min(w, fillW), h, h, h));
                    }
                    g2.dispose();
                }
            };
            progressBar.setPreferredSize(new Dimension(160, 6));
            progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            center.add(progressBar);
            center.add(Box.createVerticalStrut(4));
        }

        // Bottom status chip or date
        JPanel botRow = new JPanel(new BorderLayout());
        botRow.setOpaque(false);

        if (earned) {
            JLabel earnedChip = UITheme.createBadge(
                    "\u2713 Unlocked " + ach.getFormattedEarnedDate(),
                    UITheme.SUCCESS_SOFT,
                    UITheme.SUCCESS_TEXT
            );
            botRow.add(earnedChip, BorderLayout.WEST);
        } else {
            JLabel hintLbl = new JLabel(ach.progressHint);
            hintLbl.setFont(UITheme.font(Font.BOLD, 11f));
            hintLbl.setForeground(UITheme.PRIMARY);

            JLabel lockedChip = UITheme.createBadge("Locked", new Color(241, 245, 249), UITheme.TEXT_MUTED);
            botRow.add(hintLbl, BorderLayout.WEST);
            botRow.add(lockedChip, BorderLayout.EAST);
        }

        center.add(botRow);
        card.add(center, BorderLayout.CENTER);

        return card;
    }

    private static class DataPayload {
        final AchievementService.StreakInfo streak;
        final List<AchievementService.Achievement> achievements;

        DataPayload(AchievementService.StreakInfo streak, List<AchievementService.Achievement> achievements) {
            this.streak = streak;
            this.achievements = achievements;
        }
    }
}
