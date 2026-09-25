package com.webcrawler.downloader;

import com.webcrawler.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * HTML Downloader – fetches raw HTTP content using Apache HttpClient 5.
 *
 * Key features (from README spec):
 *  - Uses Apache HttpClient with a shared connection pool (configured in HttpClientConfig).
 *  - Content-type guard: only returns content when Content-Type is text/html.
 *  - Handles HTTP 3xx redirects by returning the redirect target URL.
 *  - DNS caching is provided by the JVM's built-in DNS cache (kept simple as agreed).
 *  - Returns an empty Optional on non-HTML content or errors (caller skips parsing).
 */
@Component
public class HtmlDownloader {

    private static final Logger log = LoggerFactory.getLogger(HtmlDownloader.class);

    private final CloseableHttpClient httpClient;
    private final CrawlerProperties props;

    public HtmlDownloader(CloseableHttpClient httpClient, CrawlerProperties props) {
        this.httpClient = httpClient;
        this.props      = props;
    }

    /**
     * Downloads the content at {@code url}.
     *
     * @return {@link DownloadResult} on success, empty Optional on skip/error.
     */
    public Optional<DownloadResult> download(String url) {
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        request.addHeader("Accept-Language", "en-US,en;q=0.9");
        request.addHeader("User-Agent", props.getUserAgent());

        try {
            return httpClient.execute(request, response -> handleResponse(url, response));
        } catch (IOException e) {
            log.warn("Download failed for {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DownloadResult> handleResponse(String originalUrl, ClassicHttpResponse response)
            throws IOException {

        int statusCode = response.getCode();

        // Handle redirects manually (we disabled auto-redirect in HttpClientConfig)
        if (statusCode >= 300 && statusCode < 400) {
            Header locationHeader = response.getFirstHeader("Location");
            String redirectUrl = locationHeader != null ? resolve(originalUrl, locationHeader.getValue()) : null;
            log.debug("Redirect {} -> {}", originalUrl, redirectUrl);
            return Optional.of(DownloadResult.redirect(originalUrl, statusCode, redirectUrl));
        }

        if (statusCode != 200) {
            log.debug("Non-200 status {} for {}", statusCode, originalUrl);
            return Optional.of(DownloadResult.error(originalUrl, statusCode));
        }

        // Content-type guard: only process HTML pages
        Header contentTypeHeader = response.getFirstHeader("Content-Type");
        String contentType = contentTypeHeader != null ? contentTypeHeader.getValue() : "";
        if (!contentType.contains("text/html")) {
            log.debug("Skipping non-HTML content ({}) for {}", contentType, originalUrl);
            return Optional.empty();
        }

        // Read body (capped at 2 MB to avoid memory exhaustion)
        byte[] bodyBytes = response.getEntity() != null
                ? response.getEntity().getContent().readNBytes(2 * 1024 * 1024)
                : new byte[0];
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        return Optional.of(DownloadResult.success(originalUrl, statusCode, contentType, body));
    }

    /** Resolves a potentially relative redirect location against the original URL. */
    private String resolve(String base, String location) {
        try {
            return URI.create(base).resolve(location).toString();
        } catch (Exception e) {
            return location;
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Discriminated union representing the outcome of a download attempt.
     */
    public record DownloadResult(
            String url,
            int statusCode,
            String contentType,
            String body,
            String redirectUrl,
            boolean isRedirect,
            boolean isSuccess
    ) {
        static DownloadResult success(String url, int status, String ct, String body) {
            return new DownloadResult(url, status, ct, body, null, false, true);
        }

        static DownloadResult redirect(String url, int status, String redirectTo) {
            return new DownloadResult(url, status, null, null, redirectTo, true, false);
        }

        static DownloadResult error(String url, int status) {
            return new DownloadResult(url, status, null, null, null, false, false);
        }
    }
}
