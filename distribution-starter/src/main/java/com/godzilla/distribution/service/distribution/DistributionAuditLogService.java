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

    /**
     * 记录审计日志。
     *
     * @param distributorId 归属渠道ID（冗余列，用于数据权限行过滤；biz_id 与渠道ID独立自增，不可互相替代）
     */
    void record(String bizType, Long bizId, Long distributorId, String action, Object beforeSnapshot, Object afterSnapshot, String remark);
}
