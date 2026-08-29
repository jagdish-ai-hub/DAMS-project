package com.dams.jobcard.repository;

import com.dams.jobcard.entity.JobCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobCardRepository extends JpaRepository<JobCard, Long> {

    Optional<JobCard> findByIdAndOrgId(Long id, Long orgId);

    List<JobCard> findByOrgIdAndCustomerIdOrderByCreatedAtDesc(Long orgId, Long customerId);

    List<JobCard> findByOrgIdAndCustomerIdInOrderByCreatedAtDesc(Long orgId, Collection<Long> customerIds);

    long countByOrgIdAndCustomerId(Long orgId, Long customerId);

    /** Universal search — match on the internal id (typed as a number), invoice_no or dbm_id. */
    @Query("""
        select j from JobCard j
        where j.orgId = :orgId
          and ( (:idQ is not null and j.id = :idQ)
                or (j.invoiceNo is not null and lower(j.invoiceNo) like lower(concat('%', :q, '%')))
                or (j.dbmId is not null and lower(j.dbmId) like lower(concat('%', :q, '%'))) )
        """)
    List<JobCard> search(@Param("orgId") Long orgId, @Param("q") String q, @Param("idQ") Long idQ);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
