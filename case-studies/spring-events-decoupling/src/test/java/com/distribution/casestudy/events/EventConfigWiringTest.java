package com.distribution.casestudy.events;

import com.distribution.casestudy.events.config.EventConfig;
import com.distribution.casestudy.events.event.DeliveryCompleteEvent;
import com.distribution.casestudy.events.listener.DeliveryToReceivableListener;
import com.distribution.casestudy.events.listener.QualityToReworkListener;
import com.distribution.casestudy.events.listener.WorkOrderToInventoryListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring assembly tests for the event infrastructure — the wiring that plain
 * unit tests ({@link EventListenerTest}) never touch.
 *
 * <p>Covers the three failure modes that unit tests cannot see:</p>
 * <ol>
 *   <li>listener registration when callback beans are present;</li>
 *   <li>fail-fast context startup when a callback bean is missing
 *       (a silently skipped listener is worse than a failed boot);</li>
 *   <li>{@code AFTER_COMMIT} semantics: published inside a transaction → fired
 *       after commit; published without a transaction → never fired.</li>
 * </ol>
 */
class EventConfigWiringTest {

    /** 全量回调：三个回调 bean 都注册（空实现，只验证装配） */
    @Configuration
    static class AllCallbacksFixture {
        @Bean
        DeliveryToReceivableListener.AccountsReceivableCallback receivableCallback() {
            return (orderId, orderNo, customerId, amount, receivedAmount, outstandingAmount, dueDate, status) -> { };
        }

        @Bean
        WorkOrderToInventoryListener.FinishedGoodsCallback finishedGoodsCallback() {
            return (workOrderId, workOrderNo, productId, productCode, quantity, producedDate, status) -> { };
        }

        @Bean
        QualityToReworkListener.ReworkOrderCallback reworkOrderCallback() {
            return (originalWorkOrderId, originalWorkOrderNo, processName, defectType,
                    defectDescription, quantity, recommendedAction) -> { };
        }
    }

    /** 仅注册一个回调：另外两个 listener 的装配必须因此失败 */
    @Configuration
    static class OnlyReceivableCallbackFixture {
        @Bean
        DeliveryToReceivableListener.AccountsReceivableCallback receivableCallback() {
            return (orderId, orderNo, customerId, amount, receivedAmount, outstandingAmount, dueDate, status) -> { };
        }
    }

    /** 计数回调：验证 AFTER_COMMIT 语义时捕获实际触发 */
    @Configuration
    static class CountingCallbacksFixture {
        final AtomicInteger receivableCount = new AtomicInteger();
        final CountDownLatch fired = new CountDownLatch(1);

        @Bean
        DeliveryToReceivableListener.AccountsReceivableCallback receivableCallback() {
            return (orderId, orderNo, customerId, amount, receivedAmount, outstandingAmount, dueDate, status) -> {
                receivableCount.incrementAndGet();
                fired.countDown();
            };
        }

        @Bean
        WorkOrderToInventoryListener.FinishedGoodsCallback finishedGoodsCallback() {
            return (workOrderId, workOrderNo, productId, productCode, quantity, producedDate, status) -> { };
        }

        @Bean
        QualityToReworkListener.ReworkOrderCallback reworkOrderCallback() {
            return (originalWorkOrderId, originalWorkOrderNo, processName, defectType,
                    defectDescription, quantity, recommendedAction) -> { };
        }
    }

    /**
     * 无资源的事务管理器：AbstractPlatformTransactionManager 负责把事务绑定到线程
     * （TransactionSynchronizationManager），这正是 @TransactionalEventListener
     * 判定「发布方是否在事务内」的依据；begin/commit/rollback 本身为空操作。
     */
    static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            /* 无资源可绑定 */
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            /* 空提交：仅触发 synchronization 回调 */
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            /* 空回滚 */
        }
    }

    /**
     * 与生产路径（Boot 自动装配事务基础设施）对齐：注册 TransactionalEventListenerFactory
     * 后，{@code @TransactionalEventListener} 才会被识别为事务感知监听器——
     * 裸 ApplicationContextRunner 只有 DefaultEventListenerFactory（supportsMethod 恒 true），
     * 若不补注册，注解会被当作普通 @EventListener 处理（事务门控静默失效）。
     * 该工厂 order=50，排序在默认工厂之前认领事务感知方法，两个工厂共存（与生产一致）。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EventConfig.class)
            .withBean(TransactionalEventListenerFactory.class);

    private static DeliveryCompleteEvent deliveryEvent() {
        return new DeliveryCompleteEvent(new Object(), 1L, 2L, "ORD-WIRE-1", 3L,
                new BigDecimal("99.00"), LocalDate.of(2026, 10, 22));
    }

    @Test
    @DisplayName("回调 bean 就绪 → 三个 listener + 专用线程池全部注册")
    void listenersRegisteredWhenCallbacksPresent() {
        runner.withUserConfiguration(AllCallbacksFixture.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasBean("eventAsyncExecutor");
                    assertThat(ctx).hasSingleBean(DeliveryToReceivableListener.class);
                    assertThat(ctx).hasSingleBean(WorkOrderToInventoryListener.class);
                    assertThat(ctx).hasSingleBean(QualityToReworkListener.class);
                });
    }

    @Test
    @DisplayName("回调 bean 全缺 → 上下文启动失败（fail-fast，不静默跳过注册）")
    void contextFailsFastWhenCallbacksMissing() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("仅缺部分回调 → 上下文同样启动失败")
    void contextFailsFastWhenSomeCallbackMissing() {
        runner.withUserConfiguration(OnlyReceivableCallbackFixture.class)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("事务提交后监听器才触发（AFTER_COMMIT + @Async）")
    void listenerFiresAfterCommit() {
        runner.withUserConfiguration(CountingCallbacksFixture.class)
                .withBean(PlatformTransactionManager.class, NoopTransactionManager::new)
                .run(ctx -> {
                    CountingCallbacksFixture fixture = ctx.getBean(CountingCallbacksFixture.class);
                    TransactionTemplate tx = new TransactionTemplate(
                            ctx.getBean(PlatformTransactionManager.class));

                    tx.executeWithoutResult(status -> ctx.publishEvent(deliveryEvent()));

                    assertTrue(fixture.fired.await(3, TimeUnit.SECONDS),
                            "发布方事务提交后，异步监听器应被触发");
                    assertEquals(1, fixture.receivableCount.get());
                });
    }

    @Test
    @DisplayName("无事务发布 → 监听器不触发（AFTER_COMMIT 语义，README 前提被锁定）")
    void listenerNotFiredWithoutTransaction() throws Exception {
        runner.withUserConfiguration(CountingCallbacksFixture.class)
                .withBean(PlatformTransactionManager.class, NoopTransactionManager::new)
                .run(ctx -> {
                    CountingCallbacksFixture fixture = ctx.getBean(CountingCallbacksFixture.class);

                    /* 无活跃事务：@TransactionalEventListener 默认 fallbackExecution=false，事件被丢弃 */
                    ctx.publishEvent(deliveryEvent());

                    Thread.sleep(300); // 给误触发留出异步窗口
                    assertEquals(0, fixture.receivableCount.get(),
                            "无事务发布不得触发 AFTER_COMMIT 监听器（使用前提：必须在事务内发布）");
                });
    }
}
