package com.godzilla.distribution.dto.distribution.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;

@Data
public class CreateDistributionLeadFollowUpRequestDTO {
    @NotBlank(message = "跟进类型不能为空")
    @Size(max = 32, message = "跟进类型长度不能超过32")
    private String followUpType;

    @NotBlank(message = "跟进内容不能为空")
    @Size(max = 5000, message = "跟进内容长度不能超过5000")
    private String content;

    private Date nextActionAt;
}
