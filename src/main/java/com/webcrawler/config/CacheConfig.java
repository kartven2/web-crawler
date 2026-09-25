package com.webcrawler.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory cache configuration.
 *
 * Currently used for:
 *  - "robotsCache" – caches parsed robots.txt allow/disallow decisions per domain.
 *    This avoids repeated HTTP fetches of robots.txt for every URL in the same domain.
 *
 * In production this would be replaced by Redis-backed caching (e.g. Spring Data Redis
 * with @Cacheable + RedisCacheManager), giving a shared cache across multiple crawler nodes.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("robotsCache");
    }
}
