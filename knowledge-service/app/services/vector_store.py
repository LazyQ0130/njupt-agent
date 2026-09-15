import hashlib
import json
import re
import unicodedata
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
    CURATED_RANKING_BONUS = 0.15
    INDEX_SCHEMA_VERSION = "2"

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
        try:
            ids = [
                self._chunk_id(document_id, index, chunk)
                for index, chunk in enumerate(chunks)
            ]
            with self._lock:
                source_url = str(chunks[0].metadata.get("source_url", "")).strip()
                existing_ids = self._replacement_ids(
                    document_id=document_id,
                    source_url=source_url,
                )

                # LangChain's Chroma adapter uses Chroma's upsert operation
                # here. Write the replacement first so a failed embedding or
                # write leaves the previous document searchable.
                try:
                    self._store.add_documents(documents=chunks, ids=ids)
                except Exception:
                    # A failed batch may have written some new rows. Those IDs
                    # are versioned and did not exist before this attempt, so
                    # remove only them and leave the previous index intact.
                    try:
                        self._delete_ids(set(ids).difference(existing_ids))
                    except Exception:
                        # Preserve the original write error. A later retry can
                        # remove any partial replacement rows by document ID.
                        pass
                    raise

                # Re-indexing can shrink a document, and web pages receive a
                # fresh document_id whenever their content hash changes. Once
                # the replacement is durable, remove only the now-stale rows.
                # This also preserves the existing one-document-per-URL web
                # indexing behavior without deleting its old content early.
                self._delete_ids(existing_ids.difference(ids))
        except Exception as exception:
            raise VectorStoreError("文档向量写入失败") from exception
        return len(chunks)

    @classmethod
    def _chunk_id(
        cls,
        document_id: str,
        index: int,
        chunk: Document,
    ) -> str:
        payload = json.dumps(
            {
                "schema_version": cls.INDEX_SCHEMA_VERSION,
                "document_id": document_id,
                "index": index,
                "page_content": chunk.page_content,
                "metadata": chunk.metadata,
            },
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def _replacement_ids(
        self,
        *,
        document_id: str,
        source_url: str,
    ) -> set[str]:
        existing_ids = set(
            self._collection.get(
                where={"document_id": document_id},
                include=[],
            )["ids"]
        )
        if source_url:
            existing_ids.update(
                self._collection.get(
                    where={"source_url": source_url},
                    include=[],
                )["ids"]
            )
        return existing_ids

    def _delete_ids(self, ids: set[str]) -> None:
        if ids:
            self._collection.delete(ids=sorted(ids))

    def search(
        self,
        question: str,
        top_k: int,
        category: str | None = None,
    ) -> list[RagSearchResult]:
        # Retrieve a wider semantic candidate pool before applying category,
        # title and curated-source ranking. Otherwise a relevant curated entry
        # that sits just outside the raw vector top-k can never be promoted.
        candidate_count = max(100, top_k * 5)
        try:
            with self._lock:
                preferred = (
                    self._store.similarity_search_with_score(
                        question,
                        k=candidate_count,
                        filter={"category": category},
                    )
                    if category
                    else []
                )
                general = self._store.similarity_search_with_score(
                    question,
                    k=candidate_count,
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
                score=ChromaVectorStore._result_score(
                    question,
                    document,
                    score,
                ),
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
                ChromaVectorStore.CURATED_RANKING_BONUS
                if document.metadata.get("source_type") == "CURATED_OFFICIAL"
                else 0.0
            )
            transfer_bonus = ChromaVectorStore._transfer_ranking_bonus(
                question,
                document,
            )
            lexical_bonus = ChromaVectorStore._lexical_content_bonus(
                question,
                document,
            )
            return (
                raw_similarity
                + category_bonus
                + filename_bonus
                + curated_bonus
                + transfer_bonus
                + lexical_bonus,
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

    @staticmethod
    def _lexical_content_bonus(
        question: str,
        document: Document,
    ) -> float:
        """Recover short exact queries that semantic similarity underrates."""
        normalized_question = unicodedata.normalize("NFKC", question).lower()
        searchable = unicodedata.normalize(
            "NFKC",
            (
                f"{document.metadata.get('filename', '')}\n"
                f"{document.page_content[:4000]}"
            ),
        ).lower()

        ignored_bigrams = {
            "哪些",
            "什么",
            "怎么",
            "如何",
            "一下",
            "请问",
            "是否",
        }
        bigrams: set[str] = set()
        for run in re.findall(r"[\u3400-\u9fff]+", normalized_question):
            bigrams.update(
                run[index : index + 2]
                for index in range(max(0, len(run) - 1))
            )
        matched_bigrams = {
            bigram
            for bigram in bigrams.difference(ignored_bigrams)
            if bigram in searchable
        }
        chinese_bonus = min(0.12, len(matched_bigrams) * 0.025)

        compact_searchable = re.sub(r"\s+", "", searchable)
        class_markers = {
            re.sub(r"\s+", "", marker)
            for marker in re.findall(
                r"(?<![a-z0-9])([a-z][a-z0-9-]{0,5}\s*类)",
                normalized_question,
            )
        }
        class_bonus = (
            0.10
            if any(marker in compact_searchable for marker in class_markers)
            else 0.0
        )
        return min(0.20, chinese_bonus + class_bonus)

    @staticmethod
    def _transfer_ranking_bonus(
        question: str,
        document: Document,
    ) -> float:
        target = ChromaVectorStore._transfer_target(question)
        if not target:
            return 0.0
        filename = str(document.metadata.get("filename", ""))
        if "转专业" not in filename:
            return 0.0
        searchable = f"{filename}\n{document.page_content[:1600]}"
        if target in searchable:
            return 0.35
        target_bigrams = {
            target[index : index + 2]
            for index in range(max(0, len(target) - 1))
        }
        if any(bigram in filename for bigram in target_bigrams):
            return 0.30
        if any(signal in filename for signal in ("管理办法", "工作安排", "转专业安排")):
            return 0.08
        return 0.02

    @staticmethod
    def _transfer_target(question: str) -> str | None:
        match = re.search(r"转专业目标[：:]\s*([^\n；;]+)", question)
        if not match:
            return None
        target = re.sub(r"(专业|学院)$", "", match.group(1).strip())
        return target or None

    @staticmethod
    def _result_score(
        question: str,
        document: Document,
        distance: float,
    ) -> float:
        raw_similarity = max(0.0, min(1.0, 1.0 - float(distance)))
        filename_bonus = ChromaVectorStore._filename_bonus(
            question,
            str(document.metadata.get("filename", "")),
        )
        curated_bonus = (
            ChromaVectorStore.CURATED_RANKING_BONUS
            if document.metadata.get("source_type") == "CURATED_OFFICIAL"
            else 0.0
        )
        transfer_bonus = ChromaVectorStore._transfer_ranking_bonus(
            question,
            document,
        )
        lexical_bonus = ChromaVectorStore._lexical_content_bonus(
            question,
            document,
        )
        return max(
            0.0,
            min(
                1.0,
                raw_similarity
                + filename_bonus
                + curated_bonus
                + transfer_bonus
                + lexical_bonus,
            ),
        )
