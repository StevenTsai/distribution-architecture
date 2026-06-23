package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

public enum DistributionFollowUpType {
    CALL("call"),
    WECHAT("wechat"),
    VISIT("visit"),
    REMARK("remark"),
    SYSTEM("system");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionFollowUpType type : values()) {
            CODE_SET.add(type.code);
        }
    }

    private final String code;

    DistributionFollowUpType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
