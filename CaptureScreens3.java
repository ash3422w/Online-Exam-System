package com.onlineexam.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.student.*;

public class CaptureScreens3 {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        } catch (Exception ignored) {}

        File dir = new File("report_images");
        if (!dir.exists()) dir.mkdirs();

        SwingUtilities.invokeLater(() -> {
            try {
                ExamStore.clearSession();
                ExamStore.setSelectedExam(null);
                ExamStore.setSelectedSubject(1);
                ExamStore.setExamQuestionCount(5);

                ExamModule exam = new ExamModule();
                captureFrame(exam, new File(dir, "screen_05_exam_module.png"), 1200, 800);
                exam.dispose();

                System.out.println("EXAM_MODULE_CAPTURED_SUCCESSFULLY");
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static void captureFrame(JFrame frame, File outputFile, int width, int height) {
        try {
            frame.setSize(width, height);
            frame.addNotify();
            frame.validate();
            frame.doLayout();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            frame.paint(g2);
            g2.dispose();

            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Captured: " + outputFile.getName());
        } catch (Exception ex) {
            System.err.println("Failed capturing " + outputFile.getName() + ": " + ex.getMessage());
        }
    }
}
