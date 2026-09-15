package com.njupt.aiassistant.service.retrieval;

import com.njupt.aiassistant.entity.DocumentCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionClassifierTests {

    private final QuestionClassifier classifier = new QuestionClassifier();

    @Test
    void classifiesCampusQuestionsBeforeRetrieval() {
        assertThat(classifier.classify("新生报到需要准备什么？"))
                .isEqualTo(DocumentCategory.NEW_STUDENT);
        assertThat(classifier.classify("转专业有什么条件？"))
                .isEqualTo(DocumentCategory.ACADEMIC);
        assertThat(classifier.classify("图书馆开放时间？"))
                .isEqualTo(DocumentCategory.LIFE);
        assertThat(classifier.classify("南邮A类竞赛有哪些？"))
                .isEqualTo(DocumentCategory.ACADEMIC);
        assertThat(classifier.classify("讲一下晨跑"))
                .isEqualTo(DocumentCategory.LIFE);
        assertThat(classifier.classify("讲一下早锻炼"))
                .isEqualTo(DocumentCategory.LIFE);
        assertThat(classifier.classify("计算机专业如何规划？"))
                .isEqualTo(DocumentCategory.MAJOR);
        assertThat(classifier.classify("通信工程就业方向？"))
                .isEqualTo(DocumentCategory.CAREER);
        assertThat(classifier.classify("南邮校训是什么？"))
                .isEqualTo(DocumentCategory.SCHOOL_OVERVIEW);
        assertThat(classifier.classify("现任校长是谁？"))
                .isEqualTo(DocumentCategory.SCHOOL_OVERVIEW);
        assertThat(classifier.classify("学校有哪些教学机构？"))
                .isEqualTo(DocumentCategory.ORGANIZATION);
        assertThat(classifier.classify("学校有哪些科研机构？"))
                .isEqualTo(DocumentCategory.ORGANIZATION);
        assertThat(classifier.classify("有哪些重点实验室和科研平台？"))
                .isEqualTo(DocumentCategory.RESEARCH);
        assertThat(classifier.classify("今天天气怎么样？")).isNull();
    }

    @Test
    void classifiesColloquialTransferQuestionsAsAcademic() {
        assertThat(classifier.classify("数字经济转自动化条件"))
                .isEqualTo(DocumentCategory.ACADEMIC);
        assertThat(classifier.classify("从数字经济转到自动化有什么要求"))
                .isEqualTo(DocumentCategory.ACADEMIC);
        assertThat(classifier.classify("自动化学院接收转专业学生吗"))
                .isEqualTo(DocumentCategory.ACADEMIC);
        assertThat(classifier.classify("自动化转专业考什么"))
                .isEqualTo(DocumentCategory.ACADEMIC);
    }

    @Test
    void doesNotTreatUnrelatedTransferWordsAsAcademic() {
        assertThat(classifier.classify("校园卡怎么转账"))
                .isNotEqualTo(DocumentCategory.ACADEMIC);
        assertThat(classifier.classify("网页为什么自动跳转"))
                .isNotEqualTo(DocumentCategory.ACADEMIC);
    }
}
