package com.jobscheduler.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Handles temporary file uploads used by the frontend before enqueuing a job.
 *
 * Flow:
 *  1. User selects a PDF in the EnqueueJobModal.
 *  2. Frontend POSTs it here → gets back a temporary URL.
 *  3. Frontend fills the "fileUrl" field with that URL.
 *  4. When the job executes, PdfJobExecutor downloads from that URL.
 *
 * Files are stored in a configurable temp directory (default: system temp).
 * They are NOT cleaned up automatically — for production use S3/GCS instead.
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Value("${upload.dir:#{systemProperties['java.io.tmpdir']}}")
    private String uploadDir;

    @Value("${server.port:8081}")
    private String serverPort;

    /**
     * POST /api/upload/pdf
     * Accepts a multipart PDF file, saves it to the upload directory,
     * and returns the URL the job executor can download from.
     */
    @PostMapping("/pdf")
    public ResponseEntity<Map<String, String>> uploadPdf(
            @RequestParam("file") MultipartFile file) throws IOException {

        // Basic validation
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF files are accepted."));
        }

        if (file.getSize() > 20L * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File exceeds 20 MB limit."));
        }

        // Save file with unique name to avoid collisions
        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);

        String storedName = UUID.randomUUID() + "_" + originalName;
        Path dest = dir.resolve(storedName);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        log.info("[UploadController] Saved PDF: {} ({} bytes)", dest, file.getSize());

        // Return download URL pointing back to our own /api/upload/files/{name} endpoint
        String fileUrl = "http://localhost:" + serverPort + "/api/upload/files/" + storedName;

        return ResponseEntity.ok(Map.of(
                "fileName", originalName,
                "fileUrl",  fileUrl,
                "sizeBytes", String.valueOf(file.getSize())
        ));
    }

    /**
     * GET /api/upload/files/{fileName}
     * Serves the uploaded file so PdfJobExecutor can download it.
     */
    @org.springframework.web.bind.annotation.GetMapping("/files/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(
            @org.springframework.web.bind.annotation.PathVariable String fileName)
            throws IOException {

        Path file = Path.of(uploadDir).resolve(fileName).normalize();
        // Prevent path traversal
        if (!file.startsWith(Path.of(uploadDir).toAbsolutePath())) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        org.springframework.core.io.Resource resource =
                new org.springframework.core.io.FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
