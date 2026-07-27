import sqlite3
from contextlib import closing
from datetime import UTC, datetime
from pathlib import Path
from threading import RLock

from app.models.schemas import CrawlerStatusResponse, CrawlRunStatus


class CrawlerStateStore:
    def __init__(self, database_path: Path) -> None:
        database_path.parent.mkdir(parents=True, exist_ok=True)
        self._database_path = database_path
        self._lock = RLock()
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self._database_path)
        connection.row_factory = sqlite3.Row
        return connection

    def _initialize(self) -> None:
        with self._lock, closing(self._connect()) as connection:
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS crawl_record (
                    url TEXT PRIMARY KEY,
                    title TEXT,
                    category TEXT,
                    content_hash TEXT,
                    etag TEXT,
                    last_modified TEXT,
                    last_status TEXT NOT NULL,
                    crawled_at TEXT NOT NULL,
                    indexed_at TEXT
                );

                CREATE TABLE IF NOT EXISTS crawl_run (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    status TEXT NOT NULL,
                    force_reindex INTEGER NOT NULL DEFAULT 0,
                    started_at TEXT NOT NULL,
                    finished_at TEXT,
                    total_pages INTEGER NOT NULL DEFAULT 0,
                    success_count INTEGER NOT NULL DEFAULT 0,
                    failed_count INTEGER NOT NULL DEFAULT 0,
                    indexed_count INTEGER NOT NULL DEFAULT 0,
                    unchanged_count INTEGER NOT NULL DEFAULT 0,
                    robots_denied_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT
                );
                """
            )
            connection.commit()

    def start_run(self, force_reindex: bool) -> int:
        with self._lock, closing(self._connect()) as connection:
            cursor = connection.execute(
                """
                INSERT INTO crawl_run(status, force_reindex, started_at)
                VALUES ('RUNNING', ?, ?)
                """,
                (int(force_reindex), datetime.now(UTC).isoformat()),
            )
            connection.commit()
            return int(cursor.lastrowid)

    def finish_run(self, run_id: int, stats: dict[str, int | str | None]) -> None:
        with self._lock, closing(self._connect()) as connection:
            connection.execute(
                """
                UPDATE crawl_run
                SET status = ?, finished_at = ?, total_pages = ?,
                    success_count = ?, failed_count = ?, indexed_count = ?,
                    unchanged_count = ?, robots_denied_count = ?, last_error = ?
                WHERE id = ?
                """,
                (
                    stats["status"],
                    datetime.now(UTC).isoformat(),
                    stats["total_pages"],
                    stats["success_count"],
                    stats["failed_count"],
                    stats["indexed_count"],
                    stats["unchanged_count"],
                    stats["robots_denied_count"],
                    stats.get("last_error"),
                    run_id,
                ),
            )
            connection.commit()

    def record_for(self, url: str) -> dict[str, str | None] | None:
        with self._lock, closing(self._connect()) as connection:
            row = connection.execute(
                "SELECT * FROM crawl_record WHERE url = ?",
                (url,),
            ).fetchone()
            return dict(row) if row else None

    def known_urls(self) -> list[str]:
        with self._lock, closing(self._connect()) as connection:
            rows = connection.execute(
                "SELECT url FROM crawl_record ORDER BY crawled_at DESC"
            ).fetchall()
            return [str(row["url"]) for row in rows]

    def save_record(
        self,
        *,
        url: str,
        title: str | None,
        category: str | None,
        content_hash: str | None,
        etag: str | None,
        last_modified: str | None,
        status: str,
        indexed: bool,
    ) -> None:
        now = datetime.now(UTC).isoformat()
        with self._lock, closing(self._connect()) as connection:
            connection.execute(
                """
                INSERT INTO crawl_record(
                    url, title, category, content_hash, etag, last_modified,
                    last_status, crawled_at, indexed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(url) DO UPDATE SET
                    title = excluded.title,
                    category = excluded.category,
                    content_hash = excluded.content_hash,
                    etag = COALESCE(excluded.etag, crawl_record.etag),
                    last_modified = COALESCE(
                        excluded.last_modified, crawl_record.last_modified
                    ),
                    last_status = excluded.last_status,
                    crawled_at = excluded.crawled_at,
                    indexed_at = COALESCE(
                        excluded.indexed_at, crawl_record.indexed_at
                    )
                """,
                (
                    url,
                    title,
                    category,
                    content_hash,
                    etag,
                    last_modified,
                    status,
                    now,
                    now if indexed else None,
                ),
            )
            connection.commit()

    def status(self, running: bool) -> CrawlerStatusResponse:
        with self._lock, closing(self._connect()) as connection:
            count = int(
                connection.execute(
                    "SELECT COUNT(*) FROM crawl_record"
                ).fetchone()[0]
            )
            row = connection.execute(
                "SELECT * FROM crawl_run ORDER BY id DESC LIMIT 1"
            ).fetchone()
        latest = None
        if row:
            latest = CrawlRunStatus(
                id=row["id"],
                status=row["status"],
                force_reindex=bool(row["force_reindex"]),
                started_at=datetime.fromisoformat(row["started_at"]),
                finished_at=(
                    datetime.fromisoformat(row["finished_at"])
                    if row["finished_at"]
                    else None
                ),
                total_pages=row["total_pages"],
                success_count=row["success_count"],
                failed_count=row["failed_count"],
                indexed_count=row["indexed_count"],
                unchanged_count=row["unchanged_count"],
                robots_denied_count=row["robots_denied_count"],
                last_error=row["last_error"],
            )
        return CrawlerStatusResponse(
            running=running,
            total_webpages=count,
            last_updated=latest.finished_at if latest else None,
            latest_run=latest,
        )
