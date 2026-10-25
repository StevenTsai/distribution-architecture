package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionComplianceRecordEntity {
    private Long id;
    private String bizType;
    private Long bizId;
    /** 归属渠道ID（冗余列，用于租户行过滤；biz_id 与渠道ID独立自增，不能互相替代） */
    private Long distributorId;
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
