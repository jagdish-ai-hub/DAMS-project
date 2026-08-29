package com.dams.user.repository;

import com.dams.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByInviteToken(String inviteToken);

    // --- Org-scoped (Owner managing their team) ---

    List<AppUser> findByOrganization_IdOrderByNameAsc(Long orgId);

    Optional<AppUser> findByIdAndOrganization_Id(Long id, Long orgId);

    long countByHomeBranchId(Long homeBranchId);

    List<AppUser> findByOrganization_Id(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrganization_Id(Long orgId);
}
