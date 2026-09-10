package com.dams.attachment;

import com.dams.attachment.entity.Attachment;
import com.dams.attachment.entity.ParentType;
import com.dams.attachment.repository.AttachmentRepository;
import com.dams.attachment.service.AttachmentService;
import com.dams.attachment.storage.StorageService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Attachment visibility and freeze rules: org membership is not enough — every
 * read, upload and delete honours branch access, and approved/closed documents
 * are frozen at both the document and the row level.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentSecurityTest {

    private static final long ORG = 1L;
    private static final long DOC_ID = 500L;
    private static final long HOME_BRANCH = 3L;
    private static final long OTHER_BRANCH = 9L;

    @Mock private AttachmentRepository attachmentRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private StorageService storage;
    @Mock private BranchScope branchScope;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentRepo, receiveDocumentRepo, settlementLineRepo,
            expenseDocumentRepo, expenseLineRepo, storage, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upload_refusesAnotherBranchDocument() {
        when(branchScope.canSeeBranch(OTHER_BRANCH)).thenReturn(false);
        when(receiveDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED, false, OTHER_BRANCH)));
        MockMultipartFile file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.upload(ParentType.RECEIVE_DOCUMENT, DOC_ID, file, null))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("outside your access");
    }

    @Test
    void upload_refusesSettledReceipt() {
        when(branchScope.canSeeBranch(HOME_BRANCH)).thenReturn(true);
        when(receiveDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(receiveDoc(WorkflowStatus.APPROVED, true, HOME_BRANCH)));
        MockMultipartFile file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.upload(ParentType.RECEIVE_DOCUMENT, DOC_ID, file, null))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("frozen");
    }

    @Test
    void upload_refusesApprovedButUnclosedExpense() {
        when(branchScope.canSeeBranch(HOME_BRANCH)).thenReturn(true);
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.APPROVED)));
        MockMultipartFile file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.upload(ParentType.EXPENSE_DOCUMENT, DOC_ID, file, null))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("frozen");
    }

    @Test
    void delete_refusesApprovedExpense_evenWhenRowIsNotFrozen() {
        // Row flags are only written on close/settle/approve transitions; the document
        // predicate must block deletes on its own for rows stored before that.
        when(branchScope.canSeeBranch(HOME_BRANCH)).thenReturn(true);
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.APPROVED)));
        when(attachmentRepo.findByIdAndOrgId(77L, ORG))
            .thenReturn(Optional.of(expenseAttachment(false)));

        assertThatThrownBy(() -> service.delete(77L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("frozen");
    }

    @Test
    void signedUrl_refusesAnotherBranchDocument() {
        when(branchScope.canSeeBranch(OTHER_BRANCH)).thenReturn(false);
        when(attachmentRepo.findByIdAndOrgId(77L, ORG)).thenReturn(Optional.of(attachment(false)));
        when(receiveDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED, false, OTHER_BRANCH)));

        assertThatThrownBy(() -> service.signedUrl(77L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("outside your access");
    }

    private static ReceiveDocument receiveDoc(WorkflowStatus status, boolean settled, long branchId) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        d.setOrgId(ORG);
        d.setBranchId(branchId);
        d.setJobCardId(50L);
        d.setDocumentNo("OOR-JUL26-R-011");
        d.setWorkflowStatus(status);
        d.setSettled(settled);
        d.setCreatedBy(7L);
        return d;
    }

    private static ExpenseDocument expenseDoc(ExpenseWorkflowStatus status) {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        d.setOrgId(ORG);
        d.setBranchId(HOME_BRANCH);
        d.setReceiverId(9L);
        d.setExpenseCategoryId(2L);
        d.setBusinessStatusId(4L);
        d.setDocumentNo("OOR-JUL26-E-002");
        d.setWorkflowStatus(status);
        d.setCreatedBy(7L);
        return d;
    }

    private static Attachment attachment(boolean frozen) {
        Attachment a = new Attachment();
        ReflectionTestUtils.setField(a, "id", 77L);
        a.setOrgId(ORG);
        a.setParentType(ParentType.RECEIVE_DOCUMENT);
        a.setParentId(DOC_ID);
        a.setFilename("bill.pdf");
        a.setObjectKey("k");
        a.setFrozen(frozen);
        return a;
    }

    private static Attachment expenseAttachment(boolean frozen) {
        Attachment a = attachment(frozen);
        a.setParentType(ParentType.EXPENSE_DOCUMENT);
        return a;
    }
}
