package com.njupt.aiassistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.common.RequestHeaders;
import com.njupt.aiassistant.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            RateLimitProperties properties,
            ObjectMapper objectMapper
    ) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var path = request.getRequestURI();
        if (path.startsWith("/api/chat/")) {
            if (!consume(
                    "chat:ip:" + request.getRemoteAddr(),
                    properties.chatIpPerMinute()
            )) {
                reject(response);
                return;
            }
            var sessionId = request.getHeader(
                    RequestHeaders.ANONYMOUS_SESSION_ID
            );
            if (sessionId != null && !sessionId.isBlank()
                    && !consume(
                            "chat:session:" + sessionId.trim(),
                            properties.chatSessionPerMinute()
                    )) {
                reject(response);
                return;
            }
        } else if (path.equals("/api/admin/auth/login")) {
            if (!consume(
                    "admin-login:ip:" + request.getRemoteAddr(),
                    properties.adminLoginIpPerMinute()
            )) {
                reject(response);
                return;
            }
        } else if (path.startsWith("/api/admin/")) {
            if (!consume(
                    "admin:ip:" + request.getRemoteAddr(),
                    properties.adminIpPerMinute()
            )) {
                reject(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean consume(String key, int capacity) {
        return rateLimitService.tryConsume(key, capacity);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(
                        ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                        ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()
                )
        );
    }
}
