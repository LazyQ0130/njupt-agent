from app.models.schemas import DocumentCategory


class RuleBasedClassifier:
    RETROSPECTIVE_SIGNALS = (
        "成功举办",
        "顺利举办",
        "圆满举行",
        "举办",
        "举行",
        "开展",
        "召开会议",
        "领导调研",
        "走访调研",
        "主题党日",
        "党支部",
        "党总支",
        "师德大讲堂",
        "教职工",
        "荣获",
        "喜报",
        "获奖",
        "媒体报道",
    )

    STUDENT_ACTION_SIGNALS = (
        "申请",
        "报名",
        "办理",
        "选课",
        "考试",
        "补考",
        "缓考",
        "重修",
        "转专业",
        "培养方案",
        "培养计划",
        "学籍",
        "成绩复核",
        "奖学金",
        "助学金",
        "困难认定",
        "勤工助学",
        "住宿申请",
        "宿舍报修",
        "借阅规则",
        "推免",
        "招生简章",
        "实施细则",
        "管理办法",
        "办事流程",
        "材料清单",
        "截止时间",
    )
    TITLE_RULES: tuple[tuple[DocumentCategory, tuple[str, ...]], ...] = (
        (
            DocumentCategory.ORGANIZATION,
            (
                "党政群部门",
                "教学机构",
                "科研机构",
                "直属单位",
                "基层党的组织",
                "基层党组织",
                "独立学院",
                "组织机构",
                "内设机构",
            ),
        ),
        (
            DocumentCategory.SCHOOL_OVERVIEW,
            (
                "学校简介",
                "学校章程",
                "南邮精神",
                "校标校训",
                "校标",
                "校训",
                "南邮校史",
                "学校历史",
                "校史",
                "校歌",
                "现任领导",
                "校区地图",
                "校园景观",
                "校园景色",
                "校园风光",
            ),
        ),
        (
            DocumentCategory.RESEARCH,
            (
                "学科建设",
                "科研平台",
                "重点实验室",
                "研究中心",
                "学术刊物",
                "科研成果",
                "学术讲座",
                "学术报告",
                "科学研究",
            ),
        ),
    )

    RULES: tuple[tuple[DocumentCategory, tuple[str, ...]], ...] = (
        (
            DocumentCategory.ORGANIZATION,
            (
                "党政群部门",
                "教学机构",
                "科研机构",
                "直属单位",
                "基层党的组织",
                "基层党组织",
                "独立学院",
            ),
        ),
        (
            DocumentCategory.SCHOOL_OVERVIEW,
            (
                "学校简介",
                "学校章程",
                "南邮精神",
                "校标校训",
                "校史沿革",
                "现任领导",
                "校区地图",
                "校园景观",
            ),
        ),
        (
            DocumentCategory.RESEARCH,
            (
                "学科建设",
                "科研平台",
                "重点实验室",
                "研究中心",
                "学术刊物",
                "科研成果",
                "学术讲座",
                "学术报告",
            ),
        ),
        (
            DocumentCategory.NEW_STUDENT,
            (
                "新生",
                "入学",
                "报到",
                "迎新",
                "军训",
                "校园卡",
                "新生手册",
            ),
        ),
        (
            DocumentCategory.ACADEMIC,
            (
                "选课",
                "考试",
                "补考",
                "缓考",
                "重修",
                "转专业",
                "培养方案",
                "培养计划",
                "学分",
                "校历",
                "学籍",
                "成绩",
                "课程",
                "教务",
                "竞赛",
            ),
        ),
        (
            DocumentCategory.LIFE,
            (
                "图书馆",
                "食堂",
                "宿舍",
                "快递",
                "校园服务",
                "后勤",
                "医疗",
                "保卫",
            ),
        ),
        (
            DocumentCategory.MAJOR,
            (
                "专业介绍",
                "学院介绍",
                "培养方向",
                "专业方向",
                "本科专业",
                "专业设置",
            ),
        ),
        (
            DocumentCategory.CAREER,
            (
                "就业",
                "招聘",
                "考研",
                "升学",
                "推免",
                "职业发展",
            ),
        ),
    )

    def classify(
        self,
        title: str,
        content: str,
    ) -> DocumentCategory | None:
        static_category = self.classify_static_title(title)
        if static_category is not None:
            return static_category

        corpus = f"{title}\n{content[:1200]}"
        has_student_action = any(
            signal in corpus for signal in self.STUDENT_ACTION_SIGNALS
        )
        if (
            any(signal in title for signal in self.RETROSPECTIVE_SIGNALS)
            and not has_student_action
        ):
            return None

        for category, keywords in self.RULES:
            if any(keyword in title for keyword in keywords):
                return category

        if not has_student_action:
            return None

        searchable = content[:1200]
        for category, keywords in self.RULES:
            if any(keyword in searchable for keyword in keywords):
                return category
        return None

    def classify_static_title(
        self,
        title: str,
    ) -> DocumentCategory | None:
        for category, keywords in self.TITLE_RULES:
            if any(keyword in title for keyword in keywords):
                return category
        return None

    def priority(self, text: str) -> int:
        score = sum(
            1
            for _, keywords in self.TITLE_RULES + self.RULES
            for keyword in keywords
            if keyword in text
        )
        return -score
