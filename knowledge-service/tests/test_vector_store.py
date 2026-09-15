import pytest
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings

from app.core.errors import VectorStoreError
from app.services.vector_store import ChromaVectorStore


def match(
    document_id: str,
    category: str,
    distance: float,
    filename: str | None = None,
):
    return (
        Document(
            page_content=f"{document_id} content",
            metadata={
                "document_id": document_id,
                "chunk_index": 0,
                "category": category,
                "filename": filename or f"{document_id}.html",
            },
        ),
        distance,
    )


def test_low_score_category_results_do_not_reserve_context_slots():
    preferred = [
        match(f"low-academic-{index}", "ACADEMIC", 0.81 + index / 100)
        for index in range(3)
    ]
    general = [
        *preferred,
        match("science", "ACADEMIC", 0.48, "理学院转专业方案.html"),
        match("communication", "ACADEMIC", 0.51, "通信学院转专业方案.html"),
        match("automation", "ACADEMIC", 0.52, "25自动化转专业.pdf"),
    ]

    merged = ChromaVectorStore._merge_matches(
        preferred,
        general,
        top_k=5,
        question="自动化专业转专业条件",
    )

    ids = [item[0].metadata["document_id"] for item in merged]
    assert set(ids[:3]) == {"automation", "science", "communication"}
    assert "automation" in ids
    assert len(ids) == len(set(ids))


def test_filename_bonus_is_capped_and_does_not_change_raw_distance():
    automation = match(
        "automation",
        "ACADEMIC",
        0.52,
        "25自动化转专业.pdf",
    )
    general = match("general", "LIFE", 0.46, "办事指南.pdf")

    merged = ChromaVectorStore._merge_matches(
        [automation],
        [automation, general],
        top_k=2,
        question="自动化专业转专业条件",
    )

    assert merged[0] == automation
    assert merged[0][1] == 0.52
    assert ChromaVectorStore._filename_bonus(
        "自动化专业转专业条件",
        "25自动化转专业.pdf",
    ) <= 0.10


def test_curated_source_receives_small_ranking_bonus():
    raw = match("raw", "ACADEMIC", 0.40, "缓考原文.html")
    curated = match("curated", "ACADEMIC", 0.43, "缓考办理知识")
    curated[0].metadata["source_type"] = "CURATED_OFFICIAL"

    merged = ChromaVectorStore._merge_matches(
        [],
        [raw, curated],
        top_k=2,
        question="缓考如何办理",
    )

    assert merged[0] == curated


def test_result_score_includes_title_and_curated_quality_signals():
    curated = match(
        "curated",
        "LIFE",
        0.66,
        "校外访问图书馆电子资源：CARSI使用方法",
    )
    curated[0].metadata["source_type"] = "CURATED_OFFICIAL"

    score = ChromaVectorStore._result_score(
        "如何在校外通过CARSI访问知网",
        curated[0],
        curated[1],
    )

    assert score > 0.35
    assert score <= 1.0


def test_short_competition_query_gets_exact_content_and_class_marker_bonus():
    contest = match(
        "contest-directory",
        "ACADEMIC",
        0.70,
        "大学生创新创业竞赛项目认定及分类目录.pdf",
    )
    contest[0].page_content = (
        "序号 竞赛名称 分类 负责部门 "
        "中国国际大学生创新大赛 A 类 创新创业教育学院"
    )

    score = ChromaVectorStore._result_score(
        "南邮A类竞赛有哪些",
        contest[0],
        contest[1],
    )

    assert score > 0.35
    assert ChromaVectorStore._lexical_content_bonus(
        "南邮A类竞赛有哪些",
        contest[0],
    ) >= 0.10


def test_class_marker_does_not_boost_unrelated_content():
    unrelated = match(
        "library",
        "LIFE",
        0.70,
        "图书馆开放时间.pdf",
    )

    assert ChromaVectorStore._lexical_content_bonus(
        "南邮A类竞赛有哪些",
        unrelated[0],
    ) == 0.0


def test_transfer_target_document_outranks_curated_overview_and_other_college():
    target = match(
        "automation-transfer",
        "ACADEMIC",
        0.58,
        "25自动化转专业.pdf",
    )
    source = match(
        "economics-transfer",
        "ACADEMIC",
        0.49,
        "25经济转专业.pdf",
    )
    overview = match(
        "automation-overview",
        "MAJOR",
        0.46,
        "自动化学院：本科专业、培养层次与教务联系",
    )
    overview[0].metadata["source_type"] = "CURATED_OFFICIAL"
    question = (
        "原问题：数字经济转自动化条件\n"
        "转专业目标：自动化\n"
        "来源专业：数字经济\n"
        "检索重点：自动化专业转专业接收条件、名额、笔试和面试要求"
    )

    merged = ChromaVectorStore._merge_matches(
        [target, source],
        [overview, source, target],
        top_k=3,
        question=question,
    )

    assert merged[0] == target
    assert ChromaVectorStore._result_score(
        question,
        target[0],
        target[1],
    ) > 0.35


def test_transfer_target_direction_changes_ranking():
    automation = match(
        "automation",
        "ACADEMIC",
        0.50,
        "25自动化转专业.pdf",
    )
    economics = match(
        "economics",
        "ACADEMIC",
        0.50,
        "25经济转专业.pdf",
    )

    toward_automation = ChromaVectorStore._merge_matches(
        [],
        [economics, automation],
        top_k=2,
        question="转专业目标：自动化\n来源专业：数字经济",
    )
    toward_economics = ChromaVectorStore._merge_matches(
        [],
        [economics, automation],
        top_k=2,
        question="转专业目标：数字经济\n来源专业：自动化",
    )

    assert toward_automation[0] == automation
    assert toward_economics[0] == economics


class StableEmbeddings(Embeddings):
    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self.embed_query(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return [
            float(len(text) + 1),
            float(sum(ord(character) for character in text) % 997 + 1),
        ]


@pytest.fixture
def persistent_vector_store(tmp_path) -> ChromaVectorStore:
    return ChromaVectorStore(
        persist_directory=tmp_path / "chroma",
        collection_name="vector_store_replacement",
        embeddings=StableEmbeddings(),
    )


def chunk(
    document_id: str,
    index: int,
    content: str,
    *,
    category: str | None = None,
    source_url: str | None = None,
) -> Document:
    metadata: dict[str, str | int] = {
        "document_id": document_id,
        "chunk_index": index,
        "filename": f"{document_id}.pdf",
        "source": "test",
        "page": 1,
    }
    if category is not None:
        metadata["category"] = category
    if source_url is not None:
        metadata["source_url"] = source_url
    return Document(page_content=content, metadata=metadata)


def test_reindex_replaces_category_removes_category_and_deletes_stale_chunks(
    persistent_vector_store: ChromaVectorStore,
):
    document_id = "training-plan"
    persistent_vector_store.index(
        document_id,
        [
            chunk(document_id, 0, "curriculum content", category="ACADEMIC"),
            chunk(document_id, 1, "old tail chunk", category="ACADEMIC"),
        ],
    )
    previous = persistent_vector_store._collection.get(
        where={"document_id": document_id},
        include=[],
    )

    persistent_vector_store.index(
        document_id,
        [chunk(document_id, 0, "curriculum content", category="MAJOR")],
    )
    categorized = persistent_vector_store._collection.get(
        where={"document_id": document_id},
        include=["documents", "metadatas"],
    )
    assert categorized["ids"]
    assert len(categorized["ids"]) == 1
    assert categorized["ids"][0] not in previous["ids"]
    assert categorized["documents"] == ["curriculum content"]
    assert categorized["metadatas"] == [
        {
            "category": "MAJOR",
            "chunk_index": 0,
            "document_id": document_id,
            "filename": f"{document_id}.pdf",
            "page": 1,
            "source": "test",
        }
    ]

    persistent_vector_store.index(
        document_id,
        [chunk(document_id, 0, "curriculum content")],
    )
    uncategorized = persistent_vector_store._collection.get(
        where={"document_id": document_id},
        include=["documents", "metadatas"],
    )
    assert uncategorized["ids"] != categorized["ids"]
    assert uncategorized["documents"] == ["curriculum content"]
    assert uncategorized["metadatas"] == [
        {
            "chunk_index": 0,
            "document_id": document_id,
            "filename": f"{document_id}.pdf",
            "page": 1,
            "source": "test",
        }
    ]


def test_web_replacement_removes_old_source_url_after_new_write(
    persistent_vector_store: ChromaVectorStore,
):
    source_url = "https://example.edu.cn/news/1"
    persistent_vector_store.index(
        "old-content-hash",
        [
            chunk(
                "old-content-hash",
                0,
                "old web content",
                source_url=source_url,
            )
        ],
    )

    persistent_vector_store.index(
        "new-content-hash",
        [
            chunk(
                "new-content-hash",
                0,
                "new web content",
                source_url=source_url,
            ),
            chunk(
                "new-content-hash",
                1,
                "new web tail",
                source_url=source_url,
            ),
        ],
    )

    indexed = persistent_vector_store._collection.get(
        where={"source_url": source_url},
        include=["documents", "metadatas"],
    )
    assert indexed["documents"] == ["new web content", "new web tail"]
    assert {
        metadata["document_id"]
        for metadata in indexed["metadatas"]
    } == {"new-content-hash"}


def test_failed_reindex_preserves_previous_vectors(
    persistent_vector_store: ChromaVectorStore,
    monkeypatch,
):
    document_id = "training-plan"
    persistent_vector_store.index(
        document_id,
        [chunk(document_id, 0, "old content", category="ACADEMIC")],
    )

    original_add_documents = persistent_vector_store._store.add_documents

    def partially_add_documents(*, documents, ids):
        original_add_documents(documents=documents[:1], ids=ids[:1])
        raise RuntimeError("embedding service unavailable")

    monkeypatch.setattr(
        persistent_vector_store._store,
        "add_documents",
        partially_add_documents,
    )

    with pytest.raises(VectorStoreError):
        persistent_vector_store.index(
            document_id,
            [
                chunk(document_id, 0, "new content", category="MAJOR"),
                chunk(document_id, 1, "new tail", category="MAJOR"),
            ],
        )

    indexed = persistent_vector_store._collection.get(
        where={"document_id": document_id},
        include=["documents", "metadatas"],
    )
    assert indexed["documents"] == ["old content"]
    assert indexed["metadatas"] == [
        {
            "category": "ACADEMIC",
            "chunk_index": 0,
            "document_id": document_id,
            "filename": f"{document_id}.pdf",
            "page": 1,
            "source": "test",
        }
    ]
