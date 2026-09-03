package com.dams.user.repository;

import com.dams.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Just the display name — avoids loading the whole AppUser (+ its org join) for a label. */
    @Query("select u.name from AppUser u where u.id = :id")
    Optional<String> findNameById(@Param("id") Long id);

    /** {homeBranchId, count} for every cashier in the org — one query for the whole branch list. */
    @Query("""
        select u.homeBranchId, count(u.id) from AppUser u
        where u.organization.id = :orgId and u.homeBranchId is not null
        group by u.homeBranchId
        """)
    List<Object[]> countByHomeBranchGrouped(@Param("orgId") Long orgId);

    List<AppUser> findByOrganization_Id(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrganization_Id(Long orgId);
}
