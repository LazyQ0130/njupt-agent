from langchain_core.documents import Document

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
