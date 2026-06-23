package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionLeadStage {
    PENDING_REVIEW("pending_review"),
    INVALID("invalid"),
    CONTACTED("contacted"),
    INTERESTED("interested"),
    SCHEDULED("scheduled"),
    VISITED("visited"),
    SIGNED("signed"),
    CONVERTED("converted"),
    CLOSED("closed");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionLeadStage stage : values()) {
            CODE_SET.add(stage.code);
        }
    }

    private final String code;

    DistributionLeadStage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
