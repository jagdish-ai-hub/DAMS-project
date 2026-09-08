package com.dams.staff;

import com.dams.audit.service.AuditService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.staff.dto.CreateAdvanceEntryRequest;
import com.dams.staff.dto.CreateStaffRequest;
import com.dams.staff.entity.StaffAdvanceEntry;
import com.dams.staff.entity.StaffMember;
import com.dams.staff.repository.StaffAdvanceEntryRepository;
import com.dams.staff.repository.StaffMemberRepository;
import com.dams.staff.service.StaffService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEAT-44: advances out, recoveries in, outstanding derived. Recovery can
 * never exceed the outstanding; deactivated staff take no new entries.
 */
@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    private static final long ORG = 1L;
    private static final long ACTOR = 50L;
    private static final long STAFF = 9L;

    @Mock private StaffMemberRepository staffRepo;
    @Mock private StaffAdvanceEntryRepository entryRepo;
    @Mock private BranchScope branchScope;
    @Mock private AuditService auditService;

    private StaffService service;

    @BeforeEach
    void setUp() {
        service = new StaffService(staffRepo, entryRepo, branchScope, auditService);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(ACTOR);
        lenient().when(staffRepo.save(any(StaffMember.class))).thenAnswer(i -> {
            StaffMember s = i.getArgument(0);
            if (s.getId() == null) ReflectionTestUtils.setField(s, "id", STAFF);
            return s;
        });
        lenient().when(entryRepo.save(any(StaffAdvanceEntry.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordEntry_advance_savesKindAndAmount() {
        when(staffRepo.findByIdAndOrgId(STAFF, ORG)).thenReturn(Optional.of(member(true)));

        var r = service.recordEntry(STAFF, entry("ADVANCE", "5000"));

        assertThat(r.kind()).isEqualTo("ADVANCE");
        assertThat(r.amount()).isEqualByComparingTo("5000");
    }

    @Test
    void recordEntry_recoveryBeyondOutstanding_isRefused() {
        when(staffRepo.findByIdAndOrgId(STAFF, ORG)).thenReturn(Optional.of(member(true)));
        when(entryRepo.outstandingFor(ORG, STAFF)).thenReturn(new BigDecimal("2000"));

        assertThatThrownBy(() -> service.recordEntry(STAFF, entry("RECOVERY", "5000")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("exceeds the outstanding");
        verify(entryRepo, never()).save(any());
    }

    @Test
    void recordEntry_recoveryWithinOutstanding_succeeds() {
        when(staffRepo.findByIdAndOrgId(STAFF, ORG)).thenReturn(Optional.of(member(true)));
        when(entryRepo.outstandingFor(ORG, STAFF)).thenReturn(new BigDecimal("5000"));

        var r = service.recordEntry(STAFF, entry("RECOVERY", "2000"));

        assertThat(r.kind()).isEqualTo("RECOVERY");
    }

    @Test
    void recordEntry_deactivatedStaff_takesNothing() {
        when(staffRepo.findByIdAndOrgId(STAFF, ORG)).thenReturn(Optional.of(member(false)));

        assertThatThrownBy(() -> service.recordEntry(STAFF, entry("ADVANCE", "1000")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("deactivated");
        verify(entryRepo, never()).save(any());
    }

    @Test
    void addMember_reactivatesInsteadOfDuplicating() {
        StaffMember inactive = member(false);
        when(staffRepo.findByOrgIdAndNameIgnoreCase(ORG, "Ramesh")).thenReturn(Optional.of(inactive));

        var r = service.addMember(staff("Ramesh", null));

        assertThat(r.id()).isEqualTo(STAFF);
        assertThat(r.active()).isTrue();
        assertThat(inactive.isActive()).isTrue();
    }

    @Test
    void members_reportsDerivedOutstanding() {
        when(staffRepo.findByOrgIdOrderByNameAsc(ORG)).thenReturn(List.of(member(true)));
        when(entryRepo.outstandingFor(ORG, STAFF)).thenReturn(new BigDecimal("3000"));

        var rows = service.members();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).outstanding()).isEqualByComparingTo("3000");
    }

    // ---------------------------------------------------------- fixtures

    private static StaffMember member(boolean active) {
        StaffMember s = new StaffMember();
        ReflectionTestUtils.setField(s, "id", STAFF);
        s.setOrgId(ORG);
        s.setName("Ramesh");
        s.setActive(active);
        return s;
    }

    private static CreateStaffRequest staff(String name, String phone) {
        CreateStaffRequest r = new CreateStaffRequest();
        r.setName(name);
        r.setPhone(phone);
        return r;
    }

    private static CreateAdvanceEntryRequest entry(String kind, String amount) {
        CreateAdvanceEntryRequest r = new CreateAdvanceEntryRequest();
        r.setKind(kind);
        r.setAmount(new BigDecimal(amount));
        r.setTxnDate(LocalDate.now());
        return r;
    }
}
