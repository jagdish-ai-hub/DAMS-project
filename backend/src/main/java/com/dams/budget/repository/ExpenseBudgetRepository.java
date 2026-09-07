package com.dams.budget.repository;

import com.dams.budget.entity.ExpenseBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, Long> {

    List<ExpenseBudget> findByOrgIdAndMonthKeyOrderByCategoryIdAsc(Long orgId, String monthKey);

    List<ExpenseBudget> findByOrgIdOrderByMonthKeyDescCategoryIdAsc(Long orgId);

    Optional<ExpenseBudget> findByOrgIdAndCategoryIdAndMonthKey(Long orgId, Long categoryId, String monthKey);

    Optional<ExpenseBudget> findByIdAndOrgId(Long id, Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
