package com.webcrawler.service;

import com.webcrawler.config.CrawlerProperties;
import com.webcrawler.frontier.UrlFrontierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Crawler Starter – seeds the URL frontier when the application boots.
 *
 * Listens for {@link ApplicationReadyEvent} (fired after the full Spring context
 * is initialised and all beans are ready) to ensure RabbitMQ, Redis, and
 * PostgreSQL connections are established before we enqueue anything.
 *
 * The seed URL is read from {@code app.crawler.start-url} in application.yml.
 * Additional seeds can be pushed later via the /api/v1/seed REST endpoint.
 */
@Service
public class CrawlerStarterService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerStarterService.class);

    private final UrlFrontierService frontier;
    private final CrawlerProperties  props;

    public CrawlerStarterService(UrlFrontierService frontier, CrawlerProperties props) {
        this.frontier = frontier;
        this.props    = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String seedUrl = props.getStartUrl();
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║       Web Crawler starting up                ║");
        log.info("║  Seed URL   : {}  ", seedUrl);
        log.info("║  Max Depth  : {}                             ", props.getMaxDepth());
        log.info("║  Workers    : {}                             ", props.getWorkerCount());
        log.info("║  Crawl Delay: {} ms                         ", props.getCrawlDelayMs());
        log.info("╚══════════════════════════════════════════════╝");

        frontier.enqueue(seedUrl, 0, null);
    }
}
