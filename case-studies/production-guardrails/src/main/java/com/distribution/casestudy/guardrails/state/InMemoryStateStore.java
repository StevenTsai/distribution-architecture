package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存参考实现（测试/本地档，{@code app.state.store=memory}）。
 *
 * <p>角色不是「第三个生产实现」，而是契约的第一参照：零外部依赖地把
 * {@link StateStore} 的语义钉死，供 MySQL / Redis 实现的契约测试对齐。</p>
 */
public class InMemoryStateStore implements StateStore {

    private record Entry(String json, long expireAt) {
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    public InMemoryStateStore(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        map.put(key, new Entry(write(value), now() + ttl.toMillis()));
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() <= now()) {
            map.remove(key, entry);
            return null;
        }
        return read(entry.json(), type, key);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        long expireAt = now() + ttl.toMillis();
        Entry created = new Entry(write(value), expireAt);
        Entry existing = map.compute(key, (k, cur) ->
                (cur == null || cur.expireAt() <= now()) ? created : cur);
        return existing == created;
    }

    @Override
    public void delete(String key) {
        map.remove(key);
    }

    @Override
    public boolean exists(String key) {
        Entry entry = map.get(key);
        return entry != null && entry.expireAt() > now();
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        long now = now();
        long[] result = {1L};
        map.compute(key, (k, cur) -> {
            if (cur == null || cur.expireAt() <= now) {
                result[0] = 1L;
                return new Entry("1", now + ttlOnCreate.toMillis());
            }
            long next;
            try {
                next = Long.parseLong(cur.json()) + 1;
            } catch (NumberFormatException e) {
                /* 契约：计数键不得被 set 写入非整数——非法即拒绝（而非静默重置），
                   抛出后 compute 不应用新值，原值保持不变 */
                throw new IllegalStateException("拒绝自增：计数键当前值不是整数，key=" + k, e);
            }
            result[0] = next;
            return new Entry(String.valueOf(next), cur.expireAt());
        });
        return result[0];
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("状态序列化失败", e);
        }
    }

    private <T> T read(String raw, Class<T> type, String key) {
        try {
            return json.readValue(raw, type);
        } catch (Exception e) {
            /* 宽容读：损坏值视同不存在并清除 */
            map.remove(key);
            return null;
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
