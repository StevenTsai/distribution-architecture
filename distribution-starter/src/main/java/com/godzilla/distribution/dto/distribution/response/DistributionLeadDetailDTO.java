package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

@Data
public class DistributionLeadDetailDTO extends DistributionLeadDTO {
    private Long createdBy;
    private Long updatedBy;
}
