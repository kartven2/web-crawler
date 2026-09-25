package com.webcrawler.frontier;

import com.webcrawler.config.CrawlerProperties;
import com.webcrawler.model.CrawlTask;
import com.webcrawler.service.UrlSeenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * URL Frontier – the heart of the crawl scheduling system.
 *
 * Responsibilities:
 *  1. Accept candidate URLs and decide whether to enqueue them.
 *  2. Route URLs to a per-domain RabbitMQ queue (creates queue if needed).
 *  3. Enforce politeness limits via UrlSeenService counters.
 *
 * Design (from README):
 *  "Queue router: each queue only contains URLs from the same host.
 *   Maintain a mapping table in Redis mapping each host to a queue."
 *
 *  We use RabbitMQ's topic exchange for the routing:
 *      routing key = crawler.domain.<sanitised-hostname>
 *  Each queue is named:  crawler.url.<sanitised-hostname>
 *  The default queue (crawler.url.default) catches everything else.
 */
@Service
public class UrlFrontierService {

    private static final Logger log = LoggerFactory.getLogger(UrlFrontierService.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final UrlSeenService urlSeenService;
    private final CrawlerProperties props;

    @Value("${crawler.rabbitmq.exchange}")
    private String exchange;

    @Value("${crawler.rabbitmq.routing-key-prefix}")
    private String routingKeyPrefix;

    public UrlFrontierService(RabbitTemplate rabbitTemplate,
                              RabbitAdmin rabbitAdmin,
                              UrlSeenService urlSeenService,
                              CrawlerProperties props) {
        this.rabbitTemplate  = rabbitTemplate;
        this.rabbitAdmin     = rabbitAdmin;
        this.urlSeenService  = urlSeenService;
        this.props           = props;
    }

    /**
     * Enqueues a single URL into the frontier after applying all filters.
     *
     * @param url       URL to enqueue
     * @param depth     depth from seed
     * @param parentUrl URL that contained this link (for tracing)
     */
    public void enqueue(String url, int depth, String parentUrl) {
        // 1. Depth guard
        if (depth > props.getMaxDepth()) {
            log.debug("Skipping (max depth): {}", url);
            return;
        }

        // 2. Deduplication – atomic Redis SETNX
        if (!urlSeenService.markAsSeen(url)) {
            log.debug("Skipping (already seen): {}", url);
            return;
        }

        String domain = extractHost(url);
        if (domain == null) {
            log.warn("Skipping (invalid URL): {}", url);
            return;
        }

        // 3. Per-domain circuit-breaker
        long domainCount = urlSeenService.incrementDomainCount(domain);
        if (domainCount > props.getMaxUrlsPerDomain()) {
            log.debug("Skipping (domain limit reached for {}): {}", domain, url);
            return;
        }

        // 4. Ensure per-domain queue exists
        ensureDomainQueue(domain);

        // 5. Publish to RabbitMQ
        CrawlTask task = CrawlTask.builder()
                .url(url)
                .depth(depth)
                .domain(domain)
                .parentUrl(parentUrl)
                .enqueuedAt(Instant.now().toEpochMilli())
                .build();

        String routingKey = routingKeyPrefix + sanitise(domain);
        rabbitTemplate.convertAndSend(exchange, routingKey, task);
        log.info("Enqueued [depth={}] {}", depth, url);
    }

    /** Convenience method to enqueue multiple URLs at once. */
    public void enqueueAll(List<String> urls, int depth, String parentUrl) {
        urls.forEach(url -> enqueue(url, depth, parentUrl));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Lazily creates a durable per-domain queue and binds it to the
     * topic exchange with the domain-specific routing key.
     *
     * In a production system this mapping would be stored in Redis so that
     * multiple crawler instances can share queue declarations without duplication.
     * RabbitMQ's idempotent queue-declare makes repeated calls safe.
     */
    private void ensureDomainQueue(String domain) {
        String queueName  = "crawler.url." + sanitise(domain);
        String routingKey = routingKeyPrefix + sanitise(domain);

        Queue queue = QueueBuilder.durable(queueName).build();
        rabbitAdmin.declareQueue(queue);

        TopicExchange exchange = new TopicExchange(this.exchange);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        rabbitAdmin.declareBinding(binding);
    }

    /** Extracts the hostname from a URL, returns null on parse failure. */
    public static String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** Replaces dots with underscores so the hostname is valid as a queue name suffix. */
    private String sanitise(String domain) {
        return domain.replace(".", "_").replace(":", "_");
    }
}
