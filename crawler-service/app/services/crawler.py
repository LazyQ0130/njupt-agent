import hashlib
import heapq
import itertools
import re
import time
from datetime import UTC, datetime
from urllib.parse import urlsplit

import httpx

from app.core.config import Settings
from app.core.errors import CrawlerError, FetchError
from app.models.schemas import CrawledDocument, CrawlScope, DocumentCategory
from app.services.backend_client import BackendDocumentClient
from app.services.classifier import RuleBasedClassifier
from app.services.page_parser import WebPageParser
from app.services.robots import RobotsPolicy
from app.services.state_store import CrawlerStateStore


class OfficialWebsiteCrawler:
    MAX_CUSTOM_HOSTS = 30
    MAX_CUSTOM_PAGES_PER_HOST = 50
    STATIC_CATEGORIES = frozenset(
        {
            DocumentCategory.SCHOOL_OVERVIEW,
            DocumentCategory.ORGANIZATION,
            DocumentCategory.RESEARCH,
        }
    )
    STATIC_LIST_PATH = re.compile(r"/list\d*\.htm$", re.IGNORECASE)

    SOURCE_NAMES = {
        "www.njupt.edu.cn": "南京邮电大学官网",
        "jwc.njupt.edu.cn": "南京邮电大学本科生院",
        "cs.njupt.edu.cn": "南京邮电大学计算机学院",
    }

    def __init__(
        self,
        *,
        settings: Settings,
        client: httpx.Client,
        parser: WebPageParser,
        classifier: RuleBasedClassifier,
        robots: RobotsPolicy,
        state_store: CrawlerStateStore,
        backend_client: BackendDocumentClient,
    ) -> None:
        self._settings = settings
        self._client = client
        self._parser = parser
        self._classifier = classifier
        self._robots = robots
        self._state = state_store
        self._backend = backend_client
        self._last_request_at: dict[str, float] = {}

    def execute(
        self,
        run_id: int,
        *,
        force_reindex: bool,
        seed_urls: tuple[str, ...] | None = None,
        since: datetime | None = None,
        max_pages: int | None = None,
        allowed_hosts: frozenset[str] | None = None,
        crawl_scope: CrawlScope = CrawlScope.EXACT_HOST,
    ) -> None:
        stats: dict[str, int | str | None] = {
            "status": "COMPLETED",
            "total_pages": 0,
            "success_count": 0,
            "failed_count": 0,
            "indexed_count": 0,
            "unchanged_count": 0,
            "robots_denied_count": 0,
            "last_error": None,
        }
        queue: list[tuple[int, int, int, int, str, str]] = []
        queued: set[str] = set()
        visited: set[str] = set()
        host_order: dict[str, int] = {}
        host_seeds: dict[str, list[str]] = {}
        pages_by_host: dict[str, int] = {}
        sequence = itertools.count()
        is_custom_run = seed_urls is not None
        expanded_scope = (
            is_custom_run
            and crawl_scope == CrawlScope.DIRECT_NJUPT_SUBDOMAINS
        )

        def enqueue(
            url: str,
            depth: int,
            priority: int,
            expected_host: str,
        ) -> None:
            normalized = self._parser.normalize_url(url)
            if (
                not normalized
                or normalized in queued
                or normalized in visited
                or not self._is_allowed_for_host(normalized, expected_host)
                or depth > self._settings.crawler_max_depth
                or expected_host not in host_order
            ):
                return
            queued.add(normalized)
            heapq.heappush(
                queue,
                (
                    host_order[expected_host],
                    priority,
                    depth,
                    next(sequence),
                    normalized,
                    expected_host,
                ),
            )

        resolved_seeds = seed_urls or self._settings.seed_urls
        per_host_page_limit = min(
            max_pages or self._settings.crawler_max_pages,
            (
                self.MAX_CUSTOM_PAGES_PER_HOST
                if is_custom_run
                else self._settings.crawler_max_pages
            ),
        )
        total_page_limit = (
            per_host_page_limit
            * (self.MAX_CUSTOM_HOSTS if expanded_scope else 1)
            if is_custom_run
            else self._settings.crawler_max_pages
        )
        configured_hosts = (
            allowed_hosts
            or frozenset(self._settings.allowed_domains)
        )

        def register_seed(seed: str, *, discovered: bool = False) -> bool:
            normalized = self._parser.normalize_url(seed)
            if not normalized:
                return False
            host = (urlsplit(normalized).hostname or "").lower()
            if discovered:
                if (
                    not expanded_scope
                    or not self._is_official_subdomain_url(normalized)
                    or host in host_order
                    or len(host_order) >= self.MAX_CUSTOM_HOSTS
                ):
                    return False
            elif not self._is_allowed(normalized, configured_hosts):
                return False

            if host not in host_order:
                host_order[host] = len(host_order)
                host_seeds[host] = []
                pages_by_host[host] = 0
            if normalized not in host_seeds[host]:
                host_seeds[host].append(normalized)
            enqueue(normalized, 0, 0, host)
            return True

        for seed in resolved_seeds:
            register_seed(seed)

        normalized_primary_seed = (
            self._parser.normalize_url(resolved_seeds[0])
            if resolved_seeds
            else None
        )
        if seed_urls is None:
            for known_url in self._state.known_urls():
                normalized = self._parser.normalize_url(known_url)
                if not normalized or not self._is_allowed(
                    normalized,
                    configured_hosts,
                ):
                    continue
                host = (urlsplit(normalized).hostname or "").lower()
                if host not in host_order:
                    host_order[host] = len(host_order)
                    host_seeds[host] = []
                    pages_by_host[host] = 0
                enqueue(normalized, 1, 1, host)

        while (
            queue
            and int(stats["total_pages"]) < total_page_limit
        ):
            _, _, depth, _, url, expected_host = heapq.heappop(queue)
            queued.discard(url)
            if url in visited:
                continue
            if (
                is_custom_run
                and pages_by_host[expected_host] >= per_host_page_limit
            ):
                continue
            visited.add(url)
            pages_by_host[expected_host] += 1
            stats["total_pages"] = int(stats["total_pages"]) + 1

            if not self._robots.can_fetch(url):
                stats["robots_denied_count"] = (
                    int(stats["robots_denied_count"]) + 1
                )
                self._state.save_record(
                    url=url,
                    title=None,
                    category=None,
                    content_hash=None,
                    etag=None,
                    last_modified=None,
                    status="ROBOTS_DENIED",
                    indexed=False,
                )
                continue

            record = self._state.record_for(url)
            try:
                response = self._fetch(
                    url,
                    record=None if force_reindex else record,
                    expected_host=expected_host,
                )
                if response.status_code == 304:
                    stats["success_count"] = int(stats["success_count"]) + 1
                    stats["unchanged_count"] = int(stats["unchanged_count"]) + 1
                    continue
                parsed = self._parser.parse(response.text, url)
                stats["success_count"] = int(stats["success_count"]) + 1

                if (
                    expanded_scope
                    and url == normalized_primary_seed
                    and expected_host
                    == (urlsplit(normalized_primary_seed).hostname or "").lower()
                ):
                    for link, _ in parsed.content_links:
                        link_host = (urlsplit(link).hostname or "").lower()
                        if link_host != expected_host:
                            register_seed(link, discovered=True)

                for link, anchor_text in parsed.links:
                    link_host = (urlsplit(link).hostname or "").lower()
                    if is_custom_run:
                        if (
                            link_host != expected_host
                            or not self._is_custom_candidate(
                                link,
                                tuple(host_seeds[expected_host]),
                            )
                        ):
                            continue
                    elif link_host not in configured_hosts:
                        continue
                    elif link_host not in host_order:
                        host_order[link_host] = len(host_order)
                        host_seeds[link_host] = []
                        pages_by_host[link_host] = 0
                    enqueue(
                        link,
                        depth + 1,
                        self._classifier.priority(anchor_text + " " + link),
                        link_host,
                    )

                category = self._classifier.classify(
                    parsed.title,
                    parsed.content,
                )
                content_hash = self._content_hash(
                    parsed.title,
                    parsed.content,
                    parsed.published_time.isoformat()
                    if parsed.published_time
                    else "",
                )
                etag = response.headers.get("etag")
                last_modified = response.headers.get("last-modified")
                if since is not None:
                    published_utc = self._as_utc(parsed.published_time)
                    if published_utc is None or published_utc < since:
                        self._state.save_record(
                            url=url,
                            title=parsed.title,
                            category=category.value if category else None,
                            content_hash=content_hash,
                            etag=etag,
                            last_modified=last_modified,
                            status=(
                                "DATE_UNKNOWN"
                                if published_utc is None
                                else "OUT_OF_RANGE"
                            ),
                            indexed=False,
                        )
                        continue
                is_static_category = category in self.STATIC_CATEGORIES
                static_title_category = (
                    self._classifier.classify_static_title(parsed.title)
                )
                is_static_list = (
                    is_static_category
                    and static_title_category == category
                    and self.STATIC_LIST_PATH.search(
                        urlsplit(url).path
                    ) is not None
                )
                minimum_content_length = 50 if is_static_category else 120
                if (
                    category is None
                    or (not parsed.is_detail and not is_static_list)
                    or len(parsed.content) < minimum_content_length
                ):
                    self._state.save_record(
                        url=url,
                        title=parsed.title,
                        category=category.value if category else None,
                        content_hash=content_hash,
                        etag=etag,
                        last_modified=last_modified,
                        status="DISCOVERED",
                        indexed=False,
                    )
                    continue

                if (
                    not force_reindex
                    and record
                    and bool(record.get("indexed"))
                    and record.get("content_hash") == content_hash
                ):
                    stats["unchanged_count"] = int(stats["unchanged_count"]) + 1
                    self._state.save_record(
                        url=url,
                        title=parsed.title,
                        category=category.value,
                        content_hash=content_hash,
                        etag=etag,
                        last_modified=last_modified,
                        status="UNCHANGED",
                        indexed=False,
                    )
                    continue

                now = datetime.now(UTC)
                published = self._as_utc(parsed.published_time)
                last_updated = published or self._parse_http_date(
                    last_modified
                ) or now
                document = CrawledDocument(
                    title=parsed.title,
                    content=parsed.content,
                    source=self._source_name(url),
                    source_url=url,
                    category=category,
                    published_time=published,
                    crawl_time=now,
                    last_updated=last_updated,
                    content_hash=content_hash,
                    force_reindex=force_reindex,
                )
                action = self._backend.ingest(document)
                indexed = action not in {
                    "UNCHANGED",
                    "DRY_RUN",
                    "EXCLUDED",
                }
                if indexed:
                    stats["indexed_count"] = int(stats["indexed_count"]) + 1
                elif action == "UNCHANGED":
                    stats["unchanged_count"] = int(stats["unchanged_count"]) + 1
                self._state.save_record(
                    url=url,
                    title=parsed.title,
                    category=category.value,
                    content_hash=content_hash,
                    etag=etag,
                    last_modified=last_modified,
                    status=action,
                    indexed=indexed,
                )
            except CrawlerError as exception:
                stats["failed_count"] = int(stats["failed_count"]) + 1
                stats["last_error"] = str(exception)
                self._state.save_record(
                    url=url,
                    title=None,
                    category=None,
                    content_hash=(
                        str(record.get("content_hash"))
                        if record and record.get("content_hash")
                        else None
                    ),
                    etag=None,
                    last_modified=None,
                    status="FAILED",
                    indexed=False,
                )

        if int(stats["failed_count"]) > 0:
            stats["status"] = "COMPLETED_WITH_ERRORS"
        self._state.finish_run(run_id, stats)

    def _fetch(
        self,
        url: str,
        record: dict[str, str | None] | None,
        expected_host: str,
    ) -> httpx.Response:
        headers = {"User-Agent": self._settings.crawler_user_agent}
        if record:
            if record.get("etag"):
                headers["If-None-Match"] = str(record["etag"])
            if record.get("last_modified"):
                headers["If-Modified-Since"] = str(record["last_modified"])

        last_exception: Exception | None = None
        for attempt in range(self._settings.crawler_max_retries + 1):
            self._respect_delay(url)
            try:
                response = self._client.get(url, headers=headers)
                if not self._is_allowed_for_host(
                    str(response.url),
                    expected_host,
                ):
                    raise FetchError("页面重定向到未授权站点")
                if response.status_code == 304:
                    return response
                if response.status_code in {429, 500, 502, 503, 504}:
                    raise httpx.HTTPStatusError(
                        "retryable response",
                        request=response.request,
                        response=response,
                    )
                response.raise_for_status()
                content_type = response.headers.get("content-type", "")
                if "html" not in content_type.lower():
                    raise FetchError("目标不是 HTML 页面")
                return response
            except (httpx.HTTPError, FetchError) as exception:
                last_exception = exception
                if attempt < self._settings.crawler_max_retries:
                    time.sleep(min(2**attempt, 4))
        raise FetchError(f"页面抓取失败: {url}") from last_exception

    def _respect_delay(self, url: str) -> None:
        host = urlsplit(url).hostname or ""
        now = time.monotonic()
        elapsed = now - self._last_request_at.get(host, 0.0)
        remaining = self._settings.crawler_request_delay_seconds - elapsed
        if remaining > 0:
            time.sleep(remaining)
        self._last_request_at[host] = time.monotonic()

    def _is_allowed(
        self,
        url: str,
        allowed_hosts: frozenset[str] | None = None,
    ) -> bool:
        parsed = urlsplit(url)
        hosts = allowed_hosts or frozenset(self._settings.allowed_domains)
        return (
            parsed.scheme == "https"
            and parsed.hostname is not None
            and parsed.hostname.lower() in hosts
        )

    @staticmethod
    def _is_allowed_for_host(url: str, expected_host: str) -> bool:
        try:
            parsed = urlsplit(url)
            return (
                parsed.scheme == "https"
                and parsed.hostname is not None
                and parsed.hostname.lower() == expected_host
                and parsed.username is None
                and parsed.password is None
                and parsed.port in (None, 443)
            )
        except ValueError:
            return False

    @staticmethod
    def _is_official_subdomain_url(url: str) -> bool:
        try:
            parsed = urlsplit(url)
            host = (parsed.hostname or "").lower().rstrip(".")
            return (
                parsed.scheme == "https"
                and host.endswith(".njupt.edu.cn")
                and parsed.username is None
                and parsed.password is None
                and parsed.port in (None, 443)
            )
        except ValueError:
            return False

    def _source_name(self, url: str) -> str:
        host = (urlsplit(url).hostname or "").lower()
        return self.SOURCE_NAMES.get(
            host,
            f"南京邮电大学官方站点（{host}）",
        )

    @staticmethod
    def _is_custom_candidate(
        url: str,
        seed_urls: tuple[str, ...],
    ) -> bool:
        parsed_url = urlsplit(url)
        path = parsed_url.path.lower()
        filename = path.rsplit("/", 1)[-1]
        for seed_url in seed_urls:
            parsed_seed = urlsplit(seed_url)
            if parsed_url.hostname != parsed_seed.hostname:
                continue
            seed_path = parsed_seed.path.lower()
            seed_directory = seed_path.rsplit("/", 1)[0] + "/"
            if (
                path.startswith(seed_directory)
                and re.fullmatch(r"list\d*\.htm", filename)
            ):
                return True

            channel_match = re.search(r"/(\d+)/list\d*\.htm$", seed_path)
            if channel_match:
                channel = re.escape(channel_match.group(1))
                if re.search(rf"/c{channel}a\d+/page\.htm$", path):
                    return True
            elif path.endswith("/page.htm"):
                return True
        return False

    @staticmethod
    def _content_hash(title: str, content: str, published: str) -> str:
        value = f"{title.strip()}\n{content.strip()}\n{published}".encode("utf-8")
        return hashlib.sha256(value).hexdigest()

    @staticmethod
    def _parse_http_date(value: str | None) -> datetime | None:
        if not value:
            return None
        try:
            from email.utils import parsedate_to_datetime

            return parsedate_to_datetime(value)
        except (TypeError, ValueError, OverflowError):
            return None

    @staticmethod
    def _as_utc(value: datetime | None) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None:
            return value.replace(tzinfo=UTC)
        return value.astimezone(UTC)
