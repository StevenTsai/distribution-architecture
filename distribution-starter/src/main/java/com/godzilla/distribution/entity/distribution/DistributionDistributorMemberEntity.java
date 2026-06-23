package com.godzilla.distribution.entity.distribution;

import lombok.Data;

import java.util.Date;

@Data
public class DistributionDistributorMemberEntity {
    private Long id;
    private Long distributorId;
    private Long userId;
    private String name;
    private String phone;
    private String roleCode;
    private String dataScope;
    private Long managerMemberId;
    private String productLinesJson;
    private String trainingStatus;
    private String status;
    private Date lastActiveAt;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
    private Integer deleted;
}
