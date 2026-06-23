package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

@Data
public class DistributionDistributorMemberDetailDTO extends DistributionDistributorMemberDTO {
    private Long createdBy;
    private Long updatedBy;
}
