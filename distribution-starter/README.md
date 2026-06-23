# Distribution Starter

一套可运行的分销管理系统骨架，基于 [distribution-architecture](../) 设计文档构建。展示了数据权限、审计日志、合规检查等核心模式在 Spring Boot + MyBatis 技术栈下的工程化落地。

## 这是什么

这是一个**最小可运行示例**，让你能 clone 后直接跑起来体验核心设计模式。包含：

- **Session Token 认证** — 服务端会话管理，支持主动失效
- **4 级数据权限** — SQL 层注入的行级过滤（ALL / OWN_DISTRIBUTOR / OWN_AND_CHILDREN / SELF）
- **MyBatis 三段式 SQL** — BaseColumnList + ConditionWhere + ScopeCondition
- **审计日志** — JSON 快照 + 统一入口
- **合规记录** — 主动管理型合规检查

## 快速开始

### 前置条件

- Java 17+
- Maven 3.6+
- MySQL 8.0

### 1. 创建数据库

```sql
CREATE DATABASE distribution_starter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 初始化表结构

按顺序执行 SQL 脚本：

```bash
mysql -u root -p distribution_starter < src/main/script/sql/01_shared_tables.sql
mysql -u root -p distribution_starter < src/main/script/sql/02_distribution_tables.sql
```

### 3. 修改配置

编辑 `src/main/resources/application-dev.properties`，修改数据库连接信息：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/distribution_starter?...
spring.datasource.username=root
spring.datasource.password=your_password_here
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

### 5. 验证

- Swagger UI: http://localhost:9030/swagger-ui.html
- 测试接口需要在请求头中携带：
  - `x-biz: distribution-starter`
  - `Authorization: test-token-admin`（开发环境预置的测试 token）

### 运行测试

```bash
mvn test
```

测试使用 H2 内存数据库，无需额外配置。

## 项目结构

```
src/main/java/com/godzilla/distribution/
├── DistributionApplication.java         启动类
├── common/                              基础设施
│   ├── Result.java                      统一响应封装
│   ├── ResultCode.java                  错误码枚举
│   ├── GlobalExceptionHandler.java      全局异常处理
│   ├── annotation/                      自定义注解
│   │   ├── SessionAuth.java             认证标记
│   │   └── OpsApi.java                  环境条件注解
│   ├── aspect/
│   │   └── ControllerRequestLogAspect   请求日志切面
│   └── config/
│       └── CommonConfig.java            公共配置
├── config/
│   └── WebConfig.java                   拦截器 + CORS
├── controller/                          控制器层
│   └── interceptors/
│       └── AuthHeaderInterceptor.java   认证拦截器
├── entity/                              实体类
│   ├── distribution/                    分销领域实体
│   └── shared/                          共享实体（用户、会话）
├── enums/                               枚举定义
├── mapper/                              MyBatis Mapper
│   ├── distribution/                    分销 Mapper 接口
│   └── shared/                          共享 Mapper 接口
├── service/                             业务逻辑层
│   └── distribution/
│       └── impl/
│           ├── DistributionDataPermissionService  核心：数据权限服务
│           └── ...
└── dto/                                 数据传输对象
```

## 核心设计模式

### 数据权限 SQL 下发

不在每个 Service 方法中手动过滤数据，而是通过 MyBatis 的 `<sql>` 片段将权限条件自动注入 SQL：

```xml
<!-- ScopeCondition：数据权限过滤 -->
<sql id="ScopeCondition">
    <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
        and id in
        <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </if>
</sql>
```

开发者只需要选择"用带权限的查询"还是"不带权限的查询"：

```java
// 带权限过滤
mapper.selectByConditionWithScope(..., scope.getAuthorizedDistributorIds());

// 不带权限过滤（管理员场景）
mapper.selectByCondition(...);
```

### 审计日志 JSON 快照

所有变更操作自动记录 before/after 快照：

```json
{
  "bizType": "lead",
  "bizId": 1,
  "action": "update_stage",
  "beforeSnapshot": "{\"stage\":\"pending_review\"}",
  "afterSnapshot": "{\"stage\":\"contacted\"}"
}
```

## 与设计文档的对应关系

| 设计文档 | Starter 中的实现 |
|----------|-----------------|
| [数据权限模型](../architecture/data-permission-model.md) | `DistributionDataPermissionService` + `ScopeCondition` SQL 片段 |
| [审计日志](../architecture/audit-and-compliance.md) | `DistributionAuditLogService` + JSON 快照 |
| [MyBatis 工程化](../architecture/mybatis-patterns.md) | 三段式 SQL 片段 + 对偶查询方法 |
| [Session vs JWT](../decisions/why-session-not-jwt.md) | `AuthHeaderInterceptor` + `user_login_session` 表 |

> **注意**：佣金流转（事件驱动状态机 + 规则快照 + 冲回）和结算流程因复杂度较高，未包含在 Starter 中。请参考 [佣金流转文档](../architecture/commission-pipeline.md) 和完整实现。

## 技术栈

- **Spring Boot 3.2.5**
- **Java 17**
- **MyBatis 3.0.3**
- **MySQL 8.0**
- **SpringDoc 2.3.0**（OpenAPI 3 / Swagger UI）
- Redis（可选，当前版本未启用）

## 相关文档

- [架构设计文档](../architecture/) — 核心设计模式详解
- [从零构建教程](../tutorial/) — 7 步完整叙事线
- [Spring Boot 3.x 迁移指南](../guides/spring-boot-3-migration.md) — 升级到 Java 17+
- [贡献指南](../CONTRIBUTING.md) — 如何参与贡献

## License

[Apache License 2.0](../LICENSE)
