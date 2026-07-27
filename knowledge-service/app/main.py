from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.api.routes import router
from app.core.config import Settings, get_settings
from app.core.errors import (
    DocumentParsingError,
    EmbeddingConfigurationError,
    EmptyDocumentError,
    UnsupportedDocumentError,
    VectorStoreError,
)
from app.services.document_parser import DocumentParser
from app.services.document_classifier import DocumentClassifier
from app.services.embedding import create_embeddings
from app.services.rag_service import RagService
from app.services.text_chunker import TextChunker
from app.services.vector_store import ChromaVectorStore


def build_rag_service(settings: Settings) -> RagService:
    embeddings = create_embeddings(settings.embedding)
    vector_store = ChromaVectorStore(
        persist_directory=settings.chroma.persist_directory,
        collection_name=settings.chroma.collection_name,
        embeddings=embeddings,
    )
    return RagService(
        parser=DocumentParser(),
        chunker=TextChunker(
            chunk_size_tokens=settings.chunking.chunk_size_tokens,
            chunk_overlap_tokens=settings.chunking.chunk_overlap_tokens,
        ),
        vector_store=vector_store,
        default_top_k=settings.default_top_k,
        classifier=DocumentClassifier(),
    )


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.settings = resolved_settings
        app.state.rag_service = build_rag_service(resolved_settings)
        yield

    application = FastAPI(
        title="NJUPT Knowledge Service",
        version="0.1.0",
        description="南邮校园资料解析、向量索引与检索服务",
        lifespan=lifespan,
    )

    @application.exception_handler(UnsupportedDocumentError)
    @application.exception_handler(DocumentParsingError)
    @application.exception_handler(EmptyDocumentError)
    async def handle_document_error(
        request: Request,
        exception: Exception,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            content={"detail": str(exception)},
        )

    @application.exception_handler(EmbeddingConfigurationError)
    @application.exception_handler(VectorStoreError)
    async def handle_dependency_error(
        request: Request,
        exception: Exception,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"detail": str(exception)},
        )

    application.include_router(router)
    return application


app = create_app()
