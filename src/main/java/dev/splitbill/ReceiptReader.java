package dev.splitbill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.List;

/** Reads a receipt photo with the Gemini API and returns its items as JSON. */
@Service
public class ReceiptReader {

    private static final String PROMPT = """
            This is a photo of a restaurant receipt (often Thai). Read it and return JSON only:
            {"items":[{"name":string,"qty":integer,"price":number}],
             "serviceCharge":number,"vat":number,"discount":number,"total":number}
            Rules:
            - price = the line total for that row (qty x unit price), in baht.
            - Keep item names as printed (Thai or English).
            - vat: only VAT added on top of item prices. If VAT is already included in prices, use 0.
            - discount is a positive number, 0 if none.
            - total = the final amount paid.
            - If it is not a receipt, return {"items":[],"serviceCharge":0,"vat":0,"discount":0,"total":0}.
            """;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper json = JsonMapper.builder().build();
    private final String apiKey;
    private final String model;

    private final String fallbackModels;

    public ReceiptReader(@Value("${gemini.api-key:}") String apiKey,
                         @Value("${gemini.model:gemini-4-hydrogen}") String model,
                         @Value("${gemini.fallback-models:gemini-3-flash-preview,gemini-3.1-flash-lite,gemini-flash-latest}") String fallbackModels) {
        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModels = fallbackModels;
    }

    public boolean hasKey() {
        return !apiKey.isBlank();
    }

    public Receipt read(byte[] image, String mimeType) throws IOException, InterruptedException {
        if (apiKey.isBlank()) throw new IllegalStateException("ยังไม่ได้ตั้งค่า GEMINI_API_KEY");

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", PROMPT),
                        Map.of("inline_data", Map.of(
                                "mime_type", mimeType,
                                "data", Base64.getEncoder().encodeToString(image)))))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0));

        String payload = json.writeValueAsString(body);
        int lastStatus = 0;
        // busy models answer 429/503 or hang; move straight on to the next model
        List<String> models = new java.util.ArrayList<>(List.of(model));
        for (String m : fallbackModels.split(",")) if (!m.isBlank() && !models.contains(m.strip())) models.add(m.strip());
        for (String m : models) {
            try {
                HttpResponse<String> res = http.send(request(m, payload), HttpResponse.BodyHandlers.ofString());
                lastStatus = res.statusCode();
                if (lastStatus == 200) return parse(res.body());
            } catch (java.net.http.HttpTimeoutException e) {
                lastStatus = 504;
            }
        }
        throw new IllegalStateException("AI ไม่ว่างตอนนี้ (" + lastStatus + ") ลองกดใหม่อีกครั้ง หรือแก้รายการเอง");
    }

    private HttpRequest request(String m, String payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
    }

    private Receipt parse(String responseBody) {
        // skip "thought" parts that thinking models may put before the answer
        for (JsonNode part : json.readTree(responseBody).path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.has("text")) {
                return toReceipt(json.readTree(part.path("text").asText()));
            }
        }
        throw new IllegalStateException("Gemini ไม่ได้ส่งข้อความกลับมา");
    }

    private static Receipt toReceipt(JsonNode r) {
        List<Receipt.Line> items = new java.util.ArrayList<>();
        for (JsonNode it : r.path("items")) {
            // free lines such as bags or "0.00" promos are not something anyone has to pay for
            if (it.path("price").asDouble(0) <= 0) continue;
            items.add(new Receipt.Line(it.path("name").asText(""), Math.max(it.path("qty").asInt(1), 1),
                    it.path("price").asDouble(0)));
        }
        double sc = r.path("serviceCharge").asDouble(0), vat = r.path("vat").asDouble(0);
        double disc = r.path("discount").asDouble(0), total = r.path("total").asDouble(0);
        return new Receipt(items, sc, vat, disc, total, "gemini", Receipt.isBalanced(items, sc, vat, disc, total));
    }
}
