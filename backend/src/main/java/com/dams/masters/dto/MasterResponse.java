package com.dams.masters.dto;

import com.dams.common.entity.OrgMaster;
import com.dams.masters.MasterType;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Master row. Type-specific fields are omitted from JSON when they don't apply. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MasterResponse(
    Long id,
    String type,
    String name,
    boolean active,
    int sortOrder,
    Boolean isClaim,
    Boolean requiresBank,
    Boolean requiresRef,
    Boolean isCash,
    Boolean triggersClaim,
    Long expenseCategoryId,
    BigDecimal limitAmount
) {

    public static MasterResponse of(MasterType type, OrgMaster m) {
        Boolean isClaim = null;
        Boolean requiresBank = null;
        Boolean requiresRef = null;
        Boolean isCash = null;
        Boolean triggersClaim = null;
        Long expenseCategoryId = null;
        BigDecimal limitAmount = null;

        if (m instanceof ReceiveCategory rc) {
            isClaim = rc.isClaim();
        } else if (m instanceof SettlementMode sm) {
            requiresBank = sm.isRequiresBank();
            requiresRef = sm.isRequiresRef();
            isCash = sm.isCash();
        } else if (m instanceof ExpenseMode em) {
            requiresBank = em.isRequiresBank();
            requiresRef = em.isRequiresRef();
            isCash = em.isCash();
        } else if (m instanceof ExpenseBusinessStatus ebs) {
            triggersClaim = ebs.isTriggersClaim();
        } else if (m instanceof ExpenseSubCategory esc) {
            expenseCategoryId = esc.getExpenseCategoryId();
            limitAmount = esc.getLimitAmount();
        }

        return new MasterResponse(
            m.getId(), type.slug(), m.getName(), m.isActive(), m.getSortOrder(),
            isClaim, requiresBank, requiresRef, isCash, triggersClaim, expenseCategoryId, limitAmount);
    }
}
