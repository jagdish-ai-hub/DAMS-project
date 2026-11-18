package com.dams.invoiceextract;

import com.dams.common.exception.DamsException;
import com.dams.invoiceextract.service.PdfContent;
import com.dams.invoiceextract.service.PdfTextReader;
import com.dams.invoiceextract.service.PdfWord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfTextReaderTest {

    /** A hand-written one-page PDF with the text "Hello Invoice" — no PDF library needed. */
    private static final String TINY_PDF = "%PDF-1.4\n"
        + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
        + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
        + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
        + "4 0 obj<</Length 44>>stream\nBT /F1 12 Tf 72 700 Td (Hello Invoice) Tj ET\nendstream endobj\n"
        + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n"
        + "trailer<</Root 1 0 R/Size 6>>\n%%EOF\n";

    @Test
    void parseBbox_readsPagesAndWordsWithPositions_andDecodesEntities() {
        String html = "<doc>\n  <page width=\"612.0\" height=\"792.0\">\n"
            + "    <word xMin=\"10.5\" yMin=\"20.25\" xMax=\"40.0\" yMax=\"30.0\">AT&amp;T</word>\n"
            + "    <word xMin=\"45.0\" yMin=\"20.25\" xMax=\"70.0\" yMax=\"30.0\">¤2.00</word>\n"
            + "  </page>\n  <page width=\"612.0\" height=\"792.0\">\n"
            + "    <word xMin=\"1.0\" yMin=\"2.0\" xMax=\"3.0\" yMax=\"4.0\">next</word>\n  </page>\n</doc>\n";

        List<List<PdfWord>> pages = PdfTextReader.parseBbox(html);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).extracting(PdfWord::t).containsExactly("AT&T", "¤2.00");
        assertThat(pages.get(0).get(0).x0()).isEqualTo(10.5);
        assertThat(pages.get(0).get(0).y0()).isEqualTo(20.25);
        assertThat(pages.get(1)).hasSize(1);
    }

    @Test
    void aMissingPdfToolsBinary_saysSoPlainly() {
        PdfTextReader missing = new PdfTextReader("/no/such/pdftotext");

        assertThatThrownBy(() -> missing.read(TINY_PDF.getBytes(StandardCharsets.ISO_8859_1)))
            .isInstanceOf(DamsException.class).hasMessageContaining("poppler-utils");
    }

    @Test
    void aRealPdf_isReadByPdftotext_whenItIsInstalled() {
        assumeTrue(pdftotextInstalled(), "poppler-utils (pdftotext) is not installed here");

        PdfContent c = new PdfTextReader().read(TINY_PDF.getBytes(StandardCharsets.ISO_8859_1));

        assertThat(c.pages()).hasSize(1);
        assertThat(c.pages().get(0)).extracting(PdfWord::t).containsExactly("Hello", "Invoice");
        assertThat(c.layoutText()).contains("Hello Invoice");
    }

    @Test
    void garbageThatCallsItselfAPdf_isRefusedByPdftotext_whenItIsInstalled() {
        assumeTrue(pdftotextInstalled(), "poppler-utils (pdftotext) is not installed here");

        assertThatThrownBy(() -> new PdfTextReader().read("%PDF-1.4 this is not really a pdf".getBytes(StandardCharsets.ISO_8859_1)))
            .isInstanceOf(DamsException.class);
    }

    private static boolean pdftotextInstalled() {
        try {
            Process p = new ProcessBuilder("pdftotext", "-v").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}
