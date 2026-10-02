package com.dams.jobcard.repository;

import com.dams.jobcard.entity.JobCard;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobCardRepository extends JpaRepository<JobCard, Long> {

    Optional<JobCard> findByIdAndOrgId(Long id, Long orgId);

    /** Owner dashboard — every job card in the org (outstanding-amount scan). */
    List<JobCard> findByOrgId(Long orgId);

    boolean existsByOrgId(Long orgId);

    /** Batch id lookup scoped by org — resolves many job cards in one query (My Entries, queues). */
    List<JobCard> findByOrgIdAndIdIn(Long orgId, Collection<Long> ids);

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

    /**
     * Picker search (rev 56). Branch scope is applied here, not after the fact, so the limit is
     * honest: {@code allBranches} true = unrestricted, else {@code branchIds} (never empty).
     * {@code qBlank} lists the newest cards. {@code qLike} is a lower-cased %text% pattern and
     * {@code vLike} a %NORMALISEDVEHICLE% pattern (a never-matching value when q has none).
     */
    @Query("""
        select j from JobCard j
        left join Customer c on c.id = j.customerId
        left join Vehicle v on v.id = j.vehicleId
        where j.orgId = :orgId
          and (:allBranches = true or j.branchId in :branchIds)
          and (:customerId is null or j.customerId = :customerId)
          and (:vehicleId is null or j.vehicleId = :vehicleId)
          and ( :qBlank = true
                or (:idQ is not null and j.id = :idQ)
                or (c.name is not null and lower(c.name) like :qLike)
                or (c.phone is not null and c.phone like :qLike)
                or (v.vehicleNo is not null and v.vehicleNo like :vLike)
                or (j.vehicleNoText is not null and j.vehicleNoText like :vLike)
                or (j.dbmId is not null and lower(j.dbmId) like :qLike)
                or (j.invoiceNo is not null and lower(j.invoiceNo) like :qLike)
                or exists (select 1 from ReceiveDocument r
                            where r.jobCardId = j.id and r.orgId = :orgId
                              and r.documentNo is not null and lower(r.documentNo) like :qLike) )
        order by j.createdAt desc, j.id desc
        """)
    List<JobCard> searchForPicker(@Param("orgId") Long orgId,
                                  @Param("allBranches") boolean allBranches,
                                  @Param("branchIds") Collection<Long> branchIds,
                                  @Param("customerId") Long customerId,
                                  @Param("vehicleId") Long vehicleId,
                                  @Param("qBlank") boolean qBlank,
                                  @Param("qLike") String qLike,
                                  @Param("vLike") String vLike,
                                  @Param("idQ") Long idQ,
                                  Limit limit);

    /**
     * The numbered DAMS-Receive-IDs ("Ooriba IDs") of these job cards, newest first, as
     * {@code [jobCardId, documentNo]} rows. Unsubmitted drafts have no number and are skipped.
     */
    @Query("""
        select r.jobCardId, r.documentNo from ReceiveDocument r
        where r.orgId = :orgId and r.jobCardId in :jobCardIds and r.documentNo is not null
        order by r.createdAt desc, r.id desc
        """)
    List<Object[]> receiveNumbersFor(@Param("orgId") Long orgId, @Param("jobCardIds") Collection<Long> jobCardIds);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
