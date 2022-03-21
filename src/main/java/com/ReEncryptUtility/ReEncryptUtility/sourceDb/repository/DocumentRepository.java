package com.ReEncryptUtility.ReEncryptUtility.sourceDb.repository;

import com.ReEncryptUtility.ReEncryptUtility.sourceDb.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface DocumentRepository extends JpaRepository<DocumentEntity, String>{
    public List<DocumentEntity> findByDemographicEntityPreRegistrationId(String preId);
}
