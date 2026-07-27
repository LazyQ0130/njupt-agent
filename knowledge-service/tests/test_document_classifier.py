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
