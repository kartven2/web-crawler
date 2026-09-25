package com.webcrawler.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent record of a successfully crawled page.
 * Stored in PostgreSQL table {@code crawled_pages}.
 */
@Entity
@Table(
    name = "crawled_pages",
    indexes = {
        @Index(name = "idx_crawled_url",    columnList = "url",    unique = true),
        @Index(name = "idx_crawled_domain", columnList = "domain")
    }
)
public class CrawledPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 512)
    private String domain;

    private int statusCode;

    @Column(length = 256)
    private String contentType;

    @Column(length = 1024)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String bodyText;

    private int outboundLinkCount;
    private int depth;

    @Column(nullable = false)
    private Instant crawledAt;

    // ── No-arg constructor (required by JPA) ──────────────────────────────────
    public CrawledPage() {}

    // ── All-args constructor (used by JPQL projection query) ─────────────────
    public CrawledPage(Long id, String url, String domain, int statusCode,
                       String contentType, String title, String bodyText,
                       int outboundLinkCount, int depth, Instant crawledAt) {
        this.id               = id;
        this.url              = url;
        this.domain           = domain;
        this.statusCode       = statusCode;
        this.contentType      = contentType;
        this.title            = title;
        this.bodyText         = bodyText;
        this.outboundLinkCount = outboundLinkCount;
        this.depth            = depth;
        this.crawledAt        = crawledAt;
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String url, domain, contentType, title, bodyText;
        private int statusCode, outboundLinkCount, depth;
        private Instant crawledAt;

        public Builder id(Long v)                { this.id = v; return this; }
        public Builder url(String v)             { this.url = v; return this; }
        public Builder domain(String v)          { this.domain = v; return this; }
        public Builder statusCode(int v)         { this.statusCode = v; return this; }
        public Builder contentType(String v)     { this.contentType = v; return this; }
        public Builder title(String v)           { this.title = v; return this; }
        public Builder bodyText(String v)        { this.bodyText = v; return this; }
        public Builder outboundLinkCount(int v)  { this.outboundLinkCount = v; return this; }
        public Builder depth(int v)              { this.depth = v; return this; }
        public Builder crawledAt(Instant v)      { this.crawledAt = v; return this; }

        public CrawledPage build() {
            return new CrawledPage(id, url, domain, statusCode, contentType,
                                   title, bodyText, outboundLinkCount, depth, crawledAt);
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public Long    getId()               { return id; }
    public void    setId(Long id)        { this.id = id; }

    public String  getUrl()              { return url; }
    public void    setUrl(String url)    { this.url = url; }

    public String  getDomain()           { return domain; }
    public void    setDomain(String d)   { this.domain = d; }

    public int     getStatusCode()       { return statusCode; }
    public void    setStatusCode(int s)  { this.statusCode = s; }

    public String  getContentType()      { return contentType; }
    public void    setContentType(String c){ this.contentType = c; }

    public String  getTitle()            { return title; }
    public void    setTitle(String t)    { this.title = t; }

    public String  getBodyText()         { return bodyText; }
    public void    setBodyText(String b) { this.bodyText = b; }

    public int     getOutboundLinkCount()     { return outboundLinkCount; }
    public void    setOutboundLinkCount(int n){ this.outboundLinkCount = n; }

    public int     getDepth()            { return depth; }
    public void    setDepth(int d)       { this.depth = d; }

    public Instant getCrawledAt()        { return crawledAt; }
    public void    setCrawledAt(Instant t){ this.crawledAt = t; }
}
