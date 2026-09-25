package com.webcrawler.parser;

import com.webcrawler.filter.UrlFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Content Parser – uses Jsoup to extract links and metadata from raw HTML.
 *
 * Responsibilities (from README):
 *  - Parse HTML and extract all anchor hrefs.
 *  - Resolve relative URLs to absolute form.
 *  - Run extracted URLs through UrlFilter before returning.
 *  - Extract page title and plain-text body for storage.
 *
 * Why Jsoup?
 *  - Industry-standard Java HTML parser with CSS selector support.
 *  - Handles malformed/partial HTML gracefully (important for real-world pages).
 *  - Pure Java, no native dependencies.
 */
@Component
public class ContentParser {

    private static final Logger log = LoggerFactory.getLogger(ContentParser.class);

    private final UrlFilter urlFilter;

    public ContentParser(UrlFilter urlFilter) {
        this.urlFilter = urlFilter;
    }

    /**
     * Parses the HTML document and returns extracted data.
     *
     * @param baseUrl the URL this HTML was fetched from (used to resolve relative links)
     * @param html    raw HTML string
     * @return {@link ParseResult} containing title, text snippet, and filtered outbound URLs
     */
    public ParseResult parse(String baseUrl, String html) {
        Document doc = Jsoup.parse(html, baseUrl);

        String title = doc.title();

        // Extract visible text (skip scripts/styles) — cap at 64 KB for DB storage
        String bodyText = doc.body() != null ? doc.body().text() : "";
        if (bodyText.length() > 65536) {
            bodyText = bodyText.substring(0, 65536);
        }

        List<String> links = extractLinks(doc, baseUrl);

        return new ParseResult(title, bodyText, links);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private List<String> extractLinks(Document doc, String baseUrl) {
        Elements anchors = doc.select("a[href]");
        List<String> result = new ArrayList<>(anchors.size());

        for (Element anchor : anchors) {
            String href = anchor.attr("abs:href"); // Jsoup resolves relative → absolute
            if (href.isBlank()) continue;

            // Normalise fragment-only links
            int hashIdx = href.indexOf('#');
            if (hashIdx > 0) href = href.substring(0, hashIdx);
            if (href.isBlank()) continue;

            // Apply URL filter rules
            if (urlFilter.isAllowed(href)) {
                result.add(href);
            }
        }

        log.debug("Extracted {} valid links from {}", result.size(), baseUrl);
        return result;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Holds the output of a single parse operation.
     *
     * @param title    page &lt;title&gt; tag content
     * @param bodyText plain-text body (≤64 KB)
     * @param links    filtered absolute outbound URLs discovered on the page
     */
    public record ParseResult(String title, String bodyText, List<String> links) {}
}
