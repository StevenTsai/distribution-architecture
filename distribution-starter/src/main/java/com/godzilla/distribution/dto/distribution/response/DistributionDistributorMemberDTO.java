package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DistributionDistributorMemberDTO {
    private Long id;
    private Long distributorId;
    private String distributorName;
    private Long userId;
    private String name;
    private String phone;
    private String roleCode;
    private String dataScope;
    private Long managerMemberId;
    private String managerMemberName;
    private List<String> productLines;
    private List<DistributionProductLineDTO> productLineMetas;
    private String trainingStatus;
    private String status;
    private Date lastActiveAt;
    private String remark;
    private Date createTime;
    private Date modifyTime;
}
