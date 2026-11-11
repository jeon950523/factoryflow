package com.factoryflow.security;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.global.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record NewUser(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{3,31}") String username,
            @NotBlank String password,
            @NotNull UserRole role
    ) { }
    public record UserSummary(Long id, String username, UserRole role, boolean active) {
        static UserSummary of(AppUser user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.isActive());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<UserSummary>> list() {
        return ApiResponse.of(users.findAll().stream().map(UserSummary::of).toList());
    }

    @PostMapping
    public ApiResponse<UserSummary> create(@Valid @RequestBody NewUser request) {
        int bytes = request.password().getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 12 || bytes > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 UTF-8 기준 12~72바이트여야 합니다.");
        }
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "이미 존재하는 계정입니다.");
        }
        try {
            AppUser saved = users.saveAndFlush(AppUser.create(request.username(),
                    encoder.encode(request.password()), request.role()));
            return ApiResponse.of(UserSummary.of(saved));
        } catch (DataIntegrityViolationException collision) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "이미 존재하는 계정입니다.");
        }
    }
}
