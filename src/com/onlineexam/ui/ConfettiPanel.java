package com.onlineexam.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * Transparent celebratory confetti burst overlay (~120 particles).
 * Colors: indigo, pink, sky, amber, mint.
 * Physics: upward/outward burst, gravity, gentle drag, rotation + flutter tilt,
 * smooth fade-out over ~3 seconds, then stops its javax.swing.Timer automatically.
 */
public class ConfettiPanel extends JComponent {

    private static final int PARTICLE_COUNT = 120;
    private static final double DURATION_SEC = 3.0;
    private static final Color[] PALETTE = {
            new Color(79, 70, 229),   // Indigo #4F46E5
            new Color(236, 72, 153),  // Pink   #EC4899
            new Color(14, 165, 233),  // Sky    #0EA5E9
            new Color(245, 158, 11),  // Amber  #F59E0B
            new Color(16, 185, 129)   // Mint   #10B981
    };

    private static final class Piece {
        double x, y;
        double vx, vy;
        double angle, angularVel;
        double tilt, tiltVel;
        float width, height;
        boolean circle;
        Color color;
    }

    private final Piece[] pieces = new Piece[PARTICLE_COUNT];
    private boolean initialized = false;
    private double elapsedSec = 0.0;
    private long lastNanos = 0L;
    private Timer timer;
    private Runnable onFinished;

    public ConfettiPanel() {
        setOpaque(false);
    }

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public void startBurst() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        initialized = false;
        elapsedSec = 0.0;
        lastNanos = System.nanoTime();

        timer = new Timer(Anim.TICK_MS, e -> onTick());
        timer.setCoalesce(true);
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    @Override
    public void removeNotify() {
        stop();
        super.removeNotify();
    }

    // Transparent to mouse clicks so underlying UI stays interactive
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    private void initParticles(int w, int h) {
        Random rng = new Random();
        double originX = w * 0.5;
        double originY = Math.min(h * 0.28, 220.0);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Piece p = new Piece();
            // Spread burst across upper third with outward fan
            p.x = originX + (rng.nextDouble() - 0.5) * Math.min(w * 0.55, 420.0);
            p.y = originY + (rng.nextDouble() - 0.5) * 60.0;

            double angle = -Math.PI / 2.0 + (rng.nextDouble() - 0.5) * 1.9;
            double speed = 180.0 + rng.nextDouble() * 340.0;
            p.vx = Math.cos(angle) * speed;
            p.vy = Math.sin(angle) * speed - 60.0;

            p.angle = rng.nextDouble() * Math.PI * 2.0;
            p.angularVel = (rng.nextDouble() - 0.5) * 9.0;
            p.tilt = rng.nextDouble() * Math.PI * 2.0;
            p.tiltVel = 4.0 + rng.nextDouble() * 6.0;

            p.circle = rng.nextFloat() < 0.22f;
            p.width = p.circle ? (5f + rng.nextFloat() * 4f) : (6f + rng.nextFloat() * 6f);
            p.height = p.circle ? p.width : (4f + rng.nextFloat() * 4.5f);
            p.color = PALETTE[i % PALETTE.length];
            pieces[i] = p;
        }
        initialized = true;
    }

    private void onTick() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (!initialized) {
            initParticles(w, h);
        }

        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        dt = Math.min(0.05, Math.max(0.0, dt));
        elapsedSec += dt;

        double gravity = 520.0;
        double drag = Math.pow(0.985, dt * 60.0);

        for (Piece p : pieces) {
            p.vx *= drag;
            p.vy = p.vy * drag + gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.angle += p.angularVel * dt;
            p.tilt += p.tiltVel * dt;
        }

        if (elapsedSec >= DURATION_SEC) {
            stop();
            repaint();
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!initialized || elapsedSec >= DURATION_SEC) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.configureGraphics(g2);

        // Smooth fade-out during the last 0.9s
        float globalAlpha = 1.0f;
        double fadeStart = DURATION_SEC - 0.9;
        if (elapsedSec > fadeStart) {
            float t = (float) ((elapsedSec - fadeStart) / 0.9);
            globalAlpha = 1.0f - Anim.easeInOutCubic(t);
        }
        if (globalAlpha <= 0.01f) {
            g2.dispose();
            return;
        }

        int h = getHeight();
        for (Piece p : pieces) {
            if (p.y > h + 30) continue;

            Graphics2D pg = (Graphics2D) g2.create();
            pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, globalAlpha));
            pg.translate(p.x, p.y);
            pg.rotate(p.angle);
            double flutter = Math.max(0.25, Math.abs(Math.cos(p.tilt)));
            pg.scale(1.0, flutter);

            pg.setColor(p.color);
            if (p.circle) {
                pg.fill(new Ellipse2D.Float(-p.width / 2f, -p.height / 2f, p.width, p.height));
            } else {
                pg.fill(new RoundRectangle2D.Float(-p.width / 2f, -p.height / 2f, p.width, p.height, 2.5f, 2.5f));
            }
            pg.dispose();
        }

        g2.dispose();
    }
}
