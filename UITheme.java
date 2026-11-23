package com.onlineexam.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UITheme {

    // ================= BUNDLE-READY FONT LOADER (Inter -> Segoe UI fallback) =================
    private static boolean interFontLoaded = false;

    public static final String FONT_FAMILY = loadAppFont();

    private static String loadAppFont() {
        String loadedFamily = null;
        File fontsDir = new File("fonts");
        if (fontsDir.exists() && fontsDir.isDirectory()) {
            File[] files = fontsDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".ttf") || lower.endsWith(".otf");
            });
            if (files != null) {
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                for (File f : files) {
                    try {
                        int format = f.getName().toLowerCase(Locale.ROOT).endsWith(".otf")
                                ? Font.TYPE1_FONT : Font.TRUETYPE_FONT;
                        Font font = Font.createFont(format, f);
                        ge.registerFont(font);
                        if (font.getFamily().toLowerCase(Locale.ROOT).contains("inter")) {
                            loadedFamily = font.getFamily();
                            interFontLoaded = true;
                        }
                    } catch (Exception ignored) {
                        // Ignore malformed font files and continue fallback
                    }
                }
            }
        }
        if (loadedFamily != null) {
            return loadedFamily;
        }
        // Check if Inter is already installed on the host OS
        try {
            for (String fam : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if ("Inter".equalsIgnoreCase(fam)) {
                    interFontLoaded = true;
                    return fam;
                }
            }
        } catch (Exception ignored) {
        }
        return "Segoe UI";
    }

    public static boolean isInterFontLoaded() {
        return interFontLoaded;
    }

    public static String getLoadedFontFamily() {
        return FONT_FAMILY;
    }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size)).deriveFont(size);
    }

    // ================= PASTEL AURORA LIGHT PALETTE (LIGHT THEME ONLY) =================
    public static Color BG_CANVAS = new Color(248, 249, 252);          // Canvas #F8F9FC
    public static Color BG_CARD = Color.WHITE;                         // Opaque White Surface
    public static Color BG_SUBTLE = new Color(248, 250, 252);          // Subtle Slate Surface #F8FAFC

    // Glass surface tokens (Crisp enterprise white cards)
    public static Color GLASS_FILL = Color.WHITE;                      // Pure White Card Fill
    public static Color GLASS_BORDER = new Color(226, 232, 240);       // Hairline Border #E2E8F0
    public static Color GLASS_SLATE_BORDER = new Color(226, 232, 240); // Hairline Slate #E2E8F0

    public static Color BORDER = new Color(226, 232, 240);             // Hairline Border #E2E8F0
    public static Color BORDER_STRONG = new Color(203, 213, 225);      // #CBD5E1

    // Brand Aurora Accents
    public static Color PRIMARY = new Color(79, 70, 229);              // Primary Indigo #4F46E5
    public static Color PRIMARY_HOVER = new Color(67, 56, 202);        // #4338CA
    public static Color PRIMARY_SOFT = new Color(238, 242, 255);       // #EEF2FF

    public static Color INDIGO = new Color(79, 70, 229);               // #4F46E5
    public static Color INDIGO_BRIGHT = new Color(99, 102, 241);       // #6366F1

    public static Color VIOLET = new Color(124, 58, 237);              // Violet #7C3AED
    public static Color VIOLET_HOVER = new Color(109, 40, 217);        // #6D28D9
    public static Color VIOLET_SOFT = new Color(245, 243, 255);        // #F5F3FF

    public static Color SKY = new Color(14, 165, 233);                 // Sky #0EA5E9
    public static Color SKY_SOFT = new Color(224, 242, 254);           // #E0F2FE

    public static Color PINK = new Color(236, 72, 153);                // Pink #EC4899
    public static Color PINK_HOVER = new Color(219, 39, 119);          // #DB2777
    public static Color PINK_SOFT = new Color(252, 231, 243);          // #FCE7F3

    public static Color TEAL = new Color(14, 165, 233);                // Mapped to Sky #0EA5E9
    public static Color TEAL_SOFT = new Color(224, 242, 254);

    // Semantic Status Colors
    public static Color SUCCESS = new Color(16, 185, 129);             // Success #10B981
    public static Color SUCCESS_HOVER = new Color(5, 150, 105);        // #059669
    public static Color SUCCESS_SOFT = new Color(236, 253, 245);       // #ECFDF5
    public static Color SUCCESS_TEXT = new Color(4, 120, 87);          // #047857

    public static Color WARNING = new Color(245, 158, 11);             // Warning #F59E0B
    public static Color WARNING_HOVER = new Color(217, 119, 6);        // #D97706
    public static Color WARNING_SOFT = new Color(255, 251, 235);       // #FFFBEB
    public static Color WARNING_TEXT = new Color(180, 83, 9);          // #B45309

    public static Color DANGER = new Color(239, 68, 68);               // Danger #EF4444
    public static Color DANGER_HOVER = new Color(220, 38, 38);         // #DC2626
    public static Color DANGER_SOFT = new Color(254, 242, 242);        // #FEF2F2
    public static Color DANGER_TEXT = new Color(185, 28, 28);          // #B91C1C

    public static Color GOLD = new Color(245, 158, 11);
    public static Color GOLD_SOFT = new Color(254, 243, 199);

    // Light-compatible Sidebar / Brand tokens (no dark/black backgrounds)
    public static Color SIDEBAR_BG = new Color(79, 70, 229);           // Primary Indigo #4F46E5
    public static Color SIDEBAR_HOVER = new Color(99, 102, 241);       // Soft Indigo Hover
    public static Color SIDEBAR_ACTIVE = new Color(124, 58, 237);      // Violet Active Pill
    public static Color SIDEBAR_TEXT = new Color(238, 242, 255);       // Crisp Light Indigo Text
    public static Color SIDEBAR_MUTED = new Color(199, 210, 254);      // Muted Periwinkle
    public static Color NAVY = new Color(79, 70, 229);

    public static Color MONO_BTN_BG = PRIMARY;
    public static Color MONO_BTN_HOVER = PRIMARY_HOVER;
    public static Color MONO_BTN_FG = Color.WHITE;

    // Typography Colors
    public static Color TEXT_PRIMARY = new Color(15, 23, 42);          // #0F172A
    public static Color TEXT_SECONDARY = new Color(71, 85, 105);       // #475569
    public static Color TEXT_MUTED = new Color(148, 163, 184);         // #94A3B8

    // ================= TYPOGRAPHY =================
    public static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 20);
    public static final Font FONT_SUBTITLE = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_HEADING = new Font(FONT_FAMILY, Font.BOLD, 15);
    public static final Font FONT_LABEL = new Font(FONT_FAMILY, Font.BOLD, 12);
    public static final Font FONT_BODY = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_BUTTON = new Font(FONT_FAMILY, Font.BOLD, 13);
    public static final Font FONT_BADGE = new Font(FONT_FAMILY, Font.BOLD, 11);

    public enum IconType {
        CAP, PLAY, TROPHY, CHECKLIST, CHART, PLUS, BOOK, USERS, USER, CLOCK, SHIELD, SUBJECT, FLAG,
        DASHBOARD, LOGOUT, THEME, CHECK, LIST, AWARD,
        MEDAL, FLAME, STAR, BOLT, TARGET, CALENDAR
    }

    public enum ToastType {
        SUCCESS, WARNING, ERROR, INFO
    }

    private UITheme() {
    }

    // ================= GRAPHICS2D QUALITY HELPER =================
    public static void configureGraphics(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // ================= GRADIENT HELPERS =================

    /**
     * Primary Aurora gradient: Indigo (#4F46E5) -> Violet (#7C3AED).
     */
    public static LinearGradientPaint createPrimaryGradient(float x1, float y1, float x2, float y2) {
        float ex = (x2 == x1 && y2 == y1) ? x1 + 1f : x2;
        return new LinearGradientPaint(x1, y1, ex, y2, new float[]{0f, 1f}, new Color[]{PRIMARY, VIOLET});
    }

    public static LinearGradientPaint primaryGradient(int width, int height) {
        return createPrimaryGradient(0f, 0f, Math.max(1f, width), Math.max(0f, height * 0.25f));
    }

    /**
     * Secondary Aurora gradient: Violet (#7C3AED) -> Pink (#EC4899).
     */
    public static LinearGradientPaint createAccentGradient(float x1, float y1, float x2, float y2) {
        float ex = (x2 == x1 && y2 == y1) ? x1 + 1f : x2;
        return new LinearGradientPaint(x1, y1, ex, y2, new float[]{0f, 1f}, new Color[]{VIOLET, PINK});
    }

    public static LinearGradientPaint accentGradient(int width, int height) {
        return createAccentGradient(0f, 0f, Math.max(1f, width), Math.max(0f, height * 0.25f));
    }

    /**
     * Soft multi-stop Indigo -> Violet -> Pink gradient border for glass panels.
     */
    public static LinearGradientPaint createAuroraBorderGradient(float x1, float y1, float x2, float y2, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        float ex = (x2 == x1 && y2 == y1) ? x1 + 1f : x2;
        return new LinearGradientPaint(
                x1, y1, ex, y2,
                new float[]{0f, 0.55f, 1f},
                new Color[]{
                        Anim.withAlpha(PRIMARY, a),
                        Anim.withAlpha(VIOLET, a),
                        Anim.withAlpha(PINK, Math.max(0, a - 12))
                }
        );
    }

    // ================= COLORED SOFT SHADOW HELPER =================

    /**
     * Paints a soft, multi-layered floating shadow tinted with Primary Indigo (never gray/black).
     */
    public static void paintColoredShadow(Graphics2D g2, float x, float y, float w, float h, float arc) {
        paintColoredShadow(g2, x, y, w, h, arc, PRIMARY, 5, 4);
    }

    /**
     * Paints a diffused multi-step colored shadow tinted with the given accent color at low alpha.
     */
    public static void paintColoredShadow(
            Graphics2D g2, float x, float y, float w, float h, float arc,
            Color shadowColor, int spread, int offsetY) {
        Color tint = shadowColor != null ? shadowColor : PRIMARY;
        int layers = Math.max(1, spread);
        configureGraphics(g2);

        // Ambient diffused shadow: neutral slate at very low alpha (3%..6%)
        for (int i = layers; i >= 1; i--) {
            float progress = i / (float) layers;
            int ambientAlpha = Math.max(1, Math.round((1f - progress * 0.65f) * 6f));
            float expand = i * 1.3f;
            g2.setColor(new Color(15, 23, 42, ambientAlpha));
            g2.fill(new RoundRectangle2D.Float(
                    x - expand * 0.5f,
                    y + expand * 0.25f,
                    w + expand,
                    h + expand * 0.75f,
                    arc + expand * 0.6f,
                    arc + expand * 0.6f
            ));
        }

        // Key directional elevation shadow: slight vertical offset with accent tint
        for (int i = layers; i >= 1; i--) {
            float progress = i / (float) layers;
            int keyAlpha = Math.max(2, Math.round((1f - progress * 0.6f) * 10f));
            float expand = i * 0.85f;
            float dy = offsetY * progress;
            g2.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), keyAlpha));
            g2.fill(new RoundRectangle2D.Float(
                    x - expand * 0.35f,
                    y + dy,
                    w + expand * 0.7f,
                    h + expand * 0.35f,
                    arc + expand * 0.4f,
                    arc + expand * 0.4f
            ));
        }
    }

    // ================= LIGHT-ONLY LOOK & FEEL (Rule 6: No Dark Mode) =================

    public static boolean isDarkMode() {
        return false;
    }

    public static void toggleDarkMode() {
        // Light theme only (no-op preserved for binary/source compatibility)
        setupFlatLaf();
    }

    public static void setDarkMode(boolean ignored) {
        // Light theme only
        setupFlatLaf();
    }

    public static void initGlobalLookAndFeel() {
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        setupFlatLaf();
    }

    private static void setupFlatLaf() {
        try {
            FlatLightLaf.setup();
        } catch (Exception ignored) {
        }

        UIManager.put("defaultFont", FONT_BODY);
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 64));
        UIManager.put("Component.focusedBorderColor", PRIMARY);
        UIManager.put("Component.borderColor", BORDER_STRONG);
        UIManager.put("TextField.background", BG_CARD);
        UIManager.put("TextField.margin", new Insets(7, 12, 7, 12));
        UIManager.put("PasswordField.background", BG_CARD);
        UIManager.put("PasswordField.margin", new Insets(7, 12, 7, 12));
        UIManager.put("PasswordField.showRevealButton", true);
        UIManager.put("ComboBox.background", BG_CARD);
        UIManager.put("ComboBox.selectionBackground", PRIMARY_SOFT);
        UIManager.put("ComboBox.selectionForeground", PRIMARY);

        // Enterprise Table styling
        UIManager.put("Table.rowHeight", 40);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", new Color(241, 245, 249));
        UIManager.put("Table.background", BG_CARD);
        UIManager.put("Table.selectionBackground", PRIMARY_SOFT);
        UIManager.put("Table.selectionForeground", PRIMARY);
        UIManager.put("Table.selectionInactiveBackground", new Color(245, 247, 250));
        UIManager.put("Table.selectionInactiveForeground", TEXT_PRIMARY);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("TableHeader.height", 38);
        UIManager.put("TableHeader.background", new Color(248, 250, 252));
        UIManager.put("TableHeader.foreground", new Color(71, 85, 105));
        UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        UIManager.put("TableHeader.separatorColor", new Color(241, 245, 249));

        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 8);
        UIManager.put("ScrollBar.trackArc", 0);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.track", BG_CANVAS);

        UIManager.put("TabbedPane.tabHeight", 38);
        UIManager.put("TabbedPane.underlineColor", PRIMARY);
        UIManager.put("TabbedPane.inactiveUnderlineColor", BORDER_STRONG);
        UIManager.put("TabbedPane.selectedForeground", PRIMARY);
        UIManager.put("TabbedPane.foreground", TEXT_SECONDARY);
        UIManager.put("TabbedPane.hoverColor", PRIMARY_SOFT);
        UIManager.put("TabbedPane.contentSeparatorHeight", 1);
        UIManager.put("TabbedPane.showTabSeparators", false);

        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.showIcon", false);
        UIManager.put("TitlePane.background", BG_CARD);
        UIManager.put("TitlePane.foreground", TEXT_PRIMARY);

        UIManager.put("OptionPane.background", BG_CARD);
        UIManager.put("Panel.background", BG_CANVAS);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
    }

    /**
     * Preserved signature for existing callers, hidden per Rule 6 (Light Theme Only).
     */
    public static ModernButton themeToggleButton(Runnable onToggle) {
        ModernButton hidden = headerGhostButton("");
        hidden.setVisible(false);
        hidden.setEnabled(false);
        hidden.setPreferredSize(new Dimension(0, 0));
        hidden.setMinimumSize(new Dimension(0, 0));
        hidden.setMaximumSize(new Dimension(0, 0));
        return hidden;
    }

    // ================= SIDEBAR NAVIGATION HELPER =================
    public static JButton createSidebarNavButton(String text, boolean active, Runnable onClick) {
        return createSidebarNavButton(null, text, active, onClick);
    }

    public static JButton createSidebarNavButton(IconType iconType, String text, boolean active, Runnable onClick) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                if (active) {
                    g2.setPaint(createPrimaryGradient(0, 0, getWidth(), getHeight()));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                    g2.setColor(Color.WHITE);
                    g2.fill(new RoundRectangle2D.Float(0, 8, 3, getHeight() - 16, 2, 2));
                } else if (hovered) {
                    g2.setColor(SIDEBAR_HOVER);
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        if (iconType != null) {
            btn.setIcon(createSwingIcon(iconType, active ? Color.WHITE : SIDEBAR_MUTED, 16));
            btn.setIconTextGap(11);
        }
        btn.setFont(new Font(FONT_FAMILY, active ? Font.BOLD : Font.PLAIN, 13));
        btn.setForeground(active ? Color.WHITE : SIDEBAR_TEXT);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(9, 14, 9, 14));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btn.addActionListener(e -> {
            if (onClick != null) onClick.run();
        });
        return btn;
    }

    public static Icon createSwingIcon(IconType type, Color color, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                drawVectorGlyph(g2, type, color, size, 0.14f);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    // ================= CLEAN NON-BLOCKING STATUS TOAST =================
    public static void showToast(Component comp, String title, String message, ToastType type) {
        if (comp == null) return;
        if (comp instanceof JFrame) {
            showToast((JFrame) comp, title, message, type);
            return;
        }
        Window win = (comp instanceof Window) ? (Window) comp : SwingUtilities.getWindowAncestor(comp);
        if (win instanceof JFrame) {
            showToast((JFrame) win, title, message, type);
        }
    }

    public static void showToast(JFrame frame, String title, String message, ToastType type) {
        if (frame == null) return;
        JLayeredPane layeredPane = frame.getLayeredPane();
        if (layeredPane == null) return;

        Color accent;
        switch (type) {
            case SUCCESS: accent = SUCCESS; break;
            case WARNING: accent = WARNING; break;
            case ERROR:   accent = DANGER;  break;
            default:      accent = PRIMARY; break;
        }

        final float[] alpha = {0.0f};
        JPanel toast = new JPanel(new BorderLayout(10, 0)) {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha[0]))));
                super.paint(g2);
                g2.dispose();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                int w = getWidth();
                int h = getHeight();
                paintColoredShadow(g2, 3, 2, w - 6, h - 6, 12, accent, 4, 3);
                Shape shape = new RoundRectangle2D.Float(3, 2, w - 6, h - 6, 12, 12);
                g2.setColor(new Color(255, 255, 255, 235));
                g2.fill(shape);
                Shape oldClip = g2.getClip();
                g2.clip(shape);
                g2.setColor(accent);
                g2.fillRect(3, 2, 4, h - 6);
                g2.setClip(oldClip);
                g2.setColor(GLASS_SLATE_BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);
                g2.dispose();
            }
        };
        toast.setOpaque(false);
        toast.setBorder(new EmptyBorder(10, 16, 12, 16));

        JPanel textStack = new JPanel(new GridLayout(2, 1, 0, 1));
        textStack.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        titleLbl.setForeground(TEXT_PRIMARY);

        JLabel msgLbl = new JLabel(message);
        msgLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        msgLbl.setForeground(TEXT_SECONDARY);

        textStack.add(titleLbl);
        textStack.add(msgLbl);
        toast.add(textStack, BorderLayout.CENTER);

        int toastW = Math.min(368, Math.max(264, message.length() * 6 + 72));
        int toastH = 60;
        int targetX = Math.max(16, layeredPane.getWidth() - toastW - 20);
        int targetY = 16;

        toast.setBounds(targetX + 18, targetY, toastW, toastH);
        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.moveToFront(toast);

        final int[] step = {0};
        javax.swing.Timer anim = new javax.swing.Timer(Anim.TICK_MS, null);
        anim.addActionListener(e -> {
            step[0]++;
            if (step[0] <= 12) {
                float ease = Anim.easeOutCubic(step[0] / 12f);
                alpha[0] = ease;
                toast.setLocation(targetX + Math.round((1f - ease) * 18f), targetY);
                toast.repaint();
            } else if (step[0] > 170 && step[0] <= 186) {
                float p = (step[0] - 170) / 16f;
                alpha[0] = 1f - Anim.easeInOutCubic(p);
                toast.repaint();
            } else if (step[0] > 186) {
                anim.stop();
                layeredPane.remove(toast);
                layeredPane.repaint();
            }
        });
        anim.start();
    }

    // ================= ACHIEVEMENT CELEBRATION TOAST =================
    public static IconType parseIconKey(String key) {
        if (key == null) return IconType.TROPHY;
        String k = key.trim().toLowerCase(Locale.ROOT);
        switch (k) {
            case "medal": return IconType.MEDAL;
            case "flame": return IconType.FLAME;
            case "star": return IconType.STAR;
            case "bolt": return IconType.BOLT;
            case "target": return IconType.TARGET;
            case "calendar": return IconType.CALENDAR;
            case "trophy": return IconType.TROPHY;
            default: return IconType.AWARD;
        }
    }

    public static Icon createBadgeIcon(String key, Color color, int size) {
        return createSwingIcon(parseIconKey(key), color, size);
    }

    public static void showAchievementToast(Component comp, String badgeName, String badgeDesc, String iconKey, int delayMs) {
        if (comp == null) return;
        if (delayMs > 0) {
            javax.swing.Timer delayTimer = new javax.swing.Timer(delayMs, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                showAchievementToast(comp, badgeName, badgeDesc, iconKey, 0);
            });
            delayTimer.setRepeats(false);
            delayTimer.start();
            return;
        }

        JFrame frame = (comp instanceof JFrame) ? (JFrame) comp : (JFrame) SwingUtilities.getWindowAncestor(comp);
        if (frame == null) return;
        JLayeredPane layeredPane = frame.getLayeredPane();
        if (layeredPane == null) return;

        final IconType iconType = parseIconKey(iconKey);
        final float[] alpha = {0.0f};
        final float[] shine = {-0.6f};

        JPanel toast = new JPanel(new BorderLayout(14, 0)) {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha[0]))));
                super.paint(g2);
                g2.dispose();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                int w = getWidth();
                int h = getHeight();

                // Soft golden drop shadow
                paintColoredShadow(g2, 4, 3, w - 8, h - 8, 14, new Color(245, 158, 11), 6, 4);

                // Toast body
                Shape shape = new RoundRectangle2D.Float(4, 3, w - 8, h - 8, 14, 14);
                g2.setColor(new Color(255, 255, 255, 245));
                g2.fill(shape);

                // Golden left indicator stripe
                Shape oldClip = g2.getClip();
                g2.clip(shape);
                g2.setPaint(new LinearGradientPaint(4, 3, 4, h - 5,
                        new float[]{0f, 1f},
                        new Color[]{new Color(245, 158, 11), new Color(217, 119, 6)}));
                g2.fillRect(4, 3, 5, h - 8);

                // Animated shine sweep ribbon
                if (shine[0] > -0.5f && shine[0] < 1.5f) {
                    float sx = 4 + shine[0] * (w - 8);
                    Path2D ribbon = new Path2D.Float();
                    ribbon.moveTo(sx - 35f, 0);
                    ribbon.lineTo(sx + 20f, 0);
                    ribbon.lineTo(sx + 50f, h);
                    ribbon.lineTo(sx - 5f, h);
                    ribbon.closePath();

                    Paint sheen = new LinearGradientPaint(sx - 35f, 0, sx + 50f, h,
                            new float[]{0f, 0.5f, 1f},
                            new Color[]{
                                new Color(255, 255, 255, 0),
                                new Color(255, 243, 199, 140),
                                new Color(255, 255, 255, 0)
                            });
                    g2.setPaint(sheen);
                    g2.fill(ribbon);
                }
                g2.setClip(oldClip);

                // Subtle gold border
                g2.setColor(new Color(251, 191, 36, 160));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(shape);
                g2.dispose();
            }
        };
        toast.setOpaque(false);
        toast.setBorder(new EmptyBorder(10, 16, 10, 16));

        // Circular badge vector glyph icon
        JComponent badgeCircle = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                int size = Math.min(getWidth(), getHeight());
                // Soft amber/gold circle background
                g2.setColor(new Color(254, 243, 199));
                g2.fill(new Ellipse2D.Float(2, 2, size - 4, size - 4));
                g2.setColor(new Color(245, 158, 11, 180));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new Ellipse2D.Float(2, 2, size - 4, size - 4));
                // Vector glyph
                drawVectorGlyph(g2, iconType, new Color(180, 83, 9), size, 0.22f);
                g2.dispose();
            }
        };
        badgeCircle.setPreferredSize(new Dimension(46, 46));
        badgeCircle.setMinimumSize(new Dimension(46, 46));
        toast.add(badgeCircle, BorderLayout.WEST);

        // Text stack
        JPanel textStack = new JPanel();
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.setOpaque(false);

        JLabel kicker = new JLabel("\u2726 ACHIEVEMENT UNLOCKED");
        kicker.setFont(new Font(FONT_FAMILY, Font.BOLD, 10));
        kicker.setForeground(new Color(217, 119, 6)); // Amber-600

        JLabel nameLbl = new JLabel(badgeName != null ? badgeName : "Badge Unlocked");
        nameLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        nameLbl.setForeground(TEXT_PRIMARY);

        JLabel descLbl = new JLabel(badgeDesc != null ? badgeDesc : "");
        descLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        descLbl.setForeground(TEXT_SECONDARY);

        textStack.add(kicker);
        textStack.add(Box.createVerticalStrut(2));
        textStack.add(nameLbl);
        textStack.add(Box.createVerticalStrut(1));
        textStack.add(descLbl);
        toast.add(textStack, BorderLayout.CENTER);

        int toastW = Math.max(340, Math.min(420, (badgeName != null ? badgeName.length() : 10) * 8 + 200));
        int toastH = 72;
        int targetX = Math.max(16, layeredPane.getWidth() - toastW - 24);
        int targetY = 18;

        toast.setBounds(targetX + 30, targetY, toastW, toastH);
        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.moveToFront(toast);

        final int[] step = {0};
        javax.swing.Timer anim = new javax.swing.Timer(Anim.TICK_MS, null);
        anim.addActionListener(e -> {
            step[0]++;
            // Slide in (0 to 14 ticks ~ 220ms)
            if (step[0] <= 14) {
                float ease = Anim.easeOutCubic(step[0] / 14f);
                alpha[0] = ease;
                toast.setLocation(targetX + Math.round((1f - ease) * 30f), targetY);
                toast.repaint();
            } else if (step[0] <= 90) {
                // Sweep shine across the badge (from tick 15 to 70)
                float sp = (step[0] - 15) / 55f;
                shine[0] = -0.5f + 2.0f * Anim.easeInOutCubic(sp);
                toast.repaint();
            } else if (step[0] > 220 && step[0] <= 240) {
                // Fade out smoothly
                float p = (step[0] - 220) / 20f;
                alpha[0] = 1f - Anim.easeInOutCubic(p);
                toast.repaint();
            } else if (step[0] > 240) {
                anim.stop();
                layeredPane.remove(toast);
                layeredPane.repaint();
            }
        });
        anim.start();
    }

    // ================= AVATAR INITIALS CIRCLE =================
    public static JComponent createAvatarCircle(String name, Color bg, Color fg, int size) {
        String safe = (name == null || name.trim().isEmpty()) ? "?" : name.trim();
        String initials = safe.length() >= 2 ? safe.substring(0, 2).toUpperCase() : safe.toUpperCase();

        JComponent comp = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                g2.setColor(bg);
                g2.fill(new Ellipse2D.Float(0, 0, size, size));
                g2.setFont(new Font(FONT_FAMILY, Font.BOLD, Math.max(11, size / 3)));
                g2.setColor(fg);
                FontMetrics fm = g2.getFontMetrics();
                int x = (size - fm.stringWidth(initials)) / 2;
                int y = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initials, x, y);
                g2.dispose();
            }
        };
        comp.setPreferredSize(new Dimension(size, size));
        comp.setMinimumSize(new Dimension(size, size));
        comp.setMaximumSize(new Dimension(size, size));
        comp.setOpaque(false);
        return comp;
    }

    // ================= SUBTLE VECTOR ICON =================
    public static class VectorIcon extends JComponent {
        private final IconType type;
        private final Color strokeColor;
        private final Color badgeBg;
        private final int size;

        public VectorIcon(IconType type, Color strokeColor, Color badgeBg, int size) {
            this.type = type;
            this.strokeColor = strokeColor;
            this.badgeBg = badgeBg;
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            configureGraphics(g2);

            if (badgeBg != null) {
                g2.setColor(badgeBg);
                g2.fill(new RoundRectangle2D.Float(0, 0, size, size, 10, 10));
            }

            drawVectorGlyph(g2, type, strokeColor, size, badgeBg != null ? 0.25f : 0.14f);
            g2.dispose();
        }
    }

    public static void drawIcon(Graphics2D g2, IconType type, int cx, int cy, Color color, int size, float padRatio) {
        Graphics2D g = (Graphics2D) g2.create();
        g.translate(cx - size / 2, cy - size / 2);
        drawVectorGlyph(g, type, color, size, padRatio);
        g.dispose();
    }

    public static void drawVectorGlyph(Graphics2D g2, IconType type, Color strokeColor, int size, float padRatio) {
        configureGraphics(g2);

        float pad = size * padRatio;
        float s = size - pad * 2f;
        float cx = size / 2f;
        float cy = size / 2f;

        g2.setColor(strokeColor);
        g2.setStroke(new BasicStroke(Math.max(1.4f, size * 0.055f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (type) {
            case CAP: {
                Path2D roof = new Path2D.Float();
                roof.moveTo(cx, pad + s * 0.14f);
                roof.lineTo(pad + s, cy - s * 0.06f);
                roof.lineTo(cx, cy + s * 0.16f);
                roof.lineTo(pad, cy - s * 0.06f);
                roof.closePath();
                g2.draw(roof);
                Path2D base = new Path2D.Float();
                base.moveTo(pad + s * 0.22f, cy + s * 0.08f);
                base.lineTo(pad + s * 0.22f, pad + s * 0.78f);
                base.quadTo(cx, pad + s * 0.94f, pad + s * 0.78f, pad + s * 0.78f);
                base.lineTo(pad + s * 0.78f, cy + s * 0.08f);
                g2.draw(base);
                break;
            }
            case PLAY: {
                Path2D tri = new Path2D.Float();
                tri.moveTo(pad + s * 0.26f, pad + s * 0.16f);
                tri.lineTo(pad + s * 0.84f, cy);
                tri.lineTo(pad + s * 0.26f, pad + s * 0.84f);
                tri.closePath();
                g2.fill(tri);
                break;
            }
            case TROPHY:
            case AWARD: {
                g2.draw(new RoundRectangle2D.Float(pad + s * 0.22f, pad + s * 0.10f, s * 0.56f, s * 0.48f, 4, 4));
                g2.draw(new Arc2D.Float(pad + s * 0.04f, pad + s * 0.16f, s * 0.22f, s * 0.28f, 90, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Float(pad + s * 0.74f, pad + s * 0.16f, s * 0.22f, s * 0.28f, -90, 180, Arc2D.OPEN));
                g2.drawLine((int) cx, (int) (pad + s * 0.58f), (int) cx, (int) (pad + s * 0.84f));
                g2.drawLine((int) (pad + s * 0.28f), (int) (pad + s * 0.86f), (int) (pad + s * 0.72f), (int) (pad + s * 0.86f));
                break;
            }
            case CHECKLIST: {
                g2.draw(new RoundRectangle2D.Float(pad + s * 0.12f, pad + s * 0.08f, s * 0.76f, s * 0.84f, 3, 3));
                g2.drawLine((int) (pad + s * 0.28f), (int) (pad + s * 0.36f), (int) (pad + s * 0.38f), (int) (pad + s * 0.46f));
                g2.drawLine((int) (pad + s * 0.38f), (int) (pad + s * 0.46f), (int) (pad + s * 0.56f), (int) (pad + s * 0.28f));
                g2.drawLine((int) (pad + s * 0.28f), (int) (pad + s * 0.66f), (int) (pad + s * 0.72f), (int) (pad + s * 0.66f));
                break;
            }
            case CHART: {
                g2.drawLine((int) (pad + s * 0.10f), (int) (pad + s * 0.86f), (int) (pad + s * 0.90f), (int) (pad + s * 0.86f));
                g2.fill(new RoundRectangle2D.Float(pad + s * 0.18f, pad + s * 0.50f, s * 0.16f, s * 0.32f, 2, 2));
                g2.fill(new RoundRectangle2D.Float(pad + s * 0.44f, pad + s * 0.24f, s * 0.16f, s * 0.58f, 2, 2));
                g2.fill(new RoundRectangle2D.Float(pad + s * 0.70f, pad + s * 0.38f, s * 0.16f, s * 0.44f, 2, 2));
                break;
            }
            case PLUS: {
                g2.draw(new Ellipse2D.Float(pad + s * 0.06f, pad + s * 0.06f, s * 0.88f, s * 0.88f));
                g2.drawLine((int) cx, (int) (pad + s * 0.28f), (int) cx, (int) (pad + s * 0.72f));
                g2.drawLine((int) (pad + s * 0.28f), (int) cy, (int) (pad + s * 0.72f), (int) cy);
                break;
            }
            case BOOK:
            case SUBJECT: {
                g2.draw(new RoundRectangle2D.Float(pad + s * 0.12f, pad + s * 0.10f, s * 0.76f, s * 0.80f, 3, 3));
                g2.drawLine((int) (pad + s * 0.30f), (int) (pad + s * 0.12f), (int) (pad + s * 0.30f), (int) (pad + s * 0.88f));
                g2.drawLine((int) (pad + s * 0.44f), (int) (pad + s * 0.36f), (int) (pad + s * 0.74f), (int) (pad + s * 0.36f));
                g2.drawLine((int) (pad + s * 0.44f), (int) (pad + s * 0.56f), (int) (pad + s * 0.68f), (int) (pad + s * 0.56f));
                break;
            }
            case USERS:
            case USER: {
                g2.draw(new Ellipse2D.Float(cx - s * 0.18f, pad + s * 0.10f, s * 0.36f, s * 0.36f));
                g2.draw(new Arc2D.Float(pad + s * 0.12f, pad + s * 0.52f, s * 0.76f, s * 0.56f, 0, 180, Arc2D.OPEN));
                break;
            }
            case CLOCK: {
                g2.draw(new Ellipse2D.Float(pad + s * 0.08f, pad + s * 0.08f, s * 0.84f, s * 0.84f));
                g2.drawLine((int) cx, (int) cy, (int) cx, (int) (pad + s * 0.26f));
                g2.drawLine((int) cx, (int) cy, (int) (pad + s * 0.70f), (int) cy);
                break;
            }
            case SHIELD: {
                Path2D sh = new Path2D.Float();
                sh.moveTo(cx, pad + s * 0.06f);
                sh.lineTo(pad + s * 0.86f, pad + s * 0.24f);
                sh.lineTo(pad + s * 0.82f, pad + s * 0.60f);
                sh.quadTo(cx, pad + s * 0.96f, pad + s * 0.18f, pad + s * 0.60f);
                sh.lineTo(pad + s * 0.14f, pad + s * 0.24f);
                sh.closePath();
                g2.draw(sh);
                break;
            }
            case FLAG: {
                g2.drawLine((int) (pad + s * 0.22f), (int) (pad + s * 0.10f), (int) (pad + s * 0.22f), (int) (pad + s * 0.90f));
                Path2D flag = new Path2D.Float();
                flag.moveTo(pad + s * 0.22f, pad + s * 0.14f);
                flag.lineTo(pad + s * 0.84f, pad + s * 0.32f);
                flag.lineTo(pad + s * 0.22f, pad + s * 0.52f);
                flag.closePath();
                g2.draw(flag);
                break;
            }
            case DASHBOARD: {
                float gap = s * 0.12f;
                float box = (s - gap) / 2f;
                g2.draw(new RoundRectangle2D.Float(pad, pad, box, box, 2, 2));
                g2.draw(new RoundRectangle2D.Float(pad + box + gap, pad, box, box, 2, 2));
                g2.draw(new RoundRectangle2D.Float(pad, pad + box + gap, box, box, 2, 2));
                g2.draw(new RoundRectangle2D.Float(pad + box + gap, pad + box + gap, box, box, 2, 2));
                break;
            }
            case LOGOUT: {
                Path2D door = new Path2D.Float();
                door.moveTo(pad + s * 0.45f, pad + s * 0.12f);
                door.lineTo(pad + s * 0.14f, pad + s * 0.12f);
                door.lineTo(pad + s * 0.14f, pad + s * 0.88f);
                door.lineTo(pad + s * 0.45f, pad + s * 0.88f);
                g2.draw(door);
                g2.drawLine((int) (pad + s * 0.40f), (int) cy, (int) (pad + s * 0.88f), (int) cy);
                g2.drawLine((int) (pad + s * 0.68f), (int) (pad + s * 0.28f), (int) (pad + s * 0.88f), (int) cy);
                g2.drawLine((int) (pad + s * 0.68f), (int) (pad + s * 0.72f), (int) (pad + s * 0.88f), (int) cy);
                break;
            }
            case THEME: {
                g2.draw(new Ellipse2D.Float(pad + s * 0.16f, pad + s * 0.16f, s * 0.68f, s * 0.68f));
                g2.fill(new Arc2D.Float(pad + s * 0.16f, pad + s * 0.16f, s * 0.68f, s * 0.68f, 90, 180, Arc2D.PIE));
                break;
            }
            case CHECK: {
                g2.drawLine((int) (pad + s * 0.16f), (int) (pad + s * 0.52f), (int) (pad + s * 0.40f), (int) (pad + s * 0.76f));
                g2.drawLine((int) (pad + s * 0.40f), (int) (pad + s * 0.76f), (int) (pad + s * 0.86f), (int) (pad + s * 0.24f));
                break;
            }
            case LIST: {
                g2.drawLine((int) (pad + s * 0.16f), (int) (pad + s * 0.24f), (int) (pad + s * 0.84f), (int) (pad + s * 0.24f));
                g2.drawLine((int) (pad + s * 0.16f), (int) cy, (int) (pad + s * 0.84f), (int) cy);
                g2.drawLine((int) (pad + s * 0.16f), (int) (pad + s * 0.76f), (int) (pad + s * 0.84f), (int) (pad + s * 0.76f));
                break;
            }
            case MEDAL: {
                // Top V-ribbon
                Path2D rib = new Path2D.Float();
                rib.moveTo(pad + s * 0.24f, pad + s * 0.10f);
                rib.lineTo(cx, pad + s * 0.44f);
                rib.lineTo(pad + s * 0.76f, pad + s * 0.10f);
                g2.draw(rib);
                // Central medal disc
                float discSize = s * 0.54f;
                float discX = cx - discSize / 2f;
                float discY = pad + s * 0.36f;
                g2.draw(new Ellipse2D.Float(discX, discY, discSize, discSize));
                // Inner concentric accent circle
                float innerSize = s * 0.32f;
                g2.draw(new Ellipse2D.Float(cx - innerSize / 2f, discY + (discSize - innerSize) / 2f, innerSize, innerSize));
                break;
            }
            case FLAME: {
                // Outer flame contour
                Path2D flame = new Path2D.Float();
                flame.moveTo(cx, pad + s * 0.08f);
                flame.curveTo(pad + s * 0.90f, pad + s * 0.38f, pad + s * 0.86f, pad + s * 0.78f, cx, pad + s * 0.92f);
                flame.curveTo(pad + s * 0.14f, pad + s * 0.78f, pad + s * 0.10f, pad + s * 0.38f, cx, pad + s * 0.08f);
                flame.closePath();
                g2.draw(flame);
                // Inner flick core
                Path2D inner = new Path2D.Float();
                inner.moveTo(cx, pad + s * 0.48f);
                inner.curveTo(pad + s * 0.68f, pad + s * 0.64f, pad + s * 0.64f, pad + s * 0.84f, cx, pad + s * 0.88f);
                inner.curveTo(pad + s * 0.36f, pad + s * 0.84f, pad + s * 0.32f, pad + s * 0.64f, cx, pad + s * 0.48f);
                inner.closePath();
                g2.draw(inner);
                break;
            }
            case STAR: {
                // 5-pointed geometric star
                Path2D star = new Path2D.Float();
                float outerR = s * 0.46f;
                float innerR = s * 0.19f;
                for (int i = 0; i < 10; i++) {
                    double angle = (i * Math.PI / 5.0) - (Math.PI / 2.0);
                    float r = (i % 2 == 0) ? outerR : innerR;
                    float x = cx + (float) (r * Math.cos(angle));
                    float y = cy + (float) (r * Math.sin(angle));
                    if (i == 0) star.moveTo(x, y);
                    else star.lineTo(x, y);
                }
                star.closePath();
                g2.draw(star);
                break;
            }
            case BOLT: {
                // Lightning bolt
                Path2D bolt = new Path2D.Float();
                bolt.moveTo(pad + s * 0.55f, pad + s * 0.08f);
                bolt.lineTo(pad + s * 0.22f, pad + s * 0.52f);
                bolt.lineTo(pad + s * 0.48f, pad + s * 0.52f);
                bolt.lineTo(pad + s * 0.40f, pad + s * 0.92f);
                bolt.lineTo(pad + s * 0.78f, pad + s * 0.44f);
                bolt.lineTo(pad + s * 0.52f, pad + s * 0.44f);
                bolt.closePath();
                g2.draw(bolt);
                break;
            }
            case TARGET: {
                // Concentric circles + crosshairs
                g2.draw(new Ellipse2D.Float(pad + s * 0.10f, pad + s * 0.10f, s * 0.80f, s * 0.80f));
                g2.draw(new Ellipse2D.Float(pad + s * 0.28f, pad + s * 0.28f, s * 0.44f, s * 0.44f));
                g2.fill(new Ellipse2D.Float(cx - s * 0.08f, cy - s * 0.08f, s * 0.16f, s * 0.16f));
                // Crosshair tick marks
                g2.drawLine((int) cx, (int) pad, (int) cx, (int) (pad + s * 0.10f));
                g2.drawLine((int) cx, (int) (pad + s * 0.90f), (int) cx, (int) (pad + s));
                g2.drawLine((int) pad, (int) cy, (int) (pad + s * 0.10f), (int) cy);
                g2.drawLine((int) (pad + s * 0.90f), (int) cy, (int) (pad + s), (int) cy);
                break;
            }
            case CALENDAR: {
                // Calendar sheet
                g2.draw(new RoundRectangle2D.Float(pad + s * 0.12f, pad + s * 0.16f, s * 0.76f, s * 0.72f, 4, 4));
                // Header divider
                g2.drawLine((int) (pad + s * 0.12f), (int) (pad + s * 0.38f), (int) (pad + s * 0.88f), (int) (pad + s * 0.38f));
                // Top binding rings
                g2.drawLine((int) (pad + s * 0.32f), (int) (pad + s * 0.08f), (int) (pad + s * 0.32f), (int) (pad + s * 0.20f));
                g2.drawLine((int) (pad + s * 0.68f), (int) (pad + s * 0.08f), (int) (pad + s * 0.68f), (int) (pad + s * 0.20f));
                // Day grid dots
                float dotR = Math.max(1.5f, s * 0.04f);
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 3; c++) {
                        float dx = pad + s * (0.30f + c * 0.20f);
                        float dy = pad + s * (0.52f + r * 0.18f);
                        g2.fill(new Ellipse2D.Float(dx - dotR, dy - dotR, dotR * 2, dotR * 2));
                    }
                }
                break;
            }
        }
    }

    // ================= AIRY LIGHT TOP HEADER =================
    public static class GradientHeader extends JPanel {

        public GradientHeader(String titleText, String subtitleText) {
            this(titleText, subtitleText, BG_CARD, BG_CARD);
        }

        public GradientHeader(String titleText, String subtitleText, Color ignored1, Color ignored2) {
            setLayout(new BorderLayout(16, 0));
            setBackground(BG_CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                    new EmptyBorder(14, 24, 14, 24)
            ));

            JPanel textStack = new JPanel();
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            textStack.setOpaque(false);

            JLabel title = new JLabel(titleText);
            title.setFont(FONT_TITLE);
            title.setForeground(TEXT_PRIMARY);
            textStack.add(title);

            if (subtitleText != null && !subtitleText.isEmpty()) {
                JLabel sub = new JLabel(subtitleText);
                sub.setFont(FONT_SUBTITLE);
                sub.setForeground(TEXT_SECONDARY);
                sub.setBorder(new EmptyBorder(2, 0, 0, 0));
                textStack.add(sub);
            }

            add(textStack, BorderLayout.WEST);
        }
    }

    // ================= ROUNDED PANEL (BACKED BY GLASS STYLE) =================
    public static class RoundedPanel extends GlassPanel {

        public RoundedPanel(int radius, Color fillColor, Color borderColor) {
            super(Math.max(10, radius), fillColor, borderColor);
            // Keep inner tinted tiles lightweight without heavy outer shadows
            if (fillColor != null && !fillColor.equals(BG_CARD) && !fillColor.equals(GLASS_FILL)) {
                setShadowEnabled(false);
                setSpotlightEnabled(false);
                setGradientBorderEnabled(false);
            }
        }

        public RoundedPanel(int radius) {
            super(GlassPanel.DEFAULT_RADIUS, null, null);
        }
    }

    // ================= AURORA MODERN BUTTON =================
    public static class ModernButton extends JButton {
        private Color baseColor;
        private Color hoverColor;
        private Color borderColor;
        private boolean usePrimaryGradient = false;
        private boolean useAccentGradient = false;
        private boolean hovered = false;
        private final int radius;

        public ModernButton(String text, Color baseColor, Color hoverColor, Color textColor) {
            this(text, baseColor, hoverColor, textColor, null, 10);
        }

        public ModernButton(String text, Color baseColor, Color hoverColor, Color textColor, Color borderColor, int radius) {
            super(text);
            this.baseColor = baseColor;
            this.hoverColor = hoverColor;
            this.borderColor = borderColor;
            this.radius = radius > 28 ? radius : Math.max(8, Math.min(radius, 12));

            setFont(FONT_BUTTON);
            setForeground(textColor);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 8, 16));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        public void setButtonColors(Color baseColor, Color hoverColor) {
            this.baseColor = baseColor;
            this.hoverColor = hoverColor;
            this.usePrimaryGradient = false;
            this.useAccentGradient = false;
            repaint();
        }

        public void setGradientMode(boolean primaryGrad, boolean accentGrad) {
            this.usePrimaryGradient = primaryGrad;
            this.useAccentGradient = accentGrad;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            Shape pill = new RoundRectangle2D.Float(0, 0, w, h, radius, radius);

            if (!isEnabled()) {
                g2.setColor(BORDER);
                g2.fill(pill);
            } else if (usePrimaryGradient) {
                g2.setPaint(hovered
                        ? new LinearGradientPaint(0, 0, Math.max(1, w), h, new float[]{0f, 1f}, new Color[]{PRIMARY_HOVER, VIOLET_HOVER})
                        : primaryGradient(w, h));
                g2.fill(pill);
            } else if (useAccentGradient) {
                g2.setPaint(hovered
                        ? new LinearGradientPaint(0, 0, Math.max(1, w), h, new float[]{0f, 1f}, new Color[]{VIOLET_HOVER, PINK_HOVER})
                        : accentGradient(w, h));
                g2.fill(pill);
            } else {
                g2.setColor(hovered ? hoverColor : baseColor);
                g2.fill(pill);
            }

            if (borderColor != null && isEnabled()) {
                g2.setColor(hovered ? PRIMARY : borderColor);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ================= CIRCULAR SCORE GAUGE =================
    public static class ScoreGaugePanel extends JPanel {
        private final double targetPercentage;
        private double currentPercentage = 0.0;
        private final int score;
        private final int total;
        private final Color arcColor;
        private final Color trackColor;

        public ScoreGaugePanel(double percentage, int score, int total, boolean passed) {
            this.targetPercentage = Math.max(0.0, Math.min(100.0, percentage));
            this.score = score;
            this.total = total;
            this.arcColor = passed ? SUCCESS : DANGER;
            this.trackColor = BORDER;
            setPreferredSize(new Dimension(172, 172));
            setOpaque(false);

            Anim.animate(620, p -> {
                currentPercentage = targetPercentage * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                currentPercentage = targetPercentage;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            configureGraphics(g2);

            int size = Math.min(getWidth(), getHeight()) - 22;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            float stroke = 10f;

            g2.setColor(trackColor);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(x, y, size, size));

            double extent = -(currentPercentage / 100.0) * 360.0;
            g2.setColor(arcColor);
            g2.draw(new Arc2D.Double(x, y, size, size, 90, extent, Arc2D.OPEN));

            String pctText = String.format("%.0f%%", currentPercentage);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 30));
            g2.setColor(TEXT_PRIMARY);
            FontMetrics fmPct = g2.getFontMetrics();
            int pctX = (getWidth() - fmPct.stringWidth(pctText)) / 2;
            int pctY = getHeight() / 2 + 4;
            g2.drawString(pctText, pctX, pctY);

            String subText = score + " / " + total + " marks";
            g2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
            g2.setColor(TEXT_MUTED);
            FontMetrics fmSub = g2.getFontMetrics();
            int subX = (getWidth() - fmSub.stringWidth(subText)) / 2;
            g2.drawString(subText, subX, pctY + 20);

            g2.dispose();
        }
    }

    // ================= PASTEL AURORA SUBJECT BAR CHART (GRADIENT BARS + HOVER CROSSHAIR & VALUE BUBBLE) =================
    public static class SubjectBarChartPanel extends JPanel {
        private Map<String, Double> data = new LinkedHashMap<>();
        private double animProgress = 1.0;
        private int hoveredIndex = -1;
        private javax.swing.Timer animTimer;

        public SubjectBarChartPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(560, 175));

            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getPoint());
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoveredIndex != -1) {
                        hoveredIndex = -1;
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
        }

        private void updateHover(Point pt) {
            int count = data.size();
            if (count == 0) return;
            int leftPad = 42;
            int rightPad = 24;
            int chartW = Math.max(100, getWidth() - leftPad - rightPad);
            if (pt.x < leftPad || pt.x > leftPad + chartW) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
                return;
            }
            int slotW = Math.max(1, chartW / count);
            int idx = Math.max(0, Math.min(count - 1, (pt.x - leftPad) / slotW));
            if (idx != hoveredIndex) {
                hoveredIndex = idx;
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                repaint();
            }
        }

        public void setData(Map<String, Double> subjectAverages) {
            this.data = subjectAverages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(subjectAverages);
            this.hoveredIndex = -1;
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
            animProgress = 0.0;
            animTimer = Anim.animate(520, p -> {
                animProgress = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                animProgress = 1.0;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int leftPad = 42;
            int rightPad = 24;
            int topPad = 26;
            int bottomPad = 30;
            int chartW = Math.max(100, w - leftPad - rightPad);
            int chartH = Math.max(60, h - topPad - bottomPad);

            g2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
            for (int m : new int[]{0, 40, 100}) {
                int yLine = topPad + chartH - (int) (chartH * (m / 100.0));
                if (m == 40) {
                    g2.setColor(new Color(245, 158, 11, 160));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
                } else {
                    g2.setColor(BORDER);
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.drawLine(leftPad, yLine, leftPad + chartW, yLine);
                g2.setColor(m == 40 ? WARNING_TEXT : TEXT_MUTED);
                g2.drawString(m + "%", 6, yLine + 4);
            }

            if (data.isEmpty()) {
                g2.setFont(FONT_BODY);
                g2.setColor(TEXT_MUTED);
                String msg = "No exam data available yet.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, topPad + chartH / 2);
                g2.dispose();
                return;
            }

            Color[][] barGrads = {
                    {PRIMARY, VIOLET},
                    {VIOLET, PINK},
                    {SKY, PRIMARY},
                    {PINK, WARNING}
            };
            int count = data.size();
            int slotW = chartW / Math.max(1, count);
            int barW = Math.min(50, Math.max(24, slotW / 2));

            int idx = 0;
            String hovKey = null;
            double hovVal = 0.0;
            int hovCx = 0;
            int hovBy = 0;

            for (Map.Entry<String, Double> entry : data.entrySet()) {
                double pct = Math.max(0.0, Math.min(100.0, entry.getValue()));
                int barH = (int) Math.round(chartH * (pct / 100.0) * animProgress);
                int bx = leftPad + idx * slotW + (slotW - barW) / 2;
                int by = topPad + chartH - barH;
                boolean isHov = (idx == hoveredIndex);

                if (isHov) {
                    hovKey = entry.getKey();
                    hovVal = pct;
                    hovCx = bx + barW / 2;
                    hovBy = by;

                    // Vertical crosshair behind hovered bar
                    g2.setColor(Anim.withAlpha(PRIMARY, 70));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
                    g2.drawLine(hovCx, topPad, hovCx, topPad + chartH);
                }

                if (barH > 0) {
                    Color[] pair = barGrads[idx % barGrads.length];
                    if (isHov) {
                        paintColoredShadow(g2, bx, by, barW, barH, 8, pair[0], 4, 2);
                    }
                    g2.setPaint(new LinearGradientPaint(bx, by, bx, by + Math.max(1, barH),
                            new float[]{0f, 1f}, new Color[]{pair[0], pair[1]}));
                    g2.fill(new RoundRectangle2D.Float(bx, by, barW, barH, 8, 8));
                }

                if (!isHov) {
                    String valStr = String.format("%.1f%%", pct * animProgress);
                    g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
                    g2.setColor(TEXT_PRIMARY);
                    FontMetrics fmVal = g2.getFontMetrics();
                    g2.drawString(valStr, bx + (barW - fmVal.stringWidth(valStr)) / 2, Math.max(topPad - 4, by - 5));
                }

                g2.setFont(new Font(FONT_FAMILY, isHov ? Font.BOLD : Font.PLAIN, 12));
                g2.setColor(isHov ? PRIMARY : TEXT_SECONDARY);
                FontMetrics fmSub = g2.getFontMetrics();
                g2.drawString(entry.getKey(), bx + (barW - fmSub.stringWidth(entry.getKey())) / 2, topPad + chartH + 19);

                idx++;
            }

            // Floating frosted value bubble tooltip on hover
            if (hovKey != null) {
                String tip = hovKey + "  \u2022  " + String.format("%.1f%%", hovVal);
                g2.setFont(font(Font.BOLD, 11.5f));
                FontMetrics tfm = g2.getFontMetrics();
                int bw = tfm.stringWidth(tip) + 18;
                int bh = 26;
                int tx = Math.max(leftPad, Math.min(w - bw - 6, hovCx - bw / 2));
                int ty = Math.max(4, hovBy - bh - 8);

                paintColoredShadow(g2, tx, ty, bw, bh, 8f, PRIMARY, 3, 2);
                g2.setColor(new Color(255, 255, 255, 246));
                g2.fill(new RoundRectangle2D.Float(tx, ty, bw, bh, 8, 8));
                g2.setPaint(createPrimaryGradient(tx, ty, tx + bw, ty + bh));
                g2.setStroke(new BasicStroke(1.1f));
                g2.draw(new RoundRectangle2D.Float(tx + 0.5f, ty + 0.5f, bw - 1f, bh - 1f, 8, 8));
                g2.setColor(TEXT_PRIMARY);
                g2.drawString(tip, tx + 9, ty + (bh + tfm.getAscent() - tfm.getDescent()) / 2);
            }

            g2.dispose();
        }
    }

    // ================= PASTEL AURORA STUDENT TREND CHART (SELF-DRAWING LINE + HOVER CROSSHAIR & BUBBLE) =================
    public static class StudentTrendChartPanel extends JPanel {
        private List<String> labels = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
        private float lineProgress = 1.0f;
        private float areaAlpha = 1.0f;
        private int hoveredIndex = -1;
        private javax.swing.Timer animTimer;
        private javax.swing.Timer areaTimer;

        public StudentTrendChartPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(600, 155));

            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getPoint());
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoveredIndex != -1) {
                        hoveredIndex = -1;
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
        }

        private void updateHover(Point pt) {
            int n = values.size();
            if (n == 0) return;
            int leftPad = 42;
            int rightPad = 24;
            int chartW = Math.max(100, getWidth() - leftPad - rightPad);
            if (pt.x < leftPad - 12 || pt.x > leftPad + chartW + 12) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
                return;
            }
            int nearest = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double px = (n == 1) ? (leftPad + chartW / 2.0) : (leftPad + i * (chartW / (double) (n - 1)));
                double d = Math.abs(pt.x - px);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = i;
                }
            }
            if (nearest != hoveredIndex) {
                hoveredIndex = nearest;
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                repaint();
            }
        }

        public void setData(List<String> labels, List<Double> percentages) {
            this.labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
            this.values = percentages == null ? new ArrayList<>() : new ArrayList<>(percentages);
            this.hoveredIndex = -1;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            if (areaTimer != null && areaTimer.isRunning()) areaTimer.stop();
            lineProgress = 0.0f;
            areaAlpha = 0.0f;
            animTimer = Anim.animate(540, p -> {
                lineProgress = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                lineProgress = 1.0f;
                areaTimer = Anim.animate(320, p2 -> {
                    areaAlpha = Anim.easeOutCubic(p2);
                    repaint();
                }, () -> {
                    areaAlpha = 1.0f;
                    repaint();
                });
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int leftPad = 42;
            int rightPad = 24;
            int topPad = 24;
            int bottomPad = 26;
            int chartW = Math.max(100, w - leftPad - rightPad);
            int chartH = Math.max(50, h - topPad - bottomPad);

            g2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
            for (int m : new int[]{0, 40, 100}) {
                int yLine = topPad + chartH - (int) (chartH * (m / 100.0));
                if (m == 40) {
                    g2.setColor(new Color(245, 158, 11, 150));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
                } else {
                    g2.setColor(BORDER);
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.drawLine(leftPad, yLine, leftPad + chartW, yLine);
                g2.setColor(m == 40 ? WARNING_TEXT : TEXT_MUTED);
                g2.drawString(m + "%", 6, yLine + 4);
            }

            if (values.isEmpty()) {
                g2.setFont(FONT_BODY);
                g2.setColor(TEXT_MUTED);
                String msg = "Complete exams to see your score progression.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, topPad + chartH / 2);
                g2.dispose();
                return;
            }

            int n = values.size();
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = (n == 1) ? (leftPad + chartW / 2f) : (leftPad + i * (chartW / (float) (n - 1)));
                double pct = Math.max(0.0, Math.min(100.0, values.get(i)));
                ys[i] = topPad + chartH - (float) (chartH * (pct / 100.0));
            }

            // Soft gradient area fade-in after line draws
            if (n > 1 && areaAlpha > 0.01f) {
                Path2D area = new Path2D.Float();
                area.moveTo(xs[0], topPad + chartH);
                for (int i = 0; i < n; i++) area.lineTo(xs[i], ys[i]);
                area.lineTo(xs[n - 1], topPad + chartH);
                area.closePath();

                GradientPaint areaGrad = new GradientPaint(
                        0, topPad, Anim.withAlpha(PRIMARY, Math.round(52 * areaAlpha)),
                        0, topPad + chartH, Anim.withAlpha(PINK, 0));
                g2.setPaint(areaGrad);
                g2.fill(area);
            }

            // Progressive self-drawing gradient line (Indigo -> Violet -> Pink)
            if (n > 1 && lineProgress > 0.005f) {
                float scaled = lineProgress * (n - 1);
                int fullSegs = Math.min(n - 1, (int) Math.floor(scaled));
                float frac = scaled - fullSegs;

                Path2D line = new Path2D.Float();
                line.moveTo(xs[0], ys[0]);
                for (int i = 1; i <= fullSegs; i++) line.lineTo(xs[i], ys[i]);
                if (fullSegs < n - 1 && frac > 0f) {
                    line.lineTo(xs[fullSegs] + (xs[fullSegs + 1] - xs[fullSegs]) * frac,
                            ys[fullSegs] + (ys[fullSegs + 1] - ys[fullSegs]) * frac);
                }

                g2.setPaint(new LinearGradientPaint(xs[0], topPad, xs[n - 1], topPad,
                        new float[]{0f, 0.55f, 1f}, new Color[]{PRIMARY, VIOLET, PINK}));
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(line);
            }

            // Hover crosshair lines
            if (hoveredIndex >= 0 && hoveredIndex < n) {
                g2.setColor(Anim.withAlpha(PRIMARY, 95));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
                g2.drawLine(Math.round(xs[hoveredIndex]), topPad, Math.round(xs[hoveredIndex]), topPad + chartH);
                g2.drawLine(leftPad, Math.round(ys[hoveredIndex]), leftPad + chartW, Math.round(ys[hoveredIndex]));
            }

            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
            for (int i = 0; i < n; i++) {
                float threshold = (n == 1) ? 0f : (i / (float) (n - 1));
                if (lineProgress + 0.02f < threshold) continue;

                boolean passed = values.get(i) >= 40.0;
                boolean isHov = (i == hoveredIndex);
                if (isHov) {
                    g2.setColor(Anim.withAlpha(passed ? PRIMARY : DANGER, 45));
                    g2.fill(new Ellipse2D.Float(xs[i] - 10, ys[i] - 10, 20, 20));
                }
                g2.setColor(BG_CARD);
                g2.fill(new Ellipse2D.Float(xs[i] - (isHov ? 6 : 5), ys[i] - (isHov ? 6 : 5), isHov ? 12 : 10, isHov ? 12 : 10));
                g2.setColor(passed ? PRIMARY : DANGER);
                g2.fill(new Ellipse2D.Float(xs[i] - (isHov ? 4 : 3.5f), ys[i] - (isHov ? 4 : 3.5f), isHov ? 8 : 7, isHov ? 8 : 7));

                FontMetrics fm = g2.getFontMetrics();
                if (!isHov) {
                    String pctLbl = String.format("%.0f%%", values.get(i));
                    g2.setColor(TEXT_PRIMARY);
                    g2.drawString(pctLbl, xs[i] - fm.stringWidth(pctLbl) / 2f, Math.max(12f, ys[i] - 9f));
                }

                if (i < labels.size()) {
                    String xLbl = labels.get(i);
                    g2.setColor(isHov ? PRIMARY : TEXT_MUTED);
                    g2.drawString(xLbl, xs[i] - fm.stringWidth(xLbl) / 2f, topPad + chartH + 17f);
                }
            }

            // Floating hover value bubble
            if (hoveredIndex >= 0 && hoveredIndex < n) {
                String lbl = hoveredIndex < labels.size() ? labels.get(hoveredIndex) : ("Attempt #" + (hoveredIndex + 1));
                String tip = lbl + "  \u2022  " + String.format("%.1f%%", values.get(hoveredIndex));
                g2.setFont(font(Font.BOLD, 11.5f));
                FontMetrics tfm = g2.getFontMetrics();
                int bw = tfm.stringWidth(tip) + 18;
                int bh = 26;
                int tx = Math.max(leftPad, Math.min(w - bw - 6, Math.round(xs[hoveredIndex]) - bw / 2));
                int ty = Math.max(4, Math.round(ys[hoveredIndex]) - bh - 12);

                paintColoredShadow(g2, tx, ty, bw, bh, 8f, PRIMARY, 3, 2);
                g2.setColor(new Color(255, 255, 255, 246));
                g2.fill(new RoundRectangle2D.Float(tx, ty, bw, bh, 8, 8));
                g2.setPaint(createPrimaryGradient(tx, ty, tx + bw, ty + bh));
                g2.setStroke(new BasicStroke(1.1f));
                g2.draw(new RoundRectangle2D.Float(tx + 0.5f, ty + 0.5f, bw - 1f, bh - 1f, 8, 8));
                g2.setColor(TEXT_PRIMARY);
                g2.drawString(tip, tx + 9, ty + (bh + tfm.getAscent() - tfm.getDescent()) / 2);
            }

            g2.dispose();
        }
    }

    // ================= GRADIENT PROGRESS BAR =================
    public static class ModernProgressBar extends JComponent {
        private final int maximum;
        private int value = 0;
        private boolean useAccentGradient = false;

        public ModernProgressBar(int max) {
            this.maximum = Math.max(1, max);
            setPreferredSize(new Dimension(100, 8));
            setOpaque(false);
        }

        public void setValue(int value) {
            this.value = Math.max(0, Math.min(maximum, value));
            repaint();
        }

        public int getValue() {
            return value;
        }

        public void setUseAccentGradient(boolean useAccentGradient) {
            this.useAccentGradient = useAccentGradient;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            configureGraphics(g2);
            int w = getWidth();
            int h = getHeight();
            float arc = Math.min(h, 8f);
            g2.setColor(new Color(79, 70, 229, 24));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            int fillW = (int) Math.round((value * 1.0 / maximum) * w);
            if (fillW > 0) {
                g2.setPaint(useAccentGradient ? accentGradient(w, h) : primaryGradient(w, h));
                g2.fill(new RoundRectangle2D.Float(0, 0, fillW, h, arc, arc));
            }
            g2.dispose();
        }
    }

    // ================= BUTTON FACTORIES =================
    public static ModernButton primaryButton(String text) {
        ModernButton btn = new ModernButton(text, PRIMARY, PRIMARY_HOVER, Color.WHITE, null, 10);
        btn.setGradientMode(true, false);
        return btn;
    }

    public static ModernButton gradientPrimaryButton(String text) {
        return primaryButton(text);
    }

    public static ModernButton accentButton(String text) {
        ModernButton btn = new ModernButton(text, VIOLET, PINK, Color.WHITE, null, 10);
        btn.setGradientMode(false, true);
        return btn;
    }

    public static ModernButton gradientAccentButton(String text) {
        return accentButton(text);
    }

    public static ModernButton successButton(String text) {
        return new ModernButton(text, SUCCESS, SUCCESS_HOVER, Color.WHITE, null, 10);
    }

    public static ModernButton dangerButton(String text) {
        return new ModernButton(text, DANGER, DANGER_HOVER, Color.WHITE, null, 10);
    }

    public static ModernButton secondaryButton(String text) {
        return new ModernButton(text, BG_CARD, PRIMARY_SOFT, TEXT_PRIMARY, BORDER_STRONG, 10);
    }

    public static ModernButton headerGhostButton(String text) {
        return new ModernButton(text, BG_CARD, PRIMARY_SOFT, TEXT_PRIMARY, BORDER_STRONG, 10);
    }

    // ================= GLASS METRIC & ACTION CARDS =================
    public static JPanel createKpiCard(String labelText, String valueText, String subText, Color accent, Color softBg, IconType iconType) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(16, 20, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        label.setForeground(TEXT_SECONDARY);
        topRow.add(label, BorderLayout.WEST);

        if (iconType != null) {
            VectorIcon icon = new VectorIcon(iconType, accent != null ? accent : PRIMARY, softBg != null ? softBg : PRIMARY_SOFT, 32);
            topRow.add(icon, BorderLayout.EAST);
        }

        JLabel val = new JLabel(valueText);
        val.setFont(new Font(FONT_FAMILY, Font.BOLD, 28));
        val.setForeground(TEXT_PRIMARY);

        card.add(topRow, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);

        if (subText != null && !subText.isEmpty()) {
            JLabel sub = new JLabel(subText);
            sub.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
            sub.setForeground(TEXT_MUTED);
            card.add(sub, BorderLayout.SOUTH);
        }
        return card;
    }

    public static JPanel createProgressBarRow(String labelText, int current, int max, Color barColor) {
        JPanel row = new JPanel(new BorderLayout(0, 5));
        row.setOpaque(false);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel left = new JLabel(labelText);
        left.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        left.setForeground(TEXT_PRIMARY);
        JLabel right = new JLabel(current + " / " + max);
        right.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        right.setForeground(TEXT_MUTED);
        head.add(left, BorderLayout.WEST);
        head.add(right, BorderLayout.EAST);

        JComponent bar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(BORDER);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
                double ratio = max > 0 ? Math.min(1.0, Math.max(0.0, current / (double) max)) : 0.0;
                int fw = (int) Math.round(w * ratio);
                if (fw > 0) {
                    if (barColor == null || barColor.equals(PRIMARY)) {
                        g2.setPaint(primaryGradient(w, h));
                    } else {
                        g2.setColor(barColor);
                    }
                    g2.fill(new RoundRectangle2D.Float(0, 0, fw, h, h, h));
                }
                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(120, 7));

        row.add(head, BorderLayout.NORTH);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    public static javax.swing.table.TableCellRenderer createStatusPillRenderer() {
        return new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String text = value == null ? "" : value.toString();
                boolean pass = "Pass".equalsIgnoreCase(text);
                boolean fail = "Fail".equalsIgnoreCase(text);

                int hov = getHoveredRow(tbl);
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
                cell.setOpaque(true);
                if (isSelected || row == hov) {
                    cell.setBackground(PRIMARY_SOFT);
                } else {
                    cell.setBackground(row % 2 == 0 ? BG_CARD : new Color(249, 250, 253));
                }

                if (pass || fail) {
                    JLabel pill = createBadge(
                            pass ? "Pass" : "Fail",
                            pass ? SUCCESS_SOFT : DANGER_SOFT,
                            pass ? SUCCESS_TEXT : DANGER_TEXT
                    );
                    pill.setBorder(new EmptyBorder(2, 10, 2, 10));
                    cell.add(pill);
                } else {
                    JLabel lbl = new JLabel(text);
                    lbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
                    lbl.setForeground(TEXT_PRIMARY);
                    cell.add(lbl);
                }
                return cell;
            }
        };
    }

    public static int getHoveredRow(JTable table) {
        if (table == null) return -1;
        Object val = table.getClientProperty("ui.hoveredRow");
        return (val instanceof Integer) ? (Integer) val : -1;
    }

    public static JPanel createActionCard(
            String badgeTag, String titleText, String description, Color accentColor, Color softTint, Runnable onClick) {
        return createActionCard(IconType.SUBJECT, badgeTag, titleText, description, accentColor, softTint, onClick);
    }

    public static JPanel createActionCard(
            IconType iconType, String badgeTag, String titleText, String description,
            Color accentColor, Color softTint, Runnable onClick) {
        RoundedPanel card = new RoundedPanel(16);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(18, 20, 20, 20));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel title = new JLabel(titleText);
        title.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        title.setForeground(TEXT_PRIMARY);

        JLabel tag = createBadge(badgeTag, PRIMARY_SOFT, PRIMARY);

        topRow.add(title, BorderLayout.WEST);
        topRow.add(tag, BorderLayout.EAST);

        JLabel desc = new JLabel("<html>" + description + "</html>");
        desc.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        desc.setForeground(TEXT_SECONDARY);
        desc.setVerticalAlignment(SwingConstants.TOP);

        card.add(topRow, BorderLayout.NORTH);
        card.add(desc, BorderLayout.CENTER);

        MouseAdapter interactive = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorderColor(PRIMARY);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorderColor(null);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        };

        card.addMouseListener(interactive);
        return card;
    }

    public static JLabel createBadge(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                configureGraphics(g2);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 999, 999));
                Color borderTint = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 35);
                g2.setColor(borderTint);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 999, 999));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setBackground(bg);
        label.setForeground(fg);
        label.setFont(FONT_BADGE);
        label.setBorder(new EmptyBorder(3, 10, 3, 10));
        return label;
    }

    public static JLabel createDifficultyBadge(String difficulty) {
        String norm = (difficulty == null || difficulty.trim().isEmpty()) ? "MEDIUM" : difficulty.trim().toUpperCase();
        if ("EASY".equals(norm)) {
            return createBadge("EASY", SUCCESS_SOFT, SUCCESS_TEXT);
        }
        if ("HARD".equals(norm)) {
            return createBadge("HARD", DANGER_SOFT, DANGER_TEXT);
        }
        return createBadge("MEDIUM", WARNING_SOFT, WARNING_TEXT);
    }

    // ================= NATIVE FLATLAF INPUTS =================
    public static class RoundedTextField extends JTextField {
        public RoundedTextField() {
            this("");
        }

        public RoundedTextField(String placeholder) {
            setFont(FONT_BODY);
            setPreferredSize(new Dimension(240, 36));
            putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
            if (placeholder != null && !placeholder.isEmpty()) {
                putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
            }
        }
    }

    public static class RoundedPasswordField extends JPasswordField {
        public RoundedPasswordField() {
            this("");
        }

        public RoundedPasswordField(String placeholder) {
            setFont(FONT_BODY);
            setPreferredSize(new Dimension(240, 36));
            putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10; showRevealButton: true");
            if (placeholder != null && !placeholder.isEmpty()) {
                putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
            }
        }
    }

    public static JPanel createPasswordInputWithToggle(RoundedPasswordField passField) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(passField, BorderLayout.CENTER);
        return wrap;
    }

    public static JButton createLinkButton(String prefixText, String highlightText) {
        JButton link = new JButton("<html><span style='color:#475569;'>" + prefixText
                + " </span><span style='color:#4F46E5;'><b>" + highlightText + "</b></span></html>");
        link.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        link.setContentAreaFilled(false);
        link.setBorderPainted(false);
        link.setFocusPainted(false);
        link.setOpaque(false);
        link.setCursor(new Cursor(Cursor.HAND_CURSOR));
        link.setBorder(new EmptyBorder(4, 4, 4, 4));
        return link;
    }

    public static RoundedTextField createTextField() {
        return new RoundedTextField();
    }

    public static RoundedTextField createTextField(String placeholder) {
        return new RoundedTextField(placeholder);
    }

    public static RoundedPasswordField createPasswordField() {
        return new RoundedPasswordField();
    }

    public static RoundedPasswordField createPasswordField(String placeholder) {
        return new RoundedPasswordField(placeholder);
    }

    public static <T> JComboBox<T> createComboBox(T[] items) {
        JComboBox<T> box = items == null ? new JComboBox<>() : new JComboBox<>(items);
        box.setFont(FONT_BODY);
        box.setPreferredSize(new Dimension(190, 36));
        box.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        return box;
    }

    public static class ModernTabbedPane extends JTabbedPane {
        public ModernTabbedPane() {
            setFont(FONT_LABEL);
            putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE, "underlined");
            putClientProperty(FlatClientProperties.TABBED_PANE_SHOW_CONTENT_SEPARATOR, true);
        }
    }

    public static JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_LABEL);
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(40);
        table.setFillsViewportHeight(true);
        table.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(241, 245, 249));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(PRIMARY_SOFT);
        table.setSelectionForeground(PRIMARY);
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.putClientProperty("ui.hoveredRow", -1);

        // Install automatic light-indigo row hover tracking on every styled table
        if (table.getClientProperty("ui.hoverInstalled") == null) {
            table.putClientProperty("ui.hoverInstalled", Boolean.TRUE);
            table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int r = table.rowAtPoint(e.getPoint());
                    if (r != getHoveredRow(table)) {
                        table.putClientProperty("ui.hoveredRow", r);
                        table.repaint();
                    }
                }
            });
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (getHoveredRow(table) != -1) {
                        table.putClientProperty("ui.hoveredRow", -1);
                        table.repaint();
                    }
                }
            });
        }

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(TEXT_SECONDARY);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
    }

    public static Color tableRowBackground(JTable table, int row, boolean isSelected) {
        if (isSelected || row == getHoveredRow(table)) {
            return PRIMARY_SOFT;
        }
        return (row % 2 == 0) ? BG_CARD : new Color(249, 250, 253);
    }

    public static JScrollPane styleScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_CARD);
        applyModernScrollBars(sp);
        return sp;
    }

    public static void applyModernScrollBars(JScrollPane sp) {
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        String thinStyle = "width: 8; thumbArc: 999; trackArc: 999; showButtons: false; thumbInsets: 2,2,2,2";
        sp.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, thinStyle);
        sp.getHorizontalScrollBar().putClientProperty(FlatClientProperties.STYLE, thinStyle);
    }

    // ================= VIEWPORT-FITTING SCROLLABLE PANEL =================
    public static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel(LayoutManager layout) {
            super(layout);
        }

        public ScrollablePanel() {
            super();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
