from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field, HttpUrl, field_validator


class DocumentCategory(StrEnum):
    NEW_STUDENT = "NEW_STUDENT"
    ACADEMIC = "ACADEMIC"
    LIFE = "LIFE"
    MAJOR = "MAJOR"
    CAREER = "CAREER"
    SCHOOL_OVERVIEW = "SCHOOL_OVERVIEW"
    ORGANIZATION = "ORGANIZATION"
    RESEARCH = "RESEARCH"


class DateScope(StrEnum):
    RECENT = "RECENT"
    ALL = "ALL"


class CrawlScope(StrEnum):
    EXACT_HOST = "EXACT_HOST"
    DIRECT_NJUPT_SUBDOMAINS = "DIRECT_NJUPT_SUBDOMAINS"


class CrawledDocument(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=50)
    source: str = Field(min_length=1, max_length=128)
    source_type: str = "OFFICIAL_WEBSITE"
    source_url: HttpUrl
    category: DocumentCategory
    published_time: datetime | None = None
    crawl_time: datetime
    last_updated: datetime
    content_hash: str = Field(pattern=r"^[a-f0-9]{64}$")
    force_reindex: bool = False


class CrawlTriggerResponse(BaseModel):
    accepted: bool
    run_id: int | None = None
    message: str


class CustomCrawlRequest(BaseModel):
    seed_url: HttpUrl
    date_scope: DateScope = DateScope.RECENT
    crawl_scope: CrawlScope = CrawlScope.EXACT_HOST
    years: int = Field(default=2, ge=1, le=5)
    max_pages: int = Field(default=50, ge=1, le=50)
    force_reindex: bool = False

    @field_validator("seed_url")
    @classmethod
    def validate_official_njupt_url(cls, value: HttpUrl) -> HttpUrl:
        host = (value.host or "").lower().rstrip(".")
        if (
            value.scheme != "https"
            or not host.endswith(".njupt.edu.cn")
            or value.port not in (None, 443)
            or value.username is not None
            or value.password is not None
        ):
            raise ValueError("仅支持南京邮电大学官方 HTTPS 子域名")
        return value


class CrawlRunStatus(BaseModel):
    id: int
    status: str
    force_reindex: bool
    started_at: datetime
    finished_at: datetime | None = None
    total_pages: int = 0
    success_count: int = 0
    failed_count: int = 0
    indexed_count: int = 0
    unchanged_count: int = 0
    robots_denied_count: int = 0
    last_error: str | None = None


class CrawlerStatusResponse(BaseModel):
    running: bool
    total_webpages: int
    last_updated: datetime | None = None
    latest_run: CrawlRunStatus | None = None


class HealthResponse(BaseModel):
    status: str
    scheduler_enabled: bool
    allowed_domains: list[str]
