package com.godzilla.distribution.controller.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.Result;
import com.godzilla.distribution.common.annotation.SessionAuth;
import com.godzilla.distribution.entity.shared.AdminUserEntity;
import com.godzilla.distribution.entity.shared.UserLoginSessionEntity;
import com.godzilla.distribution.mapper.shared.AdminUserEntityMapper;
import com.godzilla.distribution.mapper.shared.UserLoginSessionEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

/**
 * 认证拦截器
 * 基于 Session Token 的会话认证，直接使用 Mapper 查询
 */
@Slf4j
@Component
public class AuthHeaderInterceptor implements HandlerInterceptor {

    private static final String LOGIN_SOURCE_MANAGE = "MANAGE";
    private static final String BIZ_HEADER_VALUE = "distribution-starter";
    private static final String MANAGER_PATH_PATTERN = "/manage";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private final AdminUserEntityMapper adminUserDao;
    private final UserLoginSessionEntityMapper userLoginSessionDao;

    public AuthHeaderInterceptor(AdminUserEntityMapper adminUserDao,
                                 UserLoginSessionEntityMapper userLoginSessionDao) {
        this.adminUserDao = adminUserDao;
        this.userLoginSessionDao = userLoginSessionDao;
    }

    public static String getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        CURRENT_USER_ID.remove();

        String requestURI = request.getRequestURI();

        if (handler instanceof org.springframework.web.method.HandlerMethod) {
            org.springframework.web.method.HandlerMethod handlerMethod = (org.springframework.web.method.HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            if (method.isAnnotationPresent(SessionAuth.class) || method.getDeclaringClass().isAnnotationPresent(SessionAuth.class)) {
                String biz = request.getHeader("x-biz");
                String token = request.getHeader("Authorization");

                if (!isValidBiz(biz)) {
                    writeResponse(response, 401, Result.authFail());
                    return false;
                }
                if (!isValidToken(token)) {
                    writeResponse(response, 401, Result.loginFail());
                    return false;
                }

                try {
                    return handleManagerAuth(response, biz, token);
                } catch (Exception e) {
                    log.error("认证用户身份时发生异常，biz={}, token={}", biz, maskToken(token), e);
                    writeResponse(response, 401, Result.loginFail());
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CURRENT_USER_ID.remove();
    }

    private boolean isValidBiz(String biz) {
        return BIZ_HEADER_VALUE.equals(biz);
    }

    private boolean isValidToken(String token) {
        return token != null && !token.isEmpty();
    }

    private boolean handleManagerAuth(HttpServletResponse response, String biz, String token) {
        try {
            return handleBackendAuth(response, biz, token, LOGIN_SOURCE_MANAGE);
        } catch (Exception e) {
            log.error("获取管理后台 session 失败，biz={}, token={}", biz, maskToken(token), e);
            writeResponse(response, 401, Result.loginFail());
            return false;
        }
    }

    /**
     * 日志脱敏：token 仅保留前 8 位，其余以 *** 替代，避免完整凭证进入日志。
     */
    private String maskToken(String token) {
        if (StringUtils.isBlank(token)) {
            return "***";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 8) {
            return "***"; // 过短的值可能等同于完整凭证，不输出任何内容
        }
        return trimmed.substring(0, 8) + "***";
    }

    private boolean handleBackendAuth(HttpServletResponse response, String biz, String token, String expectedLoginSource) {
        UserLoginSessionEntity sessionEntity = getActiveBackendSession(biz, token);
        if (sessionEntity == null || StringUtils.isBlank(sessionEntity.getOpenid())) {
            writeResponse(response, 401, Result.loginFail());
            return false;
        }
        if (!expectedLoginSource.equalsIgnoreCase(StringUtils.trimToEmpty(sessionEntity.getLoginSource()))) {
            writeResponse(response, 401, Result.loginFail());
            return false;
        }

        Long adminUserId;
        try {
            adminUserId = Long.parseLong(sessionEntity.getOpenid());
        } catch (NumberFormatException e) {
            writeResponse(response, 401, Result.loginFail());
            return false;
        }

        AdminUserEntity adminUser = adminUserDao.selectByPrimaryKey(adminUserId);
        if (adminUser == null || adminUser.getStatus() == null || adminUser.getStatus() != (byte) 1
                || adminUser.getUserId() == null) {
            writeResponse(response, 401, Result.loginFail());
            return false;
        }

        CURRENT_USER_ID.set(String.valueOf(adminUser.getUserId()));
        return true;
    }

    private UserLoginSessionEntity getActiveBackendSession(String biz, String token) {
        List<UserLoginSessionEntity> sessions = userLoginSessionDao.selectBySkeyAndBiz(token, biz, new Date());
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        return sessions.get(0);
    }

    private void writeResponse(HttpServletResponse response, int status, Result<?> result) {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(result));
            writer.flush();
        } catch (Exception e) {
            log.warn("写入响应失败: {}", e.getMessage());
        }
    }
}
