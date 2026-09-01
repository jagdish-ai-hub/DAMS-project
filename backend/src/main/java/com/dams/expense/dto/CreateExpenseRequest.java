package com.dams.expense.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * New Expense — one continuous flow (AGENT.md). The receiver is either an existing master
 * ({@code receiverId}) or created inline from {@code receiverName} (deduped on name, like
 * Customer). {@code jobCardId} is optional — an expense may be pure branch overhead.
 *
 * There is deliberately <b>no branch field</b> — the document always posts under the
 * cashier's fixed home branch.
 *
 * {@code lines} may be empty (Save Draft with the header only). {@code submit = true} also
 * submits the document (assigns its number, stamps line ids, moves it to SUBMITTED).
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateExpenseRequest {

    private Long jobCardId;

    private Long receiverId;
    @Size(max = 160) private String receiverName;
    @Size(max = 32) private String receiverPhone;

    @NotNull(message = "expenseCategoryId is required")
    private Long expenseCategoryId;

    @NotNull(message = "businessStatusId is required")
    private Long businessStatusId;

    @Valid
    private List<ExpenseLineInput> lines = new ArrayList<>();

    private boolean submit;

    public boolean hasReceiverId() {
        return receiverId != null;
    }
}
