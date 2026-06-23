package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DistributionComplianceRecordDTO {
    private Long id;
    private String bizType;
    private Long bizId;
    private String productLineCode;
    private String recordType;
    private String status;
    private Long reviewedBy;
    private Date reviewedAt;
    private String remark;
    private List<String> attachments;
    private Date createTime;
}
