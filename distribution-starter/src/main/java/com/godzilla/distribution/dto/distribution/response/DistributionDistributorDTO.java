package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DistributionDistributorDTO {
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
    private List<String> productLines;
    private List<DistributionProductLineDTO> productLineMetas;
    private String status;
    private String remark;
    private Date createTime;
    private Date modifyTime;
}
