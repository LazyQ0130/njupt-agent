package com.njupt.aiassistant.service.retrieval;

import com.njupt.aiassistant.entity.DocumentCategory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuestionClassifier {

    private static final Map<DocumentCategory, List<String>> RULES =
            new LinkedHashMap<>();

    static {
        RULES.put(DocumentCategory.SCHOOL_OVERVIEW, List.of(
                "学校简介", "南邮简介", "学校章程", "南邮精神",
                "校训", "校标", "校徽", "校史", "校歌",
                "现任领导", "校长是谁", "书记是谁", "校区地图",
                "几个校区", "校区地址", "校园景观"
        ));
        RULES.put(DocumentCategory.ORGANIZATION, List.of(
                "组织机构", "内设机构", "党政群部门", "教学机构",
                "科研机构", "直属单位", "基层党组织", "独立学院",
                "学院设置", "有哪些学院", "哪些部门", "部门职责"
        ));
        RULES.put(DocumentCategory.RESEARCH, List.of(
                "科学研究", "学科建设", "科研平台", "重点实验室",
                "研究中心", "学术刊物", "科研成果", "学术讲座",
                "学术报告", "科研"
        ));
        RULES.put(DocumentCategory.NEW_STUDENT, List.of(
                "新生", "入学", "报到", "迎新", "军训", "校园卡"
        ));
        RULES.put(DocumentCategory.ACADEMIC, List.of(
                "选课", "考试", "补考", "重修", "转专业", "学分",
                "绩点", "校历", "教务", "课程", "成绩", "竞赛", "创新创业"
        ));
        RULES.put(DocumentCategory.LIFE, List.of(
                "图书馆", "食堂", "宿舍", "快递", "校园网",
                "后勤", "医疗", "保卫", "生活", "早锻炼", "晨跑"
        ));
        RULES.put(DocumentCategory.CAREER, List.of(
                "就业", "招聘", "考研", "升学", "推免", "职业"
        ));
        RULES.put(DocumentCategory.MAJOR, List.of(
                "专业", "培养方案", "培养方向", "通信工程",
                "计算机", "软件工程", "课程规划"
        ));
    }

    public DocumentCategory classify(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        if (TransferQueryIntent.analyze(question).isPresent()) {
            return DocumentCategory.ACADEMIC;
        }
        for (var entry : RULES.entrySet()) {
            if (entry.getValue().stream().anyMatch(question::contains)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
