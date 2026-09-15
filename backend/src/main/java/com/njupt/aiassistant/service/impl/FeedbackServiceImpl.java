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
import com.njupt.aiassistant.vo.LowQualityQuestionVO;
import com.njupt.aiassistant.vo.QualityStatsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    static final int LOW_CONFIDENCE_THRESHOLD = 70;

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
        var histories = chatHistoryMapper.findAll();
        var feedbackByHistoryId = feedbackMapper.findAll().stream()
                .collect(Collectors.toMap(
                        item -> item.getChatHistoryId(),
                        item -> item.getUserFeedback(),
                        (left, right) -> right
                ));
        var helpful = feedbackByHistoryId.values().stream()
                .filter(FeedbackType.HELPFUL::equals)
                .count();
        var incorrect = feedbackByHistoryId.values().stream()
                .filter(FeedbackType.INCORRECT::equals)
                .count();
        var totalAnswers = histories.size();
        var feedbackCount = helpful + incorrect;
        var rate = feedbackCount == 0
                ? 0.0
                : Math.round(helpful * 1000.0 / feedbackCount) / 10.0;
        var uniqueAnonymousUsers = histories.stream()
                .map(item -> item.getAnonymousSessionId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();
        var lowConfidenceAnswerCount = histories.stream()
                .filter(item -> isLowConfidence(item.getConfidence()))
                .count();
        var lowConfidenceIncorrectCount = histories.stream()
                .filter(item -> isLowConfidence(item.getConfidence()))
                .filter(item -> FeedbackType.INCORRECT.equals(
                        feedbackByHistoryId.get(item.getId())
                ))
                .count();
        var frequent = histories.stream()
                .map(item -> item.getQuestion().trim())
                .collect(Collectors.groupingBy(item -> item, Collectors.counting()))
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
        var lowQuality = histories.stream()
                .filter(item -> isLowConfidence(item.getConfidence())
                        || FeedbackType.INCORRECT.equals(
                        feedbackByHistoryId.get(item.getId())))
                .collect(Collectors.groupingBy(item -> item.getQuestion().trim()))
                .entrySet().stream()
                .map(entry -> {
                    var samples = entry.getValue();
                    var averageConfidence = samples.stream()
                            .mapToInt(item -> item.getConfidence() == null
                                    ? 0
                                    : item.getConfidence())
                            .average()
                            .orElse(0.0);
                    var incorrectCount = samples.stream()
                            .filter(item -> FeedbackType.INCORRECT.equals(
                                    feedbackByHistoryId.get(item.getId())))
                            .count();
                    return new LowQualityQuestionVO(
                            entry.getKey(),
                            samples.size(),
                            incorrectCount,
                            Math.round(averageConfidence * 10.0) / 10.0
                    );
                })
                .sorted(Comparator
                        .comparingLong(LowQualityQuestionVO::incorrectCount)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingLong(
                                        LowQualityQuestionVO::occurrences
                                ).reversed()
                        )
                        .thenComparing(LowQualityQuestionVO::question))
                .limit(10)
                .toList();
        return new QualityStatsVO(
                totalAnswers,
                uniqueAnonymousUsers,
                feedbackCount,
                helpful,
                incorrect,
                rate,
                lowConfidenceAnswerCount,
                lowConfidenceIncorrectCount,
                frequent,
                lowQuality
        );
    }

    private boolean isLowConfidence(Integer confidence) {
        return confidence != null && confidence < LOW_CONFIDENCE_THRESHOLD;
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
