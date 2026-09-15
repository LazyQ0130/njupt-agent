package com.njupt.aiassistant.service.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTermNormalizerTests {

    @Test
    void expandsReviewedAliasWithoutChangingOriginalQuestion() {
        var normalizer = new QueryTermNormalizer(new ObjectMapper());

        var normalized = normalizer.normalize("讲一下晨跑");

        assertThat(normalized.originalQuestion()).isEqualTo("讲一下晨跑");
        assertThat(normalized.retrievalQuestion())
                .contains("讲一下晨跑", "检索规范词：早锻炼");
        assertThat(normalized.matchedCanonicalTerms())
                .containsExactly("早锻炼");
    }

    @Test
    void expandsNjuptAbbreviationForRetrieval() {
        var normalizer = new QueryTermNormalizer(new ObjectMapper());

        var normalized = normalizer.normalize("南邮A类竞赛有哪些");

        assertThat(normalized.originalQuestion()).isEqualTo("南邮A类竞赛有哪些");
        assertThat(normalized.retrievalQuestion())
                .contains("南邮A类竞赛有哪些", "检索规范词：南京邮电大学");
        assertThat(normalized.matchedCanonicalTerms())
                .containsExactly("南京邮电大学");
    }

    @Test
    void doesNotDuplicateCanonicalTermOrChangeUnknownQuestions() {
        var normalizer = new QueryTermNormalizer(new ObjectMapper());

        var canonical = normalizer.normalize("讲一下早锻炼");
        var unknown = normalizer.normalize("讲一下跑步技巧");

        assertThat(canonical.retrievalQuestion()).isEqualTo("讲一下早锻炼");
        assertThat(unknown.retrievalQuestion()).isEqualTo("讲一下跑步技巧");
        assertThat(unknown.matchedCanonicalTerms()).isEmpty();
    }

    @Test
    void prefersLongerOverlappingAliases() {
        var normalizer = new QueryTermNormalizer(List.of(
                new QueryTermNormalizer.TermMatch("规范词A", "晨", "LIFE"),
                new QueryTermNormalizer.TermMatch("规范词B", "晨跑", "LIFE")
        ));

        var normalized = normalizer.normalize("讲一下晨跑");

        assertThat(normalized.matchedCanonicalTerms()).containsExactly("规范词B");
    }

    @Test
    void rejectsConflictingAliasOwners() {
        assertThatThrownBy(() -> new QueryTermNormalizer(List.of(
                new QueryTermNormalizer.TermMatch("规范词A", "晨跑", "LIFE"),
                new QueryTermNormalizer.TermMatch("规范词B", "晨跑", "LIFE")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("映射冲突");
    }
}
