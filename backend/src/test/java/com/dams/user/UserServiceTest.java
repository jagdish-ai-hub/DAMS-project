package com.dams.user;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.organization.entity.Organization;
import com.dams.user.dto.RoleGrantDto;
import com.dams.user.dto.UserResponse;
import com.dams.user.entity.UserRoleGrant;
import org.mockito.ArgumentCaptor;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.email.EmailService;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.dto.UserRequest;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import com.dams.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Documents the team-management rules: role/branch validation and the last-Owner guard.
 * Test names describe the behaviour being proven — see AGENT.md.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long ORG = 1L;

    @Mock private AppUserRepository userRepo;
    @Mock private UserBranchAccessRepository branchAccessRepo;
    @Mock private UserRoleGrantRepository grantRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private OrganizationRepository orgRepo;
    @Mock private EmailService emailService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, branchAccessRepo, grantRepo, branchRepo, orgRepo, emailService,
            "http://localhost:5173");
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_rejectsSuperAdminRole() {
        assertThatThrownBy(() -> service.create(request("x@demo", Role.SUPER_ADMIN)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("SUPER_ADMIN");
    }

    @Test
    void create_cashierWithoutHomeBranch_throwsBadRequest() {
        lenient().when(userRepo.existsByEmailIgnoreCase("c@demo")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("c@demo", Role.CASHIER)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("home branch");
    }

    @Test
    void create_accountantWithoutBranches_throwsBadRequest() {
        lenient().when(userRepo.existsByEmailIgnoreCase("a@demo")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("a@demo", Role.ACCOUNTANT)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("assigned branch");
    }

    @Test
    void create_duplicateEmail_throwsConflict() {
        lenient().when(userRepo.existsByEmailIgnoreCase("dup@demo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("dup@demo", Role.FINANCE_MANAGER)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void update_demotingTheLastActiveOwner_throwsBadRequest() {
        AppUser owner = new AppUser();
        ReflectionTestUtils.setField(owner, "id", 10L);
        owner.setRole(Role.OWNER);
        owner.setActive(true);

        lenient().when(userRepo.findByIdAndOrganization_Id(10L, ORG)).thenReturn(java.util.Optional.of(owner));
        lenient().when(userRepo.findByOrganization_Id(ORG)).thenReturn(List.of(owner));

        UserRequest req = request("owner@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(1L));

        // caller is someone else (id 99) so the self-guard doesn't short-circuit
        assertThatThrownBy(() -> service.update(10L, req, 99L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("at least one active Owner");
    }

    // --- Extra switchable roles (plan.md rev 55) ---

    @Test
    void create_accountantWithExtraCashierRole_storesOneGrantPerBranchAndLabelsIt() {
        stubHappyCreate();
        UserRequest req = request("a@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(2L, 3L));
        req.setRoleGrants(List.of(new RoleGrantDto(Role.CASHIER, List.of(3L))));

        UserResponse resp = service.create(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRoleGrant>> saved = ArgumentCaptor.forClass(List.class);
        verify(grantRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        UserRoleGrant grant = saved.getValue().get(0);
        assertThat(grant.getRole()).isEqualTo(Role.CASHIER);
        assertThat(grant.getBranchId()).isEqualTo(3L);
        assertThat(grant.getUserId()).isEqualTo(20L);
        assertThat(grant.getOrgId()).isEqualTo(ORG);
        assertThat(resp.roleGrants()).containsExactly(new RoleGrantDto(Role.CASHIER, List.of(3L)));
        assertThat(resp.extraRolesLabel()).isEqualTo("Cashier (OOR)");
    }

    @Test
    void create_extraFinanceManagerRole_isOrgWideSoItStoresNoBranch() {
        stubHappyCreate();
        UserRequest req = request("a@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(2L));
        req.setRoleGrants(List.of(new RoleGrantDto(Role.FINANCE_MANAGER, List.of(3L))));   // branch ignored

        service.create(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRoleGrant>> saved = ArgumentCaptor.forClass(List.class);
        verify(grantRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(g -> {
            assertThat(g.getRole()).isEqualTo(Role.FINANCE_MANAGER);
            assertThat(g.getBranchId()).isNull();
        });
    }

    @Test
    void create_extraCashierRoleWithoutBranches_throwsBadRequest() {
        stubBranches();
        UserRequest req = request("a@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(2L));
        req.setRoleGrants(List.of(new RoleGrantDto(Role.CASHIER, List.of())));

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("at least one branch");
    }

    @Test
    void create_extraRoleInAnotherOrgsBranch_throwsNotFound() {
        stubBranches();
        UserRequest req = request("a@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(2L));
        req.setRoleGrants(List.of(new RoleGrantDto(Role.CASHIER, List.of(99L))));   // not in this org

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(DamsException.class);
    }

    @Test
    void create_cannotGrantOwnerAsAnExtraRole() {
        stubBranches();
        UserRequest req = request("a@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(2L));
        req.setRoleGrants(List.of(new RoleGrantDto(Role.OWNER, List.of())));

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("cannot be given");
    }

    @Test
    void create_ownerGetsNoGrants_becauseAnOwnerCanAlreadyActAsAnyRole() {
        stubHappyCreate();
        UserRequest req = request("o@demo", Role.OWNER);
        req.setRoleGrants(List.of(new RoleGrantDto(Role.CASHIER, List.of(3L))));

        UserResponse resp = service.create(req);

        verify(grantRepo, never()).saveAll(any());
        assertThat(resp.roleGrants()).isNull();
    }

    @Test
    void create_grantEqualToThePrimaryRole_isDropped() {
        stubHappyCreate();
        UserRequest req = request("a@demo", Role.ACCOUNTANT);
        req.setBranchIds(List.of(2L));
        req.setRoleGrants(List.of(new RoleGrantDto(Role.ACCOUNTANT, List.of(3L))));

        service.create(req);

        verify(grantRepo, never()).saveAll(any());
    }

    private void stubBranches() {
        Branch oob = branch(2L, "OOB");
        Branch oor = branch(3L, "OOR");
        lenient().when(userRepo.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        lenient().when(branchRepo.findByIdAndOrgId(2L, ORG)).thenReturn(java.util.Optional.of(oob));
        lenient().when(branchRepo.findByIdAndOrgId(3L, ORG)).thenReturn(java.util.Optional.of(oor));
        lenient().when(branchRepo.findByIdAndOrgId(99L, ORG)).thenReturn(java.util.Optional.empty());
        lenient().when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(oob, oor));
    }

    private void stubHappyCreate() {
        stubBranches();
        lenient().when(orgRepo.getReferenceById(ORG)).thenReturn(new Organization());
        lenient().when(userRepo.getReferenceById(20L)).thenReturn(new AppUser());
        lenient().when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 20L);
            return u;
        });
        lenient().when(grantRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setName(code + " branch");
        b.setActive(true);
        return b;
    }

    private static UserRequest request(String email, Role role) {
        UserRequest r = new UserRequest();
        r.setName("Test");
        r.setEmail(email);
        r.setRole(role);
        return r;
    }
}
