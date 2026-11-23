package com.onlineexam.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pastel Aurora Light modal glass dialog.
 * Uses a soft airy white/light-slate frosted veil behind the centered GlassPanel
 * (LIGHT THEME ONLY — never black), with smooth fade + easeOutBack scale-in.
 */
public class GlassDialog extends JDialog {

    private final GlassPanel card;
    private float veilAlpha = 0f;
    private boolean closing = false;

    public GlassDialog(Window owner, String title, String subtitle, JComponent body, JPanel footerButtons, int cardWidth, int cardHeight) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        // Cover owner window bounds cleanly so the soft white/slate veil frames the app
        if (owner != null && owner.isShowing()) {
            setBounds(owner.getBounds());
        } else {
            setSize(Math.max(cardWidth + 120, 680), Math.max(cardHeight + 120, 480));
            setLocationRelativeTo(owner);
        }

        // Soft airy white/light-slate translucent veil (NEVER black)
        JPanel veil = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.configureGraphics(g2);
                int w = getWidth();
                int h = getHeight();

                // Frosted white/light-slate mist
                g2.setColor(new Color(241, 245, 249, Math.round(178f * veilAlpha)));
                g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(148, 163, 184, Math.round(38f * veilAlpha)));
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        veil.setOpaque(false);

        card = new GlassPanel(18);
        card.setFillColor(new Color(255, 255, 255, 248));
        card.setShadowColor(UITheme.PRIMARY);
        card.setSpotlightEnabled(false);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(24, 28, 22, 28));
        card.setPreferredSize(new Dimension(cardWidth, cardHeight));
        card.setAlpha(0f);
        card.setScale(0.92f);

        // Header
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);

        JPanel headText = new JPanel();
        headText.setLayout(new BoxLayout(headText, BoxLayout.Y_AXIS));
        headText.setOpaque(false);

        JLabel titleLbl = new JLabel(title != null ? title : "");
        titleLbl.setFont(UITheme.font(Font.BOLD, 18f));
        titleLbl.setForeground(UITheme.TEXT_PRIMARY);
        headText.add(titleLbl);

        if (subtitle != null && !subtitle.isEmpty()) {
            JLabel subLbl = new JLabel(subtitle);
            subLbl.setFont(UITheme.font(Font.PLAIN, 12f));
            subLbl.setForeground(UITheme.TEXT_SECONDARY);
            subLbl.setBorder(new EmptyBorder(3, 0, 0, 0));
            headText.add(subLbl);
        }

        // Subtle close X button
        JButton closeX = new JButton("\u2715") {
            private boolean hov = false;
            {
                setFont(UITheme.font(Font.BOLD, 13f));
                setForeground(UITheme.TEXT_MUTED);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                setPreferredSize(new Dimension(30, 30));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hov = true; setForeground(UITheme.TEXT_PRIMARY); repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hov = false; setForeground(UITheme.TEXT_MUTED); repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (hov) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    UITheme.configureGraphics(g2);
                    g2.setColor(UITheme.PRIMARY_SOFT);
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        closeX.addActionListener(e -> closeAnimated(null));

        head.add(headText, BorderLayout.CENTER);
        head.add(closeX, BorderLayout.EAST);

        card.add(head, BorderLayout.NORTH);
        if (body != null) {
            card.add(body, BorderLayout.CENTER);
        }
        if (footerButtons != null) {
            card.add(footerButtons, BorderLayout.SOUTH);
        }

        veil.add(card);
        setContentPane(veil);

        // Escape key dismisses cleanly
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "glassDialogEsc");
        getRootPane().getActionMap().put("glassDialogEsc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeAnimated(null);
            }
        });
    }

    public void showAnimated() {
        SwingUtilities.invokeLater(() -> Anim.animate(230, p -> {
            veilAlpha = Anim.easeOutCubic(p);
            card.setAlpha(Anim.easeOutCubic(p));
            card.setScale(0.92f + 0.08f * Anim.easeOutBack(p));
            repaint();
        }, () -> {
            veilAlpha = 1f;
            card.setAlpha(1f);
            card.setScale(1f);
            repaint();
        }));
        setVisible(true);
    }

    public void closeAnimated(Runnable afterClose) {
        if (closing) return;
        closing = true;
        Anim.animate(160, p -> {
            float inv = 1f - Anim.easeOutCubic(p);
            veilAlpha = inv;
            card.setAlpha(inv);
            card.setScale(0.94f + 0.06f * inv);
            repaint();
        }, () -> {
            dispose();
            if (afterClose != null) {
                afterClose.run();
            }
        });
    }

    public GlassPanel getCard() {
        return card;
    }

    // ================= CONVENIENCE STATIC FACTORIES =================

    public static boolean showConfirm(
            Window owner, String title, String message,
            String confirmText, String cancelText, boolean dangerAction) {
        AtomicBoolean confirmed = new AtomicBoolean(false);

        JLabel msg = new JLabel("<html><div style='line-height:1.45;'>"
                + (message == null ? "" : message.replace("\n", "<br>")) + "</div></html>");
        msg.setFont(UITheme.font(Font.PLAIN, 13.5f));
        msg.setForeground(UITheme.TEXT_SECONDARY);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);

        UITheme.ModernButton cancelBtn = UITheme.secondaryButton(cancelText != null ? cancelText : "Cancel");
        cancelBtn.setPreferredSize(new Dimension(118, 38));

        UITheme.ModernButton okBtn = dangerAction
                ? UITheme.dangerButton(confirmText != null ? confirmText : "Delete")
                : UITheme.gradientPrimaryButton(confirmText != null ? confirmText : "Confirm");
        okBtn.setPreferredSize(new Dimension(136, 38));

        footer.add(cancelBtn);
        footer.add(okBtn);

        GlassDialog dialog = new GlassDialog(owner, title, null, msg, footer, 440, 225);
        if (dangerAction) {
            dialog.getCard().setShadowColor(UITheme.DANGER);
            dialog.getCard().setTopAccentColor(UITheme.DANGER);
        }
        cancelBtn.addActionListener(e -> dialog.closeAnimated(null));
        okBtn.addActionListener(e -> {
            confirmed.set(true);
            dialog.closeAnimated(null);
        });

        dialog.showAnimated();
        return confirmed.get();
    }

    public static void showCustom(
            Window owner, String title, String subtitle,
            JComponent content, String closeText, int width, int height) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);

        UITheme.ModernButton closeBtn = UITheme.gradientPrimaryButton(closeText != null ? closeText : "Done");
        closeBtn.setPreferredSize(new Dimension(120, 38));
        footer.add(closeBtn);

        GlassDialog dialog = new GlassDialog(owner, title, subtitle, content, footer, width, height);
        closeBtn.addActionListener(e -> dialog.closeAnimated(null));
        dialog.showAnimated();
    }
}
