package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionAuditLogEntity {
    private Long id;
    private String bizType;
    private Long bizId;
    private String action;
    private String beforeSnapshot;
    private String afterSnapshot;
    private Long operatorUserId;
    private String operatorName;
    private String remark;
    private Date createTime;
}
