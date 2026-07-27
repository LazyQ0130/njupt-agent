package com.njupt.aiassistant;

import com.njupt.aiassistant.dto.AnswerFeedbackRequest;
import com.njupt.aiassistant.dto.ConversationMessageRequest;
import com.njupt.aiassistant.entity.FeedbackType;
import com.njupt.aiassistant.mapper.AnswerFeedbackMapper;
import com.njupt.aiassistant.mapper.ChatHistoryMapper;
import com.njupt.aiassistant.mapper.ConversationMapper;
import com.njupt.aiassistant.mapper.MessageMapper;
import com.njupt.aiassistant.service.ConversationService;
import com.njupt.aiassistant.service.FeedbackService;
import com.njupt.aiassistant.service.ai.AiProviderRouter;
import com.njupt.aiassistant.service.ai.ChatAnswer;
import com.njupt.aiassistant.service.ai.ChatSource;
import com.njupt.aiassistant.service.ai.DialogueMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class ConversationFeedbackIntegrationTests {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private AnswerFeedbackMapper feedbackMapper;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @MockitoBean
    private AiProviderRouter providerRouter;

    @BeforeEach
    void cleanDatabase() {
        feedbackMapper.deleteAll();
        messageMapper.deleteAll();
        chatHistoryMapper.deleteAll();
        conversationMapper.deleteAll();
    }

    @Test
    void keepsConversationContextAndUpsertsAnswerFeedback() {
        var anonymousSessionId = UUID.randomUUID().toString();
        var source = new ChatSource(
                "计算机学院培养方案.pdf",
                "official",
                1,
                0.91,
                "南京邮电大学计算机学院",
                "https://cs.njupt.edu.cn/",
                "MAJOR"
        );
        when(providerRouter.answer(anyString(), anyList()))
                .thenReturn(
                        new ChatAnswer(
                                "计算机专业注重计算基础与工程实践。",
                                List.of(source),
                                95
                        ),
                        new ChatAnswer(
                                "结合上一轮专业背景，大一应先打好数学和编程基础。",
                                List.of(source),
                                95
                        )
                );

        var conversation = conversationService.create(anonymousSessionId);
        conversationService.send(
                new ConversationMessageRequest(
                        conversation.id(),
                        "南邮计算机专业怎么样？"
                ),
                anonymousSessionId
        );
        var second = conversationService.send(
                new ConversationMessageRequest(
                        conversation.id(),
                        "大一应该怎么学习？"
                ),
                anonymousSessionId
        );

        @SuppressWarnings("unchecked")
        var histories = ArgumentCaptor.forClass(
                (Class<List<DialogueMessage>>) (Class<?>) List.class
        );
        verify(providerRouter, org.mockito.Mockito.times(2))
                .answer(anyString(), histories.capture());
        assertThat(histories.getAllValues().get(1))
                .extracting(DialogueMessage::role, DialogueMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "USER",
                                "南邮计算机专业怎么样？"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "ASSISTANT",
                                "计算机专业注重计算基础与工程实践。"
                        )
                );
        assertThat(messageMapper
                .findByConversationIdOrderByCreatedTimeAsc(conversation.id()))
                .hasSize(4);

        feedbackService.submit(new AnswerFeedbackRequest(
                second.chatId(),
                FeedbackType.HELPFUL,
                null
        ), anonymousSessionId);
        feedbackService.submit(new AnswerFeedbackRequest(
                second.chatId(),
                FeedbackType.INCORRECT,
                "缺少课程示例"
        ), anonymousSessionId);

        assertThat(feedbackMapper.count()).isEqualTo(1);
        var saved = feedbackMapper.findByChatHistoryId(second.chatId())
                .orElseThrow();
        assertThat(saved.getUserFeedback()).isEqualTo(FeedbackType.INCORRECT);
        assertThat(saved.getReason()).isEqualTo("缺少课程示例");
        var statistics = feedbackService.statistics();
        assertThat(statistics.totalAnswers()).isEqualTo(2);
        assertThat(statistics.incorrectCount()).isEqualTo(1);
    }
}
