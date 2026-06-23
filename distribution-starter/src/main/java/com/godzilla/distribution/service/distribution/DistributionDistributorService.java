package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorStatusRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDetailDTO;

public interface DistributionDistributorService {
    PageResponseDTO<DistributionDistributorDTO> listDistributors(String name, String status, String levelCode, String productLine, Integer page, Integer pageSize);

    DistributionDistributorDetailDTO getDistributor(Long id);

    Long createDistributor(CreateDistributorRequestDTO request);

    void updateDistributor(Long id, UpdateDistributorRequestDTO request);

    void updateDistributorStatus(Long id, UpdateDistributorStatusRequestDTO request);

    void deleteDistributor(Long id);
}
