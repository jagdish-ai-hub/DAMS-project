package com.dams.jobcard;

import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.jobcard.dto.ClaimActionResponse;
import com.dams.jobcard.dto.CreateClaimActionRequest;
import com.dams.jobcard.entity.ClaimAction;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimActionRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.ClaimActionService;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.common.exception.DamsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FEAT-38: the next step on every open claim. Only FM/Owner can own an
 * action; overdue derives at read time; completing keeps history.
 */
@ExtendWith(MockitoExtension.class)
class ClaimActionServiceTest {

    private static final long ORG = 1L;
    private static final long ACTOR = 8L;
    private static final long JOB = 11L;
    private static final long BRANCH = 3L;

    @Mock private ClaimActionRepository actionRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private BranchScope branchScope;
    @Mock private AuditService auditService;

    private ClaimActionService service;

    @BeforeEach
    void setUp() {
        service = new ClaimActionService(actionRepo, jobCardRepo, branchRepo, userRepo, branchScope, auditService);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(ACTOR);
        lenient().when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        lenient().when(jobCardRepo.findByIdAndOrgId(JOB, ORG)).thenReturn(Optional.of(jobCard()));
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(actionRepo.save(any(ClaimAction.class))).thenAnswer(i -> {
            ClaimAction a = i.getArgument(0);
            if (a.getId() == null) ReflectionTestUtils.setField(a, "id", 300L);
            return a;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_recordsAction_withFmOwner() {
        when(userRepo.findByIdAndOrganization_Id(ACTOR, ORG)).thenReturn(Optional.of(user(Role.FINANCE_MANAGER)));

        ClaimActionResponse r = service.create(request(ACTOR));

        assertThat(r.jobCardId()).isEqualTo(JOB);
        assertThat(r.jobCardReference()).isEqualTo("OOR-JC-11");
        assertThat(r.overdue()).isFalse();
    }

    @Test
    void create_refusesCashierOwnership() {
        when(userRepo.findByIdAndOrganization_Id(7L, ORG)).thenReturn(Optional.of(user(Role.CASHIER)));

        assertThatThrownBy(() -> service.create(request(7L)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Finance Manager or Owner");
    }

    @Test
    void openActions_derivesOverdueAtReadTime() {
        // The query returns open rows only (done_at IS NULL); done rows never arrive here.
        ClaimAction late = action(null);
        late.setDueDate(LocalDate.now().minusDays(3));
        when(actionRepo.findByOrgIdAndDoneAtIsNullOrderByDueDateAsc(ORG)).thenReturn(List.of(late));

        var rows = service.openActions();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).overdue()).isTrue();
    }

    @Test
    void complete_stampsDone_keepsTheRow() {
        ClaimAction a = action(300L);
        when(actionRepo.findByIdAndOrgId(300L, ORG)).thenReturn(Optional.of(a));

        ClaimActionResponse r = service.complete(300L);

        assertThat(r.doneAt()).isNotNull();
        assertThat(a.getDoneAt()).isNotNull();
    }

    // ---------------------------------------------------------- fixtures

    private static CreateClaimActionRequest request(Long ownerId) {
        CreateClaimActionRequest r = new CreateClaimActionRequest();
        r.setJobCardId(JOB);
        r.setAction("Call Eicher for the LR copy");
        r.setOwnerUserId(ownerId);
        r.setDueDate(LocalDate.now().plusDays(2));
        return r;
    }

    private static ClaimAction action(Long id) {
        ClaimAction a = new ClaimAction();
        if (id != null) ReflectionTestUtils.setField(a, "id", id);
        a.setOrgId(ORG);
        a.setJobCardId(JOB);
        a.setAction("Call Eicher for the LR copy");
        a.setDueDate(LocalDate.now().plusDays(2));
        a.setCreatedBy(ACTOR);
        return a;
    }

    private static JobCard jobCard() {
        JobCard j = new JobCard();
        ReflectionTestUtils.setField(j, "id", JOB);
        j.setOrgId(ORG);
        j.setBranchId(BRANCH);
        j.setCustomerId(5L);
        return j;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", BRANCH);
        b.setOrgId(ORG);
        b.setCode("OOR");
        return b;
    }

    private static AppUser user(Role role) {
        AppUser u = new AppUser();
        u.setRole(role);
        return u;
    }
}
