package com.godzilla.distribution.common;

import lombok.Getter;

public enum ResultCode {
    SUCCESS(200, "success"),
    FAIL(10000, "fail"),
    AUTH_FAIL(401, "invalid header Authorization"),
    WX_LOGIN_FAIL(20001, "wx login fail:"),
    WX_INVALID_SESSION(20002, "invalid session"),
    ADMIN_LOGIN_FAIL(20015, "admin login fail，用户名或密码错误"),
    DISTRIBUTION_OPERATOR_NOT_LOGIN(30001, "distribution operator not login"),
    DISTRIBUTION_OPERATOR_NOT_BOUND(30002, "distribution operator not bound"),
    DISTRIBUTION_OPERATOR_MULTI_BOUND(30003, "distribution operator multi bound"),
    DISTRIBUTOR_NOT_FOUND(30004, "distributor not found"),
    DISTRIBUTOR_CODE_EXISTS(30005, "distributor code exists"),
    DISTRIBUTOR_PARENT_INVALID(30006, "distributor parent invalid"),
    DISTRIBUTOR_OWNER_NOT_FOUND(30007, "distributor owner not found"),
    DISTRIBUTOR_STATUS_INVALID(30008, "distributor status invalid"),
    DISTRIBUTOR_MEMBER_NOT_FOUND(30009, "distributor member not found"),
    DISTRIBUTOR_MEMBER_DISTRIBUTOR_NOT_FOUND(30010, "distributor member distributor not found"),
    DISTRIBUTOR_MEMBER_ROLE_INVALID(30011, "distributor member role invalid"),
    DISTRIBUTOR_MEMBER_DATA_SCOPE_INVALID(30012, "distributor member data scope invalid"),
    DISTRIBUTOR_MEMBER_TRAINING_STATUS_INVALID(30013, "distributor member training status invalid"),
    DISTRIBUTOR_MEMBER_STATUS_INVALID(30014, "distributor member status invalid"),
    DISTRIBUTOR_MEMBER_PHONE_EXISTS(30015, "distributor member phone exists"),
    DISTRIBUTOR_MEMBER_USER_NOT_FOUND(30016, "distributor member user not found"),
    DISTRIBUTOR_MEMBER_USER_ALREADY_BOUND(30017, "distributor member user already bound"),
    DISTRIBUTOR_MEMBER_MANAGER_INVALID(30018, "distributor member manager invalid"),
    DISTRIBUTION_LEAD_NOT_FOUND(30033, "distribution lead not found"),
    DISTRIBUTION_LEAD_DISTRIBUTOR_NOT_FOUND(30034, "distribution lead distributor not found"),
    DISTRIBUTION_LEAD_MEMBER_NOT_FOUND(30035, "distribution lead member not found"),
    DISTRIBUTION_LEAD_PRODUCT_LINE_INVALID(30036, "distribution lead product line invalid"),
    DISTRIBUTION_LEAD_OWNER_NOT_FOUND(30037, "distribution lead owner not found"),
    DISTRIBUTION_LEAD_STAGE_INVALID(30038, "distribution lead stage invalid"),
    DISTRIBUTION_LEAD_FOLLOW_UP_TYPE_INVALID(30039, "distribution lead follow up type invalid"),
    DISTRIBUTION_AUDIT_BIZ_TYPE_INVALID(30072, "distribution audit biz type invalid"),
    DISTRIBUTION_AUDIT_LOG_NOT_FOUND(30073, "distribution audit log not found"),
    DISTRIBUTION_COMPLIANCE_RECORD_NOT_FOUND(30074, "distribution compliance record not found"),
    DISTRIBUTION_COMPLIANCE_BIZ_TYPE_INVALID(30075, "distribution compliance biz type invalid"),
    DISTRIBUTION_COMPLIANCE_RECORD_TYPE_INVALID(30076, "distribution compliance record type invalid"),
    DISTRIBUTION_COMPLIANCE_STATUS_INVALID(30077, "distribution compliance status invalid"),
    DISTRIBUTION_COMPLIANCE_STATUS_TRANSITION_INVALID(30078, "distribution compliance status transition invalid"),
    DISTRIBUTION_DATA_ACCESS_DENIED(30079, "distribution data access denied"),
    DISTRIBUTION_LEAD_PHONE_EXISTS(30080, "distribution lead phone exists"),
    DISTRIBUTION_LEAD_SOURCE_MEMBER_DISABLED(30081, "distribution lead source member disabled"),

    ;

    @Getter
    private int code;
    @Getter
    private String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
