from enum import StrEnum

from pydantic import BaseModel, Field, HttpUrl


class DocumentCategory(StrEnum):
    NEW_STUDENT = "NEW_STUDENT"
    ACADEMIC = "ACADEMIC"
    LIFE = "LIFE"
    MAJOR = "MAJOR"
    CAREER = "CAREER"
    SCHOOL_OVERVIEW = "SCHOOL_OVERVIEW"
    ORGANIZATION = "ORGANIZATION"
    RESEARCH = "RESEARCH"


class RagSearchRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    top_k: int | None = Field(default=None, ge=1, le=20)
    category: DocumentCategory | None = None


class RagSearchResult(BaseModel):
    content: str
    filename: str
    page: int = Field(ge=1)
    source: str
    source_url: str | None = None
    source_type: str | None = None
    category: DocumentCategory | None = None
    score: float = Field(ge=0.0, le=1.0)


class IndexDocumentResponse(BaseModel):
    document_id: str
    filename: str
    chunks_indexed: int
    category: DocumentCategory | None = None


class IndexWebDocumentRequest(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=50)
    source: str = Field(min_length=1, max_length=128)
    source_url: HttpUrl
    category: DocumentCategory
    crawl_time: str
    last_updated: str
    content_hash: str = Field(pattern=r"^[a-f0-9]{64}$")
    source_type: str = Field(
        default="OFFICIAL_WEBSITE",
        pattern=r"^(OFFICIAL_WEBSITE|CURATED_OFFICIAL)$",
    )


class HealthResponse(BaseModel):
    status: str
    collection: str
    embedding_provider: str


class ErrorResponse(BaseModel):
    detail: str
