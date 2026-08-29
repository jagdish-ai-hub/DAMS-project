package com.dams.user;

import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.email.EmailService;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.dto.UserRequest;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Documents the team-management rules: role/branch validation and the last-Owner guard.
 * Test names describe the behaviour being proven — see AGENT.md.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long ORG = 1L;

    @Mock private AppUserRepository userRepo;
    @Mock private UserBranchAccessRepository branchAccessRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private OrganizationRepository orgRepo;
    @Mock private EmailService emailService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, branchAccessRepo, branchRepo, orgRepo, emailService,
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

    private static UserRequest request(String email, Role role) {
        UserRequest r = new UserRequest();
        r.setName("Test");
        r.setEmail(email);
        r.setRole(role);
        return r;
    }
}
