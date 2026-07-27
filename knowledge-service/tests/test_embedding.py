import pytest

from app.core.config import EmbeddingSettings
from app.core.errors import EmbeddingConfigurationError
from app.services.embedding import HashEmbeddings, create_embeddings


def test_hash_embeddings_are_deterministic_and_normalized() -> None:
    embeddings = HashEmbeddings(dimensions=128)

    first = embeddings.embed_query("南邮转专业条件")
    second = embeddings.embed_query("南邮转专业条件")

    assert first == second
    assert len(first) == 128
    assert sum(value * value for value in first) == pytest.approx(1.0)


def test_openai_compatible_provider_requires_api_key() -> None:
    settings = EmbeddingSettings(
        provider="openai-compatible",
        api_key=None,
        model="custom-embedding",
    )

    with pytest.raises(EmbeddingConfigurationError):
        create_embeddings(settings)
