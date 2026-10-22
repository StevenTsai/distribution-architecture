# Distribution Starter

[中文](https://github.com/StevenTsai/distribution-architecture/blob/main/distribution-starter/README.md) | English

A runnable skeleton for a distribution management system, built on the [distribution-architecture](../README.md) design documents. It demonstrates how core patterns — data permissions, audit logging, compliance checks — are implemented in a Spring Boot + MyBatis stack.

## What Is This

This is a **minimal runnable example**: clone it and run it to experience the core design patterns first-hand. It includes:

- **Session token authentication** — server-side session management with active invalidation
- **4-level data permissions** — row-level filtering injected at the SQL layer (ALL / OWN_DISTRIBUTOR / OWN_AND_CHILDREN / SELF)
- **MyBatis three-part SQL** — BaseColumnList + ConditionWhere + ScopeCondition
- **Audit logging** — JSON snapshots + a unified entry point
- **Compliance records** — actively managed compliance checks
- **PII encryption** — mobile numbers encrypted with AES-GCM before persisting, HMAC lookup keys, masked list views (see `common/pii/PiiCipher.java`)

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0

### 1. Create the Database

```sql
CREATE DATABASE distribution_starter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Initialize the Schema

Run the SQL scripts in order:

```bash
mysql -u root -p distribution_starter < src/main/script/sql/01_shared_tables.sql
mysql -u root -p distribution_starter < src/main/script/sql/02_distribution_tables.sql
```

### 3. Update the Configuration

Edit `src/main/resources/application-dev.properties` and update the database connection settings:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/distribution_starter?...
spring.datasource.username=root
spring.datasource.password=your_password_here
```

The PII encryption key is controlled by `app.pii.key`: development defaults to `dev-only-change-me`, while production must inject it through the `PII_KEY` environment variable — otherwise the application refuses to start.

### 4. Start the Application

```bash
mvn spring-boot:run
```

### 5. Verify

- Swagger UI: http://localhost:9030/swagger-ui.html
- Test endpoints require the following request headers:
  - `x-biz: distribution-starter`
  - `Authorization: test-token-admin` (a test token preconfigured for the development environment)

### Run the Tests

```bash
mvn test
```

The tests use an in-memory H2 database, so no extra configuration is needed.

## Project Structure

```
src/main/java/com/godzilla/distribution/
├── DistributionApplication.java         Application entry point
├── common/                              Infrastructure
│   ├── Result.java                      Unified response wrapper
│   ├── ResultCode.java                  Error code enum
│   ├── GlobalExceptionHandler.java      Global exception handling
│   ├── annotation/                      Custom annotations
│   │   ├── SessionAuth.java             Authentication marker
│   │   └── OpsApi.java                  Environment-conditional annotation
│   ├── aspect/
│   │   └── ControllerRequestLogAspect   Request logging aspect
│   └── config/
│       └── CommonConfig.java            Shared configuration
├── config/
│   └── WebConfig.java                   Interceptors + CORS
├── controller/                          Controller layer
│   └── interceptors/
│       └── AuthHeaderInterceptor.java   Authentication interceptor
├── entity/                              Entity classes
│   ├── distribution/                    Distribution domain entities
│   └── shared/                          Shared entities (user, session)
├── enums/                               Enum definitions
├── mapper/                              MyBatis mappers
│   ├── distribution/                    Distribution mapper interfaces
│   └── shared/                          Shared mapper interfaces
├── service/                             Business logic layer
│   └── distribution/
│       └── impl/
│           ├── DistributionDataPermissionService  Core: data permission service
│           └── ...
└── dto/                                 Data transfer objects
```

## Core Design Patterns

### Data Permissions Injected into SQL

Instead of manually filtering data in every Service method, permission conditions are injected into SQL through MyBatis `<sql>` fragments:

```xml
<!-- ScopeCondition: data permission filtering -->
<sql id="ScopeCondition">
    <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
        and id in
        <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </if>
</sql>
```

Developers only choose between "query with permissions" and "query without permissions":

```java
// With permission filtering
mapper.selectByConditionWithScope(..., scope.getAuthorizedDistributorIds());

// Without permission filtering (admin scenarios)
mapper.selectByCondition(...);
```

### Audit Log JSON Snapshots

Every mutating operation automatically records before/after snapshots:

```json
{
  "bizType": "lead",
  "bizId": 1,
  "action": "update_stage",
  "beforeSnapshot": "{\"stage\":\"pending_review\"}",
  "afterSnapshot": "{\"stage\":\"contacted\"}"
}
```

## Mapping to the Design Documents

| Design document | Implementation in the Starter |
|-----------------|-------------------------------|
| [Data Permission Model](../architecture/data-permission-model.md) | `DistributionDataPermissionService` + the `ScopeCondition` SQL fragment |
| [Audit Log](../architecture/audit-and-compliance.md) | `DistributionAuditLogService` + JSON snapshots |
| [MyBatis Engineering](../architecture/mybatis-patterns.md) | Three-part SQL fragments + dual query methods |
| [Session vs JWT](../decisions/why-session-not-jwt.md) | `AuthHeaderInterceptor` + the `user_login_session` table |

> **Note**: the commission pipeline (event-driven state machine + rule snapshots + reversal) and the settlement flow are not included in the Starter because of their complexity. See the [commission pipeline document](../architecture/commission-pipeline.md) for the full implementation.

## Tech Stack

- **Spring Boot 3.2.5**
- **Java 17**
- **MyBatis 3.0.3**
- **MySQL 8.0**
- **SpringDoc 2.3.0** (OpenAPI 3 / Swagger UI)
- Redis (optional, not enabled in the current version)

## Related Documentation

- [Architecture design documents](../architecture/) — detailed design patterns
- [Build-from-scratch tutorial](../../tutorial/) — a complete 7-step narrative (Chinese)
- [Spring Boot 3.x migration guide](../guides/spring-boot-3-migration.md) — upgrading to Java 17+
- [Contributing guide](../CONTRIBUTING.md) — how to get involved

## License

[Apache License 2.0](../../LICENSE)
