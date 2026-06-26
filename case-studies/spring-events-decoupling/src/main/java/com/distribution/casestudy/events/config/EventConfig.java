package com.distribution.casestudy.events.config;

import com.distribution.casestudy.events.listener.DeliveryToReceivableListener;
import com.distribution.casestudy.events.listener.QualityToReworkListener;
import com.distribution.casestudy.events.listener.WorkOrderToInventoryListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Event infrastructure configuration.
 *
 * <p>Extracted from jewelry ERP {@code AsyncConfig}. The original config provided
 * a single {@code asyncExecutor} bean used for both parallel queries and
 * async event listeners. This version provides a dedicated
 * {@code eventAsyncExecutor} bean for event processing.</p>
 *
 * <h3>Thread pool design</h3>
 * <ul>
 *   <li>Core pool size 2: handles normal event volume</li>
 *   <li>Max pool size 8: handles burst loads</li>
 *   <li>Queue capacity 100: buffers events during peak periods</li>
 *   <li>CallerRunsPolicy: when pool is saturated, the publisher thread handles the event
 *       (backpressure, prevents event loss)</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class EventConfig {

    @Bean("eventAsyncExecutor")
    public Executor eventAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnBean(type = "com.distribution.casestudy.events.listener.DeliveryToReceivableListener.AccountsReceivableCallback")
    public DeliveryToReceivableListener deliveryToReceivableListener(
            DeliveryToReceivableListener.AccountsReceivableCallback callback) {
        return new DeliveryToReceivableListener(callback);
    }

    @Bean
    @ConditionalOnBean(type = "com.distribution.casestudy.events.listener.WorkOrderToInventoryListener.FinishedGoodsCallback")
    public WorkOrderToInventoryListener workOrderToInventoryListener(
            WorkOrderToInventoryListener.FinishedGoodsCallback callback) {
        return new WorkOrderToInventoryListener(callback);
    }

    @Bean
    @ConditionalOnBean(type = "com.distribution.casestudy.events.listener.QualityToReworkListener.ReworkOrderCallback")
    public QualityToReworkListener qualityToReworkListener(
            QualityToReworkListener.ReworkOrderCallback callback) {
        return new QualityToReworkListener(callback);
    }
}
