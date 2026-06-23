package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberStatusRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorMemberDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorMemberDetailDTO;

public interface DistributionDistributorMemberService {
    PageResponseDTO<DistributionDistributorMemberDTO> listMembers(Long distributorId, String status, String roleCode, String phone, Integer page, Integer pageSize);

    DistributionDistributorMemberDetailDTO getMember(Long id);

    Long createMember(CreateDistributorMemberRequestDTO request);

    void updateMember(Long id, UpdateDistributorMemberRequestDTO request);

    void updateMemberStatus(Long id, UpdateDistributorMemberStatusRequestDTO request);
}
