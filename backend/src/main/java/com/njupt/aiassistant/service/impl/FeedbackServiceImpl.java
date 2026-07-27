package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.AnswerFeedbackRequest;
import com.njupt.aiassistant.entity.AnswerFeedbackEntity;
import com.njupt.aiassistant.entity.FeedbackType;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.AnswerFeedbackMapper;
import com.njupt.aiassistant.mapper.ChatHistoryMapper;
import com.njupt.aiassistant.service.FeedbackService;
import com.njupt.aiassistant.service.AnonymousSessionService;
import com.njupt.aiassistant.vo.AnswerFeedbackVO;
import com.njupt.aiassistant.vo.FrequentQuestionVO;
import com.njupt.aiassistant.vo.QualityStatsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final AnswerFeedbackMapper feedbackMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final AnonymousSessionService anonymousSessionService;

    public FeedbackServiceImpl(
            AnswerFeedbackMapper feedbackMapper,
            ChatHistoryMapper chatHistoryMapper,
            AnonymousSessionService anonymousSessionService
    ) {
        this.feedbackMapper = feedbackMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.anonymousSessionService = anonymousSessionService;
    }

    @Override
    @Transactional
    public AnswerFeedbackVO submit(
            AnswerFeedbackRequest request,
            String anonymousSessionId
    ) {
        var sessionId = anonymousSessionService.requireValid(
                anonymousSessionId
        );
        var chatHistory = chatHistoryMapper.findById(request.chatId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHAT_HISTORY_NOT_FOUND
                ));
        if (!sessionId.equals(chatHistory.getAnonymousSessionId())) {
            throw new BusinessException(ErrorCode.CHAT_HISTORY_NOT_FOUND);
        }
        var entity = feedbackMapper.findByChatHistoryId(request.chatId())
                .orElseGet(AnswerFeedbackEntity::new);
        entity.setChatHistoryId(request.chatId());
        entity.setUserFeedback(request.feedback());
        entity.setReason(normalizeReason(request.reason()));
        return toVO(feedbackMapper.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public QualityStatsVO statistics() {
        var totalAnswers = chatHistoryMapper.count();
        var helpful = feedbackMapper.countByUserFeedback(FeedbackType.HELPFUL);
        var incorrect = feedbackMapper.countByUserFeedback(FeedbackType.INCORRECT);
        var feedbackCount = helpful + incorrect;
        var rate = feedbackCount == 0
                ? 0.0
                : Math.round(helpful * 1000.0 / feedbackCount) / 10.0;
        var frequent = chatHistoryMapper.findAll().stream()
                .map(item -> item.getQuestion().trim())
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted((left, right) -> {
                    var countOrder = Long.compare(right.getValue(), left.getValue());
                    return countOrder != 0
                            ? countOrder
                            : left.getKey().compareTo(right.getKey());
                })
                .limit(10)
                .map(entry -> new FrequentQuestionVO(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
        return new QualityStatsVO(
                totalAnswers,
                feedbackCount,
                helpful,
                incorrect,
                rate,
                frequent
        );
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private AnswerFeedbackVO toVO(AnswerFeedbackEntity entity) {
        return new AnswerFeedbackVO(
                entity.getId(),
                entity.getChatHistoryId(),
                entity.getUserFeedback(),
                entity.getReason(),
                entity.getCreatedTime()
        );
    }
}
