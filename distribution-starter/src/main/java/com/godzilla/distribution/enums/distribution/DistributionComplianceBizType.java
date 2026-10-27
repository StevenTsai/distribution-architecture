package com.godzilla.distribution.enums.distribution;

import java.util.HashSet;
import java.util.Set;

/**
 * 合规记录业务类型：合规记录挂靠的业务对象类型。
 * distributor 的 bizId 即渠道ID；member/lead 的 bizId 与渠道ID独立自增，需解析归属渠道。
 */
public enum DistributionComplianceBizType {
    DISTRIBUTOR("distributor"),
    MEMBER("member"),
    POLICY("policy"),
    LEAD("lead");

    private static final Set<String> CODE_SET = new HashSet<String>();

    static {
        for (DistributionComplianceBizType type : values()) {
            CODE_SET.add(type.code);
        }
    }

    private final String code;

    DistributionComplianceBizType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return CODE_SET.contains(code);
    }
}
