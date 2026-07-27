package com.njupt.aiassistant.service.llm;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.DeepSeekProperties;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.ai.AiProviderConfigurationService;
import com.njupt.aiassistant.service.ai.AiRuntimeConfig;
import com.njupt.aiassistant.service.ai.AiUsageService;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekLLMServiceTests {

    @Test
    void recordsAllUsageCountersWithoutPromptOrAnswerPayloads() {
        var builder = RestClient.builder().baseUrl("http://localhost:18081");
        var server = MockRestServiceServer.bindTo(builder).build();
        var configuration = mock(AiProviderConfigurationService.class);
        var usageService = mock(AiUsageService.class);
        when(configuration.resolve()).thenReturn(
                new AiRuntimeConfig(
                        "test-secret",
                        "deepseek-v4-flash",
                        true,
                        "DATABASE"
                )
        );
        var service = new DeepSeekLLMService(
                builder.build(),
                configuration,
                usageService,
                properties(""),
                new RagPromptBuilder()
        );
        server.expect(once(), requestTo("http://localhost:18081/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {"role": "assistant", "content": "测试回答"}
                          }],
                          "usage": {
                            "prompt_tokens": 120,
                            "completion_tokens": 30,
                            "total_tokens": 150,
                            "prompt_cache_hit_tokens": 80,
                            "prompt_cache_miss_tokens": 40
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.generateAnswer("测试问题", List.of(result(0.91))))
                .isEqualTo("测试回答");
        verify(usageService).recordSuccess(
                eq("deepseek-v4-flash"),
                eq(new AiUsageService.Usage(120, 30, 150, 80, 40)),
                anyLong()
        );
        server.verify();
    }

    @Test
    void sendsGroundedMessagesAndReturnsAssistantContent() {
        var builder = RestClient.builder().baseUrl("http://localhost:18081");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new DeepSeekLLMService(
                builder.build(),
                properties("test-secret"),
                new RagPromptBuilder()
        );
        server.expect(once(), requestTo("http://localhost:18081/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-secret"))
                .andExpect(content().string(containsString("\"model\":\"deepseek-chat\"")))
                .andExpect(content().string(containsString("\"role\":\"system\"")))
                .andExpect(content().string(containsString("只能依据知识库内容回答")))
                .andExpect(content().string(containsString("教务管理规定.pdf")))
                .andExpect(content().string(containsString("南邮转专业需要什么条件")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "根据资料，申请要求以当年度通知为准。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var answer = service.generateAnswer(
                "南邮转专业需要什么条件",
                List.of(result(0.91))
        );

        assertThat(answer).isEqualTo("根据资料，申请要求以当年度通知为准。");
        server.verify();
    }

    @Test
    void mapsInsufficientBalanceToFriendlyError() {
        var builder = RestClient.builder().baseUrl("http://localhost:18081");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new DeepSeekLLMService(
                builder.build(),
                properties("test-secret"),
                new RagPromptBuilder()
        );
        server.expect(once(), requestTo("http://localhost:18081/chat/completions"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));

        assertThatThrownBy(() -> service.generateAnswer(
                "测试问题",
                List.of(result(0.91))
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.AI_SERVICE_FAILED);
                    assertThat(exception.getMessage())
                            .isEqualTo("AI服务暂时不可用，请稍后重试。");
                    assertThat(exception.getMessage()).doesNotContain("test-secret");
                }
        );
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyWithoutSendingRequest() {
        var service = new DeepSeekLLMService(
                RestClient.builder().baseUrl("http://localhost:18081").build(),
                properties(""),
                new RagPromptBuilder()
        );

        assertThatThrownBy(() -> service.generateAnswer(
                "测试问题",
                List.of(result(0.91))
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE)
        );
    }

    @Test
    void mapsNetworkAndTimeoutFailuresToFriendlyError() {
        var builder = RestClient.builder().baseUrl("http://localhost:18081");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new DeepSeekLLMService(
                builder.build(),
                properties("test-secret"),
                new RagPromptBuilder()
        );
        server.expect(once(), requestTo("http://localhost:18081/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("simulated timeout");
                });

        assertThatThrownBy(() -> service.generateAnswer(
                "测试问题",
                List.of(result(0.91))
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .isEqualTo("AI服务暂时不可用，请稍后重试。");
                    assertThat(exception.getMessage()).doesNotContain("test-secret");
                }
        );
        server.verify();
    }

    private DeepSeekProperties properties(String apiKey) {
        return new DeepSeekProperties(
                apiKey,
                URI.create("http://localhost:18081"),
                "deepseek-chat",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                800
        );
    }

    private RagSearchResultVO result(double score) {
        return new RagSearchResultVO(
                "学生申请转专业应关注教务处当年度通知。",
                "教务管理规定.pdf",
                3,
                "南京邮电大学教务处",
                score
        );
    }
}
