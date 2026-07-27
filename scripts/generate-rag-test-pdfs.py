from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "output" / "pdf"
FONT_PATH = Path(r"C:\Windows\Fonts\simsun.ttc")
FONT_NAME = "SimSun"


DOCUMENTS = (
    {
        "filename": "新生手册.pdf",
        "department": "学生工作处（联调模拟）",
        "title": "新生手册",
        "question": "新生报到需要准备什么？",
        "sections": (
            (
                "报到前准备",
                "请准备录取通知书、本人有效身份证件及复印件、近期证件照，"
                "并按学校当年度新生通知完成线上信息填报。若通知要求提交户口迁移、"
                "党团组织关系或家庭经济情况材料，应一并携带。",
            ),
            (
                "到校办理顺序",
                "到校后依次完成身份核验、学院报到、宿舍入住和校园卡相关手续。"
                "具体地点、时间和材料数量以当年度迎新通知为准。",
            ),
        ),
    },
    {
        "filename": "教务管理规定.pdf",
        "department": "教务处（联调模拟）",
        "title": "教务管理规定",
        "question": "南邮转专业需要什么条件？",
        "sections": (
            (
                "转专业申请条件",
                "学生申请转专业时，应具有正常在校学籍，并满足学校当年度转专业通知"
                "以及目标学院公布的接收条件。不同学院可对先修课程、成绩、接收人数和"
                "考核方式提出具体要求。",
            ),
            (
                "申请与考核",
                "学生应在规定时间内提交申请材料。目标学院可组织材料审核、笔试或面试，"
                "最终结果按学校公示流程确认。具体条件、时间和材料清单必须以教务处及"
                "目标学院当年度正式通知为准。",
            ),
        ),
    },
    {
        "filename": "专业培养方案.pdf",
        "department": "计算机学院（联调模拟）",
        "title": "计算机科学与技术专业培养方案",
        "question": "计算机科学与技术专业大一主要课程有哪些？",
        "sections": (
            (
                "一年级主要课程",
                "联调培养方案列出的基础课程包括高等数学、线性代数、大学英语、"
                "程序设计基础、计算机科学导论和离散数学。部分课程可能分学期开设。",
            ),
            (
                "选课说明",
                "课程名称、学分和开课学期可能随培养方案修订而变化，学生应以本人年级"
                "对应的正式培养方案和教务系统选课信息为准。",
            ),
        ),
    },
)


def register_font() -> None:
    if not FONT_PATH.exists():
        raise FileNotFoundError(f"Chinese font not found: {FONT_PATH}")
    pdfmetrics.registerFont(TTFont(FONT_NAME, str(FONT_PATH), subfontIndex=0))


def build_styles() -> dict[str, ParagraphStyle]:
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "DocumentTitle",
            parent=base["Title"],
            fontName=FONT_NAME,
            fontSize=22,
            leading=30,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#003B70"),
            spaceAfter=8 * mm,
        ),
        "notice": ParagraphStyle(
            "Notice",
            parent=base["Normal"],
            fontName=FONT_NAME,
            fontSize=9,
            leading=15,
            textColor=colors.HexColor("#9A3412"),
            backColor=colors.HexColor("#FFF7ED"),
            borderColor=colors.HexColor("#FDBA74"),
            borderWidth=0.6,
            borderPadding=8,
            spaceAfter=7 * mm,
        ),
        "meta": ParagraphStyle(
            "Meta",
            parent=base["Normal"],
            fontName=FONT_NAME,
            fontSize=10,
            leading=17,
            textColor=colors.HexColor("#475569"),
        ),
        "question": ParagraphStyle(
            "Question",
            parent=base["Heading2"],
            fontName=FONT_NAME,
            fontSize=14,
            leading=21,
            textColor=colors.HexColor("#1D4ED8"),
            spaceBefore=5 * mm,
            spaceAfter=4 * mm,
        ),
        "heading": ParagraphStyle(
            "SectionHeading",
            parent=base["Heading2"],
            fontName=FONT_NAME,
            fontSize=13,
            leading=20,
            textColor=colors.HexColor("#003B70"),
            spaceBefore=6 * mm,
            spaceAfter=2 * mm,
        ),
        "body": ParagraphStyle(
            "Body",
            parent=base["BodyText"],
            fontName=FONT_NAME,
            fontSize=11,
            leading=20,
            textColor=colors.HexColor("#1E293B"),
            firstLineIndent=22,
            spaceAfter=3 * mm,
        ),
        "footer": ParagraphStyle(
            "Footer",
            parent=base["Normal"],
            fontName=FONT_NAME,
            fontSize=8,
            leading=12,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#64748B"),
        ),
    }


def draw_page(canvas, document) -> None:
    canvas.saveState()
    width, _ = A4
    canvas.setStrokeColor(colors.HexColor("#DBEAFE"))
    canvas.setLineWidth(0.8)
    canvas.line(22 * mm, 18 * mm, width - 22 * mm, 18 * mm)
    canvas.setFont(FONT_NAME, 8)
    canvas.setFillColor(colors.HexColor("#64748B"))
    canvas.drawCentredString(
        width / 2,
        11 * mm,
        f"南邮智答 RAG 联调资料  |  第 {document.page} 页",
    )
    canvas.restoreState()


def build_document(item: dict, styles: dict[str, ParagraphStyle]) -> Path:
    output = OUTPUT_DIRECTORY / item["filename"]
    document = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        leftMargin=24 * mm,
        rightMargin=24 * mm,
        topMargin=24 * mm,
        bottomMargin=25 * mm,
        title=item["title"],
        author="南邮智答联调测试",
        subject="RAG 联调测试资料，非学校正式文件",
    )
    metadata = Table(
        [
            ["资料来源", item["department"]],
            ["用途", "Phase 4.2 RAG 检索与回答联调"],
        ],
        colWidths=[28 * mm, 112 * mm],
    )
    metadata.setStyle(
        TableStyle(
            [
                ("FONTNAME", (0, 0), (-1, -1), FONT_NAME),
                ("FONTSIZE", (0, 0), (-1, -1), 10),
                ("TEXTCOLOR", (0, 0), (0, -1), colors.HexColor("#003B70")),
                ("TEXTCOLOR", (1, 0), (1, -1), colors.HexColor("#334155")),
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F8FAFC")),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ]
        )
    )

    story = [
        Paragraph(item["title"], styles["title"]),
        Paragraph(
            "重要说明：本文件仅用于软件联调和自动化测试，不是南京邮电大学正式文件，"
            "不得作为办理校园事务的依据。",
            styles["notice"],
        ),
        metadata,
        Paragraph(item["question"], styles["question"]),
    ]
    for heading, body in item["sections"]:
        story.extend(
            [
                Paragraph(heading, styles["heading"]),
                Paragraph(body, styles["body"]),
            ]
        )
    story.extend(
        [
            Spacer(1, 8 * mm),
            Paragraph(
                "实际事项请查阅南京邮电大学相关部门发布的最新正式通知。",
                styles["footer"],
            ),
        ]
    )
    document.build(story, onFirstPage=draw_page, onLaterPages=draw_page)
    return output


def main() -> None:
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    register_font()
    styles = build_styles()
    for item in DOCUMENTS:
        print(build_document(item, styles))


if __name__ == "__main__":
    main()
