# Spring Boot 3.x 迁移指南

> 将 distribution-starter 从 Spring Boot 2.7.18 迁移到 Spring Boot 3.x（Java 17+）

---

## 概述

Spring Boot 3.x 带来了以下主要变化：

| 变化 | 影响 |
|------|------|
| Java 17 最低要求 | 所有代码需要兼容 Java 17 |
| Jakarta EE 9+ | `javax.*` 包名变为 `jakarta.*` |
| Spring Security 6 | 安全配置方式变化 |
| SpringDoc 2.x | Swagger 配置方式变化 |
| MyBatis Spring Boot 3.x | 需要升级 starter 版本 |

## 迁移步骤

### 1. 升级 Java 版本

```xml
<!-- pom.xml -->
<properties>
    <java.version>17</java.version>
</properties>
```

### 2. 升级 Spring Boot Parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version> <!-- 或最新稳定版 -->
    <relativePath/>
</parent>
```

### 3. 升级 MyBatis Starter

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version> <!-- 兼容 Spring Boot 3.x -->
</dependency>
```

### 4. 升级 MySQL Connector

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> 注意：groupId 从 `mysql:mysql-connector-java` 变为 `com.mysql:mysql-connector-j`

### 5. 升级 SpringDoc

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

> 注意：artifactId 从 `springdoc-openapi-ui` 变为 `springdoc-openapi-starter-webmvc-ui`

### 6. javax → jakarta 包名迁移

这是最大的变更。需要全局替换以下导入：

```java
// 旧 (Spring Boot 2.x)
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

// 新 (Spring Boot 3.x)
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
```

**需要修改的文件**：

| 文件 | 涉及的 javax 导入 |
|------|-------------------|
| `AuthHeaderInterceptor.java` | `javax.servlet.http.*` |
| `ControllerRequestLogAspect.java` | `javax.servlet.http.*`, `javax.validation.*` |
| `WebConfig.java` | 无直接导入，但依赖的类已变化 |
| 所有 Controller 中的 `@Valid` | `javax.validation.Valid` |

**批量替换命令**：

```bash
# 在 distribution-starter 目录下
find . -name "*.java" -exec sed -i '' 's/import javax\.servlet/import jakarta.servlet/g' {} +
find . -name "*.java" -exec sed -i '' 's/import javax\.validation/import jakarta.validation/g' {} +
```

### 7. 更新 application.properties

```properties
# Spring Boot 3.x 中 HikariCP 配置前缀变化
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# MyBatis 配置不变
mybatis.config-location=classpath:/mybatis-config.xml
mybatis.mapper-locations=classpath:/mapper/*.xml,classpath:/mapper/**/*.xml
```

### 8. 更新测试依赖

```xml
<!-- JUnit 5 是 Spring Boot 3.x 的默认测试框架 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<!-- 如果需要 JUnit 4 兼容 -->
<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <scope>test</scope>
</dependency>
```

测试类需要更新注解：

```java
// 旧
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MyTest {
    @Test
    public void testSomething() { }
}

// 新
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MyTest {
    @Test
    void testSomething() { }
}
```

### 9. 处理废弃 API

#### Spring MVC 路径匹配

Spring Boot 3.x 默认使用 `PathPatternParser`，不再支持 `AntPathMatcher` 的某些特性。如果依赖 `**/*.json` 这样的后缀匹配，需要配置：

```properties
spring.mvc.pathmatch.matching-strategy=ant_path_matcher
```

#### CORS 配置

`allowedOriginPatterns("*")` 在 Spring Boot 3.x 中需要显式配置：

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOriginPatterns("*") // 保持不变
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

## 完整的 pom.xml 示例

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <mybatis.version>3.0.3</mybatis.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

## 迁移检查清单

- [ ] Java 版本升级到 17+
- [ ] Spring Boot Parent 升级到 3.x
- [ ] MyBatis Starter 升级到 3.0.x
- [ ] MySQL Connector 升级到 `com.mysql:mysql-connector-j`
- [ ] SpringDoc 升级到 2.x
- [ ] 所有 `javax.servlet` 替换为 `jakarta.servlet`
- [ ] 所有 `javax.validation` 替换为 `jakarta.validation`
- [ ] 测试从 JUnit 4 迁移到 JUnit 5
- [ ] 编译通过：`mvn clean compile`
- [ ] 测试通过：`mvn test`
- [ ] 启动成功：`mvn spring-boot:run`
- [ ] Swagger UI 可访问：`http://localhost:9030/swagger-ui.html`

## 常见问题

### Q: 启动时报 `ClassNotFoundException: javax.servlet.http.HttpServletRequest`

A: 你漏掉了 `javax` → `jakarta` 的替换。检查所有 Java 文件的导入语句。

### Q: MyBatis Mapper 扫描不到

A: 确保 `@MapperScan` 注解的包路径正确，且 MyBatis Starter 版本为 3.0.x。

### Q: Swagger UI 404

A: SpringDoc 2.x 的默认路径变为 `/swagger-ui/index.html`，配置也需要更新：
```properties
springdoc.swagger-ui.path=/swagger-ui.html
```
