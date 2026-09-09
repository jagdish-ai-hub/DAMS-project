package com.dams.masters;

import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.masters.service.MastersService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * The Super Admin org-purge deletes every master row. {@code expense_sub_category} has an
 * FK to {@code expense_category}, so the children must go first — otherwise the delete
 * fails with a foreign-key violation (hit once a provisioned/edited org has sub-categories).
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

    @Test
    void purgeOrg_deletesSubCategoriesBeforeTheirParentCategory() {
        MastersService service = new MastersService(receiveCategoryRepo, receiveStatusRepo, settlementModeRepo,
            expenseCategoryRepo, subCategoryRepo, expenseModeRepo, expenseStatusRepo, bankRepo);

        service.purgeOrg(99L);

        InOrder order = inOrder(subCategoryRepo, expenseCategoryRepo);
        order.verify(subCategoryRepo).deleteByOrgId(99L);
        order.verify(expenseCategoryRepo).deleteByOrgId(99L);
    }

    @Test
    void purgeOrg_deletesEverySubCategoryRowExactlyOnce() {
        MastersService service = new MastersService(receiveCategoryRepo, receiveStatusRepo, settlementModeRepo,
            expenseCategoryRepo, subCategoryRepo, expenseModeRepo, expenseStatusRepo, bankRepo);

        service.purgeOrg(99L);

        verify(subCategoryRepo).deleteByOrgId(99L);
        verify(bankRepo).deleteByOrgId(99L);
        verify(receiveCategoryRepo).deleteByOrgId(99L);
    }
}
