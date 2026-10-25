package com.flowboard.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a request id to the logging context and emits one access line per API request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("flowboard.access");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String requestId = req.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        res.setHeader("X-Request-Id", requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            if (req.getRequestURI().startsWith("/api/")) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                log.info("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), ms);
            }
            MDC.clear();
        }
    }
}
