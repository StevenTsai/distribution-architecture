package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.entity.shared.MedicalUserInfoEntity;

public interface DistributionOperatorService {
    Long getCurrentOperatorUserId();

    MedicalUserInfoEntity getCurrentOperatorUser();
}
