package com.webcrawler.model;

/**
 * Snapshot of crawler statistics returned by the /api/v1/stats endpoint.
 */
public class CrawlerStats {

    private long   totalCrawled;
    private long   currentlyCrawling;
    private long   queued;
    private long   failed;
    private String lastCrawledUrl;
    private long   domainsDiscovered;

    private CrawlerStats(Builder b) {
        this.totalCrawled      = b.totalCrawled;
        this.currentlyCrawling = b.currentlyCrawling;
        this.queued            = b.queued;
        this.failed            = b.failed;
        this.lastCrawledUrl    = b.lastCrawledUrl;
        this.domainsDiscovered = b.domainsDiscovered;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long totalCrawled, currentlyCrawling, queued, failed, domainsDiscovered;
        private String lastCrawledUrl;

        public Builder totalCrawled(long v)       { this.totalCrawled = v; return this; }
        public Builder currentlyCrawling(long v)  { this.currentlyCrawling = v; return this; }
        public Builder queued(long v)             { this.queued = v; return this; }
        public Builder failed(long v)             { this.failed = v; return this; }
        public Builder lastCrawledUrl(String v)   { this.lastCrawledUrl = v; return this; }
        public Builder domainsDiscovered(long v)  { this.domainsDiscovered = v; return this; }
        public CrawlerStats build()               { return new CrawlerStats(this); }
    }

    public long   getTotalCrawled()       { return totalCrawled; }
    public long   getCurrentlyCrawling()  { return currentlyCrawling; }
    public long   getQueued()             { return queued; }
    public long   getFailed()             { return failed; }
    public String getLastCrawledUrl()     { return lastCrawledUrl; }
    public long   getDomainsDiscovered()  { return domainsDiscovered; }
}
