import hashlib
from datetime import UTC, datetime

from app.models.schemas import (
    IndexDocumentResponse,
    IndexWebDocumentRequest,
    RagSearchResult,
)
from app.services.document_parser import DocumentParser
from app.services.document_parser import ParsedPage
from app.services.document_classifier import DocumentClassifier
from app.services.text_chunker import TextChunker
from app.services.vector_store import ChromaVectorStore


class RagService:
    def __init__(
        self,
        *,
        parser: DocumentParser,
        chunker: TextChunker,
        vector_store: ChromaVectorStore,
        default_top_k: int,
        classifier: DocumentClassifier,
    ) -> None:
        self._parser = parser
        self._chunker = chunker
        self._vector_store = vector_store
        self._default_top_k = default_top_k
        self._classifier = classifier

    def index_document(
        self,
        *,
        filename: str,
        content: bytes,
        source: str,
        upload_time: str | None,
    ) -> IndexDocumentResponse:
        normalized_source = source.strip() or "未知来源"
        normalized_upload_time = upload_time or datetime.now(UTC).isoformat()
        document_id = hashlib.sha256(
            content + b"\0" + filename.encode("utf-8") + b"\0"
            + normalized_source.encode("utf-8")
        ).hexdigest()
        pages = self._parser.parse(filename, content)
        category = self._classifier.classify(filename, pages)
        chunks = self._chunker.split(
            pages,
            filename=filename,
            source=normalized_source,
            upload_time=normalized_upload_time,
            document_id=document_id,
            extra_metadata={
                "category": category,
                "source_type": "UPLOADED_FILE",
            }
            if category
            else {"source_type": "UPLOADED_FILE"},
        )
        indexed_count = self._vector_store.index(document_id, chunks)
        return IndexDocumentResponse(
            document_id=document_id,
            filename=filename,
            chunks_indexed=indexed_count,
            category=category,
        )

    def search(
        self,
        question: str,
        top_k: int | None,
        category: str | None = None,
    ) -> list[RagSearchResult]:
        return self._vector_store.search(
            question=question.strip(),
            top_k=top_k or self._default_top_k,
            category=category,
        )

    def delete_document(self, document_id: str) -> None:
        self._vector_store.delete(document_id)

    def index_web_document(
        self,
        request: IndexWebDocumentRequest,
    ) -> IndexDocumentResponse:
        source_url = str(request.source_url)
        document_id = hashlib.sha256(
            f"{source_url}\0{request.content_hash}".encode("utf-8")
        ).hexdigest()
        chunks = self._chunker.split(
            [ParsedPage(page=1, text=request.content.strip())],
            filename=request.title.strip(),
            source=request.source.strip(),
            upload_time=request.crawl_time,
            document_id=document_id,
            extra_metadata={
                "source_url": source_url,
                "category": request.category,
                "last_updated": request.last_updated,
                "crawl_time": request.crawl_time,
                "content_hash": request.content_hash,
                "source_type": request.source_type,
            },
        )
        indexed_count = self._vector_store.index(document_id, chunks)
        return IndexDocumentResponse(
            document_id=document_id,
            filename=request.title.strip(),
            chunks_indexed=indexed_count,
            category=request.category,
        )
