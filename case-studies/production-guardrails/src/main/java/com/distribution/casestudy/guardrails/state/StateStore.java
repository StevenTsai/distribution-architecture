package com.distribution.casestudy.guardrails.state;

import java.time.Duration;

/**
 * 短生命周期状态存取抽象（验证码 / 令牌吊销黑名单 / 握手材料）。
 *
 * <p>实现：
 * <ul>
 *   <li>{@link MysqlStateStore}（默认，{@code app.state.store=mysql} 或未配置）——app_state 表，零额外中间件；</li>
 *   <li>{@link RedisStateStore}（预留回切，{@code app.state.store=redis}）——改配置即可启用，无需改代码；</li>
 *   <li>{@link InMemoryStateStore}（测试/本地参考实现，{@code app.state.store=memory}）——契约测试的第一参照。</li>
 * </ul>
 *
 * <p>语义契约（三种实现必须一致，由 {@code StateStoreContractTest} 锁定）：
 * <ul>
 *   <li>值按 JSON 序列化存储；get 反序列化失败按「不存在」处理并删除该键（宽容读）；</li>
 *   <li>过期键视同不存在（读不到、setIfAbsent 可覆盖、increment 重置为 1）；</li>
 *   <li>setIfAbsent：仅当键不存在（或已过期）时写入成功，返回 true；</li>
 *   <li>increment：原子自增并返回自增后的值；键首次创建时按传入 TTL 设置过期；值为整数语义。</li>
 *   <li>时钟归属：过期判定一律使用<b>应用时钟</b>（各实现自行取 now 并下推到存储层参数），
 *       不存在「写用应用钟、判过期用数据库钟」的混用。</li>
 * </ul>
 */
public interface StateStore {

    /** 写入（覆盖），TTL 从现在起计 */
    void set(String key, Object value, Duration ttl);

    /** 读取；键不存在/已过期/值损坏（并删除）时返回 null */
    <T> T get(String key, Class<T> type);

    /** 仅当键不存在（或已过期）时写入；返回是否写入成功（false=键仍活跃） */
    boolean setIfAbsent(String key, Object value, Duration ttl);

    /** 删除键（不存在时静默） */
    void delete(String key);

    /** 键是否存在且未过期 */
    boolean exists(String key);

    /** 原子自增（值为整数语义），返回自增后的值；键首次创建时按 ttlOnCreate 设置过期 */
    long increment(String key, Duration ttlOnCreate);
}
