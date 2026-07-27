package com.njupt.aiassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiAssistantApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void applicationStartsAndServesCoreApis() {
        var documents = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/documents",
                Map.class
        );

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Anonymous-Session-Id",
                "203e5f61-6820-4596-a02e-ea73b7d022aa");
        var ask = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat/ask",
                new HttpEntity<>(Map.of("question", "南邮转专业需要什么条件"), headers),
                Map.class
        );

        assertThat(documents.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(documents.getBody()).containsEntry("code", 200);
        assertThat(ask.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ask.getBody()).containsEntry("code", 200);
        var answerData = (Map<?, ?>) ask.getBody().get("data");
        assertThat(answerData.get("confidence")).isEqualTo(98);
    }
}
