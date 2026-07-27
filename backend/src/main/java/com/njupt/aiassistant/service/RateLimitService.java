package com.njupt.aiassistant.service;

public interface RateLimitService {
    boolean tryConsume(String key, int capacityPerMinute);
}
