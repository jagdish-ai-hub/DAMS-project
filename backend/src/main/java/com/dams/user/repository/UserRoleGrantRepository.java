package com.dams.user.repository;

import com.dams.user.entity.Role;
import com.dams.user.entity.UserRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, Long> {

    List<UserRoleGrant> findByUserId(Long userId);

    List<UserRoleGrant> findByUserIdIn(java.util.Collection<Long> userIds);

    void deleteByUserId(Long userId);

    /** Branch-scoped grant (ACCOUNTANT / CASHIER). */
    boolean existsByUserIdAndRoleAndBranchId(Long userId, Role role, Long branchId);

    /** Org-wide grant (FINANCE_MANAGER — branch_id is null). */
    boolean existsByUserIdAndRoleAndBranchIdIsNull(Long userId, Role role);
}
