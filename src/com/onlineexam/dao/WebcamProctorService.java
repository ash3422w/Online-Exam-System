package com.onlineexam.dao;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.GlassDialog;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service for managing webcam device checks, student consent disclosures,
 * and background periodic snapshot captures (max 320px wide, JPEG quality 60)
 * at random 60-90 second intervals during proctored exams.
 */
public final class WebcamProctorService {

    public static final int MAX_SNAPSHOT_WIDTH = 320;
    public static final float JPEG_QUALITY = 0.60f;
    private static final Random RANDOM = new Random();

    private WebcamProctorService() {
    }

    /**
     * Checks whether the webcam-capture library is present and loadable.
     */
    public static boolean isAvailable() {
        try {
            Class.forName("com.github.sarxos.webcam.Webcam");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Checks whether an active physical webcam is detected on the machine.
     */
    public static boolean isCameraDetected() {
        if (!isAvailable()) return false;
        try {
            List<Webcam> webcams = Webcam.getWebcams();
            return webcams != null && !webcams.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns the name of the default webcam, or null if none.
     */
    public static String getCameraName() {
        if (!isAvailable()) return null;
        try {
            Webcam webcam = Webcam.getDefault();
            return webcam != null ? webcam.getName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Shows a student consent & camera verification modal dialog.
     * Explains what is captured, how long it is kept, and who can view it.
     * If declined or no camera is detected, the student cannot start this exam.
     * Returns true ONLY if consent is given and an active camera is present.
     */
    public static boolean showConsentDialog(Component parent, String examTitle) {
        final boolean[] agreed = {false};
        boolean cameraExists = isCameraDetected();
        String camName = cameraExists ? getCameraName() : null;

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(10, 16, 12, 16));

        // Header Notice Card
        GlassPanel headerCard = new GlassPanel(12);
        headerCard.setFillColor(new Color(245, 247, 255));
        headerCard.setBorderColor(new Color(224, 231, 255));
        headerCard.setLayout(new BorderLayout(10, 0));
        headerCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        headerCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel noticeTitle = new JLabel("Webcam Proctoring Required for this Exam");
        noticeTitle.setFont(UITheme.font(Font.BOLD, 14f));
        noticeTitle.setForeground(UITheme.PRIMARY);

        JLabel examNameLbl = new JLabel("Exam: " + (examTitle != null ? examTitle : "Selected Examination"));
        examNameLbl.setFont(UITheme.font(Font.PLAIN, 12f));
        examNameLbl.setForeground(UITheme.TEXT_SECONDARY);

        JPanel headStack = new JPanel(new GridLayout(2, 1, 0, 2));
        headStack.setOpaque(false);
        headStack.add(noticeTitle);
        headStack.add(examNameLbl);
        headerCard.add(headStack, BorderLayout.CENTER);

        content.add(headerCard);
        content.add(Box.createVerticalStrut(14));

        // Disclosure Grid
        JPanel disclosuresPanel = new JPanel(new GridLayout(3, 1, 0, 10));
        disclosuresPanel.setOpaque(false);
        disclosuresPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        disclosuresPanel.add(createDisclosureItem(
                "What is captured?",
                "Periodic low-resolution still photos (max 320 px wide) taken automatically every 60 to 90 seconds at random intervals during the exam. No continuous video streaming or audio recording occurs.",
                UITheme.IconType.CHECKLIST,
                UITheme.PRIMARY
        ));

        disclosuresPanel.add(createDisclosureItem(
                "How long is it kept?",
                "Snapshots are encrypted and stored in the proctor audit archive for 30 days, after which they are automatically permanently purged by the retention policy.",
                UITheme.IconType.CLOCK,
                UITheme.SKY
        ));

        disclosuresPanel.add(createDisclosureItem(
                "Who can see it?",
                "Only authorized institution administrators and certified proctors strictly for identity verification and anti-cheating audit trail reviews.",
                UITheme.IconType.USER,
                UITheme.VIOLET
        ));

        content.add(disclosuresPanel);
        content.add(Box.createVerticalStrut(16));

        // Device Status Box
        GlassPanel deviceBox = new GlassPanel(10);
        deviceBox.setLayout(new BorderLayout(12, 0));
        deviceBox.setBorder(new EmptyBorder(10, 14, 10, 14));
        deviceBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (cameraExists) {
            deviceBox.setFillColor(UITheme.SUCCESS_SOFT);
            deviceBox.setBorderColor(Anim.withAlpha(UITheme.SUCCESS, 80));
            JLabel devIcon = new JLabel("\u2714");
            devIcon.setFont(UITheme.font(Font.BOLD, 18f));
            devIcon.setForeground(UITheme.SUCCESS_TEXT);

            JLabel devText = new JLabel("Camera detected: " + (camName != null ? camName : "Active Webcam"));
            devText.setFont(UITheme.font(Font.BOLD, 12.5f));
            devText.setForeground(UITheme.SUCCESS_TEXT);

            deviceBox.add(devIcon, BorderLayout.WEST);
            deviceBox.add(devText, BorderLayout.CENTER);
        } else {
            deviceBox.setFillColor(UITheme.DANGER_SOFT);
            deviceBox.setBorderColor(Anim.withAlpha(UITheme.DANGER, 90));
            JLabel devIcon = new JLabel("\u26A0");
            devIcon.setFont(UITheme.font(Font.BOLD, 18f));
            devIcon.setForeground(UITheme.DANGER_TEXT);

            JLabel devText = new JLabel("No camera detected! A functional webcam is mandatory to take this exam.");
            devText.setFont(UITheme.font(Font.BOLD, 12.5f));
            devText.setForeground(UITheme.DANGER_TEXT);

            deviceBox.add(devIcon, BorderLayout.WEST);
            deviceBox.add(devText, BorderLayout.CENTER);
        }
        content.add(deviceBox);

        // Buttons Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);

        UITheme.ModernButton declineBtn = UITheme.secondaryButton(cameraExists ? "Decline Consent" : "Cancel");
        UITheme.ModernButton agreeBtn = UITheme.gradientPrimaryButton("I Agree & Start Exam \u2192");
        agreeBtn.setPreferredSize(new Dimension(190, 38));
        agreeBtn.setEnabled(cameraExists);

        footer.add(declineBtn);
        if (cameraExists) {
            footer.add(agreeBtn);
        }

        Window windowOwner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        GlassDialog dialog = new GlassDialog(
                windowOwner,
                "Webcam Proctoring Consent & Device Check",
                "Review the proctoring disclosures and confirm your camera before beginning",
                content,
                footer,
                640,
                520
        );

        declineBtn.addActionListener(e -> {
            agreed[0] = false;
            dialog.closeAnimated(null);
        });

        agreeBtn.addActionListener(e -> {
            agreed[0] = true;
            dialog.closeAnimated(null);
        });

        dialog.setVisible(true);
        return agreed[0];
    }

    private static JPanel createDisclosureItem(String heading, String body, UITheme.IconType iconType, Color color) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);

        UITheme.VectorIcon icon = new UITheme.VectorIcon(iconType, color, Anim.withAlpha(color, 25), 32);
        p.add(icon, BorderLayout.WEST);

        JPanel textStack = new JPanel(new GridLayout(2, 1, 0, 2));
        textStack.setOpaque(false);

        JLabel h = new JLabel(heading);
        h.setFont(UITheme.font(Font.BOLD, 12.5f));
        h.setForeground(UITheme.TEXT_PRIMARY);

        JLabel b = new JLabel("<html>" + body + "</html>");
        b.setFont(UITheme.font(Font.PLAIN, 11f));
        b.setForeground(UITheme.TEXT_SECONDARY);

        textStack.add(h);
        textStack.add(b);
        p.add(textStack, BorderLayout.CENTER);
        return p;
    }

    /**
     * Resizes a raw image to max maxWidth and compresses it to JPEG format at specified quality.
     */
    public static byte[] compressToJpeg(BufferedImage rawImage, int maxWidth, float quality) throws IOException {
        if (rawImage == null) return null;
        int origW = rawImage.getWidth();
        int origH = rawImage.getHeight();
        int targetW = origW;
        int targetH = origH;
        if (targetW > maxWidth) {
            targetW = maxWidth;
            targetH = Math.max(1, (int) Math.round((double) origH * maxWidth / origW));
        }

        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(rawImage, 0, 0, targetW, targetH, null);
        g2.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.max(0.05f, Math.min(1.0f, quality)));
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(scaled, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    /**
     * Represents an active background proctoring webcam capture session for an exam.
     */
    public static class ProctoringSession {
        private final String examSessionId;
        private final String userId;
        private final Integer examId;
        private final Consumer<String> statusCallback;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread workerThread;
        private Webcam webcam;

        public ProctoringSession(String examSessionId, String userId, Integer examId, Consumer<String> statusCallback) {
            this.examSessionId = examSessionId;
            this.userId = userId;
            this.examId = examId;
            this.statusCallback = statusCallback;
        }

        public synchronized void start() {
            if (running.get()) return;
            running.set(true);

            workerThread = new Thread(() -> runCaptureLoop(), "webcam-proctor-" + examSessionId);
            workerThread.setDaemon(true);
            workerThread.start();
        }

        private void runCaptureLoop() {
            try {
                webcam = Webcam.getDefault();
                if (webcam == null) {
                    notifyStatus("Camera Unavailable");
                    ProctorDAO.logEvent(examSessionId, userId, examId, null,
                            ProctorDAO.EVENT_CAMERA_LOST, "No default webcam detected on exam start");
                    return;
                }

                // Prefer small QVGA 320x240 resolution natively if supported
                try {
                    Dimension qvga = WebcamResolution.QVGA.getSize();
                    for (Dimension d : webcam.getViewSizes()) {
                        if (d.equals(qvga)) {
                            webcam.setViewSize(qvga);
                            break;
                        }
                    }
                } catch (Throwable ignored) {}

                boolean opened = webcam.open();
                if (!opened) {
                    notifyStatus("Camera Lost");
                    ProctorDAO.logEvent(examSessionId, userId, examId, null,
                            ProctorDAO.EVENT_CAMERA_LOST, "Webcam failed to open device handle");
                } else {
                    notifyStatus("Camera Active");
                }

                // Take an initial baseline snapshot 4 seconds in
                sleepInterruptible(4000);
                if (running.get()) {
                    captureAndSave("BASELINE");
                }

                while (running.get()) {
                    // Random interval between 60 and 90 seconds
                    int intervalSeconds = 60 + RANDOM.nextInt(31);
                    sleepInterruptible(intervalSeconds * 1000L);
                    if (!running.get()) break;

                    captureAndSave("PERIODIC");
                }
            } catch (Throwable t) {
                System.err.println("[WebcamProctorService] Worker error: " + t.getMessage());
            } finally {
                closeWebcam();
            }
        }

        private void captureAndSave(String flag) {
            if (webcam == null) return;
            try {
                if (!webcam.isOpen()) {
                    boolean reOpened = webcam.open();
                    if (!reOpened) {
                        notifyStatus("Camera Lost");
                        ProctorDAO.logEvent(examSessionId, userId, examId, null,
                                ProctorDAO.EVENT_CAMERA_LOST, "Webcam disconnected or closed");
                        return;
                    }
                }

                BufferedImage img = webcam.getImage();
                if (img == null) {
                    notifyStatus("Camera Lost");
                    ProctorDAO.logEvent(examSessionId, userId, examId, null,
                            ProctorDAO.EVENT_CAMERA_LOST, "Webcam returned null frame (camera disconnected)");
                    return;
                }

                notifyStatus("Camera Active");
                byte[] jpegBytes = compressToJpeg(img, MAX_SNAPSHOT_WIDTH, JPEG_QUALITY);
                if (jpegBytes != null && jpegBytes.length > 0) {
                    ProctorDAO.saveSnapshot(examSessionId, userId, jpegBytes, flag);
                }
            } catch (Throwable ex) {
                notifyStatus("Camera Lost");
                ProctorDAO.logEvent(examSessionId, userId, examId, null,
                        ProctorDAO.EVENT_CAMERA_LOST, "Webcam capture exception: " + ex.getMessage());
            }
        }

        private void sleepInterruptible(long millis) {
            long remaining = millis;
            while (remaining > 0 && running.get()) {
                long step = Math.min(1000L, remaining);
                try {
                    Thread.sleep(step);
                } catch (InterruptedException e) {
                    break;
                }
                remaining -= step;
            }
        }

        private void notifyStatus(String status) {
            if (statusCallback != null) {
                SwingUtilities.invokeLater(() -> statusCallback.accept(status));
            }
        }

        public synchronized void stop() {
            running.set(false);
            if (workerThread != null) {
                workerThread.interrupt();
            }
            closeWebcam();
        }

        private void closeWebcam() {
            try {
                if (webcam != null && webcam.isOpen()) {
                    webcam.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
