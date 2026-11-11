package com.factoryflow.security;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fail closed for a new database: never silently ship a known admin password.
 * Existing installations do not rehash/overwrite their admin account on startup.
 */
@Component
@Profile("!test")
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder,
                          @Value("${FACTORYFLOW_ADMIN_USERNAME:}") String username,
                          @Value("${FACTORYFLOW_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (users.count() > 0) return;
        if (!username.matches("[A-Za-z][A-Za-z0-9._-]{3,31}") || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Fresh database requires FACTORYFLOW_ADMIN_USERNAME "
                    + "(4-32 ASCII chars) and FACTORYFLOW_ADMIN_PASSWORD (12-72 UTF-8 bytes).");
        }
        users.saveAndFlush(AppUser.create(username, encoder.encode(password), UserRole.ADMIN));
    }
}
