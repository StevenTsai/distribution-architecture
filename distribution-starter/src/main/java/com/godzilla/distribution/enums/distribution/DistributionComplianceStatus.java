package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionComplianceStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    EXPIRED("expired");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionComplianceStatus status : values()) {
            CODE_SET.add(status.code);
        }
    }

    private final String code;

    DistributionComplianceStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
