package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionDistributorEntity {
    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private String levelCode;
    private Long ownerUserId;
    private String contactName;
    private String contactPhone;
    private String province;
    private String city;
    private String settlementType;
    private String contractStatus;
    private String qualificationStatus;
    private String productLinesJson;
    private String status;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
    private Integer deleted;
}
