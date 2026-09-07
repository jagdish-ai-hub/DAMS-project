package com.dams.budget.service;

import com.dams.budget.dto.BudgetResponse;
import com.dams.budget.dto.BudgetUpsertRequest;
import com.dams.budget.entity.ExpenseBudget;
import com.dams.budget.repository.ExpenseBudgetRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.repository.ExpenseCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Monthly spend caps per expense category. Reads are org-scoped and open to any org role
 * that can see money figures; writes are OWNER-only (enforced on the controller).
 * Upsert on the natural key {@code (category, month)} — setting a cap twice just moves it.
 */
@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final ExpenseBudgetRepository budgetRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;

    public BudgetService(ExpenseBudgetRepository budgetRepo,
                         ExpenseCategoryRepository expenseCategoryRepo) {
        this.budgetRepo = budgetRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(String monthKey) {
        Long orgId = TenantContext.requireOrgId();
        List<ExpenseBudget> rows = monthKey == null
            ? budgetRepo.findByOrgIdOrderByMonthKeyDescCategoryIdAsc(orgId)
            : budgetRepo.findByOrgIdAndMonthKeyOrderByCategoryIdAsc(orgId, monthKey);
        return rows.stream().map(BudgetResponse::of).toList();
    }

    @Transactional
    public BudgetResponse upsert(BudgetUpsertRequest request) {
        Long orgId = TenantContext.requireOrgId();
        BigDecimal cap = request.getCapAmount();
        if (cap == null || cap.signum() <= 0) {
            throw DamsException.badRequest("capAmount must be greater than zero");
        }
        expenseCategoryRepo.findByIdAndOrgId(request.getCategoryId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Expense category", request.getCategoryId()));

        ExpenseBudget budget = budgetRepo
            .findByOrgIdAndCategoryIdAndMonthKey(orgId, request.getCategoryId(), request.getMonthKey())
            .orElseGet(() -> {
                ExpenseBudget b = new ExpenseBudget();
                b.setOrgId(orgId);
                b.setCategoryId(request.getCategoryId());
                b.setMonthKey(request.getMonthKey());
                return b;
            });
        boolean created = budget.getId() == null;
        budget.setCapAmount(cap);
        budget.setUpdatedAt(Instant.now());
        budget = budgetRepo.save(budget);

        log.info("Expense budget {}: orgId={} categoryId={} monthKey={} cap={}",
            created ? "created" : "updated", orgId, budget.getCategoryId(), budget.getMonthKey(), cap);
        return BudgetResponse.of(budget);
    }
}
