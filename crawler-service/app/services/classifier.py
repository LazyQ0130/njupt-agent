from dataclasses import dataclass
from enum import StrEnum
from urllib.parse import urlsplit

from app.models.schemas import DocumentCategory


class PageType(StrEnum):
    EVERGREEN_STATIC = "EVERGREEN_STATIC"
    CONTENT_DETAIL = "CONTENT_DETAIL"
    RETROSPECTIVE = "RETROSPECTIVE"
    UNKNOWN = "UNKNOWN"


@dataclass(frozen=True)
class ClassificationResult:
    category: DocumentCategory | None
    page_type: PageType
    confidence: float
    matched_signals: tuple[str, ...] = ()

    @property
    def is_trusted_evergreen(self) -> bool:
        return (
            self.page_type == PageType.EVERGREEN_STATIC
            and self.confidence >= 0.9
            and self.category is not None
        )


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

    EVERGREEN_TITLE_RULES: tuple[
        tuple[DocumentCategory, tuple[str, ...]], ...
    ] = (
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
                "部门简介",
                "单位简介",
                "部门职责",
                "单位职责",
                "工作职责",
                "机构设置",
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
                "实验室简介",
                "科研中心简介",
                "科研成果",
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
                "部门职责",
                "单位职责",
                "工作职责",
                "机构设置",
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
                "培养管理",
                "学分",
                "校历",
                "学籍",
                "成绩",
                "课程",
                "教务",
                "竞赛",
                "办事指南",
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
                "校园网",
                "后勤",
                "医疗",
                "保卫",
                "借阅",
                "开放时间",
            ),
        ),
        (
            DocumentCategory.MAJOR,
            (
                "专业介绍",
                "学院介绍",
                "学院简介",
                "学院概况",
                "培养方向",
                "培养层次",
                "专业方向",
                "本科专业",
                "专业设置",
                "课程体系",
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

    COLLEGE_PROFILE_TITLES = (
        "学院简介",
        "学院概况",
        "学院介绍",
        "院情简介",
        "院情概况",
    )
    COLLEGE_PROFILE_SIGNALS = (
        "本科专业",
        "专业设置",
        "人才培养",
        "培养层次",
        "本科生",
        "硕士点",
        "博士点",
    )
    LIBRARY_PROFILE_TITLES = (
        "本馆简介",
        "馆情介绍",
        "馆情简介",
        "图书馆概况",
        "图书馆简介",
        "借阅与开放服务",
        "图书馆开放时间",
    )
    RESEARCH_PROFILE_TITLES = (
        "实验室简介",
        "研究中心简介",
        "科研中心简介",
        "科研平台简介",
        "学科简介",
    )
    SERVICE_GUIDE_TITLES = (
        "办事指南",
        "服务指南",
        "借阅规则",
        "开放时间",
    )

    def classify(
        self,
        title: str,
        content: str,
        url: str = "",
    ) -> DocumentCategory | None:
        return self.classify_page(title, content, url).category

    def classify_page(
        self,
        title: str,
        content: str,
        url: str = "",
    ) -> ClassificationResult:
        searchable = content[:1200]
        corpus = f"{title}\n{searchable}"
        host = (urlsplit(url).hostname or "").lower()

        evergreen = self._classify_evergreen(title, corpus, host)
        if evergreen is not None:
            return evergreen

        has_student_action = any(
            signal in corpus for signal in self.STUDENT_ACTION_SIGNALS
        )
        retrospective = self._matches(title, self.RETROSPECTIVE_SIGNALS)
        if retrospective and not has_student_action:
            return ClassificationResult(
                category=None,
                page_type=PageType.RETROSPECTIVE,
                confidence=0.95,
                matched_signals=retrospective,
            )

        static_category = self.classify_static_title(title)
        if static_category is not None:
            return ClassificationResult(
                category=static_category,
                page_type=PageType.CONTENT_DETAIL,
                confidence=0.9,
                matched_signals=self._category_title_matches(
                    title,
                    static_category,
                ),
            )

        for category, keywords in self.RULES:
            matches = self._matches(title, keywords)
            if matches:
                return ClassificationResult(
                    category=category,
                    page_type=PageType.CONTENT_DETAIL,
                    confidence=0.85,
                    matched_signals=matches,
                )

        if not has_student_action:
            return ClassificationResult(
                category=None,
                page_type=PageType.UNKNOWN,
                confidence=0.0,
            )

        for category, keywords in self.RULES:
            matches = self._matches(searchable, keywords)
            if matches:
                return ClassificationResult(
                    category=category,
                    page_type=PageType.CONTENT_DETAIL,
                    confidence=0.7,
                    matched_signals=matches,
                )
        return ClassificationResult(
            category=None,
            page_type=PageType.UNKNOWN,
            confidence=0.0,
        )

    def _classify_evergreen(
        self,
        title: str,
        corpus: str,
        host: str,
    ) -> ClassificationResult | None:
        library_titles = self._matches(title, self.LIBRARY_PROFILE_TITLES)
        library_context = (
            host == "lib.njupt.edu.cn"
            or "图书馆" in corpus
            or "馆藏" in corpus
            or "借阅" in corpus
        )
        if library_titles and library_context:
            return ClassificationResult(
                category=DocumentCategory.LIFE,
                page_type=PageType.EVERGREEN_STATIC,
                confidence=0.98,
                matched_signals=library_titles + ("图书馆上下文",),
            )

        research_titles = self._matches(
            title,
            self.RESEARCH_PROFILE_TITLES,
        )
        if research_titles and any(
            signal in corpus
            for signal in ("科研", "研究", "实验室", "学科", "平台")
        ):
            return ClassificationResult(
                category=DocumentCategory.RESEARCH,
                page_type=PageType.EVERGREEN_STATIC,
                confidence=0.96,
                matched_signals=research_titles,
            )

        college_titles = self._matches(title, self.COLLEGE_PROFILE_TITLES)
        college_context = self._matches(
            corpus,
            self.COLLEGE_PROFILE_SIGNALS,
        )
        if college_titles and college_context:
            return ClassificationResult(
                category=DocumentCategory.MAJOR,
                page_type=PageType.EVERGREEN_STATIC,
                confidence=0.96,
                matched_signals=college_titles + college_context,
            )

        for category, keywords in self.EVERGREEN_TITLE_RULES:
            matches = self._matches(title, keywords)
            if matches:
                return ClassificationResult(
                    category=category,
                    page_type=PageType.EVERGREEN_STATIC,
                    confidence=0.95,
                    matched_signals=matches,
                )

        service_titles = self._matches(title, self.SERVICE_GUIDE_TITLES)
        if service_titles:
            if any(
                signal in corpus
                for signal in (
                    "图书馆",
                    "借阅",
                    "食堂",
                    "宿舍",
                    "校园网",
                    "后勤",
                    "医疗",
                    "保卫",
                )
            ):
                return ClassificationResult(
                    category=DocumentCategory.LIFE,
                    page_type=PageType.EVERGREEN_STATIC,
                    confidence=0.92,
                    matched_signals=service_titles,
                )
            if any(
                signal in corpus
                for signal in (
                    "学籍",
                    "考试",
                    "选课",
                    "转专业",
                    "成绩",
                    "学分",
                    "教务",
                    "培养管理",
                )
            ):
                return ClassificationResult(
                    category=DocumentCategory.ACADEMIC,
                    page_type=PageType.EVERGREEN_STATIC,
                    confidence=0.92,
                    matched_signals=service_titles,
                )
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
            for _, keywords in (
                self.EVERGREEN_TITLE_RULES + self.TITLE_RULES + self.RULES
            )
            for keyword in keywords
            if keyword in text
        )
        return -score

    def _category_title_matches(
        self,
        title: str,
        category: DocumentCategory,
    ) -> tuple[str, ...]:
        for candidate, keywords in self.TITLE_RULES:
            if candidate == category:
                return self._matches(title, keywords)
        return ()

    @staticmethod
    def _matches(value: str, signals: tuple[str, ...]) -> tuple[str, ...]:
        return tuple(signal for signal in signals if signal in value)
