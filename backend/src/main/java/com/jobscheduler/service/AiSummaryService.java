package com.jobscheduler.service;

import com.jobscheduler.entity.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryService.class);

    /**
     * Generates a structured AI diagnostic failure summary for a failed job.
     */
    public String generateSummary(Job job, String errorMessage, String errorStack) {
        String combinedErr = ((errorMessage != null ? errorMessage : "") + " " + (errorStack != null ? errorStack : ""))
                .toLowerCase();
        String queueName = job != null && job.getQueue() != null ? job.getQueue().getName() : "default";
        Map<String, Object> payload = job != null ? job.getPayload() : null;

        log.info("Generating AI failure summary for queue '{}', err length {}", queueName, combinedErr.length());

        // 1. SMTP / Email Connection Timeout or Mail Authentication
        if (combinedErr.contains("mailconnectexception") || combinedErr.contains("mailsendexception")
                || combinedErr.contains("sockettimeoutexception") || combinedErr.contains("smtp.gmail.com")
                || combinedErr.contains("587") || combinedErr.contains("authenticationfailedexception")) {
            return """
                    🤖 **AI Diagnostic Summary: Network & Mail Server Connection Timeout**

                    📌 **Category**: SMTP Network Delivery Failure
                    🔍 **Root Cause**: Failed to establish connection to `smtp.gmail.com`. Cloud deployment environments (e.g. Render/AWS) frequently block outbound STARTTLS Port 587, resulting in connection timeouts.
                    💡 **Recommended Action**:
                    1. Update `application.properties` JavaMail configuration to use SSL SMTPS Port 465 (`spring.mail.port=465` and `spring.mail.properties.mail.smtp.ssl.enable=true`).
                    2. Verify the 16-character Google App Password environment variable (`SPRING_MAIL_PASSWORD`).
                    3. Click **Re-queue Job** once configuration is updated.
                    """;
        }

        // 2. NullPointerException / Missing Required Payload Field
        if (combinedErr.contains("nullpointerexception") || combinedErr.contains("cannot read field")
                || combinedErr.contains("is null") || combinedErr.contains("missing required")) {
            return String.format(
                    """
                            🤖 **AI Diagnostic Summary: Missing Required Payload Parameter**

                            📌 **Category**: Payload Null Pointer Exception
                            🔍 **Root Cause**: The job worker expected required parameters in the job payload, but received empty or null values. (Current Payload: `%s`).
                            💡 **Recommended Action**:
                            1. Inspect the job payload structure in the details tab.
                            2. Ensure all mandatory keys (e.g. `to`, `subject`, `message`, `values`) are provided.
                            3. Edit job payload and re-enqueue.
                            """,
                    payload != null ? payload.toString() : "{}");
        }

        // 3. PDF Extractor / Document Processing Error
        if (combinedErr.contains("pdf") || combinedErr.contains("invalidpdfexception")
                || combinedErr.contains("filenotfoundexception") || queueName.toLowerCase().contains("pdf")) {
            return """
                    🤖 **AI Diagnostic Summary: PDF Document Extraction Failure**

                    📌 **Category**: File Processing Error
                    🔍 **Root Cause**: The worker could not fetch or parse the specified PDF document. The target `fileUrl` may be invalid, inaccessible, or corrupted.
                    💡 **Recommended Action**:
                    1. Verify the `fileUrl` parameter in the job payload is publicly accessible.
                    2. Ensure the uploaded file size is under 20 MB and is a valid PDF.
                    """;
        }

        // 4. Arithmetic / Numeric Math Operation Error
        if (combinedErr.contains("arithmeticexception") || combinedErr.contains("divide by zero")
                || combinedErr.contains("numberformatexception") || queueName.toLowerCase().contains("calculate")) {
            return """
                    🤖 **AI Diagnostic Summary: Mathematical Operation Error**

                    📌 **Category**: Math Execution Exception
                    🔍 **Root Cause**: An arithmetic error occurred while processing values (e.g., division by zero or non-numeric elements passed to calculator).
                    💡 **Recommended Action**:
                    1. Check the `values` array in the job payload.
                    2. Ensure elements are non-zero valid numbers.
                    """;
        }

        // 5. Connection Timeout / External Webhook Failure
        if (combinedErr.contains("connectexception") || combinedErr.contains("timeoutexception")
                || combinedErr.contains("unknownhostexception") || combinedErr.contains("http 500")
                || combinedErr.contains("http 404")) {
            return """
                    🤖 **AI Diagnostic Summary: Remote Endpoint Unreachable**

                    📌 **Category**: HTTP / Webhook Connection Timeout
                    🔍 **Root Cause**: The external HTTP endpoint or service host returned an error code or did not respond within the timeout window.
                    💡 **Recommended Action**:
                    1. Verify target server availability and endpoint URL.
                    2. Re-queue the job when target server recovers.
                    """;
        }

        // 6. Generic Failure Fallback
        String cleanMsg = errorMessage != null ? errorMessage.trim() : "Unknown execution error";
        return String.format("""
                🤖 **AI Diagnostic Summary: Job Execution Failure**

                📌 **Category**: Execution Exception
                🔍 **Root Cause**: %s
                💡 **Recommended Action**:
                1. Inspect the stack trace under Job Attempts.
                2. Verify payload inputs and retry by clicking **Re-queue Job**.
                """, cleanMsg);
    }
}
