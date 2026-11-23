package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Lightweight Swing animation utilities powered strictly by javax.swing.Timer (16ms tick).
 * Never blocks the Event Dispatch Thread.
 */
public final class Anim {

    public static final int TICK_MS = 16;

    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    /**
     * Implemented by translucent components (e.g., GlassPanel) for full-subtree alpha fading.
     */
    public interface Fadeable {
        void setAlpha(float alpha);
        float getAlpha();
    }

    /**
     * Implemented by layout-safe animated components for smooth translation without fighting LayoutManagers.
     */
    public interface Slideable {
        void setSlideOffset(int dx, int dy);
    }

    private Anim() {
    }

    // ================= EASING FUNCTIONS =================

    public static float clamp01(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t;
    }

    public static float easeOutCubic(float t) {
        float x = 1f - clamp01(t);
        return 1f - (x * x * x);
    }

    public static float easeInOutCubic(float t) {
        float x = clamp01(t);
        if (x < 0.5f) {
            return 4f * x * x * x;
        }
        float f = -2f * x + 2f;
        return 1f - (f * f * f) / 2f;
    }

    public static float easeOutBack(float t) {
        float x = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float xm1 = x - 1f;
        return 1f + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
    }

    public static float easeOutElastic(float t) {
        float x = clamp01(t);
        if (x == 0f) return 0f;
        if (x == 1f) return 1f;
        double c4 = (2.0 * Math.PI) / 3.0;
        return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * c4) + 1.0);
    }

    // ================= CORE TIMER ANIMATOR =================

    public static Timer animate(int durationMs, Consumer<Float> onProgress) {
        return animate(durationMs, onProgress, null);
    }

    public static Timer animate(int durationMs, Consumer<Float> onProgress, Runnable onDone) {
        int safeDuration = Math.max(1, durationMs);
        long startNanos = System.nanoTime();
        double durationNanos = safeDuration * 1_000_000.0;

        Timer timer = new Timer(TICK_MS, null);
        timer.setCoalesce(true);
        timer.addActionListener(e -> {
            double elapsed = System.nanoTime() - startNanos;
            float rawProgress = clamp01((float) (elapsed / durationNanos));

            if (onProgress != null) {
                onProgress.accept(rawProgress);
            }

            if (rawProgress >= 1f) {
                timer.stop();
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
        timer.start();
        return timer;
    }

    // ================= LABEL COUNT-UP =================

    public static Timer countUp(JLabel label, int from, int to, int durationMs, String format) {
        return countUp(label, (double) from, (double) to, durationMs, format);
    }

    public static Timer countUp(JLabel label, double from, double to, int durationMs, String format) {
        if (label == null) {
            return null;
        }
        String fmt = (format == null || format.isEmpty()) ? "%.0f" : format;
        boolean integerFormat = isIntegerFormat(fmt);

        // Cancel any previous countUp timer attached to this label
        Object prev = label.getClientProperty("anim.countUpTimer");
        if (prev instanceof Timer) {
            ((Timer) prev).stop();
        }

        Timer t = animate(durationMs, p -> {
            float eased = easeOutCubic(p);
            double value = from + (to - from) * eased;
            label.setText(formatValue(fmt, value, integerFormat));
        }, () -> {
            label.setText(formatValue(fmt, to, integerFormat));
            label.putClientProperty("anim.countUpTimer", null);
        });
        label.putClientProperty("anim.countUpTimer", t);
        return t;
    }

    private static boolean isIntegerFormat(String fmt) {
        return fmt.matches(".*%[0-9\\-+ ,#]*[dxo].*");
    }

    private static String formatValue(String fmt, double value, boolean integerFormat) {
        try {
            if (integerFormat) {
                return String.format(Locale.US, fmt, Math.round(value));
            }
            return String.format(Locale.US, fmt, value);
        } catch (Exception ex) {
            return integerFormat ? String.valueOf(Math.round(value)) : String.format(Locale.US, "%.1f", value);
        }
    }

    // ================= FADE IN =================

    public static Timer fadeIn(JComponent component) {
        return fadeIn(component, 360);
    }

    public static Timer fadeIn(JComponent component, int durationMs) {
        if (component == null) {
            return null;
        }
        Object prev = component.getClientProperty("anim.fadeTimer");
        if (prev instanceof Timer) {
            ((Timer) prev).stop();
        }

        if (component instanceof Fadeable) {
            Fadeable fadeable = (Fadeable) component;
            fadeable.setAlpha(0f);
            Timer t = animate(durationMs, p -> {
                fadeable.setAlpha(easeOutCubic(p));
                component.repaint();
            }, () -> {
                fadeable.setAlpha(1f);
                component.repaint();
                component.putClientProperty("anim.fadeTimer", null);
            });
            component.putClientProperty("anim.fadeTimer", t);
            return t;
        }

        // Fallback for standard Swing components: fade foreground alpha cleanly
        Color storedFg = (Color) component.getClientProperty("anim.origFg");
        Color targetFg = storedFg != null ? storedFg : component.getForeground();
        if (targetFg == null) {
            targetFg = UITheme.TEXT_PRIMARY;
        }
        component.putClientProperty("anim.origFg", targetFg);
        final Color baseFg = targetFg;

        component.setForeground(withAlpha(baseFg, 0));
        Timer t = animate(durationMs, p -> {
            int alpha = Math.round(baseFg.getAlpha() * easeOutCubic(p));
            component.setForeground(withAlpha(baseFg, alpha));
            component.repaint();
        }, () -> {
            component.setForeground(baseFg);
            component.repaint();
            component.putClientProperty("anim.fadeTimer", null);
        });
        component.putClientProperty("anim.fadeTimer", t);
        return t;
    }

    // ================= SLIDE IN =================

    public static Timer slideIn(JComponent component, Direction direction) {
        return slideIn(component, direction, 420, 26);
    }

    public static Timer slideIn(JComponent component, String direction) {
        Direction dir = Direction.UP;
        if (direction != null) {
            try {
                dir = Direction.valueOf(direction.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                dir = Direction.UP;
            }
        }
        return slideIn(component, dir, 420, 26);
    }

    public static Timer slideIn(JComponent component, Direction direction, int durationMs, int distancePx) {
        if (component == null) {
            return null;
        }
        Direction dir = direction == null ? Direction.UP : direction;
        int startDx = 0;
        int startDy = 0;
        switch (dir) {
            case UP:    startDy = distancePx;  break;
            case DOWN:  startDy = -distancePx; break;
            case LEFT:  startDx = distancePx;  break;
            case RIGHT: startDx = -distancePx; break;
        }

        Object prev = component.getClientProperty("anim.slideTimer");
        if (prev instanceof Timer) {
            ((Timer) prev).stop();
        }

        final int initDx = startDx;
        final int initDy = startDy;

        if (component instanceof Slideable) {
            Slideable slideable = (Slideable) component;
            Fadeable fadeable = (component instanceof Fadeable) ? (Fadeable) component : null;
            slideable.setSlideOffset(initDx, initDy);
            if (fadeable != null) {
                fadeable.setAlpha(0f);
            }

            Timer t = animate(durationMs, p -> {
                float eased = easeOutCubic(p);
                int dx = Math.round(initDx * (1f - eased));
                int dy = Math.round(initDy * (1f - eased));
                slideable.setSlideOffset(dx, dy);
                if (fadeable != null) {
                    fadeable.setAlpha(clamp01(eased));
                }
                component.repaint();
            }, () -> {
                slideable.setSlideOffset(0, 0);
                if (fadeable != null) {
                    fadeable.setAlpha(1f);
                }
                component.repaint();
                component.putClientProperty("anim.slideTimer", null);
            });
            component.putClientProperty("anim.slideTimer", t);
            return t;
        }

        // Layout-safe fallback for generic JComponents using temporary EmptyBorder padding
        Border saved = (Border) component.getClientProperty("anim.origBorder");
        if (saved == null && component.getClientProperty("anim.hasOrigBorder") == null) {
            saved = component.getBorder();
            component.putClientProperty("anim.origBorder", saved);
            component.putClientProperty("anim.hasOrigBorder", Boolean.TRUE);
        }
        final Border origBorder = saved;

        Timer t = animate(durationMs, p -> {
            float eased = easeOutCubic(p);
            int curDx = Math.round(initDx * (1f - eased));
            int curDy = Math.round(initDy * (1f - eased));
            int top = Math.max(0, curDy);
            int bottom = Math.max(0, -curDy);
            int left = Math.max(0, curDx);
            int right = Math.max(0, -curDx);
            Border shift = new EmptyBorder(top, left, bottom, right);
            component.setBorder(origBorder == null ? shift : new CompoundBorder(shift, origBorder));
            component.revalidate();
            component.repaint();
        }, () -> {
            component.setBorder(origBorder);
            component.revalidate();
            component.repaint();
            component.putClientProperty("anim.slideTimer", null);
        });
        component.putClientProperty("anim.slideTimer", t);
        return t;
    }

    public static Color withAlpha(Color c, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
