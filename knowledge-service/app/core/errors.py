class KnowledgeServiceError(Exception):
    """Base error for expected knowledge-service failures."""


class UnsupportedDocumentError(KnowledgeServiceError):
    pass


class DocumentParsingError(KnowledgeServiceError):
    pass


class EmptyDocumentError(KnowledgeServiceError):
    pass


class EmbeddingConfigurationError(KnowledgeServiceError):
    pass


class VectorStoreError(KnowledgeServiceError):
    pass
