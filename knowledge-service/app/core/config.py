from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class EmbeddingSettings(BaseModel):
    provider: Literal["hash", "openai-compatible"] = "hash"
    api_key: SecretStr | None = None
    base_url: str | None = None
    model: str = "text-embedding-3-small"
    hash_dimensions: int = Field(default=384, ge=64, le=4096)


class ChromaSettings(BaseModel):
    persist_directory: Path = Path("./data/chroma")
    collection_name: str = Field(
        default="njupt_knowledge",
        min_length=3,
        max_length=63,
        pattern=r"^[a-zA-Z0-9][a-zA-Z0-9._-]*[a-zA-Z0-9]$",
    )


class ChunkingSettings(BaseModel):
    chunk_size_tokens: int = Field(default=800, ge=500, le=1000)
    chunk_overlap_tokens: int = Field(default=100, ge=0, le=250)


class Settings(BaseSettings):
    service_host: str = "0.0.0.0"
    service_port: int = Field(default=8090, ge=1, le=65535)
    log_level: str = "INFO"
    max_upload_mb: int = Field(default=50, ge=1, le=200)
    default_top_k: int = Field(default=5, ge=1, le=20)
    embedding: EmbeddingSettings = EmbeddingSettings()
    chroma: ChromaSettings = ChromaSettings()
    chunking: ChunkingSettings = ChunkingSettings()

    model_config = SettingsConfigDict(
        env_file=".env",
        env_nested_delimiter="__",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
