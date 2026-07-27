from pathlib import Path

from app.services.document_parser import ParsedPage


class DocumentClassifier:
    """Classifies uploaded documents using the shared knowledge taxonomy."""

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
            ),
        ),
    )

    def classify(self, filename: str, pages: list[ParsedPage]) -> str | None:
        first_page = pages[0].text[:1200] if pages else ""
        filename_stem = Path(filename).stem
        corpus = f"{filename_stem}\n{first_page}".lower()

        for category, keywords in self._TITLE_RULES:
            if any(keyword in filename_stem for keyword in keywords):
                return category

        best_category: str | None = None
        best_score = 0
        for category, keywords in self._RULES:
            score = sum(
                max(1, len(keyword)) for keyword in keywords if keyword in corpus
            )
            if score > best_score:
                best_category = category
                best_score = score
        return best_category
