package com.njupt.aiassistant.service.ai;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockChatAnswerProvider implements ChatAnswerProvider {

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public ChatAnswer answer(String question) {
        if (question.contains("转专业")) {
            return new ChatAnswer(
                    "根据南京邮电大学本科生转专业管理相关规定，学生应关注教务处当年度通知，"
                            + "确认申请条件、接收计划、考核方式和材料提交时间。"
                            + "不同学院的接收要求可能存在差异，最终以教务处和目标学院发布的正式文件为准。",
                    List.of(new ChatSource(
                            "《本科生转专业管理办法》",
                            "official",
                            1,
                            0.98,
                            "教务处",
                            null,
                            "ACADEMIC"
                    )),
                    98
            );
        }

        return new ChatAnswer(
                "当前为 Phase 3 模拟回答。系统已经完成模型无关的接口层，"
                        + "后续可通过 ChatAnswerProvider 接入 DeepSeek、OpenAI 或校园自建模型。",
                List.of(new ChatSource(
                        "南邮智答模拟知识源",
                        "official",
                        1,
                        0.92,
                        "南邮智答",
                        null,
                        null
                )),
                92
        );
    }
}
