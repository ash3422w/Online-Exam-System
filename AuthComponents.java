package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * Shared Pastel Aurora Light interactive widgets for LoginUI and SignupUI.
 * All animations use javax.swing.Timer (via Anim) and full Graphics2D quality hints.
 */
public final class AuthComponents {

    private AuthComponents() {
    }

    // ================= CARD SHAKE & SCALE-FADE EXIT =================

    public static void shakeCard(GlassPanel card) {
        if (card == null) return;
        Anim.animate(400, p -> {
            double damped = Math.sin(p * Math.PI * 5.0) * (1.0 - p);
            card.setSlideOffset((int) Math.round(damped * 14.0), 0);
        }, () -> card.setSlideOffset(0, 0));
    }

    static void scaleFadeOut(GlassPanel card, Runnable onDone) {
        if (card == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Anim.animate(280, p -> {
            float e = Anim.easeOutCubic(p);
            card.setScale(1.0f + 0.07f * e);
            card.setAlpha(1.0f - e);
        }, () -> {
            card.setAlpha(0f);
            if (onDone != null) onDone.run();
        });
    }

    static void staggerFadeIn(FadePanel[] rows, int initialDelayMs, int stepDelayMs) {
        if (rows == null) return;
        for (int i = 0; i < rows.length; i++) {
            final FadePanel row = rows[i];
            row.setAlpha(0f);
            row.setSlideOffset(-14, 0);
            int delay = initialDelayMs + i * stepDelayMs;
            Timer starter = new Timer(Math.max(16, delay), e -> {
                ((Timer) e.getSource()).stop();
                Anim.slideIn(row, Anim.Direction.RIGHT, 380, 14);
            });
            starter.setRepeats(false);
            starter.start();
        }
    }

    // ================= FADEABLE + SLIDEABLE LIGHT CONTAINER =================

    static class FadePanel extends JPanel implements Anim.Fadeable, Anim.Slideable {
        private float alpha = 1.0f;
        private int dx = 0;
        private int dy = 0;

        FadePanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
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

    // ================= INLINE FADING VALIDATION MESSAGE =================

    public static class InlineValidationLabel extends JComponent implements Anim.Fadeable {
        private String message = "";
        private Color messageColor = UITheme.DANGER_TEXT;
        private float alpha = 0f;
        private Timer fadeTimer;

        public InlineValidationLabel() {
            setOpaque(false);
            setPreferredSize(new Dimension(340, 20));
            setMinimumSize(new Dimension(100, 18));
        }

        public void showError(String text) {
            showMessage(text, UITheme.DANGER_TEXT);
        }

        public void showSuccess(String text) {
            showMessage(text, UITheme.SUCCESS_TEXT);
        }

        public void showMessage(String text, Color color) {
            this.message = text == null ? "" : text;
            this.messageColor = color != null ? color : UITheme.DANGER_TEXT;
            if (fadeTimer != null && fadeTimer.isRunning()) {
                fadeTimer.stop();
            }
            this.alpha = 0f;
            fadeTimer = Anim.animate(240, p -> {
                this.alpha = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                this.alpha = 1f;
                repaint();
            });
        }

        public void clear() {
            if (message.isEmpty() && alpha <= 0.01f) return;
            if (fadeTimer != null && fadeTimer.isRunning()) {
                fadeTimer.stop();
            }
            final float startAlpha = alpha;
            fadeTimer = Anim.animate(160, p -> {
                this.alpha = startAlpha * (1f - Anim.easeOutCubic(p));
                repaint();
            }, () -> {
                this.message = "";
                this.alpha = 0f;
                repaint();
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
        protected void paintComponent(Graphics g) {
            if (message.isEmpty() || alpha <= 0.01f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setFont(UITheme.font(Font.BOLD, 11.5f));
            g2.setColor(messageColor);
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(message, 2, y);
            g2.dispose();
        }
    }

    // ================= FLOATING LABEL INPUT WITH GRADIENT UNDERLINE & JAVA2D EYE =================

    public static class FloatingInputField extends JPanel {
        private final String labelText;
        private final boolean passwordMode;
        private final JTextField textField;
        private final JPasswordField passwordField;
        private final EyeToggleButton eyeButton;

        private boolean focused = false;
        private float floatProgress = 0f;      // 0 = centered placeholder, 1 = shrunk top label
        private float underlineProgress = 0f;  // 0 = none, 1 = full width L->R sweep
        private float errorFlash = 0f;         // 1 -> 0 red border flash

        private Timer floatTimer;
        private Timer underlineTimer;
        private Timer errorTimer;

        public FloatingInputField(String labelText, boolean passwordMode) {
            this.labelText = labelText;
            this.passwordMode = passwordMode;

            setOpaque(false);
            setLayout(new BorderLayout(4, 0));
            setPreferredSize(new Dimension(356, 52));
            setMinimumSize(new Dimension(240, 52));
            setBorder(new EmptyBorder(18, 13, 6, passwordMode ? 6 : 13));

            if (passwordMode) {
                passwordField = new JPasswordField();
                passwordField.setEchoChar('\u2022');
                passwordField.putClientProperty("JTextField.showRevealButton", false);
                textField = passwordField;
            } else {
                passwordField = null;
                textField = new JTextField();
            }

            textField.setOpaque(false);
            textField.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
            textField.setFont(UITheme.font(Font.PLAIN, 13.5f));
            textField.setForeground(UITheme.TEXT_PRIMARY);
            textField.setCaretColor(UITheme.PRIMARY);

            add(textField, BorderLayout.CENTER);

            if (passwordMode) {
                eyeButton = new EyeToggleButton();
                eyeButton.addActionListener(e -> {
                    boolean show = eyeButton.isRevealed();
                    passwordField.setEchoChar(show ? (char) 0 : '\u2022');
                    textField.requestFocusInWindow();
                });
                JPanel eyeWrap = new JPanel(new GridBagLayout());
                eyeWrap.setOpaque(false);
                eyeWrap.setBorder(new EmptyBorder(0, 0, 6, 4));
                eyeWrap.add(eyeButton);
                add(eyeWrap, BorderLayout.EAST);
            } else {
                eyeButton = null;
            }

            // Clicking anywhere on the container focuses the inner input
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    textField.requestFocusInWindow();
                }
            });

            textField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    animateFloatTo(1f);
                    animateUnderlineTo(1f);
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    animateFloatTo(hasContent() ? 1f : 0f);
                    animateUnderlineTo(0f);
                }
            });

            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { onTextChanged(); }
                @Override public void removeUpdate(DocumentEvent e) { onTextChanged(); }
                @Override public void changedUpdate(DocumentEvent e) { onTextChanged(); }
            });
        }

        private boolean hasContent() {
            if (passwordMode) {
                return passwordField.getPassword().length > 0;
            }
            return !textField.getText().isEmpty();
        }

        private void onTextChanged() {
            float target = (focused || hasContent()) ? 1f : 0f;
            if (Math.abs(floatProgress - target) > 0.01f) {
                animateFloatTo(target);
            }
        }

        private void animateFloatTo(float target) {
            if (floatTimer != null && floatTimer.isRunning()) {
                floatTimer.stop();
            }
            final float start = floatProgress;
            floatTimer = Anim.animate(200, p -> {
                floatProgress = start + (target - start) * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                floatProgress = target;
                repaint();
            });
        }

        private void animateUnderlineTo(float target) {
            if (underlineTimer != null && underlineTimer.isRunning()) {
                underlineTimer.stop();
            }
            final float start = underlineProgress;
            underlineTimer = Anim.animate(240, p -> {
                underlineProgress = start + (target - start) * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                underlineProgress = target;
                repaint();
            });
        }

        public void flashError() {
            if (errorTimer != null && errorTimer.isRunning()) {
                errorTimer.stop();
            }
            errorFlash = 1.0f;
            repaint();
            errorTimer = Anim.animate(680, p -> {
                errorFlash = 1.0f - Anim.easeInOutCubic(p);
                repaint();
            }, () -> {
                errorFlash = 0f;
                repaint();
            });
        }

        public String getInputText() {
            if (passwordMode) {
                return new String(passwordField.getPassword());
            }
            return textField.getText();
        }

        public void setInputText(String text) {
            textField.setText(text == null ? "" : text);
            floatProgress = (focused || hasContent()) ? 1f : 0f;
            repaint();
        }

        public void addActionListener(ActionListener listener) {
            textField.addActionListener(listener);
        }

        public void addDocumentChangeListener(Runnable onChange) {
            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { onChange.run(); }
                @Override public void removeUpdate(DocumentEvent e) { onChange.run(); }
                @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float arc = 11f;
            Shape box = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc);

            // Crisp white input surface
            g2.setColor(Color.WHITE);
            g2.fill(box);

            // Base border blended with error flash red when triggered
            if (errorFlash > 0.01f) {
                g2.setColor(blendColor(UITheme.BORDER, UITheme.DANGER, errorFlash));
                g2.setStroke(new BasicStroke(1.5f));
            } else if (focused) {
                // Subtle focus glow
                g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 40));
                g2.setStroke(new BasicStroke(2.5f));
                g2.draw(new RoundRectangle2D.Float(0.2f, 0.2f, w - 0.4f, h - 0.4f, arc + 1f, arc + 1f));

                g2.setColor(UITheme.PRIMARY);
                g2.setStroke(new BasicStroke(1.5f));
            } else {
                g2.setColor(UITheme.BORDER);
                g2.setStroke(new BasicStroke(1f));
            }
            g2.draw(box);

            // Left-to-right gradient underline sweep on focus
            if (underlineProgress > 0.01f) {
                Shape oldClip = g2.getClip();
                g2.clip(box);
                float lineW = w * underlineProgress;
                g2.setPaint(errorFlash > 0.2f
                        ? UITheme.DANGER
                        : UITheme.createPrimaryGradient(0f, h - 2f, Math.max(1f, w), h - 2f));
                g2.fill(new RoundRectangle2D.Float(0f, h - 2.5f, lineW, 2.5f, 2f, 2f));
                g2.setClip(oldClip);
            }

            // Floating label: glides up and shrinks smoothly from 13pt -> 10.5pt
            float fontSize = 13.0f - (2.5f * floatProgress);
            int fontStyle = floatProgress > 0.5f ? Font.BOLD : Font.PLAIN;
            g2.setFont(UITheme.font(fontStyle, fontSize));

            Color labelColor;
            if (errorFlash > 0.25f) {
                labelColor = blendColor(UITheme.TEXT_MUTED, UITheme.DANGER_TEXT, errorFlash);
            } else if (focused) {
                labelColor = blendColor(UITheme.TEXT_MUTED, UITheme.PRIMARY, floatProgress);
            } else {
                labelColor = blendColor(UITheme.TEXT_MUTED, UITheme.TEXT_SECONDARY, floatProgress);
            }
            g2.setColor(labelColor);

            FontMetrics fm = g2.getFontMetrics();
            float restY = (h + fm.getAscent() - fm.getDescent()) / 2f;
            float topY = 15.5f;
            float curY = restY + (topY - restY) * floatProgress;
            g2.drawString(labelText, 13f, curY);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ================= JAVA2D SHOW/HIDE PASSWORD EYE ICON =================

    private static class EyeToggleButton extends JButton {
        private boolean revealed = false;
        private boolean hovered = false;

        EyeToggleButton() {
            setPreferredSize(new Dimension(28, 24));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText("Show / hide password");

            addActionListener(e -> {
                revealed = !revealed;
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            });
        }

        boolean isRevealed() {
            return revealed;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            g2.setColor(hovered || revealed ? UITheme.PRIMARY : UITheme.TEXT_MUTED);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Almond eye outline using smooth quadratic curves
            float rx = 8.5f;
            float ry = 6.2f;
            QuadCurve2D topLid = new QuadCurve2D.Float(cx - rx, cy, cx, cy - ry * 1.45f, cx + rx, cy);
            QuadCurve2D botLid = new QuadCurve2D.Float(cx - rx, cy, cx, cy + ry * 1.45f, cx + rx, cy);
            g2.draw(topLid);
            g2.draw(botLid);

            // Iris circle
            float ir = 2.8f;
            if (revealed) {
                g2.fill(new Ellipse2D.Float(cx - ir, cy - ir, ir * 2f, ir * 2f));
            } else {
                g2.draw(new Ellipse2D.Float(cx - ir, cy - ir, ir * 2f, ir * 2f));
                // Diagonal slash indicating password is hidden
                g2.draw(new Line2D.Float(cx - 7.2f, cy + 6.2f, cx + 7.2f, cy - 6.2f));
            }

            g2.dispose();
        }
    }

    // ================= SLIDING PILL ROLE TOGGLE (easeInOutCubic 250ms) =================

    static class SlidingRoleToggle extends JComponent {
        private final String leftLabel;
        private final String rightLabel;
        private boolean rightSelected = false;
        private float pillPos = 0f; // 0 = left, 1 = right
        private Timer slideTimer;
        private Consumer<String> onRoleChanged;

        SlidingRoleToggle(String leftLabel, String rightLabel) {
            this.leftLabel = leftLabel;
            this.rightLabel = rightLabel;
            setPreferredSize(new Dimension(356, 40));
            setMinimumSize(new Dimension(240, 40));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setOpaque(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    boolean selectRight = e.getX() >= getWidth() / 2;
                    setRole(selectRight ? "admin" : "student", true);
                }
            });
        }

        void setOnRoleChanged(Consumer<String> listener) {
            this.onRoleChanged = listener;
        }

        String getSelectedRole() {
            return rightSelected ? "admin" : "student";
        }

        void setRole(String role, boolean notify) {
            boolean wantRight = "admin".equalsIgnoreCase(role);
            if (wantRight == rightSelected && (slideTimer == null || !slideTimer.isRunning())) {
                return;
            }
            rightSelected = wantRight;
            float target = rightSelected ? 1f : 0f;
            if (slideTimer != null && slideTimer.isRunning()) {
                slideTimer.stop();
            }
            final float start = pillPos;
            slideTimer = Anim.animate(250, p -> {
                pillPos = start + (target - start) * Anim.easeInOutCubic(p);
                repaint();
            }, () -> {
                pillPos = target;
                repaint();
            });
            if (notify && onRoleChanged != null) {
                onRoleChanged.accept(getSelectedRole());
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();
            float trackArc = 12f;

            // Clean modern slate track #F1F5F9
            g2.setColor(new Color(241, 245, 249));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, trackArc, trackArc));
            g2.setColor(UITheme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, trackArc, trackArc));

            // Sliding white pill with crisp diffused shadow
            float pad = 3.5f;
            float pillW = (w - pad * 2f) / 2f;
            float pillH = h - pad * 2f;
            float pillX = pad + pillPos * pillW;

            UITheme.paintColoredShadow(g2, pillX, pad, pillW, pillH, 8f, UITheme.PRIMARY, 3, 2);
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(pillX, pad, pillW, pillH, 8f, 8f));
            g2.setColor(UITheme.BORDER);
            g2.draw(new RoundRectangle2D.Float(pillX + 0.5f, pad + 0.5f, pillW - 1f, pillH - 1f, 8f, 8f));

            // Left & right labels with smooth active color blend
            g2.setFont(UITheme.font(Font.BOLD, 12.5f));
            FontMetrics fm = g2.getFontMetrics();
            int textY = (h + fm.getAscent() - fm.getDescent()) / 2;

            Color leftColor = blendColor(UITheme.PRIMARY, UITheme.TEXT_SECONDARY, pillPos);
            Color rightColor = blendColor(UITheme.TEXT_SECONDARY, UITheme.PRIMARY, pillPos);

            g2.setColor(leftColor);
            int lx = (int) ((w / 2f - fm.stringWidth(leftLabel)) / 2f);
            g2.drawString(leftLabel, lx, textY);

            g2.setColor(rightColor);
            int rx = (int) (w / 2f + (w / 2f - fm.stringWidth(rightLabel)) / 2f);
            g2.drawString(rightLabel, rx, textY);

            g2.dispose();
        }
    }

    // ================= PRIMARY GRADIENT BUTTON (HOVER BRIGHTEN, 0.97 PRESS SCALE, RIPPLE) =================

    static class RippleGradientButton extends JButton {
        private float hoverProgress = 0f;
        private float pressScale = 1.0f;

        private Point rippleOrigin = null;
        private float rippleProgress = 0f;
        private boolean rippleActive = false;

        private Timer hoverTimer;
        private Timer scaleTimer;
        private Timer rippleTimer;

        RippleGradientButton(String text) {
            super(text);
            setFont(UITheme.font(Font.BOLD, 13.5f));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(356, 44));
            setBorder(new EmptyBorder(10, 18, 12, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) animateHover(1f);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    animateHover(0f);
                    animateScale(1.0f);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    animateScale(0.97f);
                    triggerRipple(e.getPoint());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    animateScale(1.0f);
                }
            });
        }

        private void animateHover(float target) {
            if (hoverTimer != null && hoverTimer.isRunning()) hoverTimer.stop();
            final float start = hoverProgress;
            hoverTimer = Anim.animate(160, p -> {
                hoverProgress = start + (target - start) * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                hoverProgress = target;
                repaint();
            });
        }

        private void animateScale(float target) {
            if (scaleTimer != null && scaleTimer.isRunning()) scaleTimer.stop();
            final float start = pressScale;
            scaleTimer = Anim.animate(95, p -> {
                pressScale = start + (target - start) * Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                pressScale = target;
                repaint();
            });
        }

        private void triggerRipple(Point origin) {
            if (rippleTimer != null && rippleTimer.isRunning()) rippleTimer.stop();
            rippleOrigin = origin;
            rippleProgress = 0f;
            rippleActive = true;
            rippleTimer = Anim.animate(420, p -> {
                rippleProgress = Anim.easeOutCubic(p);
                repaint();
            }, () -> {
                rippleActive = false;
                rippleProgress = 0f;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);

            int w = getWidth();
            int h = getHeight();

            // Press scale around center (1.0 -> 0.97)
            if (Math.abs(pressScale - 1.0f) > 0.001f) {
                g2.translate(w / 2.0, h / 2.0);
                g2.scale(pressScale, pressScale);
                g2.translate(-w / 2.0, -h / 2.0);
            }

            float padX = 2f;
            float padTop = 1f;
            float padBottom = 5f;
            float bw = Math.max(4f, w - padX * 2f);
            float bh = Math.max(4f, h - padTop - padBottom);
            float arc = 11f;

            // Colored indigo shadow beneath button
            if (isEnabled()) {
                UITheme.paintColoredShadow(g2, padX, padTop, bw, bh, arc, UITheme.PRIMARY, 4, 3);
            }

            Shape pill = new RoundRectangle2D.Float(padX, padTop, bw, bh, arc, arc);

            if (!isEnabled()) {
                g2.setColor(UITheme.BORDER_STRONG);
                g2.fill(pill);
            } else {
                // Brighten Indigo -> Violet smoothly on hover
                Color startCol = blendColor(UITheme.PRIMARY, new Color(99, 102, 241), hoverProgress);
                Color endCol = blendColor(UITheme.VIOLET, new Color(147, 90, 248), hoverProgress);
                g2.setPaint(new LinearGradientPaint(padX, padTop, padX + bw, padTop + bh * 0.25f,
                        new float[]{0f, 1f}, new Color[]{startCol, endCol}));
                g2.fill(pill);
            }

            // Cursor-originated expanding translucent white ripple
            if (rippleActive && rippleOrigin != null) {
                Shape oldClip = g2.getClip();
                g2.clip(pill);
                float maxR = (float) Math.hypot(bw, bh) * 0.85f;
                float r = Math.max(4f, maxR * rippleProgress);
                int rAlpha = Math.max(0, Math.round(92f * (1f - rippleProgress)));
                g2.setColor(new Color(255, 255, 255, rAlpha));
                g2.fill(new Ellipse2D.Float(rippleOrigin.x - r, rippleOrigin.y - r, r * 2f, r * 2f));
                g2.setClip(oldClip);
            }

            // Subtle inner top highlight for polish
            g2.setColor(new Color(255, 255, 255, 45));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(padX + 0.5f, padTop + 0.5f, bw - 1f, bh - 1f, arc, arc));

            // Centered crisp white label
            g2.setFont(getFont());
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (int) Math.round(padX + (bw - fm.stringWidth(getText())) / 2.0);
            int ty = (int) Math.round(padTop + (bh + fm.getAscent() - fm.getDescent()) / 2.0);
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }
    }

    private static Color blendColor(Color a, Color b, float t) {
        float x = Anim.clamp01(t);
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * x);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * x);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * x);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * x);
        return new Color(r, g, bl, al);
    }
}
