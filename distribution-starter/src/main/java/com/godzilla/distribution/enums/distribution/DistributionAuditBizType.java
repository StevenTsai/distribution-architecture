package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionAuditBizType {
    DISTRIBUTOR("distributor"),
    MEMBER("member"),
    POLICY("policy"),
    LEAD("lead"),
    ATTRIBUTION("attribution"),
    BUSINESS_ORDER("business_order"),
    COMMISSION("commission"),
    SETTLEMENT("settlement"),
    COMPLIANCE("compliance");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionAuditBizType type : values()) {
            CODE_SET.add(type.code);
        }
    }

    private final String code;

    DistributionAuditBizType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
