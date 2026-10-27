package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StateStore 语义契约——三种实现共用的同一断言集。
 *
 * <p>这是生产系统中缺失、事后补齐的那类测试（见 ADR「状态存储可插拔」后续演进 #1）：
 * 抽象的契约不能只写在 javadoc 里，必须有一份对所有实现跑同一断言的参数化测试。</p>
 *
 * <p>TTL 统一取 1 秒 + 轮询等待过期：慢，但对真实时钟的三个实现都确定可过。</p>
 */
abstract class StateStoreContractTest {

    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final Duration TTL = Duration.ofSeconds(1);

    /** 契约测试用值对象：JSON 往返 + 不兼容类型互转 */
    record SessionInfo(String user, int issuedAt) {
    }

    protected StateStore store;

    protected abstract StateStore createStore();

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    /** 轮询直到键过期消失（上限 4s），并断言最终不可读 */
    protected void awaitExpired(String key) {
        long deadline = System.currentTimeMillis() + 4_000;
        while (System.currentTimeMillis() < deadline && store.get(key, SessionInfo.class) != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待过期被中断", e);
            }
        }
        assertNull(store.get(key, SessionInfo.class), "TTL 到期后键应不可读");
    }

    // ========== set / get ==========

    @Nested
    @DisplayName("set()/get()")
    class SetGet {

        @Test
        @DisplayName("JSON 值往返一致")
        void roundTrip() {
            store.set("sess:1", new SessionInfo("alice", 42), TTL);
            SessionInfo got = store.get("sess:1", SessionInfo.class);
            assertNotNull(got);
            assertEquals("alice", got.user());
            assertEquals(42, got.issuedAt());
        }

        @Test
        @DisplayName("不存在的键 → null")
        void missingKeyReturnsNull() {
            assertNull(store.get("nope", SessionInfo.class));
        }

        @Test
        @DisplayName("宽容读：用不兼容类型读取 → null，且键被清除")
        void incompatibleTypeReturnsNullAndDeletes() {
            store.set("sess:2", new SessionInfo("bob", 1), TTL);
            /* JSON 对象无法反序列化为 Integer → 宽容读 */
            assertNull(store.get("sess:2", Integer.class));
            /* 键已被清除：换回正确类型也读不到 */
            assertNull(store.get("sess:2", SessionInfo.class));
        }

        @Test
        @DisplayName("set 覆盖旧值")
        void setOverwrites() {
            store.set("k", new SessionInfo("a", 1), TTL);
            store.set("k", new SessionInfo("b", 2), TTL);
            assertEquals("b", store.get("k", SessionInfo.class).user());
        }
    }

    // ========== TTL ==========

    @Nested
    @DisplayName("过期语义")
    class Expiry {

        @Test
        @DisplayName("TTL 到期后读不到")
        void expiredKeyUnreadable() {
            store.set("e1", new SessionInfo("x", 1), TTL);
            assertNotNull(store.get("e1", SessionInfo.class));
            awaitExpired("e1");
        }

        @Test
        @DisplayName("过期键可被 setIfAbsent 覆盖")
        void setIfAbsentAfterExpiry() {
            store.set("e2", new SessionInfo("x", 1), TTL);
            awaitExpired("e2");
            assertTrue(store.setIfAbsent("e2", new SessionInfo("y", 2), TTL));
            assertEquals("y", store.get("e2", SessionInfo.class).user());
        }

        @Test
        @DisplayName("过期后 increment 重置为 1")
        void incrementAfterExpiryResets() {
            assertEquals(1L, store.increment("e3", TTL));
            assertEquals(2L, store.increment("e3", TTL));
            awaitExpired("e3");
            assertEquals(1L, store.increment("e3", TTL));
        }

        @Test
        @DisplayName("亚秒 TTL（毫秒语义）同样生效")
        void subSecondTtlHonored() {
            store.set("sub", new SessionInfo("x", 1), Duration.ofMillis(300));
            assertNotNull(store.get("sub", SessionInfo.class), "TTL 未到期前应可读（毫秒精度）");
            awaitExpired("sub");
        }
    }

    // ========== setIfAbsent ==========

    @Nested
    @DisplayName("setIfAbsent()")
    class SetIfAbsent {

        @Test
        @DisplayName("键不存在 → 写入成功返回 true")
        void absentSucceeds() {
            assertTrue(store.setIfAbsent("a1", new SessionInfo("a", 1), TTL));
        }

        @Test
        @DisplayName("键活跃 → 返回 false，原值不变")
        void presentFails() {
            assertTrue(store.setIfAbsent("a2", new SessionInfo("first", 1), TTL));
            assertFalse(store.setIfAbsent("a2", new SessionInfo("second", 2), TTL));
            assertEquals("first", store.get("a2", SessionInfo.class).user());
        }
    }

    // ========== delete / exists ==========

    @Nested
    @DisplayName("delete()/exists()")
    class DeleteExists {

        @Test
        @DisplayName("删除后不存在；删除不存在的键静默")
        void deleteIdempotent() {
            store.set("d1", new SessionInfo("a", 1), TTL);
            assertTrue(store.exists("d1"));
            store.delete("d1");
            assertFalse(store.exists("d1"));
            store.delete("d1"); // 不抛异常
        }

        @Test
        @DisplayName("exists 反映活跃状态，过期即 false")
        void existsReflectsLiveness() {
            assertFalse(store.exists("x0"));
            store.set("x0", new SessionInfo("a", 1), TTL);
            assertTrue(store.exists("x0"));
            awaitExpired("x0");
            assertFalse(store.exists("x0"));
        }
    }

    // ========== increment ==========

    @Nested
    @DisplayName("increment()")
    class Increment {

        @Test
        @DisplayName("首建为 1，随后逐次递增")
        void incrementsFromOne() {
            assertEquals(1L, store.increment("n1", TTL));
            assertEquals(2L, store.increment("n1", TTL));
            assertEquals(3L, store.increment("n1", TTL));
        }

        @Test
        @DisplayName("并发自增：各调用方所得值互不重复，最终值精确等于总次数")
        void concurrentIncrementIsAtomic() throws Exception {
            int threads = 4;
            int perThread = 25;
            long total = (long) threads * perThread;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            try {
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        List<Long> mine = new ArrayList<>(perThread);
                        for (int i = 0; i < perThread; i++) {
                            mine.add(store.increment("n2", Duration.ofMinutes(1)));
                        }
                        return mine;
                    }));
                }
                start.countDown();
                List<Long> all = new ArrayList<>();
                for (Future<List<Long>> f : futures) {
                    all.addAll(f.get(10, TimeUnit.SECONDS));
                }
                /* 原子性的硬断言：返回值不重不漏（恰好覆盖 1..N），最终值精确 */
                assertEquals(total, all.size());
                assertEquals(total, all.stream().distinct().count(),
                        "并发 increment 的返回值必须互不重复（重复=读回了别人的值）");
                assertEquals(total, store.get("n2", Long.class),
                        "并发 increment 最终值必须精确等于总次数");
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("计数键被 set 写入非整数 → 拒绝（IllegalStateException），原值不变")
        void incrementRejectsNonIntegerValue() {
            store.set("bad", "not-a-number", TTL);
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> store.increment("bad", TTL));
            assertTrue(e.getMessage().contains("bad"), "拒绝信息须点名业务键");
            assertEquals("not-a-number", store.get("bad", String.class),
                    "拒绝后原值应保持不变（不得静默归零/重置）");
        }
    }
}
