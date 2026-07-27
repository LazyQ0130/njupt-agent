import re
from dataclasses import dataclass
from datetime import datetime
from urllib.parse import parse_qsl, urlencode, urljoin, urlsplit, urlunsplit

from bs4 import BeautifulSoup, Tag


@dataclass(frozen=True)
class ParsedWebPage:
    title: str
    content: str
    published_time: datetime | None
    links: tuple[tuple[str, str], ...]
    content_links: tuple[tuple[str, str], ...]
    is_detail: bool


class WebPageParser:
    CONTENT_SELECTORS = (
        "article",
        ".wp_articlecontent",
        ".v_news_content",
        ".article-content",
        ".article_content",
        ".news-content",
        ".news_content",
        ".content-detail",
        ".content",
        "main",
    )
    REMOVE_SELECTORS = (
        "script",
        "style",
        "noscript",
        "iframe",
        "svg",
        "form",
        "nav",
        "footer",
        "aside",
        ".nav",
        ".navbar",
        ".header",
        ".footer",
        ".sidebar",
        ".breadcrumb",
        ".share",
        ".pagination",
        ".related",
        ".advertisement",
    )
    BLOCKED_EXTENSIONS = (
        ".jpg",
        ".jpeg",
        ".png",
        ".gif",
        ".svg",
        ".zip",
        ".rar",
        ".7z",
        ".mp4",
        ".mp3",
        ".doc",
        ".docx",
        ".xls",
        ".xlsx",
        ".ppt",
        ".pptx",
        ".pdf",
    )
    DATE_PATTERN = re.compile(r"(20\d{2})[-./年](\d{1,2})[-./月](\d{1,2})")

    def parse(self, html: str, url: str) -> ParsedWebPage:
        original = BeautifulSoup(html, "html.parser")
        links = self._extract_links(original, url)
        soup = BeautifulSoup(html, "html.parser")
        for selector in self.REMOVE_SELECTORS:
            for element in soup.select(selector):
                element.decompose()

        title = self._extract_title(soup)
        content_node, selected_detail = self._select_content(soup)
        content = self._clean_text(content_node)
        content_links = self._extract_links(content_node, url)
        published_time = (
            self._extract_published_time(soup, content)
            or self._parse_url_date(url)
        )
        is_detail = (
            selected_detail
            or "/page.htm" in url.lower()
            or bool(re.search(r"/20\d{2}/\d{4}/", url))
        )
        return ParsedWebPage(
            title=title,
            content=content,
            published_time=published_time,
            links=links,
            content_links=content_links,
            is_detail=is_detail,
        )

    def _extract_title(self, soup: BeautifulSoup) -> str:
        for selector in ("h1", ".arti_title", ".article-title", ".news-title"):
            node = soup.select_one(selector)
            if node and node.get_text(" ", strip=True):
                return self._normalize_space(node.get_text(" ", strip=True))[:255]
        meta = soup.select_one('meta[property="og:title"]')
        if meta and meta.get("content"):
            return self._normalize_space(str(meta["content"]))[:255]
        if soup.title:
            title = soup.title.get_text(" ", strip=True)
            return self._normalize_space(re.split(r"[-|_]", title)[0])[:255]
        return "未命名网页"

    def _select_content(self, soup: BeautifulSoup) -> tuple[Tag, bool]:
        for selector in self.CONTENT_SELECTORS:
            node = soup.select_one(selector)
            if node and len(node.get_text(" ", strip=True)) >= 80:
                return node, selector not in ("main",)
        return soup.body or soup, False

    def _clean_text(self, node: Tag) -> str:
        raw_lines = node.get_text("\n", strip=True).splitlines()
        lines: list[str] = []
        previous = ""
        for raw_line in raw_lines:
            line = self._normalize_space(raw_line)
            if not line or line == previous:
                continue
            if len(line) <= 2 and line in {"首页", "更多", "返回", "关闭"}:
                continue
            lines.append(line)
            previous = line
        return "\n".join(lines).strip()

    def _extract_published_time(
        self,
        soup: BeautifulSoup,
        content: str,
    ) -> datetime | None:
        for selector in (
            'meta[property="article:published_time"]',
            'meta[name="publishdate"]',
            'meta[name="PubDate"]',
            'meta[name="date"]',
        ):
            meta = soup.select_one(selector)
            if meta and meta.get("content"):
                parsed = self._parse_date(str(meta["content"]))
                if parsed:
                    return parsed
        return self._parse_date(content[:500])

    def _parse_date(self, value: str) -> datetime | None:
        match = self.DATE_PATTERN.search(value)
        if not match:
            return None
        try:
            return datetime(
                int(match.group(1)),
                int(match.group(2)),
                int(match.group(3)),
            )
        except ValueError:
            return None

    def _parse_url_date(self, value: str) -> datetime | None:
        match = re.search(r"/(20\d{2})/(\d{2})(\d{2})/", value)
        if not match:
            return self._parse_date(value)
        try:
            return datetime(
                int(match.group(1)),
                int(match.group(2)),
                int(match.group(3)),
            )
        except ValueError:
            return None

    def _extract_links(
        self,
        soup: BeautifulSoup,
        base_url: str,
    ) -> tuple[tuple[str, str], ...]:
        links: list[tuple[str, str]] = []
        seen: set[str] = set()
        for anchor in soup.select("a[href]"):
            normalized = self.normalize_url(
                urljoin(base_url, str(anchor.get("href", "")))
            )
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            links.append(
                (
                    normalized,
                    self._normalize_space(anchor.get_text(" ", strip=True)),
                )
            )
        return tuple(links)

    def normalize_url(self, value: str) -> str | None:
        try:
            parsed = urlsplit(value)
        except ValueError:
            return None
        if parsed.scheme.lower() not in {"http", "https"} or not parsed.hostname:
            return None
        if parsed.path.lower().endswith(self.BLOCKED_EXTENSIONS):
            return None
        query = urlencode(
            [
                (key, item)
                for key, item in parse_qsl(
                    parsed.query,
                    keep_blank_values=True,
                )
                if not key.lower().startswith(("utm_", "from", "spm"))
            ]
        )
        return urlunsplit(
            (
                parsed.scheme.lower(),
                parsed.netloc.lower(),
                parsed.path or "/",
                query,
                "",
            )
        )

    @staticmethod
    def _normalize_space(value: str) -> str:
        return re.sub(r"\s+", " ", value).strip()
