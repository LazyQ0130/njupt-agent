import hashlib
import math
import re
from collections.abc import Iterable

from langchain_core.embeddings import Embeddings
from langchain_openai import OpenAIEmbeddings

from app.core.config import EmbeddingSettings
from app.core.errors import EmbeddingConfigurationError


class HashEmbeddings(Embeddings):
    """Deterministic local embedding for development and automated tests only."""

    def __init__(self, dimensions: int = 384) -> None:
        self._dimensions = dimensions

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._embed(text)

    def _embed(self, text: str) -> list[float]:
        vector = [0.0] * self._dimensions
        for token in self._tokens(text):
            digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
            value = int.from_bytes(digest, byteorder="big", signed=False)
            index = value % self._dimensions
            vector[index] += 1.0 if value & 1 else -1.0

        norm = math.sqrt(sum(item * item for item in vector))
        if norm == 0:
            return vector
        return [item / norm for item in vector]

    @staticmethod
    def _tokens(text: str) -> Iterable[str]:
        normalized = text.lower()
        latin_tokens = re.findall(r"[a-z0-9]+", normalized)
        chinese = re.findall(r"[\u3400-\u9fff]", normalized)
        chinese_bigrams = [
            "".join(chinese[index : index + 2])
            for index in range(max(0, len(chinese) - 1))
        ]
        return [*latin_tokens, *chinese, *chinese_bigrams]


def create_embeddings(settings: EmbeddingSettings) -> Embeddings:
    if settings.provider == "hash":
        return HashEmbeddings(settings.hash_dimensions)

    if not settings.api_key or not settings.api_key.get_secret_value().strip():
        raise EmbeddingConfigurationError(
            "openai-compatible Embedding 需要配置 EMBEDDING__API_KEY"
        )
    if not settings.model.strip():
        raise EmbeddingConfigurationError("Embedding model 不能为空")

    parameters: dict[str, object] = {
        "api_key": settings.api_key.get_secret_value(),
        "model": settings.model,
    }
    if settings.base_url and settings.base_url.strip():
        parameters["base_url"] = settings.base_url.rstrip("/")
    return OpenAIEmbeddings(**parameters)
