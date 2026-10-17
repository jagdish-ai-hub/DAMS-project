package com.dams.branch;

import com.dams.branch.dto.BranchRequest;
import com.dams.branch.dto.BranchResponse;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.BranchService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    private static final long ORG = 1L;

    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private UserBranchAccessRepository branchAccessRepo;
    @Mock private BranchScope branchScope;

    private BranchService service;

    @BeforeEach
    void setUp() {
        service = new BranchService(branchRepo, userRepo, branchAccessRepo, branchScope);
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_normalisesCodeToUpperCase_andStampsOrgFromContext() {
        when(branchRepo.existsByOrgIdAndCodeIgnoreCase(ORG, "OOK")).thenReturn(false);
        when(branchRepo.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchRequest req = new BranchRequest();
        req.setName("  Koraput ");
        req.setCode("ook");

        BranchResponse result = service.create(req);

        assertThat(result.code()).isEqualTo("OOK");
        assertThat(result.name()).isEqualTo("Koraput");

        ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
        org.mockito.Mockito.verify(branchRepo).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG);
        assertThat(captor.getValue().getCode()).isEqualTo("OOK");
    }

    @Test
    void create_throwsConflict_whenCodeAlreadyUsedInOrg() {
        when(branchRepo.existsByOrgIdAndCodeIgnoreCase(ORG, "OOR")).thenReturn(true);

        BranchRequest req = new BranchRequest();
        req.setName("Rayagada");
        req.setCode("OOR");

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("OOR");
    }

    @Test
    void get_throwsNotFound_whenBranchBelongsToAnotherOrg() {
        when(branchRepo.findByIdAndOrgId(99L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Branch");
    }

    @Test
    void list_returnsOnlyBranchesInCallersScope() {
        Branch allowed = branch(2L, "OOB");
        Branch outside = branch(3L, "OOR");
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(allowed, outside));
        when(branchScope.allowedBranchIds()).thenReturn(Optional.of(java.util.Set.of(2L)));
        when(userRepo.countByHomeBranchGrouped(ORG)).thenReturn(List.of());
        when(branchAccessRepo.countByBranchIdGrouped(List.of(2L))).thenReturn(List.of());

        assertThat(service.list()).extracting(BranchResponse::id).containsExactly(2L);
    }

    private static Branch branch(long id, String code) {
        Branch branch = new Branch();
        org.springframework.test.util.ReflectionTestUtils.setField(branch, "id", id);
        branch.setOrgId(ORG);
        branch.setCode(code);
        branch.setName(code);
        branch.setActive(true);
        return branch;
    }
}
