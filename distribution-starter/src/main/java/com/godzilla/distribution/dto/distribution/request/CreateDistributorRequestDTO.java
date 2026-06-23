package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class CreateDistributorRequestDTO {
    @NotBlank(message = "渠道编码不能为空")
    @Size(max = 32, message = "渠道编码长度不能超过32")
    private String code;

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 128, message = "渠道名称长度不能超过128")
    private String name;

    private Long parentId;

    @NotBlank(message = "渠道等级不能为空")
    @Size(max = 32, message = "渠道等级长度不能超过32")
    private String levelCode;

    private Long ownerUserId;

    @Size(max = 64, message = "联系人长度不能超过64")
    private String contactName;

    @Size(max = 32, message = "联系电话长度不能超过32")
    private String contactPhone;

    @Size(max = 32, message = "省份长度不能超过32")
    private String province;

    @Size(max = 32, message = "城市长度不能超过32")
    private String city;

    @Size(max = 32, message = "结算方式长度不能超过32")
    private String settlementType;

    @Size(max = 32, message = "合同状态长度不能超过32")
    private String contractStatus;

    @Size(max = 32, message = "资质状态长度不能超过32")
    private String qualificationStatus;

    private List<String> productLines;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
