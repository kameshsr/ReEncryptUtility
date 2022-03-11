package com.ReEncryptUtility.ReEncryptUtility.repository;

import com.ReEncryptUtility.ReEncryptUtility.entity.DemographicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface DemographicRepository extends JpaRepository<DemographicEntity, String> {
    public DemographicEntity findBypreRegistrationId(@Param("preRegId") String preRegId);
}
