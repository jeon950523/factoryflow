package com.factoryflow;

import com.factoryflow.security.AppUser;
import com.factoryflow.security.AppUserRepository;
import com.factoryflow.security.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc // IMPORTANT: real Spring Security filters enabled here, unlike legacy business-only tests.
class SessionSecurityIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_security_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    private final String admin = "admin_security_test";
    private final String viewer = "viewer_security_test";
    private final String password = "safe-test-password-only";

    @BeforeEach
    void seedUsers() {
        if (!users.existsByUsername(admin)) {
            users.saveAndFlush(AppUser.create(admin, passwords.encode(password), UserRole.ADMIN));
        }
        if (!users.existsByUsername(viewer)) {
            users.saveAndFlush(AppUser.create(viewer, passwords.encode(password), UserRole.VIEWER));
        }
    }

    record Login(MockHttpSession session, String csrf) {}

    private String csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private Login login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = csrf(session);
        MvcResult login = mvc.perform(post("/api/v1/auth/login").session(session)
                        .header("X-CSRF-TOKEN", before).param("username", username).param("password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();
        // The login strategy rotates the CSRF token and changes the session ID to block fixation.
        String after = csrf(session);
        assertNotEquals(before, after);
        return new Login(session, after);
    }

    @Test
    void unauthenticatedApiMustRejectReadWriteAndSessionExposure() throws Exception {
        mvc.perform(get("/api/v1/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"RM-SEC-001\",\"name\":\"secret\",\"itemType\":\"RAW_MATERIAL\"}"))
                .andExpect(status().isForbidden()); // Missing CSRF must be rejected before authorization.
    }

    @Test
    void adminCanCreateAndViewerMayOnlyReadIncludingAdminUsersProtected() throws Exception {
        Login adminLogin = login(admin, password);
        mvc.perform(get("/api/v1/auth/me").session(adminLogin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        String sku = "RM-AUTH-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/items").session(adminLogin.session())
                        .header("X-CSRF-TOKEN", adminLogin.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"검증 자재\",\"itemType\":\"RAW_MATERIAL\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/users").session(adminLogin.session()))
                .andExpect(status().isOk());

        Login viewerLogin = login(viewer, password);
        mvc.perform(get("/api/v1/items").session(viewerLogin.session())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(viewerLogin.session()))
                .andExpect(jsonPath("$.data.role").value("VIEWER"));
        mvc.perform(get("/api/v1/admin/users").session(viewerLogin.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/items").session(viewerLogin.session())
                        .header("X-CSRF-TOKEN", viewerLogin.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"RM-VIEW-001\",\"name\":\"금지\",\"itemType\":\"RAW_MATERIAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfIsMandatoryForLoggedInAdminAndLogoutRevokesSession() throws Exception {
        Login logged = login(admin, password);
        mvc.perform(post("/api/v1/items").session(logged.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"RM-NO-CSRF\",\"name\":\"금지\",\"itemType\":\"RAW_MATERIAL\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(logged.session())
                        .header("X-CSRF-TOKEN", logged.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedOut").value(true));
        mvc.perform(get("/api/v1/auth/me").session(logged.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginAndWrongCsrfMustNotStartSessionAsAdmin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/auth/login").session(session)
                        .param("username", admin).param("password", password))
                .andExpect(status().isForbidden());
        String token = csrf(session);
        mvc.perform(post("/api/v1/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .param("username", admin).param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLoginAttemptsReturn429WithoutBypassingCsrfOrCreatingLogin() throws Exception {
        // Unique valid-looking name: the result must not disclose whether the account exists.
        String probe = "probe" + UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession session = new MockHttpSession();
        String token = csrf(session);
        for (int attempt = 0; attempt < 8; attempt++) {
            mvc.perform(post("/api/v1/auth/login").session(session)
                            .header("X-CSRF-TOKEN", token)
                            .param("username", probe).param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .param("username", probe).param("password", "wrong-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("LOGIN_RATE_LIMITED"));
        // CSRF protection is ahead of the limiter.
        mvc.perform(post("/api/v1/auth/login").session(session)
                        .param("username", probe).param("password", password))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
