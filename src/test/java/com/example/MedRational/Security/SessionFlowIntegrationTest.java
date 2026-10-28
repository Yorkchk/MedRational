package com.example.MedRational.Security;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Repositories.UserSessionRepository;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The whole session cycle over HTTP, against the real database: sign in, renew, sign out, and the
// 401 (sign in or renew) / 403 (not allowed) split the app relies on
@SpringBootTest(properties = "preview.office-enabled=false")
@AutoConfigureMockMvc
class SessionFlowIntegrationTest {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSessionRepository sessionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.save(User.builder()
                .email("session-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Session Test")
                .role(Role.ROLE_USER)
                .emailVerified(true)
                .build());
    }

    @AfterEach
    void deleteUser() {
        sessionRepository.deleteAll(sessionRepository.findAll().stream()
                .filter(s -> s.getUser().getId().equals(user.getId())).toList());
        userRepository.deleteById(user.getId());
    }

    private String signIn() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    @Test
    void noTokenIsA401SoTheAppKnowsToSignIn() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    // Regression: an expired token used to answer 403, which the app can't tell from "not allowed"
    @Test
    void anExpiredTokenIsA401SoTheAppRenewsIt() throws Exception {
        String expired = Jwts.builder()
                .subject(user.getEmail())
                .claim("role", "ROLE_USER")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInStudentCallingAnAdminEndpointIsStillA403() throws Exception {
        String token = jwtUtil.generateToken(user.getEmail(), "ROLE_USER");

        mvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signInRenewAndSignOut() throws Exception {
        String signedIn = signIn();
        String firstRefresh = JsonPath.read(signedIn, "$.refreshToken");
        String firstJwt = JsonPath.read(signedIn, "$.token");

        // The JWT works
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + firstJwt))
                .andExpect(status().isOk());

        // Renewing gives a new pair, and the old refresh token stops working
        String renewed = refresh(firstRefresh).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondRefresh = JsonPath.read(renewed, "$.refreshToken");
        assertNotEquals(firstRefresh, secondRefresh);
        assertEquals(user.getId().intValue(), (int) JsonPath.read(renewed, "$.userId"));
        refresh(firstRefresh).andExpect(status().isUnauthorized());

        // Still one row for this device
        assertEquals(1, sessionRepository.findAll().stream()
                .filter(s -> s.getUser().getId().equals(user.getId())).count());

        // Signing out ends the session
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(status().isNoContent());
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }
}
