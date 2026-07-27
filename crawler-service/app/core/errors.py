class CrawlerError(RuntimeError):
    """Base error safe to aggregate into crawl statistics."""


class FetchError(CrawlerError):
    """A page could not be fetched after bounded retries."""


class BackendIntegrationError(CrawlerError):
    """The Spring ingestion endpoint could not accept a document."""
