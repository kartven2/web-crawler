package com.webcrawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;

/**
 * URL Deduplication Service backed by Redis.
 *
 * Two Redis data structures are used:
 *
 *  1. visited:<url-sha256>  – SET key (no value), expires never.
 *     Used to avoid crawling the same URL twice across all workers.
 *
 *  2. domain:lastcrawl:<domain> – STRING, stores last-crawl Unix epoch ms.
 *     Used to enforce per-domain politeness (crawl-delay).
 *
 * Why Redis?
 *  - O(1) SET and GET operations.
 *  - Shared across all crawler instances (horizontal scale-out).
 *  - Atomic SETNX prevents race conditions between concurrent workers.
 */
@Service
public class UrlSeenService {

    private static final Logger log = LoggerFactory.getLogger(UrlSeenService.class);

    private static final String VISITED_KEY_PREFIX   = "visited:";
    private static final String LAST_CRAWL_PREFIX    = "domain:lastcrawl:";
    private static final String DOMAIN_COUNT_PREFIX  = "domain:count:";
    private static final String FAILED_SET_KEY       = "crawler:failed";
    private static final String FAILED_COUNT_KEY     = "crawler:failedcount";
    private static final String CRAWLING_COUNT_KEY   = "crawler:inprogress";
    private static final String LAST_URL_KEY         = "crawler:lasturl";

    private final StringRedisTemplate redis;

    public UrlSeenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ── URL Deduplication ─────────────────────────────────────────────────────

    /**
     * Atomically marks a URL as seen.
     * @return {@code true} if this URL was NOT seen before (new); {@code false} if already visited.
     */
    public boolean markAsSeen(String url) {
        String key = VISITED_KEY_PREFIX + normalise(url);
        // SETNX (set-if-not-exists) is atomic – safe for concurrent workers
        Boolean isNew = redis.opsForValue().setIfAbsent(key, "1");
        return Boolean.TRUE.equals(isNew);
    }

    /** Returns {@code true} if this URL has already been crawled or is in-flight. */
    public boolean isSeen(String url) {
        return Boolean.TRUE.equals(redis.hasKey(VISITED_KEY_PREFIX + normalise(url)));
    }

    // ── Per-domain Politeness ─────────────────────────────────────────────────

    /**
     * Returns the number of milliseconds the caller must wait before
     * issuing the next request to {@code domain}.
     * Returns 0 if the domain is ready.
     */
    public long getWaitTimeMs(String domain, long crawlDelayMs) {
        String key = LAST_CRAWL_PREFIX + domain;
        String lastStr = redis.opsForValue().get(key);
        if (lastStr == null) return 0;
        long elapsed = System.currentTimeMillis() - Long.parseLong(lastStr);
        return Math.max(0, crawlDelayMs - elapsed);
    }

    /** Records that {@code domain} was just crawled (sets last-crawl timestamp). */
    public void recordCrawl(String domain) {
        redis.opsForValue().set(LAST_CRAWL_PREFIX + domain,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofHours(1));
    }

    // ── Per-domain URL count (circuit-breaker) ────────────────────────────────

    public long incrementDomainCount(String domain) {
        Long count = redis.opsForValue().increment(DOMAIN_COUNT_PREFIX + domain);
        return count == null ? 0 : count;
    }

    public long getDomainCount(String domain) {
        String val = redis.opsForValue().get(DOMAIN_COUNT_PREFIX + domain);
        return val == null ? 0 : Long.parseLong(val);
    }

    // ── Global counters (used by stats endpoint) ──────────────────────────────

    public void incrementInProgress() {
        redis.opsForValue().increment(CRAWLING_COUNT_KEY);
    }

    public void decrementInProgress() {
        redis.opsForValue().decrement(CRAWLING_COUNT_KEY);
    }

    public long getInProgress() {
        String val = redis.opsForValue().get(CRAWLING_COUNT_KEY);
        return val == null ? 0 : Long.parseLong(val);
    }

    public void recordFailure(String url) {
        redis.opsForSet().add(FAILED_SET_KEY, url);
        redis.opsForValue().increment(FAILED_COUNT_KEY);
    }

    public long getFailedCount() {
        String val = redis.opsForValue().get(FAILED_COUNT_KEY);
        return val == null ? 0 : Long.parseLong(val);
    }

    public void setLastCrawledUrl(String url) {
        redis.opsForValue().set(LAST_URL_KEY, url);
    }

    public String getLastCrawledUrl() {
        return redis.opsForValue().get(LAST_URL_KEY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Normalises a URL to a canonical form before hashing.
     * Strips trailing slashes and lowercases the scheme+host.
     */
    private String normalise(String url) {
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (path != null && path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
            return (uri.getScheme() + "://" + uri.getHost() + path + query).toLowerCase();
        } catch (Exception e) {
            return url.toLowerCase();
        }
    }
}
