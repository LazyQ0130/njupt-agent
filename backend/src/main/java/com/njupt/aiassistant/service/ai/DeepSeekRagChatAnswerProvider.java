package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.config.RagAnswerProperties;
import com.njupt.aiassistant.service.llm.LLMService;
import com.njupt.aiassistant.service.retrieval.QuestionClassifier;
import com.njupt.aiassistant.service.retrieval.QueryTermNormalizer;
import com.njupt.aiassistant.service.retrieval.Retriever;
import com.njupt.aiassistant.service.retrieval.TransferQueryIntent;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

@Component
public class DeepSeekRagChatAnswerProvider implements ChatAnswerProvider {

    static final String NO_KNOWLEDGE_ANSWER =
            "知识库中暂无相关信息，请咨询相关部门。";

    private final Retriever retriever;
    private final LLMService llmService;
    private final RagAnswerProperties properties;
    private final QuestionClassifier questionClassifier;
    private final QueryTermNormalizer queryTermNormalizer;

    public DeepSeekRagChatAnswerProvider(
            Retriever retriever,
            LLMService llmService,
            RagAnswerProperties properties,
            QuestionClassifier questionClassifier,
            QueryTermNormalizer queryTermNormalizer
    ) {
        this.retriever = retriever;
        this.llmService = llmService;
        this.properties = properties;
        this.questionClassifier = questionClassifier;
        this.queryTermNormalizer = queryTermNormalizer;
    }

    @Override
    public String providerName() {
        return "deepseek";
    }

    @Override
    public ChatAnswer answer(String question) {
        return answer(question, List.of());
    }

    @Override
    public ChatAnswer answer(
            String question,
            List<DialogueMessage> history
    ) {
        var retrievalQuestion = contextualize(question, history);
        var normalizedQuery = queryTermNormalizer.normalize(retrievalQuestion);
        var normalizedRetrievalQuestion = normalizedQuery.retrievalQuestion();
        var transferIntent = TransferQueryIntent.analyze(normalizedRetrievalQuestion);
        var plannedRetrievalQuestion = transferIntent
                .map(intent -> intent.retrievalQuestion(normalizedRetrievalQuestion))
                .orElse(normalizedRetrievalQuestion);
        var category = questionClassifier.classify(plannedRetrievalQuestion);
        var retrieved = retriever.retrieve(
                plannedRetrievalQuestion,
                properties.candidateK(),
                category
        );
        var relevant = retrieved.stream()
                .filter(item -> item.score() >= properties.minimumRelevanceScore())
                .limit(properties.topK())
                .toList();
        if (relevant.isEmpty()) {
            return new ChatAnswer(NO_KNOWLEDGE_ANSWER, List.of(), 0);
        }

        var answer = llmService.generateAnswer(question, relevant, history);
        var highestScore = relevant.stream()
                .mapToDouble(RagSearchResultVO::score)
                .max()
                .orElse(0.0);
        return new ChatAnswer(
                answer,
                buildSources(relevant),
                calculateConfidence(highestScore)
        );
    }

    private String contextualize(
            String question,
            List<DialogueMessage> history
    ) {
        if (history.isEmpty()) {
            return question;
        }
        var lastUserContext = "";
        for (int index = history.size() - 1; index >= 0; index--) {
            var message = history.get(index);
            if ("USER".equalsIgnoreCase(message.role())) {
                lastUserContext = message.content();
                break;
            }
        }
        if (lastUserContext.isBlank()) {
            return question;
        }
        return lastUserContext + "\n后续问题：" + question;
    }

    private List<ChatSource> buildSources(List<RagSearchResultVO> retrieved) {
        var uniqueSources = new LinkedHashMap<String, ChatSource>();
        for (var item : retrieved) {
            var key = item.filename() + ":" + item.page();
            uniqueSources.putIfAbsent(
                    key,
                    new ChatSource(
                            item.filename(),
                            item.sourceType() == null || item.sourceType().isBlank()
                                    ? "official"
                                    : item.sourceType(),
                            item.page(),
                            item.score(),
                            item.source(),
                            item.sourceUrl(),
                            item.category()
                    )
            );
        }
        return List.copyOf(uniqueSources.values());
    }

    static int calculateConfidence(double highestScore) {
        if (highestScore >= 0.85) {
            return 95;
        }
        if (highestScore >= 0.70) {
            return 85;
        }
        return Math.min(59, Math.max(0, (int) Math.round(highestScore * 100)));
    }
}
