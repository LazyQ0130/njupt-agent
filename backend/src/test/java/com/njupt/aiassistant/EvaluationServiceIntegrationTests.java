package com.njupt.aiassistant;

import com.njupt.aiassistant.mapper.EvaluationResultMapper;
import com.njupt.aiassistant.service.EvaluationService;
import com.njupt.aiassistant.service.ai.AiProviderRouter;
import com.njupt.aiassistant.service.ai.ChatAnswer;
import com.njupt.aiassistant.service.ai.ChatSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class EvaluationServiceIntegrationTests {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private EvaluationResultMapper resultMapper;

    @MockitoBean
    private AiProviderRouter providerRouter;

    @BeforeEach
    void cleanResults() {
        resultMapper.deleteAll();
    }

    @Test
    void evaluatesAllConfiguredQuestionsAndPersistsAReport() {
        when(providerRouter.answer(anyString())).thenReturn(new ChatAnswer(
                "请按学校通知准备申请材料，并关注课程、时间、地点和相关条件。",
                List.of(new ChatSource(
                        "南京邮电大学学生办事指南",
                        "official",
                        1,
                        0.82,
                        "南京邮电大学",
                        "https://www.njupt.edu.cn/",
                        "ACADEMIC"
                )),
                85
        ));

        var report = evaluationService.run();

        assertThat(report.total()).isEqualTo(109);
        assertThat(resultMapper.count()).isEqualTo(109);
        assertThat(report.runId()).isNotBlank();
        assertThat(report.sourceCoverage()).isEqualTo(100.0);
        assertThat(report.nonEmptyRate()).isEqualTo(100.0);
        assertThat(report.averageScore()).isBetween(0.0, 100.0);
        assertThat(report.categoryScores()).containsOnlyKeys(
                "NEW_STUDENT",
                "ACADEMIC",
                "LIFE",
                "MAJOR",
                "CAREER",
                "SCHOOL_OVERVIEW",
                "ORGANIZATION",
                "RESEARCH"
        );
        Map<String, Long> distribution = report.results().stream()
                .collect(Collectors.groupingBy(
                        item -> item.category(),
                        Collectors.counting()
                ));
        assertThat(distribution).containsExactlyInAnyOrderEntriesOf(Map.of(
                "NEW_STUDENT", 25L,
                "ACADEMIC", 25L,
                "LIFE", 20L,
                "MAJOR", 20L,
                "CAREER", 10L,
                "SCHOOL_OVERVIEW", 3L,
                "ORGANIZATION", 3L,
                "RESEARCH", 3L
        ));
    }
}
