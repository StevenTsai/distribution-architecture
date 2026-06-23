# Quick Start: Get Running in 5 Minutes

[中文](../../guides/quick-start.md) | English

> Get the distribution management system running locally from scratch.

---

## Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| Java | 17+ | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| MySQL | 8.0 | Local or remote |
| Redis | 5.0+ | Optional — core features don't heavily depend on it |

## Step 1: Clone the Project

```bash
git clone <repo-url>
cd distribution-starter
```

## Step 2: Create the Database

```bash
# Log into MySQL
mysql -u root -p

# Create the database
CREATE DATABASE distribution_starter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## Step 3: Run the Table Creation Scripts

```bash
# Shared tables (users, admins, sessions)
mysql -u root -p distribution_starter < src/main/script/sql/01_shared_tables.sql

# Distribution business tables
mysql -u root -p distribution_starter < src/main/script/sql/02_distribution_tables.sql
```

After execution, you should have all required tables:

```sql
mysql -u root -p -e "USE distribution_starter; SHOW TABLES;"
```

## Step 4: Configure Database Connection

Edit `application-dev.properties`:

```properties
# Update to your database connection
spring.datasource.url=jdbc:mysql://localhost:3306/distribution_starter?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your_password
```

> **Note**: This file may contain remote test database connection info. Replace with your local configuration.

## Step 5: Configure Redis (Optional)

`application.properties` configures a Redis connection pool. If you don't have Redis locally:

**Option A: Start Redis**

```bash
# macOS
brew install redis
brew services start redis

# Docker
docker run -d -p 6379:6379 redis:7
```

**Option B: Comment Out Redis Configuration**

If you don't need Redis temporarily, comment out the Redis-related configuration in `application.properties`. Spring Boot will degrade gracefully (though some features may throw errors).

## Step 6: Start the Application

```bash
# Option 1: Maven
mvn spring-boot:run

# Option 2: IDE
# Run the main method in DistributionApplication.java
```

On successful startup, you'll see:

```
Started DistributionApplication in X.XXX seconds
```

## Step 7: Verify

### Access Swagger UI

Open your browser:

```
http://localhost:9030/swagger-ui.html
```

You'll see all API documentation and can test them online.

### Test the API

```bash
# Health check (if available)
curl http://localhost:9030/api/manage/distribution/distributors

# Note: Most endpoints require authentication. You need to log in first to get a token.
# The login endpoint is not in this service (it's in the user service).
```

### Prepare Test Data

Since the login endpoint is not in this service, you need to manually insert test data:

```sql
-- 1. Insert admin user
INSERT INTO admin_user (id, username, password, status, user_id, create_time, modify_time)
VALUES (1, 'admin', MD5('admin123'), 1, 1, NOW(), NOW());

-- 2. Insert business user
INSERT INTO medical_user_info (id, name, phone, create_time, modify_time)
VALUES (1, 'Test Admin', '13800138000', NOW(), NOW());

-- 3. Insert login session
INSERT INTO user_login_session (skey, openid, biz, login_source, expire_time, create_time)
VALUES ('test-token-123', '1', 'distribution-starter', 'MANAGE', DATE_ADD(NOW(), INTERVAL 7 DAY), NOW());

-- 4. Insert distribution member
INSERT INTO distribution_distributor_member
(distributor_id, user_id, name, phone, role_code, data_scope, status, deleted, create_time, modify_time)
VALUES (NULL, 1, 'Test Admin', '13800138000', 'DIST_SUPER_ADMIN', 'ALL', 'active', 0, NOW(), NOW());
```

Then call the API with the token:

```bash
# Query distributor list
curl -H "Authorization: test-token-123" \
     -H "x-biz: distribution-starter" \
     http://localhost:9030/api/manage/distribution/distributors
```

## Project Structure Overview

```
src/main/java/com/godzilla/distribution/
├── DistributionApplication.java     ← Entry point
├── controller/                      ← API layer
│   ├── admin/distribution/          ← Admin side: 13 Controllers
│   └── partner/                     ← Partner portal: 1 Controller
├── service/                         ← Business logic layer
│   └── distribution/impl/           ← 13 Service implementations
├── mapper/                          ← Data access layer
│   └── distribution/                ← 19 MyBatis Mappers
├── entity/                          ← Database entities
├── dto/                             ← Request/Response DTOs
├── enums/                           ← Business enums
├── common/                          ← Common classes (Result, annotations, exceptions)
├── config/                          ← Configuration classes
└── exception/                       ← Exception classes

├── application.properties           ← Main config
├── application-dev.properties       ← Dev environment config
├── mybatis-config.xml               ← MyBatis global config
└── mapper/                          ← MyBatis XML files
```

## FAQ

### Q: Startup throws `Communications link failure`

Database connection failed. Check:
- Is MySQL running?
- Are the connection details in `application-dev.properties` correct?
- Is the port correct (default 3306)?

### Q: Startup throws `Unknown database 'distribution_starter'`

Run Step 2 first to create the database.

### Q: Swagger UI won't open

Confirm the port is correct. Default is 9030:
```
http://localhost:9030/swagger-ui.html
```

### Q: API returns 401

You need to include `Authorization` and `x-biz` in the request headers:
```
Authorization: <token>
x-biz: distribution-starter
```

### Q: Redis connection failure

Redis is not a hard dependency for core features. If you don't need it temporarily, you can ignore Redis-related errors or start a local Redis instance.

## Next Steps

- Read the [Architecture Design Documents](../architecture/) to understand core design patterns
- Read the [Extend New Module Guide](extend-new-module.md) to learn how to add new features
- Read the [Data Permission Model](../architecture/data-permission-model.md) to understand permission control mechanisms
