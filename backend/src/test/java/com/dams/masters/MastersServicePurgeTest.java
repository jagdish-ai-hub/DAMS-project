package com.dams.masters;

import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.common.security.BranchScope;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRoleRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.masters.repository.UpiVpaRepository;
import com.dams.masters.service.MastersService;
import com.dams.masters.service.ReceiveStatusAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * The Super Admin org-purge deletes every master row. Two tables carry an FK to another
 * master — {@code expense_sub_category} to {@code expense_category}, and
 * {@code receive_business_status_role} to {@code receive_business_status} — so both sets of
 * children must go first, or the delete fails with a foreign-key violation.
 */
@ExtendWith(MockitoExtension.class)
class MastersServicePurgeTest {

    @Mock private ReceiveCategoryRepository receiveCategoryRepo;
    @Mock private ReceiveBusinessStatusRepository receiveStatusRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ExpenseSubCategoryRepository subCategoryRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;
    @Mock private ExpenseBusinessStatusRepository expenseStatusRepo;
    @Mock private BankRepository bankRepo;
    @Mock private ClaimTypeRepository claimTypeRepo;
    @Mock private UpiVpaRepository upiVpaRepo;
    @Mock private ReceiveBusinessStatusRoleRepository receiveStatusRoleRepo;
    @Mock private ReceiveStatusAccessService statusAccess;
    @Mock private BranchScope branchScope;

    private MastersService service() {
        return new MastersService(receiveCategoryRepo, receiveStatusRepo, settlementModeRepo,
            expenseCategoryRepo, subCategoryRepo, expenseModeRepo, expenseStatusRepo, bankRepo, claimTypeRepo,
            upiVpaRepo, receiveStatusRoleRepo, statusAccess, branchScope);
    }

    @Test
    void purgeOrg_deletesSubCategoriesBeforeTheirParentCategory() {
        service().purgeOrg(99L);

        InOrder order = inOrder(subCategoryRepo, expenseCategoryRepo);
        order.verify(subCategoryRepo).deleteByOrgId(99L);
        order.verify(expenseCategoryRepo).deleteByOrgId(99L);
    }

    @Test
    void purgeOrg_deletesEverySubCategoryRowExactlyOnce() {
        service().purgeOrg(99L);

        verify(subCategoryRepo).deleteByOrgId(99L);
        verify(bankRepo).deleteByOrgId(99L);
        verify(receiveCategoryRepo).deleteByOrgId(99L);
        verify(claimTypeRepo).deleteByOrgId(99L);
    }

    @Test
    void purgeOrg_deletesStatusRoleGrantsBeforeTheStatusesTheyPointAt() {
        service().purgeOrg(99L);

        InOrder order = inOrder(receiveStatusRoleRepo, receiveStatusRepo);
        order.verify(receiveStatusRoleRepo).deleteByOrgId(99L);
        order.verify(receiveStatusRepo).deleteByOrgId(99L);
    }
}
