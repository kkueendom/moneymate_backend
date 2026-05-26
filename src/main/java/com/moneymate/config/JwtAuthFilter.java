package com.moneymate.config;

import com.moneymate.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String JWT_BLACKLIST_PREFIX = "jwt_bl:";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip JWT validation for Swagger UI and API docs
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/swagger-ui/") || requestUri.startsWith("/api-docs/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                Claims claims = jwtUtil.parseToken(token);
                // Reject tokens that have been explicitly revoked via logout.
                // Without this check a stolen/leaked access token remains usable until its TTL
                // expires even after the user has logged out.
                String jti = claims.getId();
                if (Boolean.TRUE.equals(redis.hasKey(JWT_BLACKLIST_PREFIX + jti))) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String userId = claims.getSubject();
                // Confirm user still exists and is not deleted
                if (userRepository.existsByIdAndDeletedFalse(userId)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
