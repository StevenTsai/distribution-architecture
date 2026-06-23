package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionMemberStatus {
    ACTIVE("active"),
    DISABLED("disabled"),
    FROZEN("frozen");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionMemberStatus status : values()) {
            CODE_SET.add(status.code);
        }
    }

    private final String code;

    DistributionMemberStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
