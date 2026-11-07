package com.example.MedRational.Security;

import com.example.MedRational.Repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Regression: POST /api/v1/auth/admin/create used to be public (everything under /api/v1/auth is),
// so anyone could create an admin account. Admins are now only promoted in the database.
@SpringBootTest(properties = "preview.office-enabled=false")
@AutoConfigureMockMvc
class AdminCreationEndpointTest {

    private final String email = "intruder-" + UUID.randomUUID() + "@example.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }

    @Test
    void nobodyCanCreateAnAdminThroughTheApi() throws Exception {
        int status = mvc.perform(post("/api/v1/auth/admin/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andReturn().getResponse().getStatus();

        assertNotEquals(200, status, "the admin creation endpoint must not exist");
        assertFalse(userRepository.existsByEmail(email), "no account may be created");
    }
}
