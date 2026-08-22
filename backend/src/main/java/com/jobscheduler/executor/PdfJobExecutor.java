package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import com.jobscheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PdfJobExecutor
 *
 * Downloads a PDF from the provided URL, extracts its full text using
 * Apache PDFBox, and stores a short summary (first 500 chars) as the result.
 *
 * Expected payload:
 * {
 *   "fileName" : "resume.pdf"
 *   "fileUrl"  : "https://example.com/resume.pdf"
 * }
 *
 * Stored result:
 * {
 *   "fileName"   : "resume.pdf",
 *   "sizeBytes"  : 45678,
 *   "pageCount"  : 3,
 *   "wordCount"  : 842,
 *   "summary"    : "First 500 characters of extracted text…"
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfJobExecutor implements JobExecutor {

    private final JobService jobService;

    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20 MB
    private static final int  SUMMARY_CHARS = 500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String queueName() {
        return "Pdf-Extract";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        String fileName = getString(payload, "fileName");
        String fileUrl  = getString(payload, "fileUrl");

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("PdfJobExecutor: 'fileName' is required");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("PdfJobExecutor: 'fileUrl' is required");
        }

        log.info("[PdfJobExecutor] Job {} -- Downloading PDF: {}", job.getId(), fileUrl);

        // ── Download ──────────────────────────────────────────────────────────
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("PdfJobExecutor: HTTP " + response.statusCode()
                    + " downloading " + fileUrl);
        }

        byte[] pdfBytes = response.body();

        if (pdfBytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("PdfJobExecutor: file too large ("
                    + pdfBytes.length + " bytes, max 20 MB)");
        }

        // ── Validate PDF magic bytes (%PDF) ───────────────────────────────────
        if (pdfBytes.length < 4
                || pdfBytes[0] != 0x25 || pdfBytes[1] != 0x50
                || pdfBytes[2] != 0x44 || pdfBytes[3] != 0x46) {
            throw new IllegalArgumentException(
                    "PdfJobExecutor: file is not a valid PDF");
        }

        log.info("[PdfJobExecutor] Job {} -- Downloaded {} bytes, extracting text…",
                job.getId(), pdfBytes.length);

        // ── Extract text with PDFBox ──────────────────────────────────────────
        int pageCount;
        int wordCount;
        String summary;

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            pageCount = doc.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(doc).trim();

            // Word count — split on whitespace
            wordCount = fullText.isBlank() ? 0
                    : fullText.split("\\s+").length;

            // Summary: first SUMMARY_CHARS characters, trimmed to a word boundary
            if (fullText.length() <= SUMMARY_CHARS) {
                summary = fullText;
            } else {
                summary = fullText.substring(0, SUMMARY_CHARS);
                // trim to last space so we don't cut a word mid-way
                int lastSpace = summary.lastIndexOf(' ');
                if (lastSpace > 0) summary = summary.substring(0, lastSpace);
                summary = summary + "…";
            }
        }

        log.info("[PdfJobExecutor] Job {} -- Extracted: pages={} words={} summary={}",
                job.getId(), pageCount, wordCount,
                summary.length() > 80 ? summary.substring(0, 80) + "…" : summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName",  fileName);
        result.put("sizeBytes", pdfBytes.length);
        result.put("pageCount", pageCount);
        result.put("wordCount", wordCount);
        result.put("summary",   summary);
        jobService.storeResult(job.getId(), result);
    }

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
