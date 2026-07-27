import hashlib
from pathlib import Path
from threading import RLock

import chromadb
from chromadb.config import Settings as NativeChromaSettings
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings

from app.core.errors import VectorStoreError
from app.models.schemas import RagSearchResult


class ChromaVectorStore:
    def __init__(
        self,
        *,
        persist_directory: Path,
        collection_name: str,
        embeddings: Embeddings,
    ) -> None:
        persist_directory.mkdir(parents=True, exist_ok=True)
        client = chromadb.PersistentClient(
            path=str(persist_directory),
            settings=NativeChromaSettings(anonymized_telemetry=False),
        )
        self._collection = client.get_or_create_collection(
            name=collection_name,
            metadata={"hnsw:space": "cosine"},
        )
        self._store = Chroma(
            client=client,
            collection_name=collection_name,
            embedding_function=embeddings,
            collection_metadata={"hnsw:space": "cosine"},
        )
        self._lock = RLock()

    def index(self, document_id: str, chunks: list[Document]) -> int:
        if not chunks:
            return 0
        ids = [
            hashlib.sha256(
                f"{document_id}:{index}".encode("utf-8")
            ).hexdigest()
            for index in range(len(chunks))
        ]
        try:
            with self._lock:
                source_url = str(chunks[0].metadata.get("source_url", "")).strip()
                if source_url:
                    self._collection.delete(where={"source_url": source_url})
                self._store.add_documents(documents=chunks, ids=ids)
        except Exception as exception:
            raise VectorStoreError("文档向量写入失败") from exception
        return len(chunks)

    def search(
        self,
        question: str,
        top_k: int,
        category: str | None = None,
    ) -> list[RagSearchResult]:
        try:
            with self._lock:
                preferred = (
                    self._store.similarity_search_with_score(
                        question,
                        k=top_k,
                        filter={"category": category},
                    )
                    if category
                    else []
                )
                general = self._store.similarity_search_with_score(
                    question,
                    k=top_k,
                )
                matches = self._merge_matches(
                    preferred,
                    general,
                    top_k,
                    question,
                )
        except Exception as exception:
            raise VectorStoreError("向量检索失败") from exception

        return [
            RagSearchResult(
                content=document.page_content,
                filename=str(document.metadata.get("filename", "未知文件")),
                page=max(1, int(document.metadata.get("page", 1))),
                source=str(document.metadata.get("source", "未知来源")),
                source_url=(
                    str(document.metadata["source_url"])
                    if document.metadata.get("source_url")
                    else None
                ),
                category=(
                    str(document.metadata["category"])
                    if document.metadata.get("category")
                    else None
                ),
                # The collection uses cosine distance: 0 is identical and
                # 2 is maximally dissimilar. Expose an intuitive 0–1 score.
                score=max(0.0, min(1.0, 1.0 - float(score))),
            )
            for document, score in matches
        ]

    def delete(self, document_id: str) -> None:
        try:
            with self._lock:
                self._collection.delete(
                    where={"document_id": document_id},
                )
        except Exception as exception:
            raise VectorStoreError("文档向量删除失败") from exception

    @staticmethod
    def _merge_matches(
        preferred: list[tuple[Document, float]],
        general: list[tuple[Document, float]],
        top_k: int,
        question: str,
    ) -> list[tuple[Document, float]]:
        candidates: dict[str, tuple[Document, float, bool]] = {}
        preferred_ids = {
            ChromaVectorStore._document_identity(document)
            for document, _ in preferred
        }
        for document, distance in [*preferred, *general]:
            identity = ChromaVectorStore._document_identity(document)
            existing = candidates.get(identity)
            is_preferred = identity in preferred_ids
            if existing is None or distance < existing[1]:
                candidates[identity] = (document, distance, is_preferred)

        def rank(item: tuple[Document, float, bool]) -> tuple[float, float]:
            document, distance, is_preferred = item
            raw_similarity = max(0.0, min(1.0, 1.0 - float(distance)))
            category_bonus = 0.03 if is_preferred else 0.0
            filename_bonus = ChromaVectorStore._filename_bonus(
                question,
                str(document.metadata.get("filename", "")),
            )
            curated_bonus = (
                0.05
                if document.metadata.get("source_type") == "CURATED_OFFICIAL"
                else 0.0
            )
            return (
                raw_similarity + category_bonus + filename_bonus + curated_bonus,
                raw_similarity,
            )

        ordered = sorted(candidates.values(), key=rank, reverse=True)
        return [(document, distance) for document, distance, _ in ordered[:top_k]]

    @staticmethod
    def _document_identity(document: Document) -> str:
        identity = (
            f"{document.metadata.get('document_id', '')}:"
            f"{document.metadata.get('chunk_index', '')}"
        )
        if identity == ":":
            identity = hashlib.sha256(
                document.page_content.encode("utf-8")
            ).hexdigest()
        return identity

    @staticmethod
    def _filename_bonus(question: str, filename: str) -> float:
        chinese = "".join(character for character in question if "\u4e00" <= character <= "\u9fff")
        bigrams = {
            chinese[index : index + 2]
            for index in range(max(0, len(chinese) - 1))
        }
        matches = sum(1 for bigram in bigrams if bigram in filename)
        return min(0.10, matches * 0.02)
