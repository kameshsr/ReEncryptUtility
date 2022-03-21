package com.ReEncryptUtility.ReEncryptUtility.sourceDb.repository;

import com.ReEncryptUtility.ReEncryptUtility.sourceDb.entity.DemographicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface DemographicRepository extends JpaRepository<DemographicEntity, String> {
    public DemographicEntity findBypreRegistrationId(@Param("preRegId") String preRegId);
}
