package com.dams.masters.service;

import com.dams.masters.entity.Bank;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a brand-new organization with the standard master catalogue — the same set the
 * demo dealership ships with (Flyway V5), flags and all: settlement / expense
 * {@code is_cash} (drives the Cash-page drawer), expense business-status
 * {@code triggers_claim}, receive-category {@code is_claim}.
 *
 * Without this, a freshly-onboarded Owner signs in to empty dropdowns everywhere and can't
 * record a single receipt until every mode, category and status has been typed by hand.
 * Runs inside {@code AdminOrgService.createOrganization}'s transaction.
 */
@Service
public class MasterProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(MasterProvisioningService.class);

    private final ReceiveCategoryRepository receiveCategoryRepo;
    private final ReceiveBusinessStatusRepository receiveBusinessStatusRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final ExpenseSubCategoryRepository expenseSubCategoryRepo;
    private final ExpenseModeRepository expenseModeRepo;
    private final ExpenseBusinessStatusRepository expenseBusinessStatusRepo;
    private final BankRepository bankRepo;

    public MasterProvisioningService(ReceiveCategoryRepository receiveCategoryRepo,
                                     ReceiveBusinessStatusRepository receiveBusinessStatusRepo,
                                     SettlementModeRepository settlementModeRepo,
                                     ExpenseCategoryRepository expenseCategoryRepo,
                                     ExpenseSubCategoryRepository expenseSubCategoryRepo,
                                     ExpenseModeRepository expenseModeRepo,
                                     ExpenseBusinessStatusRepository expenseBusinessStatusRepo,
                                     BankRepository bankRepo) {
        this.receiveCategoryRepo = receiveCategoryRepo;
        this.receiveBusinessStatusRepo = receiveBusinessStatusRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.expenseSubCategoryRepo = expenseSubCategoryRepo;
        this.expenseModeRepo = expenseModeRepo;
        this.expenseBusinessStatusRepo = expenseBusinessStatusRepo;
        this.bankRepo = bankRepo;
    }

    public void provisionDefaults(Long orgId) {
        receiveCategoryRepo.saveAll(List.of(
            receiveCategory(orgId, "Workshop", false, 1),
            receiveCategory(orgId, "Breakdown", false, 2),
            receiveCategory(orgId, "Advance", false, 3),
            receiveCategory(orgId, "Spare / Counter", false, 4),
            receiveCategory(orgId, "AdBlue Bucket", false, 5),
            receiveCategory(orgId, "AdBlue Barrel", false, 6),
            receiveCategory(orgId, "AMC", true, 7),
            receiveCategory(orgId, "Warranty", true, 8),
            receiveCategory(orgId, "Goodwill", true, 9),
            receiveCategory(orgId, "B2B Credit", false, 10),
            receiveCategory(orgId, "Scrap / Used Lubes / Other", false, 11)));

        receiveBusinessStatusRepo.saveAll(List.of(
            named(new ReceiveBusinessStatus(), orgId, "Hold", 1),
            named(new ReceiveBusinessStatus(), orgId, "AMC", 2),
            named(new ReceiveBusinessStatus(), orgId, "CG", 3),
            named(new ReceiveBusinessStatus(), orgId, "WIP", 4),
            named(new ReceiveBusinessStatus(), orgId, "Warranty", 5),
            named(new ReceiveBusinessStatus(), orgId, "Credit", 6),
            named(new ReceiveBusinessStatus(), orgId, "Close", 7)));

        settlementModeRepo.saveAll(List.of(
            settlementMode(orgId, "Cash", false, false, true, 1),
            settlementMode(orgId, "QR / UPI", false, true, false, 2),
            settlementMode(orgId, "Bank", true, true, false, 3),
            settlementMode(orgId, "Card", false, false, false, 4),
            settlementMode(orgId, "Adv-QR", false, true, false, 5),
            settlementMode(orgId, "Adv-Cash", false, false, true, 6),
            settlementMode(orgId, "Credit (Due)", false, false, false, 7)));

        Map<String, Long> categoryId = new HashMap<>();
        for (ExpenseCategory c : expenseCategoryRepo.saveAll(List.of(
                named(new ExpenseCategory(), orgId, "Service", 1),
                named(new ExpenseCategory(), orgId, "Sales", 2),
                named(new ExpenseCategory(), orgId, "Showroom", 3),
                named(new ExpenseCategory(), orgId, "Finance", 4)))) {
            categoryId.put(c.getName(), c.getId());
        }

        Long service = categoryId.get("Service");
        Long sales = categoryId.get("Sales");
        Long showroom = categoryId.get("Showroom");
        expenseSubCategoryRepo.saveAll(List.of(
            subCategory(orgId, service, "Food (BD)", 500, 1),
            subCategory(orgId, service, "Spare Transport", 1000, 2),
            subCategory(orgId, service, "Taxi (JC)", 2000, 3),
            subCategory(orgId, service, "Fuel (JC)", 1000, 4),
            subCategory(orgId, service, "Local Purchase (JC)", 2000, 5),
            subCategory(orgId, service, "Courier Charges", 500, 6),
            subCategory(orgId, showroom, "Stationary", 1500, 1),
            subCategory(orgId, showroom, "Misc. Office", 2000, 2),
            subCategory(orgId, showroom, "Site Repair", 5000, 3),
            subCategory(orgId, showroom, "Daily Wages", 5000, 4),
            subCategory(orgId, sales, "Sales Promotion", 5000, 1),
            subCategory(orgId, sales, "RTO Expenses", 3000, 2),
            subCategory(orgId, sales, "Misc. Sales", 2000, 3)));

        expenseModeRepo.saveAll(List.of(
            expenseMode(orgId, "Cash", false, false, true, 1),
            expenseMode(orgId, "QR / UPI", false, true, false, 2),
            expenseMode(orgId, "Bank", true, true, false, 3)));

        expenseBusinessStatusRepo.saveAll(List.of(
            expenseBusinessStatus(orgId, "Open", false, 1),
            expenseBusinessStatus(orgId, "In Progress", false, 2),
            expenseBusinessStatus(orgId, "Awaiting Receipt", false, 3),
            expenseBusinessStatus(orgId, "Received Receipt", false, 4),
            expenseBusinessStatus(orgId, "Closed", false, 5),
            expenseBusinessStatus(orgId, "Transfer to Claim", true, 6)));

        bankRepo.saveAll(List.of(
            named(new Bank(), orgId, "State Bank of India", 1),
            named(new Bank(), orgId, "HDFC Bank", 2),
            named(new Bank(), orgId, "ICICI Bank", 3),
            named(new Bank(), orgId, "Axis Bank", 4),
            named(new Bank(), orgId, "Bank of Baroda", 5),
            named(new Bank(), orgId, "Punjab National Bank", 6)));

        log.info("Provisioned default masters for orgId={}", orgId);
    }

    // --- builders ---

    private static <T extends com.dams.common.entity.OrgMaster> T named(T m, Long orgId, String name, int sort) {
        m.setOrgId(orgId);
        m.setName(name);
        m.setSortOrder(sort);
        return m;
    }

    private static ReceiveCategory receiveCategory(Long orgId, String name, boolean claim, int sort) {
        ReceiveCategory m = named(new ReceiveCategory(), orgId, name, sort);
        m.setClaim(claim);
        return m;
    }

    private static SettlementMode settlementMode(Long orgId, String name,
                                                 boolean requiresBank, boolean requiresRef, boolean cash, int sort) {
        SettlementMode m = named(new SettlementMode(), orgId, name, sort);
        m.setRequiresBank(requiresBank);
        m.setRequiresRef(requiresRef);
        m.setCash(cash);
        return m;
    }

    private static ExpenseMode expenseMode(Long orgId, String name,
                                           boolean requiresBank, boolean requiresRef, boolean cash, int sort) {
        ExpenseMode m = named(new ExpenseMode(), orgId, name, sort);
        m.setRequiresBank(requiresBank);
        m.setRequiresRef(requiresRef);
        m.setCash(cash);
        return m;
    }

    private static ExpenseBusinessStatus expenseBusinessStatus(Long orgId, String name, boolean triggersClaim, int sort) {
        ExpenseBusinessStatus m = named(new ExpenseBusinessStatus(), orgId, name, sort);
        m.setTriggersClaim(triggersClaim);
        return m;
    }

    private static ExpenseSubCategory subCategory(Long orgId, Long categoryId, String name, long limit, int sort) {
        ExpenseSubCategory m = named(new ExpenseSubCategory(), orgId, name, sort);
        m.setExpenseCategoryId(categoryId);
        m.setLimitAmount(BigDecimal.valueOf(limit));
        return m;
    }
}
