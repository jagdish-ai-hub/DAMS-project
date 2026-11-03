package com.dams.export;

import com.dams.branch.repository.BranchRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.export.service.ExportService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.repository.*;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.review.dto.ReviewQueueItem;
import com.dams.review.service.ReviewService;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** The Accountant's "Pending & closed" window export (rev 67) — rows match the screen. */
@ExtendWith(MockitoExtension.class)
class ExportServiceReviewListTest {

    private static final Long ORG = 1L;
    private static final Long BRANCH = 10L;
    private static final Long OTHER_BRANCH = 99L;

    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private ReceiverRepository receiverRepo;
    @Mock private BankRepository bankRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;
    @Mock private ExpenseCategoryRepository categoryRepo;
    @Mock private ExpenseSubCategoryRepository subCategoryRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private BranchScope branchScope;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private ReviewService reviewService;
    @Mock private ClaimCloseRepository claimCloseRepo;

    private ExportService service;

    /** What the review queue shows as each receipt's amount (Σ lines, or the invoice amount). */
    private final Map<Long, BigDecimal> screenAmount = Map.of(
        1L, new BigDecimal("20000"), 2L, new BigDecimal("8000"), 3L, new BigDecimal("1"), 4L, new BigDecimal("1"));

    @BeforeEach
    void setUp() {
        service = new ExportService(settlementLineRepo, expenseLineRepo, branchRepo, customerRepo, receiverRepo,
            bankRepo, settlementModeRepo, expenseModeRepo, categoryRepo, subCategoryRepo, vehicleRepo, branchScope,
            receiveDocumentRepo, expenseDocumentRepo, cashDocumentRepo, jobCardRepo, reviewService, claimCloseRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.canSeeBranch(anyLong())).thenAnswer(i -> BRANCH.equals(i.getArgument(0)));
        lenient().when(reviewService.toReceiptItems(eq(ORG), any())).thenAnswer(i -> {
            List<ReceiveDocument> docs = i.getArgument(1);
            return docs.stream().map(d -> new ReviewQueueItem("receipt", d.getId(), d.getDocumentNo(), d.getBranchId(),
                "PUN", "Customer " + d.getId(), "Service", screenAmount.get(d.getId()), false, false,
                Instant.parse("2026-10-03T20:00:00Z"), d.getWorkflowStatus().name(), false, true, false, false, null)).toList();
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void receiptList_followsScreenOrder_oneRowPerLine_amountOncePerTransaction_andATotalRow() {
        ReceiveDocument paid = receipt(1L, "RCV-0012", WorkflowStatus.SUBMITTED, BRANCH, 100L);
        ReceiveDocument noLines = receipt(2L, "RCV-0015", WorkflowStatus.VERIFIED, BRANCH, 200L);
        when(receiveDocumentRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(paid, noLines));
        JobCard withCustomer = jobCard(100L, "DBM-1");
        withCustomer.setCustomerId(55L);
        when(jobCardRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(withCustomer, jobCard(200L, "DBM-2")));
        Customer gstCustomer = new Customer();
        gstCustomer.setId(55L);
        gstCustomer.setName("Kalaivani Logistics");
        gstCustomer.setPhone("94370 11111");
        gstCustomer.setGstNo("21ABCDE1234F1Z5");
        when(customerRepo.findByOrgIdAndIdInOrderByNameAsc(eq(ORG), any())).thenReturn(List.of(gstCustomer));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(eq(ORG), any())).thenReturn(List.of(
            line(1L, 1, "15000"), line(1L, 2, "5000")));

        List<List<String>> rows = csv(service.exportReviewListCsv("receipt", List.of(2L, 1L)));
        List<String> h = rows.get(0);
        // rev 87 — the accounts team's layout v2: coded headers in this exact order (TXNREF appears twice).
        assertThat(h).containsExactly("TXNDT", "TXNREF", "BRNCD", "CUSTNM", "VHCLNO", "DBMSID", "INVNO",
            "INVAMT", "TXNCTG", "TXNAMT", "CLMAMT", "LNID", "LNDT", "LNMODE", "BNKNM", "TXNREF", "LMAMT", "OVDRAMT",
            "OVDRMRK", "LNRMRK", "TXNSTS", "WRKFLWSTS", "CLMCTG", "CUSTGST", "CLMFNLAMT", "CLMOVDRMRK");
        int status = h.indexOf("TXNSTS"), doc = h.indexOf("TXNREF"), txn = h.indexOf("TXNAMT"),
            lineId = h.indexOf("LNID"), lineAmt = h.indexOf("LMAMT"), dbm = h.indexOf("DBMSID"),
            submitted = h.indexOf("TXNDT"), gst = h.indexOf("CUSTGST"), claim = h.indexOf("CLMCTG"),
            workflow = h.indexOf("WRKFLWSTS");

        assertThat(rows).hasSize(5); // header, RCV-0015 (no lines), RCV-0012 ×2 lines, total
        // Screen order: the caller sent 2 before 1.
        assertThat(rows.get(1).get(doc)).isEqualTo("RCV-0015");
        assertThat(rows.get(1).get(status)).isEqualTo("Verified");
        assertThat(rows.get(1).get(txn)).isEqualTo("8000");
        assertThat(rows.get(1).get(lineId)).isEmpty();
        assertThat(rows.get(1).get(dbm)).isEqualTo("DBM-2");
        assertThat(rows.get(1).get(gst)).isEmpty();
        assertThat(rows.get(1).get(workflow)).isEqualTo("VERIFIED");
        assertThat(rows.get(1).get(claim)).isNotEmpty();
        // Submitted 20:00 UTC on the 3rd is the 4th in IST.
        assertThat(rows.get(1).get(submitted)).isEqualTo("2026-10-04");

        assertThat(rows.get(2).get(doc)).isEqualTo("RCV-0012");
        assertThat(rows.get(2).get(status)).isEqualTo("Pending");
        assertThat(rows.get(2).get(txn)).isEqualTo("20000");
        assertThat(rows.get(2).get(lineAmt)).isEqualTo("15000");
        assertThat(rows.get(2).get(gst)).isEqualTo("21ABCDE1234F1Z5");
        assertThat(rows.get(3).get(gst)).isEqualTo("21ABCDE1234F1Z5");
        assertThat(rows.get(3).get(doc)).isEqualTo("RCV-0012");
        assertThat(rows.get(3).get(txn)).isEmpty();
        assertThat(rows.get(3).get(lineAmt)).isEqualTo("5000");

        List<String> total = rows.get(4);
        assertThat(total.get(0)).isEqualTo("Total");
        assertThat(total.get(doc)).isEqualTo("2 transactions");
        assertThat(total.get(txn)).isEqualTo("28000");
        assertThat(total.get(lineAmt)).isEqualTo("20000");
        rows.forEach(r -> assertThat(r).hasSize(h.size()));
    }

    @Test
    void receiptList_dropsOtherBranchesAndStatesTheWindowNeverShows() {
        ReceiveDocument visible = receipt(1L, "RCV-0012", WorkflowStatus.SUBMITTED, BRANCH, 100L);
        ReceiveDocument otherBranch = receipt(3L, "RCV-0099", WorkflowStatus.SUBMITTED, OTHER_BRANCH, 100L);
        ReceiveDocument draft = receipt(4L, null, WorkflowStatus.DRAFT, BRANCH, 100L);
        when(receiveDocumentRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(visible, otherBranch, draft));
        when(jobCardRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(jobCard(100L, null)));

        List<List<String>> rows = csv(service.exportReviewListCsv("receipt", List.of(3L, 4L, 1L)));
        int doc = rows.get(0).indexOf("TXNREF");

        assertThat(rows).hasSize(3); // header, RCV-0012, total
        assertThat(rows.get(1).get(doc)).isEqualTo("RCV-0012");
        assertThat(rows.get(2).get(doc)).isEqualTo("1 transaction");
    }

    @Test
    void receiptList_aClaimWritesTheAmountClaimedOncePerTransaction_andTheCloseDetails_rev87() {
        ReceiveDocument claim = receipt(1L, "RCV-0020", WorkflowStatus.VERIFIED, BRANCH, 100L);
        when(receiveDocumentRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(claim));
        JobCard jc = jobCard(100L, "DBM-1");
        jc.setInvoiceAmount(new BigDecimal("50000"));
        when(jobCardRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(jc));
        when(reviewService.toReceiptItems(eq(ORG), any())).thenAnswer(i -> ((List<ReceiveDocument>) i.getArgument(1)).stream()
            .map(d -> new ReviewQueueItem("receipt", d.getId(), d.getDocumentNo(), d.getBranchId(), "PUN", "Customer",
                "Service", new BigDecimal("20000"), false, false, Instant.parse("2026-10-03T20:00:00Z"),
                d.getWorkflowStatus().name(), true, false, false, false, null)).toList());
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(eq(ORG), any())).thenReturn(List.of(
            line(1L, 1, "15000"), line(1L, 2, "5000")));
        ClaimClose close = new ClaimClose();
        close.setJobCardId(100L);
        close.setFinalAmount(new BigDecimal("42000"));
        close.setOverridden(true);
        close.setOverrideReason("OEM paid labour only");
        when(claimCloseRepo.findByOrgIdAndJobCardIdIn(eq(ORG), any())).thenReturn(List.of(close));

        List<List<String>> rows = csv(service.exportReviewListCsv("receipt", List.of(1L)));
        List<String> h = rows.get(0);
        int clm = h.indexOf("CLMAMT"), fin = h.indexOf("CLMFNLAMT"), why = h.indexOf("CLMOVDRMRK"), cat = h.indexOf("CLMCTG");

        assertThat(rows.get(1).get(clm)).isEqualTo("50000");   // the invoice amount, as the dashboard counts a claim
        assertThat(rows.get(2).get(clm)).isEmpty();            // once per transaction, so it never double counts
        assertThat(rows.get(1).get(cat)).isEqualTo("Yes");
        assertThat(rows.get(1).get(fin)).isEqualTo("42000");
        assertThat(rows.get(2).get(fin)).isEqualTo("42000");
        assertThat(rows.get(1).get(why)).isEqualTo("OEM paid labour only");
    }

    @Test
    void expenseList_followsTheExpenseLayout_andCarriesTheClaimColumns_rev87() {
        ExpenseDocument claim = new ExpenseDocument();
        claim.setId(9L);
        claim.setOrgId(ORG);
        claim.setBranchId(BRANCH);
        claim.setDocumentNo("EXP-0007");
        claim.setWorkflowStatus(ExpenseWorkflowStatus.VERIFIED);
        claim.setInvoiceNo("MR234");
        claim.setClaimFinalAmount(new BigDecimal("900"));
        claim.setClaimComputedTotal(new BigDecimal("1000"));
        claim.setClaimOverridden(true);
        claim.setClaimOverrideReason("Part disallowed");
        ExpenseDocument plain = new ExpenseDocument();
        plain.setId(10L);
        plain.setOrgId(ORG);
        plain.setBranchId(BRANCH);
        plain.setDocumentNo("EXP-0008");
        plain.setWorkflowStatus(ExpenseWorkflowStatus.VERIFIED);
        when(expenseDocumentRepo.findByOrgIdAndIdIn(eq(ORG), any())).thenReturn(List.of(claim, plain));
        when(reviewService.toExpenseItems(eq(ORG), any())).thenAnswer(i -> ((List<ExpenseDocument>) i.getArgument(1)).stream()
            .map(d -> new ReviewQueueItem("expense", d.getId(), d.getDocumentNo(), d.getBranchId(), "PUN", "City Fuel",
                "Service", new BigDecimal("1000"), false, false, Instant.parse("2026-10-03T20:00:00Z"),
                d.getWorkflowStatus().name(), d.getId() == 9L, false, false, false, null)).toList());
        ExpenseLine l1 = new ExpenseLine();
        l1.setOrgId(ORG);
        l1.setExpenseDocumentId(9L);
        l1.setLineNo(1);
        l1.setLineId("EXP-0007-L1");
        l1.setTransactionDate(LocalDate.of(2026, 10, 3));
        l1.setAmount(new BigDecimal("1000"));
        ExpenseLine l2 = new ExpenseLine();
        l2.setOrgId(ORG);
        l2.setExpenseDocumentId(10L);
        l2.setLineNo(1);
        l2.setLineId("EXP-0008-L1");
        l2.setTransactionDate(LocalDate.of(2026, 10, 3));
        l2.setAmount(new BigDecimal("1000"));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(eq(ORG), any())).thenReturn(List.of(l1, l2));

        List<List<String>> rows = csv(service.exportReviewListCsv("expense", List.of(9L, 10L)));
        List<String> h = rows.get(0);
        assertThat(h).containsExactly("TXNDT", "TXNREF", "BRNCD", "VNDRNM", "TXNCTG", "CUSTNM", "VHCLNO", "OORID",
            "DBMSID", "INVNO", "OVRLMT", "APRVBY", "CLM", "TXNAMT", "CLMAMT", "LNID", "LNDT", "TXNCTG2", "PYMNMODE",
            "BKNAME", "PYMNREF", "LNAMT", "ORGAMT", "OVDRMRK", "LMRMRK", "TXNSTS", "WRKFLWSTS", "CLMFNLAMT",
            "CLMOVDRMRK");
        int clm = h.indexOf("CLMAMT"), fin = h.indexOf("CLMFNLAMT"), why = h.indexOf("CLMOVDRMRK"), inv = h.indexOf("INVNO");

        assertThat(rows.get(1).get(inv)).isEqualTo("MR234");
        assertThat(rows.get(1).get(clm)).isEqualTo("1000");
        assertThat(rows.get(1).get(fin)).isEqualTo("900");
        assertThat(rows.get(1).get(why)).isEqualTo("Part disallowed");
        assertThat(rows.get(2).get(clm)).isEmpty();   // not a claim
        assertThat(rows.get(2).get(fin)).isEmpty();
        List<String> total = rows.get(3);
        assertThat(total.get(0)).isEqualTo("Total");
        assertThat(total.get(h.indexOf("TXNREF"))).isEqualTo("2 transactions");
        assertThat(total.get(h.indexOf("TXNAMT"))).isEqualTo("2000");
        assertThat(total.get(h.indexOf("LNAMT"))).isEqualTo("2000");
        rows.forEach(r -> assertThat(r).hasSize(h.size()));
    }

    @Test
    void unknownListType_isRejected() {
        assertThatThrownBy(() -> service.exportReviewListCsv("ledger", List.of(1L)))
            .isInstanceOf(DamsException.class);
    }

    // ---------------------------------------------------------------- fixtures

    private static ReceiveDocument receipt(Long id, String no, WorkflowStatus status, Long branchId, Long jobCardId) {
        ReceiveDocument d = new ReceiveDocument();
        d.setId(id);
        d.setOrgId(ORG);
        d.setBranchId(branchId);
        d.setJobCardId(jobCardId);
        d.setDocumentNo(no);
        d.setWorkflowStatus(status);
        return d;
    }

    private static JobCard jobCard(Long id, String dbmId) {
        JobCard jc = new JobCard();
        jc.setId(id);
        jc.setOrgId(ORG);
        jc.setBranchId(BRANCH);
        jc.setDbmId(dbmId);
        return jc;
    }

    private static SettlementLine line(Long docId, int no, String amount) {
        SettlementLine l = new SettlementLine();
        l.setOrgId(ORG);
        l.setReceiveDocumentId(docId);
        l.setLineNo(no);
        l.setLineId("L" + no);
        l.setTransactionDate(LocalDate.of(2026, 10, 3));
        l.setSettlementModeId(5L);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    /** Strips the BOM and splits a simple (no quoted commas in these fixtures) CSV into cells. */
    private static List<List<String>> csv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).replace("﻿", "");
        return Arrays.stream(text.split("\n")).map(line -> Arrays.asList(line.split(",", -1))).toList();
    }
}
