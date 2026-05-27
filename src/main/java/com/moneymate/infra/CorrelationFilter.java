package com.moneymate.infra;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that attaches a Correlation ID to every request.
 *
 * WHY THIS EXISTS:
 *   In production, a single user action (e.g. "sync transactions") triggers multiple
 *   downstream calls: database queries, Redis reads, SMS gateway calls.  Without a shared
 *   identifier those log lines are impossible to group together when debugging.
 *   This filter stamps every log line in the request's thread with the same ID.
 *
 * HOW IT WORKS:
 *   1. Read the X-Correlation-ID header from the incoming request.
 *      If the caller (API gateway, another service, mobile client) already provided one,
 *      we reuse it so the ID is consistent across the whole call chain.
 *   2. If no header is present, generate a UUID — this request is the origin.
 *   3. Write the ID into SLF4J's MDC (Mapped Diagnostic Context), a thread-local Map
 *      that Logback reads when formatting each log line.
 *   4. Echo the ID back in the response header so the client can correlate its own logs.
 *   5. ALWAYS clear MDC in the finally block — thread pools reuse threads, so a missed
 *      clear would leak one request's correlationId into the next request on that thread.
 *
 * LOG FORMAT (logback-spring.xml):
 *   %X{correlationId:--} emits the value or "--" when the key is absent.
 *   Example: 12:34:56.789 INFO  [3f2a1b...] SyncController - Push received
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // run before all other filters, including Spring Security
public class CorrelationFilter implements Filter {

    public static final String HEADER  = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req = (HttpServletRequest)  request;
        HttpServletResponse res = (HttpServletResponse) response;

        String correlationId = req.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        res.setHeader(HEADER, correlationId);   // echo back for client-side correlation

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // MUST clear — thread-local state leaks across thread-pool reuse
        }
    }
}
