package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class UpdateDistributorMemberStatusRequestDTO {
    @NotBlank(message = "成员状态不能为空")
    @Size(max = 32, message = "成员状态长度不能超过32")
    private String status;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
