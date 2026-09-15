package com.njupt.aiassistant.service.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njupt.aiassistant.entity.DocumentCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds reviewed canonical campus terms to retrieval queries without changing
 * the question that is sent to the answer model.
 */
@Component
public class QueryTermNormalizer {

    private static final String RESOURCE_PATH = "knowledge/term-aliases.json";

    private final List<TermMatch> terms;

    @Autowired
    public QueryTermNormalizer(ObjectMapper objectMapper) {
        this(loadTerms(objectMapper));
    }

    QueryTermNormalizer(List<TermMatch> terms) {
        this.terms = validateAndSort(terms);
    }

    public NormalizedQuery normalize(String question) {
        if (question == null || question.isBlank() || terms.isEmpty()) {
            return new NormalizedQuery(question, question, List.of());
        }

        Set<String> matchedCanonicalTerms = new LinkedHashSet<>();
        Set<String> matchedAliases = new LinkedHashSet<>();
        for (var term : terms) {
            if (question.contains(term.alias())
                    && matchedAliases.stream().noneMatch(
                            alias -> alias.contains(term.alias())
                    )) {
                matchedAliases.add(term.alias());
                if (!term.alias().equals(term.canonical())
                        && !question.contains(term.canonical())) {
                    matchedCanonicalTerms.add(term.canonical());
                }
            }
        }

        if (matchedCanonicalTerms.isEmpty()) {
            return new NormalizedQuery(question, question, List.of());
        }

        var retrievalQuestion = question
                + "\n检索规范词："
                + String.join("、", matchedCanonicalTerms);
        return new NormalizedQuery(
                question,
                retrievalQuestion,
                List.copyOf(matchedCanonicalTerms)
        );
    }

    private static List<TermMatch> loadTerms(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            var config = objectMapper.readValue(input, TermAliasConfig.class);
            var terms = new ArrayList<TermMatch>();
            for (var term : config.terms() == null ? List.<TermAlias>of() : config.terms()) {
                var aliases = new ArrayList<String>();
                aliases.add(term.canonical());
                if (term.aliases() != null) {
                    aliases.addAll(term.aliases());
                }
                for (var alias : aliases) {
                    terms.add(new TermMatch(
                            term.canonical(),
                            alias,
                            term.category()
                    ));
                }
            }
            return terms;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "校园术语别名配置加载失败: " + RESOURCE_PATH,
                    exception
            );
        }
    }

    private static List<TermMatch> validateAndSort(List<TermMatch> terms) {
        if (terms == null) {
            throw new IllegalStateException("校园术语别名配置不能为 null");
        }

        Map<String, String> aliasOwners = new HashMap<>();
        for (var term : terms) {
            if (term.canonical() == null || term.canonical().isBlank()) {
                throw new IllegalStateException("校园术语规范词不能为空");
            }
            if (term.alias() == null || term.alias().isBlank()) {
                throw new IllegalStateException(
                        "校园术语别名不能为空: " + term.canonical()
                );
            }
            if (term.category() != null && !term.category().isBlank()) {
                try {
                    DocumentCategory.valueOf(term.category());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException(
                            "校园术语分类无效: " + term.category(),
                            exception
                    );
                }
            }

            var previousCanonical = aliasOwners.putIfAbsent(
                    term.alias(),
                    term.canonical()
            );
            if (previousCanonical != null
                    && !previousCanonical.equals(term.canonical())) {
                throw new IllegalStateException(
                        "校园术语别名映射冲突: " + term.alias()
                );
            }
        }

        return terms.stream()
                .sorted(Comparator.comparingInt(
                        (TermMatch term) -> term.alias().length()
                ).reversed())
                .toList();
    }

    public record NormalizedQuery(
            String originalQuestion,
            String retrievalQuestion,
            List<String> matchedCanonicalTerms
    ) {
    }

    private record TermAlias(
            String canonical,
            List<String> aliases,
            String category
    ) {
    }

    private record TermAliasConfig(List<TermAlias> terms) {
    }

    record TermMatch(String canonical, String alias, String category) {
    }
}
