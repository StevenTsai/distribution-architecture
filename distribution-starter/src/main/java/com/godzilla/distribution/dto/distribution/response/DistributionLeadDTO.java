package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionLeadDTO {
    private Long id;
    private String leadNo;
    private String patientName;
    private String patientPhone;
    private Long sourceDistributorId;
    private String sourceDistributorName;
    private Long sourceMemberId;
    private String sourceMemberName;
    private String intentProductLine;
    private DistributionProductLineDTO productLineMeta;
    private String sourceRegion;
    private String sourceChannel;
    private Long ownerUserId;
    private String ownerUserName;
    private String stage;
    private Integer isDuplicate;
    private Long duplicateLeadId;
    private String invalidReason;
    private String remark;
    private Date latestFollowUpAt;
    private Date createTime;
    private Date modifyTime;
}
