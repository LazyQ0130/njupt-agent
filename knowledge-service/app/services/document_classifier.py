import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

from app.services.document_parser import ParsedPage


@dataclass(frozen=True)
class DocumentClassificationResult:
    category: str | None
    page_type: str
    confidence: float
    matched_signals: tuple[str, ...] = ()


class DocumentClassifier:
    """Classifies uploaded documents using the shared knowledge taxonomy."""

    _CURRICULUM_TITLE_SIGNALS: tuple[str, ...] = (
        "本科人才培养方案",
        "专业人才培养方案",
        "本科专业培养方案",
        "专业培养计划",
        "培养方案",
        "专业教学进程计划",
        "专业课程设置安排表",
    )

    _ACADEMIC_TITLE_SIGNALS: tuple[str, ...] = (
        "转专业",
        "学籍",
        "选课",
        "考试",
        "补考",
        "重修",
        "成绩",
        "绩点",
        "校历",
        "教务",
        "培养管理",
        "创新创业竞赛",
        "竞赛项目认定",
        "竞赛分类目录",
        "管理办法",
        "管理规定",
        "管理细则",
        "实施细则",
        "办事指南",
    )

    _ACADEMIC_NOTICE_TITLE_SIGNALS: tuple[str, ...] = (
        "转专业通知",
        "选课通知",
        "考试通知",
        "补考通知",
        "学籍通知",
        "成绩通知",
        "教务通知",
    )

    _FILENAME_CONVERSION_NOISE = re.compile(
        r"\s*(?:的副本\s*)?(?:\(\s*)?(?:原\s*)?"
        r"(?:docx?|xlsx?|xls|pdf)\s*(?:文件)?\s*(?:转换|转化)(?:\s*\))?\s*$",
        re.IGNORECASE,
    )

    _TITLE_RULES: tuple[tuple[str, tuple[str, ...]], ...] = (
        (
            "ORGANIZATION",
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
            "SCHOOL_OVERVIEW",
            (
                "学校简介",
                "学校章程",
                "南邮精神",
                "校标校训",
                "校训",
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
            "RESEARCH",
            (
                "学科建设",
                "科研平台",
                "重点实验室",
                "研究中心",
                "学术刊物",
                "科研成果",
                "学术讲座",
                "学术报告",
                "实验室简介",
                "研究中心简介",
                "科研中心简介",
                "科研平台简介",
                "学科简介",
            ),
        ),
    )

    _RULES: tuple[tuple[str, tuple[str, ...]], ...] = (
        (
            "ORGANIZATION",
            (
                "党政群部门",
                "教学机构",
                "科研机构",
                "直属单位",
                "基层党组织",
                "独立学院",
                "部门职责",
                "单位职责",
                "工作职责",
                "机构设置",
            ),
        ),
        (
            "SCHOOL_OVERVIEW",
            (
                "学校简介",
                "学校章程",
                "南邮精神",
                "校标校训",
                "校史沿革",
                "现任领导",
                "校区地图",
                "校园景观",
                "校园景色",
                "校园风光",
            ),
        ),
        (
            "RESEARCH",
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
            "NEW_STUDENT",
            (
                "新生",
                "入学",
                "报到",
                "迎新",
                "军训",
                "校园卡",
            ),
        ),
        (
            "ACADEMIC",
            (
                "转专业",
                "选课",
                "考试",
                "补考",
                "重修",
                "学分",
                "绩点",
                "校历",
                "教务",
                "课程",
                "成绩",
                "培养管理",
                "办事指南",
            ),
        ),
        (
            "LIFE",
            (
                "图书馆",
                "食堂",
                "宿舍",
                "快递",
                "校园网",
                "后勤",
                "医疗",
                "保卫",
                "生活",
                "借阅",
                "开放时间",
            ),
        ),
        (
            "CAREER",
            (
                "就业",
                "招聘",
                "考研",
                "升学",
                "推免",
                "职业",
            ),
        ),
        (
            "MAJOR",
            (
                "专业",
                "培养方案",
                "培养方向",
                "通信工程",
                "计算机",
                "软件工程",
                "课程规划",
                "学院简介",
                "学院概况",
                "学院介绍",
                "培养层次",
                "本科专业",
                "专业设置",
                "课程体系",
            ),
        ),
    )

    def classify(self, filename: str, pages: list[ParsedPage]) -> str | None:
        return self.classify_document(filename, pages).category

    def classify_document(
        self,
        filename: str,
        pages: list[ParsedPage],
    ) -> DocumentClassificationResult:
        first_page = self._normalize_text(pages[0].text[:1200]) if pages else ""
        filename_stem = self._normalize_filename_stem(filename)
        first_page_heading = self._extract_first_page_heading(first_page)
        corpus = f"{filename_stem}\n{first_page}"

        filename_priority = self._classify_curriculum_or_academic_title(
            filename_stem
        )
        if filename_priority is not None:
            return filename_priority

        evergreen = self._classify_evergreen(filename_stem, corpus)
        if evergreen is not None:
            return evergreen

        for category, keywords in self._TITLE_RULES:
            matches = self._matches(filename_stem, keywords)
            if matches:
                return DocumentClassificationResult(
                    category=category,
                    page_type="STATIC_KNOWLEDGE",
                    confidence=0.95,
                    matched_signals=matches,
                )

        heading_priority = self._classify_curriculum_or_academic_title(
            first_page_heading
        )
        if heading_priority is not None:
            return heading_priority

        for category, keywords in self._TITLE_RULES:
            matches = self._matches(first_page_heading, keywords)
            if matches:
                return DocumentClassificationResult(
                    category=category,
                    page_type="STATIC_KNOWLEDGE",
                    confidence=0.94,
                    matched_signals=matches,
                )

        best_category: str | None = None
        best_score = 0
        best_matches: tuple[str, ...] = ()
        for category, keywords in self._RULES:
            matches = self._matches(corpus, keywords)
            score = sum(max(1, len(keyword)) for keyword in matches)
            if score > best_score:
                best_category = category
                best_score = score
                best_matches = matches
        return DocumentClassificationResult(
            category=best_category,
            page_type="CONTENT_DOCUMENT" if best_category else "UNKNOWN",
            confidence=min(0.9, 0.55 + best_score / 100),
            matched_signals=best_matches,
        )

    def _classify_curriculum_or_academic_title(
        self,
        title: str,
    ) -> DocumentClassificationResult | None:
        curriculum_matches = self._matches(title, self._CURRICULUM_TITLE_SIGNALS)
        if curriculum_matches:
            return DocumentClassificationResult(
                category="MAJOR",
                page_type="CONTENT_DOCUMENT",
                confidence=0.99,
                matched_signals=curriculum_matches,
            )

        academic_matches = self._matches(title, self._ACADEMIC_TITLE_SIGNALS)
        academic_matches += self._matches(title, self._ACADEMIC_NOTICE_TITLE_SIGNALS)
        if academic_matches:
            return DocumentClassificationResult(
                category="ACADEMIC",
                page_type="CONTENT_DOCUMENT",
                confidence=0.98,
                matched_signals=academic_matches,
            )
        return None

    def _classify_evergreen(
        self,
        filename_stem: str,
        corpus: str,
    ) -> DocumentClassificationResult | None:
        library_titles = self._matches(
            filename_stem,
            (
                "本馆简介",
                "馆情介绍",
                "馆情简介",
                "图书馆概况",
                "图书馆简介",
                "借阅与开放服务",
                "图书馆开放时间",
            ),
        )
        if library_titles and any(
            signal in corpus for signal in ("图书馆", "馆藏", "借阅")
        ):
            return DocumentClassificationResult(
                category="LIFE",
                page_type="EVERGREEN_STATIC",
                confidence=0.98,
                matched_signals=library_titles + ("图书馆上下文",),
            )

        research_titles = self._matches(
            filename_stem,
            (
                "实验室简介",
                "研究中心简介",
                "科研中心简介",
                "科研平台简介",
                "学科简介",
            ),
        )
        if research_titles and any(
            signal in corpus
            for signal in ("科研", "研究", "实验室", "学科", "平台")
        ):
            return DocumentClassificationResult(
                category="RESEARCH",
                page_type="EVERGREEN_STATIC",
                confidence=0.96,
                matched_signals=research_titles,
            )

        college_titles = self._matches(
            filename_stem,
            (
                "学院简介",
                "学院概况",
                "学院介绍",
                "院情简介",
                "院情概况",
            ),
        )
        college_context = self._matches(
            corpus,
            (
                "本科专业",
                "专业设置",
                "人才培养",
                "培养层次",
                "本科生",
                "硕士点",
                "博士点",
            ),
        )
        if college_titles and college_context:
            return DocumentClassificationResult(
                category="MAJOR",
                page_type="EVERGREEN_STATIC",
                confidence=0.96,
                matched_signals=college_titles + college_context,
            )

        organization_titles = self._matches(
            filename_stem,
            (
                "部门简介",
                "单位简介",
                "部门职责",
                "单位职责",
                "工作职责",
                "机构设置",
                "组织机构",
                "内设机构",
            ),
        )
        if organization_titles:
            return DocumentClassificationResult(
                category="ORGANIZATION",
                page_type="EVERGREEN_STATIC",
                confidence=0.95,
                matched_signals=organization_titles,
            )
        return None

    @staticmethod
    def _matches(value: str, signals: tuple[str, ...]) -> tuple[str, ...]:
        return tuple(signal for signal in signals if signal in value)

    @classmethod
    def _normalize_filename_stem(cls, filename: str) -> str:
        stem = cls._normalize_text(Path(filename).stem)
        return cls._FILENAME_CONVERSION_NOISE.sub("", stem).strip()

    @staticmethod
    def _normalize_text(value: str) -> str:
        return unicodedata.normalize("NFKC", value).lower()

    @staticmethod
    def _extract_first_page_heading(first_page: str) -> str:
        lines = [line.strip() for line in first_page.splitlines() if line.strip()]
        return "\n".join(lines[:6])[:480]
