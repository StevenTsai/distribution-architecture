package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionLeadEntity {
    private Long id;
    private String leadNo;
    private String patientName;
    private String patientPhone;
    private Long sourceDistributorId;
    private Long sourceMemberId;
    private String intentProductLine;
    private String sourceRegion;
    private String sourceChannel;
    private Long ownerUserId;
    private String stage;
    private Integer isDuplicate;
    private Long duplicateLeadId;
    private String invalidReason;
    private String remark;
    private Date latestFollowUpAt;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
    private Integer deleted;
}
