package com.kwikpic.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter matching Node's express-rate-limit config:
 * {@code rateLimit({ windowMs: 60_000, max: 120 })}
 *
 * <p>Tracks request counts per remote IP using a sliding window.
 * When the limit is exceeded, responds with 429 Too Many Requests.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate.limit.max-requests:120}")
    private int maxRequests;

    @Value("${rate.limit.window-ms:60000}")
    private long windowMs;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> new Bucket());

        if (!bucket.tryConsume()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests, please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Simple fixed-window rate limit bucket.
     * Resets the count when the window expires.
     */
    private class Bucket {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowMs) {
                // Window expired — reset
                windowStart = now;
                count = 1;
                return true;
            }
            count++;
            return count <= maxRequests;
        }
    }
}
