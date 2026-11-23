package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Animated password strength meter with 4 visual segments and criteria evaluation.
 * Enforces minimum 8 characters and rewards mixed-case, digits, and symbols.
 */
public class PasswordStrengthMeter extends JPanel {

    private float animScore = 0f;
    private int targetScore = 0;
    private String strengthLabel = "";
    private Color targetColor = UITheme.DANGER;
    private Timer animTimer;

    public PasswordStrengthMeter() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(4, 2, 6, 2));
        setPreferredSize(new Dimension(300, 26));
    }

    public static int evaluateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        if (password.length() < 8) {
            return 1; // Below minimum length requirement
        }

        int score = 1;
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }

        if (hasUpper && hasLower) score++;
        if (hasDigit) score++;
        if (hasSymbol) score++;
        if (password.length() >= 12 && score >= 3) score = 4;

        return Math.min(4, Math.max(1, score));
    }

    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            targetScore = 0;
            strengthLabel = "";
            targetColor = new Color(226, 232, 240);
        } else if (password.length() < 8) {
            targetScore = 1;
            strengthLabel = "Too short (min 8 chars)";
            targetColor = UITheme.DANGER;
        } else {
            targetScore = evaluateStrength(password);
            switch (targetScore) {
                case 1:
                    strengthLabel = "Weak";
                    targetColor = UITheme.DANGER;
                    break;
                case 2:
                    strengthLabel = "Fair";
                    targetColor = UITheme.WARNING_TEXT;
                    break;
                case 3:
                    strengthLabel = "Good";
                    targetColor = UITheme.SKY;
                    break;
                case 4:
                default:
                    strengthLabel = "Strong";
                    targetColor = UITheme.SUCCESS;
                    break;
            }
        }

        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }

        final float start = animScore;
        final float end = (float) targetScore;
        animTimer = Anim.animate(240, p -> {
            animScore = start + (end - start) * p;
            repaint();
        }, () -> {
            animScore = end;
            repaint();
        });
    }

    public boolean isPasswordValid(String password) {
        return password != null && password.length() >= 8;
    }

    public int getStrengthScore() {
        return targetScore;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.configureGraphics(g2);

        int w = getWidth();
        int barY = 2;
        int barH = 5;
        int gap = 6;
        int totalGaps = gap * 3;
        int segW = Math.max(10, (w - totalGaps) / 4);

        // Draw 4 segments
        for (int i = 0; i < 4; i++) {
            int segX = i * (segW + gap);
            Shape segShape = new RoundRectangle2D.Float(segX, barY, segW, barH, 4, 4);

            float fill = Math.max(0f, Math.min(1f, animScore - i));
            // Background inactive track
            g2.setColor(new Color(241, 245, 249));
            g2.fill(segShape);

            if (fill > 0.001f) {
                int activeW = Math.round(segW * fill);
                Shape activeShape = new RoundRectangle2D.Float(segX, barY, activeW, barH, 4, 4);
                g2.setColor(targetColor);
                g2.fill(activeShape);
            }
        }

        // Draw strength text label if any
        if (strengthLabel != null && !strengthLabel.isEmpty()) {
            g2.setFont(UITheme.font(Font.BOLD, 11f));
            g2.setColor(targetColor);
            g2.drawString(strengthLabel, 2, barY + barH + 13);
        }

        g2.dispose();
    }
}
