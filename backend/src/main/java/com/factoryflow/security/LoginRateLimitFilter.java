package com.factoryflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Check quota after CSRF validation and before password hashing. */
final class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptLimiter limiter;
    private final ObjectMapper json;

    LoginRateLimitFilter(LoginAttemptLimiter limiter, ObjectMapper json) {
        this.limiter = limiter;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return true;
        // MockMvc may leave servletPath empty even though requestURI contains the endpoint.
        // Prefer the servlet container's normalized servletPath; fall back to the URI
        // relative to the context path when the servletPath is not available.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath + "/")) {
                path = path.substring(contextPath.length());
            }
        }
        return !"/api/v1/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        LoginAttemptLimiter.Decision decision = limiter.reserve(
                request.getParameter("username"), request.getRemoteAddr());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            SecurityConfig.write(response, 429, json,
                    new SecurityConfig.AuthError("LOGIN_RATE_LIMITED", "로그인 요청이 많습니다. 잠시 후 다시 시도하세요."));
            return;
        }
        chain.doFilter(request, response);
    }
}
