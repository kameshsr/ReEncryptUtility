package com.ReEncryptUtility.ReEncryptUtility.DestinationDb.repository;

import com.ReEncryptUtility.ReEncryptUtility.DestinationDb.entity.DestinationDemographicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface DestinationDemographicRepository extends JpaRepository<DestinationDemographicEntity, String> {
    public DestinationDemographicEntity findBypreRegistrationId(@Param("preRegId") String preRegId);
}
