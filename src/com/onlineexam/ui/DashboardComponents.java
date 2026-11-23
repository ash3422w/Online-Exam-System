package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable Pastel Aurora Light dashboard widgets:
 * - BentoCard (hover lift 4px + stronger colored shadow)
 * - AnimatedBorderHeroCard (animated indigo->violet->pink gradient border)
 * - FrostedSidebar (light frosted white rail with sliding indigo active pill)
 * - SparklinePanel (self-drawing trend line, then area fade-in)
 * - CircularBestRing (personal-best circular progress ring)
 * - SkeletonShimmerPanel (light gray sweeping shimmer placeholder)
 * - TransitionContainer (cross-fade + slide page transition)
 */
public final class DashboardComponents {

    private DashboardComponents() {
    }

    public static String timeOfDayGreeting(String name) {
        int hour = LocalTime.now().getHour();
        String prefix;
        if (hour < 12) {
            prefix = "Good morning";
        } else if (hour < 17) {
            prefix = "Good afternoon";
        } else {
            prefix = "Good evening";
        }
        String safe = (name == null || name.trim().isEmpty()) ? "Student" : name.trim();
        return prefix + ", " + safe;
    }

    // ================= STAGGERED BENTO ENTRANCE (RISE 20px + FADE IN, 60ms APART) =================

    public static void staggerRiseFade(List<? extends GlassPanel> cards, int stepDelayMs) {
        if (cards == null) return;
        for (int i = 0; i < cards.size(); i++) {
            final GlassPanel card = cards.get(i);
            card.setAlpha(0f);
            card.setSlideOffset(0, 20);
            int delay = Math.max(16, i * stepDelayMs);
            Timer starter = new Timer(delay, e -> {
                ((Timer) e.getSource()).stop();
                Anim.slideIn(card, Anim.Direction.UP, 380, 20);
            });
            starter.setRepeats(false);
            starter.start();
        }
    }

    // ================= BENTO CARD (4px HOVER LIFT + STRONGER COLORED SHADOW) =================

    public static class BentoCard extends GlassPanel {
        private float liftProgress = 0f;
        private Timer liftTimer;

        public BentoCard() {
            super(14);
        }

        public BentoCard(int radius) {
            super(radius);
        }

        @Override
        protected void onHoverChanged(boolean hovered) {
            float target = hovered ? 1f : 0f;
            if (liftTimer != null && liftTimer.isRunning()) {
                liftTimer.stop();
            }
            final float start = liftProgress;
            liftTimer = Anim.animate(180, p -> {
                liftProgress = start + (target - start) * Anim.easeOutCubic(p);
                hoverLiftY = -Math.round(4f * liftProgress);
                shadowBoost = 1.0f + 0.55f * liftProgress;
                repaint();
            }, () -> {
                liftProgress = target;
                hoverLiftY = -Math.round(4f * liftProgress);
                shadowBoost = 1.0f + 0.55f * liftProgress;
                repaint();
            });
        }
    }

    // ================= HERO CARD WITH ANIMATED INDIGO->VIOLET->PINK BORDER =================

    public static class AnimatedBorderHeroCard extends BentoCard {
        private double angleRad = 0.0;
        private final Timer borderTimer;

        public AnimatedBorderHeroCard() {
            super(16);
            setGradientBorderEnabled(false); // Drawn custom with animated rotation below
            borderTimer = new Timer(33, e -> {
                if (!isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                angleRad += 0.045;
                if (angleRad > Math.PI * 2.0) {
                    angleRad -= Math.PI * 2.0;
                }
                repaint();
            });
            borderTimer.setCoalesce(true);

            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        if (!borderTimer.isRunning()) borderTimer.start();
                    } else {
                        borderTimer.stop();
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (isShowing() && !borderTimer.isRunning()) {
                borderTimer.start();
            }
        }

        @Override
        public void removeNotify() {
            borderTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float padX = shadowEnabled ? 4f : 1f;
            float padTop = shadowEnabled ? 5f : 1f;
            float padBottom = shadowEnabled ? 8f : 1f;
            float x = padX;
            float y = padTop;
            float cw = Math.max(4f, w - padX * 2f);
            float ch = Math.max(4f, h - padTop - padBottom);
            float arc = Math.min(radius, Math.min(cw, ch));

            float cx = x + cw / 2f;
            float cy = y + ch / 2f;
            float r = (float) Math.hypot(cw, ch) * 0.5f;
            float x1 = cx + (float) Math.cos(angleRad) * r;
            float y1 = cy + (float) Math.sin(angleRad) * r;
            float x2 = cx - (float) Math.cos(angleRad) * r;
            float y2 = cy - (float) Math.sin(angleRad) * r;

            LinearGradientPaint animatedBorder = new LinearGradientPaint(
                    x1, y1, x2, y2,
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{UITheme.PRIMARY, UITheme.VIOLET, UITheme.PINK}
            );
            g2.setPaint(animatedBorder);
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(new RoundRectangle2D.Float(x + 1f, y + 1f, cw - 2f, ch - 2f, arc, arc));

            g2.dispose();
        }
    }

    // ================= LIGHT FROSTED-WHITE SIDEBAR WITH SLIDING INDIGO PILL =================

    public static class FrostedSidebar extends JPanel {

        private final NavListPanel navList;
        private final List<NavItemButton> items = new ArrayList<>();
        private int activeIndex = 0;

        public FrostedSidebar(String portalSubtitle, String userName, String userSub, Runnable onSignOut) {
            super(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(240, 720));
            setBorder(new EmptyBorder(20, 14, 18, 14));

            // Brand header (Light theme: dark text + Indigo->Violet gradient cap badge)
            JPanel brandBlock = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            brandBlock.setOpaque(false);
            brandBlock.setBorder(new EmptyBorder(2, 6, 22, 4));

            JComponent crest = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.configureGraphics(g2);
                    UITheme.paintColoredShadow(g2, 1, 1, 34, 34, 10, UITheme.PRIMARY, 3, 2);
                    g2.setPaint(UITheme.createPrimaryGradient(1, 1, 35, 35));
                    g2.fill(new RoundRectangle2D.Float(1, 1, 34, 34, 10, 10));
                    Icon cap = UITheme.createSwingIcon(UITheme.IconType.CAP, Color.WHITE, 19);
                    cap.paintIcon(this, g2, 9, 9);
                    g2.dispose();
                }
            };
            crest.setPreferredSize(new Dimension(38, 40));

            JPanel brandText = new JPanel();
            brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
            brandText.setOpaque(false);

            JLabel appTitle = new JLabel("Online Exam");
            appTitle.setFont(UITheme.font(Font.BOLD, 15f));
            appTitle.setForeground(UITheme.TEXT_PRIMARY);

            JLabel tag = new JLabel(portalSubtitle);
            tag.setFont(UITheme.font(Font.PLAIN, 11.5f));
            tag.setForeground(UITheme.TEXT_MUTED);

            brandText.add(appTitle);
            brandText.add(tag);
            brandBlock.add(crest);
            brandBlock.add(brandText);

            // Center navigation list with animated sliding indigo-tinted pill
            JPanel navWrap = new JPanel(new BorderLayout(0, 8));
            navWrap.setOpaque(false);

            JLabel sectionLbl = new JLabel("NAVIGATION");
            sectionLbl.setFont(UITheme.font(Font.BOLD, 10f));
            sectionLbl.setForeground(UITheme.TEXT_MUTED);
            sectionLbl.setBorder(new EmptyBorder(0, 14, 4, 0));

            navList = new NavListPanel();
            navWrap.add(sectionLbl, BorderLayout.NORTH);
            navWrap.add(navList, BorderLayout.CENTER);

            // Footer: Light user profile card + Sign Out (ZERO dark mode toggle)
            JPanel footer = new JPanel();
            footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
            footer.setOpaque(false);
            footer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.GLASS_SLATE_BORDER),
                    new EmptyBorder(14, 4, 0, 4)
            ));

            JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            profileRow.setOpaque(false);
            profileRow.setBorder(new EmptyBorder(0, 4, 12, 4));
            profileRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            String displayUser = (userName == null || userName.isEmpty()) ? "Student" : userName;
            profileRow.add(UITheme.createAvatarCircle(displayUser, UITheme.PRIMARY, Color.WHITE, 34));

            JPanel pText = new JPanel(new GridLayout(2, 1, 0, 1));
            pText.setOpaque(false);
            JLabel uName = new JLabel(displayUser);
            uName.setFont(UITheme.font(Font.BOLD, 12.5f));
            uName.setForeground(UITheme.TEXT_PRIMARY);
            JLabel uSub = new JLabel(userSub == null ? "" : userSub);
            uSub.setFont(UITheme.font(Font.PLAIN, 11f));
            uSub.setForeground(UITheme.TEXT_MUTED);
            pText.add(uName);
            pText.add(uSub);
            profileRow.add(pText);

            NavItemButton signOutBtn = new NavItemButton(UITheme.IconType.LOGOUT, "Sign Out", false);
            signOutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            signOutBtn.addActionListener(e -> {
                if (onSignOut != null) onSignOut.run();
            });

            footer.add(profileRow);
            footer.add(signOutBtn);

            add(brandBlock, BorderLayout.NORTH);
            add(navWrap, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
        }

        public void addNavItem(UITheme.IconType icon, String label, boolean active, Runnable onClick) {
            int idx = items.size();
            NavItemButton btn = new NavItemButton(icon, label, active);
            btn.addActionListener(e -> {
                setActiveIndex(idx);
                if (onClick != null) {
                    // Let the sliding pill glide smoothly for a brief beat before executing action
                    Timer t = new Timer(130, ev -> {
                        ((Timer) ev.getSource()).stop();
                        onClick.run();
                    });
                    t.setRepeats(false);
                    t.start();
                }
            });
            items.add(btn);
            navList.addItemButton(btn);
            if (active) {
                activeIndex = idx;
                navList.snapPillTo(idx);
            }
        }

        public void setActiveIndex(int index) {
            if (index < 0 || index >= items.size()) return;
            activeIndex = index;
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setActive(i == activeIndex);
            }
            navList.animatePillTo(activeIndex);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            int w = getWidth();
            int h = getHeight();

            // Crisp white sidebar panel
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            // Crisp hairline right divider #E2E8F0
            g2.setColor(UITheme.BORDER);
            g2.drawLine(w - 1, 0, w - 1, h);

            g2.dispose();
            super.paintComponent(g);
        }

        private static class NavListPanel extends JPanel {
            private final List<NavItemButton> buttons = new ArrayList<>();
            private float pillY = 0f;
            private float pillH = 40f;
            private boolean pillInitialized = false;
            private Timer pillTimer;

            NavListPanel() {
                setOpaque(false);
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            }

            void addItemButton(NavItemButton btn) {
                btn.setAlignmentX(Component.LEFT_ALIGNMENT);
                buttons.add(btn);
                add(btn);
                add(Box.createVerticalStrut(4));
            }

            void snapPillTo(int index) {
                SwingUtilities.invokeLater(() -> {
                    if (index >= 0 && index < buttons.size()) {
                        Rectangle r = buttons.get(index).getBounds();
                        if (r.height > 0) {
                            pillY = r.y;
                            pillH = r.height;
                            pillInitialized = true;
                            repaint();
                        }
                    }
                });
            }

            void animatePillTo(int index) {
                if (index < 0 || index >= buttons.size()) return;
                Rectangle target = buttons.get(index).getBounds();
                if (target.height <= 0) return;

                if (!pillInitialized) {
                    pillY = target.y;
                    pillH = target.height;
                    pillInitialized = true;
                    repaint();
                    return;
                }
                if (pillTimer != null && pillTimer.isRunning()) {
                    pillTimer.stop();
                }
                final float startY = pillY;
                final float endY = target.y;
                pillH = target.height;
                pillTimer = Anim.animate(240, p -> {
                    pillY = startY + (endY - startY) * Anim.easeInOutCubic(p);
                    repaint();
                }, () -> {
                    pillY = endY;
                    repaint();
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (buttons.isEmpty()) return;

                if (!pillInitialized) {
                    for (NavItemButton b : buttons) {
                        if (b.isActive() && b.getHeight() > 0) {
                            pillY = b.getY();
                            pillH = b.getHeight();
                            pillInitialized = true;
                            break;
                        }
                    }
                }
                if (!pillInitialized) return;

                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);

                int w = getWidth();
                // Sliding indigo-tinted pill behind active button
                g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 28));
                g2.fill(new RoundRectangle2D.Float(2, pillY, w - 4, pillH, 10, 10));

                g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 55));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(2.5f, pillY + 0.5f, w - 5f, pillH - 1f, 10, 10));

                // Gradient vertical bar on left edge of active pill
                g2.setPaint(UITheme.createPrimaryGradient(2, pillY + 7, 5, pillY + pillH - 7));
                g2.fill(new RoundRectangle2D.Float(2, pillY + 8, 3.5f, pillH - 16, 3, 3));

                g2.dispose();
            }
        }

        private static class NavItemButton extends JButton {
            private final UITheme.IconType iconType;
            private boolean active;
            private float hoverProgress = 0f;
            private Timer hoverTimer;

            NavItemButton(UITheme.IconType iconType, String text, boolean active) {
                super(text);
                this.iconType = iconType;
                this.active = active;
                setHorizontalAlignment(SwingConstants.LEFT);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                setBorder(new EmptyBorder(10, 14, 10, 12));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                setPreferredSize(new Dimension(210, 40));
                updateVisuals();

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { animateHover(1f); }
                    @Override public void mouseExited(MouseEvent e) { animateHover(0f); }
                });
            }

            boolean isActive() {
                return active;
            }

            void setActive(boolean active) {
                this.active = active;
                updateVisuals();
                repaint();
            }

            private void updateVisuals() {
                setFont(UITheme.font(active ? Font.BOLD : Font.PLAIN, 13f));
                Color fg = active ? UITheme.PRIMARY : UITheme.TEXT_SECONDARY;
                setForeground(fg);
                setIcon(UITheme.createSwingIcon(iconType, active ? UITheme.PRIMARY : UITheme.TEXT_MUTED, 16));
                setIconTextGap(11);
            }

            private void animateHover(float target) {
                if (hoverTimer != null && hoverTimer.isRunning()) hoverTimer.stop();
                final float start = hoverProgress;
                hoverTimer = Anim.animate(150, p -> {
                    hoverProgress = start + (target - start) * Anim.easeOutCubic(p);
                    if (!active) {
                        Color fg = blend(UITheme.TEXT_SECONDARY, UITheme.PRIMARY, hoverProgress);
                        setForeground(fg);
                        setIcon(UITheme.createSwingIcon(iconType, blend(UITheme.TEXT_MUTED, UITheme.PRIMARY, hoverProgress), 16));
                    }
                    repaint();
                }, () -> {
                    hoverProgress = target;
                    repaint();
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);

                if (!active && hoverProgress > 0.01f) {
                    g2.setColor(Anim.withAlpha(UITheme.PRIMARY, Math.round(16 * hoverProgress)));
                    g2.fill(new RoundRectangle2D.Float(2, 0, getWidth() - 4, getHeight(), 10, 10));
                }

                // Subtle 3px horizontal hover nudge for tactile feedback
                int nudgeX = Math.round(3f * hoverProgress);
                if (nudgeX > 0) {
                    g2.translate(nudgeX, 0);
                }
                super.paintComponent(g2);
                g2.dispose();
            }
        }
    }

    // ================= SELF-DRAWING SPARKLINE (LINE DRAWS FIRST, THEN AREA FADES IN) =================

    public static class SparklinePanel extends JPanel {
        private List<String> labels = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
        private float lineProgress = 0f;
        private float areaAlpha = 0f;
        private Timer phase1Timer;
        private Timer phase2Timer;

        public SparklinePanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(420, 132));
        }

        public void setData(List<String> labels, List<Double> percentages) {
            this.labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
            this.values = percentages == null ? new ArrayList<>() : new ArrayList<>(percentages);
            replay();
        }

        public void replay() {
            if (phase1Timer != null && phase1Timer.isRunning()) phase1Timer.stop();
            if (phase2Timer != null && phase2Timer.isRunning()) phase2Timer.stop();

            lineProgress = 0f;
            areaAlpha = 0f;
            if (values.isEmpty()) {
                repaint();
                return;
            }

            // Phase 1: Draw trend line smoothly left-to-right (560ms)
            phase1Timer = Anim.animate(560, p -> {
                lineProgress = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                lineProgress = 1f;
                // Phase 2: Fade in the translucent gradient area fill beneath the curve (380ms)
                phase2Timer = Anim.animate(380, p2 -> {
                    areaAlpha = Anim.easeOutCubic(p2);
                    repaint();
                }, () -> {
                    areaAlpha = 1f;
                    repaint();
                });
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int left = 36;
            int right = 18;
            int top = 18;
            int bottom = 24;
            int cw = Math.max(80, w - left - right);
            int ch = Math.max(40, h - top - bottom);

            // Reference lines (0%, 40% pass line, 100%)
            g2.setFont(UITheme.font(Font.PLAIN, 10f));
            for (int m : new int[]{0, 40, 100}) {
                int y = top + ch - (int) Math.round(ch * (m / 100.0));
                if (m == 40) {
                    g2.setColor(Anim.withAlpha(UITheme.WARNING, 130));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
                } else {
                    g2.setColor(Anim.withAlpha(UITheme.TEXT_MUTED, 45));
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.drawLine(left, y, left + cw, y);
                g2.setColor(m == 40 ? UITheme.WARNING_TEXT : UITheme.TEXT_MUTED);
                g2.drawString(m + "%", 4, y + 3);
            }

            if (values.isEmpty()) {
                g2.setFont(UITheme.font(Font.PLAIN, 12.5f));
                g2.setColor(UITheme.TEXT_MUTED);
                String msg = "Complete an exam to see your score progression";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, top + ch / 2 + 4);
                g2.dispose();
                return;
            }

            int n = values.size();
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = (n == 1) ? (left + cw / 2f) : (left + i * (cw / (float) (n - 1)));
                double pct = Math.max(0.0, Math.min(100.0, values.get(i)));
                ys[i] = top + ch - (float) (ch * (pct / 100.0));
            }

            // Phase 2: Fading translucent area fill under full curve
            if (n > 1 && areaAlpha > 0.01f) {
                Path2D area = new Path2D.Float();
                area.moveTo(xs[0], top + ch);
                for (int i = 0; i < n; i++) area.lineTo(xs[i], ys[i]);
                area.lineTo(xs[n - 1], top + ch);
                area.closePath();

                int topAlpha = Math.round(58f * areaAlpha);
                GradientPaint fillGrad = new GradientPaint(
                        0, top, Anim.withAlpha(UITheme.PRIMARY, topAlpha),
                        0, top + ch, Anim.withAlpha(UITheme.VIOLET, 0)
                );
                g2.setPaint(fillGrad);
                g2.fill(area);
            }

            // Phase 1: Progressive left-to-right drawing of the trend line
            if (n > 1 && lineProgress > 0.005f) {
                float totalSegs = n - 1;
                float scaled = lineProgress * totalSegs;
                int fullSegs = Math.min(n - 1, (int) Math.floor(scaled));
                float frac = scaled - fullSegs;

                Path2D line = new Path2D.Float();
                line.moveTo(xs[0], ys[0]);
                for (int i = 1; i <= fullSegs; i++) {
                    line.lineTo(xs[i], ys[i]);
                }
                if (fullSegs < n - 1 && frac > 0f) {
                    float tipX = xs[fullSegs] + (xs[fullSegs + 1] - xs[fullSegs]) * frac;
                    float tipY = ys[fullSegs] + (ys[fullSegs + 1] - ys[fullSegs]) * frac;
                    line.lineTo(tipX, tipY);
                }

                g2.setPaint(UITheme.createPrimaryGradient(xs[0], top, xs[n - 1], top));
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(line);
            }

            // Data points & labels appear progressively as line reaches them
            g2.setFont(UITheme.font(Font.BOLD, 10.5f));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < n; i++) {
                float threshold = (n == 1) ? 0f : (i / (float) (n - 1));
                if (lineProgress + 0.02f < threshold) continue;

                boolean passed = values.get(i) >= 40.0;
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Float(xs[i] - 5f, ys[i] - 5f, 10f, 10f));
                g2.setColor(passed ? UITheme.PRIMARY : UITheme.DANGER);
                g2.fill(new Ellipse2D.Float(xs[i] - 3.3f, ys[i] - 3.3f, 6.6f, 6.6f));

                String pctStr = String.format("%.0f%%", values.get(i));
                g2.setColor(UITheme.TEXT_PRIMARY);
                g2.drawString(pctStr, xs[i] - fm.stringWidth(pctStr) / 2f, Math.max(12f, ys[i] - 8f));

                if (i < labels.size()) {
                    String lbl = labels.get(i);
                    if (lbl.length() > 7) lbl = lbl.substring(0, 7);
                    g2.setColor(UITheme.TEXT_MUTED);
                    g2.drawString(lbl, xs[i] - fm.stringWidth(lbl) / 2f, top + ch + 16f);
                }
            }

            g2.dispose();
        }
    }

    // ================= CIRCULAR PERSONAL BEST RING (FOR AVAILABLE EXAM CARDS) =================

    public static class CircularBestRing extends JComponent {
        private final boolean attempted;
        private final double targetPct;
        private double animatedPct = 0.0;

        public CircularBestRing(Double bestPct) {
            this.attempted = bestPct != null;
            this.targetPct = bestPct == null ? 0.0 : Math.max(0.0, Math.min(100.0, bestPct));
            setPreferredSize(new Dimension(48, 48));
            setMinimumSize(new Dimension(48, 48));
            setMaximumSize(new Dimension(48, 48));
            setOpaque(false);

            if (attempted && targetPct > 0.0) {
                Anim.animate(680, p -> {
                    animatedPct = targetPct * Anim.easeOutCubic(p);
                    repaint();
                }, () -> {
                    animatedPct = targetPct;
                    repaint();
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int size = Math.min(getWidth(), getHeight()) - 8;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            // Background track
            g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 28));
            g2.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(x, y, size, size));

            // Animated progress arc
            if (attempted && animatedPct > 0.1) {
                double extent = -(animatedPct / 100.0) * 360.0;
                if (targetPct >= 70.0) {
                    g2.setColor(UITheme.SUCCESS);
                } else if (targetPct >= 40.0) {
                    g2.setPaint(UITheme.createPrimaryGradient(x, y, x + size, y + size));
                } else {
                    g2.setColor(UITheme.DANGER);
                }
                g2.draw(new Arc2D.Double(x, y, size, size, 90, extent, Arc2D.OPEN));
            }

            // Center label
            String text = attempted ? String.format("%.0f%%", animatedPct) : "New";
            g2.setFont(UITheme.font(Font.BOLD, attempted ? 10.5f : 10f));
            g2.setColor(attempted ? UITheme.TEXT_PRIMARY : UITheme.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }

    // ================= LIGHT GRAY SHIMMER SKELETON PLACEHOLDER =================

    public static class SkeletonShimmerPanel extends GlassPanel {
        private float sweep = -0.4f;
        private final Timer shimmerTimer;
        private final int barRows;

        public SkeletonShimmerPanel(int barRows) {
            super(16);
            this.barRows = Math.max(2, barRows);
            setSpotlightEnabled(false);

            shimmerTimer = new Timer(Anim.TICK_MS, e -> {
                if (!isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                sweep += 0.028f;
                if (sweep > 1.4f) {
                    sweep = -0.4f;
                }
                repaint();
            });
            shimmerTimer.setCoalesce(true);

            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        if (!shimmerTimer.isRunning()) shimmerTimer.start();
                    } else {
                        shimmerTimer.stop();
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (isShowing() && !shimmerTimer.isRunning()) {
                shimmerTimer.start();
            }
        }

        @Override
        public void removeNotify() {
            shimmerTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            int padX = 22;
            int padY = 22;
            int availW = Math.max(40, w - padX * 2);
            int availH = Math.max(30, h - padY * 2);

            float shimmerCenter = sweep * w;
            float band = Math.max(90f, w * 0.35f);
            LinearGradientPaint shimmerPaint = new LinearGradientPaint(
                    shimmerCenter - band, 0, shimmerCenter + band, 40,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{
                            new Color(226, 232, 240, 170),
                            new Color(255, 255, 255, 235),
                            new Color(226, 232, 240, 170)
                    }
            );
            g2.setPaint(shimmerPaint);

            float[] widthRatios = {0.42f, 0.88f, 0.70f, 0.94f, 0.62f};
            int stepY = Math.max(20, availH / barRows);
            for (int i = 0; i < barRows; i++) {
                int bw = Math.round(availW * widthRatios[i % widthRatios.length]);
                int bh = (i == 0) ? 14 : Math.min(18, Math.max(10, stepY - 12));
                int by = padY + i * stepY;
                if (by + bh > h - 14) break;
                g2.fill(new RoundRectangle2D.Float(padX, by, bw, bh, 7, 7));
            }

            g2.dispose();
        }
    }

    // ================= CROSS-FADE + SLIDE PAGE TRANSITION CONTAINER =================

    public static class TransitionContainer extends JPanel implements Anim.Fadeable, Anim.Slideable {
        private float alpha = 1.0f;
        private int dx = 0;
        private int dy = 0;

        public TransitionContainer(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        public void transitionOutAndRun(Runnable onDone) {
            Anim.animate(210, p -> {
                float e = Anim.easeInOutCubic(p);
                setAlpha(1f - e);
                setSlideOffset(-Math.round(24f * e), 0);
            }, () -> {
                setAlpha(0f);
                if (onDone != null) onDone.run();
            });
        }

        public void transitionIn() {
            setAlpha(0f);
            setSlideOffset(18, 0);
            Anim.animate(260, p -> {
                float e = Anim.easeOutCubic(p);
                setAlpha(e);
                setSlideOffset(Math.round(18f * (1f - e)), 0);
            }, () -> {
                setAlpha(1f);
                setSlideOffset(0, 0);
            });
        }

        @Override
        public void setAlpha(float alpha) {
            this.alpha = Anim.clamp01(alpha);
            repaint();
        }

        @Override
        public float getAlpha() {
            return alpha;
        }

        @Override
        public void setSlideOffset(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            if (alpha <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            if (dx != 0 || dy != 0) {
                g2.translate(dx, dy);
            }
            if (alpha < 0.999f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            super.paint(g2);
            g2.dispose();
        }
    }

    private static Color blend(Color a, Color b, float t) {
        float x = Anim.clamp01(t);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * x),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * x),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * x),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * x)
        );
    }
}
