package com.godzilla.distribution.service.distribution;

import com.godzilla.distribution.entity.shared.UserProfileEntity;

public interface DistributionOperatorService {
    Long getCurrentOperatorUserId();

    UserProfileEntity getCurrentOperatorUser();
}
