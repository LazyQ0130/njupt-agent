package com.njupt.aiassistant.service.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferQueryIntentTests {

    @Test
    void extractsDirectionFromCompactTransferQuestion() {
        var intent = TransferQueryIntent
                .analyze("数字经济转自动化条件")
                .orElseThrow();

        assertThat(intent.sourceMajor()).isEqualTo("数字经济");
        assertThat(intent.targetMajor()).isEqualTo("自动化");
        assertThat(intent.retrievalQuestion("数字经济转自动化条件"))
                .contains(
                        "转专业目标：自动化",
                        "来源专业：数字经济",
                        "接收条件",
                        "笔试",
                        "面试"
                );
    }

    @Test
    void keepsReverseDirectionDistinct() {
        var intent = TransferQueryIntent
                .analyze("自动化转数字经济条件")
                .orElseThrow();

        assertThat(intent.sourceMajor()).isEqualTo("自动化");
        assertThat(intent.targetMajor()).isEqualTo("数字经济");
    }

    @Test
    void supportsTargetOnlyTransferPhrasing() {
        var explicitDirection = TransferQueryIntent
                .analyze("从数字经济转到自动化")
                .orElseThrow();
        assertThat(explicitDirection.sourceMajor()).isEqualTo("数字经济");
        assertThat(explicitDirection.targetMajor()).isEqualTo("自动化");
        assertThat(TransferQueryIntent.analyze("转入自动化有什么要求"))
                .get()
                .extracting(TransferQueryIntent::targetMajor)
                .isEqualTo("自动化");
        assertThat(TransferQueryIntent.analyze("自动化学院接收转专业学生吗"))
                .get()
                .extracting(TransferQueryIntent::targetMajor)
                .isEqualTo("自动化");
        assertThat(TransferQueryIntent.analyze("自动化转专业考什么"))
                .get()
                .extracting(TransferQueryIntent::targetMajor)
                .isEqualTo("自动化");
        assertThat(TransferQueryIntent.analyze("自动化的名额、笔试、面试和考核方式"))
                .get()
                .extracting(TransferQueryIntent::targetMajor)
                .isEqualTo("自动化");
    }

    @Test
    void ignoresGenericAndUnrelatedUsesOfTransfer() {
        assertThat(TransferQueryIntent.analyze("南邮转专业需要什么条件"))
                .isEmpty();
        assertThat(TransferQueryIntent.analyze("校园卡怎么转账")).isEmpty();
        assertThat(TransferQueryIntent.analyze("网页为什么自动跳转")).isEmpty();
    }
}
