# Quick Start: 5 分钟跑起来

中文 | [English](../en/guides/quick-start.md)

> 从零开始，让分销管理系统在本地跑起来。

---

## 前置条件

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| Java | 8+ | `java -version` 检查 |
| Maven | 3.6+ | `mvn -version` 检查 |
| MySQL | 8.0 | 本地或远程均可 |
| Redis | 5.0+ | 本项目配置了 Redis 但核心功能不强依赖 |

## 第一步：克隆项目

```bash
git clone <repo-url>
cd distribution-server
```

## 第二步：创建数据库

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE distribution_server DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 第三步：执行建表脚本

```bash
# 共享表（用户、患者、管理员、会话）
mysql -u root -p distribution_server < 01_shared_tables.sql

# 分销业务表（19 张）
mysql -u root -p distribution_server < 02_distribution_tables.sql
```

执行完成后应该有 23 张表：

```sql
mysql -u root -p -e "USE distribution_server; SHOW TABLES;"
```

## 第四步：配置数据库连接

编辑 `application-dev.properties`：

```properties
# 修改为你的数据库连接
spring.datasource.url=jdbc:mysql://localhost:3306/distribution_server?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your_password
```

> **注意**：该文件中可能包含远程测试数据库的连接信息，请替换为你本地的配置。

## 第五步：配置 Redis（可选）

`application.properties` 中配置了 Redis 连接池。如果本地没有 Redis，可以：

**方案 A：启动 Redis**

```bash
# macOS
brew install redis
brew services start redis

# Docker
docker run -d -p 6379:6379 redis:7
```

**方案 B：注释掉 Redis 配置**

如果暂时不需要 Redis，可以在 `application.properties` 中注释掉 Redis 相关配置。Spring Boot 在找不到 Redis 连接时会降级（但某些功能可能报错）。

## 第六步：启动应用

```bash
# 方式 1：Maven 启动
mvn spring-boot:run

# 方式 2：IDE 启动
# 运行 DistributionApplication.java 的 main 方法
```

启动成功后会看到：

```
Started DistributionApplication in X.XXX seconds
```

## 第七步：验证

### 访问 Swagger UI

打开浏览器访问：

```
http://localhost:9030/swagger-ui.html
```

你会看到所有 API 的文档，可以在线测试。

### 测试 API

```bash
# 健康检查（如果有的话）
curl http://localhost:9030/api/manage/distribution/distributors

# 注意：大部分接口需要认证，需要先登录获取 token
# 登录接口不在本服务中（在用户服务中）
```

### 需要准备测试数据

由于登录接口不在本服务中，你需要手动在数据库中插入测试数据：

```sql
-- 1. 插入管理员用户
INSERT INTO admin_user (id, username, password, status, user_id, create_time, modify_time)
VALUES (1, 'admin', MD5('admin123'), 1, 1, NOW(), NOW());

-- 2. 插入业务用户
INSERT INTO medical_user_info (id, name, phone, create_time, modify_time)
VALUES (1, '测试管理员', '13800138000', NOW(), NOW());

-- 3. 插入登录 session
INSERT INTO user_login_session (skey, openid, biz, login_source, expire_time, create_time)
VALUES ('test-token-123', '1', 'distribution-starter', 'MANAGE', DATE_ADD(NOW(), INTERVAL 7 DAY), NOW());

-- 4. 插入分销成员
INSERT INTO distribution_distributor_member
(distributor_id, user_id, name, phone, role_code, data_scope, status, deleted, create_time, modify_time)
VALUES (NULL, 1, '测试管理员', '13800138000', 'DIST_SUPER_ADMIN', 'ALL', 'active', 0, NOW(), NOW());
```

然后用 token 调用 API：

```bash
# 查询渠道列表
curl -H "Authorization: test-token-123" \
     -H "x-biz: distribution-starter" \
     http://localhost:9030/api/manage/distribution/distributors
```

## 项目结构速览

```
src/main/java/com/godzilla/distribution/
├── DistributionApplication.java     ← 启动类
├── controller/                      ← API 层
│   ├── admin/distribution/          ← 管理端 13 个 Controller
│   └── partner/                     ← 合作方门户 1 个 Controller
├── service/                         ← 业务逻辑层
│   └── distribution/impl/           ← 13 个 Service 实现
├── mapper/                          ← 数据访问层
│   └── distribution/                ← 19 个 MyBatis Mapper
├── entity/                          ← 数据库实体
├── dto/                             ← 请求/响应 DTO
├── enums/                           ← 业务枚举
├── common/                          ← 公共类（Result、注解、异常）
├── config/                          ← 配置类
└── exception/                       ← 异常类


├── application.properties           ← 主配置
├── application-dev.properties       ← 开发环境配置
├── mybatis-config.xml               ← MyBatis 全局配置
└── mapper/                          ← MyBatis XML 文件
```

## 常见问题

### Q: 启动报 `Communications link failure`

数据库连接失败。检查：
- MySQL 是否启动
- `application-dev.properties` 中的连接信息是否正确
- 端口是否正确（默认 3306）

### Q: 启动报 `Unknown database 'distribution_server'`

先执行第二步创建数据库。

### Q: Swagger UI 打不开

确认端口是否正确。默认是 9030：
```
http://localhost:9030/swagger-ui.html
```

### Q: API 返回 401

需要在请求头中带上 `Authorization` 和 `x-biz`：
```
Authorization: <token>
x-biz: distribution-starter
```

### Q: Redis 连接失败

Redis 不是核心功能的强依赖。如果暂时不需要，可以忽略 Redis 相关的错误，或启动一个本地 Redis 实例。

## 下一步

- 阅读 [架构设计文档](../architecture/) 了解核心设计模式
- 阅读 [扩展新模块指南](extend-new-module.md) 了解如何添加新功能
- 阅读 [数据权限模型](../architecture/data-permission-model.md) 了解权限控制机制
