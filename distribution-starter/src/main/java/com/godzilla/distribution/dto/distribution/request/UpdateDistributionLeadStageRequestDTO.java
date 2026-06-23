package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class UpdateDistributionLeadStageRequestDTO {
    @NotBlank(message = "线索阶段不能为空")
    @Size(max = 32, message = "线索阶段长度不能超过32")
    private String stage;

    private Long productId;

    @Size(max = 255, message = "无效原因长度不能超过255")
    private String invalidReason;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
