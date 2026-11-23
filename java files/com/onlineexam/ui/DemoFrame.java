package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Preview window for Task 1: Pastel Aurora Light palette, GlassPanel, gradients,
 * colored shadows, bundle-ready font loader, and Anim.java easing/transitions.
 *
 * Run with:
 *   java -cp "out;lib\mysql-connector-j.jar;lib\flatlaf.jar" com.onlineexam.ui.DemoFrame
 */
public class DemoFrame extends JFrame {

    private final GlassPanel kpiCard1;
    private final GlassPanel kpiCard2;
    private final GlassPanel kpiCard3;
    private final JLabel kpiVal1;
    private final JLabel kpiVal2;
    private final JLabel kpiVal3;
    private final UITheme.ModernProgressBar primaryBar;
    private final UITheme.ModernProgressBar accentBar;
    private final GlassPanel bounceTarget;

    public DemoFrame() {
        setTitle("Pastel Aurora Light \u2014 UI Foundation Preview");
        setSize(980, 680);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Animated Pastel Aurora Light mesh-gradient background
        AuroraBackground root = new AuroraBackground(new BorderLayout(0, 18));
        root.setBorder(new EmptyBorder(22, 26, 22, 26));

        // ================= HEADER GLASS BAR =================
        GlassPanel headerCard = new GlassPanel(16);
        headerCard.setLayout(new BorderLayout(16, 0));
        headerCard.setBorder(new EmptyBorder(16, 22, 18, 22));

        JPanel headLeft = new JPanel();
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        headLeft.setOpaque(false);

        JLabel title = new JLabel("Pastel Aurora Light \u2014 Visual Foundation");
        title.setFont(UITheme.font(Font.BOLD, 21f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        String fontBadgeText = UITheme.isInterFontLoaded()
                ? "Font: " + UITheme.FONT_FAMILY + " (Bundled/Installed)"
                : "Font: " + UITheme.FONT_FAMILY + " (Fallback \u2014 drop Inter TTFs in fonts/)";
        JLabel sub = new JLabel("Hover any GlassPanel for the indigo radial spotlight \u2022 " + fontBadgeText);
        sub.setFont(UITheme.font(Font.PLAIN, 12.5f));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        sub.setBorder(new EmptyBorder(3, 0, 0, 0));

        headLeft.add(title);
        headLeft.add(sub);

        JPanel headBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        headBtns.setOpaque(false);

        JLabel intLbl = new JLabel("Aurora 100%");
        intLbl.setFont(UITheme.font(Font.BOLD, 11.5f));
        intLbl.setForeground(UITheme.TEXT_SECONDARY);

        JSlider intensitySlider = new JSlider(0, 100, 100);
        intensitySlider.setOpaque(false);
        intensitySlider.setPreferredSize(new Dimension(110, 26));
        intensitySlider.setToolTipText("Adjust AuroraBackground.setIntensity(float)");
        intensitySlider.addChangeListener(e -> {
            int val = intensitySlider.getValue();
            root.setIntensity(val / 100f);
            intLbl.setText(val <= 35 ? ("Calm " + val + "%") : ("Aurora " + val + "%"));
        });

        UITheme.ModernButton replayBtn = UITheme.gradientPrimaryButton("Replay Animations");
        replayBtn.addActionListener(e -> replayAllAnimations());

        UITheme.ModernButton toastBtn = UITheme.gradientAccentButton("Preview Toast");
        toastBtn.addActionListener(e -> UITheme.showToast(
                this,
                "Pastel Aurora Ready",
                "Animated 30 FPS AuroraBackground + colored shadow active.",
                UITheme.ToastType.SUCCESS
        ));

        headBtns.add(intLbl);
        headBtns.add(intensitySlider);
        headBtns.add(replayBtn);
        headBtns.add(toastBtn);

        headerCard.add(headLeft, BorderLayout.CENTER);
        headerCard.add(headBtns, BorderLayout.EAST);

        // ================= KPI GLASS CARDS ROW =================
        JPanel kpiRow = new JPanel(new GridLayout(1, 3, 16, 0));
        kpiRow.setOpaque(false);

        kpiVal1 = new JLabel("0");
        kpiVal2 = new JLabel("0.0%");
        kpiVal3 = new JLabel("0");

        kpiCard1 = buildDemoKpi("QUESTION BANK", kpiVal1, "Frosted 70% white + Indigo shadow",
                UITheme.PRIMARY, UITheme.PRIMARY_SOFT, UITheme.IconType.BOOK);
        kpiCard2 = buildDemoKpi("PASS ACCURACY", kpiVal2, "Hover for mouse-tracking spotlight",
                UITheme.VIOLET, UITheme.VIOLET_SOFT, UITheme.IconType.CHART);
        kpiCard3 = buildDemoKpi("EXAMS COMPLETED", kpiVal3, "16ms Timer + easeOutCubic countUp",
                UITheme.PINK, UITheme.PINK_SOFT, UITheme.IconType.TROPHY);

        kpiRow.add(kpiCard1);
        kpiRow.add(kpiCard2);
        kpiRow.add(kpiCard3);

        // ================= LOWER SPLIT: PALETTE + GRADIENTS & ANIMATIONS =================
        JPanel lowerSplit = new JPanel(new GridLayout(1, 2, 16, 0));
        lowerSplit.setOpaque(false);

        // Left: Palette Swatches Card
        GlassPanel paletteCard = new GlassPanel(16);
        paletteCard.setLayout(new BorderLayout(0, 14));
        paletteCard.setBorder(new EmptyBorder(18, 22, 20, 22));

        JLabel palTitle = new JLabel("Pastel Aurora Light Palette");
        palTitle.setFont(UITheme.font(Font.BOLD, 15f));
        palTitle.setForeground(UITheme.TEXT_PRIMARY);

        JPanel swatches = new JPanel(new GridLayout(4, 3, 10, 10));
        swatches.setOpaque(false);
        swatches.add(createSwatch("Canvas", "#F6F7FB", UITheme.BG_CANVAS));
        swatches.add(createSwatch("Indigo", "#4F46E5", UITheme.PRIMARY));
        swatches.add(createSwatch("Violet", "#7C3AED", UITheme.VIOLET));
        swatches.add(createSwatch("Sky", "#0EA5E9", UITheme.SKY));
        swatches.add(createSwatch("Pink", "#EC4899", UITheme.PINK));
        swatches.add(createSwatch("Success", "#10B981", UITheme.SUCCESS));
        swatches.add(createSwatch("Warning", "#F59E0B", UITheme.WARNING));
        swatches.add(createSwatch("Danger", "#EF4444", UITheme.DANGER));
        swatches.add(createSwatch("Text Dark", "#0F172A", UITheme.TEXT_PRIMARY));
        swatches.add(createSwatch("Secondary", "#475569", UITheme.TEXT_SECONDARY));
        swatches.add(createSwatch("Muted", "#94A3B8", UITheme.TEXT_MUTED));
        swatches.add(createSwatch("Glass 70%", "RGBA", UITheme.GLASS_FILL));

        paletteCard.add(palTitle, BorderLayout.NORTH);
        paletteCard.add(swatches, BorderLayout.CENTER);

        // Right: Gradients, Progress Bars & Animation Controls
        GlassPanel animCard = new GlassPanel(16);
        animCard.setLayout(new BorderLayout(0, 14));
        animCard.setBorder(new EmptyBorder(18, 22, 20, 22));

        JLabel animTitle = new JLabel("Gradients, Easing & Glass Motion");
        animTitle.setFont(UITheme.font(Font.BOLD, 15f));
        animTitle.setForeground(UITheme.TEXT_PRIMARY);

        JPanel animBody = new JPanel();
        animBody.setLayout(new BoxLayout(animBody, BoxLayout.Y_AXIS));
        animBody.setOpaque(false);

        JLabel bar1Lbl = new JLabel("Primary Gradient (Indigo \u2192 Violet)");
        bar1Lbl.setFont(UITheme.font(Font.BOLD, 12f));
        bar1Lbl.setForeground(UITheme.TEXT_SECONDARY);
        bar1Lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        primaryBar = new UITheme.ModernProgressBar(100);
        primaryBar.setPreferredSize(new Dimension(320, 10));
        primaryBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        primaryBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel bar2Lbl = new JLabel("Accent Gradient (Violet \u2192 Pink)");
        bar2Lbl.setFont(UITheme.font(Font.BOLD, 12f));
        bar2Lbl.setForeground(UITheme.TEXT_SECONDARY);
        bar2Lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        accentBar = new UITheme.ModernProgressBar(100);
        accentBar.setUseAccentGradient(true);
        accentBar.setPreferredSize(new Dimension(320, 10));
        accentBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        accentBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Interactive bounce / slide target card
        bounceTarget = new GlassPanel(14);
        bounceTarget.setShadowColor(UITheme.VIOLET);
        bounceTarget.setLayout(new BorderLayout(10, 0));
        bounceTarget.setBorder(new EmptyBorder(12, 16, 14, 16));
        bounceTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        bounceTarget.setAlignmentX(Component.LEFT_ALIGNMENT);

        bounceTarget.add(new UITheme.VectorIcon(UITheme.IconType.CAP, UITheme.PRIMARY, UITheme.PRIMARY_SOFT, 34), BorderLayout.WEST);
        JLabel targetText = new JLabel("<html><b>Interactive Glass Target</b><br><span style='color:#475569;'>Trigger Fade, Slide, Back, or Elastic easing below</span></html>");
        targetText.setFont(UITheme.font(Font.PLAIN, 12f));
        bounceTarget.add(targetText, BorderLayout.CENTER);

        JPanel triggerRow = new JPanel(new GridLayout(2, 2, 8, 8));
        triggerRow.setOpaque(false);
        triggerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        triggerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        UITheme.ModernButton fadeBtn = UITheme.secondaryButton("fadeIn()");
        fadeBtn.addActionListener(e -> Anim.fadeIn(bounceTarget, 420));

        UITheme.ModernButton slideBtn = UITheme.secondaryButton("slideIn(UP)");
        slideBtn.addActionListener(e -> Anim.slideIn(bounceTarget, Anim.Direction.UP, 420, 22));

        UITheme.ModernButton backBtn = UITheme.secondaryButton("easeOutBack");
        backBtn.addActionListener(e -> Anim.animate(520, p -> {
            float eased = Anim.easeOutBack(p);
            bounceTarget.setSlideOffset(Math.round((1f - eased) * -36f), 0);
        }, () -> bounceTarget.setSlideOffset(0, 0)));

        UITheme.ModernButton elasticBtn = UITheme.secondaryButton("easeOutElastic");
        elasticBtn.addActionListener(e -> Anim.animate(760, p -> {
            float eased = Anim.easeOutElastic(p);
            bounceTarget.setSlideOffset(0, Math.round((1f - eased) * -28f));
        }, () -> bounceTarget.setSlideOffset(0, 0)));

        triggerRow.add(fadeBtn);
        triggerRow.add(slideBtn);
        triggerRow.add(backBtn);
        triggerRow.add(elasticBtn);

        animBody.add(bar1Lbl);
        animBody.add(Box.createVerticalStrut(6));
        animBody.add(primaryBar);
        animBody.add(Box.createVerticalStrut(14));
        animBody.add(bar2Lbl);
        animBody.add(Box.createVerticalStrut(6));
        animBody.add(accentBar);
        animBody.add(Box.createVerticalStrut(16));
        animBody.add(bounceTarget);
        animBody.add(Box.createVerticalStrut(12));
        animBody.add(triggerRow);

        animCard.add(animTitle, BorderLayout.NORTH);
        animCard.add(animBody, BorderLayout.CENTER);

        lowerSplit.add(paletteCard);
        lowerSplit.add(animCard);

        JPanel centerStack = new JPanel(new BorderLayout(0, 16));
        centerStack.setOpaque(false);
        centerStack.add(kpiRow, BorderLayout.NORTH);
        centerStack.add(lowerSplit, BorderLayout.CENTER);

        root.add(headerCard, BorderLayout.NORTH);
        root.add(centerStack, BorderLayout.CENTER);

        add(root);
        setVisible(true);

        // Kick off entrance animations smoothly on the EDT
        SwingUtilities.invokeLater(this::replayAllAnimations);
    }

    private void replayAllAnimations() {
        Anim.slideIn(kpiCard1, Anim.Direction.UP, 380, 22);
        Anim.slideIn(kpiCard2, Anim.Direction.UP, 480, 26);
        Anim.slideIn(kpiCard3, Anim.Direction.UP, 580, 30);

        Anim.countUp(kpiVal1, 0, 100, 750, "%d");
        Anim.countUp(kpiVal2, 0.0, 94.5, 850, "%.1f%%");
        Anim.countUp(kpiVal3, 0, 24, 700, "%d");

        Anim.animate(750, p -> {
            float e = Anim.easeInOutCubic(p);
            primaryBar.setValue(Math.round(82 * e));
            accentBar.setValue(Math.round(68 * e));
        });
    }

    private GlassPanel buildDemoKpi(
            String title, JLabel valLbl, String subtitle,
            Color accent, Color softBg, UITheme.IconType iconType) {
        GlassPanel card = new GlassPanel(16);
        card.setShadowColor(accent);
        card.setLeftAccentColor(accent);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(16, 20, 18, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(UITheme.font(Font.BOLD, 11f));
        t.setForeground(UITheme.TEXT_MUTED);

        top.add(t, BorderLayout.WEST);
        top.add(new UITheme.VectorIcon(iconType, accent, softBg, 32), BorderLayout.EAST);

        valLbl.setFont(UITheme.font(Font.BOLD, 28f));
        valLbl.setForeground(UITheme.TEXT_PRIMARY);

        JLabel sub = new JLabel(subtitle);
        sub.setFont(UITheme.font(Font.PLAIN, 12f));
        sub.setForeground(UITheme.TEXT_SECONDARY);

        card.add(top, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        card.add(sub, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createSwatch(String name, String hex, Color color) {
        JPanel cell = new JPanel(new BorderLayout(8, 0));
        cell.setOpaque(false);

        JComponent dot = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                g2.setColor(color);
                g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                g2.setColor(UITheme.GLASS_SLATE_BORDER);
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(28, 28));

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        JLabel n = new JLabel(name);
        n.setFont(UITheme.font(Font.BOLD, 11f));
        n.setForeground(UITheme.TEXT_PRIMARY);
        JLabel h = new JLabel(hex);
        h.setFont(UITheme.font(Font.PLAIN, 10f));
        h.setForeground(UITheme.TEXT_MUTED);
        text.add(n);
        text.add(h);

        cell.add(dot, BorderLayout.WEST);
        cell.add(text, BorderLayout.CENTER);
        return cell;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UITheme.initGlobalLookAndFeel();
            new DemoFrame();
        });
    }
}
