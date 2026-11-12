package com.dams.spareparts.util;

import com.dams.common.exception.DamsException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxReaderTest {

    @Test
    void readsBothSheetsOfTheOwnersWorkbook_findingTheDataOnTheSecondOne() {
        List<XlsxReader.Sheet> sheets = XlsxReader.read(new ByteArrayInputStream(XlsxFixtures.ownersWorkbook()));

        assertThat(sheets).extracting(XlsxReader.Sheet::name).containsExactly("SpareList", "Sheet2");
        assertThat(sheets.get(0).rows()).isEmpty();                     // the first sheet is empty, as in the real file
        XlsxReader.Sheet data = sheets.get(1);
        assertThat(data.rows().get(0).number()).isEqualTo(1);           // the title line
        assertThat(data.rows().get(0).cell(1)).isEqualTo("LOCAL PARTS OORIBA");   // column B = index 1
        assertThat(data.rows().get(1).cell(3)).isEqualTo("PART ID");
    }

    @Test
    void keepsTheExcelRowNumbers_soAnErrorCanSay_row11() {
        XlsxReader.Sheet data = XlsxReader.read(new ByteArrayInputStream(XlsxFixtures.ownersWorkbook())).get(1);

        assertThat(data.rows()).extracting(XlsxReader.Row::number).contains(3, 4, 7, 11, 17, 224);
    }

    @Test
    void aFormulaCell_returnsItsStoredValue_andAJobCardNumberKeepsItsExcelNumberForm() {
        XlsxReader.Sheet data = XlsxReader.read(new ByteArrayInputStream(XlsxFixtures.ownersWorkbook())).get(1);
        XlsxReader.Row washer = data.rows().stream().filter(r -> r.number() == 11).findFirst().orElseThrow();

        assertThat(washer.cell(11)).isEqualTo("6");                    // L = CLOSING QTY, a formula
        assertThat(washer.cell(10)).isEqualTo("4.010615068E9");         // K = JOB CARD, as Excel stores it
        assertThat(SparePartsText.cleanNumberText(washer.cell(10))).isEqualTo("4010615068");
    }

    @Test
    void columnLettersBecomeIndexes() {
        assertThat(XlsxReader.columnIndex("A1")).isZero();
        assertThat(XlsxReader.columnIndex("B3")).isEqualTo(1);
        assertThat(XlsxReader.columnIndex("Z9")).isEqualTo(25);
        assertThat(XlsxReader.columnIndex("AA10")).isEqualTo(26);
    }

    @Test
    void aFileThatIsNotAWorkbook_isRefusedWithAPlainMessage() {
        assertThatThrownBy(() -> XlsxReader.read(new ByteArrayInputStream("just some text".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(DamsException.class).hasMessageContaining("not a readable Excel");
    }

    @Test
    void aWorkbookThatDeclaresAnExternalEntity_neverLeaksTheFileItPointsAt() throws IOException {
        java.nio.file.Path secret = java.nio.file.Files.createTempFile("xxe-secret", ".txt");
        java.nio.file.Files.writeString(secret, "SECRET-MARKER");
        String evil = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
            + "<workbook xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"&xxe;\" r:id=\"rId1\"/></sheets></workbook>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write(evil.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        try {
            // Refusing the file is fine; reading it is fine; what must never happen is the secret appearing in the result.
            List<XlsxReader.Sheet> sheets = XlsxReader.read(new ByteArrayInputStream(bytes.toByteArray()));
            assertThat(sheets.toString()).doesNotContain("SECRET-MARKER");
        } catch (DamsException refused) {
            assertThat(refused.getMessage()).doesNotContain("SECRET-MARKER");
        } finally {
            java.nio.file.Files.deleteIfExists(secret);
        }
    }
}
