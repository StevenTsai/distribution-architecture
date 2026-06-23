package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class CreateDistributionComplianceRecordRequestDTO {
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 32, message = "业务类型长度不能超过32")
    private String bizType;

    @NotNull(message = "业务ID不能为空")
    private Long bizId;

    @Size(max = 32, message = "产品线长度不能超过32")
    private String productLineCode;

    @NotBlank(message = "记录类型不能为空")
    @Size(max = 32, message = "记录类型长度不能超过32")
    private String recordType;

    @Size(max = 5000, message = "内容长度不能超过5000")
    private String content;

    private List<String> attachments;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
