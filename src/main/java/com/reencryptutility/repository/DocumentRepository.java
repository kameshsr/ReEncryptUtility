package com.reencryptutility.repository;

import com.reencryptutility.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface DocumentRepository extends JpaRepository<DocumentEntity, String>{
    public List<DocumentEntity> findByDemographicEntityPreRegistrationId(String preId);
}
