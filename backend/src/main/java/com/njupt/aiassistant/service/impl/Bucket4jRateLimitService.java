package com.njupt.aiassistant.service.impl;

import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import com.njupt.aiassistant.config.RateLimitProperties;
import com.njupt.aiassistant.service.RateLimitService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Bucket4jRateLimitService implements RateLimitService {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> localBuckets =
            new ConcurrentHashMap<>();
    private RedisClient redisClient;
    private StatefulRedisConnection<byte[], byte[]> redisConnection;
    private LettuceBasedProxyManager<byte[]> proxyManager;

    public Bucket4jRateLimitService(RateLimitProperties properties) {
        this.properties = properties;
        if (properties.enabled()
                && properties.backend()
                == RateLimitProperties.Backend.REDIS) {
            initializeRedis();
        }
    }

    @Override
    public boolean tryConsume(String key, int capacityPerMinute) {
        if (!properties.enabled()) {
            return true;
        }
        var namespacedKey = "njupt:rate:"
                + capacityPerMinute + ":" + key;
        if (proxyManager != null) {
            var bucket = proxyManager.getProxy(
                    namespacedKey.getBytes(StandardCharsets.UTF_8),
                    () -> configuration(capacityPerMinute)
            );
            return bucket.tryConsume(1);
        }
        var bucket = localBuckets.computeIfAbsent(
                namespacedKey,
                ignored -> Bucket.builder()
                        .addLimit(limit -> limit
                                .capacity(capacityPerMinute)
                                .refillGreedy(
                                        capacityPerMinute,
                                        Duration.ofMinutes(1)
                                ))
                        .build()
        );
        return bucket.tryConsume(1);
    }

    private void initializeRedis() {
        if (properties.redisUri() == null) {
            throw new IllegalStateException(
                    "RATE_LIMIT_REDIS_URI is required for REDIS backend"
            );
        }
        redisClient = RedisClient.create(properties.redisUri().toString());
        redisConnection = redisClient.connect(ByteArrayCodec.INSTANCE);
        proxyManager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(
                                        Duration.ofMinutes(2)
                                )
                )
                .build();
    }

    private BucketConfiguration configuration(int capacityPerMinute) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(capacityPerMinute)
                        .refillGreedy(
                                capacityPerMinute,
                                Duration.ofMinutes(1)
                        ))
                .build();
    }

    @PreDestroy
    void closeRedis() {
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}
