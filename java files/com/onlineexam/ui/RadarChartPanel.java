package com.onlineexam.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Animated Java2D Radar Chart for Subject Strengths (Pastel Aurora Light theme).
 * Polygon expands smoothly outward from the center via 16ms javax.swing.Timer,
 * with interactive vertex hover highlight and floating value bubble.
 */
public class RadarChartPanel extends JPanel {

    private final List<String> axes = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private float animProgress = 0f;
    private int hoveredAxis = -1;
    private Timer animTimer;

    public RadarChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(280, 210));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoveredAxis(e.getPoint());
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredAxis != -1) {
                    hoveredAxis = -1;
                    repaint();
                }
            }
        });
    }

    public void setData(Map<String, Double> subjectPercentages) {
        axes.clear();
        values.clear();
        if (subjectPercentages != null) {
            for (Map.Entry<String, Double> entry : subjectPercentages.entrySet()) {
                axes.add(entry.getKey());
                double v = entry.getValue() == null ? 0.0 : entry.getValue();
                values.add(Math.max(0.0, Math.min(100.0, v)));
            }
        }
        replay();
    }

    public void replay() {
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        animProgress = 0f;
        animTimer = Anim.animate(680, p -> {
            animProgress = Anim.easeOutCubic(p);
            repaint();
        }, () -> {
            animProgress = 1f;
            repaint();
        });
    }

    private void updateHoveredAxis(Point pt) {
        int n = axes.size();
        if (n < 3) return;
        int w = getWidth();
        int h = getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0 + 4.0;
        double maxR = Math.max(30.0, Math.min(w, h) * 0.34);

        int found = -1;
        double bestDist = 26.0;
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / n);
            double r = maxR * (values.get(i) / 100.0) * animProgress;
            double vx = cx + Math.cos(angle) * r;
            double vy = cy + Math.sin(angle) * r;
            double dist = pt.distance(vx, vy);
            if (dist < bestDist) {
                bestDist = dist;
                found = i;
            }
        }
        if (found != hoveredAxis) {
            hoveredAxis = found;
            setCursor(found >= 0 ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.configureGraphics(g2);

        int w = getWidth();
        int h = getHeight();
        int n = axes.size();

        if (n < 3) {
            g2.setFont(UITheme.FONT_BODY);
            g2.setColor(UITheme.TEXT_MUTED);
            String msg = "Need at least 3 subjects for radar chart";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        double cx = w / 2.0;
        double cy = h / 2.0 + 4.0;
        double maxR = Math.max(30.0, Math.min(w, h) * 0.34);

        // 1. Concentric grid web rings (25%, 50%, 75%, 100%)
        for (int level = 1; level <= 4; level++) {
            double r = maxR * (level / 4.0);
            Path2D ring = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / n);
                double x = cx + Math.cos(angle) * r;
                double y = cy + Math.sin(angle) * r;
                if (i == 0) ring.moveTo(x, y);
                else ring.lineTo(x, y);
            }
            ring.closePath();

            if (level % 2 == 0) {
                g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 8));
                g2.fill(ring);
            }
            g2.setColor(level == 4 ? Anim.withAlpha(UITheme.PRIMARY, 42) : UITheme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(ring);
        }

        // 2. Spokes and perimeter subject labels
        g2.setFont(UITheme.font(Font.BOLD, 11f));
        FontMetrics fm = g2.getFontMetrics();
        double[] vx = new double[n];
        double[] vy = new double[n];

        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / n);
            double ex = cx + Math.cos(angle) * maxR;
            double ey = cy + Math.sin(angle) * maxR;

            g2.setColor(UITheme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine((int) Math.round(cx), (int) Math.round(cy), (int) Math.round(ex), (int) Math.round(ey));

            double valRatio = (values.get(i) / 100.0) * animProgress;
            vx[i] = cx + Math.cos(angle) * (maxR * valRatio);
            vy[i] = cy + Math.sin(angle) * (maxR * valRatio);

            // Axis label outside the 100% ring
            double lx = cx + Math.cos(angle) * (maxR + 18.0);
            double ly = cy + Math.sin(angle) * (maxR + 16.0);
            String label = axes.get(i);
            int tw = fm.stringWidth(label);
            int drawX = (int) Math.round(lx - tw / 2.0);
            int drawY = (int) Math.round(ly + fm.getAscent() / 2.0 - 1.0);
            drawX = Math.max(4, Math.min(w - tw - 4, drawX));
            drawY = Math.max(12, Math.min(h - 4, drawY));

            g2.setColor(i == hoveredAxis ? UITheme.PRIMARY : UITheme.TEXT_SECONDARY);
            g2.drawString(label, drawX, drawY);
        }

        // 3. Animated outward radar strength polygon (Indigo -> Violet -> Pink gradient)
        Path2D poly = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            if (i == 0) poly.moveTo(vx[i], vy[i]);
            else poly.lineTo(vx[i], vy[i]);
        }
        poly.closePath();

        LinearGradientPaint polyFill = new LinearGradientPaint(
                (float) (cx - maxR), (float) (cy - maxR),
                (float) (cx + maxR), (float) (cy + maxR),
                new float[]{0f, 0.55f, 1f},
                new Color[]{
                        Anim.withAlpha(UITheme.PRIMARY, 72),
                        Anim.withAlpha(UITheme.VIOLET, 64),
                        Anim.withAlpha(UITheme.PINK, 54)
                }
        );
        g2.setPaint(polyFill);
        g2.fill(poly);

        g2.setPaint(UITheme.createPrimaryGradient((float) (cx - maxR), (float) (cy - maxR),
                (float) (cx + maxR), (float) (cy + maxR)));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(poly);

        // 4. Vertex dots
        Color[] vertexColors = {UITheme.PRIMARY, UITheme.VIOLET, UITheme.SKY, UITheme.PINK};
        for (int i = 0; i < n; i++) {
            boolean isHov = (i == hoveredAxis);
            float r = isHov ? 6f : 4.2f;
            if (isHov) {
                g2.setColor(Anim.withAlpha(vertexColors[i % vertexColors.length], 48));
                g2.fill(new Ellipse2D.Double(vx[i] - 10, vy[i] - 10, 20, 20));
            }
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(vx[i] - r - 1.2, vy[i] - r - 1.2, (r + 1.2) * 2, (r + 1.2) * 2));
            g2.setColor(vertexColors[i % vertexColors.length]);
            g2.fill(new Ellipse2D.Double(vx[i] - r, vy[i] - r, r * 2, r * 2));
        }

        // 5. Floating value bubble on vertex hover
        if (hoveredAxis >= 0 && hoveredAxis < n) {
            String tip = axes.get(hoveredAxis) + ": " + String.format("%.1f%%", values.get(hoveredAxis));
            g2.setFont(UITheme.font(Font.BOLD, 11.5f));
            FontMetrics tfm = g2.getFontMetrics();
            int bw = tfm.stringWidth(tip) + 18;
            int bh = 26;
            int bx = (int) Math.round(vx[hoveredAxis] - bw / 2.0);
            int by = (int) Math.round(vy[hoveredAxis] - bh - 12.0);
            bx = Math.max(4, Math.min(w - bw - 4, bx));
            if (by < 4) by = (int) Math.round(vy[hoveredAxis] + 12.0);

            UITheme.paintColoredShadow(g2, bx, by, bw, bh, 8f, UITheme.PRIMARY, 3, 2);
            g2.setColor(new Color(255, 255, 255, 245));
            g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 8, 8));
            g2.setColor(UITheme.PRIMARY);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(bx + 0.5f, by + 0.5f, bw - 1f, bh - 1f, 8, 8));

            g2.setColor(UITheme.TEXT_PRIMARY);
            g2.drawString(tip, bx + 9, by + (bh + tfm.getAscent() - tfm.getDescent()) / 2);
        }

        g2.dispose();
    }
}
