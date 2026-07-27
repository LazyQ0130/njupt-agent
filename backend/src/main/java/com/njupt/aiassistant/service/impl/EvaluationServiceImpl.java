package com.njupt.aiassistant.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.EvaluationReviewRequest;
import com.njupt.aiassistant.entity.EvaluationResultEntity;
import com.njupt.aiassistant.evaluation.EvaluationQuestion;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.EvaluationResultMapper;
import com.njupt.aiassistant.service.EvaluationService;
import com.njupt.aiassistant.service.ai.AiProviderRouter;
import com.njupt.aiassistant.service.ai.ChatAnswer;
import com.njupt.aiassistant.vo.EvaluationItemVO;
import com.njupt.aiassistant.vo.EvaluationReportVO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    private static final String NO_KNOWLEDGE =
            "知识库中暂无相关信息，请咨询相关部门。";

    private final AiProviderRouter providerRouter;
    private final EvaluationResultMapper resultMapper;
    private final ObjectMapper objectMapper;

    public EvaluationServiceImpl(
            AiProviderRouter providerRouter,
            EvaluationResultMapper resultMapper,
            ObjectMapper objectMapper
    ) {
        this.providerRouter = providerRouter;
        this.resultMapper = resultMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EvaluationReportVO run() {
        var runId = UUID.randomUUID().toString();
        var items = new ArrayList<EvaluationItemVO>();
        for (var question : loadQuestions()) {
            ChatAnswer answer;
            try {
                answer = providerRouter.answer(question.question());
            } catch (RuntimeException exception) {
                answer = new ChatAnswer("", List.of(), 0);
            }
            var resolvedAnswer = answer;
            var hasSource = !resolvedAnswer.sources().isEmpty();
            var sourceMatch = hasSource && resolvedAnswer.sources().stream()
                    .anyMatch(source -> containsIgnoreCase(
                            source.title() + " " + source.source(),
                            question.expectedSource()
                    ));
            var nonEmpty = resolvedAnswer.answer() != null
                    && !resolvedAnswer.answer().isBlank()
                    && !NO_KNOWLEDGE.equals(resolvedAnswer.answer().trim());
            var keywordMatch = nonEmpty
                    && question.expectedKeywords() != null
                    && question.expectedKeywords().stream()
                    .anyMatch(keyword -> containsIgnoreCase(
                            resolvedAnswer.answer(),
                            keyword
                    ));
            var score = (hasSource ? 35 : 0)
                    + (sourceMatch ? 30 : 0)
                    + (keywordMatch ? 25 : 0)
                    + (nonEmpty ? 10 : 0);

            var entity = new EvaluationResultEntity();
            entity.setRunId(runId);
            entity.setCategory(question.category());
            entity.setQuestion(question.question());
            entity.setAnswer(
                    resolvedAnswer.answer() == null
                            ? ""
                            : resolvedAnswer.answer()
            );
            entity.setExpectedSource(
                    question.expectedSource() == null ? "" : question.expectedSource()
            );
            entity.setHasSource(hasSource);
            entity.setSourceMatch(sourceMatch);
            entity.setKeywordMatch(keywordMatch);
            entity.setNonEmpty(nonEmpty);
            entity.setScore(score);
            resultMapper.save(entity);
            items.add(toItem(entity));
        }
        return buildReport(runId, items);
    }

    @Override
    @Transactional(readOnly = true)
    public EvaluationReportVO getReport(String runId) {
        var items = resultMapper.findByRunIdOrderByIdAsc(runId).stream()
                .map(this::toItem)
                .toList();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评测批次不存在");
        }
        return buildReport(runId, items);
    }

    @Override
    @Transactional
    public EvaluationReportVO review(
            Long resultId,
            EvaluationReviewRequest request
    ) {
        var entity = resultMapper.findById(resultId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "评测结果不存在"
                ));
        entity.setHumanAccurate(request.accurate());
        entity.setReviewNote(normalizeNote(request.note()));
        resultMapper.save(entity);
        return getReport(entity.getRunId());
    }

    private List<EvaluationQuestion> loadQuestions() {
        try (var input = new ClassPathResource(
                "evaluation/questions.json"
        ).getInputStream()) {
            return objectMapper.readValue(
                    input,
                    new TypeReference<List<EvaluationQuestion>>() {
                    }
            );
        } catch (IOException exception) {
            throw new IllegalStateException("评测问题集读取失败", exception);
        }
    }

    private EvaluationItemVO toItem(EvaluationResultEntity entity) {
        return new EvaluationItemVO(
                entity.getId(),
                entity.getQuestion(),
                entity.getCategory(),
                entity.getAnswer(),
                Boolean.TRUE.equals(entity.getHasSource()),
                Boolean.TRUE.equals(entity.getSourceMatch()),
                Boolean.TRUE.equals(entity.getKeywordMatch()),
                Boolean.TRUE.equals(entity.getNonEmpty()),
                entity.getScore(),
                entity.getHumanAccurate(),
                entity.getReviewNote()
        );
    }

    private EvaluationReportVO buildReport(
            String runId,
            List<EvaluationItemVO> items
    ) {
        var total = items.size();
        var averageScore = percent(items.stream()
                .mapToInt(EvaluationItemVO::score)
                .average().orElse(0.0));
        var sourceCoverage = ratio(items.stream()
                .filter(EvaluationItemVO::hasSource).count(), total);
        var sourceMatchRate = ratio(items.stream()
                .filter(EvaluationItemVO::sourceMatch).count(), total);
        var keywordMatchRate = ratio(items.stream()
                .filter(EvaluationItemVO::keywordMatch).count(), total);
        var nonEmptyRate = ratio(items.stream()
                .filter(EvaluationItemVO::nonEmpty).count(), total);
        var humanReviewedCount = (int) items.stream()
                .filter(item -> item.humanAccurate() != null)
                .count();
        Double humanAccuracyRate = humanReviewedCount == 0
                ? null
                : ratio(items.stream()
                        .filter(item -> Boolean.TRUE.equals(item.humanAccurate()))
                        .count(), humanReviewedCount);
        var categoryScores = items.stream().collect(Collectors.groupingBy(
                EvaluationItemVO::category,
                LinkedHashMap::new,
                Collectors.averagingInt(EvaluationItemVO::score)
        ));
        categoryScores.replaceAll((category, score) -> percent(score));
        return new EvaluationReportVO(
                runId,
                total,
                averageScore,
                sourceCoverage,
                sourceMatchRate,
                keywordMatchRate,
                nonEmptyRate,
                humanReviewedCount,
                humanAccuracyRate,
                categoryScores,
                LocalDateTime.now(),
                List.copyOf(items)
        );
    }

    private double ratio(long value, long total) {
        return total == 0 ? 0.0 : percent(value * 100.0 / total);
    }

    private double percent(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return expected == null
                || expected.isBlank()
                || value.toLowerCase(Locale.ROOT)
                .contains(expected.toLowerCase(Locale.ROOT));
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) return null;
        var normalized = note.trim();
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }
}
