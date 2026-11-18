# Split Bill

Snap a photo of a restaurant receipt, tap who ate what, and get how much each friend owes the person who paid.

Built for the usual situation in Thailand: one friend pays for the whole table, then has to work out and chase everyone's share by hand.

**Live:** https://split-bill-pvv2.onrender.com (free instance, the first visit after a quiet spell takes about a minute to wake up)

## How it works

1. **Read the receipt on the phone.** The page shrinks the photo and reads it with Tesseract.js (Thai + English) on the phone's own CPU. A parser turns the raw text into items, quantities, prices, service charge, VAT, discount and the printed total.
2. **AI only when needed.** If the parsed items do not add up to the printed total, the photo goes to the server, which asks Gemini to read it. When OCR gets the prices right but garbles Thai names, a button lets the user ask AI to re-read.
3. **Check the list.** Every item and price can be edited, and the page shows whether the list matches the receipt total.
4. **Who ate what.** Add friends, then tap names under each item. Shared dishes are split evenly between the people tapped.
5. **Pay back.** Service charge, VAT and discounts are shared in proportion to what each person ate. Each person sees their total and what it is made of.

All money is handled in satang (integers) with the largest-remainder method, so the shares always add up to the bill exactly.

### Why OCR runs in the browser

The first version ran Tesseract on the server. On Render's free instance a single photo took 2 to 4 minutes, and the leftover OCR work starved the Gemini call. A phone reads the same photo in a few seconds, so OCR moved to the browser and the server only does AI, splitting and QR codes.

| Receipt | Server OCR (Render free) | Browser OCR |
|---|---|---|
| 7-Eleven app slip | 202 s | 4 s (no AI needed) |
| KFC paper receipt, hand-held photo | 252 s | 16 s (OCR fails, Gemini reads it) |

## Tech

- Java 21, Spring Boot 4
- Tesseract.js in the browser; Tesseract via Tess4J on the server when language data is present (local runs)
- Gemini API as fallback, trying several models when one is busy
- PromptPay QR payload (EMVCo + CRC-16/CCITT) generated in Java
- Plain HTML/CSS/JS front end, no framework
- Docker, deployed on Render

| File | What it does |
|---|---|
| `static/js/receipt-parser.js` | Raw OCR text to items and totals in the browser |
| `ReceiptParser.java` | Same parser on the server; both tolerate common Tesseract mistakes on Thai text |
| `SplitCalculator.java` | Splits items and extras between people, exact to the satang |
| `PromptPay.java` | Builds the PromptPay QR payload |
| `OcrReader.java` / `ReceiptReader.java` | Server-side Tesseract and Gemini readers |
| `ApiController.java` | `/api/scan`, `/api/split`, `/api/promptpay` |

## Run locally

Requires JDK 21+.

```sh
export GEMINI_API_KEY=your-key   # optional, only used when OCR is not enough
./mvnw spring-boot:run
```

Open http://localhost:8080.

Tests:

```sh
./mvnw test                  # Java
node --test src/test/js      # JavaScript parser
```

Optional: to let the server run OCR too (for API clients that are not the web page), put Tesseract language data in `tessdata/`:

```sh
mkdir tessdata
curl -L -o tessdata/tha.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/tha.traineddata
curl -L -o tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata
```

> Note: Maven fails to start if the project path contains non-ASCII characters (for example Thai folder names). Keep the project in an ASCII path.

## Deploy

The repo includes a `Dockerfile` and `render.yaml`. On Render, create a new Blueprint from this repo and set `GEMINI_API_KEY` in the dashboard.

## Limitations

- Tesseract reads prices reliably on clear receipts and screenshots, but Thai item names often come out with wrong vowels or tone marks. Names can be edited, or re-read with AI.
- Crumpled or angled photos usually need Gemini.
- The first scan on a phone downloads the Thai and English OCR data (a few MB), later scans use the browser cache.
