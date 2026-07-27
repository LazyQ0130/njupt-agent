package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.common.RequestHeaders;
import com.njupt.aiassistant.dto.ChatAskRequest;
import com.njupt.aiassistant.service.ChatService;
import com.njupt.aiassistant.service.ConversationService;
import com.njupt.aiassistant.service.FeedbackService;
import com.njupt.aiassistant.dto.ConversationMessageRequest;
import com.njupt.aiassistant.dto.AnswerFeedbackRequest;
import com.njupt.aiassistant.vo.ConversationAnswerVO;
import com.njupt.aiassistant.vo.ConversationDetailVO;
import com.njupt.aiassistant.vo.ConversationVO;
import com.njupt.aiassistant.vo.AnswerFeedbackVO;
import com.njupt.aiassistant.vo.ChatAnswerVO;
import com.njupt.aiassistant.vo.ChatHistoryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final FeedbackService feedbackService;

    public ChatController(
            ChatService chatService,
            ConversationService conversationService,
            FeedbackService feedbackService
    ) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.feedbackService = feedbackService;
    }

    @PostMapping("/ask")
    public ApiResponse<ChatAnswerVO> ask(
            @Valid @RequestBody ChatAskRequest request,
            @RequestHeader(name = RequestHeaders.ANONYMOUS_SESSION_ID)
            String anonymousSessionId
    ) {
        return ApiResponse.success(
                chatService.ask(request, anonymousSessionId)
        );
    }

    @GetMapping("/history")
    public ApiResponse<List<ChatHistoryVO>> history(
            @RequestHeader(name = RequestHeaders.ANONYMOUS_SESSION_ID)
            String anonymousSessionId
    ) {
        return ApiResponse.success(chatService.history(anonymousSessionId));
    }

    @PostMapping("/conversation")
    public ApiResponse<ConversationVO> createConversation(
            @RequestHeader(name = RequestHeaders.ANONYMOUS_SESSION_ID)
            String anonymousSessionId
    ) {
        return ApiResponse.success(
                conversationService.create(anonymousSessionId)
        );
    }

    @PostMapping("/message")
    public ApiResponse<ConversationAnswerVO> sendMessage(
            @Valid @RequestBody ConversationMessageRequest request,
            @RequestHeader(name = RequestHeaders.ANONYMOUS_SESSION_ID)
            String anonymousSessionId
    ) {
        return ApiResponse.success(
                conversationService.send(request, anonymousSessionId)
        );
    }

    @GetMapping("/conversation/{id}")
    public ApiResponse<ConversationDetailVO> conversation(
            @org.springframework.web.bind.annotation.PathVariable
            @Positive(message = "会话 ID 必须为正数") Long id,
            @RequestHeader(name = RequestHeaders.ANONYMOUS_SESSION_ID)
            String anonymousSessionId
    ) {
        return ApiResponse.success(
                conversationService.get(id, anonymousSessionId)
        );
    }

    @PostMapping("/feedback")
    public ApiResponse<AnswerFeedbackVO> feedback(
            @Valid @RequestBody AnswerFeedbackRequest request,
            @RequestHeader(name = RequestHeaders.ANONYMOUS_SESSION_ID)
            String anonymousSessionId
    ) {
        return ApiResponse.success(
                feedbackService.submit(request, anonymousSessionId)
        );
    }
}
