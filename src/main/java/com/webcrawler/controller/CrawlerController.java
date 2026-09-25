package com.webcrawler.controller;

import com.webcrawler.frontier.UrlFrontierService;
import com.webcrawler.model.CrawledPage;
import com.webcrawler.model.CrawlerStats;
import com.webcrawler.repository.CrawledPageRepository;
import com.webcrawler.service.StatsService;
import com.webcrawler.service.UrlSeenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the web crawler.
 *
 * All endpoints are under /api/v1 as documented in the README.
 *
 * Endpoints:
 *  POST /api/v1/seed          – submit seed URLs to the frontier
 *  GET  /api/v1/stats         – live crawl statistics
 *  GET  /api/v1/crawled       – list crawled pages (without body text)
 *  GET  /api/v1/crawled/{id}  – get full details of a single crawled page
 *  GET  /api/v1/health        – simple health check
 */
@RestController
@RequestMapping("/api/v1")
public class CrawlerController {

    private static final Logger log = LoggerFactory.getLogger(CrawlerController.class);

    private final UrlFrontierService    frontier;
    private final StatsService          statsService;
    private final CrawledPageRepository pageRepo;
    private final UrlSeenService        urlSeenService;

    public CrawlerController(UrlFrontierService frontier,
                             StatsService statsService,
                             CrawledPageRepository pageRepo,
                             UrlSeenService urlSeenService) {
        this.frontier       = frontier;
        this.statsService   = statsService;
        this.pageRepo       = pageRepo;
        this.urlSeenService = urlSeenService;
    }

    // ── Seed ──────────────────────────────────────────────────────────────────

    /**
     * Accepts a JSON array of seed URLs and enqueues them at depth 0.
     *
     * Example:
     *   curl -X POST -H "Content-Type: application/json" \
     *     -d '["https://www.geeksforgeeks.org"]' \
     *     http://localhost:8080/api/v1/seed
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> addSeeds(@RequestBody List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No URLs provided"));
        }
        log.info("Received {} seed URL(s) via API", urls.size());
        frontier.enqueueAll(urls, 0, "api-seed");
        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "count", urls.size(),
                "urls", urls
        ));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Returns a live snapshot of crawl statistics.
     *
     * Example:
     *   curl http://localhost:8080/api/v1/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<CrawlerStats> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }

    // ── Crawled Pages ─────────────────────────────────────────────────────────

    /**
     * Returns summaries of all crawled pages (title, URL, domain, status, depth).
     * Body text is omitted for performance.
     *
     * Example:
     *   curl http://localhost:8080/api/v1/crawled
     */
    @GetMapping("/crawled")
    public ResponseEntity<List<CrawledPage>> getCrawledPages() {
        return ResponseEntity.ok(pageRepo.findAllSummaries());
    }

    /**
     * Returns full details (including body text) for a single crawled page.
     *
     * Example:
     *   curl http://localhost:8080/api/v1/crawled/42
     */
    @GetMapping("/crawled/{id}")
    public ResponseEntity<CrawledPage> getCrawledPage(@PathVariable Long id) {
        return pageRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Health ────────────────────────────────────────────────────────────────

    /**
     * Lightweight health check.
     *
     * Example:
     *   curl http://localhost:8080/api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "web-crawler"));
    }
}
