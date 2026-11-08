package com.example.MedRational.Security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Over HTTP, against the real context: the limits apply to the auth endpoints and answer 429 with
// Retry-After. Low limits here; the IP limits stay high, since every MockMvc request comes from the
// same address. Unknown emails, so no code is ever emailed.
@SpringBootTest(properties = {
        "preview.office-enabled=false",
        "rate-limit.login.target.limit=3",
        "rate-limit.send-code.target.limit=2",
        "rate-limit.verify-code.target.limit=2",
        "rate-limit.login.ip.limit=1000",
        "rate-limit.send-code.ip.limit=1000",
        "rate-limit.verify-code.ip.limit=1000"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void tooManySignInsForOneEmailAnswer429() throws Exception {
        String email = unique();
        for (int i = 0; i < 3; i++) {
            login(email).andExpect(status().isUnauthorized());
        }

        login(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // Another email isn't affected
        login(unique()).andExpect(status().isUnauthorized());
    }

    @Test
    void tooManyResetCodesAnswer429() throws Exception {
        String body = "{\"email\":\"" + unique() + "\"}";
        for (int i = 0; i < 2; i++) {
            post("/api/v1/auth/forgot-password", body).andExpect(status().isBadRequest());
        }

        post("/api/v1/auth/forgot-password", body).andExpect(status().isTooManyRequests());
    }

    @Test
    void tooManyCodeChecksAnswer429() throws Exception {
        String body = "{\"email\":\"" + unique() + "\",\"code\":\"123456\"}";
        post("/api/v1/auth/delete-account/confirm", body);
        post("/api/v1/auth/delete-account/confirm", body);

        post("/api/v1/auth/delete-account/confirm", body).andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String email) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}");
    }

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String unique() {
        return "rate-" + UUID.randomUUID() + "@example.com";
    }
}
