package com.njupt.aiassistant.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.config.RagAnswerProperties;
import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.service.llm.LLMService;
import com.njupt.aiassistant.service.retrieval.QuestionClassifier;
import com.njupt.aiassistant.service.retrieval.QueryTermNormalizer;
import com.njupt.aiassistant.service.retrieval.Retriever;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekRagChatAnswerProviderTests {

    @Test
    void retrievesTopFiveGeneratesAnswerAndBuildsSources() {
        var retriever = mock(Retriever.class);
        var llmService = mock(LLMService.class);
        var provider = new DeepSeekRagChatAnswerProvider(
                retriever,
                llmService,
                new RagAnswerProperties(5, 20, 0.35),
                new QuestionClassifier(),
                new QueryTermNormalizer(new ObjectMapper())
        );
        var results = List.of(
                result("教务管理规定.pdf", 3, 0.91),
                result("教务管理规定.pdf", 3, 0.88),
                result("学院接收办法.pdf", 2, 0.76)
        );
        when(retriever.retrieve(any(), anyInt(), any()))
                .thenReturn(results);
        when(llmService.generateAnswer(any(), any(), any()))
                .thenReturn("根据知识库，具体要求以当年度通知为准。");

        var answer = provider.answer("南邮转专业需要什么条件？");

        assertThat(answer.answer())
                .isEqualTo("根据知识库，具体要求以当年度通知为准。");
        assertThat(answer.confidence()).isEqualTo(95);
        assertThat(answer.sources()).hasSize(2);
        assertThat(answer.sources().get(0).title())
                .isEqualTo("教务管理规定.pdf");
        assertThat(answer.sources().get(0).page()).isEqualTo(3);
        assertThat(answer.sources().get(0).source())
                .isEqualTo("南京邮电大学教务处");

        verify(retriever).retrieve(
                argThat(query -> query.contains("南邮转专业需要什么条件？")
                        && query.contains("检索规范词：南京邮电大学")),
                eq(20),
                eq(DocumentCategory.ACADEMIC)
        );
        verify(llmService).generateAnswer(
                "南邮转专业需要什么条件？",
                results,
                List.of()
        );
    }

    @Test
    void returnsFixedNoKnowledgeAnswerWithoutCallingLlm() {
        var retriever = mock(Retriever.class);
        var llmService = mock(LLMService.class);
        var provider = new DeepSeekRagChatAnswerProvider(
                retriever,
                llmService,
                new RagAnswerProperties(5, 20, 0.35),
                new QuestionClassifier(),
                new QueryTermNormalizer(new ObjectMapper())
        );
        when(retriever.retrieve(any(), anyInt(), any()))
                .thenReturn(List.of(
                result("无关资料.pdf", 1, 0.31)
        ));

        var answer = provider.answer("学校附近哪里可以学习潜水？");

        assertThat(answer.answer())
                .isEqualTo("知识库中暂无相关信息，请咨询相关部门。");
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.confidence()).isZero();
        verify(llmService, never()).generateAnswer(any(), any(), any());
    }

    @Test
    void expandsCampusAliasForRetrievalButKeepsOriginalQuestionForAnswer() {
        var retriever = mock(Retriever.class);
        var llmService = mock(LLMService.class);
        var provider = new DeepSeekRagChatAnswerProvider(
                retriever,
                llmService,
                new RagAnswerProperties(5, 20, 0.35),
                new QuestionClassifier(),
                new QueryTermNormalizer(new ObjectMapper())
        );
        var source = result("早锻炼安排.html", 1, 0.91);
        when(retriever.retrieve(any(), anyInt(), any())).thenReturn(List.of(source));
        when(llmService.generateAnswer(any(), any(), any()))
                .thenReturn("早锻炼安排以学校当年通知为准。");

        var answer = provider.answer("讲一下晨跑");

        assertThat(answer.answer()).isEqualTo("早锻炼安排以学校当年通知为准。");
        verify(retriever).retrieve(
                argThat(query -> query.contains("讲一下晨跑")
                        && query.contains("检索规范词：早锻炼")),
                eq(20),
                eq(DocumentCategory.LIFE)
        );
        verify(llmService).generateAnswer(
                eq("讲一下晨跑"),
                eq(List.of(source)),
                eq(List.of())
        );
    }

    @Test
    void filtersCandidatesBeforeTakingContextAndKeepsAutomationPageTwo() {
        var retriever = mock(Retriever.class);
        var llmService = mock(LLMService.class);
        var provider = new DeepSeekRagChatAnswerProvider(
                retriever,
                llmService,
                new RagAnswerProperties(5, 20, 0.35),
                new QuestionClassifier(),
                new QueryTermNormalizer(new ObjectMapper())
        );
        var automation = new RagSearchResultVO(
                "自动化专业接收转专业学生的条件与考核要求",
                "25自动化转专业.pdf",
                2,
                "自动化学院",
                null,
                "ACADEMIC",
                0.48
        );
        when(retriever.retrieve(any(), anyInt(), any())).thenReturn(List.of(
                result("低分分类结果1.html", 1, 0.19),
                result("低分分类结果2.html", 1, 0.18),
                result("低分分类结果3.html", 1, 0.15),
                result("理学院转专业方案.html", 1, 0.51),
                result("通信学院转专业方案.html", 1, 0.49),
                automation,
                result("其他学院1.html", 1, 0.45),
                result("其他学院2.html", 1, 0.44),
                result("其他学院3.html", 1, 0.43)
        ));
        when(llmService.generateAnswer(any(), any(), any()))
                .thenReturn("自动化专业转专业条件见该文件第 2 页。");

        var answer = provider.answer("自动化专业转专业条件");

        assertThat(answer.answer()).doesNotContain("暂无自动化专业");
        assertThat(answer.sources())
                .anySatisfy(source -> {
                    assertThat(source.title())
                            .isEqualTo("25自动化转专业.pdf");
                    assertThat(source.page()).isEqualTo(2);
                });
        verify(retriever).retrieve(
                argThat(query -> query.contains("转专业目标：自动化")),
                eq(20),
                eq(DocumentCategory.ACADEMIC)
        );
    }

    @Test
    void routesSchoolOverviewQuestionAndKeepsOfficialPageCitation() {
        var retriever = mock(Retriever.class);
        var llmService = mock(LLMService.class);
        var provider = new DeepSeekRagChatAnswerProvider(
                retriever,
                llmService,
                new RagAnswerProperties(5, 20, 0.35),
                new QuestionClassifier(),
                new QueryTermNormalizer(new ObjectMapper())
        );
        var sourceUrl = "https://www.njupt.edu.cn/17223/list.htm";
        var result = new RagSearchResultVO(
                "南邮校训为厚德、弘毅、求是、笃行。",
                "校标校训",
                1,
                "南京邮电大学官网",
                sourceUrl,
                "SCHOOL_OVERVIEW",
                0.91
        );
        when(retriever.retrieve(any(), anyInt(), any()))
                .thenReturn(List.of(result));
        when(llmService.generateAnswer(any(), any(), any()))
                .thenReturn("南邮校训是“厚德、弘毅、求是、笃行”。");

        var answer = provider.answer("南邮校训是什么？");

        assertThat(answer.answer()).contains("厚德", "弘毅", "求是", "笃行");
        assertThat(answer.sources()).singleElement()
                .satisfies(source -> {
                    assertThat(source.title()).isEqualTo("校标校训");
                    assertThat(source.url()).isEqualTo(sourceUrl);
                    assertThat(source.category())
                            .isEqualTo("SCHOOL_OVERVIEW");
                });
        verify(retriever).retrieve(
                argThat(query -> query.contains("南邮校训是什么？")
                        && query.contains("检索规范词：南京邮电大学")),
                eq(20),
                eq(DocumentCategory.SCHOOL_OVERVIEW)
        );
    }

    @Test
    void calculatesConfidenceOnlyFromRetrievalScore() {
        assertThat(DeepSeekRagChatAnswerProvider.calculateConfidence(0.92))
                .isEqualTo(95);
        assertThat(DeepSeekRagChatAnswerProvider.calculateConfidence(0.85))
                .isEqualTo(95);
        assertThat(DeepSeekRagChatAnswerProvider.calculateConfidence(0.70))
                .isEqualTo(85);
        assertThat(DeepSeekRagChatAnswerProvider.calculateConfidence(0.69))
                .isEqualTo(59);
        assertThat(DeepSeekRagChatAnswerProvider.calculateConfidence(0.43))
                .isEqualTo(43);
    }

    private RagSearchResultVO result(
            String filename,
            int page,
            double score
    ) {
        return new RagSearchResultVO(
                "联调测试资料内容",
                filename,
                page,
                "南京邮电大学教务处",
                score
        );
    }
}
