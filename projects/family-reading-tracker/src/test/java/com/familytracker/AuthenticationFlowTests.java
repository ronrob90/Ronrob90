package com.familytracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void anonymousAndInvalidTokensCannotReadOrWriteBooks() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/api/openlibrary/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Example book\"}")
        ).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/books").header("Authorization", "Bearer invalid")).andExpect(
            status().isUnauthorized()
        );
    }

    @Test
    void registrationLoginAndBookImportWorkWithoutExposingPassword() throws Exception {
        String name = "demo-" + UUID.randomUUID();
        String body = json.writeValueAsString(
            java.util.Map.of(
                "username",
                name,
                "password",
                "local-test-only-123",
                "role",
                "ROLE_PARENT"
            )
        );
        mvc.perform(
            post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated());
        String result = mvc
            .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String token = json.readTree(result).path("token").asText();
        mvc.perform(get("/api/families").header("Authorization", "Bearer " + token)).andExpect(
            status().isOk()
        );
        mvc.perform(
            post("/api/openlibrary/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sample book\",\"author\":\"Example Author\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Sample book"));
    }

    @Test
    void registrationRejectsAdministrativeRoles() throws Exception {
        mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"example-admin\",\"password\":\"local-test-only-123\",\"role\":\"ROLE_ADMIN\"}"
                )
        ).andExpect(status().isBadRequest());
    }
}
