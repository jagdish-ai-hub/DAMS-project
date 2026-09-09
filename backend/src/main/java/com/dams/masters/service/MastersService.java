package com.dams.masters.service;

import com.dams.common.entity.OrgMaster;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.MasterType;
import com.dams.masters.dto.MasterRequest;
import com.dams.masters.dto.MasterResponse;
import com.dams.masters.entity.*;
import com.dams.masters.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One dispatch point for all eight Owner-editable masters (see {@link MasterType}).
 * The lists are uniform CRUD — a single parameterised service is clearer to maintain
 * than eight near-identical copies. Every operation is org-scoped via TenantContext;
 * single-row reads go through findByIdAndOrgId so a cross-org id can't be fetched by PK.
 */
@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class MastersService {

    private static final Logger log = LoggerFactory.getLogger(MastersService.class);

    private final Map<MasterType, OrgMasterRepository> repos = new EnumMap<>(MasterType.class);
    private final Map<MasterType, Supplier<? extends OrgMaster>> factories = new EnumMap<>(MasterType.class);
    private final ExpenseSubCategoryRepository subCategoryRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;

    public MastersService(ReceiveCategoryRepository receiveCategoryRepo,
                          ReceiveBusinessStatusRepository receiveStatusRepo,
                          SettlementModeRepository settlementModeRepo,
                          ExpenseCategoryRepository expenseCategoryRepo,
                          ExpenseSubCategoryRepository subCategoryRepo,
                          ExpenseModeRepository expenseModeRepo,
                          ExpenseBusinessStatusRepository expenseStatusRepo,
                          BankRepository bankRepo) {
        this.subCategoryRepo = subCategoryRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;

        repos.put(MasterType.RECEIVE_CATEGORIES, receiveCategoryRepo);
        repos.put(MasterType.RECEIVE_STATUSES, receiveStatusRepo);
        repos.put(MasterType.SETTLEMENT_MODES, settlementModeRepo);
        repos.put(MasterType.EXPENSE_CATEGORIES, expenseCategoryRepo);
        repos.put(MasterType.EXPENSE_SUB_CATEGORIES, subCategoryRepo);
        repos.put(MasterType.EXPENSE_MODES, expenseModeRepo);
        repos.put(MasterType.EXPENSE_STATUSES, expenseStatusRepo);
        repos.put(MasterType.BANKS, bankRepo);

        factories.put(MasterType.RECEIVE_CATEGORIES, ReceiveCategory::new);
        factories.put(MasterType.RECEIVE_STATUSES, ReceiveBusinessStatus::new);
        factories.put(MasterType.SETTLEMENT_MODES, SettlementMode::new);
        factories.put(MasterType.EXPENSE_CATEGORIES, ExpenseCategory::new);
        factories.put(MasterType.EXPENSE_SUB_CATEGORIES, ExpenseSubCategory::new);
        factories.put(MasterType.EXPENSE_MODES, ExpenseMode::new);
        factories.put(MasterType.EXPENSE_STATUSES, ExpenseBusinessStatus::new);
        factories.put(MasterType.BANKS, Bank::new);
    }

    @Transactional(readOnly = true)
    public List<MasterResponse> list(MasterType type, Long expenseCategoryId) {
        Long orgId = TenantContext.requireOrgId();

        List<? extends OrgMaster> rows;
        if (type.isExpenseSubCategory() && expenseCategoryId != null) {
            rows = subCategoryRepo.findByOrgIdAndExpenseCategoryIdOrderBySortOrderAscIdAsc(orgId, expenseCategoryId);
        } else {
            rows = repos.get(type).findByOrgIdOrderBySortOrderAscIdAsc(orgId);
        }
        return rows.stream().map(m -> MasterResponse.of(type, m)).toList();
    }

    @Transactional(readOnly = true)
    public MasterResponse get(MasterType type, Long id) {
        return MasterResponse.of(type, load(type, id));
    }

    @Transactional
    public MasterResponse create(MasterType type, MasterRequest request) {
        Long orgId = TenantContext.requireOrgId();
        String name = request.getName().trim();

        OrgMaster entity = factories.get(type).get();
        entity.setOrgId(orgId);
        entity.setName(name);
        entity.setActive(true);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        applyTypeSpecific(type, entity, request, true);
        checkNameFree(type, orgId, entity, name, null);

        OrgMaster saved = (OrgMaster) repos.get(type).save(entity);
        log.info("Master created: orgId={} type={} id={} name='{}'", orgId, type.slug(), saved.getId(), name);
        return MasterResponse.of(type, saved);
    }

    @Transactional
    public MasterResponse update(MasterType type, Long id, MasterRequest request) {
        Long orgId = TenantContext.requireOrgId();
        OrgMaster entity = load(type, id);
        String name = request.getName().trim();

        applyTypeSpecific(type, entity, request, false);
        checkNameFree(type, orgId, entity, name, entity.getId());

        entity.setName(name);
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }

        OrgMaster saved = (OrgMaster) repos.get(type).save(entity);
        log.info("Master updated: orgId={} type={} id={} name='{}' active={}",
            orgId, type.slug(), saved.getId(), saved.getName(), saved.isActive());
        return MasterResponse.of(type, saved);
    }

    /**
     * Delete every master row for an org. Used ONLY by the Super Admin org-purge
     * (com.dams.admin) — it takes an explicit orgId and does not read TenantContext.
     */
    @Transactional
    public void purgeOrg(long orgId) {
        // expense_sub_category has an FK to expense_category — delete the children first,
        // whatever order the EnumMap iterates in.
        subCategoryRepo.deleteByOrgId(orgId);
        repos.forEach((type, repo) -> {
            if (type != MasterType.EXPENSE_SUB_CATEGORIES) {
                repo.deleteByOrgId(orgId);
            }
        });
    }

    // --- helpers ---

    private OrgMaster load(MasterType type, Long id) {
        Long orgId = TenantContext.requireOrgId();
        Optional<OrgMaster> found = repos.get(type).findByIdAndOrgId(id, orgId);
        return found.orElseThrow(() -> DamsException.notFound("Master row", id));
    }

    private void applyTypeSpecific(MasterType type, OrgMaster entity, MasterRequest req, boolean isCreate) {
        if (type.hasClaimFlag() && entity instanceof ReceiveCategory rc) {
            if (req.getIsClaim() != null) {
                rc.setClaim(req.getIsClaim());
            }
        } else if (type.hasModeFlags() && entity instanceof SettlementMode sm) {
            if (req.getRequiresBank() != null) {
                sm.setRequiresBank(req.getRequiresBank());
            }
            if (req.getRequiresRef() != null) {
                sm.setRequiresRef(req.getRequiresRef());
            }
            if (req.getIsCash() != null) {
                sm.setCash(req.getIsCash());
            }
        } else if (type.hasModeFlags() && entity instanceof ExpenseMode em) {
            if (req.getRequiresBank() != null) {
                em.setRequiresBank(req.getRequiresBank());
            }
            if (req.getRequiresRef() != null) {
                em.setRequiresRef(req.getRequiresRef());
            }
            if (req.getIsCash() != null) {
                em.setCash(req.getIsCash());
            }
        } else if (type.hasClaimTriggerFlag() && entity instanceof ExpenseBusinessStatus ebs) {
            if (req.getTriggersClaim() != null) {
                ebs.setTriggersClaim(req.getTriggersClaim());
            }
        } else if (type.isExpenseSubCategory() && entity instanceof ExpenseSubCategory esc) {
            Long parentId = req.getExpenseCategoryId();
            if (isCreate && parentId == null) {
                throw DamsException.badRequest("expenseCategoryId is required for an expense sub-category");
            }
            if (parentId != null) {
                Long orgId = TenantContext.requireOrgId();
                expenseCategoryRepo.findByIdAndOrgId(parentId, orgId)
                    .orElseThrow(() -> DamsException.notFound("Expense category", parentId));
                esc.setExpenseCategoryId(parentId);
            }
            esc.setLimitAmount(req.getLimitAmount());
        }
    }

    /** Names are unique per org (per parent category for sub-categories). */
    private void checkNameFree(MasterType type, Long orgId, OrgMaster entity, String name, Long selfId) {
        boolean clash;
        if (type.isExpenseSubCategory() && entity instanceof ExpenseSubCategory esc) {
            clash = subCategoryRepo.existsByOrgIdAndExpenseCategoryIdAndNameIgnoreCase(
                orgId, esc.getExpenseCategoryId(), name);
        } else {
            clash = repos.get(type).existsByOrgIdAndNameIgnoreCase(orgId, name);
        }
        // On update, a row keeping its own name is fine.
        if (clash && (selfId == null || !name.equalsIgnoreCase(entity.getName()))) {
            throw DamsException.conflict("'" + name + "' already exists in this list");
        }
    }
}
