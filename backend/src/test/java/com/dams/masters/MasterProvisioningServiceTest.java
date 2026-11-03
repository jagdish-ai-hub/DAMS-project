package com.dams.masters;

import com.dams.masters.entity.Bank;
import com.dams.masters.entity.ClaimType;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveBusinessStatusRole;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRoleRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.masters.service.MasterProvisioningService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** rev 88 — a new organization copies the template organization's active lists and Owner switches. */
@ExtendWith(MockitoExtension.class)
class MasterProvisioningServiceTest {

    private static final long TEMPLATE = 1L;
    private static final long NEW_ORG = 9L;

    @Mock private ReceiveCategoryRepository receiveCategoryRepo;
    @Mock private ReceiveBusinessStatusRepository receiveBusinessStatusRepo;
    @Mock private ReceiveBusinessStatusRoleRepository receiveBusinessStatusRoleRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ExpenseSubCategoryRepository expenseSubCategoryRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;
    @Mock private ExpenseBusinessStatusRepository expenseBusinessStatusRepo;
    @Mock private BankRepository bankRepo;
    @Mock private ClaimTypeRepository claimTypeRepo;
    @Mock private OrganizationRepository organizationRepo;

    private MasterProvisioningService service;
    private final AtomicLong ids = new AtomicLong(1000);
    private final List<ReceiveBusinessStatus> savedStatuses = new ArrayList<>();
    private final List<ReceiveBusinessStatusRole> savedGrants = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MasterProvisioningService(receiveCategoryRepo, receiveBusinessStatusRepo, receiveBusinessStatusRoleRepo,
            settlementModeRepo, expenseCategoryRepo, expenseSubCategoryRepo, expenseModeRepo, expenseBusinessStatusRepo,
            bankRepo, claimTypeRepo, organizationRepo);
        lenient().when(receiveBusinessStatusRepo.save(any())).thenAnswer(i -> {
            ReceiveBusinessStatus s = i.getArgument(0);
            ReflectionTestUtils.setField(s, "id", ids.incrementAndGet());
            savedStatuses.add(s);
            return s;
        });
        lenient().when(receiveBusinessStatusRoleRepo.save(any())).thenAnswer(i -> {
            savedGrants.add(i.getArgument(0));
            return i.getArgument(0);
        });
        lenient().when(expenseCategoryRepo.save(any())).thenAnswer(i -> {
            ExpenseCategory c = i.getArgument(0);
            ReflectionTestUtils.setField(c, "id", ids.incrementAndGet());
            return c;
        });
    }

    @Test
    void copiesTheTemplatesActiveItems_withTheirFlagsLimitsAndRoles_andItsOwnerSwitches() {
        Organization template = org(TEMPLATE, "OORIBA MOTORS DEMO");
        template.setMasterTemplate(true);
        template.setAccountantDirectApproveCash(true);
        Organization fresh = org(NEW_ORG, "New Dealer");
        when(organizationRepo.findFirstByMasterTemplateTrue()).thenReturn(Optional.of(template));
        when(organizationRepo.findById(NEW_ORG)).thenReturn(Optional.of(fresh));

        when(receiveCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(
            master(new ReceiveCategory(), 11L, "Workshop", true, 1), master(new ReceiveCategory(), 12L, "B2B Credit", false, 2)));
        when(claimTypeRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(
            master(new ClaimType(), 21L, "Claim", true, 0), master(new ClaimType(), 22L, "Warranty", false, 8)));
        ReceiveBusinessStatus awaiting = master(new ReceiveBusinessStatus(), 31L, "Awaiting Claim", true, 4);
        ReceiveBusinessStatus retired = master(new ReceiveBusinessStatus(), 32L, "Close", true, 97);
        retired.setDeprecated(true);
        when(receiveBusinessStatusRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(awaiting, retired));
        when(receiveBusinessStatusRoleRepo.findByOrgId(TEMPLATE)).thenReturn(List.of(
            grant(31L, Role.CASHIER), grant(31L, Role.FINANCE_MANAGER), grant(32L, Role.CASHIER)));
        SettlementMode cash = master(new SettlementMode(), 41L, "Cash", true, 1);
        cash.setCash(true);
        when(settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(cash));
        when(expenseCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(
            master(new ExpenseCategory(), 51L, "Service", true, 1)));
        ExpenseSubCategory fuel = master(new ExpenseSubCategory(), 61L, "Fuel (JC)", true, 4);
        fuel.setExpenseCategoryId(51L);
        fuel.setLimitAmount(new BigDecimal("1000"));
        when(expenseSubCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(fuel));
        when(expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of());
        ExpenseBusinessStatus claim = master(new ExpenseBusinessStatus(), 71L, "Transfer to Claim", true, 6);
        claim.setTriggersClaim(true);
        ExpenseBusinessStatus fm = master(new ExpenseBusinessStatus(), 72L, "Requires Finance Approval", true, 7);
        fm.setRequiresFmApproval(true);
        when(expenseBusinessStatusRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(claim, fm));
        when(bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(TEMPLATE)).thenReturn(List.of(master(new Bank(), 81L, "HDFC Bank", true, 2)));

        service.provisionDefaults(NEW_ORG);

        List<ReceiveCategory> categories = captured(receiveCategoryRepo);
        assertThat(categories).extracting(ReceiveCategory::getName).containsExactly("Workshop");   // inactive left out
        assertThat(categories.get(0).getOrgId()).isEqualTo(NEW_ORG);
        assertThat(categories.get(0).getId()).isNull();                                       // new rows, not moved ones
        List<ClaimType> claimTypes = captured(claimTypeRepo);
        assertThat(claimTypes).extracting(ClaimType::getName).containsExactly("Claim");

        assertThat(savedStatuses).extracting(ReceiveBusinessStatus::getName).containsExactly("Awaiting Claim");  // retired left out
        assertThat(savedGrants).extracting(ReceiveBusinessStatusRole::getRole).containsExactly(Role.CASHIER, Role.FINANCE_MANAGER);
        assertThat(savedGrants).allSatisfy(g -> {
            assertThat(g.getOrgId()).isEqualTo(NEW_ORG);
            assertThat(g.getStatusId()).isEqualTo(savedStatuses.get(0).getId());
        });

        List<SettlementMode> modes = captured(settlementModeRepo);
        assertThat(modes).singleElement().satisfies(m -> assertThat(m.isCash()).isTrue());
        List<ExpenseSubCategory> subs = captured(expenseSubCategoryRepo);
        assertThat(subs).singleElement().satisfies(sc -> {
            assertThat(sc.getLimitAmount()).isEqualByComparingTo("1000");
            assertThat(sc.getExpenseCategoryId()).isNotEqualTo(51L);   // points at the NEW organization's Service
        });
        List<ExpenseBusinessStatus> statuses = captured(expenseBusinessStatusRepo);
        assertThat(statuses.get(0).isTriggersClaim()).isTrue();
        assertThat(statuses.get(1).isRequiresFmApproval()).isTrue();
        List<Bank> banks = captured(bankRepo);
        assertThat(banks).extracting(Bank::getName).containsExactly("HDFC Bank");

        assertThat(fresh.isAccountantDirectApproveCash()).isTrue();
        assertThat(fresh.isMultiBranchCashierAccess()).isFalse();
        assertThat(fresh.isMasterTemplate()).isFalse();
    }

    @Test
    void withNoTemplateOrganization_usesTheBuiltInCatalogue() {
        when(organizationRepo.findFirstByMasterTemplateTrue()).thenReturn(Optional.empty());

        service.provisionDefaults(NEW_ORG);

        List<ReceiveCategory> categories = captured(receiveCategoryRepo);
        assertThat(categories).extracting(ReceiveCategory::getName).contains("Workshop", "Breakdown");
        verify(receiveCategoryRepo, org.mockito.Mockito.never()).findByOrgIdOrderBySortOrderAscIdAsc(any());
    }

    // ------------------------------------------------------------------ fixtures

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> List<T> captured(org.springframework.data.repository.CrudRepository repo) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repo, atLeastOnce()).saveAll(captor.capture());
        return (List<T>) captor.getValue();
    }

    private static Organization org(Long id, String name) {
        Organization o = new Organization(name);
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }

    private static <T extends com.dams.common.entity.OrgMaster> T master(T m, Long id, String name, boolean active, int sort) {
        ReflectionTestUtils.setField(m, "id", id);
        m.setOrgId(TEMPLATE);
        m.setName(name);
        m.setActive(active);
        m.setSortOrder(sort);
        return m;
    }

    private static ReceiveBusinessStatusRole grant(Long statusId, Role role) {
        ReceiveBusinessStatusRole g = new ReceiveBusinessStatusRole();
        g.setOrgId(TEMPLATE);
        g.setStatusId(statusId);
        g.setRole(role);
        return g;
    }
}
