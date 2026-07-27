import httpx

from app.core.errors import BackendIntegrationError
from app.models.schemas import CrawledDocument


class BackendDocumentClient:
    def __init__(
        self,
        *,
        client: httpx.Client,
        base_url: str,
        shared_token: str,
        dry_run: bool,
    ) -> None:
        self._client = client
        self._base_url = base_url.rstrip("/")
        self._shared_token = shared_token.strip()
        self._dry_run = dry_run

    def ingest(self, document: CrawledDocument) -> str:
        if self._dry_run:
            return "DRY_RUN"
        if not self._shared_token:
            raise BackendIntegrationError(
                "CRAWLER_SHARED_TOKEN 未配置，拒绝发送采集文档"
            )
        try:
            response = self._client.post(
                f"{self._base_url}/api/internal/crawler/documents",
                headers={"X-Crawler-Token": self._shared_token},
                json=document.model_dump(mode="json"),
            )
            response.raise_for_status()
            payload = response.json()
            return str(payload.get("data", {}).get("action", "UNKNOWN"))
        except (httpx.HTTPError, ValueError, TypeError) as exception:
            raise BackendIntegrationError("Spring 文档入库接口调用失败") from exception
