package com.jobscheduler.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.ECParameterSpec;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.AlgorithmParameters;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates Supabase-issued JWTs signed with ES256 (ECDSA P-256).
 *
 * Supabase exposes its public key(s) via JWKS at:
 *   https://<project>.supabase.co/auth/v1/.well-known/jwks.json
 *
 * We fetch the JWKS once at startup and cache the PublicKey objects.
 * No private key or JWT secret is needed — we only verify signatures.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${supabase.jwks.url}")
    private String jwksUrl;

    /** kid → PublicKey cache */
    private final ConcurrentHashMap<String, PublicKey> keyCache = new ConcurrentHashMap<>();

    /** Fallback: single key when JWKS has only one entry (no kid match needed) */
    private PublicKey singleKey;

    @PostConstruct
    public void loadKeys() {
        try {
            log.info("Loading Supabase JWKS from {}", jwksUrl);

            @SuppressWarnings("unchecked")
            Map<String, Object> jwks = RestClient.create()
                    .get()
                    .uri(jwksUrl)
                    .retrieve()
                    .body(Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

            for (Map<String, Object> jwk : keys) {
                String kty = (String) jwk.get("kty");
                if (!"EC".equals(kty)) {
                    log.warn("Skipping non-EC JWK with kty={}", kty);
                    continue;
                }
                PublicKey pk = buildEcPublicKey(jwk);
                String kid = (String) jwk.get("kid");
                if (kid != null) {
                    keyCache.put(kid, pk);
                }
                singleKey = pk; // keeps the last one as fallback
            }

            log.info("Loaded {} EC public key(s) from JWKS", keyCache.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Supabase JWKS from " + jwksUrl, e);
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public Optional<Claims> validateAndExtract(String token) {
        // Peek at the header to get kid, then pick the right key
        PublicKey key = resolveKey(token);
        if (key == null) {
            log.debug("No matching public key found for token");
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(Claims claims) {
        return (String) claims.get("email");
    }

    @SuppressWarnings("unchecked")
    public String extractName(Claims claims) {
        Object meta = claims.get("user_metadata");
        if (meta instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null) return name.toString();
            Object fullName = map.get("full_name");
            if (fullName != null) return fullName.toString();
        }
        String email = extractEmail(claims);
        return email != null ? email.split("@")[0] : "Unknown";
    }

    @SuppressWarnings("unchecked")
    public String extractAvatarUrl(Claims claims) {
        Object meta = claims.get("user_metadata");
        if (meta instanceof Map<?, ?> map) {
            Object avatar = map.get("avatar_url");
            if (avatar != null) return avatar.toString();
            Object picture = map.get("picture");
            if (picture != null) return picture.toString();
        }
        return null;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Extract the `kid` from the JWT header (without full validation) and
     * return the matching cached PublicKey. Falls back to singleKey if no kid.
     */
    private PublicKey resolveKey(String token) {
        try {
            // JWT is header.payload.signature — split and base64-decode the header
            String[] parts = token.split("\\.");
            if (parts.length < 2) return singleKey;

            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            String headerJson = new String(headerBytes, java.nio.charset.StandardCharsets.UTF_8);

            // Simple string search for kid value — avoids pulling in a JSON library just for this
            String kid = extractStringField(headerJson, "kid");
            if (kid != null && keyCache.containsKey(kid)) {
                return keyCache.get(kid);
            }
        } catch (Exception e) {
            log.debug("Could not extract kid from JWT header: {}", e.getMessage());
        }
        return singleKey; // fallback: try the only / most recent key
    }

    /**
     * Build a Java {@link ECPublicKey} from a JWK map with kty=EC.
     * The x and y coordinates are base64url-encoded big-endian byte arrays.
     */
    private PublicKey buildEcPublicKey(Map<String, Object> jwk) throws Exception {
        String crv = (String) jwk.get("crv");
        if (!"P-256".equals(crv)) {
            throw new IllegalArgumentException("Unsupported EC curve: " + crv);
        }

        byte[] xBytes = Base64.getUrlDecoder().decode((String) jwk.get("x"));
        byte[] yBytes = Base64.getUrlDecoder().decode((String) jwk.get("y"));

        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1")); // secp256r1 == P-256
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

        ECPoint point = new ECPoint(x, y);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecSpec);

        return KeyFactory.getInstance("EC").generatePublic(keySpec);
    }

    /** Naive JSON field extractor — only used on the small JWT header object. */
    private String extractStringField(String json, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
