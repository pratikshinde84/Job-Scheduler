package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * PdfJobExecutor
 *
 * Processes a PDF file — downloads it from the provided URL, reads
 * its byte size, and logs metadata.  In production, swap in a PDF
 * library (Apache PDFBox, iText, etc.) to extract text, generate
 * thumbnails, or perform any other processing.
 *
 * Expected payload:
 * {
 *   "fileName" : "resume.pdf"                         — display name (required)
 *   "fileUrl"  : "https://example.com/resume.pdf"     — publicly accessible URL (required)
 * }
 */
@Slf4j
@Component
public class PdfJobExecutor implements JobExecutor {

    /** Maximum file size accepted: 20 MB */
    private static final long MAX_BYTES = 20 * 1024 * 1024;

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
            throw new IllegalArgumentException("PdfJobExecutor: 'fileName' field is required");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("PdfJobExecutor: 'fileUrl' field is required");
        }

        log.info("[PdfJobExecutor] Job {} — Processing PDF: fileName={} url={}",
                job.getId(), fileName, fileUrl);

        // ── Download PDF ─────────────────────────────────────────────────────
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "PdfJobExecutor: failed to download PDF, HTTP status=" + response.statusCode());
        }

        byte[] pdfBytes = response.body();

        if (pdfBytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "PdfJobExecutor: file too large (" + pdfBytes.length + " bytes, max " + MAX_BYTES + ")");
        }

        // ── Validate PDF magic bytes (%PDF) ──────────────────────────────────
        if (pdfBytes.length < 4 ||
                pdfBytes[0] != 0x25 || pdfBytes[1] != 0x50 ||
                pdfBytes[2] != 0x44 || pdfBytes[3] != 0x46) {
            throw new IllegalArgumentException(
                    "PdfJobExecutor: downloaded file does not appear to be a valid PDF");
        }

        // ── Simulate processing (replace with real PDFBox / iText logic) ─────
        log.info("[PdfJobExecutor] Job {} — PDF downloaded: {} bytes, simulating extraction…",
                job.getId(), pdfBytes.length);
        Thread.sleep(500);

        log.info("[PdfJobExecutor] Job {} — PDF processed successfully: fileName={} size={} bytes",
                job.getId(), fileName, pdfBytes.length);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
