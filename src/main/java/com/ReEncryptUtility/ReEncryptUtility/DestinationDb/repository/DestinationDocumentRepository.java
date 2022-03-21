package com.ReEncryptUtility.ReEncryptUtility.DestinationDb.repository;

import com.ReEncryptUtility.ReEncryptUtility.DestinationDb.entity.DestinationDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface DestinationDocumentRepository extends JpaRepository<DestinationDocumentEntity, String>{
    public List<DestinationDocumentEntity> findByDemographicEntityPreRegistrationId(String preId);
}
