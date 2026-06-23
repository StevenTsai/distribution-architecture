package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionComplianceRecordType {
    QUALIFICATION("qualification"),
    TRAINING("training"),
    AUTHORIZATION("authorization"),
    VIOLATION("violation"),
    APPROVAL("approval");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionComplianceRecordType type : values()) {
            CODE_SET.add(type.code);
        }
    }

    private final String code;

    DistributionComplianceRecordType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
