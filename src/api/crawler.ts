import { request } from "./request";

export interface CrawlerRun {
  id: number;
  status: string;
  forceReindex: boolean;
  startedAt: string;
  finishedAt: string | null;
  totalPages: number;
  successCount: number;
  failedCount: number;
  indexedCount: number;
  unchangedCount: number;
  robotsDeniedCount: number;
  lastError: string | null;
}

export interface CrawlerStatus {
  running: boolean;
  totalWebpages: number;
  lastUpdated: string | null;
  latestRun: CrawlerRun | null;
}

export interface CrawlerTrigger {
  accepted: boolean;
  runId: number | null;
  message: string;
}

export type CrawlerScope = "EXACT_HOST" | "DIRECT_NJUPT_SUBDOMAINS";

export function getCrawlerStatus() {
  return request<CrawlerStatus>("/api/admin/crawler");
}

export function runCrawler() {
  return request<CrawlerTrigger>("/api/admin/crawler/run", { method: "POST" });
}

export function runCustomCrawler(
  seedUrl: string,
  years = 2,
  maxPages = 50,
  forceReindex = false,
  dateScope: "RECENT" | "ALL" = "RECENT",
  crawlScope: CrawlerScope = "EXACT_HOST",
) {
  return request<CrawlerTrigger>("/api/admin/crawler/custom", {
    method: "POST",
    body: JSON.stringify({
      seed_url: seedUrl,
      date_scope: dateScope,
      crawl_scope: crawlScope,
      years,
      max_pages: maxPages,
      force_reindex: forceReindex,
    }),
  });
}

export function reindexCrawler() {
  return request<CrawlerTrigger>("/api/admin/crawler/reindex", {
    method: "POST",
  });
}
