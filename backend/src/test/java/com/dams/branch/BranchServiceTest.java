package com.dams.branch;

import com.dams.branch.dto.BranchRequest;
import com.dams.branch.dto.BranchResponse;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.BranchService;
import com.dams.common.exception.DamsException;
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

    private BranchService service;

    @BeforeEach
    void setUp() {
        service = new BranchService(branchRepo, userRepo, branchAccessRepo);
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
}
