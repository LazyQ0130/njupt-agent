from fastapi import APIRouter, File, Form, Request, UploadFile, status
from fastapi.concurrency import run_in_threadpool

from app.models.schemas import (
    HealthResponse,
    IndexDocumentResponse,
    IndexWebDocumentRequest,
    RagSearchRequest,
    RagSearchResult,
)
from app.services.rag_service import RagService

router = APIRouter()


def get_rag_service(request: Request) -> RagService:
    return request.app.state.rag_service


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    settings = request.app.state.settings
    return HealthResponse(
        status="ok",
        collection=settings.chroma.collection_name,
        embedding_provider=settings.embedding.provider,
    )


@router.post(
    "/documents/index",
    response_model=IndexDocumentResponse,
    status_code=status.HTTP_201_CREATED,
)
async def index_document(
    request: Request,
    file: UploadFile = File(...),
    source: str = Form(..., min_length=1, max_length=128),
    upload_time: str | None = Form(default=None, max_length=64),
) -> IndexDocumentResponse:
    filename = file.filename or "unnamed"
    max_size = request.app.state.settings.max_upload_mb * 1024 * 1024
    content = await file.read(max_size + 1)
    await file.close()
    if len(content) > max_size:
        from fastapi import HTTPException

        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"文件不能超过 {request.app.state.settings.max_upload_mb} MB",
        )

    service = get_rag_service(request)
    return await run_in_threadpool(
        service.index_document,
        filename=filename,
        content=content,
        source=source,
        upload_time=upload_time,
    )


@router.post(
    "/documents/index-text",
    response_model=IndexDocumentResponse,
    status_code=status.HTTP_201_CREATED,
)
async def index_web_document(
    payload: IndexWebDocumentRequest,
    request: Request,
) -> IndexDocumentResponse:
    service: RagService = request.app.state.rag_service
    return await run_in_threadpool(service.index_web_document, payload)


@router.post("/rag/search", response_model=list[RagSearchResult])
async def search(
    payload: RagSearchRequest,
    request: Request,
) -> list[RagSearchResult]:
    service = get_rag_service(request)
    return await run_in_threadpool(
        service.search,
        question=payload.question,
        top_k=payload.top_k,
        category=payload.category.value if payload.category else None,
    )


@router.delete("/documents/{document_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_document(
    document_id: str,
    request: Request,
) -> None:
    service = get_rag_service(request)
    await run_in_threadpool(service.delete_document, document_id)
