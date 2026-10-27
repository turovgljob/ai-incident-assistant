package com.example.incidents.api;

import com.example.incidents.llm.FakeLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeLlmClient llm;

    @BeforeEach
    void resetLlm() {
        llm.reset();
    }

    @Test
    void returnsMachineReadableAnalysis() throws Exception {
        llm.reply("""
                {
                  "category": "User authentication errors",
                  "summary": "auth-service rejects tokens with invalid signatures, so some mobile users cannot log in.",
                  "severity": "high",
                  "hypotheses": [
                    {
                      "title": "Signing key rotation mismatch",
                      "reasoning": "Invalid signature errors appear right after tokens are validated.",
                      "next_steps": ["Check recent key rotation in auth-service.", "Compare JWKS served to the mobile app."]
                    }
                  ]
                }
                """);

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Some customers cannot log in via the mobile app, auth-service responds with 401."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("User authentication errors"))
                .andExpect(jsonPath("$.severity").value("high"))
                .andExpect(jsonPath("$.hypotheses", hasSize(1)))
                .andExpect(jsonPath("$.hypotheses[0].next_steps", hasSize(2)))
                .andExpect(jsonPath("$.meta.source").value("llm"))
                .andExpect(jsonPath("$.meta.attempts").value(1))
                .andExpect(jsonPath("$.meta.input_language").value("en"))
                .andExpect(jsonPath("$.meta.similar_incidents[0]").value("INC-104"));
    }

    @Test
    void rejectsBlankDescription() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class FakeLlmConfiguration {

        @Bean
        @Primary
        FakeLlmClient fakeLlmClient() {
            return new FakeLlmClient();
        }
    }
}
