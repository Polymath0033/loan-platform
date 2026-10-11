package com.loan.apigateway.utils;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisUtils {

    public final RedisTemplate<String, String> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;


    public String findValueByKey(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error retrieving value from Redis for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    public String fetchFromRedis(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error retrieving value from Redis for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    public void saveValue(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(172800));

        } catch (Exception e) {
            log.error("Error saving value to Redis for key {}: {}", key, e.getMessage());
        }
    }


    public void deleteValue(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error deleting value from Redis for key {}: {}", key, e.getMessage());
        }
    }

    public boolean exists(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Error checking existence of key {} in Redis: {}", key, e.getMessage());
            return false;
        }
    }




}
