package com.dams.user.repository;

import com.dams.user.entity.UserBranchAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBranchAccessRepository extends JpaRepository<UserBranchAccess, UserBranchAccess.UserBranchAccessId> {

    List<UserBranchAccess> findByUserId(Long userId);

    long countByBranchId(Long branchId);

    void deleteByUserId(Long userId);
}
