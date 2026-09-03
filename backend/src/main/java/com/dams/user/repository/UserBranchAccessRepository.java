package com.dams.user.repository;

import com.dams.user.entity.UserBranchAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserBranchAccessRepository extends JpaRepository<UserBranchAccess, UserBranchAccess.UserBranchAccessId> {

    List<UserBranchAccess> findByUserId(Long userId);

    long countByBranchId(Long branchId);

    /** {branchId, count} of access rows for the given branches — one query for the branch list. */
    @Query("""
        select uba.branchId, count(uba) from UserBranchAccess uba
        where uba.branchId in :branchIds
        group by uba.branchId
        """)
    List<Object[]> countByBranchIdGrouped(@Param("branchIds") Collection<Long> branchIds);

    void deleteByUserId(Long userId);
}
