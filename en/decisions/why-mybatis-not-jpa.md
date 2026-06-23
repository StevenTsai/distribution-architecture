# ADR: Why MyBatis over JPA

[中文](../../decisions/why-mybatis-not-jpa.md) | English

> Status: Accepted
> Date: Initial design phase
> Decision Maker: Architecture Team

---

## Background

The two mainstream choices for Java persistence layer frameworks:

1. **MyBatis**: SQL mapping framework, hand-written SQL, XML/annotation configuration
2. **JPA (Hibernate)**: ORM framework, object-relational mapping, auto-generated SQL

This project chose MyBatis.

## Decision

Use MyBatis Spring Boot Starter, SQL written in XML Mapper files, Entities are plain POJOs.

## Alternatives Considered

### Option A: Spring Data JPA

| Dimension | JPA | MyBatis |
|-----------|-----|---------|
| SQL Control | Auto-generated; complex queries need JPQL or Native SQL | Fully hand-written, precise control |
| Learning Curve | Need to understand ORM mapping, lazy loading, L1/L2 cache | Just need to know SQL |
| Complex Queries | JPQL syntax limited; Native SQL breaks abstraction | Native SQL, no restrictions |
| Performance Tuning | Need to understand Hibernate's cache and flush mechanisms | SQL is the final executed SQL |
| Multi-table JOIN | `@OneToMany` etc. annotations are convenient but prone to N+1 | Hand-written JOINs, explicit control |
| Database Migration | Entity changes auto-affect SQL | Need to sync XML changes |
| Code Volume | Entity + Repository interface is enough | Entity + Mapper interface + XML |

### Option B: MyBatis-Plus

| Dimension | MyBatis-Plus | This Project's MyBatis |
|-----------|-------------|----------------------|
| Simple CRUD | Auto-generated, no XML needed | Need hand-written XML |
| Complex Queries | Wrapper API or custom SQL | Pure hand-written XML |
| Flexibility | Wrapper has limits, extremely complex queries still need hand-written | No restrictions |
| Learning Cost | Need to learn Wrapper API | Just need SQL |
| Code Intrusion | Need to extend BaseMapper | Pure interface, no inheritance |

## Reasons for Choosing MyBatis

### 1. Business Query Complexity

Distribution system query scenarios are inherently complex:

```sql
-- Lead list query: needs business conditions + data permissions + pagination
SELECT <include refid="BaseColumnList"/>
FROM distribution_lead
WHERE deleted = 0
  AND (patient_name LIKE '%张%' OR patient_phone LIKE '%张%')  -- Fuzzy search
  AND source_distributor_id IN (1, 5, 23, 47)                  -- Data permissions
  AND stage = 'signed'                                           -- Business filter
ORDER BY create_time DESC, id DESC
LIMIT 0, 10
```

Writing this kind of query with JPA's `@Query` or JPQL has syntax limitations and isn't intuitive. With MyBatis, you write SQL directly — what you see is what you get.

### 2. SQL-Layer Data Permission Injection

One of this project's core designs is SQL-layer data permission injection (see [data-permission-model.md](../architecture/data-permission-model.md)). This requires dynamically appending `WHERE ... AND distributor_id IN (...)` conditions in SQL.

MyBatis's `<sql>` fragment + `<include>` mechanism naturally supports this pattern:

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

To achieve similar results with JPA, you'd need:
- Either use Specification to dynamically build CriteriaQuery (complex and unintuitive)
- Or use interceptors to auto-modify generated SQL (black magic, hard to debug)

### 3. Performance Controllability

Admin panel report queries (e.g., data dashboards, commission summaries) need precise control over SQL execution plans:

```xml
<!-- Commission settlement query: precise control over JOINs and aggregation -->
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

MyBatis SQL is the final executed SQL — no ORM "translation layer." Developers can directly use `EXPLAIN` to analyze query plans and optimize indexes.

### 4. Team Skill Set

- The development team is proficient in SQL, not familiar with Hibernate's cache mechanisms and lazy loading pitfalls
- During debugging, SQL from XML can be copied directly to a database client for execution
- New members only need "know SQL + know MyBatis XML" to get started

### 5. Consistency with Existing System

This project was extracted from an existing system that already uses MyBatis. Maintaining consistency allows:
- Reuse of existing Mapper XMLs and Entities
- Shared database connection pools and MyBatis configuration
- Zero migration cost

## Trade-offs

### Costs Paid

1. **Higher Code Volume**: Each table needs Entity + Mapper interface + XML (3 files)
   - Mitigation: MyBatis Generator can auto-generate basic code
   - Mitigation: Most Entities and Mappers in this project are Generator-generated

2. **Table Structure Changes Require Multi-Point Sync**: Adding a field requires changing Entity + XML + DTO
   - Mitigation: Use `insertSelective` / `updateByPrimaryKeySelective` to only operate on changed fields
   - Mitigation: IDEA's MyBatis plugin can assist with synchronization

3. **No Automatic Dirty Checking or Cascade Updates**: JPA's `@Transactional` + auto-flush mechanism needs manual implementation in MyBatis
   - Mitigation: All write operations in this project explicitly call `updateByPrimaryKeySelective`, making intent clear

4. **No Compile-Time SQL Validation**: SQL syntax errors in XML are only discovered at runtime
   - Mitigation: MyBatis's `@Param` annotations + IDEA plugins can do basic checks during editing

### Benefits Gained

1. SQL fully controllable, no restrictions on complex queries
2. SQL-layer data permission injection is simple to implement
3. Performance tuning is intuitive (SQL → EXPLAIN → Optimize)
4. Low learning curve, fast team onboarding
5. Compatible with existing system, zero migration cost

## Future Evolution

If needed in the future:

1. **Reduce Simple CRUD XML Code**: Introduce MyBatis-Plus's `BaseMapper`; complex queries still use XML
2. **Auto-Generate Basic Code**: Configure MyBatis Generator to auto-generate Entity + Mapper from database tables
3. **Hybrid Usage**: Simple queries use MyBatis-Plus Wrapper, complex queries use XML (this project already partially adopts this pattern)

---

