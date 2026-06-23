package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class CreateDistributorMemberRequestDTO {
    @NotNull(message = "所属渠道不能为空")
    private Long distributorId;

    private Long userId;

    @NotBlank(message = "成员姓名不能为空")
    @Size(max = 64, message = "成员姓名长度不能超过64")
    private String name;

    @NotBlank(message = "成员手机号不能为空")
    @Size(max = 32, message = "成员手机号长度不能超过32")
    private String phone;

    @NotBlank(message = "角色不能为空")
    @Size(max = 32, message = "角色长度不能超过32")
    private String roleCode;

    @NotBlank(message = "数据范围不能为空")
    @Size(max = 32, message = "数据范围长度不能超过32")
    private String dataScope;

    private Long managerMemberId;

    private List<String> productLines;

    @Size(max = 32, message = "培训状态长度不能超过32")
    private String trainingStatus;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
