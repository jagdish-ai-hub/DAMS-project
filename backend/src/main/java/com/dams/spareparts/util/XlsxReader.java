package com.dams.spareparts.util;

import com.dams.common.exception.DamsException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the text of every cell of every sheet of an .xlsx file using only the JDK (zip + StAX).
 *
 * Deliberately not Apache POI: it would be a new dependency, and the backend has already hit its
 * Metaspace limit once (plan.md rev 90). This reader only needs cell text, so it is ~150 lines.
 *
 * Safety: DTDs and external entities are switched off (no XXE), and the unzipped size is capped so a
 * small "zip bomb" file cannot exhaust memory. Formula cells return their stored (cached) value.
 */
public final class XlsxReader {

    /** Total unzipped bytes we are willing to read from one upload. */
    private static final long MAX_UNZIPPED_BYTES = 60L * 1024 * 1024;
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** One row: its Excel row number (as the user sees it) and its cell texts, indexed from column A = 0. */
    public record Row(int number, String[] cells) {
        public String cell(int index) {
            return index >= 0 && index < cells.length && cells[index] != null ? cells[index] : "";
        }
    }

    /** One worksheet: its name and its non-empty rows in order. */
    public record Sheet(String name, List<Row> rows) {
    }

    private XlsxReader() {
    }

    public static List<Sheet> read(InputStream in) {
        Map<String, byte[]> parts = unzip(in);
        try {
            List<String> shared = readSharedStrings(parts.get("xl/sharedStrings.xml"));
            Map<String, String> targetByRelId = readRelationships(parts.get("xl/_rels/workbook.xml.rels"));
            byte[] workbook = parts.get("xl/workbook.xml");
            if (workbook == null) {
                throw notExcel();
            }
            List<Sheet> sheets = new ArrayList<>();
            XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(workbook));
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("sheet")) {
                    String name = r.getAttributeValue(null, "name");
                    String target = targetByRelId.get(r.getAttributeValue(REL_NS, "id"));
                    byte[] sheetXml = target == null ? null : parts.get(resolve(target));
                    sheets.add(new Sheet(name, sheetXml == null ? List.of() : readSheet(sheetXml, shared)));
                }
            }
            return sheets;
        } catch (XMLStreamException e) {
            throw notExcel();
        }
    }

    // --- zip ---

    private static Map<String, byte[]> unzip(InputStream in) {
        Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean needed = name.equals("xl/workbook.xml") || name.equals("xl/sharedStrings.xml")
                    || name.equals("xl/_rels/workbook.xml.rels")
                    || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"));
                if (!needed) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = zip.read(chunk)) > 0) {
                    total += n;
                    if (total > MAX_UNZIPPED_BYTES) {
                        throw DamsException.badRequest("That Excel file is too large to read");
                    }
                    buffer.write(chunk, 0, n);
                }
                parts.put(name, buffer.toByteArray());
            }
        } catch (IOException e) {
            throw notExcel();
        }
        if (parts.isEmpty()) {
            throw notExcel();
        }
        return parts;
    }

    private static DamsException notExcel() {
        return DamsException.badRequest("That file is not a readable Excel (.xlsx) workbook");
    }

    // --- workbook parts ---

    private static String resolve(String target) {
        return target.startsWith("/") ? target.substring(1) : "xl/" + target;
    }

    private static Map<String, String> readRelationships(byte[] xml) throws XMLStreamException {
        Map<String, String> out = new LinkedHashMap<>();
        if (xml == null) {
            return out;
        }
        XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(xml));
        while (r.hasNext()) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("Relationship")) {
                out.put(r.getAttributeValue(null, "Id"), r.getAttributeValue(null, "Target"));
            }
        }
        return out;
    }

    private static List<String> readSharedStrings(byte[] xml) throws XMLStreamException {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(xml));
        StringBuilder current = null;
        boolean inPhonetic = false;
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();
                if (tag.equals("si")) {
                    current = new StringBuilder();
                } else if (tag.equals("rPh")) {
                    inPhonetic = true; // phonetic (furigana) runs are not part of the cell text
                } else if (tag.equals("t") && current != null && !inPhonetic) {
                    current.append(r.getElementText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String tag = r.getLocalName();
                if (tag.equals("rPh")) {
                    inPhonetic = false;
                } else if (tag.equals("si") && current != null) {
                    out.add(current.toString());
                    current = null;
                }
            }
        }
        return out;
    }

    // --- one sheet ---

    private static List<Row> readSheet(byte[] xml, List<String> shared) throws XMLStreamException {
        List<Row> rows = new ArrayList<>();
        XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(xml));
        int rowNumber = 0;
        Map<Integer, String> cells = null;
        int nextColumn = 0;
        String type = null;
        int column = 0;
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();
                if (tag.equals("row")) {
                    String number = r.getAttributeValue(null, "r");
                    rowNumber = number != null ? Integer.parseInt(number) : rowNumber + 1;
                    cells = new LinkedHashMap<>();
                    nextColumn = 0;
                } else if (tag.equals("c") && cells != null) {
                    String ref = r.getAttributeValue(null, "r");
                    column = ref != null ? columnIndex(ref) : nextColumn;
                    nextColumn = column + 1;
                    type = r.getAttributeValue(null, "t");
                } else if (cells != null && (tag.equals("v") || tag.equals("t"))) {
                    // <v> is the stored value; <t> is text inside an inline string (<is><t>)
                    String text = r.getElementText();
                    if (tag.equals("v") && "s".equals(type)) {
                        int index = Integer.parseInt(text.trim());
                        text = index >= 0 && index < shared.size() ? shared.get(index) : "";
                    }
                    cells.merge(column, text, String::concat);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("row") && cells != null) {
                if (!cells.isEmpty()) {
                    int width = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
                    String[] array = new String[width];
                    cells.forEach((index, text) -> array[index] = text);
                    rows.add(new Row(rowNumber, array));
                }
                cells = null;
            }
        }
        return rows;
    }

    /** "B3" -> 1, "AA10" -> 26. */
    static int columnIndex(String ref) {
        int index = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            index = index * 26 + (ch - 'A' + 1);
        }
        return index - 1;
    }

    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);                  // no DTDs at all
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false); // no XXE
        return factory;
    }
}
