from fastapi import APIRouter, Request, status

from app.core.config import Settings
from app.models.schemas import (
    CustomCrawlRequest,
    CrawlerStatusResponse,
    CrawlTriggerResponse,
    HealthResponse,
)
from app.services.manager import CrawlerManager
from app.services.state_store import CrawlerStateStore


router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    settings: Settings = request.app.state.settings
    return HealthResponse(
        status="ok",
        scheduler_enabled=settings.crawler_schedule_enabled,
        allowed_domains=list(settings.allowed_domains),
    )


@router.get("/crawler/status", response_model=CrawlerStatusResponse)
def crawler_status(request: Request) -> CrawlerStatusResponse:
    manager: CrawlerManager = request.app.state.manager
    store: CrawlerStateStore = request.app.state.state_store
    return store.status(manager.running)


@router.post(
    "/crawler/run",
    response_model=CrawlTriggerResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
async def run_crawler(request: Request) -> CrawlTriggerResponse:
    manager: CrawlerManager = request.app.state.manager
    return manager.trigger(force_reindex=False)


@router.post(
    "/crawler/reindex",
    response_model=CrawlTriggerResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
async def reindex_crawler(request: Request) -> CrawlTriggerResponse:
    manager: CrawlerManager = request.app.state.manager
    return manager.trigger(force_reindex=True)


@router.post(
    "/crawler/custom",
    response_model=CrawlTriggerResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
async def custom_crawler(
    payload: CustomCrawlRequest,
    request: Request,
) -> CrawlTriggerResponse:
    manager: CrawlerManager = request.app.state.manager
    return manager.trigger_custom(
        seed_url=str(payload.seed_url),
        date_scope=payload.date_scope,
        crawl_scope=payload.crawl_scope,
        years=payload.years,
        max_pages=payload.max_pages,
        force_reindex=payload.force_reindex,
    )
