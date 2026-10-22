package com.distribution.casestudy.guardrails.state;

/**
 * 内存参考实现的契约执行。它是契约的「第一参照」——
 * 语义争议先在无外部依赖的这里定案，再要求其余实现对齐。
 */
class InMemoryStateStoreContractTest extends StateStoreContractTest {

    @Override
    protected StateStore createStore() {
        return new InMemoryStateStore(JSON);
    }
}
