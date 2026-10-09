package com.dams.dashboard.service;

import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.entity.PreApprovalStatus;
import com.dams.receive.entity.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.dams.dashboard.service.PendingWorkService.ACCOUNTANT;
import static com.dams.dashboard.service.PendingWorkService.CASHIER;
import static com.dams.dashboard.service.PendingWorkService.FINANCE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;

/** rev 71 — who an entry is "stuck with", for every state. Pure rules, no database. */
class PendingWorkHoldTest {

    private static ExpenseDocument expense(ExpenseWorkflowStatus status, PreApprovalStatus pre) {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "workflowStatus", status);
        ReflectionTestUtils.setField(d, "preApprovalStatus", pre);
        return d;
    }

    @Test
    void receipts_followTheWorkflow() {
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.DRAFT).holder()).isEqualTo(CASHIER);
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.DRAFT).draft()).isTrue();
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.QUERIED).holder()).isEqualTo(CASHIER);
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.QUERIED).draft()).isFalse();
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.SUBMITTED).holder()).isEqualTo(ACCOUNTANT);
        // an FM query comes back to the Accountant, not the Cashier (rev 49)
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.FM_QUERIED).holder()).isEqualTo(ACCOUNTANT);
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.VERIFIED).holder()).isEqualTo(FINANCE_MANAGER);
    }

    @Test
    void finishedReceiptsAndCash_areHeldByNobody() {
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.APPROVED)).isNull();
        assertThat(PendingWorkService.receiptHold(WorkflowStatus.REJECTED)).isNull();
        assertThat(PendingWorkService.cashHold(CashWorkflowStatus.APPROVED)).isNull();
        assertThat(PendingWorkService.cashHold(CashWorkflowStatus.REJECTED)).isNull();
    }

    @Test
    void cash_followsTheSameRoute() {
        assertThat(PendingWorkService.cashHold(CashWorkflowStatus.QUERIED).holder()).isEqualTo(CASHIER);
        assertThat(PendingWorkService.cashHold(CashWorkflowStatus.SUBMITTED).holder()).isEqualTo(ACCOUNTANT);
        assertThat(PendingWorkService.cashHold(CashWorkflowStatus.FM_QUERIED).holder()).isEqualTo(ACCOUNTANT);
        assertThat(PendingWorkService.cashHold(CashWorkflowStatus.VERIFIED).holder()).isEqualTo(FINANCE_MANAGER);
    }

    @Test
    void expenseDrafts_areTheFmsOnlyWhilePreApprovalIsPending() {
        var plain = PendingWorkService.expenseHold(expense(ExpenseWorkflowStatus.DRAFT, null), false, false, false);
        assertThat(plain.holder()).isEqualTo(CASHIER);
        assertThat(plain.draft()).isTrue();

        var pending = PendingWorkService.expenseHold(expense(ExpenseWorkflowStatus.DRAFT, PreApprovalStatus.PENDING), false, true, false);
        assertThat(pending.holder()).isEqualTo(FINANCE_MANAGER);
        assertThat(pending.draft()).isFalse();   // waiting on the FM, not an unsent draft

        var queried = PendingWorkService.expenseHold(expense(ExpenseWorkflowStatus.DRAFT, PreApprovalStatus.QUERIED), false, true, false);
        assertThat(queried.holder()).isEqualTo(CASHIER);
        assertThat(queried.draft()).isFalse();   // sent back to be edited, so it counts as waiting on the Cashier
    }

    @Test
    void verifiedExpense_goesToTheFmOnlyWhenItNeedsThem() {
        var verified = expense(ExpenseWorkflowStatus.VERIFIED, null);
        // ordinary, in limit → the Accountant closes it alone
        assertThat(PendingWorkService.expenseHold(verified, false, false, false).holder()).isEqualTo(ACCOUNTANT);
        // over limit / FM-approval status → FM
        assertThat(PendingWorkService.expenseHold(verified, false, true, false).holder()).isEqualTo(FINANCE_MANAGER);
        // …unless an FM pre-approval still covers it (rev 53): no second FM step
        assertThat(PendingWorkService.expenseHold(verified, false, true, true).holder()).isEqualTo(ACCOUNTANT);
        // a claim is always the FM's (Close Claim)
        assertThat(PendingWorkService.expenseHold(verified, true, false, false).holder()).isEqualTo(FINANCE_MANAGER);
    }

    @Test
    void approvedExpense_waitsForWhoeverClosesIt() {
        var approved = expense(ExpenseWorkflowStatus.APPROVED, null);
        assertThat(PendingWorkService.expenseHold(approved, false, false, false).holder()).isEqualTo(ACCOUNTANT);
        assertThat(PendingWorkService.expenseHold(approved, true, false, false).holder()).isEqualTo(FINANCE_MANAGER);
        assertThat(PendingWorkService.expenseHold(expense(ExpenseWorkflowStatus.CLOSED, null), false, false, false)).isNull();
        assertThat(PendingWorkService.expenseHold(expense(ExpenseWorkflowStatus.REJECTED, null), false, false, false)).isNull();
    }
}
