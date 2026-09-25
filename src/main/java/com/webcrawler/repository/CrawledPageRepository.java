package com.webcrawler.repository;

import com.webcrawler.model.CrawledPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CrawledPage}.
 * Backed by PostgreSQL – provides persistent crawled-content storage.
 */
@Repository
public interface CrawledPageRepository extends JpaRepository<CrawledPage, Long> {

    boolean existsByUrl(String url);

    Optional<CrawledPage> findByUrl(String url);

    List<CrawledPage> findByDomain(String domain);

    /** Count of unique domains crawled so far. */
    @Query("SELECT COUNT(DISTINCT p.domain) FROM CrawledPage p")
    long countDistinctDomains();

    /** Returns the most recently crawled page URL. */
    @Query("SELECT p.url FROM CrawledPage p ORDER BY p.crawledAt DESC LIMIT 1")
    Optional<String> findLastCrawledUrl();

    /** Page-level summary for the /api/v1/crawled endpoint (avoids fetching full body text). */
    @Query("SELECT new com.webcrawler.model.CrawledPage(p.id, p.url, p.domain, p.statusCode, " +
           "p.contentType, p.title, null, p.outboundLinkCount, p.depth, p.crawledAt) " +
           "FROM CrawledPage p ORDER BY p.crawledAt DESC")
    List<CrawledPage> findAllSummaries();
}
