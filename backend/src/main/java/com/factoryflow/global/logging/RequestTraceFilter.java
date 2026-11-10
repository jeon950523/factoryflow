package com.factoryflow.global.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 서버에서 요청별 식별자를 발급해 응답 헤더와 애플리케이션 로그를 연결한다. */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ID_ATTRIBUTE = "factoryflowTraceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader("X-Trace-Id", traceId);
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId"); // 스레드풀 재사용 시 다른 요청에 오염되지 않도록 반드시 제거
        }
    }
}
