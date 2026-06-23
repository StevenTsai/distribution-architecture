package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.ApproveDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.RejectDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDetailDTO;

public interface DistributionComplianceService {
    PageResponseDTO<DistributionComplianceRecordDTO> listRecords(String bizType,
                                                                 Long bizId,
                                                                 String recordType,
                                                                 String status,
                                                                 Integer page,
                                                                 Integer pageSize);

    DistributionComplianceRecordDetailDTO getRecord(Long id);

    Long createRecord(CreateDistributionComplianceRecordRequestDTO request);

    void approveRecord(Long id, ApproveDistributionComplianceRecordRequestDTO request);

    void rejectRecord(Long id, RejectDistributionComplianceRecordRequestDTO request);
}
