package org.example.rewards;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class ApiRequestLog extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(ApiRequestLog.class);
    private static final String PREFIX = ApiRequestLog.class.getName() + ".";
    private static final List<String> FIELDS = List.of("userId", "rewardId", "grantId", "grantStatus",
            "expiresAt", "errorCode", "reason");
    private static final Pattern RESOURCE = Pattern.compile(
            "/api/v1/(users|rewards)/([0-9]{1,19})(?:/grants(?:/([0-9a-fA-F-]{36})/use)?)?");

    // Business code can enrich a request without coupling the transaction to a logging backend.
    static void field(String key, Object value) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && value != null) {
            attributes.setAttribute(PREFIX + key, value, RequestAttributes.SCOPE_REQUEST);
        }
    }

    static void grant(Models.Grant grant) {
        field("userId", grant.userId());
        field("rewardId", grant.rewardId());
        field("grantId", grant.id().toString());
        field("grantStatus", grant.status().name());
        field("expiresAt", grant.expiresAt().toInstant().toString());
    }

    static void failure(String code, String reason) {
        field("errorCode", code);
        field("reason", reason);
    }

    static void failure(HttpServletRequest request, String code, String reason) {
        request.setAttribute(PREFIX + "errorCode", code);
        request.setAttribute(PREFIX + "reason", reason);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        long start = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : 500;
            record(request, requestId, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }

    private static void record(HttpServletRequest request, String requestId, int status, long duration) {
        var event = status >= 500 ? LOG.atError() : status >= 400 ? LOG.atWarn() : LOG.atInfo();
        var route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        var handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        boolean replay = Boolean.TRUE.equals(request.getAttribute(PREFIX + "replayed"));
        event.addKeyValue("requestId", requestId)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("action", handler instanceof HandlerMethod hm ? hm.getMethod().getName() : "request")
                .addKeyValue("httpStatus", status)
                .addKeyValue("outcome", status >= 400 ? "failure" : replay ? "replayed" : "success")
                .addKeyValue("durationMs", duration);
        if (route != null) event.addKeyValue("route", route.toString());

        // Security can reject a request before MVC has matched a handler.
        var resource = RESOURCE.matcher(request.getRequestURI());
        if (resource.matches()) {
            try {
                String key = resource.group(1).equals("users") ? "userId" : "rewardId";
                request.setAttribute(PREFIX + key, Long.parseLong(resource.group(2)));
                if (resource.group(3) != null) {
                    request.setAttribute(PREFIX + "grantId", UUID.fromString(resource.group(3)).toString());
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed path values are omitted. The request is still searchable by requestId.
            }
        }
        if (status >= 400 && request.getAttribute(PREFIX + "errorCode") == null) {
            event.addKeyValue("errorCode", "HTTP_" + status);
        }
        for (String field : FIELDS) {
            Object value = request.getAttribute(PREFIX + field);
            if (value != null) event.addKeyValue(field, value);
        }
        event.log("API request completed");
    }
}
