package com.factoryflow.security;

import com.factoryflow.global.api.ApiResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record CsrfResponse(String token, String headerName) { }
    public record CurrentUser(String username, String role) {
        public static CurrentUser from(Authentication authentication) {
            String role = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .findFirst().orElse("VIEWER");
            return new CurrentUser(authentication.getName(), role);
        }
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(@RequestAttribute("_csrf") CsrfToken csrf) {
        // Spring Security 7 returns the request-masked token; POST must send this exact value.
        return ApiResponse.of(new CsrfResponse(csrf.getToken(), csrf.getHeaderName()));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(Authentication authentication) {
        return ApiResponse.of(CurrentUser.from(authentication));
    }
}
