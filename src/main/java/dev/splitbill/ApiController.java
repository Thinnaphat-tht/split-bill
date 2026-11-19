package dev.splitbill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ReceiptReader gemini;
    private final OcrReader ocr;
    private final long ocrTimeoutSeconds;

    public ApiController(ReceiptReader gemini, OcrReader ocr,
                         @Value("${ocr.timeout-seconds:20}") long ocrTimeoutSeconds) {
        this.gemini = gemini;
        this.ocr = ocr;
        this.ocrTimeoutSeconds = ocrTimeoutSeconds;
    }

    /**
     * Hybrid scan: Tesseract first (free, offline). Only when its items do not add up
     * to the printed total do we ask Gemini. If Gemini fails, the OCR result is still returned.
     */
    @PostMapping("/scan")
    public Receipt scan(@RequestParam("image") MultipartFile image,
                        // the web page already ran OCR on the phone and only needs Gemini
                        @RequestParam(defaultValue = "false") boolean aiOnly) throws Exception {
        String type = image.getContentType();
        if (type == null || !type.startsWith("image/")) {
            throw new IllegalArgumentException("ไฟล์ต้องเป็นรูปภาพ");
        }
        byte[] bytes = image.getBytes();

        Receipt fromOcr = null;
        if (!aiOnly && ocr.available()) {
            // on a slow server Tesseract can take minutes; give up and let Gemini read it instead
            CompletableFuture<String> job = CompletableFuture.supplyAsync(() -> {
                try {
                    return ocr.read(bytes);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
            try {
                String text = job.get(ocrTimeoutSeconds, TimeUnit.SECONDS);
                fromOcr = ReceiptParser.parse(text);
                if (fromOcr.balanced()) return fromOcr;
                log.info("OCR result does not add up, asking Gemini. OCR text:\n{}", text);
            } catch (TimeoutException e) {
                log.warn("OCR took longer than {}s, using Gemini", ocrTimeoutSeconds);
            } catch (Exception | UnsatisfiedLinkError e) {
                log.warn("OCR failed: {}", e.toString());
            }
        }
        String reason = "อ่านใบเสร็จไม่สำเร็จ ลองถ่ายใหม่ให้ชัดขึ้น หรือเพิ่มรายการเอง";
        if (gemini.hasKey()) {
            try {
                Receipt r = gemini.read(bytes, type);
                if (fromOcr != null && !fromOcr.items().isEmpty() && r.items().isEmpty()) return fromOcr;
                return fromOcr == null ? r : r.withSource("ocr+gemini");
            } catch (IllegalStateException e) {
                log.warn("Gemini failed: {}", e.getMessage());
                reason = e.getMessage();   // e.g. "AI is busy", worth showing to the user
            } catch (Exception e) {
                log.warn("Gemini failed: {}", e.toString());
            }
        }
        if (fromOcr != null) return fromOcr;
        throw new IllegalStateException(reason);
    }

    @PostMapping("/split")
    public List<SplitCalculator.PersonTotal> split(@RequestBody SplitCalculator.Request req) {
        return SplitCalculator.split(req);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> failed(Exception e) {
        return ResponseEntity.status(502).body(Map.of("error", "อ่านใบเสร็จไม่สำเร็จ ลองถ่ายใหม่ให้ชัดขึ้น"));
    }
}
