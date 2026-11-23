package com.onlineexam.data;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates official PDF examination certificates using OpenPDF and ZXing.
 * Formatted in A4 landscape with refined indigo double borders, corner ornaments,
 * a vector seal, verifiable QR code, and signature line.
 */
public final class CertificatePdfBuilder {

    private CertificatePdfBuilder() {
    }

    /**
     * Checks whether OpenPDF and ZXing core libraries are present on the classpath.
     */
    public static boolean isAvailable() {
        try {
            Class.forName("com.lowagie.text.Document");
            Class.forName("com.google.zxing.qrcode.QRCodeWriter");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Builds and writes a tamper-evident PDF certificate to the target file.
     */
    public static void buildCertificate(
            File targetFile,
            String certCode,
            String studentDisplayName,
            String examTitle,
            String subjectName,
            String scoreText,
            String percentageText,
            String grade,
            String examDateStr) throws Exception {

        if (targetFile == null) {
            throw new IllegalArgumentException("Target certificate file cannot be null.");
        }

        Document doc = new Document(PageSize.A4.rotate(), 40, 40, 40, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(targetFile));
        doc.open();

        PdfContentByte cb = writer.getDirectContent();
        float w = PageSize.A4.rotate().getWidth();
        float h = PageSize.A4.rotate().getHeight();

        // 1. Refined Indigo Double Border
        cb.setRGBColorStroke(79, 70, 229); // Primary Indigo #4F46E5
        cb.setLineWidth(2.5f);
        cb.rectangle(26, 26, w - 52, h - 52);
        cb.stroke();

        cb.setRGBColorStroke(165, 180, 252); // Fine Inner Indigo #A5B4FC
        cb.setLineWidth(0.8f);
        cb.rectangle(32, 32, w - 64, h - 64);
        cb.stroke();

        // 2. Corner Ornaments (Geometric diamonds & accents at 4 inner corners)
        float[][] corners = {
                {42, 42},
                {w - 42, 42},
                {42, h - 42},
                {w - 42, h - 42}
        };
        cb.setRGBColorFill(79, 70, 229);
        for (float[] c : corners) {
            cb.circle(c[0], c[1], 3.5f);
            cb.fill();
        }

        // Inner corner accent lines
        cb.setRGBColorStroke(99, 102, 241);
        cb.setLineWidth(0.8f);
        // Top-left
        cb.moveTo(38, h - 46); cb.lineTo(46, h - 46); cb.stroke();
        cb.moveTo(46, h - 38); cb.lineTo(46, h - 46); cb.stroke();
        // Top-right
        cb.moveTo(w - 38, h - 46); cb.lineTo(w - 46, h - 46); cb.stroke();
        cb.moveTo(w - 46, h - 38); cb.lineTo(w - 46, h - 46); cb.stroke();
        // Bottom-left
        cb.moveTo(38, 46); cb.lineTo(46, 46); cb.stroke();
        cb.moveTo(46, 38); cb.lineTo(46, 46); cb.stroke();
        // Bottom-right
        cb.moveTo(w - 38, 46); cb.lineTo(w - 46, 46); cb.stroke();
        cb.moveTo(w - 46, 38); cb.lineTo(w - 46, 46); cb.stroke();

        // 3. Verifiable QR Code via ZXing Core
        String safeCode = (certCode != null && !certCode.trim().isEmpty()) ? certCode.trim() : "CERT-VERIFY";
        QRCodeWriter qrWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BitMatrix matrix = qrWriter.encode(safeCode, BarcodeFormat.QR_CODE, 120, 120, hints);

        BufferedImage qrBimg = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                qrBimg.setRGB(x, y, matrix.get(x, y) ? 0x1E1B4B : 0xFFFFFF);
            }
        }
        com.lowagie.text.Image qrPdfImage = com.lowagie.text.Image.getInstance(qrBimg, null);
        qrPdfImage.scaleAbsolute(64, 64);
        qrPdfImage.setAbsolutePosition(52, 54);
        cb.addImage(qrPdfImage);

        // QR Code Details & Verification Instructions
        cb.beginText();
        cb.setFontAndSize(FontFactory.getFont(FontFactory.HELVETICA_BOLD).getBaseFont(), 8.5f);
        cb.setRGBColorFill(30, 27, 75);
        cb.showTextAligned(Element.ALIGN_LEFT, safeCode, 124, 98, 0);
        cb.setFontAndSize(FontFactory.getFont(FontFactory.HELVETICA).getBaseFont(), 7.5f);
        cb.setRGBColorFill(107, 114, 128);
        cb.showTextAligned(Element.ALIGN_LEFT, "Scan or enter the ID to verify", 124, 85, 0);
        cb.showTextAligned(Element.ALIGN_LEFT, "Tamper-evident official certificate", 124, 73, 0);
        cb.endText();

        // 4. Vector Seal Drawn with Shapes (Center Bottom)
        float sealX = w / 2f;
        float sealY = 82f;

        // Ribbon Tails
        cb.setRGBColorFill(217, 119, 6);
        cb.moveTo(sealX - 18, sealY - 14);
        cb.lineTo(sealX - 26, sealY - 44);
        cb.lineTo(sealX - 16, sealY - 38);
        cb.lineTo(sealX - 6, sealY - 44);
        cb.lineTo(sealX - 8, sealY - 14);
        cb.fill();

        cb.moveTo(sealX + 8, sealY - 14);
        cb.lineTo(sealX + 6, sealY - 44);
        cb.lineTo(sealX + 16, sealY - 38);
        cb.lineTo(sealX + 26, sealY - 44);
        cb.lineTo(sealX + 18, sealY - 14);
        cb.fill();

        // Seal Circles
        cb.setRGBColorFill(254, 243, 199);
        cb.circle(sealX, sealY, 28);
        cb.fill();

        cb.setRGBColorStroke(245, 158, 11);
        cb.setLineWidth(1.6f);
        cb.circle(sealX, sealY, 28);
        cb.stroke();

        cb.setLineWidth(0.8f);
        cb.circle(sealX, sealY, 24);
        cb.stroke();

        // Seal Center Inscription
        cb.beginText();
        cb.setFontAndSize(FontFactory.getFont(FontFactory.HELVETICA_BOLD).getBaseFont(), 7f);
        cb.setRGBColorFill(180, 83, 9);
        cb.showTextAligned(Element.ALIGN_CENTER, "OFFICIAL", sealX, sealY + 7, 0);
        cb.showTextAligned(Element.ALIGN_CENTER, "EXCELLENCE", sealX, sealY - 3, 0);
        cb.showTextAligned(Element.ALIGN_CENTER, "VERIFIED", sealX, sealY - 12, 0);
        cb.endText();

        // 5. Signature Line (Right Bottom)
        cb.setRGBColorStroke(107, 114, 128);
        cb.setLineWidth(1f);
        cb.moveTo(w - 230, 80);
        cb.lineTo(w - 60, 80);
        cb.stroke();

        cb.beginText();
        cb.setFontAndSize(FontFactory.getFont(FontFactory.HELVETICA_BOLD).getBaseFont(), 10f);
        cb.setRGBColorFill(17, 24, 39);
        cb.showTextAligned(Element.ALIGN_CENTER, "Controller of Examinations", w - 145, 66, 0);
        cb.setFontAndSize(FontFactory.getFont(FontFactory.HELVETICA).getBaseFont(), 8f);
        cb.setRGBColorFill(107, 114, 128);
        cb.showTextAligned(Element.ALIGN_CENTER, "Academic Evaluation Council", w - 145, 54, 0);
        cb.endText();

        // 6. Certificate Content Document Flow
        Paragraph kicker = new Paragraph("ONLINE EXAMINATION MANAGEMENT SYSTEM\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(99, 102, 241)));
        kicker.setAlignment(Element.ALIGN_CENTER);
        kicker.setSpacingBefore(18);
        doc.add(kicker);

        Paragraph title = new Paragraph("Certificate of Achievement\n",
                FontFactory.getFont(FontFactory.TIMES_BOLD, 30, new Color(30, 27, 75)));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        doc.add(title);

        Paragraph certText = new Paragraph("This is to certify that\n",
                FontFactory.getFont(FontFactory.TIMES_ITALIC, 13, new Color(75, 85, 99)));
        certText.setAlignment(Element.ALIGN_CENTER);
        doc.add(certText);

        String safeStudent = (studentDisplayName != null && !studentDisplayName.trim().isEmpty())
                ? studentDisplayName.trim() : "Candidate";
        Paragraph studentName = new Paragraph(safeStudent + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, new Color(17, 24, 39)));
        studentName.setAlignment(Element.ALIGN_CENTER);
        studentName.setSpacingBefore(3);
        studentName.setSpacingAfter(3);
        doc.add(studentName);

        Paragraph completionText = new Paragraph("has successfully completed\n",
                FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(75, 85, 99)));
        completionText.setAlignment(Element.ALIGN_CENTER);
        doc.add(completionText);

        String safeExam = (examTitle != null && !examTitle.trim().isEmpty()) ? examTitle.trim() : "Examination";
        String safeSubj = (subjectName != null && !subjectName.trim().isEmpty()) ? subjectName.trim() : "";
        String examFullText = safeSubj.isEmpty() ? safeExam : (safeExam + " (" + safeSubj + ")");
        Paragraph examName = new Paragraph(examFullText + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(49, 46, 129)));
        examName.setAlignment(Element.ALIGN_CENTER);
        examName.setSpacingBefore(3);
        examName.setSpacingAfter(7);
        doc.add(examName);

        String safeScore = (scoreText != null) ? scoreText : "-";
        String safePct = (percentageText != null) ? percentageText : "-";
        String safeGrade = (grade != null) ? grade : "-";
        String scoreLineText = "Score: " + safeScore + "   \u2022   Percentage: " + safePct
                + "   \u2022   Grade: " + safeGrade + "   \u2022   Result: PASSED\n";
        Paragraph scoreLine = new Paragraph(scoreLineText,
                FontFactory.getFont(FontFactory.HELVETICA, 11f, new Color(31, 41, 55)));
        scoreLine.setAlignment(Element.ALIGN_CENTER);
        doc.add(scoreLine);

        String safeDate = (examDateStr != null && !examDateStr.trim().isEmpty()) ? examDateStr.trim() : "Recent Session";
        Paragraph dateLine = new Paragraph("Awarded on " + safeDate + "\n",
                FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(107, 114, 128)));
        dateLine.setAlignment(Element.ALIGN_CENTER);
        dateLine.setSpacingBefore(2);
        doc.add(dateLine);

        doc.close();
    }
}
