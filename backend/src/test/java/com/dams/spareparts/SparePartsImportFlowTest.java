package com.dams.spareparts;

import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.ActingDetails;
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.spareparts.dto.SparePartsDtos.AddPartRequest;
import com.dams.spareparts.dto.SparePartsDtos.ImportConfirm;
import com.dams.spareparts.dto.SparePartsDtos.ImportPreview;
import com.dams.spareparts.dto.SparePartsDtos.ImportResult;
import com.dams.spareparts.dto.SparePartsDtos.ImportRowIn;
import com.dams.spareparts.dto.SparePartsDtos.ImportRowOut;
import com.dams.spareparts.entity.ImportMode;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.repository.InvJobCardLookupRepository;
import com.dams.spareparts.repository.InvRolePermissionRepository;
import com.dams.spareparts.service.SparePartsAccess;
import com.dams.spareparts.service.SparePartsImportService;
import com.dams.spareparts.service.SparePartsPermissionService;
import com.dams.spareparts.service.SparePartsStockService;
import com.dams.spareparts.service.StockLedgerService;
import com.dams.spareparts.util.XlsxFixtures;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Excel upload, run end to end on a real .xlsx laid out like the owner's "Spare parts.xlsx": title row, header row found
 * by name, formula cells, a job card stored as 4.01E9, a TOTAL line. Preview posts nothing; confirm posts through the ledger.
 */
class SparePartsImportFlowTest {

    private static final long ORG = FakeInventory.ORG;
    private static final long OOJ = 10;
    private static final long OOB = 11;
    private static final long OOR = 12;
    private static final long MANAGER = 21;   // Inventory Manager at all three branches
    private static final long KEEPER = 30;    // Store Keeper at OOR only

    private FakeInventory fake;
    private AppUserRepository userRepo;
    private UserBranchAccessRepository branchAccessRepo;
    private SparePartsImportService importService;
    private SparePartsStockService stockService;

    @BeforeEach
    void setUp() {
        fake = new FakeInventory();
        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        userRepo = mock(AppUserRepository.class);
        branchAccessRepo = mock(UserBranchAccessRepository.class);
        BranchRepository branchRepo = mock(BranchRepository.class);
        InvRolePermissionRepository permissionRepo = mock(InvRolePermissionRepository.class);
        when(permissionRepo.findByOrgIdAndRole(anyLong(), anyString())).thenReturn(List.of());

        Organization org = new Organization("Ooriba test");
        org.setSparePartsEnabled(true);
        when(orgRepo.findById(ORG)).thenReturn(Optional.of(org));
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(branch(OOB, "OOB"), branch(OOJ, "OOJ"), branch(OOR, "OOR")));
        when(branchRepo.findByIdAndOrgId(OOB, ORG)).thenReturn(Optional.of(branch(OOB, "OOB")));
        when(branchRepo.findByIdAndOrgId(OOJ, ORG)).thenReturn(Optional.of(branch(OOJ, "OOJ")));
        when(branchRepo.findByIdAndOrgId(OOR, ORG)).thenReturn(Optional.of(branch(OOR, "OOR")));

        SparePartsAccess access = new SparePartsAccess(orgRepo, userRepo, branchAccessRepo, branchRepo,
            new SparePartsPermissionService(permissionRepo, mock(AuditService.class)));
        StockLedgerService ledger = new StockLedgerService(fake.stockRepo, fake.movementRepo);
        importService = new SparePartsImportService(access, branchRepo, fake.partRepo, fake.stockRepo, fake.importRepo, ledger);
        stockService = new SparePartsStockService(access, branchRepo, userRepo, fake.partRepo, fake.stockRepo,
            fake.movementRepo, fake.transferRepo, mock(InvJobCardLookupRepository.class), ledger);
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewReadsTheOwnersSheet_row_by_row_andPostsNothing() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);

        ImportPreview preview = importService.preview(ownersFile(), ImportMode.ADD);

        assertThat(preview.total()).isEqualTo(5);                 // the TOTAL line is not a part
        assertThat(preview.ok()).isEqualTo(5);
        assertThat(preview.errors()).isZero();
        assertThat(preview.newParts()).isEqualTo(5);
        ImportRowOut washer = row(preview, "MF450155");
        assertThat(washer.rowNumber()).isEqualTo(11);             // Excel row number, to find it in the file
        assertThat(washer.branchCode()).isEqualTo("OOB");
        assertThat(washer.quantity()).isEqualTo("6");             // CLOSING QTY, not OPENING QTY (10)
        assertThat(washer.bin()).isEqualTo("DRAW-2");
        assertThat(washer.unitPrice()).isEqualTo("25");
        assertThat(washer.jobCard()).isEqualTo("4010615068");     // restored from 4.010615068E9
        assertThat(washer.resultQty()).isEqualTo(6);
        assertThat(fake.ledger).isEmpty();                        // a preview never posts
        assertThat(fake.stock).isEmpty();
    }

    @Test
    void confirmInAddMode_putsTheSheetOnTheBooks_throughTheLedger() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);

        ImportResult result = confirm(ImportMode.ADD);

        assertThat(result.posted()).isEqualTo(5);
        assertThat(result.rejected()).isZero();
        assertThat(result.newParts()).isEqualTo(5);
        InvPart washer = part("MF450155");
        assertThat(washer.getUnitPrice()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(fake.quantity(OOB, washer.getId())).isEqualTo(6);
        assertThat(fake.quantity(OOJ, part("IA309072").getId())).isZero();   // closing 0: the part is known, nothing to post
        assertThat(fake.ledger).hasSize(4);                                  // four parts with stock, one with none
        assertThat(fake.ledger).allSatisfy(m -> assertThat(m.getMovementType()).isEqualTo(MovementType.UPLOAD_ADD));
        assertThat(fake.ledger).anySatisfy(m -> assertThat(m.getNote()).contains("billed 4 on job card 4010615068"));
        assertThat(fake.imports).hasSize(1);
        assertThat(fake.imports.get(0).getRowsPosted()).isEqualTo(5);
        fake.assertLedgerAddsUp();
    }

    @Test
    void uploadingTheSameFileTwiceInAddMode_addsTwice_whichIsWhyTheScreenAsksEveryTime() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);

        confirm(ImportMode.ADD);
        confirm(ImportMode.ADD);

        assertThat(fake.quantity(OOB, part("MF450155").getId())).isEqualTo(12);
        fake.assertLedgerAddsUp();
    }

    @Test
    void setMode_makesTheQuantityTheFilesAndRecordsOnlyTheDifference() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);
        confirm(ImportMode.ADD);
        long washer = part("MF450155").getId();
        stockService.addPart(new AddPartRequest(OOB, "MF450155", "PLAIN WASHER", null, null, 14));   // now 20
        int ledgerBefore = fake.ledger.size();

        ImportResult result = confirm(ImportMode.SET);

        assertThat(result.posted()).isEqualTo(5);
        assertThat(fake.quantity(OOB, washer)).isEqualTo(6);                   // set to the file's 6
        assertThat(fake.ledger.size()).isEqualTo(ledgerBefore + 1);            // only the washer differed
        assertThat(fake.ledger.get(fake.ledger.size() - 1).getMovementType()).isEqualTo(MovementType.UPLOAD_SET);
        assertThat(fake.ledger.get(fake.ledger.size() - 1).getQtyChange()).isEqualTo(-14);
        fake.assertLedgerAddsUp();
    }

    @Test
    void setMode_neverTouchesAPartThatIsNotInTheFile() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);
        long other = stockService.addPart(new AddPartRequest(OOB, "ZZ999999", "Not in the file", null, null, 8)).partId();

        confirm(ImportMode.SET);

        assertThat(fake.quantity(OOB, other)).isEqualTo(8);
    }

    @Test
    void rowsWithProblems_areRefusedWithAReason_andTheGoodRowsStillPost() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);
        List<ImportRowIn> rows = List.of(
            new ImportRowIn(3, "OOB", "AA100001", "Good part", "BOX-1", "5", "10", "", ""),
            new ImportRowIn(4, "XYZ", "AA100002", "Unknown branch", "", "1", "", "", ""),
            new ImportRowIn(5, "OOB", "AA100003", "Half a part", "", "2.5", "", "", ""),
            new ImportRowIn(6, "OOB", "AA100004", "", "", "1", "", "", ""),           // new part without a description
            new ImportRowIn(7, "OOB", "aa 100001", "Same part again", "", "1", "", "", ""),   // duplicate of row 3
            new ImportRowIn(8, "", "AA100005", "No branch", "", "1", "", "", ""));

        ImportResult result = importService.confirm(new ImportConfirm("mixed.xlsx", ImportMode.ADD, rows));

        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(5);
        assertThat(result.rejectedRows()).extracting(ImportRowOut::error).satisfiesExactly(
            e -> assertThat(e).contains("'XYZ' is not a branch"),
            e -> assertThat(e).contains("not a whole number"),
            e -> assertThat(e).contains("Description is needed"),
            e -> assertThat(e).contains("Same branch and part as row 3"),
            e -> assertThat(e).contains("Branch is missing"));
        assertThat(fake.quantity(OOB, part("AA100001").getId())).isEqualTo(5);
        fake.assertLedgerAddsUp();
    }

    @Test
    void aStoreKeeper_cannotUpload_byDefault() {
        login(KEEPER, Role.STORE_KEEPER, OOR);

        assertThatThrownBy(() -> importService.preview(ownersFile(), ImportMode.ADD))
            .isInstanceOf(DamsException.class).hasMessageContaining("upload an excel file or add a part");
    }

    @Test
    void anInventoryManager_canChangeStockOnlyAtTheirOwnBranches() {
        login(MANAGER + 1, Role.INVENTORY_MANAGER, OOR);   // manager at OOR only

        ImportPreview preview = importService.preview(ownersFile(), ImportMode.ADD);

        assertThat(preview.ok()).isEqualTo(1);               // the one OOR row
        assertThat(preview.errors()).isEqualTo(4);
        assertThat(preview.rows()).filteredOn(r -> r.error() != null)
            .allSatisfy(r -> assertThat(r.error()).contains("not one of yours"));
    }

    @Test
    void aFileThatIsNotXlsx_isRefusedBeforeAnythingIsRead() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);
        MockMultipartFile csv = new MockMultipartFile("file", "parts.csv", "text/csv", "a,b".getBytes());

        assertThatThrownBy(() -> importService.preview(csv, ImportMode.ADD))
            .isInstanceOf(DamsException.class).hasMessageContaining("(.xlsx)");
    }

    @Test
    void aSheetWithoutAPartIdHeader_getsAMessageThatNamesTheHeadersItNeeds() {
        login(MANAGER, Role.INVENTORY_MANAGER, OOJ, OOB, OOR);
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx", "application/octet-stream", XlsxFixtures.workbookWithoutPartHeader());

        assertThatThrownBy(() -> importService.preview(file, ImportMode.ADD))
            .isInstanceOf(DamsException.class).hasMessageContaining("PART ID");
    }

    // --- fixtures ---

    private ImportResult confirm(ImportMode mode) {
        ImportPreview preview = importService.preview(ownersFile(), mode);
        List<ImportRowIn> rows = preview.rows().stream().map(r -> new ImportRowIn(r.rowNumber(), r.branchCode(), r.partCode(),
            r.description(), r.bin(), r.quantity(), r.unitPrice(), r.billedQty(), r.jobCard())).toList();
        return importService.confirm(new ImportConfirm("Spare parts.xlsx", mode, rows));
    }

    private static MockMultipartFile ownersFile() {
        return new MockMultipartFile("file", "Spare parts.xlsx", "application/octet-stream", XlsxFixtures.ownersWorkbook());
    }

    private static ImportRowOut row(ImportPreview preview, String partCode) {
        return preview.rows().stream().filter(r -> r.partCode().equals(partCode)).findFirst().orElseThrow();
    }

    private InvPart part(String code) {
        return fake.parts.values().stream().filter(p -> p.getPartCode().equals(code)).findFirst().orElseThrow();
    }

    private void login(long userId, Role role, long... branchIds) {
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
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority(role.name())));
        auth.setDetails(new ActingDetails(role, null));
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
}
