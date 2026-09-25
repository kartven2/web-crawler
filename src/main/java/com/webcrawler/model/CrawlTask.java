package com.webcrawler.model;

import java.io.Serializable;

/**
 * A task representing a single URL to be crawled.
 * Serialised to JSON and placed onto a RabbitMQ queue by the URL Frontier.
 */
public class CrawlTask implements Serializable {

    private String url;
    private int    depth;
    private String domain;
    private String parentUrl;
    private long   enqueuedAt;

    public CrawlTask() {}

    private CrawlTask(Builder b) {
        this.url        = b.url;
        this.depth      = b.depth;
        this.domain     = b.domain;
        this.parentUrl  = b.parentUrl;
        this.enqueuedAt = b.enqueuedAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String url, domain, parentUrl;
        private int depth;
        private long enqueuedAt;

        public Builder url(String v)        { this.url = v; return this; }
        public Builder depth(int v)         { this.depth = v; return this; }
        public Builder domain(String v)     { this.domain = v; return this; }
        public Builder parentUrl(String v)  { this.parentUrl = v; return this; }
        public Builder enqueuedAt(long v)   { this.enqueuedAt = v; return this; }
        public CrawlTask build()            { return new CrawlTask(this); }
    }

    public String getUrl()           { return url; }
    public void   setUrl(String v)   { this.url = v; }

    public int    getDepth()         { return depth; }
    public void   setDepth(int v)    { this.depth = v; }

    public String getDomain()        { return domain; }
    public void   setDomain(String v){ this.domain = v; }

    public String getParentUrl()     { return parentUrl; }
    public void   setParentUrl(String v){ this.parentUrl = v; }

    public long   getEnqueuedAt()    { return enqueuedAt; }
    public void   setEnqueuedAt(long v){ this.enqueuedAt = v; }
}
