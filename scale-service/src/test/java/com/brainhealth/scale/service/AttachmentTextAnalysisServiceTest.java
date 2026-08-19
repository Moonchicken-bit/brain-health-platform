package com.brainhealth.scale.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentTextAnalysisServiceTest {
    @TempDir Path temp;

    @Test
    void extractsColonSeparatedDraftFields() {
        var result = AttachmentTextAnalysisService.extractKeyValues(
            "Patient ID: P001\nGlucose: 7.2 mmol/L\n备注：需要复核");
        assertEquals("P001", result.get("Patient ID"));
        assertEquals("7.2 mmol/L", result.get("Glucose"));
        assertEquals("需要复核", result.get("备注"));
    }

    @Test
    void recognizesTextFromImageUsingBundledLocalOcr() throws Exception {
        Path image = temp.resolve("report.png");
        BufferedImage canvas = new BufferedImage(900, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 900, 220);
        graphics.setColor(Color.BLACK); graphics.setFont(new Font("Arial", Font.PLAIN, 64));
        graphics.drawString("Patient ID: P001", 30, 100);
        graphics.drawString("Glucose: 7.2", 30, 185);
        graphics.dispose();
        ImageIO.write(canvas, "png", image.toFile());

        var service = new AttachmentTextAnalysisService(null, "eng", "", "missing-tesseract-for-test");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.analyzeFile("a1", image.toFile(), "image/png", "report.png"));
        assertTrue(error.getMessage().contains("OCR 引擎未安装"));
    }
}
