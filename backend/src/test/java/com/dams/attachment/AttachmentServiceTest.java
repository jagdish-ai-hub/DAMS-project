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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final long ORG = 1L;
    private static final long DOC_ID = 500L;

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
        lenient().when(storage.put(any(), any(), any())).thenReturn("org/1/receive_document/500/abc");
        lenient().when(attachmentRepo.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 77L);
            return a;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upload_storesAPdf_onAnOpenDocument() {
        when(receiveDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc(WorkflowStatus.SUBMITTED, false)));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", new byte[] {1, 2, 3});

        var response = service.upload(ParentType.RECEIVE_DOCUMENT, DOC_ID, file);

        assertThat(response.filename()).isEqualTo("receipt.pdf");
        verify(storage).put(any(), any(), any());
        verify(attachmentRepo).save(any(Attachment.class));
    }

    @Test
    void upload_rejectsNonPdfNonImage() {
        when(receiveDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc(WorkflowStatus.SUBMITTED, false)));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> service.upload(ParentType.RECEIVE_DOCUMENT, DOC_ID, file))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("PDF or image");
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void upload_rejectedWhenDocumentIsSettled() {
        when(receiveDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc(WorkflowStatus.APPROVED, true)));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.upload(ParentType.RECEIVE_DOCUMENT, DOC_ID, file))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("frozen");
    }

    @Test
    void upload_storesAPdf_onAnOpenExpenseDocument() {
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.SUBMITTED)));
        MockMultipartFile file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[] {1, 2});

        var response = service.upload(ParentType.EXPENSE_DOCUMENT, DOC_ID, file);

        assertThat(response.filename()).isEqualTo("bill.pdf");
        verify(attachmentRepo).save(any(Attachment.class));
    }

    @Test
    void upload_rejectedWhenExpenseDocumentIsClosed() {
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.CLOSED)));
        MockMultipartFile file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.upload(ParentType.EXPENSE_DOCUMENT, DOC_ID, file))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("frozen");
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void delete_rejectsAFrozenAttachment() {
        Attachment frozen = new Attachment();
        ReflectionTestUtils.setField(frozen, "id", 77L);
        frozen.setOrgId(ORG);
        frozen.setFilename("receipt.pdf");
        frozen.setObjectKey("k");
        frozen.setFrozen(true);
        when(attachmentRepo.findByIdAndOrgId(77L, ORG)).thenReturn(Optional.of(frozen));

        assertThatThrownBy(() -> service.delete(77L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("frozen");
        verify(storage, never()).delete(any());
    }

    private static ReceiveDocument doc(WorkflowStatus status, boolean settled) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        d.setOrgId(ORG);
        d.setBranchId(3L);
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
        d.setBranchId(3L);
        d.setReceiverId(9L);
        d.setExpenseCategoryId(2L);
        d.setBusinessStatusId(4L);
        d.setDocumentNo("OOR-JUL26-E-002");
        d.setWorkflowStatus(status);
        d.setCreatedBy(7L);
        return d;
    }
}
