package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class ApproveDistributionComplianceRecordRequestDTO {
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
