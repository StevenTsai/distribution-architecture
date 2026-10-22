package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 状态存储 Redis 实现（预留回切档）：{@code app.state.store=redis} 时启用。
 *
 * <p>仅需 Redis 可达 + starter-data-redis 保留即可，业务代码零改动——
 * 这正是「回切零代码」在代码侧的形态。时钟归属：Redis 自身时钟（TTL 原生）。</p>
 */
public class RedisStateStore implements StateStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RedisStateStore(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 写入失败: " + key, e);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        try {
            return json.readValue(raw, type);
        } catch (Exception e) {
            redis.delete(key);
            return null;
        }
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, json.writeValueAsString(value), ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 写入失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, ttlOnCreate);
        }
        return v == null ? 0 : v;
    }
}
