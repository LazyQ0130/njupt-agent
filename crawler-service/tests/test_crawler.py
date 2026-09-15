from pathlib import Path
from datetime import UTC, datetime

import httpx

from app.core.config import Settings
from app.models.schemas import CrawledDocument, CrawlScope, DocumentCategory
from app.services.classifier import RuleBasedClassifier
from app.services.crawler import OfficialWebsiteCrawler
from app.services.page_parser import WebPageParser
from app.services.robots import RobotsPolicy
from app.services.state_store import CrawlerStateStore


HOME_HTML = """
<html><head><title>本科生院</title></head><body>
  <nav>首页</nav>
  <main>
    <h2>通知公告</h2>
    <a href="/2026/0610/c1594a303951/page.htm">学生选课通知</a>
  </main>
</body></html>
"""

NOTICE_HTML = """
<html><head><title>学生选课通知</title></head><body>
  <nav>首页 机构设置</nav>
  <article>
    <h1>2026-2027学年第一学期学生选课通知</h1>
    <p>发布时间：2026-06-10</p>
    <p>学生选课前务必阅读网上选课指南，并在规定时间登录教务系统。</p>
    <p>选课完成后应核对已选课程，补改选时间以本科生院最新通知为准。</p>
    <p>如遇问题，请先查询教学管理常见问题，再咨询所在学院教学秘书。</p>
  </article>
  <footer>版权所有</footer>
</body></html>
"""


class CollectingBackend:
    def __init__(self) -> None:
        self.documents: list[CrawledDocument] = []

    def ingest(self, document: CrawledDocument) -> str:
        self.documents.append(document)
        return "CREATED"


def build_settings(database: Path, *, max_pages: int = 5) -> Settings:
    return Settings(
        crawler_max_pages=max_pages,
        crawler_max_depth=1,
        crawler_request_delay_seconds=0,
        crawler_request_timeout_seconds=2,
        crawler_max_retries=0,
        crawler_allowed_domains="jwc.njupt.edu.cn",
        crawler_seed_urls="https://jwc.njupt.edu.cn/",
        crawler_state_database=database,
        crawler_schedule_enabled=False,
    )


def crawl_single_page(
    database: Path,
    *,
    path: str,
    html: str,
    since: datetime | None,
    host: str = "jwc.njupt.edu.cn",
) -> tuple[CrawlerStateStore, CollectingBackend]:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        if request.url.path == path:
            return httpx.Response(
                200,
                text=html,
                headers={"Content-Type": "text/html; charset=utf-8"},
                request=request,
            )
        return httpx.Response(404, request=request)

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = build_settings(database, max_pages=1)
    store = CrawlerStateStore(settings.crawler_state_database)
    backend = CollectingBackend()
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=backend,  # type: ignore[arg-type]
    )
    url = f"https://{host}{path}"
    run_id = store.start_run(False)
    crawler.execute(
        run_id,
        force_reindex=False,
        seed_urls=(url,),
        since=since,
        max_pages=1,
        allowed_hosts=frozenset({host}),
    )
    client.close()
    return store, backend


def test_crawls_notice_and_skips_unchanged_content(tmp_path) -> None:
    notice_url = "https://jwc.njupt.edu.cn/2026/0610/c1594a303951/page.htm"

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        if request.url.path == "/":
            return httpx.Response(
                200,
                text=HOME_HTML,
                headers={"Content-Type": "text/html; charset=utf-8"},
                request=request,
            )
        if str(request.url) == notice_url:
            if request.headers.get("If-None-Match") == '"notice-v1"':
                return httpx.Response(304, request=request)
            return httpx.Response(
                200,
                text=NOTICE_HTML,
                headers={
                    "Content-Type": "text/html; charset=utf-8",
                    "ETag": '"notice-v1"',
                },
                request=request,
            )
        return httpx.Response(404, request=request)

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = build_settings(tmp_path / "crawler.db")
    store = CrawlerStateStore(settings.crawler_state_database)
    backend = CollectingBackend()
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=backend,  # type: ignore[arg-type]
    )

    first_run = store.start_run(False)
    crawler.execute(first_run, force_reindex=False)
    second_run = store.start_run(False)
    crawler.execute(second_run, force_reindex=False)

    assert len(backend.documents) == 1
    document = backend.documents[0]
    assert document.category == DocumentCategory.ACADEMIC
    assert str(document.source_url) == notice_url
    assert document.source == "南京邮电大学本科生院"
    assert "机构设置" not in document.content
    status = store.status(False)
    assert status.latest_run is not None
    assert status.latest_run.unchanged_count >= 1
    assert status.total_webpages == 2
    client.close()


def test_honors_robots_and_maximum_page_limit(tmp_path) -> None:
    requests: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(str(request.url))
        if request.url.path == "/robots.txt":
            return httpx.Response(
                200,
                text="User-agent: *\nDisallow: /",
                request=request,
            )
        return httpx.Response(
            200,
            text=HOME_HTML,
            headers={"Content-Type": "text/html"},
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = build_settings(tmp_path / "crawler.db", max_pages=1)
    store = CrawlerStateStore(settings.crawler_state_database)
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=CollectingBackend(),  # type: ignore[arg-type]
    )

    run_id = store.start_run(False)
    crawler.execute(run_id, force_reindex=False)

    status = store.status(False)
    assert status.latest_run is not None
    assert status.latest_run.total_pages == 1
    assert status.latest_run.robots_denied_count == 1
    assert requests == ["https://jwc.njupt.edu.cn/robots.txt"]
    client.close()


def test_parser_uses_standard_njupt_url_date_as_fallback() -> None:
    page = WebPageParser().parse(
        """
        <html><head><title>学院通知</title></head><body>
          <article>
            <h1>学院通知</h1>
            <p>请相关学生按照学院要求完成材料提交和现场确认。</p>
          </article>
        </body></html>
        """,
        "https://coa.njupt.edu.cn/2025/0724/c2277a287459/page.htm",
    )

    assert page.published_time == datetime(2025, 7, 24)


def test_custom_crawl_only_indexes_pages_inside_date_window(tmp_path) -> None:
    list_html = """
    <html><head><title>通知公告</title></head><body><main>
      <a href="/2026/0501/c2277a100/page.htm">2026年转专业通知</a>
      <a href="/2023/0501/c2277a099/page.htm">2023年转专业通知</a>
      <a href="/zyjs/list.htm">学院介绍</a>
      <a href="https://outside.njupt.edu.cn/notice/page.htm">其他学院</a>
    </main></body></html>
    """

    def detail_html(year: int) -> str:
        return f"""
        <html><head><title>{year}年转专业通知</title></head><body>
          <article><h1>{year}年转专业通知</h1>
          <p>发布时间：{year}-05-01</p>
          <p>学生申请转专业需要关注报名条件、材料提交时间和学院考核安排。</p>
          <p>具体名额、笔试和面试要求以自动化学院当年度正式通知为准。</p>
          <p>申请人应在规定时间内完成系统报名并按要求提交相关证明材料。</p>
          <p>学院将按学校规定公示审核结果和后续办理安排。</p>
          </article>
        </body></html>
        """

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        if request.url.path == "/2277/list.htm":
            body = list_html
        elif "/2026/" in request.url.path:
            body = detail_html(2026)
        elif "/2023/" in request.url.path:
            body = detail_html(2023)
        else:
            return httpx.Response(404, request=request)
        return httpx.Response(
            200,
            text=body,
            headers={"Content-Type": "text/html; charset=utf-8"},
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = build_settings(tmp_path / "crawler.db")
    store = CrawlerStateStore(settings.crawler_state_database)
    backend = CollectingBackend()
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=backend,  # type: ignore[arg-type]
    )

    run_id = store.start_run(False)
    crawler.execute(
        run_id,
        force_reindex=False,
        seed_urls=("https://coa.njupt.edu.cn/2277/list.htm",),
        since=datetime(2024, 1, 1, tzinfo=UTC),
        max_pages=10,
        allowed_hosts=frozenset({"coa.njupt.edu.cn"}),
    )

    assert [document.title for document in backend.documents] == [
        "2026年转专业通知"
    ]
    assert backend.documents[0].source == (
        "南京邮电大学官方站点（coa.njupt.edu.cn）"
    )
    assert backend.documents[0].published_time is not None
    assert backend.documents[0].published_time.tzinfo == UTC
    assert store.record_for(
        "https://coa.njupt.edu.cn/2023/0501/c2277a099/page.htm"
    )["last_status"] == "OUT_OF_RANGE"
    assert store.record_for(
        "https://outside.njupt.edu.cn/notice/page.htm"
    ) is None
    assert store.record_for(
        "https://coa.njupt.edu.cn/zyjs/list.htm"
    ) is None
    client.close()


def test_all_date_scope_indexes_old_and_undated_detail_pages(tmp_path) -> None:
    list_html = """
    <html><head><title>通知公告</title></head><body><main>
      <a href="/2023/0501/c2277a099/page.htm">2023年转专业通知</a>
      <a href="/notice/c2277a098/page.htm">自动化学院转专业补充说明</a>
    </main></body></html>
    """
    old_html = """
    <html><head><title>2023年转专业通知</title></head><body><article>
      <h1>2023年转专业通知</h1><p>发布时间：2023-05-01</p>
      <p>学生申请转专业需要关注报名条件、材料提交时间和学院考核安排。</p>
      <p>申请人应按要求提交成绩单、申请表和其他证明材料，并参加学院组织的考核。</p>
      <p>具体名额和录取规则以自动化学院发布的正式通知为准，结果按学校要求公示。</p>
      <p>考核过程包括资格审核、专业能力测试和综合评价，各环节安排由学院统一通知。</p>
      <p>学生应及时查看学院官方网站，按规定时间完成确认，逾期处理以正式方案为准。</p>
    </article></body></html>
    """
    undated_html = """
    <html><head><title>自动化学院转专业补充说明</title></head><body><article>
      <h1>自动化学院转专业补充说明</h1>
      <p>学生申请转专业需要关注报名条件、材料提交要求和学院考核安排。</p>
      <p>申请人应按要求提交成绩单、申请表和其他证明材料，并参加学院组织的考核。</p>
      <p>具体名额和录取规则以自动化学院发布的正式通知为准，结果按学校要求公示。</p>
      <p>考核过程包括资格审核、专业能力测试和综合评价，各环节安排由学院统一通知。</p>
      <p>学生应及时查看学院官方网站，按规定时间完成确认，逾期处理以正式方案为准。</p>
    </article></body></html>
    """

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        bodies = {
            "/2277/list.htm": list_html,
            "/2023/0501/c2277a099/page.htm": old_html,
            "/notice/c2277a098/page.htm": undated_html,
        }
        body = bodies.get(request.url.path)
        if body is None:
            return httpx.Response(404, request=request)
        return httpx.Response(
            200,
            text=body,
            headers={"Content-Type": "text/html; charset=utf-8"},
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = build_settings(tmp_path / "crawler.db")
    store = CrawlerStateStore(settings.crawler_state_database)
    backend = CollectingBackend()
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=backend,  # type: ignore[arg-type]
    )

    run_id = store.start_run(False)
    crawler.execute(
        run_id,
        force_reindex=False,
        seed_urls=("https://coa.njupt.edu.cn/2277/list.htm",),
        since=None,
        max_pages=10,
        allowed_hosts=frozenset({"coa.njupt.edu.cn"}),
    )

    assert {document.title for document in backend.documents} == {
        "2023年转专业通知",
        "自动化学院转专业补充说明",
    }
    assert any(document.published_time is None for document in backend.documents)
    client.close()


def test_all_scope_indexes_undated_static_list_with_50_character_floor(
    tmp_path,
) -> None:
    path = "/17221/list.htm"
    html = """
    <html><head><title>学校章程</title></head><body><main>
      <h1>学校章程</h1>
      <p>本章程规定学校的办学宗旨、治理结构、师生权利义务、教学科研管理和监督机制。</p>
      <p>学校依法自主办学，坚持立德树人，并依照章程保障学生和教职工参与学校治理。</p>
    </main></body></html>
    """

    store, backend = crawl_single_page(
        tmp_path / "all-static.db",
        path=path,
        html=html,
        since=None,
    )

    assert len(backend.documents) == 1
    assert backend.documents[0].category == DocumentCategory.SCHOOL_OVERVIEW
    assert store.record_for(f"https://jwc.njupt.edu.cn{path}")[
        "last_status"
    ] == "CREATED"


def test_recent_scope_indexes_trusted_undated_static_list(tmp_path) -> None:
    path = "/17352/list.htm"
    html = """
    <html><head><title>党政群部门</title></head><body><main>
      <h1>党政群部门</h1>
      <p>学校党政群部门包括党委办公室、校长办公室、组织部、宣传部、教务处和科学技术处。</p>
      <p>各部门依照职责分工服务学校教学、科研、管理和师生发展。</p>
    </main></body></html>
    """

    store, backend = crawl_single_page(
        tmp_path / "recent-static.db",
        path=path,
        html=html,
        since=datetime(2024, 1, 1, tzinfo=UTC),
    )

    assert len(backend.documents) == 1
    assert backend.documents[0].category == DocumentCategory.ORGANIZATION
    assert store.record_for(f"https://jwc.njupt.edu.cn{path}")[
        "last_status"
    ] == "CREATED"


def test_static_list_below_50_characters_is_discovered_only(tmp_path) -> None:
    path = "/jxjg/list.htm"
    html = """
    <html><head><title>教学机构</title></head><body><main>
      <h1>教学机构</h1><p>学校设有若干学院。</p>
    </main></body></html>
    """

    store, backend = crawl_single_page(
        tmp_path / "short-static.db",
        path=path,
        html=html,
        since=None,
    )

    assert backend.documents == []
    assert store.record_for(f"https://jwc.njupt.edu.cn{path}")[
        "last_status"
    ] == "CONTENT_TOO_SHORT"


def test_ordinary_list_is_not_admitted_by_path_alone(tmp_path) -> None:
    path = "/notice/list.htm"
    html = """
    <html><head><title>通知公告</title></head><body><main>
      <h1>通知公告</h1>
      <p>这里展示学校近期发布的各类新闻、活动、会议和一般通知公告。</p>
      <p>部分链接可能提到重点实验室，但页面本身仅用于目录导航。</p>
      <p>更多内容请访问各栏目详情页面并以相应发布单位的正式通知为准。</p>
    </main></body></html>
    """

    store, backend = crawl_single_page(
        tmp_path / "ordinary-list.db",
        path=path,
        html=html,
        since=None,
    )

    assert backend.documents == []
    assert store.record_for(f"https://jwc.njupt.edu.cn{path}")[
        "last_status"
    ] == "UNCLASSIFIED"


def test_recent_scope_indexes_undated_library_profile(tmp_path) -> None:
    path = "/1379/list.htm"
    html = """
    <html><head><title>本馆简介</title></head><body><main>
      <h1>本馆简介</h1>
      <p>南京邮电大学图书馆由仙林校区图书馆、三牌楼校区图书馆和锁金村校区图书馆组成。</p>
      <p>图书馆拥有丰富的纸质和电子馆藏，提供阅览座位、自助借还、文献检索和学习空间服务。</p>
      <p>各校区图书馆持续面向师生开放，具体开放时间、借阅规则和电子资源使用方式以图书馆公告为准。</p>
    </main></body></html>
    """

    store, backend = crawl_single_page(
        tmp_path / "library-profile.db",
        path=path,
        html=html,
        since=datetime(2024, 1, 1, tzinfo=UTC),
        host="lib.njupt.edu.cn",
    )

    assert len(backend.documents) == 1
    assert backend.documents[0].category == DocumentCategory.LIFE
    assert backend.documents[0].published_time is None
    assert store.record_for(f"https://lib.njupt.edu.cn{path}")[
        "last_status"
    ] == "CREATED"


def test_undated_news_is_not_admitted_as_evergreen(tmp_path) -> None:
    path = "/news/list.htm"
    html = """
    <html><head><title>图书馆成功举办阅读推广活动</title></head>
    <body><article>
      <h1>图书馆成功举办阅读推广活动</h1>
      <p>图书馆组织师生开展阅读交流，活动现场进行了作品展示、经验分享和互动讨论。</p>
      <p>本次活动丰富了校园文化生活，参与师生交流了阅读心得，并共同参观了馆藏展示空间。</p>
      <p>图书馆将继续举办相关文化活动，为师生提供更多交流机会和阅读推广服务。</p>
    </article></body></html>
    """

    store, backend = crawl_single_page(
        tmp_path / "undated-library-news.db",
        path=path,
        html=html,
        since=datetime(2024, 1, 1, tzinfo=UTC),
        host="lib.njupt.edu.cn",
    )

    assert backend.documents == []
    assert store.record_for(f"https://lib.njupt.edu.cn{path}")[
        "last_status"
    ] == "UNCLASSIFIED"


def test_unindexed_record_is_refetched_after_classification_rules_change(
    tmp_path,
) -> None:
    path = "/1379/list.htm"
    page_fetches = 0
    original_html = """
    <html><head><title>普通页面</title></head><body><main>
      <h1>普通页面</h1>
      <p>这里暂时只有一般介绍内容，尚未包含可识别的学生服务主题或明确的知识分类信息。</p>
      <p>页面提供公开信息说明，但当前标题和正文不足以判断其属于哪一种校园知识类别。</p>
      <p>后续页面更新后可能补充更明确的服务对象、业务范围和负责单位等内容。</p>
    </main></body></html>
    """
    updated_html = """
    <html><head><title>本馆简介</title></head><body><main>
      <h1>本馆简介</h1>
      <p>南京邮电大学图书馆由多个校区图书馆组成，为全校师生提供文献、阅览和学习空间。</p>
      <p>图书馆拥有纸质及电子馆藏，并提供自助借还、文献检索、电子资源和咨询服务。</p>
      <p>师生可根据图书馆开放安排使用馆舍，具体借阅规则和开放时间以正式说明为准。</p>
      <p>馆内还设有研读空间、信息检索终端和自助文印设备，以满足读者不同形式的学习需求。</p>
      <p>图书馆持续完善资源保障体系，并通过咨询、培训和阅读推广支持学校教学与科研工作。</p>
    </main></body></html>
    """

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal page_fetches
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        if request.url.path != path:
            return httpx.Response(404, request=request)
        page_fetches += 1
        if request.headers.get("If-None-Match"):
            return httpx.Response(304, request=request)
        return httpx.Response(
            200,
            text=original_html if page_fetches == 1 else updated_html,
            headers={
                "Content-Type": "text/html; charset=utf-8",
                "ETag": '"profile-v1"',
            },
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = build_settings(tmp_path / "reclassify.db", max_pages=1)
    store = CrawlerStateStore(settings.crawler_state_database)
    backend = CollectingBackend()
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=backend,  # type: ignore[arg-type]
    )
    url = f"https://lib.njupt.edu.cn{path}"

    first_run = store.start_run(False)
    crawler.execute(
        first_run,
        force_reindex=False,
        seed_urls=(url,),
        since=datetime(2024, 1, 1, tzinfo=UTC),
        max_pages=1,
        allowed_hosts=frozenset({"lib.njupt.edu.cn"}),
    )
    assert store.record_for(url)["last_status"] == "UNCLASSIFIED"

    second_run = store.start_run(False)
    crawler.execute(
        second_run,
        force_reindex=False,
        seed_urls=(url,),
        since=datetime(2024, 1, 1, tzinfo=UTC),
        max_pages=1,
        allowed_hosts=frozenset({"lib.njupt.edu.cn"}),
    )

    assert page_fetches == 2
    assert len(backend.documents) == 1
    assert backend.documents[0].category == DocumentCategory.LIFE
    client.close()


def test_parser_separates_content_links_from_navigation_and_footer() -> None:
    parsed = WebPageParser().parse(
        """
        <html><head><title>教学机构</title></head><body>
          <nav><a href="https://mail.njupt.edu.cn/">电子邮件</a></nav>
          <main>
            <h1>教学机构</h1>
            <p>学校设有多个教学单位，各学院官网入口如下。</p>
            <a href="https://scie.njupt.edu.cn/">通信与信息工程学院</a>
          </main>
          <footer><a href="https://news.njupt.edu.cn/">新闻网</a></footer>
        </body></html>
        """,
        "https://www.njupt.edu.cn/jxjg/list.htm",
    )

    assert {link for link, _ in parsed.links} == {
        "https://mail.njupt.edu.cn/",
        "https://scie.njupt.edu.cn/",
        "https://news.njupt.edu.cn/",
    }
    assert parsed.content_links == (
        ("https://scie.njupt.edu.cn/", "通信与信息工程学院"),
    )


def test_directory_scope_crawls_direct_hosts_sequentially_without_spreading(
    tmp_path,
) -> None:
    seed_url = "https://www.njupt.edu.cn/jxjg/list.htm"
    page_requests: list[str] = []
    directory_html = """
    <html><head><title>教学机构</title></head><body>
      <nav><a href="https://mail.njupt.edu.cn/">电子邮件</a></nav>
      <main>
        <h1>教学机构</h1>
        <p>学校教学机构目录及学院官方网站入口。</p>
        <a href="https://scie.njupt.edu.cn/">通信与信息工程学院</a>
        <a href="https://scie.njupt.edu.cn/xygk/list.htm">重复学院入口</a>
        <a href="https://cs.njupt.edu.cn/">计算机学院</a>
        <a href="https://example.com/">站外网站</a>
      </main>
      <footer><a href="https://news.njupt.edu.cn/">新闻网</a></footer>
    </body></html>
    """
    root_html = """
    <html><head><title>学院首页</title></head><body><main>
      <h1>学院首页</h1>
      <p>学院人才培养、教学科研和学生工作信息。</p>
      <a href="/notice/list.htm">通知公告</a>
      <a href="https://ai.njupt.edu.cn/">其他学院</a>
    </main></body></html>
    """
    list_html = """
    <html><head><title>通知公告</title></head><body><main>
      <h1>通知公告</h1>
      <a href="/2026/0701/c100a1/page.htm">学院教学安排通知</a>
    </main></body></html>
    """
    detail_html = """
    <html><head><title>学院教学安排通知</title></head><body><article>
      <h1>学院教学安排通知</h1>
      <p>发布时间：2026-07-01</p>
      <p>学院将按照培养方案组织本学期课程教学、实验实践和考试安排，请相关学生及时关注课程调整。</p>
      <p>学生应在规定时间内完成选课确认，并通过学院官方网站查看后续教学通知与具体要求。</p>
      <p>如有问题请联系学院教学办公室，并以学院发布的正式通知为准。</p>
    </article></body></html>
    """

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        page_requests.append(str(request.url))
        if str(request.url) == seed_url:
            body = directory_html
        elif request.url.path == "/":
            body = root_html
        elif request.url.path == "/notice/list.htm":
            body = list_html
        elif request.url.path == "/2026/0701/c100a1/page.htm":
            body = detail_html
        else:
            return httpx.Response(404, request=request)
        return httpx.Response(
            200,
            text=body,
            headers={"Content-Type": "text/html; charset=utf-8"},
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = Settings(
        crawler_max_pages=50,
        crawler_max_depth=2,
        crawler_request_delay_seconds=0,
        crawler_max_retries=0,
        crawler_allowed_domains="www.njupt.edu.cn",
        crawler_seed_urls=seed_url,
        crawler_state_database=tmp_path / "directory.db",
        crawler_schedule_enabled=False,
    )
    store = CrawlerStateStore(settings.crawler_state_database)
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=CollectingBackend(),  # type: ignore[arg-type]
    )
    run_id = store.start_run(False)
    crawler.execute(
        run_id,
        force_reindex=False,
        seed_urls=(seed_url,),
        since=None,
        max_pages=50,
        allowed_hosts=frozenset({"www.njupt.edu.cn"}),
        crawl_scope=CrawlScope.DIRECT_NJUPT_SUBDOMAINS,
    )

    assert page_requests == [
        seed_url,
        "https://scie.njupt.edu.cn/",
        "https://scie.njupt.edu.cn/notice/list.htm",
        "https://scie.njupt.edu.cn/2026/0701/c100a1/page.htm",
        "https://cs.njupt.edu.cn/",
        "https://cs.njupt.edu.cn/notice/list.htm",
        "https://cs.njupt.edu.cn/2026/0701/c100a1/page.htm",
    ]
    assert store.record_for("https://mail.njupt.edu.cn/") is None
    assert store.record_for("https://news.njupt.edu.cn/") is None
    assert store.record_for("https://ai.njupt.edu.cn/") is None
    assert store.record_for("https://example.com/") is None
    status = store.status(False)
    assert status.latest_run is not None
    assert status.latest_run.total_pages == 7
    client.close()


def test_directory_scope_caps_total_hosts_and_applies_per_host_page_limit(
    tmp_path,
) -> None:
    seed_url = "https://www.njupt.edu.cn/jxjg/list.htm"
    links = "\n".join(
        f'<a href="https://college{index}.njupt.edu.cn/">学院 {index}</a>'
        for index in range(35)
    )
    directory_html = (
        "<html><head><title>教学机构</title></head><body><main>"
        "<h1>教学机构</h1><p>学校教学机构目录。</p>"
        f"{links}</main></body></html>"
    )
    page_requests: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        page_requests.append(str(request.url))
        body = (
            directory_html
            if str(request.url) == seed_url
            else "<html><head><title>学院首页</title></head><body><main>"
            "<h1>学院首页</h1><p>学院官方网站公开内容。</p>"
            '<a href="/notice/list.htm">通知公告</a></main></body></html>'
        )
        return httpx.Response(
            200,
            text=body,
            headers={"Content-Type": "text/html"},
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    settings = Settings(
        crawler_max_pages=50,
        crawler_max_depth=2,
        crawler_request_delay_seconds=0,
        crawler_max_retries=0,
        crawler_allowed_domains="www.njupt.edu.cn",
        crawler_seed_urls=seed_url,
        crawler_state_database=tmp_path / "limits.db",
        crawler_schedule_enabled=False,
    )
    store = CrawlerStateStore(settings.crawler_state_database)
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=CollectingBackend(),  # type: ignore[arg-type]
    )
    run_id = store.start_run(False)
    crawler.execute(
        run_id,
        force_reindex=False,
        seed_urls=(seed_url,),
        since=None,
        max_pages=1,
        allowed_hosts=frozenset({"www.njupt.edu.cn"}),
        crawl_scope=CrawlScope.DIRECT_NJUPT_SUBDOMAINS,
    )

    status = store.status(False)
    assert status.latest_run is not None
    assert status.latest_run.total_pages == 30
    assert len(page_requests) == 30
    assert page_requests[-1] == "https://college28.njupt.edu.cn/"
    assert "https://college29.njupt.edu.cn/" not in page_requests
    client.close()


def test_directory_scope_rejects_cross_host_redirects(tmp_path) -> None:
    seed_url = "https://www.njupt.edu.cn/jxjg/list.htm"

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(404, request=request)
        if str(request.url) == seed_url:
            return httpx.Response(
                200,
                text="""
                <html><head><title>教学机构</title></head><body><main>
                  <h1>教学机构</h1><p>学校教学机构目录。</p>
                  <a href="https://scie.njupt.edu.cn/">通信学院</a>
                </main></body></html>
                """,
                headers={"Content-Type": "text/html"},
                request=request,
            )
        if str(request.url) == "https://scie.njupt.edu.cn/":
            return httpx.Response(
                302,
                headers={"Location": "https://ai.njupt.edu.cn/"},
                request=request,
            )
        return httpx.Response(
            200,
            text="<html><body><main><h1>其他学院</h1></main></body></html>",
            headers={"Content-Type": "text/html"},
            request=request,
        )

    client = httpx.Client(
        transport=httpx.MockTransport(handler),
        follow_redirects=True,
    )
    settings = Settings(
        crawler_max_pages=50,
        crawler_max_depth=2,
        crawler_request_delay_seconds=0,
        crawler_max_retries=0,
        crawler_allowed_domains="www.njupt.edu.cn",
        crawler_seed_urls=seed_url,
        crawler_state_database=tmp_path / "redirect.db",
        crawler_schedule_enabled=False,
    )
    store = CrawlerStateStore(settings.crawler_state_database)
    crawler = OfficialWebsiteCrawler(
        settings=settings,
        client=client,
        parser=WebPageParser(),
        classifier=RuleBasedClassifier(),
        robots=RobotsPolicy(client, settings.crawler_user_agent),
        state_store=store,
        backend_client=CollectingBackend(),  # type: ignore[arg-type]
    )
    run_id = store.start_run(False)
    crawler.execute(
        run_id,
        force_reindex=False,
        seed_urls=(seed_url,),
        since=None,
        max_pages=50,
        allowed_hosts=frozenset({"www.njupt.edu.cn"}),
        crawl_scope=CrawlScope.DIRECT_NJUPT_SUBDOMAINS,
    )

    assert store.record_for("https://scie.njupt.edu.cn/")[
        "last_status"
    ] == "FAILED"
    assert store.record_for("https://ai.njupt.edu.cn/") is None
    status = store.status(False)
    assert status.latest_run is not None
    assert status.latest_run.status == "COMPLETED_WITH_ERRORS"
    client.close()
