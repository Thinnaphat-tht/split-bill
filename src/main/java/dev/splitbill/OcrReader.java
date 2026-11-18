package dev.splitbill;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads text from a receipt photo with Tesseract, offline and free. */
@Service
public class OcrReader {

    private final String dataPath;

    public OcrReader(@Value("${ocr.tessdata:tessdata}") String dataPath) {
        this.dataPath = dataPath;
    }

    public boolean available() {
        return Files.exists(Path.of(dataPath, "tha.traineddata"));
    }

    public String read(byte[] image) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(image));
        if (img == null) throw new IOException("อ่านไฟล์รูปไม่ได้");

        // Tesseract is not thread-safe, so each request gets its own instance
        Tesseract t = new Tesseract();
        t.setDatapath(dataPath);
        t.setLanguage("tha+eng");
        t.setPageSegMode(6); // a single uniform block of text, fits receipts
        t.setVariable("preserve_interword_spaces", "1");
        t.setVariable("user_defined_dpi", "300");
        return t.doOCR(prepare(img));
    }

    /** Grayscale and upscale small photos: Tesseract reads Thai much better at ~300 dpi. */
    private static BufferedImage prepare(BufferedImage src) {
        double scale = src.getWidth() < 1400 ? 1400.0 / src.getWidth() : 1.0;
        int w = (int) (src.getWidth() * scale), h = (int) (src.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
