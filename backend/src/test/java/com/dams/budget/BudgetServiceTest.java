package com.dams.budget;

import com.dams.budget.dto.BudgetResponse;
import com.dams.budget.dto.BudgetUpsertRequest;
import com.dams.budget.entity.ExpenseBudget;
import com.dams.budget.repository.ExpenseBudgetRepository;
import com.dams.budget.service.BudgetService;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.repository.ExpenseCategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Expense budgets: upsert creates on the (category, month) natural key, moves the cap when
 * the row exists, and refuses a non-positive cap. Caps never block submissions — they only
 * inform — so there is no spend-vs-cap gate to test here.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final long ORG = 1L;
    private static final long CATEGORY = 31L;

    @Mock private ExpenseBudgetRepository budgetRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepo, expenseCategoryRepo);
        TenantContext.setOrgId(ORG);
        ExpenseCategory category = new ExpenseCategory();
        ReflectionTestUtils.setField(category, "id", CATEGORY);
        lenient().when(expenseCategoryRepo.findByIdAndOrgId(CATEGORY, ORG))
            .thenReturn(Optional.of(category));
        lenient().when(budgetRepo.save(any(ExpenseBudget.class)))
            .thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upsert_createsBudget_whenNoRowExistsForCategoryAndMonth() {
        when(budgetRepo.findByOrgIdAndCategoryIdAndMonthKey(ORG, CATEGORY, "202608"))
            .thenReturn(Optional.empty());

        BudgetResponse response = service.upsert(request(new BigDecimal("50000")));

        assertThat(response.categoryId()).isEqualTo(CATEGORY);
        assertThat(response.monthKey()).isEqualTo("202608");
        assertThat(response.capAmount()).isEqualByComparingTo("50000");
        verify(budgetRepo).save(any(ExpenseBudget.class));
    }

    @Test
    void upsert_movesCap_whenRowAlreadyExists() {
        ExpenseBudget existing = budget(900L, new BigDecimal("40000"));
        when(budgetRepo.findByOrgIdAndCategoryIdAndMonthKey(ORG, CATEGORY, "202608"))
            .thenReturn(Optional.of(existing));

        BudgetResponse response = service.upsert(request(new BigDecimal("75000")));

        assertThat(response.id()).isEqualTo(900L);
        assertThat(existing.getCapAmount()).isEqualByComparingTo("75000");
    }

    @Test
    void upsert_rejectsNonPositiveCap() {
        assertThatThrownBy(() -> service.upsert(request(new BigDecimal("0"))))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("greater than zero");
        verify(budgetRepo, never()).save(any());
    }

    @Test
    void upsert_notFound_whenCategoryIsOutsideTheOrg() {
        when(expenseCategoryRepo.findByIdAndOrgId(999L, ORG)).thenReturn(Optional.empty());

        BudgetUpsertRequest bad = new BudgetUpsertRequest();
        bad.setCategoryId(999L);
        bad.setMonthKey("202608");
        bad.setCapAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> service.upsert(bad)).isInstanceOf(DamsException.class);
        verify(budgetRepo, never()).save(any());
    }

    private static BudgetUpsertRequest request(BigDecimal cap) {
        BudgetUpsertRequest r = new BudgetUpsertRequest();
        r.setCategoryId(CATEGORY);
        r.setMonthKey("202608");
        r.setCapAmount(cap);
        return r;
    }

    private static ExpenseBudget budget(Long id, BigDecimal cap) {
        ExpenseBudget b = new ExpenseBudget();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCategoryId(CATEGORY);
        b.setMonthKey("202608");
        b.setCapAmount(cap);
        return b;
    }
}
