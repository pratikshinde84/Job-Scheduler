package com.jobscheduler.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jobscheduler.security.JwtService;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Sliding-window rate limit filter.
 *
 * Runs once per request BEFORE the JWT authentication filter so that
 * unauthenticated (and malformed-token) requests are also counted.
 *
 * Rate limit key strategy:
 *  - Authenticated requests  → keyed by userId (from JWT sub claim)
 *  - Unauthenticated requests → keyed by client IP address
 *
 * Buckets:
 *  - "enqueue"  → POST /api/queues/*/jobs  (tighter limit)
 *  - "default"  → all other /api/** paths
 *
 * Response when limited (HTTP 429):
 *  Headers:
 *    X-RateLimit-Limit     : max requests in window
 *    X-RateLimit-Remaining : requests left in window
 *    X-RateLimit-Reset     : Unix epoch seconds when window resets
 *    Retry-After           : seconds until retry is safe
 *  Body (JSON):
 *    { "status": 429, "error": "Too Many Requests", "message": "...", "retryAfterSeconds": N }
 *
 * Non-API paths (actuator, OPTIONS) are always exempt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;
    private final JwtService         jwtService;
    private final ObjectMapper       objectMapper;

    @Value("${ratelimit.enabled:true}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path   = request.getRequestURI();
        String method = request.getMethod();

        // Skip OPTIONS (CORS preflight), actuator, and non-API paths
        return "OPTIONS".equalsIgnoreCase(method)
                || path.startsWith("/actuator")
                || !path.startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String key    = resolveKey(request);
        String bucket = resolveBucket(request);

        boolean allowed = rateLimiter.isAllowed(key, bucket);

        // Always set informational headers
        int  limit          = rateLimiter.getLimit(bucket);
        long remaining      = rateLimiter.remaining(key, bucket);
        int  windowSeconds  = rateLimiter.getWindowSeconds(bucket);
        long resetEpoch     = Instant.now().getEpochSecond() + windowSeconds;

        response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(resetEpoch));

        if (!allowed) {
            log.warn("RateLimit: key={} bucket={} BLOCKED", key, bucket);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));

            Map<String, Object> body = Map.of(
                    "status",            429,
                    "error",             "Too Many Requests",
                    "message",           "Rate limit exceeded. Try again in " + windowSeconds + " seconds.",
                    "retryAfterSeconds", windowSeconds
            );
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        chain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extract the rate-limit key:
     *  - JWT present and valid → userId (UUID string)
     *  - No/invalid JWT        → client IP address
     */
    private String resolveKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Optional<Claims> claims = jwtService.validateAndExtract(token);
                if (claims.isPresent()) {
                    return jwtService.extractUserId(claims.get()).toString();
                }
            } catch (Exception ignored) {
                // fall through to IP-based key
            }
        }
        return getClientIp(request);
    }

    /**
     * Determine the rate-limit bucket:
     *  - POST /api/queues/*/jobs → "enqueue"  (tighter)
     *  - everything else         → "default"
     */
    private String resolveBucket(HttpServletRequest request) {
        String path   = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) && path.matches("/api/queues/[^/]+/jobs")) {
            return "enqueue";
        }
        return "default";
    }

    /** Respects X-Forwarded-For (Upstash / proxy deployments). */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
