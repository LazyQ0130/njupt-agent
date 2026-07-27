from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import httpx
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI

from app.api.routes import router
from app.core.config import Settings
from app.services.backend_client import BackendDocumentClient
from app.services.classifier import RuleBasedClassifier
from app.services.crawler import OfficialWebsiteCrawler
from app.services.manager import CrawlerManager
from app.services.page_parser import WebPageParser
from app.services.robots import RobotsPolicy
from app.services.state_store import CrawlerStateStore


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        client = httpx.Client(
            follow_redirects=True,
            timeout=resolved.crawler_request_timeout_seconds,
            limits=httpx.Limits(max_connections=5, max_keepalive_connections=3),
        )
        store = CrawlerStateStore(resolved.crawler_state_database)
        parser = WebPageParser()
        classifier = RuleBasedClassifier()
        robots = RobotsPolicy(client, resolved.crawler_user_agent)
        backend = BackendDocumentClient(
            client=client,
            base_url=resolved.backend_base_url,
            shared_token=resolved.crawler_shared_token,
            dry_run=resolved.crawler_dry_run,
        )
        crawler = OfficialWebsiteCrawler(
            settings=resolved,
            client=client,
            parser=parser,
            classifier=classifier,
            robots=robots,
            state_store=store,
            backend_client=backend,
        )
        manager = CrawlerManager(crawler, store)
        scheduler = AsyncIOScheduler(timezone="Asia/Shanghai")
        if resolved.crawler_schedule_enabled:
            scheduler.add_job(
                manager.scheduled_run,
                "interval",
                hours=resolved.crawler_schedule_interval_hours,
                id="njupt-official-site-crawl",
                max_instances=1,
                coalesce=True,
            )
            scheduler.start()

        app.state.settings = resolved
        app.state.state_store = store
        app.state.manager = manager
        app.state.scheduler = scheduler
        try:
            yield
        finally:
            if scheduler.running:
                scheduler.shutdown(wait=False)
            client.close()

    app = FastAPI(
        title="NJUPT Crawler Service",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.include_router(router)
    return app


app = create_app()
