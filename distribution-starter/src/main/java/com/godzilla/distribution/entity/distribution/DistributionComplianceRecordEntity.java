package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionComplianceRecordEntity {
    private Long id;
    private String bizType;
    private Long bizId;
    private String productLineCode;
    private String recordType;
    private String status;
    private String content;
    private String attachmentsJson;
    private Long reviewedBy;
    private Date reviewedAt;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
    private Integer deleted;
}
