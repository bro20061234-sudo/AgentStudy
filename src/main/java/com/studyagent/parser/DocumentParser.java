package com.studyagent.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class DocumentParser {

    public static String extractText(File file) {
        if (file == null || !file.exists()) return "";
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".pdf")) {
                return extractTextFromPdf(Files.readAllBytes(file.toPath()));
            } else if (name.endsWith(".docx")) {
                return extractTextFromDocx(Files.readAllBytes(file.toPath()));
            } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                return Files.readString(file.toPath());
            }
        } catch (Exception e) {
            System.err.println("⚠️ [DocumentParser] Lỗi đọc file " + file.getName() + ": " + e.getMessage());
        }
        return "";
    }

    public static String extractTextFromPdf(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), 60));
            return stripper.getText(document);
        } catch (Exception e) {
            System.err.println("⚠️ [DocumentParser] Lỗi giải mã PDF: " + e.getMessage());
            return "";
        }
    }

    public static List<byte[]> renderPdfToImages(byte[] pdfBytes) {
        List<byte[]> images = new java.util.ArrayList<>();
        if (pdfBytes == null || pdfBytes.length == 0) return images;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), 10);
            for (int i = 0; i < pageCount; i++) {
                java.awt.image.BufferedImage bim = renderer.renderImageWithDPI(i, 150);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bim, "jpg", baos);
                images.add(baos.toByteArray());
                if (bim.getHeight() > bim.getWidth()) {
                    java.awt.image.BufferedImage rotated = new java.awt.image.BufferedImage(
                            bim.getHeight(), bim.getWidth(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D graphics = rotated.createGraphics();
                    java.awt.geom.AffineTransform transform = new java.awt.geom.AffineTransform();
                    transform.translate(bim.getHeight(), 0);
                    transform.rotate(Math.PI / 2);
                    graphics.drawImage(bim, transform, null);
                    graphics.dispose();
                    baos.reset();
                    javax.imageio.ImageIO.write(rotated, "jpg", baos);
                    images.add(baos.toByteArray());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [DocumentParser] Lỗi render PDF thành ảnh: " + e.getMessage());
        }
        return images;
    }

    public static String extractTextFromDocx(byte[] docxBytes) {
        try (InputStream is = new ByteArrayInputStream(docxBytes);
             XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        if (paragraph.getNumID() != null) sb.append("- ");
                        sb.append(text).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                        List<String> cells = new java.util.ArrayList<>();
                        for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            cells.add(cellText == null ? "" : cellText.replace('\n', ' ').trim());
                        }
                        sb.append("- ").append(String.join(" | ", cells)).append('\n');
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            System.err.println("⚠️ [DocumentParser] Lỗi giải mã Word .docx: " + e.getMessage());
            return "";
        }
    }
}
