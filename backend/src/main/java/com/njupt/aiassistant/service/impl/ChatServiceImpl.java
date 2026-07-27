package com.njupt.aiassistant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.ChatAskRequest;
import com.njupt.aiassistant.entity.ChatHistoryEntity;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.ChatHistoryMapper;
import com.njupt.aiassistant.service.ChatService;
import com.njupt.aiassistant.service.ai.AiProviderRouter;
import com.njupt.aiassistant.service.ai.ChatSource;
import com.njupt.aiassistant.vo.ChatAnswerVO;
import com.njupt.aiassistant.vo.ChatHistoryVO;
import com.njupt.aiassistant.vo.ChatSourceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private static final TypeReference<List<ChatSource>> SOURCE_LIST_TYPE = new TypeReference<>() {
    };

    private final AiProviderRouter providerRouter;
    private final ChatHistoryMapper chatHistoryMapper;
    private final com.njupt.aiassistant.service.AnonymousSessionService
            anonymousSessionService;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(
            AiProviderRouter providerRouter,
            ChatHistoryMapper chatHistoryMapper,
            com.njupt.aiassistant.service.AnonymousSessionService
                    anonymousSessionService,
            ObjectMapper objectMapper
    ) {
        this.providerRouter = providerRouter;
        this.chatHistoryMapper = chatHistoryMapper;
        this.anonymousSessionService = anonymousSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ChatAnswerVO ask(
            ChatAskRequest request,
            String anonymousSessionId
    ) {
        var sessionId = anonymousSessionService.requireValid(
                anonymousSessionId
        );
        var result = providerRouter.answer(request.question().trim());
        var entity = new ChatHistoryEntity();
        entity.setUserId(null);
        entity.setAnonymousSessionId(sessionId);
        entity.setQuestion(request.question().trim());
        entity.setAnswer(result.answer());
        entity.setSources(writeSources(result.sources()));
        entity.setConfidence(result.confidence());
        chatHistoryMapper.save(entity);

        return new ChatAnswerVO(
                result.answer(),
                toSourceVOs(result.sources()),
                result.confidence(),
                entity.getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatHistoryVO> history(String anonymousSessionId) {
        var sessionId = anonymousSessionService.requireValid(
                anonymousSessionId
        );
        var entities = chatHistoryMapper
                .findByAnonymousSessionIdOrderByCreatedTimeDesc(sessionId);
        return entities.stream().map(this::toHistoryVO).toList();
    }

    private ChatHistoryVO toHistoryVO(ChatHistoryEntity entity) {
        return new ChatHistoryVO(
                entity.getId(),
                entity.getUserId(),
                entity.getQuestion(),
                entity.getAnswer(),
                toSourceVOs(readSources(entity.getSources())),
                entity.getConfidence(),
                entity.getCreatedTime()
        );
    }

    private List<ChatSourceVO> toSourceVOs(List<ChatSource> sources) {
        return sources.stream()
                .map(source -> new ChatSourceVO(
                        source.title(),
                        source.type(),
                        source.page(),
                        source.score(),
                        source.source(),
                        source.url(),
                        source.category()
                ))
                .toList();
    }

    private String writeSources(List<ChatSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.DATA_SERIALIZATION_FAILED,
                    "聊天来源序列化失败",
                    exception
            );
        }
    }

    private List<ChatSource> readSources(String sources) {
        try {
            return objectMapper.readValue(sources, SOURCE_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.DATA_SERIALIZATION_FAILED,
                    "聊天来源反序列化失败",
                    exception
            );
        }
    }
}
