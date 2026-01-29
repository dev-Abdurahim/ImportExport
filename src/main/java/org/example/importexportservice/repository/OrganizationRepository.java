package org.example.importexportservice.repository;

import org.example.importexportservice.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization,String> {
}
