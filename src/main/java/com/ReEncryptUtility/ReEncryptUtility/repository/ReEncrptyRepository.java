package com.ReEncryptUtility.ReEncryptUtility.repository;

import com.ReEncryptUtility.ReEncryptUtility.entity.DemographicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

//repository that extends CrudRepository

public interface ReEncrptyRepository extends JpaRepository<DemographicEntity, String> {

}
