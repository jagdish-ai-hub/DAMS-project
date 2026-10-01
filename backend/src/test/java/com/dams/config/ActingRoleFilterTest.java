package com.dams.config;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.security.ActingDetails;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A switched role is re-checked against the database on every request (plan.md rev 55), so a
 * grant the Owner removes stops working on the very next call rather than at token expiry.
 */
@ExtendWith(MockitoExtension.class)
class ActingRoleFilterTest {

    private static final long ORG = 1L;
    private static final long USER = 7L;
    private static final long OOR = 3L;

    @Mock private AppUserRepository userRepo;
    @Mock private UserRoleGrantRepository grantRepo;
    @Mock private BranchRepository branchRepo;

    private ActingRoleFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ActingRoleFilter(userRepo, grantRepo, branchRepo, new ObjectMapper().findAndRegisterModules());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void aUserInTheirOwnRole_isNotChecked() throws Exception {
        authenticate(Role.ACCOUNTANT, Role.ACCOUNTANT, null);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(userRepo, grantRepo, branchRepo);
        assertThat(chain.getRequest()).isNotNull();   // passed down the chain
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aSwitchedRoleWhoseGrantStillExists_passesThrough() throws Exception {
        authenticate(Role.CASHIER, Role.ACCOUNTANT, OOR);
        user(Role.ACCOUNTANT, true);
        lenient().when(grantRepo.existsByUserIdAndRoleAndBranchId(USER, Role.CASHIER, OOR)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aSwitchedRoleWhoseGrantWasRevoked_getsA401AndNeverReachesTheApp() throws Exception {
        authenticate(Role.CASHIER, Role.ACCOUNTANT, OOR);
        user(Role.ACCOUNTANT, true);
        lenient().when(grantRepo.existsByUserIdAndRoleAndBranchId(USER, Role.CASHIER, OOR)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("acting role was removed");
        assertThat(chain.getRequest()).isNull();                       // chain was not invoked
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aSwitchedRole_afterTheUsersOwnRoleChanged_isRejected() throws Exception {
        authenticate(Role.CASHIER, Role.ACCOUNTANT, OOR);
        user(Role.FINANCE_MANAGER, true);   // the Owner re-assigned their primary role since the token was issued

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(grantRepo, never()).existsByUserIdAndRoleAndBranchId(any(), any(), any());
    }

    @Test
    void aSwitchedRole_forADeactivatedUser_isRejected() throws Exception {
        authenticate(Role.CASHIER, Role.ACCOUNTANT, OOR);
        user(Role.ACCOUNTANT, false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void anOwnerActingAtABranchOfTheirOrg_needsNoGrant() throws Exception {
        authenticate(Role.CASHIER, Role.OWNER, OOR);
        user(Role.OWNER, true);
        lenient().when(branchRepo.findByIdAndOrgId(OOR, ORG)).thenReturn(Optional.of(new Branch()));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(grantRepo);
    }

    @Test
    void anOwnerActingAtABranchOfAnotherOrg_isRejected() throws Exception {
        authenticate(Role.CASHIER, Role.OWNER, 99L);
        user(Role.OWNER, true);
        lenient().when(branchRepo.findByIdAndOrgId(99L, ORG)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aTokenClaimingToActAsOwner_isRejected() throws Exception {
        authenticate(Role.OWNER, Role.ACCOUNTANT, OOR);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // --- fixtures ---

    private void authenticate(Role acting, Role primary, Long branchId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            USER, null, List.of(new SimpleGrantedAuthority(acting.name())));
        auth.setDetails(new ActingDetails(primary, branchId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void user(Role role, boolean active) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", USER);
        u.setRole(role);
        u.setActive(active);
        lenient().when(userRepo.findByIdAndOrganization_Id(USER, ORG)).thenReturn(Optional.of(u));
    }
}
