package com.webcrawler.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Robots.txt Checker.
 *
 * Fetches and parses the robots.txt file for a given domain to determine
 * whether our crawler is allowed to access a specific URL.
 *
 * Design:
 *  - Results are cached in-memory (Spring @Cacheable / ConcurrentHashMap)
 *    per domain so we fetch robots.txt only once per domain per JVM lifetime.
 *  - In a production system this cache would be backed by Redis with a TTL
 *    (e.g. 24h) to handle sites that update their robots.txt over time.
 *  - Implements a simplified parser (User-agent: * + Disallow: rules only).
 *    A production crawler would use a full robots.txt parser library.
 *
 * Politeness (from README):
 *  "Checks the robots.txt for the domain. Follow rules of robots.txt
 *   to download the web pages."
 */
@Component
public class RobotsTxtChecker {

    private static final Logger log = LoggerFactory.getLogger(RobotsTxtChecker.class);

    private static final String USER_AGENT_WILDCARD = "*";
    private static final int    ROBOTS_FETCH_TIMEOUT_MS = 5000;

    /**
     * Returns {@code true} if our crawler is allowed to fetch {@code url}
     * according to the site's robots.txt.
     *
     * Returns {@code true} (allow) if robots.txt cannot be fetched,
     * following the "on error, be liberal" convention.
     */
    @Cacheable(value = "robotsCache", key = "#url.replaceAll('https?://([^/]+).*', '$1')")
    public boolean isAllowed(String url) {
        String robotsUrl = getRobotsUrl(url);
        if (robotsUrl == null) return true;

        try {
            Document doc = Jsoup.connect(robotsUrl)
                    .timeout(ROBOTS_FETCH_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .get();
            String robotsText = doc.body().text();
            return parseRobots(robotsText, url);
        } catch (IOException e) {
            log.debug("Could not fetch robots.txt for {} ({}); allowing crawl", robotsUrl, e.getMessage());
            return true; // fail-open: allow crawling if robots.txt unavailable
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Very simplified robots.txt parser.
     * Looks for "User-agent: *" blocks and checks Disallow: rules.
     *
     * For a production-grade parser consider: https://github.com/google/robotstxt-java
     */
    private boolean parseRobots(String robotsText, String targetUrl) {
        String path = extractPath(targetUrl);
        boolean inRelevantBlock = false;

        for (String rawLine : robotsText.split("[\r\n]+")) {
            String line = rawLine.trim();
            if (line.startsWith("#") || line.isBlank()) continue;

            if (line.toLowerCase().startsWith("user-agent:")) {
                String agent = line.substring("user-agent:".length()).trim();
                inRelevantBlock = agent.equals(USER_AGENT_WILDCARD);
            } else if (inRelevantBlock && line.toLowerCase().startsWith("disallow:")) {
                String disallowed = line.substring("disallow:".length()).trim();
                if (!disallowed.isBlank() && path.startsWith(disallowed)) {
                    log.debug("robots.txt disallows {} (rule: Disallow: {})", targetUrl, disallowed);
                    return false;
                }
            } else if (inRelevantBlock && line.toLowerCase().startsWith("allow:")) {
                String allowed = line.substring("allow:".length()).trim();
                if (!allowed.isBlank() && path.startsWith(allowed)) {
                    return true; // explicit allow overrides
                }
            }
        }
        return true;
    }

    private String getRobotsUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + "/robots.txt";
        } catch (Exception e) {
            return null;
        }
    }

    private String extractPath(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isBlank() ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }
}
