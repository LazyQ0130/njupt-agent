package com.njupt.aiassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "rate-limit.chat-ip-per-minute=100",
        "rate-limit.chat-session-per-minute=2"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsTheThirdChatRequestForOneSession() throws Exception {
        var sessionId = "33bc9388-a5c6-4d49-b00f-40b060c2feeb";
        for (var index = 0; index < 2; index++) {
            mockMvc.perform(get("/api/chat/history")
                            .header("X-Anonymous-Session-Id", sessionId))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/chat/history")
                        .header("X-Anonymous-Session-Id", sessionId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value(42901));
    }
}
