package com.njupt.aiassistant;

import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.service.llm.LLMService;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "ai.provider=deepseek"
)
class RagChatFlowIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private LLMService llmService;

    @Test
    void answersThreeCampusQuestionsWithRetrievedSources() {
        verifyQuestion(
                "南邮转专业需要什么条件？",
                "教务管理规定.pdf",
                "南京邮电大学教务处",
                "https://jwc.njupt.edu.cn/zzy/page.htm",
                "ACADEMIC",
                0.91,
                "根据资料，转专业要求以当年度通知和目标学院接收条件为准。",
                95
        );
        verifyQuestion(
                "计算机科学与技术专业大一主要课程有哪些？",
                "专业培养方案.pdf",
                "南京邮电大学计算机学院",
                "https://cs.njupt.edu.cn/pyfa/page.htm",
                "MAJOR",
                0.76,
                "根据培养方案，主要课程包括高等数学、程序设计基础和计算机科学导论。",
                85
        );
        verifyQuestion(
                "新生报到需要准备什么？",
                "新生手册.pdf",
                "南京邮电大学学生工作处",
                "https://www.njupt.edu.cn/xinsheng/page.htm",
                "NEW_STUDENT",
                0.58,
                "根据新生手册，应准备录取通知书、身份证件和通知要求的材料。",
                58
        );
    }

    @SuppressWarnings("unchecked")
    private void verifyQuestion(
            String question,
            String filename,
            String source,
            String sourceUrl,
            String category,
            double score,
            String generatedAnswer,
            int expectedConfidence
    ) {
        when(ragClient.search(any())).thenReturn(List.of(
                new RagSearchResultVO(
                        "仅用于当前问题的知识库片段",
                        filename,
                        1,
                        source,
                        sourceUrl,
                        category,
                        score
                )
        ));
        when(llmService.generateAnswer(any(), any(), any()))
                .thenReturn(generatedAnswer);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Anonymous-Session-Id",
                "e3e085a2-ebd7-4cbf-a109-d4dc9b2c0905");
        var response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat/ask",
                new HttpEntity<>(Map.of("question", question), headers),
                Map.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(200);
        var data = (Map<String, Object>) body.get("data");
        assertThat(data.get("answer")).isEqualTo(generatedAnswer);
        assertThat(data.get("confidence")).isEqualTo(expectedConfidence);
        var sources = (List<Map<String, Object>>) data.get("sources");
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).get("title")).isEqualTo(filename);
        assertThat(sources.get(0).get("page")).isEqualTo(1);
        assertThat(sources.get(0).get("source")).isEqualTo(source);
        assertThat(sources.get(0).get("url")).isEqualTo(sourceUrl);
        assertThat(sources.get(0).get("category")).isEqualTo(category);
        assertThat((double) sources.get(0).get("score")).isEqualTo(score);
    }
}
