package com.dams.spareparts.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a real .xlsx (a zip of XML parts) in memory, laid out exactly like the owner's "Spare parts.xlsx": an empty
 * first sheet "SpareList", then "Sheet2" with a title in B1, the header row in row 2 (columns B..L), part rows with
 * formula cells (their stored values), a job card number stored as the number 4.010541946E9, and a TOTAL line.
 */
public final class XlsxFixtures {

    private XlsxFixtures() {
    }

    private record Cell(String column, char kind, String value) {
    }

    private static Cell s(String column, String text) {
        return new Cell(column, 's', text);
    }

    private static Cell n(String column, String number) {
        return new Cell(column, 'n', number);
    }

    private static Cell f(String column, String cachedValue) {
        return new Cell(column, 'f', cachedValue);
    }

    /** The owner's layout with five representative rows. */
    public static byte[] ownersWorkbook() {
        List<String> shared = new ArrayList<>();
        StringBuilder rows = new StringBuilder();
        row(rows, shared, 1, s("B", "LOCAL PARTS OORIBA"));
        row(rows, shared, 2, s("B", "SL NO."), s("C", "BRANCH"), s("D", "PART ID"), s("E", "PART DESCRIPTION"),
            s("F", "OPENING QTY"), s("G", "STORAGE BIN"), s("H", "PRICE PER UNIT  "), s("I", "TOTAL PRICE "),
            s("J", "BILLED QTY"), s("K", "JOB CARD"), s("L", "CLOSING QTY"));
        row(rows, shared, 3, f("B", "1"), s("C", "OOR"), s("D", "IA306245"), s("E", "RUB-DRIP-LH"), n("F", "1.0"),
            s("G", "BOX-2"), n("H", "700.0"), f("I", "700"), f("L", "1"));
        row(rows, shared, 4, f("B", "2"), s("C", "OOB"), s("D", "IA306698"), s("E", "CAP L"), n("F", "1.0"),
            s("G", "DRAW-2"), n("H", "20.0"), f("I", "20"), f("L", "1"));
        row(rows, shared, 7, f("B", "5"), s("C", "OOB"), s("D", "IA306699"), s("E", "CAP R"), n("F", "2.0"),
            s("G", "DRAW-2"), n("H", "20.0"), f("I", "40"), n("J", "1.0"), n("K", "4.010541946E9"), f("L", "1"));
        row(rows, shared, 11, f("B", "9"), s("C", "OOB"), s("D", "MF450155"), s("E", "PLAIN WASHER"), n("F", "10.0"),
            s("G", "DRAW-2"), n("H", "25.0"), f("I", "250"), n("J", "4.0"), n("K", "4.010615068E9"), f("L", "6"));
        row(rows, shared, 17, f("B", "15"), s("C", "OOJ"), s("D", "IA309072"), s("E", "Fender Assy RH 1114 Sleepar"),
            n("F", "1.0"), s("G", "CORNER WALL"), n("H", "3885.0"), f("I", "3885"), n("J", "1.0"), n("K", "4.010614834E9"), f("L", "0"));
        row(rows, shared, 224, s("G", "TOTAL"), n("H", "41971"), n("I", "49032"));   // the TOTAL line: not a part
        return workbook(shared, rows.toString());
    }

    /** A workbook whose only sheet has no PART ID header at all. */
    public static byte[] workbookWithoutPartHeader() {
        List<String> shared = new ArrayList<>();
        StringBuilder rows = new StringBuilder();
        row(rows, shared, 1, s("A", "Name"), s("B", "Qty"));
        row(rows, shared, 2, s("A", "washer"), n("B", "3"));
        return workbook(shared, rows.toString());
    }

    private static void row(StringBuilder out, List<String> shared, int number, Cell... cells) {
        out.append("<row r=\"").append(number).append("\">");
        for (Cell c : cells) {
            String ref = c.column() + number;
            switch (c.kind()) {
                case 's' -> {
                    shared.add(c.value());
                    out.append("<c r=\"").append(ref).append("\" t=\"s\"><v>").append(shared.size() - 1).append("</v></c>");
                }
                case 'n' -> out.append("<c r=\"").append(ref).append("\"><v>").append(c.value()).append("</v></c>");
                default -> out.append("<c r=\"").append(ref).append("\"><f>1+0</f><v>").append(c.value()).append("</v></c>");
            }
        }
        out.append("</row>");
    }

    private static byte[] workbook(List<String> shared, String sheetRows) {
        StringBuilder strings = new StringBuilder("<?xml version=\"1.0\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String text : shared) {
            strings.append("<si><t>").append(text.replace("&", "&amp;")).append("</t></si>");
        }
        strings.append("</sst>");
        String sheetNs = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook " + sheetNs
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                + "<sheet name=\"SpareList\" sheetId=\"3\" r:id=\"rId3\"/><sheet name=\"Sheet2\" sheetId=\"4\" r:id=\"rId4\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId3\" Target=\"worksheets/sheet3.xml\"/><Relationship Id=\"rId4\" Target=\"worksheets/sheet4.xml\"/></Relationships>");
            put(zip, "xl/sharedStrings.xml", strings.toString());
            put(zip, "xl/worksheets/sheet3.xml", "<?xml version=\"1.0\"?><worksheet " + sheetNs + "><sheetData/></worksheet>");
            put(zip, "xl/worksheets/sheet4.xml", "<?xml version=\"1.0\"?><worksheet " + sheetNs + "><sheetData>" + sheetRows + "</sheetData></worksheet>");
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
