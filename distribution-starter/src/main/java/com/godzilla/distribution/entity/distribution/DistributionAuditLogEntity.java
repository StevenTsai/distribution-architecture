package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionAuditLogEntity {
    private Long id;
    private String bizType;
    private Long bizId;
    /** 归属渠道ID（冗余列，用于租户行过滤；biz_id 与渠道ID独立自增，不能互相替代） */
    private Long distributorId;
    private String action;
    private String beforeSnapshot;
    private String afterSnapshot;
    private Long operatorUserId;
    private String operatorName;
    private String remark;
    private Date createTime;
}
