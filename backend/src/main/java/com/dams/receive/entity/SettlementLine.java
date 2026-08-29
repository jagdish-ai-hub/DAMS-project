package com.dams.receive.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One payment received against a {@link ReceiveDocument}. {@code lineNo} is 1-based and
 * dense within a document; {@code lineId} ({@code {documentNo}-L{n}}) is stamped once the
 * parent document has a number, and is never reused even if a line is later voided.
 *
 * The override columns ({@code overriddenBy} / {@code overrideReason} / {@code overriddenAt}
 * / {@code originalAmount}) are the Accountant amount-override trail — populated in Stage 7,
 * defined here so the column set is stable.
 */
@Entity
@Table(name = "settlement_line")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class SettlementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "receive_document_id", nullable = false, updatable = false)
    private Long receiveDocumentId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "line_id", length = 48)
    private String lineId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "settlement_mode_id", nullable = false)
    private Long settlementModeId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "original_amount", precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "bank_id")
    private Long bankId;

    @Column(name = "transaction_ref", length = 80)
    private String transactionRef;

    @Column(length = 300)
    private String remark;

    @Column(name = "overridden_by")
    private Long overriddenBy;

    @Column(name = "override_reason", length = 300)
    private String overrideReason;

    @Column(name = "overridden_at")
    private Instant overriddenAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
