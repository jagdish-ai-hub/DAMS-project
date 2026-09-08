package com.dams.staff.repository;

import com.dams.staff.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    Optional<StaffMember> findByIdAndOrgId(Long id, Long orgId);

    List<StaffMember> findByOrgIdOrderByNameAsc(Long orgId);

    Optional<StaffMember> findByOrgIdAndNameIgnoreCase(Long orgId, String name);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
