package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionDataScope {
    ALL("ALL"),
    OWN_DISTRIBUTOR("OWN_DISTRIBUTOR"),
    OWN_AND_CHILDREN("OWN_AND_CHILDREN"),
    SELF("SELF");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionDataScope scope : values()) {
            CODE_SET.add(scope.code);
        }
    }

    private final String code;

    DistributionDataScope(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
