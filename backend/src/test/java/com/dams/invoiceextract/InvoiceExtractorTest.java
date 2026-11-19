package com.dams.invoiceextract;

import com.dams.invoiceextract.dto.InvoiceExtractResponse;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.Item;
import com.dams.invoiceextract.service.InvoiceExtractor;
import com.dams.invoiceextract.service.PdfContent;
import com.dams.invoiceextract.service.PdfTextReader;
import com.dams.invoiceextract.service.PdfWord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads sanitised copies of real Eicher dealer PDFs (names, phones, PAN, GSTIN, addresses, vehicle and chassis numbers
 * replaced; the amounts are the real ones) and proves the figures reconcile with what each document prints itself.
 * The fixtures hold {@code pdftotext -bbox} words and {@code -layout} text, so no PDF tool is needed to run these.
 */
class InvoiceExtractorTest {

    private final InvoiceExtractor extractor = new InvoiceExtractor();

    static PdfContent fixture(String name) {
        return new PdfContent(PdfTextReader.parseBbox(gz(name + ".bbox.txt.gz")), gz(name + ".layout.txt.gz"));
    }

    private static String gz(String resource) {
        try (InputStream in = InvoiceExtractorTest.class.getResourceAsStream("/invoiceextract/" + resource)) {
            assertThat(in).as(resource).isNotNull();
            return new String(new GZIPInputStream(in).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String field(InvoiceExtractResponse r, String label) {
        return r.header().stream().filter(f -> f.label().equals(label)).map(InvoiceExtractResponse.Field::value).findFirst().orElse(null);
    }

    private static BigDecimal total(InvoiceExtractResponse r, String label) {
        return r.totals().stream().filter(t -> t.label().equals(label)).map(InvoiceExtractResponse.Total::value).findFirst().orElse(null);
    }

    private static Item byCode(List<Item> items, String code) {
        return items.stream().filter(i -> code.equals(i.code())).findFirst().orElseThrow(() -> new AssertionError("no line " + code));
    }

    private static void assertEvery(BigDecimal actual, String expected) {
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    void igstTaxInvoice_readsSparesLubeAndLabour_andEveryCheckPasses() {
        InvoiceExtractResponse r = extractor.extract(fixture("tax_invoice_igst"), "inv.pdf");

        assertThat(r.documentType()).isEqualTo("TAX_INVOICE");
        assertThat(r.documentLabel()).isEqualTo("Tax invoice (IGST)");
        assertThat(r.spares()).hasSize(10);          // the table runs over a page break
        assertThat(r.lube()).hasSize(2);             // this invoice's lube header says "Part Description", not "Lube Description"
        assertThat(r.labour()).hasSize(5);
        assertThat(r.problems()).isEmpty();
        assertThat(r.checksPassed()).isEqualTo(r.checksTotal());
        assertThat(r.checksTotal()).isGreaterThan(60);

        assertThat(field(r, "Invoice No.")).isEqualTo("7731162600134");
        assertThat(field(r, "Job Card No. (Eicher)")).isEqualTo("4010734608");
        assertThat(field(r, "Vehicle Reg No.")).isEqualTo("CG00AA0003");
        assertThat(field(r, "Chassis No.")).isEqualTo("MC2SAMPLE00000003");
        assertThat(field(r, "Model")).isEqualTo("Pro2075 E TCST BSVI PS NGB BASE WTL");   // wraps onto a second line
        assertEvery(total(r, "Invoice amount"), "16644.11");

        Item secondary = byCode(r.spares(), "ID350639");          // part number wraps over three lines: ID350639(560 / 39400) / 5%
        assertThat(secondary.hsnSac()).isEqualTo("56039400");
        assertEvery(secondary.gstRate(), "5");
        assertThat(secondary.description()).isEqualTo("SECONDARY ELEMENT");

        Item wiper = byCode(r.spares(), "IE451148");
        assertEvery(wiper.qty(), "2");
        assertEvery(wiper.taxable(), "940.68");
        assertEvery(wiper.total(), "1110.00");

        Item oil = byCode(r.lube(), "IM300905L");
        assertEvery(oil.qty(), "10");
        assertEvery(oil.discount(), "381.36");

        Item diag = r.labour().get(0);
        assertThat(diag.description()).isEqualTo("Advance Diagnostic Labor");
        assertThat(diag.code()).isEqualTo("0101888880");
        assertEvery(diag.total(), "352.05");
        assertThat(r.freeItems()).hasSize(1);
    }

    @Test
    void cgstSgstTaxInvoice_picksUpTheRowThatLandsOnTheNextPageWithoutAHeader() {
        InvoiceExtractResponse r = extractor.extract(fixture("tax_invoice_cgst_sgst"), "inv.pdf");

        assertThat(r.documentLabel()).isEqualTo("Tax invoice (CGST + SGST)");
        assertThat(r.spares()).hasSize(7);           // row 7 (Rust Bust) opens page 2, which repeats no header
        assertThat(byCode(r.spares(), "IZ300494").description()).isEqualTo("Rust Bust");
        assertThat(r.lube()).hasSize(1);
        assertThat(r.labour()).isEmpty();
        assertThat(r.problems()).isEmpty();
        assertThat(r.checksPassed()).isEqualTo(r.checksTotal());

        Item oil = r.lube().get(0);
        assertEvery(oil.qty(), "11");
        assertEvery(oil.discount(), "419.50");       // the discount is for the whole line, not per unit
        assertEvery(oil.cgst(), "339.79");
        assertEvery(oil.sgst(), "339.79");
        assertEvery(total(r, "Invoice amount"), "12811.52");
        assertThat(field(r, "Receiver GSTIN")).isEqualTo("21BBBBB1111B1ZW");
    }

    @Test
    void nothingChargedInvoice_listsTheFreeItems_andNoLines() {
        InvoiceExtractResponse r = extractor.extract(fixture("nil_warranty_invoice"), "inv.pdf");

        assertThat(r.documentLabel()).isEqualTo("Tax invoice — nothing charged");
        assertThat(r.spares()).isEmpty();
        assertThat(r.lube()).isEmpty();
        assertThat(r.labour()).isEmpty();
        assertThat(r.freeItems()).hasSize(6);
        assertThat(r.freeItems().get(3).description()).isEqualTo("SCR Dosing pump Adblue lines replacement");
        assertEvery(r.freeItems().get(3).quantity(), "0.65");
        assertThat(r.checksPassed()).isEqualTo(r.checksTotal());
    }

    @Test
    void warrantyClaim_readsPartsAndLabour_includingNumbersThatWrapInsideANarrowColumn() {
        InvoiceExtractResponse r = extractor.extract(fixture("warranty_claim"), "claim.pdf");

        assertThat(r.documentType()).isEqualTo("WARRANTY_CLAIM");
        assertThat(r.spares()).hasSize(5);
        assertThat(r.lube()).isEmpty();
        assertThat(r.labour()).hasSize(3);
        assertThat(r.problems()).isEmpty();
        assertThat(r.checksPassed()).isEqualTo(r.checksTotal());

        Item cover = byCode(r.spares(), "ID343468");
        assertEvery(cover.unitAmount(), "7625.15");   // printed 7625.1 / 5 on two lines
        assertThat(cover.hsnSac()).isEqualTo("87089300");     // printed 8708930 / 0
        assertEvery(cover.total(), "9664.30");
        assertEvery(byCode(r.spares(), "ID343472").taxable(), "10005.66");

        Item van = byCode(r.labour(), "0117999991");
        assertThat(van.description()).isEqualTo("Service Van Branded");
        assertEvery(van.manHr(), "267.00");
        assertEvery(van.ratePerHr(), "22.00");
        assertEvery(van.taxable(), "5874.00");
        assertEvery(van.tds(), "-117.48");

        assertThat(field(r, "Claim No.")).isEqualTo("7731N261100138");
        assertThat(field(r, "Model")).isEqualTo("Pro3018XP Plus M HSD 22FT BSVI NGB 190");
        assertThat(field(r, "Billed To")).isEqualTo("VE COMMERCIAL VEHICLE LTD");
        assertEvery(total(r, "Grand total"), "43006.06");
    }

    @Test
    void aMisreadAmount_isCaughtByTheChecks_notTrusted() {
        PdfContent real = fixture("tax_invoice_igst");
        List<List<PdfWord>> pages = new ArrayList<>();
        for (List<PdfWord> pg : real.pages()) {
            List<PdfWord> copy = new ArrayList<>();
            for (PdfWord w : pg) {
                // one line total read as 1,651.99 instead of 1,651.49
                copy.add(w.t().equals("1,651.49") ? new PdfWord("1,651.99", w.x0(), w.y0(), w.x1(), w.y1()) : w);
            }
            pages.add(copy);
        }

        InvoiceExtractResponse r = extractor.extract(new PdfContent(pages, real.layoutText()), "tampered.pdf");

        assertThat(r.checksPassed()).isLessThan(r.checksTotal());
        assertThat(r.checks()).anyMatch(c -> !c.ok() && c.name().contains("total = taxable + tax"));
        assertThat(r.checks()).anyMatch(c -> !c.ok() && c.name().contains("sum of line totals = printed sub total"));
    }

    @Test
    void aPageThatIsNotAnInvoice_givesAnEmptyResult_withoutFailing() {
        PdfContent other = new PdfContent(List.of(List.of(new PdfWord("Hello", 10, 10, 40, 20), new PdfWord("world", 45, 10, 80, 20))), "Hello world");

        InvoiceExtractResponse r = extractor.extract(other, "other.pdf");

        assertThat(r.spares()).isEmpty();
        assertThat(r.header()).isEmpty();
        assertThat(r.checksTotal()).isZero();
    }
}
