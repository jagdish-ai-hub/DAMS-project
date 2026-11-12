package com.dams.spareparts.controller;

import com.dams.common.time.OrgTime;
import com.dams.spareparts.dto.SparePartsDtos.AccessInfo;
import com.dams.spareparts.dto.SparePartsDtos.ActionResult;
import com.dams.spareparts.dto.SparePartsDtos.AddPartRequest;
import com.dams.spareparts.dto.SparePartsDtos.BillRequest;
import com.dams.spareparts.dto.SparePartsDtos.CorrectRequest;
import com.dams.spareparts.dto.SparePartsDtos.Dashboard;
import com.dams.spareparts.dto.SparePartsDtos.ImportConfirm;
import com.dams.spareparts.dto.SparePartsDtos.ImportPreview;
import com.dams.spareparts.dto.SparePartsDtos.ImportResult;
import com.dams.spareparts.dto.SparePartsDtos.MovementRow;
import com.dams.spareparts.dto.SparePartsDtos.PermissionGrid;
import com.dams.spareparts.dto.SparePartsDtos.PermissionUpdate;
import com.dams.spareparts.dto.SparePartsDtos.StockPage;
import com.dams.spareparts.dto.SparePartsDtos.TransferCreate;
import com.dams.spareparts.dto.SparePartsDtos.TransferReason;
import com.dams.spareparts.dto.SparePartsDtos.TransferRow;
import com.dams.spareparts.dto.SparePartsDtos.TransferSend;
import com.dams.spareparts.entity.ImportMode;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.TransferStatus;
import com.dams.spareparts.service.SparePartsAccess;
import com.dams.spareparts.service.SparePartsDashboardService;
import com.dams.spareparts.service.SparePartsImportService;
import com.dams.spareparts.service.SparePartsPermissionService;
import com.dams.spareparts.service.SparePartsStockService;
import com.dams.spareparts.service.SparePartsTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Spare Parts module API (docs/modules/spare-parts.md). Every call except GET /access returns 403 while the module is
 * switched off for the organization; the rules themselves (who may do what, at which branch) live in the services.
 */
@RestController
@RequestMapping("/api/v1/spare-parts")
@Tag(name = "Spare Parts", description = "Local spare stock per branch, billing against job cards, branch requests")
@SecurityRequirement(name = "bearerAuth")
public class SparePartsController {

    /** The three roles that may look at stock. The services re-check everything against the database. */
    private static final String VIEWERS = "hasAnyAuthority('OWNER','INVENTORY_MANAGER','STORE_KEEPER')";

    private final SparePartsAccess access;
    private final SparePartsStockService stockService;
    private final SparePartsImportService importService;
    private final SparePartsTransferService transferService;
    private final SparePartsPermissionService permissionService;
    private final SparePartsDashboardService dashboardService;

    public SparePartsController(SparePartsAccess access,
                                SparePartsStockService stockService,
                                SparePartsImportService importService,
                                SparePartsTransferService transferService,
                                SparePartsPermissionService permissionService,
                                SparePartsDashboardService dashboardService) {
        this.access = access;
        this.stockService = stockService;
        this.importService = importService;
        this.transferService = transferService;
        this.permissionService = permissionService;
        this.dashboardService = dashboardService;
    }

    // --- access ---

    @GetMapping("/access")
    @Operation(summary = "Is the module on for me, and what may I do? (any signed-in user; never 403)")
    public AccessInfo access() {
        return access.describe();
    }

    // --- stock ---

    @GetMapping("/stock")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Parts with the quantity in every branch (search by part ID or description)")
    public StockPage stock(@RequestParam(required = false) String q,
                           @RequestParam(required = false) Long branchId,
                           @RequestParam(defaultValue = "false") boolean inStockOnly) {
        return stockService.search(q, branchId, inStockOnly);
    }

    @GetMapping("/movements")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "The stock ledger, newest first")
    public List<MovementRow> movements(@RequestParam(required = false) Long branchId,
                                       @RequestParam(required = false) Long partId,
                                       @RequestParam(required = false) MovementType type,
                                       @RequestParam(required = false) String reference,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Integer limit) {
        return stockService.movements(branchId, partId, type, reference, q, limit);
    }

    @GetMapping("/export/stock")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Stock of every branch as an Excel-friendly CSV, in the owner's sheet layout")
    public ResponseEntity<byte[]> exportStock() {
        byte[] csv = stockService.exportCsv();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"spare-parts-stock-" + OrgTime.today() + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    // --- Excel upload (two steps: preview, then confirm) ---

    @PostMapping(value = "/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Read an Excel file and show what each row would do. Posts nothing.")
    public ImportPreview previewImport(@RequestParam("file") MultipartFile file,
                                       @RequestParam("mode") ImportMode mode) {
        return importService.preview(file, mode);
    }

    @PostMapping("/imports")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Post the valid rows of a previewed upload; invalid rows are returned, not posted")
    public ImportResult confirmImport(@Valid @RequestBody ImportConfirm request) {
        return importService.confirm(request);
    }

    // --- single-part actions ---

    @PostMapping("/parts")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Add a part by hand with an opening quantity at a branch")
    public ActionResult addPart(@Valid @RequestBody AddPartRequest request) {
        return stockService.addPart(request);
    }

    @PostMapping("/stock/correct")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Set a branch quantity to the counted number (reason required)")
    public ActionResult correct(@Valid @RequestBody CorrectRequest request) {
        return stockService.correct(request);
    }

    @PostMapping("/bills")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Bill a part to a (non-claim) job card; reduces the branch stock")
    public ActionResult bill(@Valid @RequestBody BillRequest request) {
        return stockService.bill(request);
    }

    // --- branch requests ---

    @GetMapping("/transfers")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Branch requests, newest first, with what the caller may do next")
    public List<TransferRow> transfers(@RequestParam(required = false) TransferStatus status,
                                       @RequestParam(required = false) Long branchId) {
        return transferService.list(status, branchId);
    }

    @PostMapping("/transfers")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Ask another branch for a part (changes no stock)")
    public ResponseEntity<TransferRow> createTransfer(@Valid @RequestBody TransferCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.create(request));
    }

    @PostMapping("/transfers/{id}/send")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Supplying branch marks the part Sent: its stock goes down, the part is in transit")
    public TransferRow send(@PathVariable Long id, @Valid @RequestBody(required = false) TransferSend body) {
        return transferService.send(id, body == null ? null : body.quantity());
    }

    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Requesting branch marks the part Received: its stock goes up")
    public TransferRow receive(@PathVariable Long id) {
        return transferService.receive(id);
    }

    @PostMapping("/transfers/{id}/decline")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Supplying branch declines a request (reason required)")
    public TransferRow decline(@PathVariable Long id, @Valid @RequestBody TransferReason body) {
        return transferService.decline(id, body.reason());
    }

    @PostMapping("/transfers/{id}/cancel")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Requesting branch withdraws a request that has not been sent (reason required)")
    public TransferRow cancel(@PathVariable Long id, @Valid @RequestBody TransferReason body) {
        return transferService.cancel(id, body.reason());
    }

    // --- Owner ---

    @GetMapping("/dashboard")
    @PreAuthorize(VIEWERS)
    @Operation(summary = "Organization-wide picture: value per branch, billed this month, requests, recent activity")
    public Dashboard dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "What Inventory Manager and Store Keeper may do (Owner)")
    public PermissionGrid permissions() {
        access.requireOwner();
        return permissionService.grid();
    }

    @PutMapping("/permissions")
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Change what Inventory Manager and Store Keeper may do (Owner)")
    public PermissionGrid updatePermissions(@Valid @RequestBody PermissionUpdate body) {
        SparePartsAccess.Caller owner = access.requireOwner();
        return permissionService.update(body.grid(), owner.userId());
    }
}
