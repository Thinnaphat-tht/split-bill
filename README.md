# Split Bill

Snap a photo of a restaurant receipt, tap who ate what, and get how much each friend owes, with a PromptPay QR code for each person.

Built for the usual situation in Thailand: one friend pays for the whole table, then has to work out and chase everyone's share by hand.

## How it works

1. **Read the receipt (hybrid OCR).** The photo is first read offline with Tesseract (Thai + English). A Java parser turns the raw text into items, quantities, prices, service charge, VAT, discount and the printed total. Only when the parsed items do not add up to the printed total does the server ask Gemini to read the photo instead.
2. **Check the list.** Every item and price can be edited, and the page shows whether the list matches the receipt total.
3. **Who ate what.** Add friends, then tap names under each item. Shared dishes are split evenly between the people tapped.
4. **Pay back.** Service charge, VAT and discounts are shared in proportion to what each person ate. Each person gets their total and a PromptPay QR code for that exact amount.

All money is handled in satang (integers) with the largest-remainder method, so the shares always add up to the bill exactly.

## Tech

- Java 21, Spring Boot 4
- Tesseract via Tess4J for offline OCR, Gemini API as fallback
- PromptPay QR payload (EMVCo + CRC-16/CCITT) generated in Java
- Plain HTML/CSS/JS front end, no framework
- Docker, deployed on Render

| File | What it does |
|---|---|
| `ReceiptParser.java` | Raw OCR text to items and totals; tolerates common Tesseract mistakes on Thai text |
| `SplitCalculator.java` | Splits items and extras between people, exact to the satang |
| `PromptPay.java` | Builds the PromptPay QR payload |
| `OcrReader.java` / `ReceiptReader.java` | Tesseract and Gemini readers |
| `ApiController.java` | `/api/scan`, `/api/split`, `/api/promptpay` |

## Run locally

Requires JDK 21+. Download the Tesseract language data first:

```sh
mkdir tessdata
curl -L -o tessdata/tha.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/tha.traineddata
curl -L -o tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata
```

Then:

```sh
export GEMINI_API_KEY=your-key   # optional, only used as fallback
./mvnw spring-boot:run
```

Open http://localhost:8080. Run the tests with `./mvnw test`.

> Note: Maven fails to start if the project path contains non-ASCII characters (for example Thai folder names). Keep the project in an ASCII path.

## Deploy

The repo includes a `Dockerfile` and `render.yaml`. On Render, create a new Blueprint from this repo and set `GEMINI_API_KEY` in the dashboard.

## Limitations

- Tesseract reads prices reliably on clear receipts and screenshots, but Thai item names often come out with wrong vowels or tone marks. Names can be edited before splitting.
- Crumpled or angled photos usually fall back to Gemini.
