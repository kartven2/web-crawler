package com.webcrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the scalable web crawler.
 *
 * Architecture overview:
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  REST API  ──►  URL Frontier (RabbitMQ)                 │
 *  │                    │                                     │
 *  │                    ▼                                     │
 *  │           CrawlerWorker (N threads)                      │
 *  │              │          │                                │
 *  │     robots.txt check   URL dedup (Redis)                 │
 *  │              │                                           │
 *  │       HtmlDownloader (Apache HttpClient)                 │
 *  │              │                                           │
 *  │       ContentParser (Jsoup)                              │
 *  │         │        │                                       │
 *  │   new URLs     Crawled content ──► PostgreSQL            │
 *  │      │                                                   │
 *  │    UrlFilter ──► back to RabbitMQ frontier              │
 *  └─────────────────────────────────────────────────────────┘
 */
@SpringBootApplication
@EnableAsync
public class WebCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebCrawlerApplication.class, args);
    }
}
