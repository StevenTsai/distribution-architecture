package com.godzilla.distribution.common.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.log.aspect.enabled", havingValue = "true")
public class ControllerRequestLogAspect {

    private static final int MAX_LOG_VALUE_LENGTH = 512;

    private static final Set<String> SENSITIVE_FIELD_NAMES = new HashSet<>(Arrays.asList(
            "password",
            "pwd",
            "token",
            "secret",
            "authorization",
            "phone",
            "mobile",
            "openid",
            "openId",
            "idcard",
            "idCard"
    ));

    private final ObjectMapper objectMapper;

    @Before("execution(* com.godzilla.distribution.controller..*.*(..))")
    public void logRequestArgs(JoinPoint joinPoint) {
        CodeSignature signature = (CodeSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> requestArgs = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (shouldSkip(arg)) {
                continue;
            }

            String parameterName = parameterNames != null && i < parameterNames.length
                    ? parameterNames[i]
                    : "arg" + i;
            requestArgs.put(parameterName, sanitizeValue(parameterName, arg));
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String method = "unknown";
        String uri = "unknown";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            method = request.getMethod();
            uri = request.getRequestURI();
        }

        log.info("Controller request: {} {} {}.{} args={}",
                method,
                uri,
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                toJson(requestArgs));
    }

    private boolean shouldSkip(Object arg) {
        if (arg == null) {
            return false;
        }

        return arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile
                || arg instanceof BindingResult
                || isMultipartArray(arg)
                || isMultipartCollection(arg);
    }

    private boolean isMultipartArray(Object arg) {
        Class<?> clazz = arg.getClass();
        return clazz.isArray()
                && clazz.getComponentType() != null
                && MultipartFile.class.isAssignableFrom(clazz.getComponentType());
    }

    private boolean isMultipartCollection(Object arg) {
        if (!(arg instanceof Collection)) {
            return false;
        }

        Collection<?> collection = (Collection<?>) arg;
        if (collection.isEmpty()) {
            return false;
        }

        for (Object item : collection) {
            if (!(item instanceof MultipartFile)) {
                return false;
            }
        }
        return true;
    }

    private Object sanitizeValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }

        if (isSensitiveField(fieldName)) {
            return maskValue(value);
        }

        if (value instanceof CharSequence) {
            return truncate(value.toString());
        }

        if (value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return value;
        }

        try {
            JsonNode sanitized = sanitizeNode(null, objectMapper.valueToTree(value));
            return objectMapper.treeToValue(sanitized, Object.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return truncate(String.valueOf(value));
        }
    }

    private JsonNode sanitizeNode(String fieldName, JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                            .set(entry.getKey(), sanitizeNode(entry.getKey(), entry.getValue())));
            return node;
        }

        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, sanitizeNode(fieldName, node.get(i)));
            }
            return node;
        }

        if (isSensitiveField(fieldName)) {
            return objectMapper.getNodeFactory().textNode(maskValue(node.asText()));
        }

        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(truncate(node.asText()));
        }

        return node;
    }

    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String normalized = fieldName.replace("_", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.contains(fieldName) || SENSITIVE_FIELD_NAMES.contains(normalized);
    }

    private String maskValue(Object value) {
        return value == null ? null : "***";
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_LOG_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_VALUE_LENGTH) + "...(truncated)";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
