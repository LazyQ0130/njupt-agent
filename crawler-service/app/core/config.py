from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    backend_base_url: str = "http://localhost:8080"
    crawler_shared_token: str = ""

    crawler_max_pages: int = Field(default=50, ge=1, le=500)
    crawler_max_depth: int = Field(default=2, ge=0, le=5)
    crawler_request_delay_seconds: float = Field(default=1.5, ge=0.0, le=60.0)
    crawler_request_timeout_seconds: float = Field(default=15.0, ge=1.0, le=120.0)
    crawler_max_retries: int = Field(default=2, ge=0, le=5)
    crawler_user_agent: str = (
        "NJUPTAIAssistantCrawler/1.0 (+contact: admin@njupt.edu.cn)"
    )
    crawler_allowed_domains: str = (
        "www.njupt.edu.cn,jwc.njupt.edu.cn,cs.njupt.edu.cn"
    )
    crawler_seed_urls: str = (
        "https://www.njupt.edu.cn/,"
        "https://jwc.njupt.edu.cn/,"
        "https://cs.njupt.edu.cn/"
    )
    crawler_state_database: Path = Path("./data/crawler.db")
    crawler_dry_run: bool = False

    crawler_schedule_enabled: bool = True
    crawler_schedule_interval_hours: int = Field(default=24, ge=1, le=720)

    @property
    def allowed_domains(self) -> tuple[str, ...]:
        return tuple(
            item.strip().lower()
            for item in self.crawler_allowed_domains.split(",")
            if item.strip()
        )

    @property
    def seed_urls(self) -> tuple[str, ...]:
        return tuple(
            item.strip()
            for item in self.crawler_seed_urls.split(",")
            if item.strip()
        )
