package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionDistributorStatus {
    PENDING("pending"),
    REVIEWING("reviewing"),
    ACTIVE("active"),
    FROZEN("frozen"),
    DISABLED("disabled"),
    REJECTED("rejected");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionDistributorStatus status : values()) {
            CODE_SET.add(status.code);
        }
    }

    private final String code;

    DistributionDistributorStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
