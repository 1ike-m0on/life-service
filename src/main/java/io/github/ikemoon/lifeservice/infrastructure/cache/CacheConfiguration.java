package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {

    @Bean
    public Cache<String, Object> localCache(CacheProperties cacheProperties) {
        CacheProperties.Local local = cacheProperties.getLocal();
        return Caffeine.newBuilder()
                .maximumSize(local.getMaximumSize())
                .expireAfterWrite(local.getTtl())
                .build();
    }
}
