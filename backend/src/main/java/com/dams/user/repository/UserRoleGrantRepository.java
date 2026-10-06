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

    /** Everyone granted {@code role} at {@code branchId} (ACCOUNTANT / CASHIER grants). */
    List<UserRoleGrant> findByRoleAndBranchId(Role role, Long branchId);

    /** Everyone granted the org-wide {@code role} (FINANCE_MANAGER — branch_id is null). */
    List<UserRoleGrant> findByRoleAndBranchIdIsNull(Role role);

    void deleteByUserId(Long userId);

    /** Branch-scoped grant (ACCOUNTANT / CASHIER). */
    boolean existsByUserIdAndRoleAndBranchId(Long userId, Role role, Long branchId);

    /** Org-wide grant (FINANCE_MANAGER — branch_id is null). */
    boolean existsByUserIdAndRoleAndBranchIdIsNull(Long userId, Role role);
}
