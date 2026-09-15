from app.models.schemas import DocumentCategory
from app.services.classifier import PageType, RuleBasedClassifier
from app.services.page_parser import WebPageParser


NOTICE_HTML = """
<!doctype html>
<html>
  <head><title>选课通知 - 本科生院</title></head>
  <body>
    <nav>首页 机构设置 规章制度</nav>
    <main>
      <h1>2026-2027学年第一学期学生选课通知</h1>
      <div class="meta">发布时间：2026-06-10</div>
      <div class="wp_articlecontent">
        <p>学生选课前务必仔细阅读学生网上选课指南。</p>
        <p>正式选课时间和补改选时间以本科生院通知为准。</p>
        <p>选课完成后，请进入教务系统核对已选课程。</p>
      </div>
    </main>
    <footer>版权所有 南京邮电大学</footer>
  </body>
</html>
"""


def test_extracts_article_and_removes_navigation() -> None:
    parsed = WebPageParser().parse(
        NOTICE_HTML,
        "https://jwc.njupt.edu.cn/2026/0610/c1594a303951/page.htm",
    )

    assert parsed.title == "2026-2027学年第一学期学生选课通知"
    assert parsed.published_time is not None
    assert parsed.published_time.date().isoformat() == "2026-06-10"
    assert "学生网上选课指南" in parsed.content
    assert "机构设置" not in parsed.content
    assert "版权所有" not in parsed.content
    assert parsed.is_detail is True


def test_rule_classifier_prioritizes_student_categories() -> None:
    classifier = RuleBasedClassifier()

    assert classifier.classify("新生报到指南", "入学材料") == (
        DocumentCategory.NEW_STUDENT
    )
    assert classifier.classify("选课通知", "考试安排") == (
        DocumentCategory.ACADEMIC
    )
    assert classifier.classify("图书馆开放时间", "校园服务") == (
        DocumentCategory.LIFE
    )
    assert classifier.classify("计算机专业介绍", "专业方向") == (
        DocumentCategory.MAJOR
    )
    assert classifier.classify("毕业生就业招聘", "职业发展") == (
        DocumentCategory.CAREER
    )
    assert classifier.classify("教师科研项目", "实验室成果") is None


def test_static_knowledge_titles_override_legacy_body_keywords() -> None:
    classifier = RuleBasedClassifier()

    cases = {
        "学校简介": DocumentCategory.SCHOOL_OVERVIEW,
        "学校章程": DocumentCategory.SCHOOL_OVERVIEW,
        "南邮精神": DocumentCategory.SCHOOL_OVERVIEW,
        "校标校训": DocumentCategory.SCHOOL_OVERVIEW,
        "南邮校史": DocumentCategory.SCHOOL_OVERVIEW,
        "现任领导": DocumentCategory.SCHOOL_OVERVIEW,
        "党政群部门": DocumentCategory.ORGANIZATION,
        "教学机构": DocumentCategory.ORGANIZATION,
        "科研机构": DocumentCategory.ORGANIZATION,
        "重点实验室与科研平台": DocumentCategory.RESEARCH,
    }
    misleading_content = "新生入学、教务管理、校园生活和科研成果"

    for title, expected in cases.items():
        assert classifier.classify(title, misleading_content) == expected


def test_rejects_retrospective_news_without_student_action() -> None:
    classifier = RuleBasedClassifier()

    assert classifier.classify(
        "学院成功举办师德大讲堂活动",
        "活动围绕课程思政和人才培养展开，教师代表参加会议。",
    ) is None
    assert classifier.classify(
        "党支部开展主题党日活动",
        "师生党员参观学习并交流心得。",
    ) is None
    assert classifier.classify(
        "我院学生荣获全国竞赛一等奖",
        "学院持续加强实践课程和创新人才培养。",
    ) is None


def test_keeps_actionable_notice_even_when_it_announces_an_event() -> None:
    classifier = RuleBasedClassifier()

    assert classifier.classify(
        "关于举办本科生创新竞赛的报名通知",
        "学生须在截止时间前提交报名材料和申请表。",
    ) == DocumentCategory.ACADEMIC


def test_classifies_contextual_evergreen_profiles() -> None:
    classifier = RuleBasedClassifier()

    library = classifier.classify_page(
        "本馆简介",
        "南京邮电大学图书馆拥有丰富馆藏并提供借阅、阅览和电子资源服务。",
        "https://lib.njupt.edu.cn/1379/list.htm",
    )
    college = classifier.classify_page(
        "学院简介",
        "学院设有多个本科专业，形成本科生、硕士和博士人才培养体系。",
        "https://example.njupt.edu.cn/xyjj/list.htm",
    )
    organization = classifier.classify_page(
        "部门职责",
        "本部门负责学校行政管理、协调服务和相关单位的工作联系。",
        "https://example.njupt.edu.cn/zzjg/list.htm",
    )
    research = classifier.classify_page(
        "重点实验室简介",
        "实验室围绕信息通信开展科学研究、平台建设和人才培养。",
        "https://example.njupt.edu.cn/lab/list.htm",
    )

    assert library.category == DocumentCategory.LIFE
    assert college.category == DocumentCategory.MAJOR
    assert organization.category == DocumentCategory.ORGANIZATION
    assert research.category == DocumentCategory.RESEARCH
    assert all(
        item.page_type == PageType.EVERGREEN_STATIC
        and item.is_trusted_evergreen
        for item in (library, college, organization, research)
    )


def test_ambiguous_profile_title_requires_matching_context() -> None:
    result = RuleBasedClassifier().classify_page(
        "本馆简介",
        "这里介绍某个展示页面的视觉风格、建设过程和页面导航信息。",
        "https://example.njupt.edu.cn/1379/list.htm",
    )

    assert result.category is None
    assert result.is_trusted_evergreen is False


def test_normalizes_links_and_drops_binary_attachments() -> None:
    parser = WebPageParser()
    parsed = parser.parse(
        """
        <html><body><main>
          <h1>新生入学指南</h1>
          <p>新生报到需要携带录取通知书和身份证件，具体要求以正式通知为准。</p>
          <p>请在规定时间完成学院报到、住宿登记和校园卡相关手续。</p>
          <a href="/guide/page.htm?utm_source=test">查看指南</a>
          <a href="/files/manual.pdf">附件</a>
        </main></body></html>
        """,
        "https://www.njupt.edu.cn/start/",
    )

    assert parsed.links == (
        ("https://www.njupt.edu.cn/guide/page.htm", "查看指南"),
    )
