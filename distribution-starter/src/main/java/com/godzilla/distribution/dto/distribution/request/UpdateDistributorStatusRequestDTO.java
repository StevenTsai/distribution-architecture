package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class UpdateDistributorStatusRequestDTO {
    @NotBlank(message = "渠道状态不能为空")
    @Size(max = 32, message = "渠道状态长度不能超过32")
    private String status;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
