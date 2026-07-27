import tiktoken

from app.services.document_parser import ParsedPage
from app.services.text_chunker import TextChunker


def test_chunks_by_token_and_preserves_metadata() -> None:
    chunker = TextChunker(chunk_size_tokens=500, chunk_overlap_tokens=50)
    long_text = "南京邮电大学转专业申请条件与考核安排。" * 400

    chunks = chunker.split(
        [ParsedPage(page=3, text=long_text)],
        filename="转专业办法.pdf",
        source="教务处",
        upload_time="2026-07-25T12:00:00+08:00",
        document_id="document-1",
    )

    encoding = tiktoken.get_encoding("cl100k_base")
    assert len(chunks) > 1
    assert all(len(encoding.encode(chunk.page_content)) <= 500 for chunk in chunks)
    assert chunks[0].metadata["filename"] == "转专业办法.pdf"
    assert chunks[0].metadata["source"] == "教务处"
    assert chunks[0].metadata["page"] == 3
    assert chunks[0].metadata["chunk_index"] == 0
    assert "start_index" in chunks[0].metadata
    assert "文件名：" in chunks[0].page_content
    assert "页码：3" in chunks[0].page_content
