package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionLeadFollowUpDTO {
    private Long id;
    private Long leadId;
    private String followUpType;
    private String content;
    private Date nextActionAt;
    private Long createdBy;
    private String createdByName;
    private Date createTime;
}
