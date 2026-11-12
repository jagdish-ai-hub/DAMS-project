package com.dams.spareparts.repository;

import com.dams.jobcard.entity.JobCard;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * READ-ONLY look at DAMS job cards, for the "no local parts on a claim" rule. It lives in this module
 * so the Finance code is not touched: the module may read other modules' data, never write it.
 */
public interface InvJobCardLookupRepository extends Repository<JobCard, Long> {

    /**
     * How many job cards of the org carry this Eicher job card number (the DBM ID) AND are a claim
     * (a claim type is set). Above zero means local spare parts must not be billed to that number.
     */
    @Query("""
        select count(j) from JobCard j
        where j.orgId = :orgId and lower(j.dbmId) = lower(:jobCardNo) and j.claimTypeId is not null
        """)
    long countClaimJobCards(@Param("orgId") Long orgId, @Param("jobCardNo") String jobCardNo);
}
