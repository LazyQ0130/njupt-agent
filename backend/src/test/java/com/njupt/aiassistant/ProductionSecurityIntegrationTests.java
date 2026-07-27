package com.njupt.aiassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.entity.ConversationEntity;
import com.njupt.aiassistant.entity.MessageEntity;
import com.njupt.aiassistant.entity.MessageRole;
import com.njupt.aiassistant.mapper.ConversationMapper;
import com.njupt.aiassistant.mapper.MessageMapper;
import com.njupt.aiassistant.service.AnonymousConversationCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ProductionSecurityIntegrationTests {

    private static final String SESSION_HEADER = "X-Anonymous-Session-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private AnonymousConversationCleanupService cleanupService;

    @BeforeEach
    void cleanConversations() {
        messageMapper.deleteAll();
        conversationMapper.deleteAll();
    }

    @Test
    void studentsChatWithoutLoginAndSessionsAreIsolated() throws Exception {
        var sessionA = createAnonymousSession();
        var sessionB = createAnonymousSession();
        assertThat(sessionA).isNotEqualTo(sessionB);

        var created = mockMvc.perform(post("/api/chat/conversation")
                        .header(SESSION_HEADER, sessionA))
                .andExpect(status().isOk())
                .andReturn();
        var conversationId = objectMapper.readTree(
                created.getResponse().getContentAsByteArray()
        ).path("data").path("id").asLong();

        mockMvc.perform(get("/api/chat/conversation/{id}", conversationId)
                        .header(SESSION_HEADER, sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId));

        mockMvc.perform(get("/api/chat/conversation/{id}", conversationId)
                        .header(SESSION_HEADER, sessionB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40402));

        mockMvc.perform(post("/api/chat/ask")
                        .header(SESSION_HEADER, sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"南邮如何选课？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void adminApisRequireAValidAdminJwtAndDoNotLeakSecrets() throws Exception {
        mockMvc.perform(get("/api/admin/quality/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        var invalid = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(invalid)
                .doesNotContain("test-admin-jwt-secret")
                .doesNotContain("stackTrace");

        var token = loginAdmin();
        mockMvc.perform(get("/api/admin/quality/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void cleanupRemovesExpiredConversationsAndMessages() {
        var conversation = new ConversationEntity();
        conversation.setAnonymousSessionId(UUID.randomUUID().toString());
        conversation.setTitle("过期对话");
        conversation.setExpireTime(LocalDateTime.now().minusMinutes(1));
        conversation = conversationMapper.save(conversation);

        var message = new MessageEntity();
        message.setConversationId(conversation.getId());
        message.setRole(MessageRole.USER);
        message.setContent("即将清理");
        messageMapper.save(message);

        assertThat(cleanupService.cleanupExpired()).isEqualTo(1);
        assertThat(conversationMapper.findById(conversation.getId())).isEmpty();
        assertThat(messageMapper.findByConversationIdOrderByCreatedTimeAsc(
                conversation.getId()
        )).isEmpty();
    }

    private String createAnonymousSession() throws Exception {
        var result = mockMvc.perform(post("/api/session/anonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anonymousSessionId").isString())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        ).path("data").path("anonymousSessionId").asText();
    }

    private String loginAdmin() throws Exception {
        var result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"test-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        ).path("data");
        return data.path("accessToken").asText();
    }
}
