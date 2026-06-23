# ADR: 为什么选择 MyBatis 而非 JPA

中文 | [English](../en/decisions/why-mybatis-not-jpa.md)

> 状态：已采纳
> 日期：项目初始设计阶段（继承自 medical-chaperon-server）
> 决策者：架构团队

---

## 背景

Java 持久层框架的两大主流选择：

1. **MyBatis**：SQL 映射框架，手写 SQL，XML/注解配置
2. **JPA (Hibernate)**：ORM 框架，对象关系映射，自动生成 SQL

本项目选择了 MyBatis。

## 决策

使用 MyBatis Spring Boot Starter 2.3.1，SQL 写在 XML Mapper 文件中，Entity 为纯 POJO。

## 考虑的替代方案

### 方案 A：Spring Data JPA

| 维度 | JPA | MyBatis |
|------|-----|---------|
| SQL 控制 | 自动生成，复杂查询需要 JPQL 或 Native SQL | 完全手写，精确控制 |
| 学习曲线 | 需要理解 ORM 映射、懒加载、一级/二级缓存 | 只需要会写 SQL |
| 复杂查询 | JPQL 语法受限，Native SQL 破坏抽象 | 原生 SQL，无限制 |
| 性能调优 | 需要理解 Hibernate 的缓存和 flush 机制 | SQL 就是最终执行的 SQL |
| 多表关联 | `@OneToMany` 等注解方便但容易产生 N+1 | 手写 JOIN，显式控制 |
| 数据库迁移 | Entity 变更自动影响 SQL | 需要同步修改 XML |
| 代码量 | Entity + Repository 接口即可 | Entity + Mapper 接口 + XML |

### 方案 B：MyBatis-Plus

| 维度 | MyBatis-Plus | 本项目的 MyBatis |
|------|-------------|-----------------|
| 简单 CRUD | 自动生成，无需 XML | 需要手写 XML |
| 复杂查询 | Wrapper API 或自定义 SQL | 纯手写 XML |
| 灵活性 | Wrapper 有上限，极复杂查询仍需手写 | 无限制 |
| 学习成本 | 需要学 Wrapper API | 只需要会 SQL |
| 代码侵入 | 需要继承 BaseMapper | 纯接口，无继承 |

## 选择 MyBatis 的理由

### 1. 业务查询的复杂度

分销系统的查询场景天然复杂：

```sql
-- 线索列表查询：需要同时考虑业务条件 + 数据权限 + 分页
SELECT <include refid="BaseColumnList"/>
FROM distribution_lead
WHERE deleted = 0
  AND (patient_name LIKE '%张%' OR patient_phone LIKE '%张%')  -- 模糊搜索
  AND source_distributor_id IN (1, 5, 23, 47)                  -- 数据权限
  AND stage = 'signed'                                           -- 业务过滤
ORDER BY create_time DESC, id DESC
LIMIT 0, 10
```

用 JPA 的 `@Query` 或 JPQL 写这种查询，语法受限且不直观。用 MyBatis 直接写 SQL，所见即所得。

### 2. 数据权限的 SQL 注入

本项目的核心设计之一是数据权限的 SQL 层注入（详见 [data-permission-model.md](../architecture/data-permission-model.md)）。这需要在 SQL 中动态拼接 `WHERE ... AND distributor_id IN (...)` 条件。

MyBatis 的 `<sql>` 片段 + `<include>` 机制天然支持这种模式：

```xml
<sql id="ScopeCondition">
    <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
        and distributor_id in
        <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </if>
</sql>
```

JPA 要实现类似效果，需要：
- 要么用 Specification 动态拼接 CriteriaQuery（复杂且不直观）
- 要么用拦截器自动修改生成的 SQL（黑魔法，难以调试）

### 3. 性能可控性

管理后台的报表查询（如数据看板、佣金汇总）需要精确控制 SQL 的执行计划：

```xml
<!-- 佣金结算查询：精确控制 JOIN 和聚合 -->
<select id="selectSettleableByDistributorAndPeriod" resultMap="BaseResultMap">
    SELECT <include refid="BaseColumnList"/>
    FROM distribution_commission_ledger
    WHERE deleted = 0
      AND status = 'settleable'
      AND distributor_id = #{distributorId}
      AND received_at BETWEEN #{periodStart} AND #{periodEnd}
    ORDER BY received_at ASC
</select>
```

MyBatis 写的 SQL 就是最终执行的 SQL，没有 ORM 的"翻译层"。开发者可以直接用 `EXPLAIN` 分析查询计划，优化索引。

### 4. 团队技能栈

- 开发团队熟悉 SQL，不熟悉 Hibernate 的缓存机制和懒加载陷阱
- 调试时可以直接复制 XML 中的 SQL 到数据库客户端执行
- 新成员上手只需"会写 SQL + 会用 MyBatis XML"

### 5. 与现有系统的一致性

本项目从 `medical-chaperon-server` 提取，该系统已经使用 MyBatis。保持一致可以：
- 复用已有的 Mapper XML 和 Entity
- 共享数据库连接池和 MyBatis 配置
- 降低迁移成本

## Trade-off

### 付出的代价

1. **代码量较大**：每个表需要 Entity + Mapper 接口 + XML 三个文件
   - 缓解：MyBatis Generator 可以自动生成基础代码
   - 缓解：本项目的 Entity 和 Mapper 大部分由 Generator 生成

2. **表结构变更需要同步修改多处**：加字段需要改 Entity + XML + DTO
   - 缓解：使用 `insertSelective` / `updateByPrimaryKeySelective`，只操作变化的字段
   - 缓解：IDEA 的 MyBatis 插件可以辅助同步

3. **没有自动的脏检查和级联更新**：JPA 的 `@Transactional` + 自动 flush 机制在 MyBatis 中需要手动实现
   - 缓解：本项目所有写操作都显式调用 `updateByPrimaryKeySelective`，意图明确

4. **缺少编译期 SQL 校验**：XML 中的 SQL 语法错误只在运行时发现
   - 缓解：MyBatis 的 `@Param` 注解 + IDEA 插件可以在编辑期做基本检查

### 收益

1. SQL 完全可控，复杂查询无限制
2. 数据权限的 SQL 层注入实现简单
3. 性能调优直观（SQL → EXPLAIN → 优化）
4. 学习曲线低，团队上手快
5. 与现有系统兼容，零迁移成本

## 后续演进

如果未来需要：

1. **减少简单 CRUD 的 XML 代码**：可以引入 MyBatis-Plus 的 `BaseMapper`，复杂查询仍用 XML
2. **自动生成基础代码**：配置 MyBatis Generator，从数据库表自动生成 Entity + Mapper
3. **混合使用**：简单查询用 MyBatis-Plus Wrapper，复杂查询用 XML（本项目已部分采用这种模式）

---

- *MapperScan：`启动类上的 @MapperScan 注解
