package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionProductLineCode {
    GENE("gene"),
    PROTON("proton"),
    ORGANOID("organoid");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionProductLineCode code : values()) {
            CODE_SET.add(code.code);
        }
    }

    private final String code;

    DistributionProductLineCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
