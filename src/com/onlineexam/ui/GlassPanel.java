package com.onlineexam.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Pastel Aurora Light frosted glass container with:
 * - 16px rounded corners (configurable)
 * - 70% translucent white glass fill + subtle upper specular sheen
 * - 90% white glass highlight + 1px indigo->violet gradient border + 8% slate outer hairline
 * - Colored indigo-tinted floating shadow
 * - Smooth mouse-following light-indigo radial spotlight glow
 * - Built-in Anim.Fadeable and Anim.Slideable support
 */
public class GlassPanel extends JPanel implements Anim.Fadeable, Anim.Slideable {

    public static final int DEFAULT_RADIUS = 14;

    protected int radius;
    protected Color fillColor;
    protected Color borderColor;
    protected Color shadowColor;
    protected Color topAccentColor;
    protected Color leftAccentColor;
    protected boolean useGradientBorder = true;
    protected boolean spotlightEnabled = true;
    protected boolean shadowEnabled = true;

    private float alpha = 1.0f;
    private float scale = 1.0f;
    private int slideDx = 0;
    private int slideDy = 0;
    protected int hoverLiftY = 0;
    protected float shadowBoost = 1.0f;

    // Hover spotlight state
    private Point mousePoint = null;
    private boolean pointerInside = false;
    private float spotlightIntensity = 0f;
    private Timer spotlightTimer;
    private AWTEventListener mouseTracker;

    public GlassPanel() {
        this(DEFAULT_RADIUS);
    }

    public GlassPanel(LayoutManager layout) {
        this(DEFAULT_RADIUS);
        setLayout(layout);
    }

    public GlassPanel(int radius) {
        this(radius, null, null);
    }

    public GlassPanel(int radius, Color customFillColor, Color customBorderColor) {
        this.radius = Math.max(6, radius);
        this.fillColor = customFillColor;
        this.borderColor = customBorderColor;
        this.shadowColor = null; // defaults to UITheme.PRIMARY
        setOpaque(false);
    }

    // ================= CONFIGURATION =================

    public void setRadius(int radius) {
        this.radius = Math.max(6, radius);
        repaint();
    }

    public int getRadius() {
        return radius;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
        repaint();
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    public void setShadowColor(Color shadowColor) {
        this.shadowColor = shadowColor;
        repaint();
    }

    public void setTopAccentColor(Color accent) {
        this.topAccentColor = accent;
        repaint();
    }

    public void setLeftAccentColor(Color accent) {
        this.leftAccentColor = accent;
        repaint();
    }

    public void setGradientBorderEnabled(boolean enabled) {
        this.useGradientBorder = enabled;
        repaint();
    }

    public void setSpotlightEnabled(boolean enabled) {
        this.spotlightEnabled = enabled;
        if (!enabled) {
            spotlightIntensity = 0f;
            pointerInside = false;
        }
        repaint();
    }

    public void setShadowEnabled(boolean enabled) {
        this.shadowEnabled = enabled;
        revalidate();
        repaint();
    }

    // ================= ANIMATION INTERFACES =================

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
        this.slideDx = dx;
        this.slideDy = dy;
        repaint();
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.1f, scale);
        repaint();
    }

    public float getScale() {
        return scale;
    }

    // ================= POINTER SPOTLIGHT TRACKING =================

    @Override
    public void addNotify() {
        super.addNotify();
        installMouseTracker();
    }

    @Override
    public void removeNotify() {
        uninstallMouseTracker();
        if (spotlightTimer != null) {
            spotlightTimer.stop();
            spotlightTimer = null;
        }
        super.removeNotify();
    }

    private void installMouseTracker() {
        if (mouseTracker != null) {
            return;
        }
        mouseTracker = event -> {
            if (!spotlightEnabled || !isShowing() || !(event instanceof MouseEvent)) {
                return;
            }
            MouseEvent me = (MouseEvent) event;
            int id = me.getID();
            if (id != MouseEvent.MOUSE_MOVED
                    && id != MouseEvent.MOUSE_DRAGGED
                    && id != MouseEvent.MOUSE_EXITED
                    && id != MouseEvent.MOUSE_ENTERED) {
                return;
            }
            Component src = me.getComponent();
            if (src == null || !src.isShowing()) {
                return;
            }
            Window myWin = SwingUtilities.getWindowAncestor(this);
            Window srcWin = SwingUtilities.getWindowAncestor(src);
            if (myWin == null || myWin != srcWin) {
                if (pointerInside) {
                    pointerInside = false;
                    animateSpotlightTo(0f);
                    onHoverChanged(false);
                }
                return;
            }

            Point pt = SwingUtilities.convertPoint(src, me.getPoint(), this);
            boolean inside = contains(pt) && id != MouseEvent.MOUSE_EXITED;
            if (inside) {
                mousePoint = pt;
                if (!pointerInside) {
                    pointerInside = true;
                    animateSpotlightTo(1f);
                    onHoverChanged(true);
                } else {
                    repaint();
                }
            } else if (pointerInside) {
                pointerInside = false;
                animateSpotlightTo(0f);
                onHoverChanged(false);
            }
        };
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                    mouseTracker,
                    AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
            );
        } catch (SecurityException ignored) {
            mouseTracker = null;
        }
    }

    private void uninstallMouseTracker() {
        if (mouseTracker != null) {
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(mouseTracker);
            } catch (SecurityException ignored) {
            }
            mouseTracker = null;
        }
    }

    protected void onHoverChanged(boolean hovered) {
        // Optional hook for subclasses (e.g., BentoCard hover lift)
    }

    private void animateSpotlightTo(float target) {
        if (spotlightTimer != null && spotlightTimer.isRunning()) {
            spotlightTimer.stop();
        }
        final float start = spotlightIntensity;
        if (Math.abs(target - start) < 0.01f) {
            spotlightIntensity = target;
            repaint();
            return;
        }
        spotlightTimer = Anim.animate(180, p -> {
            float eased = Anim.easeOutCubic(p);
            spotlightIntensity = start + (target - start) * eased;
            repaint();
        }, () -> {
            spotlightIntensity = target;
            repaint();
        });
    }

    // ================= PAINTING =================

    @Override
    public void paint(Graphics g) {
        if (alpha <= 0.001f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.configureGraphics(g2);
        if (slideDx != 0 || slideDy != 0 || hoverLiftY != 0) {
            g2.translate(slideDx, slideDy + hoverLiftY);
        }
        if (Math.abs(scale - 1.0f) > 0.001f) {
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            g2.translate(cx, cy);
            g2.scale(scale, scale);
            g2.translate(-cx, -cy);
        }
        if (alpha < 0.999f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        super.paint(g2);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.configureGraphics(g2);

        int w = getWidth();
        int h = getHeight();

        // Reserve subtle breathing margin for floating colored shadow & hover lift
        float padX = shadowEnabled ? 4f : 1f;
        float padTop = shadowEnabled ? 5f : 1f;
        float padBottom = shadowEnabled ? 8f : 1f;

        float x = padX;
        float y = padTop;
        float cw = Math.max(4f, w - padX * 2f);
        float ch = Math.max(4f, h - padTop - padBottom);
        float arc = Math.min(radius, Math.min(cw, ch));

        // 1. Colored soft floating shadow (indigo-tinted, never gray/black)
        if (shadowEnabled) {
            Color tint = shadowColor != null ? shadowColor : UITheme.PRIMARY;
            int spread = Math.round(5 * shadowBoost);
            int offsetY = Math.round(4 * shadowBoost);
            UITheme.paintColoredShadow(g2, x, y, cw, ch, arc, tint, spread, offsetY);
        }

        Shape cardShape = new RoundRectangle2D.Float(x, y, cw, ch, arc, arc);

        // 2. Frosted translucent white glass fill (70% white by default)
        Color baseFill = fillColor != null ? fillColor : UITheme.GLASS_FILL;
        g2.setColor(baseFill);
        g2.fill(cardShape);

        // 3. Subtle upper specular highlight for airy frosted depth
        Shape oldClip = g2.getClip();
        g2.clip(cardShape);

        if (fillColor == null) {
            GradientPaint sheen = new GradientPaint(
                    x, y, new Color(255, 255, 255, 95),
                    x, y + ch * 0.55f, new Color(255, 255, 255, 0)
            );
            g2.setPaint(sheen);
            g2.fill(cardShape);
        }

        // 4. Optional semantic top or left accent strip
        if (topAccentColor != null) {
            g2.setColor(topAccentColor);
            g2.fillRect((int) x, (int) y, (int) Math.ceil(cw), 3);
        }
        if (leftAccentColor != null) {
            g2.setColor(leftAccentColor);
            g2.fillRect((int) x, (int) y, 3, (int) Math.ceil(ch));
        }

        // 5. Hover mouse-following light-indigo radial spotlight glow
        if (spotlightEnabled && spotlightIntensity > 0.01f && mousePoint != null) {
            float spotRadius = Math.max(120f, Math.min(cw, ch) * 0.85f);
            int centerAlpha = Math.round(34 * spotlightIntensity);
            int midAlpha = Math.round(14 * spotlightIntensity);
            RadialGradientPaint spotlight = new RadialGradientPaint(
                    new Point2D.Float(mousePoint.x, mousePoint.y),
                    spotRadius,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{
                            Anim.withAlpha(UITheme.PRIMARY, centerAlpha),
                            Anim.withAlpha(UITheme.VIOLET, midAlpha),
                            Anim.withAlpha(UITheme.PRIMARY, 0)
                    }
            );
            g2.setPaint(spotlight);
            g2.fill(cardShape);
        }

        g2.setClip(oldClip);

        // 6. Multi-layer Border:
        g2.setStroke(new BasicStroke(1f));
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, cw - 1f, ch - 1f, arc, arc));
        } else {
            // Clean 1px hairline border #E2E8F0
            g2.setColor(UITheme.BORDER);
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, cw - 1f, ch - 1f, arc, arc));

            // Subtle 1px Indigo -> Violet gradient accent border on hover
            if (useGradientBorder && spotlightIntensity > 0.05f) {
                int gradAlpha = Math.round(75 * spotlightIntensity);
                g2.setPaint(UITheme.createAuroraBorderGradient(x, y, x + cw, y + ch, gradAlpha));
                g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, cw - 1f, ch - 1f, arc, arc));
            }
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
