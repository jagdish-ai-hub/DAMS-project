package com.dams.spareparts.dto;

import com.dams.spareparts.entity.ImportMode;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.TransferStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the Spare Parts API, kept in one place so the contract reads top to bottom. */
public final class SparePartsDtos {

    private SparePartsDtos() {
    }

    // --- access ---

    public record BranchInfo(Long id, String code, String name) {
    }

    /**
     * What the signed-in user may do with the module. {@code enabled=false} means the module is off for
     * the organization (or the caller is a Super Admin) and the shell must show nothing.
     */
    public record AccessInfo(boolean enabled, boolean canView, boolean canSeeDashboard, boolean owner,
                             String role, List<Long> actBranchIds, List<String> permissions,
                             List<BranchInfo> branches) {
    }

    // --- stock ---

    public record StockCell(Long branchId, int quantity, String bin) {
    }

    public record StockRow(Long partId, String partCode, String description, BigDecimal unitPrice,
                           List<StockCell> cells, int totalQuantity, int inTransit, BigDecimal totalValue) {
    }

    public record StockPage(List<StockRow> rows, boolean truncated) {
    }

    // --- Excel upload ---

    /** One row as typed in the file (or sent back by the preview). Numbers stay text until validated. */
    public record ImportRowIn(Integer rowNumber, String branchCode, String partCode, String description,
                              String bin, String quantity, String unitPrice, String billedQty, String jobCard) {
    }

    /** One row as validated: what it would do, or why it cannot be posted. */
    public record ImportRowOut(int rowNumber, String branchCode, String partCode, String description,
                               String bin, String quantity, String unitPrice, String billedQty, String jobCard,
                               Integer currentQty, Integer resultQty, boolean newPart, String error) {
    }

    public record ImportPreview(String fileName, ImportMode mode, List<ImportRowOut> rows,
                                int total, int ok, int errors, int newParts) {
    }

    public record ImportConfirm(@NotBlank @Size(max = 255) String fileName, @NotNull ImportMode mode,
                                @NotNull List<ImportRowIn> rows) {
    }

    public record ImportResult(Long importId, int posted, int rejected, int newParts,
                               List<ImportRowOut> rejectedRows) {
    }

    // --- single-part actions ---

    public record AddPartRequest(@NotNull Long branchId, @NotBlank @Size(max = 40) String partCode,
                                 @NotBlank @Size(max = 255) String description, @Size(max = 60) String bin,
                                 BigDecimal unitPrice, @NotNull @PositiveOrZero Integer quantity) {
    }

    public record CorrectRequest(@NotNull Long branchId, @NotNull Long partId,
                                 @NotNull @PositiveOrZero Integer newQuantity,
                                 @NotBlank @Size(max = 500) String reason) {
    }

    public record BillRequest(@NotNull Long branchId, @NotNull Long partId, @NotNull @Positive Integer quantity,
                              @NotBlank @Size(max = 40) String jobCardNo, @Size(max = 20) String vehicleNo,
                              @Size(max = 500) String note) {
    }

    /** The outcome of add / correct / bill: the part, the branch quantity now, and the ledger row (null when nothing moved). */
    public record ActionResult(Long partId, int quantityAfter, Long movementId) {
    }

    // --- ledger ---

    public record MovementRow(Long id, Instant createdAt, Long branchId, String branchCode, Long partId,
                              String partCode, String description, MovementType type, int qtyChange, int qtyAfter,
                              String reference, String vehicleNo, String note, String userName, String actingRole) {
    }

    // --- branch requests ---

    public record TransferCreate(@NotNull Long fromBranchId, @NotNull Long toBranchId, @NotNull Long partId,
                                 @NotNull @Positive Integer quantity, @Size(max = 40) String jobCardNo,
                                 @Size(max = 500) String note) {
    }

    /** Quantity is optional: leave it out to send everything that was asked for. */
    public record TransferSend(@Positive Integer quantity) {
    }

    public record TransferReason(@NotBlank @Size(max = 500) String reason) {
    }

    /** A request plus what the caller may do with it right now, so the screen never re-derives the rules. */
    public record TransferRow(Long id, String requestNo, TransferStatus status,
                              Long fromBranchId, String fromBranchCode, Long toBranchId, String toBranchCode,
                              Long partId, String partCode, String description, int qtyRequested, Integer qtySent,
                              String jobCardNo, String note, String declineReason,
                              String requestedByName, Instant requestedAt, String sentByName, Instant sentAt,
                              String receivedByName, Instant receivedAt,
                              boolean canSend, boolean canReceive, boolean canDecline, boolean canCancel) {
    }

    // --- permission grid (Owner) ---

    public record PermissionInfo(String key, String label) {
    }

    public record RoleGrid(String role, String label, Map<String, Boolean> values) {
    }

    public record PermissionGrid(List<PermissionInfo> permissions, List<RoleGrid> roles) {
    }

    /** role name -> permission name -> allowed. Only the cells sent are changed. */
    public record PermissionUpdate(@NotNull Map<String, Map<String, Boolean>> grid) {
    }

    // --- Owner dashboard ---

    public record BranchTotal(Long branchId, String code, String name, int partsHeld, long units,
                              BigDecimal value, long billsThisMonth, long unitsBilledThisMonth) {
    }

    public record Dashboard(List<BranchTotal> branches, int partCount, long totalUnits, BigDecimal totalValue,
                            Map<String, Long> requestCounts, List<MovementRow> recent) {
    }
}
