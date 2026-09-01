package com.dams.attachment.service;

import com.dams.attachment.dto.AttachmentResponse;
import com.dams.attachment.dto.SignedUrlResponse;
import com.dams.attachment.entity.Attachment;
import com.dams.attachment.entity.ParentType;
import com.dams.attachment.repository.AttachmentRepository;
import com.dams.attachment.storage.StorageService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Uploads, lists, signs and deletes attachments. The bytes live in {@link StorageService};
 * this class owns the {@code attachment} rows and the rules around them:
 *
 *   - only PDF / image, max {@value #MAX_BYTES} bytes;
 *   - no uploads once the owning document is "done" — a receive document that is settled or
 *     rejected, or an expense document that is closed or rejected;
 *   - frozen attachments (set when a document is settled / approved / closed) cannot be deleted.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXACT = Set.of("application/pdf");

    private final AttachmentRepository attachmentRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final StorageService storage;
    private final BranchScope branchScope;

    public AttachmentService(AttachmentRepository attachmentRepo,
                             ReceiveDocumentRepository receiveDocumentRepo,
                             SettlementLineRepository settlementLineRepo,
                             ExpenseDocumentRepository expenseDocumentRepo,
                             ExpenseLineRepository expenseLineRepo,
                             StorageService storage,
                             BranchScope branchScope) {
        this.attachmentRepo = attachmentRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.storage = storage;
        this.branchScope = branchScope;
    }

    @Transactional
    public AttachmentResponse upload(ParentType parentType, Long parentId, MultipartFile file) {
        Long orgId = TenantContext.requireOrgId();
        OwningDoc owner = resolveOwner(orgId, parentType, parentId);

        if (owner.frozen()) {
            throw DamsException.conflict(
                "Document " + owner.label() + " is closed — its receipts are frozen and cannot be changed");
        }
        validate(file);

        byte[] content = readBytes(file);
        String keyHint = "org/" + orgId + "/" + parentType.name().toLowerCase() + "/" + parentId;
        String objectKey = storage.put(keyHint, file.getContentType(), content);

        Attachment a = new Attachment();
        a.setOrgId(orgId);
        a.setParentType(parentType);
        a.setParentId(parentId);
        a.setObjectKey(objectKey);
        a.setFilename(safeFilename(file.getOriginalFilename()));
        a.setContentType(file.getContentType());
        a.setSizeBytes(content.length);
        a.setUploadedBy(branchScope.currentUserId());
        a = attachmentRepo.save(a);

        log.info("Attachment uploaded: orgId={} {} #{} attachmentId={} bytes={}",
            orgId, parentType, parentId, a.getId(), content.length);
        return AttachmentResponse.of(a);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(ParentType parentType, Long parentId) {
        Long orgId = TenantContext.requireOrgId();
        resolveOwner(orgId, parentType, parentId); // authorises + 404s
        return attachmentRepo.findByOrgIdAndParentTypeAndParentIdOrderByUploadedAtAsc(orgId, parentType, parentId)
            .stream().map(AttachmentResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public SignedUrlResponse signedUrl(Long attachmentId) {
        Long orgId = TenantContext.requireOrgId();
        Attachment a = attachmentRepo.findByIdAndOrgId(attachmentId, orgId)
            .orElseThrow(() -> DamsException.notFound("Attachment", attachmentId));
        return new SignedUrlResponse(
            storage.signedUrl(a.getObjectKey(), a.getFilename(), a.getContentType()),
            a.getFilename(), a.getContentType());
    }

    @Transactional
    public void delete(Long attachmentId) {
        Long orgId = TenantContext.requireOrgId();
        Attachment a = attachmentRepo.findByIdAndOrgId(attachmentId, orgId)
            .orElseThrow(() -> DamsException.notFound("Attachment", attachmentId));
        if (a.isFrozen()) {
            throw DamsException.conflict("Attachment '" + a.getFilename()
                + "' is frozen (its document is approved or closed) and cannot be deleted");
        }
        storage.delete(a.getObjectKey());
        attachmentRepo.delete(a);
        log.info("Attachment deleted: orgId={} attachmentId={}", orgId, attachmentId);
    }

    /**
     * Freeze every attachment on a receive document and its lines. Called when the document
     * settles (Stage 4) and, later, on approve / claim-close.
     */
    @Transactional
    public void freezeReceiveDocument(Long orgId, Long receiveDocumentId, List<Long> settlementLineIds) {
        freeze(orgId, ParentType.RECEIVE_DOCUMENT, List.of(receiveDocumentId));
        if (!settlementLineIds.isEmpty()) {
            freeze(orgId, ParentType.SETTLEMENT_LINE, settlementLineIds);
        }
    }

    /** Freeze every attachment on an expense document and its lines — on close / approve (Stage 7). */
    @Transactional
    public void freezeExpenseDocument(Long orgId, Long expenseDocumentId, List<Long> expenseLineIds) {
        freeze(orgId, ParentType.EXPENSE_DOCUMENT, List.of(expenseDocumentId));
        if (!expenseLineIds.isEmpty()) {
            freeze(orgId, ParentType.EXPENSE_LINE, expenseLineIds);
        }
    }

    private void freeze(Long orgId, ParentType type, List<Long> parentIds) {
        List<Attachment> rows = attachmentRepo.findByOrgIdAndParentTypeAndParentIdIn(orgId, type, parentIds);
        for (Attachment a : rows) {
            a.setFrozen(true);
        }
        attachmentRepo.saveAll(rows);
    }

    // --- helpers ---

    /** The document that owns a parent, reduced to what the attachment rules need. */
    private record OwningDoc(boolean frozen, String label) {}

    private OwningDoc resolveOwner(Long orgId, ParentType parentType, Long parentId) {
        return switch (parentType) {
            case RECEIVE_DOCUMENT -> receiveOwner(receiveDocumentRepo.findByIdAndOrgId(parentId, orgId)
                .orElseThrow(() -> DamsException.notFound("Receive document", parentId)));
            case SETTLEMENT_LINE -> {
                SettlementLine line = settlementLineRepo.findByIdAndOrgId(parentId, orgId)
                    .orElseThrow(() -> DamsException.notFound("Settlement line", parentId));
                yield receiveOwner(receiveDocumentRepo.findByIdAndOrgId(line.getReceiveDocumentId(), orgId)
                    .orElseThrow(() -> DamsException.notFound("Receive document", line.getReceiveDocumentId())));
            }
            case EXPENSE_DOCUMENT -> expenseOwner(expenseDocumentRepo.findByIdAndOrgId(parentId, orgId)
                .orElseThrow(() -> DamsException.notFound("Expense document", parentId)));
            case EXPENSE_LINE -> {
                ExpenseLine line = expenseLineRepo.findByIdAndOrgId(parentId, orgId)
                    .orElseThrow(() -> DamsException.notFound("Expense line", parentId));
                yield expenseOwner(expenseDocumentRepo.findByIdAndOrgId(line.getExpenseDocumentId(), orgId)
                    .orElseThrow(() -> DamsException.notFound("Expense document", line.getExpenseDocumentId())));
            }
        };
    }

    private static OwningDoc receiveOwner(ReceiveDocument doc) {
        boolean frozen = doc.isSettled() || doc.getWorkflowStatus() == WorkflowStatus.REJECTED;
        return new OwningDoc(frozen, doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId());
    }

    private static OwningDoc expenseOwner(ExpenseDocument doc) {
        boolean frozen = doc.getWorkflowStatus() == ExpenseWorkflowStatus.CLOSED
            || doc.getWorkflowStatus() == ExpenseWorkflowStatus.REJECTED;
        return new OwningDoc(frozen, doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw DamsException.badRequest("No file was uploaded");
        }
        if (file.getSize() > MAX_BYTES) {
            throw DamsException.badRequest("Attachment is larger than the 10 MB limit");
        }
        String type = file.getContentType();
        boolean ok = type != null && (ALLOWED_EXACT.contains(type) || type.startsWith("image/"));
        if (!ok) {
            throw DamsException.badRequest("Only PDF or image files can be attached (got " + type + ")");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw DamsException.badRequest("Could not read the uploaded file: " + e.getMessage());
        }
    }

    private static String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "attachment";
        }
        String base = original.substring(original.replace('\\', '/').lastIndexOf('/') + 1);
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
