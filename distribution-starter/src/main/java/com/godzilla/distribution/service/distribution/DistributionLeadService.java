package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.AssignDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadFollowUpRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadStageRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDetailDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadFollowUpDTO;

import java.util.List;

public interface DistributionLeadService {
    PageResponseDTO<DistributionLeadDTO> listLeads(String leadNo,
                                                   String patientKeyword,
                                                   Long sourceDistributorId,
                                                   String intentProductLine,
                                                   Long ownerUserId,
                                                   String stage,
                                                   Integer page,
                                                   Integer pageSize);

    DistributionLeadDetailDTO getLead(Long id);

    Long createLead(CreateDistributionLeadRequestDTO request);

    void updateLead(Long id, UpdateDistributionLeadRequestDTO request);

    void assignLead(Long id, AssignDistributionLeadRequestDTO request);

    void updateLeadStage(Long id, UpdateDistributionLeadStageRequestDTO request);

    List<DistributionLeadFollowUpDTO> listFollowUps(Long leadId);

    Long createFollowUp(Long leadId, CreateDistributionLeadFollowUpRequestDTO request);
}
