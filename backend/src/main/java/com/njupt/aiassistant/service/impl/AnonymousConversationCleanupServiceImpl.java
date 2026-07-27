package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.config.AnonymousSessionProperties;
import com.njupt.aiassistant.mapper.ChatHistoryMapper;
import com.njupt.aiassistant.mapper.ConversationMapper;
import com.njupt.aiassistant.mapper.MessageMapper;
import com.njupt.aiassistant.service.AnonymousConversationCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AnonymousConversationCleanupServiceImpl
        implements AnonymousConversationCleanupService {

    private final ConversationMapper conversationMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final MessageMapper messageMapper;
    private final AnonymousSessionProperties properties;

    public AnonymousConversationCleanupServiceImpl(
            ConversationMapper conversationMapper,
            ChatHistoryMapper chatHistoryMapper,
            MessageMapper messageMapper,
            AnonymousSessionProperties properties
    ) {
        this.conversationMapper = conversationMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.messageMapper = messageMapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    @Scheduled(cron = "${anonymous-session.cleanup-cron:0 30 3 * * *}")
    public int cleanupExpired() {
        var now = LocalDateTime.now();
        var ids = conversationMapper.findExpiredIds(now);
        if (!ids.isEmpty()) {
            messageMapper.deleteByConversationIdIn(ids);
            chatHistoryMapper.deleteByConversationIdIn(ids);
            conversationMapper.deleteByIdIn(ids);
        }
        chatHistoryMapper.deleteByConversationIdIsNullAndCreatedTimeBefore(
                now.minus(properties.retention())
        );
        return ids.size();
    }
}
