package com.webcrawler.service;

import com.webcrawler.model.CrawlerStats;
import com.webcrawler.repository.CrawledPageRepository;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Aggregates live statistics from Redis counters and PostgreSQL for the /stats endpoint.
 */
@Service
public class StatsService {

    private final CrawledPageRepository pageRepo;
    private final UrlSeenService        urlSeenService;
    private final AmqpAdmin             amqpAdmin;

    @Value("${crawler.rabbitmq.default-queue}")
    private String defaultQueue;

    public StatsService(CrawledPageRepository pageRepo,
                        UrlSeenService urlSeenService,
                        AmqpAdmin amqpAdmin) {
        this.pageRepo       = pageRepo;
        this.urlSeenService = urlSeenService;
        this.amqpAdmin      = amqpAdmin;
    }

    public CrawlerStats getStats() {
        // Approximate queue depth from RabbitMQ management API
        long queued = 0;
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(defaultQueue);
            if (info != null) queued = info.getMessageCount();
        } catch (Exception ignored) {
            // Not fatal – RabbitMQ management might be unavailable
        }

        return CrawlerStats.builder()
                .totalCrawled(pageRepo.count())
                .currentlyCrawling(urlSeenService.getInProgress())
                .queued(queued)
                .failed(urlSeenService.getFailedCount())
                .lastCrawledUrl(urlSeenService.getLastCrawledUrl())
                .domainsDiscovered(pageRepo.countDistinctDomains())
                .build();
    }
}
