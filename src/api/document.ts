import { request } from "./request";

export type DocumentStatus =
  | "UPLOADING"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED";

export interface KnowledgeDocument {
  id: number;
  title: string;
  filename: string;
  source: string;
  type: string;
  status: DocumentStatus;
  fileSize: number;
  contentType: string | null;
  sourceType: "OFFICIAL_WEBSITE" | "CURATED_OFFICIAL" | "UPLOADED_FILE";
  sourceUrl: string | null;
  category:
    | "NEW_STUDENT"
    | "ACADEMIC"
    | "LIFE"
    | "MAJOR"
    | "CAREER"
    | "SCHOOL_OVERVIEW"
    | "ORGANIZATION"
    | "RESEARCH"
    | null;
  crawlTime: string | null;
  lastUpdated: string | null;
  contentHash: string | null;
  createdTime: string;
}

export interface DocumentDetail extends KnowledgeDocument {
  content: string | null;
}

export interface DocumentReindexResult {
  scheduledCount: number;
  skippedCount: number;
}

export type DocumentCategory = NonNullable<KnowledgeDocument["category"]>;

export interface CuratedDocumentInput {
  title: string;
  content: string;
  source: string;
  sourceUrl: string;
  category: DocumentCategory;
  publishedTime?: string;
  verifiedTime: string;
}

export function getDocuments() {
  return request<KnowledgeDocument[]>("/api/documents");
}

export function getDocument(id: number) {
  return request<DocumentDetail>(`/api/documents/${id}`);
}

export function uploadDocument(
  file: File,
  metadata: { title?: string; source?: string } = {},
) {
  const formData = new FormData();
  formData.append("file", file);
  if (metadata.title) formData.append("title", metadata.title);
  if (metadata.source) formData.append("source", metadata.source);

  return request<KnowledgeDocument>("/api/admin/documents/upload", {
    method: "POST",
    body: formData,
  });
}

export function reindexUploadedDocuments() {
  return request<DocumentReindexResult>("/api/admin/documents/reindex", {
    method: "POST",
  });
}

export function upsertCuratedDocument(input: CuratedDocumentInput) {
  return request<KnowledgeDocument>("/api/admin/documents/curated", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deleteDocument(id: number, suppressReingest = true) {
  return request<void>(
    `/api/admin/documents/${id}?suppressReingest=${suppressReingest}`,
    { method: "DELETE" },
  );
}
