export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  sources?: Array<{
    title: string;
    type: string;
    page: number | null;
    score: number | null;
    source: string | null;
    url: string | null;
    category: string | null;
  }>;
  chatId?: number | null;
  feedback?: "HELPFUL" | "INCORRECT";
  sourceMeta?: string;
  confidence?: number;
}
