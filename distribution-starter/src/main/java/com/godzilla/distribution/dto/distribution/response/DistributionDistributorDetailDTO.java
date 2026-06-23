package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

@Data
public class DistributionDistributorDetailDTO extends DistributionDistributorDTO {
    private Long createdBy;
    private Long updatedBy;
}
