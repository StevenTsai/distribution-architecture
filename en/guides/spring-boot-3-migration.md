# Spring Boot 3.x Migration Guide

[中文](../../guides/spring-boot-3-migration.md) | English

> Migrating distribution-starter from Spring Boot 2.7.18 to Spring Boot 3.x (Java 17+)

---

## Overview

Spring Boot 3.x introduces the following major changes:

| Change | Impact |
|--------|--------|
| Java 17 minimum requirement | All code must be compatible with Java 17 |
| Jakarta EE 9+ | `javax.*` package names become `jakarta.*` |
| Spring Security 6 | Security configuration approach changes |
| SpringDoc 2.x | Swagger configuration approach changes |
| MyBatis Spring Boot 3.x | Starter version must be upgraded |

## Migration Steps

### 1. Upgrade the Java Version

```xml
<!-- pom.xml -->
<properties>
    <java.version>17</java.version>
</properties>
```

### 2. Upgrade the Spring Boot Parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version> <!-- or the latest stable version -->
    <relativePath/>
</parent>
```

### 3. Upgrade the MyBatis Starter

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version> <!-- compatible with Spring Boot 3.x -->
</dependency>
```

### 4. Upgrade the MySQL Connector

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> Note: the groupId changes from `mysql:mysql-connector-java` to `com.mysql:mysql-connector-j`

### 5. Upgrade SpringDoc

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

> Note: the artifactId changes from `springdoc-openapi-ui` to `springdoc-openapi-starter-webmvc-ui`

### 6. Migrate javax → jakarta Package Names

This is the largest change. The following imports must be replaced globally:

```java
// Old (Spring Boot 2.x)
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

// New (Spring Boot 3.x)
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
```

**Files to modify**:

| File | javax imports involved |
|------|------------------------|
| `AuthHeaderInterceptor.java` | `javax.servlet.http.*` |
| `ControllerRequestLogAspect.java` | `javax.servlet.http.*`, `javax.validation.*` |
| `WebConfig.java` | No direct imports, but its dependent classes have changed |
| `@Valid` in all Controllers | `javax.validation.Valid` |

**Bulk replacement commands**:

```bash
# From the distribution-starter directory
find . -name "*.java" -exec sed -i '' 's/import javax\.servlet/import jakarta.servlet/g' {} +
find . -name "*.java" -exec sed -i '' 's/import javax\.validation/import jakarta.validation/g' {} +
```

### 7. Update application.properties

```properties
# HikariCP configuration prefix changes in Spring Boot 3.x
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# MyBatis configuration is unchanged
mybatis.config-location=classpath:/mybatis-config.xml
mybatis.mapper-locations=classpath:/mapper/*.xml,classpath:/mapper/**/*.xml
```

### 8. Update Test Dependencies

```xml
<!-- JUnit 5 is the default test framework in Spring Boot 3.x -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<!-- If JUnit 4 compatibility is needed -->
<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <scope>test</scope>
</dependency>
```

Test classes need updated annotations:

```java
// Old
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MyTest {
    @Test
    public void testSomething() { }
}

// New
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MyTest {
    @Test
    void testSomething() { }
}
```

### 9. Handle Deprecated APIs

#### Spring MVC Path Matching

Spring Boot 3.x uses `PathPatternParser` by default and no longer supports some features of `AntPathMatcher`. If you rely on suffix matching such as `**/*.json`, configure:

```properties
spring.mvc.pathmatch.matching-strategy=ant_path_matcher
```

#### CORS Configuration

`allowedOriginPatterns("*")` must be explicitly configured in Spring Boot 3.x:

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOriginPatterns("*") // unchanged
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

## Complete pom.xml Example

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

## Migration Checklist

- [ ] Java version upgraded to 17+
- [ ] Spring Boot Parent upgraded to 3.x
- [ ] MyBatis Starter upgraded to 3.0.x
- [ ] MySQL Connector upgraded to `com.mysql:mysql-connector-j`
- [ ] SpringDoc upgraded to 2.x
- [ ] All `javax.servlet` replaced with `jakarta.servlet`
- [ ] All `javax.validation` replaced with `jakarta.validation`
- [ ] Tests migrated from JUnit 4 to JUnit 5
- [ ] Compilation passes: `mvn clean compile`
- [ ] Tests pass: `mvn test`
- [ ] Startup succeeds: `mvn spring-boot:run`
- [ ] Swagger UI is accessible: `http://localhost:9030/swagger-ui.html`

## FAQ

### Q: Startup reports `ClassNotFoundException: javax.servlet.http.HttpServletRequest`

A: You missed the `javax` → `jakarta` replacement. Check the import statements in all Java files.

### Q: MyBatis Mapper cannot be scanned

A: Make sure the package path in the `@MapperScan` annotation is correct and the MyBatis Starter version is 3.0.x.

### Q: Swagger UI returns 404

A: The default path in SpringDoc 2.x changed to `/swagger-ui/index.html`, and the configuration also needs updating:
```properties
springdoc.swagger-ui.path=/swagger-ui.html
```
