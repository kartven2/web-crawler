package com.webcrawler.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * URL Filter – decides whether a URL should be crawled at all.
 *
 * Filters applied in order:
 *  1. Scheme check        – only http/https allowed.
 *  2. Extension blacklist – skip binary files, archives, media, etc.
 *  3. Content-type check  – only crawl HTML (applied post-download in HtmlDownloader).
 *  4. Domain blacklist    – configurable set of domains to skip entirely.
 *  5. URL pattern         – rejects obviously malformed or non-useful URLs.
 *
 * Design note: This component is intentionally stateless and side-effect-free,
 * making it easy to unit-test and extend with new rules.
 */
@Component
public class UrlFilter {

    private static final Logger log = LoggerFactory.getLogger(UrlFilter.class);

    /** File extensions we never want to crawl (binary/media content). */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".zip", ".tar", ".gz", ".rar", ".7z",
            ".mp3", ".mp4", ".avi", ".mkv", ".mov", ".wav",
            ".css", ".js", ".json", ".xml", ".rss",
            ".exe", ".dmg", ".pkg", ".deb", ".rpm"
    );

    /** Domains to skip completely (ad networks, tracking, etc.). */
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "facebook.com", "twitter.com", "instagram.com",
            "cdn.ampproject.org"
    );

    /** Reject URLs that contain these substrings (logout links, etc.). */
    private static final Pattern BLOCKED_PATTERNS = Pattern.compile(
            "(?i)(logout|signout|delete|unsubscribe|mailto:|javascript:|#)"
    );

    /**
     * Returns {@code true} if the URL should be crawled.
     *
     * @param url candidate URL string
     */
    public boolean isAllowed(String url) {
        if (url == null || url.isBlank()) return false;

        try {
            URI uri = URI.create(url.trim());

            // 1. Scheme check
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return false;
            }

            // 2. Extension blacklist
            String path = uri.getPath().toLowerCase();
            for (String ext : BLOCKED_EXTENSIONS) {
                if (path.endsWith(ext)) {
                    log.debug("Filtered (extension {}): {}", ext, url);
                    return false;
                }
            }

            // 3. Domain blacklist
            String host = uri.getHost();
            if (host == null) return false;
            for (String blocked : BLOCKED_DOMAINS) {
                if (host.endsWith(blocked)) {
                    log.debug("Filtered (blocked domain {}): {}", blocked, url);
                    return false;
                }
            }

            // 4. Pattern blacklist
            if (BLOCKED_PATTERNS.matcher(url).find()) {
                log.debug("Filtered (pattern match): {}", url);
                return false;
            }

            return true;

        } catch (IllegalArgumentException e) {
            log.debug("Filtered (invalid URI): {}", url);
            return false;
        }
    }
}
