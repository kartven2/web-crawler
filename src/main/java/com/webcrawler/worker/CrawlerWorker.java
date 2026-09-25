package com.webcrawler.worker;

import com.webcrawler.config.CrawlerProperties;
import com.webcrawler.downloader.HtmlDownloader;
import com.webcrawler.frontier.UrlFrontierService;
import com.webcrawler.model.CrawlTask;
import com.webcrawler.model.CrawledPage;
import com.webcrawler.parser.ContentParser;
import com.webcrawler.parser.RobotsTxtChecker;
import com.webcrawler.repository.CrawledPageRepository;
import com.webcrawler.service.UrlSeenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Crawler Worker – the core consumer of the URL Frontier.
 *
 * Each worker thread:
 *  1. Receives a {@link CrawlTask} from RabbitMQ (one per message).
 *  2. Checks robots.txt for the domain.
 *  3. Enforces per-domain politeness delay (crawl-delay).
 *  4. Downloads the page using {@link HtmlDownloader}.
 *  5. Parses HTML with {@link ContentParser} to extract links + metadata.
 *  6. Stores the crawled page in PostgreSQL via {@link CrawledPageRepository}.
 *  7. Pushes newly discovered URLs back to the frontier.
 *
 * Concurrency:
 *  The number of concurrent workers is controlled by the
 *  {@code SimpleRabbitListenerContainerFactory} bean in {@link com.webcrawler.config.RabbitMQConfig},
 *  which sets {@code concurrentConsumers = app.crawler.worker-count}.
 *
 *  PrefetchCount=1 ensures fair dispatch — each worker pulls one task at a time.
 *
 * Error handling:
 *  - Any unchecked exception causes the message to be NACK'd and dead-lettered
 *    (or re-queued depending on RabbitMQ configuration).
 *  - We catch expected errors (HTTP failures, parse errors) explicitly and
 *    record them in Redis without crashing the worker.
 */
@Component
public class CrawlerWorker {

    private static final Logger log = LoggerFactory.getLogger(CrawlerWorker.class);

    private final HtmlDownloader       downloader;
    private final ContentParser        parser;
    private final RobotsTxtChecker     robotsChecker;
    private final UrlFrontierService   frontier;
    private final CrawledPageRepository pageRepo;
    private final UrlSeenService       urlSeenService;
    private final CrawlerProperties    props;

    public CrawlerWorker(HtmlDownloader downloader,
                         ContentParser parser,
                         RobotsTxtChecker robotsChecker,
                         UrlFrontierService frontier,
                         CrawledPageRepository pageRepo,
                         UrlSeenService urlSeenService,
                         CrawlerProperties props) {
        this.downloader     = downloader;
        this.parser         = parser;
        this.robotsChecker  = robotsChecker;
        this.frontier       = frontier;
        this.pageRepo       = pageRepo;
        this.urlSeenService = urlSeenService;
        this.props          = props;
    }

    /**
     * Main message handler – called for every URL dequeued from RabbitMQ.
     *
     * The {@code queues} value targets the default queue; per-domain queues
     * are also consumed because they share the same listener container factory.
     */
    @RabbitListener(queues = "${crawler.rabbitmq.default-queue}",
                    containerFactory = "rabbitListenerContainerFactory")
    public void processTask(CrawlTask task) {
        String url    = task.getUrl();
        String domain = task.getDomain();
        int    depth  = task.getDepth();

        log.info("▶ Worker processing [depth={}]: {}", depth, url);
        urlSeenService.incrementInProgress();

        try {
            // Step 1: robots.txt check
            if (!robotsChecker.isAllowed(url)) {
                log.info("robots.txt disallows: {}", url);
                return;
            }

            // Step 2: Per-domain politeness – sleep if needed
            long waitMs = urlSeenService.getWaitTimeMs(domain, props.getCrawlDelayMs());
            if (waitMs > 0) {
                log.debug("Politeness delay {}ms for domain: {}", waitMs, domain);
                Thread.sleep(waitMs);
            }

            // Step 3: Download
            var resultOpt = downloader.download(url);
            if (resultOpt.isEmpty()) {
                log.debug("Skipped (no HTML content): {}", url);
                return;
            }

            var result = resultOpt.get();

            // Step 3a: Handle redirects – re-enqueue the redirect target
            if (result.isRedirect()) {
                if (result.redirectUrl() != null) {
                    log.debug("Following redirect: {} -> {}", url, result.redirectUrl());
                    frontier.enqueue(result.redirectUrl(), depth, url);
                }
                return;
            }

            // Step 3b: Record failure for non-200 responses
            if (!result.isSuccess()) {
                urlSeenService.recordFailure(url);
                return;
            }

            // Step 4: Parse HTML
            ContentParser.ParseResult parsed = parser.parse(url, result.body());

            // Step 5: Store in PostgreSQL
            CrawledPage page = CrawledPage.builder()
                    .url(url)
                    .domain(domain)
                    .statusCode(result.statusCode())
                    .contentType(result.contentType())
                    .title(parsed.title())
                    .bodyText(parsed.bodyText())
                    .outboundLinkCount(parsed.links().size())
                    .depth(depth)
                    .crawledAt(Instant.now())
                    .build();

            pageRepo.save(page);

            // Update last-crawled tracker (domain politeness + stats)
            urlSeenService.recordCrawl(domain);
            urlSeenService.setLastCrawledUrl(url);
            log.info("✔ Crawled [depth={}, links={}]: {}", depth, parsed.links().size(), url);

            // Step 6: Enqueue discovered links into the URL frontier
            frontier.enqueueAll(parsed.links(), depth + 1, url);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Worker interrupted while processing: {}", url);
            urlSeenService.recordFailure(url);
        } catch (Exception e) {
            log.error("Worker error for {}: {}", url, e.getMessage(), e);
            urlSeenService.recordFailure(url);
            // Re-throw to trigger RabbitMQ NACK / dead-letter routing
            throw new RuntimeException("CrawlerWorker failed for " + url, e);
        } finally {
            urlSeenService.decrementInProgress();
        }
    }
}
