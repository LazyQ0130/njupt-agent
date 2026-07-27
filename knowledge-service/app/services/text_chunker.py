from collections.abc import Iterable

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.services.document_parser import ParsedPage


class TextChunker:
    def __init__(self, chunk_size_tokens: int, chunk_overlap_tokens: int) -> None:
        self._splitter = RecursiveCharacterTextSplitter.from_tiktoken_encoder(
            encoding_name="cl100k_base",
            chunk_size=chunk_size_tokens,
            chunk_overlap=chunk_overlap_tokens,
            add_start_index=True,
            separators=[
                "\n\n",
                "\n",
                "。",
                "！",
                "？",
                "；",
                "，",
                ".",
                "!",
                "?",
                ";",
                ",",
                " ",
                "",
            ],
        )

    def split(
        self,
        pages: Iterable[ParsedPage],
        *,
        filename: str,
        source: str,
        upload_time: str,
        document_id: str,
        extra_metadata: dict[str, str | int | float | bool] | None = None,
    ) -> list[Document]:
        chunks: list[Document] = []
        for page in pages:
            metadata: dict[str, str | int | float | bool] = {
                "filename": filename,
                "source": source,
                "upload_time": upload_time,
                "page": page.page,
                "document_id": document_id,
            }
            if extra_metadata:
                metadata.update(extra_metadata)
            category = str(metadata.get("category", "未分类"))
            enriched_text = (
                f"文件名：{filename}\n"
                f"来源：{source}\n"
                f"分类：{category}\n"
                f"页码：{page.page}\n"
                f"正文：\n{page.text}"
            )
            page_document = Document(
                page_content=enriched_text,
                metadata=metadata,
            )
            chunks.extend(self._splitter.split_documents([page_document]))

        for index, chunk in enumerate(chunks):
            chunk.metadata["chunk_index"] = index
        return chunks
