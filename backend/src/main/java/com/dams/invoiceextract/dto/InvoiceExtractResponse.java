package com.dams.invoiceextract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * What was read from one Eicher dealer PDF (rev 98 test tool). Nothing here is stored.
 * {@code checks} are the proof: every figure is recomputed and compared with what the document itself prints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceExtractResponse(
    String fileName,
    String documentType,          // TAX_INVOICE | WARRANTY_CLAIM
    String documentLabel,
    List<Field> header,
    List<Item> spares,
    List<Item> lube,
    List<Item> labour,
    List<FreeItem> freeItems,
    List<Total> totals,
    List<HsnRow> hsnSummary,
    List<Check> checks,
    List<String> problems,
    int checksPassed,
    int checksTotal
) {

    /** A labelled header value, in the order it should be shown. */
    public record Field(String label, String value) {
    }

    /**
     * One spares / lube / labour line. A tax invoice fills {@code unitAmount} (net unit price; gross for labour); a
     * warranty claim fills the rate, handling, TDS and — for labour — man-hours and rate per hour. Unused fields are null.
     */
    public record Item(
        Integer page,
        String sno,
        String code,
        String hsnSac,
        BigDecimal gstRate,
        String description,
        BigDecimal qty,
        String uom,
        BigDecimal manHr,
        BigDecimal ratePerHr,
        BigDecimal unitAmount,
        BigDecimal discount,
        BigDecimal handling,
        BigDecimal taxable,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal kfc,
        BigDecimal tds,
        BigDecimal total
    ) {
    }

    /** An item "Replaced Under Warranty/AMC/FOC" — listed on the invoice with no charge. */
    public record FreeItem(Integer sno, String itemCode, String description, BigDecimal quantity) {
    }

    /** A total the document prints (spares, lube, labour, invoice amount ...). */
    public record Total(String label, BigDecimal value) {
    }

    public record HsnRow(String hsn, BigDecimal taxable, BigDecimal total) {
    }

    public record Check(String name, boolean ok, String detail) {
    }
}
