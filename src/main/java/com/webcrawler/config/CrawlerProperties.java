package com.webcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Strongly-typed binding for all {@code app.crawler.*} properties
 * defined in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "app.crawler")
public class CrawlerProperties {

    private String startUrl       = "https://example.com";
    private int    maxDepth       = 2;
    private int    workerCount    = 5;
    private long   crawlDelayMs   = 1500;
    private int    maxUrlsPerDomain = 50;
    private String userAgent      = "WebCrawlerBot/1.0";
    private int    connectTimeoutMs = 5000;
    private int    socketTimeoutMs  = 10000;
    private int    connectionPoolSize = 20;

    public String getStartUrl()          { return startUrl; }
    public void   setStartUrl(String v)  { this.startUrl = v; }

    public int  getMaxDepth()            { return maxDepth; }
    public void setMaxDepth(int v)       { this.maxDepth = v; }

    public int  getWorkerCount()         { return workerCount; }
    public void setWorkerCount(int v)    { this.workerCount = v; }

    public long getCrawlDelayMs()        { return crawlDelayMs; }
    public void setCrawlDelayMs(long v)  { this.crawlDelayMs = v; }

    public int  getMaxUrlsPerDomain()    { return maxUrlsPerDomain; }
    public void setMaxUrlsPerDomain(int v){ this.maxUrlsPerDomain = v; }

    public String getUserAgent()         { return userAgent; }
    public void   setUserAgent(String v) { this.userAgent = v; }

    public int  getConnectTimeoutMs()    { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int v){ this.connectTimeoutMs = v; }

    public int  getSocketTimeoutMs()     { return socketTimeoutMs; }
    public void setSocketTimeoutMs(int v){ this.socketTimeoutMs = v; }

    public int  getConnectionPoolSize()  { return connectionPoolSize; }
    public void setConnectionPoolSize(int v){ this.connectionPoolSize = v; }
}
