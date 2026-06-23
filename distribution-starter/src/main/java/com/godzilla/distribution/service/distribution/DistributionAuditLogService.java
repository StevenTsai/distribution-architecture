package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDetailDTO;

public interface DistributionAuditLogService {
    PageResponseDTO<DistributionAuditLogDTO> listLogs(String bizType,
                                                      Long bizId,
                                                      String action,
                                                      Integer page,
                                                      Integer pageSize);

    DistributionAuditLogDetailDTO getLog(Long id);

    void record(String bizType, Long bizId, String action, Object beforeSnapshot, Object afterSnapshot, String remark);
}
