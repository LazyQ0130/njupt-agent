package com.njupt.aiassistant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.ConversationProperties;
import com.njupt.aiassistant.config.AnonymousSessionProperties;
import com.njupt.aiassistant.dto.ConversationMessageRequest;
import com.njupt.aiassistant.entity.ChatHistoryEntity;
import com.njupt.aiassistant.entity.ConversationEntity;
import com.njupt.aiassistant.entity.MessageEntity;
import com.njupt.aiassistant.entity.MessageRole;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.ChatHistoryMapper;
import com.njupt.aiassistant.mapper.ConversationMapper;
import com.njupt.aiassistant.mapper.MessageMapper;
import com.njupt.aiassistant.service.ConversationService;
import com.njupt.aiassistant.service.AnonymousSessionService;
import com.njupt.aiassistant.service.ai.AiProviderRouter;
import com.njupt.aiassistant.service.ai.ChatSource;
import com.njupt.aiassistant.service.ai.DialogueMessage;
import com.njupt.aiassistant.vo.ChatSourceVO;
import com.njupt.aiassistant.vo.ConversationAnswerVO;
import com.njupt.aiassistant.vo.ConversationDetailVO;
import com.njupt.aiassistant.vo.ConversationMessageVO;
import com.njupt.aiassistant.vo.ConversationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final TypeReference<List<ChatSource>> SOURCE_LIST_TYPE =
            new TypeReference<>() {
            };

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final AiProviderRouter providerRouter;
    private final ConversationProperties properties;
    private final AnonymousSessionProperties sessionProperties;
    private final AnonymousSessionService anonymousSessionService;
    private final ObjectMapper objectMapper;

    public ConversationServiceImpl(
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            ChatHistoryMapper chatHistoryMapper,
            AiProviderRouter providerRouter,
            ConversationProperties properties,
            AnonymousSessionProperties sessionProperties,
            AnonymousSessionService anonymousSessionService,
            ObjectMapper objectMapper
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.providerRouter = providerRouter;
        this.properties = properties;
        this.sessionProperties = sessionProperties;
        this.anonymousSessionService = anonymousSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ConversationVO create(String anonymousSessionId) {
        var sessionId = anonymousSessionService.requireValid(
                anonymousSessionId
        );
        var conversation = new ConversationEntity();
        conversation.setUserId(null);
        conversation.setAnonymousSessionId(sessionId);
        conversation.setTitle("新对话");
        conversation.setExpireTime(nextExpireTime());
        return toVO(conversationMapper.save(conversation));
    }

    @Override
    @Transactional
    public ConversationAnswerVO send(
            ConversationMessageRequest request,
            String anonymousSessionId
    ) {
        var sessionId = anonymousSessionService.requireValid(
                anonymousSessionId
        );
        var conversation = requireConversation(
                request.conversationId(),
                sessionId
        );
        var existingMessages = messageMapper.findByConversationIdOrderByCreatedTimeAsc(
                conversation.getId()
        );
        var history = boundedHistory(existingMessages);
        var question = request.question().trim();

        var userMessage = new MessageEntity();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(question);
        messageMapper.save(userMessage);

        var result = providerRouter.answer(question, history);
        var sourcesJson = writeSources(result.sources());

        var chatHistory = new ChatHistoryEntity();
        chatHistory.setUserId(null);
        chatHistory.setAnonymousSessionId(sessionId);
        chatHistory.setConversationId(conversation.getId());
        chatHistory.setQuestion(question);
        chatHistory.setAnswer(result.answer());
        chatHistory.setSources(sourcesJson);
        chatHistory.setConfidence(result.confidence());
        chatHistoryMapper.save(chatHistory);

        var assistantMessage = new MessageEntity();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent(result.answer());
        assistantMessage.setSources(sourcesJson);
        assistantMessage.setChatHistoryId(chatHistory.getId());
        messageMapper.save(assistantMessage);

        if ("新对话".equals(conversation.getTitle())) {
            conversation.setTitle(buildTitle(question));
        }
        conversation.setUpdatedTime(LocalDateTime.now());
        conversation.setExpireTime(nextExpireTime());
        conversationMapper.save(conversation);

        return new ConversationAnswerVO(
                conversation.getId(),
                assistantMessage.getId(),
                chatHistory.getId(),
                result.answer(),
                toSourceVOs(result.sources()),
                result.confidence(),
                assistantMessage.getCreatedTime()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDetailVO get(
            Long conversationId,
            String anonymousSessionId
    ) {
        var sessionId = anonymousSessionService.requireValid(
                anonymousSessionId
        );
        var conversation = requireConversation(conversationId, sessionId);
        var messages = messageMapper.findByConversationIdOrderByCreatedTimeAsc(
                conversationId
        ).stream().map(this::toMessageVO).toList();
        return new ConversationDetailVO(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getCreatedTime(),
                conversation.getUpdatedTime(),
                messages
        );
    }

    private ConversationEntity requireConversation(
            Long id,
            String anonymousSessionId
    ) {
        var conversation = conversationMapper.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND
                ));
        if (conversation.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!anonymousSessionId.equals(
                conversation.getAnonymousSessionId()
        )) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private List<DialogueMessage> boundedHistory(List<MessageEntity> messages) {
        var selected = new ArrayList<DialogueMessage>();
        var usedCharacters = 0;
        for (int index = messages.size() - 1;
                index >= 0 && selected.size() < properties.maxHistoryMessages();
                index--) {
            var message = messages.get(index);
            var remaining = properties.maxHistoryCharacters() - usedCharacters;
            if (remaining <= 0) {
                break;
            }
            var content = message.getContent();
            if (content.length() > remaining) {
                content = content.substring(content.length() - remaining);
            }
            selected.add(new DialogueMessage(message.getRole().name(), content));
            usedCharacters += content.length();
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }

    private String buildTitle(String question) {
        return question.length() <= 30 ? question : question.substring(0, 30) + "…";
    }

    private LocalDateTime nextExpireTime() {
        return LocalDateTime.now().plus(sessionProperties.retention());
    }

    private ConversationVO toVO(ConversationEntity entity) {
        return new ConversationVO(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }

    private ConversationMessageVO toMessageVO(MessageEntity message) {
        var sources = readSources(message.getSources());
        Integer confidence = null;
        if (message.getChatHistoryId() != null) {
            confidence = chatHistoryMapper.findById(message.getChatHistoryId())
                    .map(ChatHistoryEntity::getConfidence)
                    .orElse(null);
        }
        return new ConversationMessageVO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                toSourceVOs(sources),
                message.getChatHistoryId(),
                confidence,
                message.getCreatedTime()
        );
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
        if (sources == null || sources.isBlank()) {
            return List.of();
        }
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

    private List<ChatSourceVO> toSourceVOs(List<ChatSource> sources) {
        return sources.stream().map(source -> new ChatSourceVO(
                source.title(),
                source.type(),
                source.page(),
                source.score(),
                source.source(),
                source.url(),
                source.category()
        )).toList();
    }
}
