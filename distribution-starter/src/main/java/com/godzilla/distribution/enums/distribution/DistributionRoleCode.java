package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionRoleCode {
    DIST_SUPER_ADMIN("DIST_SUPER_ADMIN"),
    DIST_OPERATOR("DIST_OPERATOR"),
    DIST_SALES("DIST_SALES"),
    DIST_FINANCE("DIST_FINANCE"),
    DIST_COMPLIANCE("DIST_COMPLIANCE"),
    DIST_ANALYST("DIST_ANALYST");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionRoleCode roleCode : values()) {
            CODE_SET.add(roleCode.code);
        }
    }

    private final String code;

    DistributionRoleCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
