package com.dams.spareparts;

import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.ActingDetails;
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.spareparts.dto.SparePartsDtos.AccessInfo;
import com.dams.spareparts.dto.SparePartsDtos.ActionResult;
import com.dams.spareparts.dto.SparePartsDtos.AddPartRequest;
import com.dams.spareparts.dto.SparePartsDtos.BillRequest;
import com.dams.spareparts.dto.SparePartsDtos.CorrectRequest;
import com.dams.spareparts.dto.SparePartsDtos.StockPage;
import com.dams.spareparts.dto.SparePartsDtos.TransferCreate;
import com.dams.spareparts.dto.SparePartsDtos.TransferRow;
import com.dams.spareparts.entity.InvRolePermission;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.TransferStatus;
import com.dams.spareparts.repository.InvJobCardLookupRepository;
import com.dams.spareparts.repository.InvRolePermissionRepository;
import com.dams.spareparts.service.SparePartsAccess;
import com.dams.spareparts.service.SparePartsPermissionService;
import com.dams.spareparts.service.SparePartsStockService;
import com.dams.spareparts.service.SparePartsTransferService;
import com.dams.spareparts.service.StockLedgerService;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spare Parts end to end, through the REAL services (access gate, ledger, stock, requests, permission grid) with only the
 * database faked. The point of these tests is the rules: nobody acts outside their branch or their grid, a request moves
 * stock only when Sent and Received, and the ledger always adds up to the quantities.
 */
class SparePartsFlowTest {

    private static final long ORG = FakeInventory.ORG;
    private static final long OOJ = 10;
    private static final long OOB = 11;
    private static final long OOR = 12;

    private static final long OWNER = 1;
    private static final long MANAGER_ALL = 21;   // Inventory Manager at all three branches
    private static final long KEEPER_OOR = 30;
    private static final long KEEPER_OOJ = 31;

    private FakeInventory fake;
    private OrganizationRepository orgRepo;
    private AppUserRepository userRepo;
    private UserBranchAccessRepository branchAccessRepo;
    private BranchRepository branchRepo;
    private InvJobCardLookupRepository jobCardLookup;
    private final List<InvRolePermission> grid = new ArrayList<>();

    private Organization org;
    private SparePartsPermissionService permissionService;
    private SparePartsStockService stockService;
    private SparePartsTransferService transferService;

    @BeforeEach
    void setUp() {
        fake = new FakeInventory();
        orgRepo = mock(OrganizationRepository.class);
        userRepo = mock(AppUserRepository.class);
        branchAccessRepo = mock(UserBranchAccessRepository.class);
        branchRepo = mock(BranchRepository.class);
        jobCardLookup = mock(InvJobCardLookupRepository.class);
        InvRolePermissionRepository permissionRepo = mock(InvRolePermissionRepository.class);

        org = new Organization("Ooriba test");
        org.setSparePartsEnabled(true);
        when(orgRepo.findById(ORG)).thenReturn(Optional.of(org));
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(branch(OOB, "OOB"), branch(OOJ, "OOJ"), branch(OOR, "OOR")));
        for (long id : new long[]{OOJ, OOB, OOR}) {
            when(branchRepo.findByIdAndOrgId(id, ORG)).thenReturn(Optional.of(branch(id, id == OOJ ? "OOJ" : id == OOB ? "OOB" : "OOR")));
        }
        when(permissionRepo.findByOrgIdAndRole(anyLong(), anyString())).thenAnswer(inv ->
            grid.stream().filter(r -> r.getRole().equals(inv.getArgument(1))).toList());
        when(permissionRepo.save(any(InvRolePermission.class))).thenAnswer(inv -> {
            InvRolePermission row = inv.getArgument(0);
            if (!grid.contains(row)) {
                grid.add(row);
            }
            return row;
        });

        permissionService = new SparePartsPermissionService(permissionRepo, mock(AuditService.class));
        SparePartsAccess access = new SparePartsAccess(orgRepo, userRepo, branchAccessRepo, branchRepo, permissionService);
        StockLedgerService ledger = new StockLedgerService(fake.stockRepo, fake.movementRepo);
        stockService = new SparePartsStockService(access, branchRepo, userRepo, fake.partRepo, fake.stockRepo,
            fake.movementRepo, fake.transferRepo, jobCardLookup, ledger);
        transferService = new SparePartsTransferService(access, branchRepo, userRepo, fake.partRepo, fake.stockRepo,
            fake.transferRepo, ledger);
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // --- the happy path: ask, send, receive ---

    @Test
    void aRequest_movesStockOnlyWhenSentAndWhenReceived_andTheLedgerAlwaysAddsUp() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);

        loginKeeper(KEEPER_OOR, OOR);
        TransferRow asked = transferService.create(new TransferCreate(OOJ, OOR, part, 1, "4010614834", "urgent"));
        assertThat(asked.status()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(fake.quantity(OOJ, part)).isEqualTo(3);      // asking changes nothing

        loginKeeper(KEEPER_OOJ, OOJ);
        TransferRow sent = transferService.send(asked.id(), null);
        assertThat(sent.status()).isEqualTo(TransferStatus.SENT);
        assertThat(fake.quantity(OOJ, part)).isEqualTo(2);      // the part left OOJ at Sent
        assertThat(fake.quantity(OOR, part)).isZero();          // ...and has not reached OOR yet
        assertThat(stockService.search("IA309072", null, false).rows().get(0).inTransit()).isEqualTo(1);

        loginKeeper(KEEPER_OOR, OOR);
        TransferRow received = transferService.receive(asked.id());
        assertThat(received.status()).isEqualTo(TransferStatus.RECEIVED);
        assertThat(fake.quantity(OOR, part)).isEqualTo(1);
        assertThat(stockService.search("IA309072", null, false).rows().get(0).inTransit()).isZero();

        fake.assertLedgerAddsUp();
        assertThat(fake.ledger).extracting(m -> m.getMovementType())
            .containsExactly(MovementType.MANUAL_ADD, MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN);
        assertThat(fake.ledger.get(1).getReference()).isEqualTo(sent.requestNo());
    }

    @Test
    void sendingTheSameRequestTwice_isRefused_andStockMovesOnce() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        loginKeeper(KEEPER_OOR, OOR);
        TransferRow asked = transferService.create(new TransferCreate(OOJ, OOR, part, 1, null, null));
        loginKeeper(KEEPER_OOJ, OOJ);
        transferService.send(asked.id(), null);

        assertThatThrownBy(() -> transferService.send(asked.id(), null))
            .isInstanceOf(DamsException.class).hasMessageContaining("SENT");
        assertThat(fake.quantity(OOJ, part)).isEqualTo(2);
        fake.assertLedgerAddsUp();
    }

    @Test
    void aRequest_cannotAskForMoreThanTheSupplierHolds() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 1);
        loginKeeper(KEEPER_OOR, OOR);

        assertThatThrownBy(() -> transferService.create(new TransferCreate(OOJ, OOR, part, 2, null, null)))
            .isInstanceOf(DamsException.class).hasMessageContaining("OOJ holds 1");
    }

    @Test
    void theSupplier_canSendLessThanAskedFor_butNeverMore() {
        long part = managerAddsPart("MF450155", "Plain washer", OOJ, 10);
        loginKeeper(KEEPER_OOR, OOR);
        TransferRow asked = transferService.create(new TransferCreate(OOJ, OOR, part, 4, null, null));
        loginKeeper(KEEPER_OOJ, OOJ);

        assertThatThrownBy(() -> transferService.send(asked.id(), 5)).isInstanceOf(DamsException.class);
        TransferRow sent = transferService.send(asked.id(), 3);

        assertThat(sent.qtySent()).isEqualTo(3);
        assertThat(fake.quantity(OOJ, part)).isEqualTo(7);
    }

    @Test
    void aDeclinedRequest_changesNoStock_andNeedsAReason() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        loginKeeper(KEEPER_OOR, OOR);
        TransferRow asked = transferService.create(new TransferCreate(OOJ, OOR, part, 1, null, null));
        loginKeeper(KEEPER_OOJ, OOJ);

        TransferRow declined = transferService.decline(asked.id(), "Reserved for a customer");

        assertThat(declined.status()).isEqualTo(TransferStatus.DECLINED);
        assertThat(declined.declineReason()).isEqualTo("Reserved for a customer");
        assertThat(fake.quantity(OOJ, part)).isEqualTo(3);
    }

    @Test
    void onlyTheSupplierCanSend_andOnlyTheRequesterCanReceive() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        loginKeeper(KEEPER_OOR, OOR);
        TransferRow asked = transferService.create(new TransferCreate(OOJ, OOR, part, 1, null, null));

        // the requester tries to send it to themselves
        assertForbidden(() -> transferService.send(asked.id(), null), "own branch");
        loginKeeper(KEEPER_OOJ, OOJ);
        transferService.send(asked.id(), null);
        // the supplier tries to mark it received on the requester's behalf
        assertForbidden(() -> transferService.receive(asked.id()), "own branch");
    }

    // --- billing a part to a job card ---

    @Test
    void billingAPart_reducesTheBranchStock_andRecordsTheJobCard() {
        long part = managerAddsPart("MF450155", "Plain washer", OOB, 10);
        loginKeeper(KEEPER_OOJ, OOB);

        ActionResult result = stockService.bill(new BillRequest(OOB, part, 4, "4010615068", "od 02 ab 1234", null));

        assertThat(result.quantityAfter()).isEqualTo(6);
        assertThat(fake.ledger.get(1).getReference()).isEqualTo("4010615068");
        assertThat(fake.ledger.get(1).getVehicleNo()).isEqualTo("OD02AB1234");
        assertThat(fake.ledger.get(1).getQtyChange()).isEqualTo(-4);
        fake.assertLedgerAddsUp();
    }

    @Test
    void billingAPart_toAClaimJobCardInDams_isRefused_andStockIsUntouched() {
        long part = managerAddsPart("MF450155", "Plain washer", OOB, 10);
        when(jobCardLookup.countClaimJobCards(ORG, "4010615068")).thenReturn(1L);
        loginKeeper(KEEPER_OOJ, OOB);

        assertThatThrownBy(() -> stockService.bill(new BillRequest(OOB, part, 4, "4010615068", null, null)))
            .isInstanceOf(DamsException.class).hasMessageContaining("claim job card");
        assertThat(fake.quantity(OOB, part)).isEqualTo(10);
    }

    @Test
    void billingAPart_acceptsAJobCardNumberDamsDoesNotKnow_asTyped() {
        long part = managerAddsPart("MF450155", "Plain washer", OOB, 10);
        loginKeeper(KEEPER_OOJ, OOB);

        // Excel shows 4010615068 as 4.010615068E9; the typed number is cleaned to plain digits
        ActionResult result = stockService.bill(new BillRequest(OOB, part, 1, "4.010615068E9", null, null));

        assertThat(result.quantityAfter()).isEqualTo(9);
        assertThat(fake.ledger.get(1).getReference()).isEqualTo("4010615068");
    }

    @Test
    void billingMoreThanTheBranchHolds_isRefusedWithTheNumbers() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOB, 1);
        loginKeeper(KEEPER_OOJ, OOB);

        assertThatThrownBy(() -> stockService.bill(new BillRequest(OOB, part, 2, "4010614834", null, null)))
            .isInstanceOf(DamsException.class).hasMessageContaining("OOB holds 1").hasMessageContaining("2 cannot be billed");
        assertThat(fake.quantity(OOB, part)).isEqualTo(1);
    }

    // --- who may do what ---

    @Test
    void aStoreKeeper_canSeeEveryBranch_butActOnlyAtTheirOwn() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        loginKeeper(KEEPER_OOR, OOR);

        StockPage page = stockService.search("IA309072", null, false);
        assertThat(page.rows()).hasSize(1);                                    // sees OOJ's stock
        assertForbidden(() -> stockService.bill(new BillRequest(OOJ, part, 1, "4010614834", null, null)), "your own branch");
        assertThat(fake.quantity(OOJ, part)).isEqualTo(3);
    }

    @Test
    void theOwner_seesEverything_butIsViewOnly() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        loginOwner();

        assertThat(stockService.search("", null, false).rows()).hasSize(1);
        assertForbidden(() -> stockService.bill(new BillRequest(OOJ, part, 1, "4010614834", null, null)), "view-only");
        assertForbidden(() -> stockService.addPart(new AddPartRequest(OOJ, "X1", "Thing", null, null, 1)), "view-only");
    }

    @Test
    void theOwner_actingAsInventoryManagerAtOneBranch_canActThereAndNowhereElse() {
        long atOOB = managerAddsPart("MF450155", "Plain washer", OOB, 10);
        long atOOJ = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        loginActing(OWNER, Role.OWNER, Role.INVENTORY_MANAGER, OOB);

        stockService.bill(new BillRequest(OOB, atOOB, 1, "4010615068", null, null));

        assertThat(fake.quantity(OOB, atOOB)).isEqualTo(9);
        assertForbidden(() -> stockService.bill(new BillRequest(OOJ, atOOJ, 1, "4010614834", null, null)), "your own branch");
        assertThat(fake.ledger.get(fake.ledger.size() - 1).getActingRole()).isEqualTo("INVENTORY_MANAGER");
        fake.assertLedgerAddsUp();
    }

    @Test
    void aStoreKeeper_cannotCorrectAQuantity_untilTheOwnerTicksIt() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOR, 3);
        loginKeeper(KEEPER_OOR, OOR);
        assertForbidden(() -> stockService.correct(new CorrectRequest(OOR, part, 2, "Counted 2")), "Masters");

        // the Owner ticks "Correct a quantity" for Store Keeper
        loginOwner();
        permissionService.update(Map.of("STORE_KEEPER", Map.of("CORRECT_QTY", true)), OWNER);

        loginKeeper(KEEPER_OOR, OOR);
        ActionResult corrected = stockService.correct(new CorrectRequest(OOR, part, 2, "Counted 2"));
        assertThat(corrected.quantityAfter()).isEqualTo(2);
        assertThat(fake.ledger.get(fake.ledger.size() - 1).getNote()).isEqualTo("Counted 2");
        fake.assertLedgerAddsUp();
    }

    @Test
    void anUntickedPermission_blocksTheActionOnTheServer_notJustTheButton() {
        long part = managerAddsPart("MF450155", "Plain washer", OOB, 10);
        loginOwner();
        permissionService.update(Map.of("STORE_KEEPER", Map.of("BILL_PART", false)), OWNER);

        loginKeeper(KEEPER_OOJ, OOB);
        assertForbidden(() -> stockService.bill(new BillRequest(OOB, part, 1, "4010615068", null, null)), "Masters");
        assertThat(fake.quantity(OOB, part)).isEqualTo(10);
    }

    @Test
    void theGrid_rejectsAnUnknownRoleOrPermission_insteadOfIgnoringIt() {
        loginOwner();

        assertThatThrownBy(() -> permissionService.update(Map.of("CASHIER", Map.of("BILL_PART", true)), OWNER))
            .isInstanceOf(DamsException.class).hasMessageContaining("not a Spare Parts role");
        assertThatThrownBy(() -> permissionService.update(Map.of("STORE_KEEPER", Map.of("BILL_PARTT", true)), OWNER))
            .isInstanceOf(DamsException.class).hasMessageContaining("not a Spare Parts permission");
    }

    @Test
    void aStoreKeeper_cannotOpenTheOwnerOnlyEndpoints() {
        loginKeeper(KEEPER_OOR, OOR);
        // requireOwner is what GET/PUT /permissions call first
        SparePartsAccess access = new SparePartsAccess(orgRepo, userRepo, branchAccessRepo, branchRepo, permissionService);

        assertThatThrownBy(access::requireOwner).isInstanceOf(DamsException.class).hasMessageContaining("Only the Owner");
    }

    // --- the switch ---

    @Test
    void whenTheModuleIsOff_everyActionIsRefused_evenForAValidRole() {
        long part = managerAddsPart("IA309072", "Fender Assy RH", OOJ, 3);
        org.setSparePartsEnabled(false);
        loginKeeper(KEEPER_OOJ, OOJ);

        assertThatThrownBy(() -> stockService.search("", null, false))
            .isInstanceOf(DamsException.class).hasMessageContaining("not turned on");
        assertThatThrownBy(() -> stockService.bill(new BillRequest(OOJ, part, 1, "4010614834", null, null)))
            .isInstanceOf(DamsException.class).hasMessageContaining("not turned on");
    }

    @Test
    void access_saysDisabled_forSuperAdminAndForAnOrgWithTheModuleOff_withoutThrowing() {
        SparePartsAccess access = new SparePartsAccess(orgRepo, userRepo, branchAccessRepo, branchRepo, permissionService);

        TenantContext.clear();                                    // Super Admin: no organization
        assertThat(access.describe().enabled()).isFalse();

        TenantContext.setOrgId(ORG);
        org.setSparePartsEnabled(false);
        loginKeeper(KEEPER_OOR, OOR);
        AccessInfo off = access.describe();
        assertThat(off.enabled()).isFalse();
        assertThat(off.branches()).isEmpty();
    }

    @Test
    void access_forAStoreKeeper_listsTheirBranchesAndTheDefaultPermissions() {
        SparePartsAccess access = new SparePartsAccess(orgRepo, userRepo, branchAccessRepo, branchRepo, permissionService);
        loginKeeper(KEEPER_OOR, OOR);

        AccessInfo info = access.describe();

        assertThat(info.enabled()).isTrue();
        assertThat(info.canView()).isTrue();
        assertThat(info.canSeeDashboard()).isFalse();
        assertThat(info.actBranchIds()).containsExactly(OOR);
        assertThat(info.permissions()).containsExactlyInAnyOrder("BILL_PART", "RAISE_REQUEST", "SEND_RECEIVE");
        assertThat(info.branches()).hasSize(3);
    }

    @Test
    void aCashier_hasNoAccessToSpareParts_evenWithTheModuleOn() {
        userAs(40, Role.CASHIER);
        authenticate(40, Role.CASHIER, Role.CASHIER, null);

        assertThatThrownBy(() -> stockService.search("", null, false))
            .isInstanceOf(DamsException.class).hasMessageContaining("cannot use Spare Parts");
        assertThat(new SparePartsAccess(orgRepo, userRepo, branchAccessRepo, branchRepo, permissionService).describe().canView()).isFalse();
    }

    // --- fixtures ---

    /** An Inventory Manager (all branches) adds the part with an opening quantity: the usual way stock appears in these tests. */
    private long managerAddsPart(String code, String description, long branchId, int quantity) {
        userAs(MANAGER_ALL, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);
        authenticate(MANAGER_ALL, Role.INVENTORY_MANAGER, Role.INVENTORY_MANAGER, null);
        ActionResult added = stockService.addPart(new AddPartRequest(branchId, code, description, "BOX-1", BigDecimal.valueOf(100), quantity));
        return added.partId();
    }

    private void loginKeeper(long userId, long branchId) {
        userAs(userId, Role.STORE_KEEPER, branchId);
        authenticate(userId, Role.STORE_KEEPER, Role.STORE_KEEPER, null);
    }

    private void loginOwner() {
        userAs(OWNER, Role.OWNER);
        authenticate(OWNER, Role.OWNER, Role.OWNER, null);
    }

    private void loginActing(long userId, Role primary, Role acting, long branchId) {
        userAs(userId, primary);
        authenticate(userId, acting, primary, branchId);
    }

    private void userAs(long userId, Role role, long... branchIds) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", userId);
        u.setRole(role);
        u.setName("User " + userId);
        u.setActive(true);
        when(userRepo.findByIdAndOrganization_Id(userId, ORG)).thenReturn(Optional.of(u));
        List<UserBranchAccess> rows = new ArrayList<>();
        for (long b : branchIds) {
            UserBranchAccess a = new UserBranchAccess();
            ReflectionTestUtils.setField(a, "branchId", b);
            rows.add(a);
        }
        when(branchAccessRepo.findByUserId(userId)).thenReturn(rows);
    }

    private static void authenticate(long userId, Role acting, Role primary, Long actingBranch) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority(acting.name())));
        auth.setDetails(new ActingDetails(primary, actingBranch));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setName(code + " branch");
        b.setActive(true);
        return b;
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String messagePart) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DamsException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getMessage()).contains(messagePart);
        });
    }
}
