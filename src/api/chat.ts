import { request } from "./request";

export interface ChatSource {
  title: string;
  type: string;
  page: number | null;
  score: number | null;
  source: string | null;
  url: string | null;
  category: string | null;
}

export interface ChatAnswer {
  answer: string;
  sources: ChatSource[];
  confidence: number;
  chatId: number | null;
}

export interface ChatHistoryItem {
  id: number;
  userId: number | null;
  question: string;
  answer: string;
  sources: ChatSource[];
  confidence: number;
  createdTime: string;
}

export function askQuestion(question: string) {
  return request<ChatAnswer>("/api/chat/ask", {
    method: "POST",
    body: JSON.stringify({ question }),
  });
}

export function getChatHistory() {
  return request<ChatHistoryItem[]>("/api/chat/history");
}

export interface Conversation {
  id: number;
  userId: number | null;
  title: string;
  createdTime: string;
  updatedTime: string;
}

export interface ConversationAnswer extends ChatAnswer {
  conversationId: number;
  messageId: number;
  chatId: number;
  createdTime: string;
}

export interface ConversationMessage {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  sources: ChatSource[];
  chatId: number | null;
  confidence: number | null;
  createdTime: string;
}

export interface ConversationDetail extends Conversation {
  messages: ConversationMessage[];
}

export type FeedbackType = "HELPFUL" | "INCORRECT";

export function createConversation() {
  return request<Conversation>("/api/chat/conversation", { method: "POST" });
}

export function sendConversationMessage(
  conversationId: number,
  question: string,
) {
  return request<ConversationAnswer>("/api/chat/message", {
    method: "POST",
    body: JSON.stringify({ conversationId, question }),
  });
}

export function getConversation(id: number) {
  return request<ConversationDetail>(`/api/chat/conversation/${id}`);
}

export function submitAnswerFeedback(
  chatId: number,
  feedback: FeedbackType,
  reason?: string,
) {
  return request<{
    id: number;
    chatId: number;
    feedback: FeedbackType;
    reason: string | null;
    createdTime: string;
  }>("/api/chat/feedback", {
    method: "POST",
    body: JSON.stringify({ chatId, feedback, reason }),
  });
}
