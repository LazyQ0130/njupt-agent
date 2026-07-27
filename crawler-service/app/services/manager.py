import asyncio
from datetime import UTC, datetime
from urllib.parse import urlsplit

from app.models.schemas import CrawlScope, CrawlTriggerResponse, DateScope
from app.services.crawler import OfficialWebsiteCrawler
from app.services.state_store import CrawlerStateStore


class CrawlerManager:
    def __init__(
        self,
        crawler: OfficialWebsiteCrawler,
        state_store: CrawlerStateStore,
    ) -> None:
        self._crawler = crawler
        self._state_store = state_store
        self._task: asyncio.Task[None] | None = None

    @property
    def running(self) -> bool:
        return self._task is not None and not self._task.done()

    def trigger(self, *, force_reindex: bool) -> CrawlTriggerResponse:
        if self.running:
            return CrawlTriggerResponse(
                accepted=False,
                message="已有采集任务正在运行",
            )
        run_id = self._state_store.start_run(force_reindex)
        self._task = asyncio.create_task(
            self._execute(run_id, force_reindex),
            name=f"crawler-run-{run_id}",
        )
        return CrawlTriggerResponse(
            accepted=True,
            run_id=run_id,
            message="重新索引任务已启动" if force_reindex else "采集任务已启动",
        )

    def trigger_custom(
        self,
        *,
        seed_url: str,
        date_scope: DateScope = DateScope.RECENT,
        crawl_scope: CrawlScope = CrawlScope.EXACT_HOST,
        years: int,
        max_pages: int,
        force_reindex: bool = False,
    ) -> CrawlTriggerResponse:
        if self.running:
            return CrawlTriggerResponse(
                accepted=False,
                message="已有采集任务正在运行",
            )
        cutoff = (
            self._years_ago(years)
            if date_scope == DateScope.RECENT
            else None
        )
        run_id = self._state_store.start_run(force_reindex)
        self._task = asyncio.create_task(
            self._execute(
                run_id,
                force_reindex,
                seed_urls=(seed_url,),
                since=cutoff,
                max_pages=max_pages,
                allowed_hosts=frozenset(
                    {(urlsplit(seed_url).hostname or "").lower()}
                ),
                crawl_scope=crawl_scope,
            ),
            name=f"crawler-custom-{run_id}",
        )
        return CrawlTriggerResponse(
            accepted=True,
            run_id=run_id,
            message=(
                "学院目录扩展采集已启动，最多处理 30 个站点、每站 50 个页面；"
                if crawl_scope == CrawlScope.DIRECT_NJUPT_SUBDOMAINS
                else ""
            )
            + (
                (
                    "定向重新索引已启动，将收录旧页面和无可靠发布日期页面"
                    if force_reindex
                    else "定向采集已启动，将收录旧页面和无可靠发布日期页面"
                )
                if date_scope == DateScope.ALL
                else (
                    f"定向重新索引已启动，仅收录近 {years} 年内容"
                    if force_reindex
                    else f"定向采集已启动，仅收录近 {years} 年内容"
                )
            ),
        )

    async def scheduled_run(self) -> None:
        self.trigger(force_reindex=False)

    async def _execute(
        self,
        run_id: int,
        force_reindex: bool,
        *,
        seed_urls: tuple[str, ...] | None = None,
        since: datetime | None = None,
        max_pages: int | None = None,
        allowed_hosts: frozenset[str] | None = None,
        crawl_scope: CrawlScope = CrawlScope.EXACT_HOST,
    ) -> None:
        try:
            await asyncio.to_thread(
                self._crawler.execute,
                run_id,
                force_reindex=force_reindex,
                seed_urls=seed_urls,
                since=since,
                max_pages=max_pages,
                allowed_hosts=allowed_hosts,
                crawl_scope=crawl_scope,
            )
        except Exception:
            self._state_store.finish_run(
                run_id,
                {
                    "status": "FAILED",
                    "total_pages": 0,
                    "success_count": 0,
                    "failed_count": 1,
                    "indexed_count": 0,
                    "unchanged_count": 0,
                    "robots_denied_count": 0,
                    "last_error": "采集任务发生未预期错误",
                },
            )

    @staticmethod
    def _years_ago(years: int) -> datetime:
        now = datetime.now(UTC)
        try:
            return now.replace(year=now.year - years)
        except ValueError:
            return now.replace(
                year=now.year - years,
                month=2,
                day=28,
            )
