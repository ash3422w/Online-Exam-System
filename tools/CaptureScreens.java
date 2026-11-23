package com.onlineexam.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.onlineexam.ui.student.*;
import com.onlineexam.ui.admin.*;

public class CaptureScreens {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        } catch (Exception ignored) {}

        File dir = new File("report_images");
        if (!dir.exists()) dir.mkdirs();

        SwingUtilities.invokeLater(() -> {
            try {
                // 1. LoginUI
                LoginUI login = new LoginUI();
                captureFrame(login, new File(dir, "screen_01_login.png"), 1050, 680);
                login.dispose();

                // 2. StudentDashboard
                StudentDashboard student = new StudentDashboard();
                captureFrame(student, new File(dir, "screen_02_student_dashboard.png"), 1200, 800);
                student.dispose();

                // 3. SubjectSelectionUI
                SubjectSelectionUI subject = new SubjectSelectionUI();
                captureFrame(subject, new File(dir, "screen_03_subject_selection.png"), 1050, 700);
                subject.dispose();

                // 4. InstructionsUI
                InstructionsUI instr = new InstructionsUI();
                captureFrame(instr, new File(dir, "screen_04_instructions.png"), 1000, 700);
                instr.dispose();

                // 5. ExamModule
                ExamModule exam = new ExamModule();
                captureFrame(exam, new File(dir, "screen_05_exam_module.png"), 1200, 800);
                exam.dispose();

                // 6. ResultUI
                ResultUI result = new ResultUI();
                captureFrame(result, new File(dir, "screen_06_result_scorecard.png"), 1100, 750);
                result.dispose();

                // 7. ReviewUI
                ReviewUI review = new ReviewUI();
                captureFrame(review, new File(dir, "screen_07_review.png"), 1150, 750);
                review.dispose();

                // 8. StudentPerformanceUI
                StudentPerformanceUI perf = new StudentPerformanceUI();
                captureFrame(perf, new File(dir, "screen_08_performance_radar.png"), 1100, 750);
                perf.dispose();

                // 9. AdminDashboard
                AdminDashboard admin = new AdminDashboard();
                captureFrame(admin, new File(dir, "screen_09_admin_dashboard.png"), 1200, 800);
                admin.dispose();

                // 10. ExamManagementUI
                ExamManagementUI examMgmt = new ExamManagementUI();
                captureFrame(examMgmt, new File(dir, "screen_10_exam_management.png"), 1100, 750);
                examMgmt.dispose();

                // 11. ProctorLogUI
                ProctorLogUI proctor = new ProctorLogUI();
                captureFrame(proctor, new File(dir, "screen_11_proctor_log.png"), 1150, 750);
                proctor.dispose();

                // 12. QuestionAnalyticsUI
                QuestionAnalyticsUI analytics = new QuestionAnalyticsUI();
                captureFrame(analytics, new File(dir, "screen_12_question_analytics.png"), 1100, 750);
                analytics.dispose();

                // 13. VerifyCertificateUI
                VerifyCertificateUI cert = new VerifyCertificateUI();
                captureFrame(cert, new File(dir, "screen_13_verify_certificate.png"), 950, 650);
                cert.dispose();

                System.out.println("ALL_13_SCREENS_CAPTURED_SUCCESSFULLY");
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
