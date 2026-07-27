from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app
from app.models.schemas import CrawlScope, CrawlTriggerResponse, DateScope
from unittest.mock import patch


def test_health_and_empty_status(tmp_path) -> None:
    settings = Settings(
        crawler_state_database=tmp_path / "crawler.db",
        crawler_schedule_enabled=False,
        crawler_dry_run=True,
    )

    with TestClient(create_app(settings)) as client:
        health = client.get("/health")
        status = client.get("/crawler/status")
        trigger = client.post("/crawler/run")

    assert health.status_code == 200
    assert health.json()["status"] == "ok"
    assert health.json()["scheduler_enabled"] is False
    assert status.status_code == 200
    assert status.json()["running"] is False
    assert status.json()["total_webpages"] == 0
    assert status.json()["latest_run"] is None
    assert trigger.status_code == 202
    assert trigger.json()["accepted"] is True
    assert trigger.json()["run_id"] == 1


def test_custom_crawl_rejects_non_njupt_and_non_https_urls(tmp_path) -> None:
    settings = Settings(
        crawler_state_database=tmp_path / "crawler.db",
        crawler_schedule_enabled=False,
        crawler_dry_run=True,
    )

    with TestClient(create_app(settings)) as client:
        external = client.post(
            "/crawler/custom",
            json={"seed_url": "https://example.com/news", "years": 2},
        )
        insecure = client.post(
            "/crawler/custom",
            json={"seed_url": "http://coa.njupt.edu.cn/2277/list.htm", "years": 2},
        )

    assert external.status_code == 422
    assert insecure.status_code == 422


def test_custom_crawl_all_forwards_unlimited_date_scope(tmp_path) -> None:
    settings = Settings(
        crawler_state_database=tmp_path / "crawler.db",
        crawler_schedule_enabled=False,
        crawler_dry_run=True,
    )
    app = create_app(settings)

    with TestClient(app) as client:
        with patch.object(
            app.state.manager,
            "trigger_custom",
            return_value=CrawlTriggerResponse(
                accepted=True,
                run_id=1,
                message="定向采集已启动",
            ),
        ) as trigger:
            response = client.post(
                "/crawler/custom",
                json={
                    "seed_url": "https://coa.njupt.edu.cn/2277/list.htm",
                    "date_scope": "ALL",
                    "max_pages": 50,
                },
            )

    assert response.status_code == 202
    trigger.assert_called_once_with(
        seed_url="https://coa.njupt.edu.cn/2277/list.htm",
        date_scope=DateScope.ALL,
        crawl_scope=CrawlScope.EXACT_HOST,
        years=2,
        max_pages=50,
        force_reindex=False,
    )


def test_custom_crawl_forwards_direct_subdomain_scope(tmp_path) -> None:
    settings = Settings(
        crawler_state_database=tmp_path / "crawler.db",
        crawler_schedule_enabled=False,
        crawler_dry_run=True,
    )
    app = create_app(settings)

    with TestClient(app) as client:
        with patch.object(
            app.state.manager,
            "trigger_custom",
            return_value=CrawlTriggerResponse(
                accepted=True,
                run_id=1,
                message="学院目录扩展采集已启动",
            ),
        ) as trigger:
            response = client.post(
                "/crawler/custom",
                json={
                    "seed_url": "https://www.njupt.edu.cn/jxjg/list.htm",
                    "crawl_scope": "DIRECT_NJUPT_SUBDOMAINS",
                    "max_pages": 50,
                },
            )

    assert response.status_code == 202
    trigger.assert_called_once_with(
        seed_url="https://www.njupt.edu.cn/jxjg/list.htm",
        date_scope=DateScope.RECENT,
        crawl_scope=CrawlScope.DIRECT_NJUPT_SUBDOMAINS,
        years=2,
        max_pages=50,
        force_reindex=False,
    )
