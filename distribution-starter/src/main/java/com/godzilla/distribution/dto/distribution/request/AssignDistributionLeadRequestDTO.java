package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AssignDistributionLeadRequestDTO {
    @NotNull(message = "负责人不能为空")
    private Long ownerUserId;
}
