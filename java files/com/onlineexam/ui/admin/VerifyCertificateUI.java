package com.onlineexam.ui.admin;

import com.onlineexam.dao.CertificateService;
import com.onlineexam.ui.Anim;
import com.onlineexam.ui.AuroraBackground;
import com.onlineexam.ui.GlassPanel;
import com.onlineexam.ui.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

/**
 * Public and Admin verification screen for examination certificates.
 * Reads solely from certificates joined to attempts and exposes strictly
 * student name, exam title, score/grade, and dates.
 */
public class VerifyCertificateUI extends JFrame {

    private final JTextField codeField;
    private final JButton verifyBtn;
    private final JPanel resultContainer;

    public VerifyCertificateUI() {
        this("");
    }

    public VerifyCertificateUI(String initialCode) {
        setTitle("Verify Certificate - Online Examination System");
        setSize(640, 600);
        setMinimumSize(new Dimension(540, 520));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        AuroraBackground root = new AuroraBackground(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        // Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel kicker = new JLabel("ACADEMIC CREDENTIAL VERIFICATION");
        kicker.setFont(UITheme.font(Font.BOLD, 11f));
        kicker.setForeground(UITheme.PRIMARY);

        JLabel title = new JLabel("Verify Certificate Authenticity");
        title.setFont(UITheme.font(Font.BOLD, 22f));
        title.setForeground(UITheme.TEXT_PRIMARY);

        JLabel desc = new JLabel("Enter the certificate ID (e.g. CERT-2026-XXXXXXXX) or scan the QR code to confirm official issuance.");
        desc.setFont(UITheme.font(Font.PLAIN, 12.5f));
        desc.setForeground(UITheme.TEXT_SECONDARY);
        desc.setBorder(new EmptyBorder(2, 0, 10, 0));

        header.add(kicker);
        header.add(Box.createVerticalStrut(2));
        header.add(title);
        header.add(desc);

        // Input Bar
        GlassPanel searchCard = new GlassPanel(14);
        searchCard.setLayout(new BorderLayout(10, 0));
        searchCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        codeField = new JTextField();
        codeField.setFont(UITheme.font(Font.BOLD, 14f));
        codeField.setForeground(UITheme.TEXT_PRIMARY);
        codeField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        if (initialCode != null && !initialCode.trim().isEmpty()) {
            codeField.setText(initialCode.trim().toUpperCase(Locale.ENGLISH));
        }

        verifyBtn = UITheme.gradientPrimaryButton("Verify Code \u2192");
        verifyBtn.setPreferredSize(new Dimension(140, 42));
        verifyBtn.addActionListener(this::performVerification);

        searchCard.add(codeField, BorderLayout.CENTER);
        searchCard.add(verifyBtn, BorderLayout.EAST);

        // Result Container
        resultContainer = new JPanel(new BorderLayout());
        resultContainer.setOpaque(false);
        renderInitialPrompt();

        JPanel centerStack = new JPanel(new BorderLayout(0, 14));
        centerStack.setOpaque(false);
        centerStack.add(searchCard, BorderLayout.NORTH);
        centerStack.add(resultContainer, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(centerStack, BorderLayout.CENTER);

        add(root);
        getRootPane().setDefaultButton(verifyBtn);
        setVisible(true);

        if (initialCode != null && !initialCode.trim().isEmpty()) {
            performVerification(null);
        }
    }

    private void renderInitialPrompt() {
        resultContainer.removeAll();
        GlassPanel promptCard = new GlassPanel(14);
        promptCard.setLayout(new GridBagLayout());
        promptCard.setBorder(new EmptyBorder(30, 20, 30, 20));

        JLabel promptLbl = new JLabel("Enter a certificate code above to verify its credentials.", SwingConstants.CENTER);
        promptLbl.setFont(UITheme.font(Font.PLAIN, 13f));
        promptLbl.setForeground(UITheme.TEXT_MUTED);

        promptCard.add(promptLbl);
        resultContainer.add(promptCard, BorderLayout.CENTER);
        resultContainer.revalidate();
        resultContainer.repaint();
    }

    private void performVerification(ActionEvent e) {
        String code = codeField.getText() != null ? codeField.getText().trim() : "";
        if (code.isEmpty()) {
            UITheme.showToast(this, "Empty Code", "Please enter a certificate ID to verify.", UITheme.ToastType.WARNING);
            codeField.requestFocus();
            return;
        }

        verifyBtn.setEnabled(false);
        verifyBtn.setText("Verifying...");
        resultContainer.removeAll();

        // Shimmer / loading card
        GlassPanel loadingCard = new GlassPanel(14);
        loadingCard.setLayout(new GridBagLayout());
        loadingCard.setBorder(new EmptyBorder(30, 20, 30, 20));
        JLabel loadLbl = new JLabel("Checking database records...", SwingConstants.CENTER);
        loadLbl.setFont(UITheme.font(Font.PLAIN, 13f));
        loadLbl.setForeground(UITheme.TEXT_SECONDARY);
        loadingCard.add(loadLbl);
        resultContainer.add(loadingCard, BorderLayout.CENTER);
        resultContainer.revalidate();
        resultContainer.repaint();

        new SwingWorker<CertificateService.VerificationResult, Void>() {
            @Override
            protected CertificateService.VerificationResult doInBackground() {
                return CertificateService.verifyCertificate(code);
            }

            @Override
            protected void done() {
                verifyBtn.setEnabled(true);
                verifyBtn.setText("Verify Code \u2192");
                try {
                    CertificateService.VerificationResult res = get();
                    displayResult(res);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    displayResult(CertificateService.VerificationResult.notFound(code));
                }
            }
        }.execute();
    }

    private void displayResult(CertificateService.VerificationResult res) {
        resultContainer.removeAll();

        GlassPanel card = new GlassPanel(16);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));

        if (res != null && res.isValid) {
            // Valid Result
            JPanel topStatus = new JPanel(new BorderLayout(14, 0));
            topStatus.setOpaque(false);

            // Animated Check Icon
            AnimatedStatusGlyph checkGlyph = new AnimatedStatusGlyph(true);
            topStatus.add(checkGlyph, BorderLayout.WEST);

            JPanel textStack = new JPanel();
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            textStack.setOpaque(false);

            JLabel statBadge = UITheme.createBadge("\u2713 OFFICIAL & VERIFIED", UITheme.SUCCESS_SOFT, UITheme.SUCCESS_TEXT);
            statBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel codeLbl = new JLabel(res.certCode);
            codeLbl.setFont(UITheme.font(Font.BOLD, 18f));
            codeLbl.setForeground(UITheme.TEXT_PRIMARY);
            codeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            textStack.add(statBadge);
            textStack.add(Box.createVerticalStrut(4));
            textStack.add(codeLbl);
            topStatus.add(textStack, BorderLayout.CENTER);

            // Details Grid
            JPanel details = new JPanel(new GridLayout(0, 2, 14, 12));
            details.setOpaque(false);
            details.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                    new EmptyBorder(14, 0, 4, 0)
            ));

            details.add(createDataCell("STUDENT NAME", res.studentName != null ? res.studentName : res.username));
            details.add(createDataCell("EXAMINATION", res.examTitle));
            details.add(createDataCell("SUBJECT", res.subjectName != null ? res.subjectName : "General"));
            details.add(createDataCell("SCORE & GRADE", res.scoreText + " (" + res.percentageText + ") \u2022 Grade " + res.grade));
            details.add(createDataCell("EXAM DATE", res.examDate));
            details.add(createDataCell("CERTIFICATE ISSUED", res.issueDate));

            card.add(topStatus, BorderLayout.NORTH);
            card.add(details, BorderLayout.CENTER);

            checkGlyph.startAnimation();
        } else {
            // Not Found Result
            JPanel topStatus = new JPanel(new BorderLayout(14, 0));
            topStatus.setOpaque(false);

            // Animated Cross Icon
            AnimatedStatusGlyph crossGlyph = new AnimatedStatusGlyph(false);
            topStatus.add(crossGlyph, BorderLayout.WEST);

            JPanel textStack = new JPanel();
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            textStack.setOpaque(false);

            JLabel statBadge = UITheme.createBadge("\u2717 NOT FOUND", UITheme.DANGER_SOFT, UITheme.DANGER_TEXT);
            statBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel codeLbl = new JLabel("Certificate Not Found");
            codeLbl.setFont(UITheme.font(Font.BOLD, 18f));
            codeLbl.setForeground(UITheme.DANGER_TEXT);
            codeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel msgLbl = new JLabel("<html>No official examination certificate matching <b>"
                    + (res != null ? res.certCode : "") + "</b> was found in the database.<br>Please ensure the ID was typed correctly or contact the examination authority.</html>");
            msgLbl.setFont(UITheme.font(Font.PLAIN, 12.5f));
            msgLbl.setForeground(UITheme.TEXT_SECONDARY);
            msgLbl.setBorder(new EmptyBorder(6, 0, 0, 0));
            msgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            textStack.add(statBadge);
            textStack.add(Box.createVerticalStrut(4));
            textStack.add(codeLbl);
            textStack.add(msgLbl);
            topStatus.add(textStack, BorderLayout.CENTER);

            card.add(topStatus, BorderLayout.CENTER);
            crossGlyph.startAnimation();
        }

        resultContainer.add(card, BorderLayout.CENTER);
        resultContainer.revalidate();
        resultContainer.repaint();
    }

    private JPanel createDataCell(String label, String value) {
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);

        JLabel l = new JLabel(label);
        l.setFont(UITheme.font(Font.BOLD, 10.5f));
        l.setForeground(UITheme.TEXT_MUTED);

        JLabel v = new JLabel(value != null ? value : "-");
        v.setFont(UITheme.font(Font.BOLD, 13f));
        v.setForeground(UITheme.TEXT_PRIMARY);
        v.setBorder(new EmptyBorder(2, 0, 0, 0));

        cell.add(l);
        cell.add(v);
        return cell;
    }

    // ================= ANIMATED STATUS GLYPH (CHECK / CROSS) =================

    private static class AnimatedStatusGlyph extends JComponent {
        private final boolean isSuccess;
        private float scale = 0.5f;

        AnimatedStatusGlyph(boolean isSuccess) {
            this.isSuccess = isSuccess;
            setPreferredSize(new Dimension(52, 52));
            setMinimumSize(new Dimension(52, 52));
            setOpaque(false);
        }

        void startAnimation() {
            scale = 0.4f;
            Anim.animate(380, p -> {
                scale = 0.4f + 0.6f * Anim.easeOutBack(p);
                repaint();
            }, () -> {
                scale = 1.0f;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.configureGraphics(g2);
            int sz = Math.min(getWidth(), getHeight());
            float cx = sz / 2f;
            float cy = sz / 2f;

            g2.translate((1f - scale) * cx, (1f - scale) * cy);
            g2.scale(scale, scale);

            Color bg = isSuccess ? UITheme.SUCCESS_SOFT : UITheme.DANGER_SOFT;
            Color fg = isSuccess ? UITheme.SUCCESS : UITheme.DANGER;

            // Background circle
            g2.setColor(bg);
            g2.fill(new Ellipse2D.Float(2, 2, sz - 4, sz - 4));
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Float(2, 2, sz - 4, sz - 4));

            // Checkmark or cross
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (isSuccess) {
                // Checkmark
                g2.drawLine((int) (sz * 0.28f), (int) (sz * 0.52f), (int) (sz * 0.44f), (int) (sz * 0.70f));
                g2.drawLine((int) (sz * 0.44f), (int) (sz * 0.70f), (int) (sz * 0.74f), (int) (sz * 0.32f));
            } else {
                // Cross
                g2.drawLine((int) (sz * 0.32f), (int) (sz * 0.32f), (int) (sz * 0.68f), (int) (sz * 0.68f));
                g2.drawLine((int) (sz * 0.68f), (int) (sz * 0.32f), (int) (sz * 0.32f), (int) (sz * 0.68f));
            }

            g2.dispose();
        }
    }
}
