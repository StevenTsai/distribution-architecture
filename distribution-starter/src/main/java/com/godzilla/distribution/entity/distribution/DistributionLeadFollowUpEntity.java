package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionLeadFollowUpEntity {
    private Long id;
    private Long leadId;
    private String followUpType;
    private String content;
    private Date nextActionAt;
    private Long createdBy;
    private Date createTime;
}
