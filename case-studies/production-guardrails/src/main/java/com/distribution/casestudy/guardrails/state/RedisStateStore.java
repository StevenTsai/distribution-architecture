package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 状态存储 Redis 实现（预留回切档）：{@code app.state.store=redis} 时启用。
 *
 * <p>仅需 Redis 可达 + starter-data-redis 保留即可，业务代码零改动——
 * 这正是「回切零代码」在代码侧的形态。时钟归属：Redis 自身时钟（TTL 原生）。</p>
 */
public class RedisStateStore implements StateStore {

    /**
     * INCR 与「首建时写入 TTL」必须是一段原子脚本：两步分开时，
     * 并发下存在「自增成功但键永不过期」的泄漏窗口。脚本内同时做整数校验，
     * 非整数计数键返回 null，由 Java 侧转为契约规定的 IllegalStateException。
     */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if raw and not string.match(raw, '^-?%d+$') then
                return nil
            end
            local v = redis.call('INCR', KEYS[1])
            if v == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return v
            """, Long.class);

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
        Long v = redis.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(ttlOnCreate.toMillis()));
        if (v == null) {
            /* Lua 返回 null = 计数键当前值非整数（契约：拒绝而非静默归零） */
            throw new IllegalStateException("拒绝自增：计数键当前值不是整数，key=" + key);
        }
        return v;
    }
}
