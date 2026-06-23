package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DistributionComplianceRecordDetailDTO {
    private Long id;
    private String bizType;
    private Long bizId;
    private String productLineCode;
    private String recordType;
    private String status;
    private String content;
    private List<String> attachments;
    private Long reviewedBy;
    private Date reviewedAt;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
}
