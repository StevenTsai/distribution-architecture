package com.godzilla.distribution.dto.distribution.response;

import lombok.Data;

@Data
public class DistributionProductLineDTO {
    private Long id;
    private String lineCode;
    private String lineName;
    private String shortName;
    private String themeColor;
    private String description;
    private String status;
    private Integer sortNo;
}
