from app.services.document_classifier import DocumentClassifier
from app.services.document_parser import ParsedPage


def test_transfer_document_is_classified_as_academic() -> None:
    classifier = DocumentClassifier()

    category = classifier.classify(
        "25自动化转专业.pdf",
        [
            ParsedPage(
                page=1,
                text="自动化学院本科生转专业工作实施方案及考核安排",
            )
        ],
    )

    assert category == "ACADEMIC"


def test_competition_directory_is_classified_as_academic() -> None:
    category = DocumentClassifier().classify(
        "关于公布大学生创新创业竞赛项目认定及分类目录的通知.pdf",
        [
            ParsedPage(
                page=2,
                text="竞赛名称 分类 负责部门 中国国际大学生创新大赛 A 类",
            )
        ],
    )

    assert category == "ACADEMIC"


def test_classifier_uses_only_first_1200_characters() -> None:
    classifier = DocumentClassifier()

    category = classifier.classify(
        "通知.pdf",
        [ParsedPage(page=1, text=("无关内容" * 400) + "就业招聘")],
    )

    assert category is None


def test_static_filename_rules_override_body_noise() -> None:
    classifier = DocumentClassifier()
    pages = [
        ParsedPage(
            page=1,
            text="本页同时包含新生入学、教务管理、校园生活和科研成果等导航文字",
        )
    ]

    assert classifier.classify("学校章程.pdf", pages) == "SCHOOL_OVERVIEW"
    assert classifier.classify("科研机构.pdf", pages) == "ORGANIZATION"
    assert classifier.classify("重点实验室.pdf", pages) == "RESEARCH"


def test_contextual_evergreen_documents_use_existing_taxonomy() -> None:
    classifier = DocumentClassifier()

    assert classifier.classify(
        "本馆简介.pdf",
        [
            ParsedPage(
                page=1,
                text="南京邮电大学图书馆提供馆藏、借阅、阅览和电子资源服务。",
            )
        ],
    ) == "LIFE"
    assert classifier.classify(
        "学院简介.pdf",
        [
            ParsedPage(
                page=1,
                text="学院设有多个本科专业并形成本科、硕士、博士人才培养体系。",
            )
        ],
    ) == "MAJOR"
    assert classifier.classify(
        "部门职责.pdf",
        [ParsedPage(page=1, text="本部门负责学校行政管理和协调服务。")],
    ) == "ORGANIZATION"
    assert classifier.classify(
        "重点实验室简介.pdf",
        [ParsedPage(page=1, text="实验室围绕信息通信开展科研和平台建设。")],
    ) == "RESEARCH"


def test_ambiguous_library_title_requires_library_context() -> None:
    result = DocumentClassifier().classify_document(
        "本馆简介.pdf",
        [ParsedPage(page=1, text="这里介绍页面的建设过程和导航信息。")],
    )

    assert result.category is None
    assert result.page_type == "UNKNOWN"


def test_curriculum_title_overrides_academic_and_research_body_noise() -> None:
    result = DocumentClassifier().classify_document(
        "2025级人工智能专业培养计划（原DOCX转换）.pdf",
        [
            ParsedPage(
                page=1,
                text=(
                    "人工智能专业培养计划\n"
                    "本方案包含选课、考试、学分要求，以及科研平台和重点实验室介绍。"
                ),
            )
        ],
    )

    assert result.category == "MAJOR"
    assert result.matched_signals == ("专业培养计划",)


def test_first_page_curriculum_heading_overrides_body_noise() -> None:
    result = DocumentClassifier().classify_document(
        "1-人工智能定稿（原DOC转换）.docx",
        [
            ParsedPage(
                page=1,
                text=(
                    "南京邮电大学\n"
                    "人工智能专业培养方案\n"
                    "课程涉及选课、考试、学分，学院建设有多个科研平台和重点实验室。"
                ),
            )
        ],
    )

    assert result.category == "MAJOR"
    assert result.matched_signals == ("培养方案",)


def test_curriculum_schedule_heading_is_major_without_plan_in_filename() -> None:
    result = DocumentClassifier().classify_document(
        "2-网络工程（原XLSX转换）.pdf",
        [
            ParsedPage(
                page=1,
                text=(
                    "十一、专业教学进程计划\n"
                    "网络工程专业课程设置安排表\n"
                    "课程类别、课程编号、考核、学分、学时和选课要求"
                ),
            )
        ],
    )

    assert result.category == "MAJOR"
    assert result.matched_signals == (
        "专业教学进程计划",
        "专业课程设置安排表",
    )


def test_explicit_academic_title_stays_academic_when_heading_mentions_curriculum() -> None:
    category = DocumentClassifier().classify(
        "2025年转专业工作实施细则（原DOC转换）.docx",
        [
            ParsedPage(
                page=1,
                text="2025级本科人才培养方案适用范围\n转专业申请和审核安排。",
            )
        ],
    )

    assert category == "ACADEMIC"


def test_explicit_research_title_stays_research_when_heading_mentions_curriculum() -> None:
    category = DocumentClassifier().classify(
        "重点实验室简介（原XLSX转换）.pdf",
        [
            ParsedPage(
                page=1,
                text="本科人才培养方案支撑条件\n重点实验室科研平台介绍。",
            )
        ],
    )

    assert category == "RESEARCH"


def test_filename_normalization_removes_conversion_suffix() -> None:
    classifier = DocumentClassifier()

    assert (
        classifier._normalize_filename_stem("智能感知专业培养方案 的副本（原XLSX转换）.pdf")
        == "智能感知专业培养方案"
    )
