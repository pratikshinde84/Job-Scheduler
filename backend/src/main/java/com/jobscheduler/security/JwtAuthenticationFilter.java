package com.jobscheduler.security;

import com.jobscheduler.entity.User;
import com.jobscheduler.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the Supabase JWT on every request, auto-provisions the local User
 * record on first visit, and populates Spring's SecurityContext.
 *
 * If the token is missing or invalid we simply do NOT populate the SecurityContext
 * and let Spring Security's ExceptionTranslationFilter return a clean 401.
 * We never write directly to the response here — doing so causes Spring Security
 * to overwrite our response with a 500.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token — just continue; Spring Security will enforce auth downstream
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Optional<Claims> claimsOpt = jwtService.validateAndExtract(token);

            if (claimsOpt.isPresent()) {
                Claims claims = claimsOpt.get();
                UUID   userId = jwtService.extractUserId(claims);
                String email  = jwtService.extractEmail(claims);
                String name   = jwtService.extractName(claims);
                String avatar = jwtService.extractAvatarUrl(claims);

                // ── Auto-provision user on first login ───────────────────────
                User user = userRepository.findById(userId).orElseGet(() -> {
                    log.info("First-time user, provisioning: id={} email={}", userId, email);
                    return userRepository.save(User.builder()
                            .id(userId)
                            .email(email)
                            .name(name != null ? name : "")
                            .avatarUrl(avatar)
                            .build());
                });

                // Sync mutable OAuth fields (name/avatar can change between logins)
                boolean dirty = false;
                if (name != null && !name.equals(user.getName())) {
                    user.setName(name); dirty = true;
                }
                if (avatar != null && !avatar.equals(user.getAvatarUrl())) {
                    user.setAvatarUrl(avatar); dirty = true;
                }
                if (dirty) user = userRepository.save(user);

                // ── Populate SecurityContext ─────────────────────────────────
                UserContext.set(user);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user, null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            } else {
                log.debug("Invalid JWT on request: {}", request.getRequestURI());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Always clean up thread-locals regardless of outcome
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
