package com.dams.auth;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.auth.dto.LoginResponse;
import com.dams.auth.dto.SwitchOptionsResponse;
import com.dams.auth.dto.SwitchRoleRequest;
import com.dams.auth.service.AuthService;
import com.dams.auth.util.JwtUtil;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserRoleGrant;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Role switching (plan.md rev 55): the server issues the acting-role token, and only from an
 * Owner-granted role at the branch asked for. Uses a real {@link JwtUtil} so the assertions are
 * on the claims that actually reach the client.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceSwitchTest {

    private static final long ORG = 1L;
    private static final long USER = 7L;
    private static final long OOB = 2L;
    private static final long OOR = 3L;

    @Mock private AppUserRepository userRepo;
    @Mock private UserBranchAccessRepository branchAccessRepo;
    @Mock private UserRoleGrantRepository grantRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private AuditService auditService;
    @Mock private PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-that-is-at-least-32-characters-long!!", 8);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepo, branchAccessRepo, grantRepo, branchRepo, auditService,
            passwordEncoder, jwtUtil);
        TenantContext.setOrgId(ORG);
        lenient().when(branchRepo.findByIdAndOrgId(OOB, ORG)).thenReturn(Optional.of(branch(OOB, "OOB", true)));
        lenient().when(branchRepo.findByIdAndOrgId(OOR, ORG)).thenReturn(Optional.of(branch(OOR, "OOR", true)));
        lenient().when(branchRepo.findByOrgIdOrderByCodeAsc(ORG))
            .thenReturn(List.of(branch(OOB, "OOB", true), branch(OOR, "OOR", true)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void switchRole_toACashierGrantedAtThatBranch_issuesAnActingTokenScopedToIt() {
        user(Role.ACCOUNTANT);
        lenient().when(grantRepo.existsByUserIdAndRoleAndBranchId(USER, Role.CASHIER, OOR)).thenReturn(true);

        LoginResponse resp = service.switchRole(USER, req(Role.CASHIER, OOR));

        Claims claims = jwtUtil.parseToken(resp.getAccessToken());
        assertThat(jwtUtil.getRole(claims)).isEqualTo(Role.CASHIER);          // the acting role authorises requests
        assertThat(jwtUtil.getPrimaryRole(claims)).isEqualTo(Role.ACCOUNTANT);
        assertThat(jwtUtil.getActingBranchId(claims)).isEqualTo(OOR);
        assertThat(jwtUtil.getHomeBranchId(claims)).isEqualTo(OOR);            // a cashier posts to the picked branch
        assertThat(resp.getRole()).isEqualTo(Role.CASHIER);
        assertThat(resp.getPrimaryRole()).isEqualTo(Role.ACCOUNTANT);
        assertThat(resp.getActingBranchId()).isEqualTo(OOR);
        verify(auditService).recordUserEvent(eq("AppUser"), eq(USER), eq(OOR), eq(EventType.ROLE_SWITCHED),
            eq(USER), any());
    }

    @Test
    void switchRole_toAnAccountantGrant_carriesOnlyThePickedBranchInTheToken() {
        user(Role.CASHIER);
        lenient().when(grantRepo.existsByUserIdAndRoleAndBranchId(USER, Role.ACCOUNTANT, OOB)).thenReturn(true);

        LoginResponse resp = service.switchRole(USER, req(Role.ACCOUNTANT, OOB));

        Claims claims = jwtUtil.parseToken(resp.getAccessToken());
        assertThat(jwtUtil.getBranchIds(claims)).containsExactly(OOB);
        assertThat(jwtUtil.getHomeBranchId(claims)).isNull();
    }

    @Test
    void switchRole_withoutAGrantAtThatBranch_isForbidden() {
        user(Role.ACCOUNTANT);
        // Cashier at OOR is granted — asking for OOB is not
        lenient().when(grantRepo.existsByUserIdAndRoleAndBranchId(USER, Role.CASHIER, OOR)).thenReturn(true);

        assertThatThrownBy(() -> service.switchRole(USER, req(Role.CASHIER, OOB)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not been given")
            .hasMessageContaining("OOB");
    }

    @Test
    void switchRole_toFinanceManager_needsTheOrgWideGrant() {
        user(Role.ACCOUNTANT);
        lenient().when(grantRepo.existsByUserIdAndRoleAndBranchIdIsNull(USER, Role.FINANCE_MANAGER)).thenReturn(true);

        LoginResponse resp = service.switchRole(USER, req(Role.FINANCE_MANAGER, OOR));

        assertThat(resp.getRole()).isEqualTo(Role.FINANCE_MANAGER);
    }

    @Test
    void switchRole_ownerMayActAsAnyRoleAtAnyBranch_withoutGrants() {
        user(Role.OWNER);

        LoginResponse resp = service.switchRole(USER, req(Role.CASHIER, OOB));

        assertThat(resp.getRole()).isEqualTo(Role.CASHIER);
        assertThat(resp.getPrimaryRole()).isEqualTo(Role.OWNER);
        assertThat(resp.getActingBranchId()).isEqualTo(OOB);
        assertThat(resp.isCanSwitchRole()).isTrue();
    }

    @Test
    void switchRole_nobodyCanSwitchIntoOwner() {
        user(Role.ACCOUNTANT);

        assertThatThrownBy(() -> service.switchRole(USER, req(Role.OWNER, OOR)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("cannot switch into");
    }

    @Test
    void switchRole_needsABranch() {
        user(Role.OWNER);

        assertThatThrownBy(() -> service.switchRole(USER, req(Role.CASHIER, null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("branch");
    }

    @Test
    void switchRole_toAnInactiveBranch_isRejected() {
        user(Role.OWNER);
        lenient().when(branchRepo.findByIdAndOrgId(OOB, ORG)).thenReturn(Optional.of(branch(OOB, "OOB", false)));

        assertThatThrownBy(() -> service.switchRole(USER, req(Role.CASHIER, OOB)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("inactive");
    }

    @Test
    void switchRole_toYourOwnRole_switchesBackToAPlainPrimaryToken() {
        user(Role.ACCOUNTANT);

        LoginResponse resp = service.switchRole(USER, req(Role.ACCOUNTANT, null));

        Claims claims = jwtUtil.parseToken(resp.getAccessToken());
        assertThat(jwtUtil.getRole(claims)).isEqualTo(Role.ACCOUNTANT);
        assertThat(jwtUtil.getPrimaryRole(claims)).isEqualTo(Role.ACCOUNTANT);
        assertThat(jwtUtil.getActingBranchId(claims)).isNull();
    }

    @Test
    void switchOptions_forAnOwner_listsEveryBranchWithTheThreeSwitchableRoles() {
        user(Role.OWNER);

        SwitchOptionsResponse options = service.switchOptions(USER);

        assertThat(options.primaryRole()).isEqualTo(Role.OWNER);
        assertThat(options.branches()).extracting(SwitchOptionsResponse.BranchOption::code)
            .containsExactly("OOB", "OOR");
        assertThat(options.branches()).allSatisfy(b ->
            assertThat(b.roles()).containsExactlyInAnyOrder(Role.FINANCE_MANAGER, Role.ACCOUNTANT, Role.CASHIER));
    }

    @Test
    void switchOptions_forAGrantedUser_listsOnlyGrantedBranchesAndNeverTheirOwnRole() {
        user(Role.ACCOUNTANT);
        lenient().when(grantRepo.findByUserId(USER)).thenReturn(List.of(
            grant(Role.CASHIER, OOR),
            grant(Role.ACCOUNTANT, OOB)));   // equal to the primary role — must not be offered

        SwitchOptionsResponse options = service.switchOptions(USER);

        assertThat(options.branches()).singleElement().satisfies(b -> {
            assertThat(b.code()).isEqualTo("OOR");
            assertThat(b.roles()).containsExactly(Role.CASHIER);
        });
    }

    @Test
    void switchOptions_anOrgWideFinanceManagerGrant_isOfferedAtEveryBranch() {
        user(Role.CASHIER);
        lenient().when(grantRepo.findByUserId(USER)).thenReturn(List.of(grant(Role.FINANCE_MANAGER, null)));

        SwitchOptionsResponse options = service.switchOptions(USER);

        assertThat(options.branches()).hasSize(2);
        assertThat(options.branches()).allSatisfy(b -> assertThat(b.roles()).containsExactly(Role.FINANCE_MANAGER));
    }

    // --- fixtures ---

    private void user(Role role) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", USER);
        u.setRole(role);
        u.setName("Ajay");
        u.setActive(true);
        if (role == Role.CASHIER) {
            u.setHomeBranchId(OOB);
        }
        lenient().when(userRepo.findByIdAndOrganization_Id(USER, ORG)).thenReturn(Optional.of(u));
        lenient().when(branchAccessRepo.findByUserId(USER)).thenReturn(List.of());
    }

    private static SwitchRoleRequest req(Role role, Long branchId) {
        SwitchRoleRequest r = new SwitchRoleRequest();
        r.setRole(role);
        r.setBranchId(branchId);
        return r;
    }

    private static UserRoleGrant grant(Role role, Long branchId) {
        UserRoleGrant g = new UserRoleGrant();
        g.setOrgId(ORG);
        g.setUserId(USER);
        g.setRole(role);
        g.setBranchId(branchId);
        return g;
    }

    private static Branch branch(long id, String code, boolean active) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setName(code + " branch");
        b.setActive(active);
        return b;
    }
}
