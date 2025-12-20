package com.eventmanager.batch.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;

import java.time.Duration;
import java.util.List;

/**
 * Cache configuration for batch jobs service.
 *
 * Mirrors the Caffeine-based caching approach used in the main backend,
 * with per-cache TTL and max size configured via application.yml.
 */
@Configuration
@EnableCaching
public class BatchJobsCacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BatchJobsCacheConfiguration.class);

    @Bean
    public CacheManager cacheManager(
        @Value("${cache.maxSize:1000}") int maxCacheSize,
        @Value("${cache.ttl.tenantFooterHtml:3600}") long tenantFooterHtmlTtl,
        @Value("${cache.ttl.tenantEmailFrom:3600}") long tenantEmailFromTtl,
        @Value("${cache.ttl.tenantEmailCopyTo:3600}") long tenantEmailCopyToTtl
    ) {
        log.info("Initializing Caffeine CacheManager with maxSize={} and per-cache TTLs", maxCacheSize);

        SimpleCacheManager cacheManager = new SimpleCacheManager();

        Cache tenantFooterHtmlCache = createCaffeineCache("tenantFooterHtmlCache", tenantFooterHtmlTtl, maxCacheSize);
        Cache tenantEmailFromCache = createCaffeineCache("tenantEmailFromCache", tenantEmailFromTtl, maxCacheSize);
        Cache tenantEmailCopyToCache = createCaffeineCache("tenantEmailCopyToCache", tenantEmailCopyToTtl, maxCacheSize);

        cacheManager.setCaches(List.of(
            tenantFooterHtmlCache,
            tenantEmailFromCache,
            tenantEmailCopyToCache
        ));

        return cacheManager;
    }

    private Cache createCaffeineCache(String name, long ttlSeconds, int maxCacheSize) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(maxCacheSize)
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds));

        log.debug("Creating Caffeine cache '{}' with TTL={}s, maxSize={}", name, ttlSeconds, maxCacheSize);
        return new CaffeineCache(name, builder.build());
    }
}




