package com.njupt.aiassistant.service;

import com.njupt.aiassistant.dto.ChatAskRequest;
import com.njupt.aiassistant.vo.ChatAnswerVO;
import com.njupt.aiassistant.vo.ChatHistoryVO;

import java.util.List;

public interface ChatService {

    ChatAnswerVO ask(ChatAskRequest request, String anonymousSessionId);

    List<ChatHistoryVO> history(String anonymousSessionId);
}
