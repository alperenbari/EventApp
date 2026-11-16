package com.event.event_ticket_app.security;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final int DEFAULT_MAX_REQUESTS_PER_WINDOW = 20;
    static final long DEFAULT_WINDOW_MS = 60_000;

    private final int maxRequestsPerWindow;
    private final long windowMs;
    private final ConcurrentHashMap<String, RequestWindow> windowsByIp = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        this(DEFAULT_MAX_REQUESTS_PER_WINDOW, DEFAULT_WINDOW_MS);
    }

    RateLimitFilter(int maxRequestsPerWindow, long windowMs) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMs = windowMs;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        RequestWindow window = windowsByIp.computeIfAbsent(request.getRemoteAddr(), ip -> new RequestWindow());

        if (window.exceedsLimit()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests, please try again later\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedRate = DEFAULT_WINDOW_MS)
    void evictExpiredWindows() {
        long now = System.currentTimeMillis();
        windowsByIp.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMs));
    }

    int trackedIpCount() {
        return windowsByIp.size();
    }

    private final class RequestWindow {
        private long startedAt = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean exceedsLimit() {
            long now = System.currentTimeMillis();
            if (isExpired(now, windowMs)) {
                startedAt = now;
                count = 0;
            }
            count++;
            return count > maxRequestsPerWindow;
        }

        synchronized boolean isExpired(long now, long ttlMs) {
            return now - startedAt > ttlMs;
        }
    }
}
