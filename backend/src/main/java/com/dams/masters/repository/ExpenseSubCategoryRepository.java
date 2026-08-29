package com.dams.masters.repository;

import com.dams.masters.entity.ExpenseSubCategory;

import java.util.List;

public interface ExpenseSubCategoryRepository extends OrgMasterRepository<ExpenseSubCategory> {

    List<ExpenseSubCategory> findByOrgIdAndExpenseCategoryIdOrderBySortOrderAscIdAsc(Long orgId, Long expenseCategoryId);

    boolean existsByOrgIdAndExpenseCategoryIdAndNameIgnoreCase(Long orgId, Long expenseCategoryId, String name);
}
