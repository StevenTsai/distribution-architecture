package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionAuditLogDTO {
    private Long id;
    private String bizType;
    private Long bizId;
    private String action;
    private Long operatorUserId;
    private String operatorName;
    private String remark;
    private Date createTime;
}
