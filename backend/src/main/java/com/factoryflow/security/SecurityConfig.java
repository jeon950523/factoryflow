package com.factoryflow.security;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.security.AuthController.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter() {
        return new LoginAttemptLimiter(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            UserDetailsService userDetailsService,
                                            PasswordEncoder encoder,
                                            LoginAttemptLimiter limiter,
                                            ObjectMapper json) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        HttpSessionCsrfTokenRepository csrfRepo = new HttpSessionCsrfTokenRepository();

        http.authenticationProvider(provider);
        // The CSRF filter runs earlier; this filter reserves a slot before BCrypt.
        http.addFilterBefore(new LoginRateLimitFilter(limiter, json),
                UsernamePasswordAuthenticationFilter.class);
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info",
                        "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers("/api/v1/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form
                .loginProcessingUrl("/api/v1/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    limiter.authenticated(authentication.getName());
                    write(response, 200, json, ApiResponse.of(CurrentUser.from(authentication)));
                })
                .failureHandler((request, response, exception) ->
                    write(response, 401, json, new AuthError("INVALID_CREDENTIALS",
                            "아이디 또는 비밀번호가 올바르지 않습니다."))))
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                    write(response, 200, json, ApiResponse.of(new LogoutResult(true)))))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    write(response, 401, json, new AuthError("UNAUTHENTICATED", "로그인이 필요합니다.")))
                .accessDeniedHandler((request, response, exception) ->
                    write(response, 403, json, new AuthError("FORBIDDEN", "해당 작업에 대한 권한이 없습니다."))));
        return http.build();
    }

    public record AuthError(String errorCode, String message) { }
    public record LogoutResult(boolean loggedOut) { }

    static void write(HttpServletResponse response, int status,
                              ObjectMapper json, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.writeValueAsString(body));
    }
}
