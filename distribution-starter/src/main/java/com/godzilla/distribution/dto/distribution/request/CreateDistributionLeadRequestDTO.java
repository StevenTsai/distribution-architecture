package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class CreateDistributionLeadRequestDTO {
    @NotBlank(message = "患者姓名不能为空")
    @Size(max = 64, message = "患者姓名长度不能超过64")
    private String patientName;

    @NotBlank(message = "患者手机号不能为空")
    @Size(max = 32, message = "患者手机号长度不能超过32")
    private String patientPhone;

    @NotNull(message = "来源渠道不能为空")
    private Long sourceDistributorId;

    private Long sourceMemberId;

    @NotBlank(message = "意向产品线不能为空")
    @Size(max = 32, message = "意向产品线长度不能超过32")
    private String intentProductLine;

    @Size(max = 64, message = "来源地区长度不能超过64")
    private String sourceRegion;

    @Size(max = 64, message = "来源子渠道长度不能超过64")
    private String sourceChannel;

    private Long ownerUserId;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
