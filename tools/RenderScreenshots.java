import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class RenderScreenshots {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        } catch (Exception e) {}

        File dir = new File("report_images");
        if (!dir.exists()) dir.mkdirs();

        // 1. Render Radar Chart
        try {
            RadarChartPanel radar = new RadarChartPanel();
            java.util.Map<String, Double> scores = new java.util.LinkedHashMap<>();
            scores.put("Core Java & OOP", 88.0);
            scores.put("Collections & Streams", 76.0);
            scores.put("Multithreading", 92.0);
            scores.put("JDBC & SQL", 84.0);
            scores.put("Swing GUI", 95.0);
            scores.put("Security & Crypto", 80.0);
            radar.setScores(scores);
            radar.setSize(500, 400);
            radar.doLayout();

            BufferedImage img = new BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 500, 400);
            radar.paint(g);
            g.dispose();
            ImageIO.write(img, "PNG", new File(dir, "screen_radar_chart.png"));
            System.out.println("Rendered screen_radar_chart.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
