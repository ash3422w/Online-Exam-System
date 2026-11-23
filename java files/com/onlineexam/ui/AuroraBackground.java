package com.onlineexam.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Reusable animated Pastel Aurora Light mesh-gradient background panel.
 *
 * - Base color #F6F7FB cached in a static BufferedImage layer
 * - 4 large soft radial-gradient pastel blobs (#C7D2FE, #BAE6FD, #FBCFE8, #DDD6FE) at 50-70% alpha
 *   drifting slowly along smooth sine paths
 * - 30 subtle floating indigo particles at ~10% alpha
 * - ~30 FPS javax.swing.Timer that automatically pauses when not showing (HierarchyListener)
 * - setIntensity(float) [0.0..1.0] to dial down visual energy (e.g., calmer Exam screen)
 */
public class AuroraBackground extends JPanel {

    public static final Color BASE_CANVAS = new Color(248, 249, 252);     // #F8F9FC
    public static final Color BLOB_INDIGO = new Color(199, 210, 254);     // #C7D2FE
    public static final Color BLOB_SKY = new Color(186, 230, 253);        // #BAE6FD
    public static final Color BLOB_PINK = new Color(251, 207, 232);       // #FBCFE8
    public static final Color BLOB_LAVENDER = new Color(221, 214, 254);   // #DDD6FE

    private static final int FPS_DELAY_MS = 42; // ~24 FPS smooth organic motion
    private static final int PARTICLE_COUNT = 30;
    private static final int BLOB_SPRITE_SIZE = 256;

    private final BlobSpec[] blobs;
    private final BufferedImage[] blobSprites;
    private final Particle[] particles;

    private BufferedImage staticLayerCache;
    private int cachedW = -1;
    private int cachedH = -1;

    private float intensity = 1.0f;
    private double timeSec = 0.0;
    private long lastTickNanos = 0L;
    private final Timer animTimer;

    private static final class BlobSpec {
        final Color color;
        final float baseAlpha;    // 0.50 .. 0.70
        final float anchorX;      // normalized 0..1
        final float anchorY;      // normalized 0..1
        final float ampX;         // normalized drift amplitude
        final float ampY;
        final float freqX;        // radians/sec
        final float freqY;
        final float phaseX;
        final float phaseY;
        final float radiusFactor; // relative to max(w, h)

        BlobSpec(Color color, float baseAlpha, float anchorX, float anchorY,
                 float ampX, float ampY, float freqX, float freqY,
                 float phaseX, float phaseY, float radiusFactor) {
            this.color = color;
            this.baseAlpha = baseAlpha;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.ampX = ampX;
            this.ampY = ampY;
            this.freqX = freqX;
            this.freqY = freqY;
            this.phaseX = phaseX;
            this.phaseY = phaseY;
            this.radiusFactor = radiusFactor;
        }
    }

    private static final class Particle {
        float nx;         // normalized x [0..1]
        float ny;         // normalized y [0..1]
        float radius;     // px [1.6 .. 3.4]
        float speedY;     // normalized upward drift per sec
        float swayAmp;    // normalized horizontal sine sway
        float swayFreq;   // radians/sec
        float phase;      // phase offset
        float alphaScale; // 0.65 .. 1.15 around 10% indigo
    }

    public AuroraBackground() {
        this(new BorderLayout());
    }

    public AuroraBackground(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        setBackground(BASE_CANVAS);

        // 4 large pastel blobs at 50-70% alpha on slow, organic sine trajectories
        this.blobs = new BlobSpec[]{
                new BlobSpec(BLOB_INDIGO,   0.66f, 0.16f, 0.20f, 0.14f, 0.12f, 0.22f, 0.17f, 0.0f,  0.8f, 0.58f),
                new BlobSpec(BLOB_SKY,      0.62f, 0.84f, 0.24f, 0.13f, 0.14f, 0.18f, 0.24f, 1.7f,  0.2f, 0.56f),
                new BlobSpec(BLOB_PINK,     0.58f, 0.78f, 0.82f, 0.15f, 0.11f, 0.15f, 0.21f, 3.1f,  2.1f, 0.54f),
                new BlobSpec(BLOB_LAVENDER, 0.64f, 0.24f, 0.80f, 0.12f, 0.13f, 0.20f, 0.16f, 4.6f,  1.2f, 0.55f)
        };

        // Pre-render soft radial-gradient blob sprites once for smooth, low-CPU 30 FPS blitting
        this.blobSprites = new BufferedImage[blobs.length];
        for (int i = 0; i < blobs.length; i++) {
            this.blobSprites[i] = createBlobSprite(blobs[i].color);
        }

        // Deterministic seed so particles look evenly balanced on every launch
        Random rng = new Random(4207L);
        this.particles = new Particle[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.nx = rng.nextFloat();
            p.ny = rng.nextFloat();
            p.radius = 1.6f + rng.nextFloat() * 1.9f;
            p.speedY = 0.006f + rng.nextFloat() * 0.011f;
            p.swayAmp = 0.008f + rng.nextFloat() * 0.016f;
            p.swayFreq = 0.35f + rng.nextFloat() * 0.55f;
            p.phase = rng.nextFloat() * (float) (Math.PI * 2.0);
            p.alphaScale = 0.70f + rng.nextFloat() * 0.45f;
            this.particles[i] = p;
        }

        this.animTimer = new Timer(FPS_DELAY_MS, e -> onTick());
        this.animTimer.setCoalesce(true);

        // Automatically start when showing and pause when hidden/minimized/disposed
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                syncTimerState();
            }
        });
    }

    /**
     * Controls overall visual intensity of blobs and particles.
     * 1.0f = full airy Pastel Aurora; 0.25f - 0.40f = calm focus mode for Exam screen.
     */
    public void setIntensity(float intensity) {
        float clamped = Anim.clamp01(intensity);
        if (Math.abs(this.intensity - clamped) > 0.001f) {
            this.intensity = clamped;
            repaint();
        }
    }

    public float getIntensity() {
        return intensity;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        syncTimerState();
    }

    @Override
    public void removeNotify() {
        animTimer.stop();
        super.removeNotify();
    }

    private void syncTimerState() {
        if (isShowing()) {
            if (!animTimer.isRunning()) {
                lastTickNanos = System.nanoTime();
                animTimer.start();
            }
        } else {
            animTimer.stop();
        }
    }

    private void onTick() {
        if (!isShowing()) {
            animTimer.stop();
            return;
        }
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win instanceof Frame && (((Frame) win).getExtendedState() & Frame.ICONIFIED) != 0) {
            return; // Minimized: pause background rendering to save CPU
        }
        long now = System.nanoTime();
        double dt = (lastTickNanos == 0L) ? (FPS_DELAY_MS / 1000.0) : ((now - lastTickNanos) / 1_000_000_000.0);
        lastTickNanos = now;
        // Clamp large jumps (e.g., after laptop sleep)
        dt = Math.min(0.1, Math.max(0.0, dt));

        // Calm intensity gently slows motion too
        double motionScale = 0.45 + 0.55 * intensity;
        timeSec += dt * motionScale;

        for (Particle p : particles) {
            p.ny -= (float) (p.speedY * dt * motionScale);
            if (p.ny < -0.04f) {
                p.ny = 1.04f;
            }
        }
        repaint();
    }

    private static BufferedImage createBlobSprite(Color color) {
        BufferedImage img = new BufferedImage(BLOB_SPRITE_SIZE, BLOB_SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        UITheme.configureGraphics(g2);

        float half = BLOB_SPRITE_SIZE / 2f;
        RadialGradientPaint paint = new RadialGradientPaint(
                new Point2D.Float(half, half),
                half,
                new float[]{0.0f, 0.42f, 0.76f, 1.0f},
                new Color[]{
                        Anim.withAlpha(color, 255),
                        Anim.withAlpha(color, 175),
                        Anim.withAlpha(color, 58),
                        Anim.withAlpha(color, 0)
                }
        );
        g2.setPaint(paint);
        g2.fillRect(0, 0, BLOB_SPRITE_SIZE, BLOB_SPRITE_SIZE);
        g2.dispose();
        return img;
    }

    private void ensureStaticLayerCache(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (staticLayerCache != null && cachedW == w && cachedH == h) {
            return;
        }
        cachedW = w;
        cachedH = h;
        staticLayerCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = staticLayerCache.createGraphics();
        UITheme.configureGraphics(g2);

        // Static Layer 1: Base #F6F7FB fill
        g2.setColor(BASE_CANVAS);
        g2.fillRect(0, 0, w, h);

        // Static Layer 2: Ultra-subtle vertical airy daylight sheen (top crisp white -> #F6F7FB)
        GradientPaint daylight = new GradientPaint(
                0, 0, new Color(255, 255, 255, 115),
                0, h, new Color(246, 247, 251, 0)
        );
        g2.setPaint(daylight);
        g2.fillRect(0, 0, w, h);

        // Static Layer 3: Whisper-faint static architectural dot mesh (indigo at 3% alpha)
        g2.setColor(Anim.withAlpha(UITheme.PRIMARY, 8));
        int spacing = 36;
        for (int y = spacing / 2; y < h; y += spacing) {
            for (int x = spacing / 2; x < w; x += spacing) {
                g2.fillRect(x, y, 1, 1);
            }
        }
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        ensureStaticLayerCache(w, h);

        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.configureGraphics(g2);

        // 1. Blit cached static base layer
        if (staticLayerCache != null) {
            g2.drawImage(staticLayerCache, 0, 0, null);
        } else {
            g2.setColor(BASE_CANVAS);
            g2.fillRect(0, 0, w, h);
        }

        // 2. Dynamic Layer A: 4 drifting pastel radial-gradient blobs (50-70% alpha scaled by intensity)
        if (intensity > 0.005f) {
            float maxDim = Math.max(w, h);
            for (int i = 0; i < blobs.length; i++) {
                BlobSpec b = blobs[i];
                float alpha = Anim.clamp01(b.baseAlpha * intensity);
                if (alpha <= 0.005f) {
                    continue;
                }

                double cx = (b.anchorX + Math.sin(timeSec * b.freqX + b.phaseX) * b.ampX) * w;
                double cy = (b.anchorY + Math.cos(timeSec * b.freqY + b.phaseY) * b.ampY) * h;
                int diameter = Math.round(maxDim * b.radiusFactor * 2f);
                int dx = (int) Math.round(cx - diameter / 2.0);
                int dy = (int) Math.round(cy - diameter / 2.0);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.drawImage(blobSprites[i], dx, dy, diameter, diameter, null);
            }

            // 3. Dynamic Layer B: ~30 subtle floating indigo dots/particles (~10% alpha)
            g2.setComposite(AlphaComposite.SrcOver);
            for (Particle p : particles) {
                int pAlpha = Math.max(0, Math.min(255, Math.round(26f * p.alphaScale * intensity)));
                if (pAlpha <= 0) {
                    continue;
                }
                double sway = Math.sin(timeSec * p.swayFreq + p.phase) * p.swayAmp;
                double px = (p.nx + sway) * w;
                double py = p.ny * h;
                g2.setColor(Anim.withAlpha(UITheme.PRIMARY, pAlpha));
                g2.fill(new Ellipse2D.Double(px - p.radius, py - p.radius, p.radius * 2.0, p.radius * 2.0));
            }
        }

        g2.dispose();
    }
}
