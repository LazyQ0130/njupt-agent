import { request } from "./request";

export interface FrequentQuestion {
  question: string;
  count: number;
}

export interface LowQualityQuestion {
  question: string;
  occurrences: number;
  incorrectCount: number;
  averageConfidence: number;
}

export interface QualityStats {
  totalAnswers: number;
  uniqueAnonymousUsers: number;
  feedbackCount: number;
  helpfulCount: number;
  incorrectCount: number;
  helpfulRate: number;
  lowConfidenceAnswerCount: number;
  lowConfidenceIncorrectCount: number;
  highFrequencyQuestions: FrequentQuestion[];
  lowQualityQuestions: LowQualityQuestion[];
}

export interface EvaluationItem {
  id: number;
  question: string;
  category: string;
  answer: string;
  hasSource: boolean;
  sourceMatch: boolean;
  keywordMatch: boolean;
  nonEmpty: boolean;
  score: number;
  humanAccurate: boolean | null;
  reviewNote: string | null;
}

export interface EvaluationReport {
  runId: string;
  total: number;
  averageScore: number;
  sourceCoverage: number;
  sourceMatchRate: number;
  keywordMatchRate: number;
  nonEmptyRate: number;
  humanReviewedCount: number;
  humanAccuracyRate: number | null;
  gatePassed: boolean;
  gateFailures: string[];
  categoryScores: Record<string, number>;
  executedAt: string;
  results: EvaluationItem[];
}

export function getQualityStats() {
  return request<QualityStats>("/api/admin/quality/stats");
}

export function runEvaluation() {
  return request<EvaluationReport>("/api/admin/evaluation/run", {
    method: "POST",
  });
}

export function reviewEvaluation(
  resultId: number,
  accurate: boolean,
  note = "",
) {
  return request<EvaluationReport>(
    `/api/admin/evaluation/results/${resultId}/review`,
    {
      method: "PUT",
      body: JSON.stringify({ accurate, note }),
    },
  );
}
