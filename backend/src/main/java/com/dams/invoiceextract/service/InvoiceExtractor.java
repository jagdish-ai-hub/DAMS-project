package com.dams.invoiceextract.service;

import com.dams.invoiceextract.dto.InvoiceExtractResponse;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.Check;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.Field;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.FreeItem;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.HsnRow;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.Item;
import com.dams.invoiceextract.dto.InvoiceExtractResponse.Total;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Spares / Lube / Labour detail and the header of an Eicher dealer PDF from word positions (rev 98 test tool).
 *
 * <p>Two document families, both Adobe LiveCycle forms:
 * <ol>
 *   <li><b>Tax Invoice</b> to the customer — CGST + SGST columns, or one IGST column when the supply crosses states.</li>
 *   <li><b>Warranty Claim-Invoice</b> to the manufacturer — parts, labour (man-hours x rate), lube and sublet.</li>
 * </ol>
 *
 * <p>Rows are rebuilt from the columns, not from line text, because descriptions, part numbers and even amounts wrap
 * over several lines. Every document is then checked against the totals it prints itself (line arithmetic, section
 * totals, invoice total, HSN summary). That check is the accuracy measure: a wrong or missed row breaks a total, and the
 * result says so instead of being trusted.
 */
@Service
public class InvoiceExtractor {

    private static final Pattern MONEY = Pattern.compile("^-?[\\d,]*\\d\\.\\d\\d$");
    private static final Pattern INT = Pattern.compile("^\\d+$");
    private static final double HDR_SPLIT = 265.0;
    private static final List<String> MID_LABELS = List.of("Invoice No.", "Invoice Date", "Odometer", "Job Card No.", "Job Card Date",
        "Job Card Type", "Model", "Sale Date", "Insurance Policy", "Insurance Co.");
    private static final List<String> FAR_LABELS = List.of("Internal Ref No.", "INS Date", "Tipper Reading", "Place of Supply",
        "Vehicle Reg No.", "Payment Type", "Man JC Date", "Man JC");
    private static final Map<String, String> TITLES = Map.of("Spare Part Details", "spares", "Lube Details", "lube", "Labour Details", "labour");

    // ------------------------------------------------------------------------------------------------ entry point

    public InvoiceExtractResponse extract(PdfContent pdf, String fileName) {
        String head = pdf.layoutText().length() > 400 ? pdf.layoutText().substring(0, 400) : pdf.layoutText();
        return head.contains("Warranty Claim-Invoice") ? claim(pdf, fileName) : taxInvoice(pdf, fileName);
    }

    // ------------------------------------------------------------------------------------------------ small helpers

    private static final class Ln {
        final double y;
        final List<PdfWord> w = new ArrayList<>();
        String text;

        Ln(double y) {
            this.y = y;
        }
    }

    /** Group words into visual lines (by y), each sorted by x. */
    private static List<Ln> linesOf(List<PdfWord> words) {
        List<PdfWord> ws = new ArrayList<>(words);
        ws.sort(Comparator.comparingDouble(PdfWord::y0).thenComparingDouble(PdfWord::x0));
        List<Ln> lines = new ArrayList<>();
        for (PdfWord w : ws) {
            if (!lines.isEmpty() && Math.abs(lines.get(lines.size() - 1).y - w.y0()) <= 2.5) {
                lines.get(lines.size() - 1).w.add(w);
            } else {
                Ln l = new Ln(w.y0());
                l.w.add(w);
                lines.add(l);
            }
        }
        for (Ln l : lines) {
            l.w.sort(Comparator.comparingDouble(PdfWord::x0));
            StringBuilder sb = new StringBuilder();
            for (PdfWord w : l.w) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w.t());
            }
            l.text = sb.toString();
        }
        return lines;
    }

    private static BigDecimal num(String s) {
        if (s == null) return null;
        String t = s.replace(",", "").strip();
        return t.matches("^-?\\d+(\\.\\d+)?$") ? new BigDecimal(t) : null;
    }

    private static double nz(BigDecimal b) {
        return b == null ? 0.0 : b.doubleValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static boolean close(BigDecimal a, double b, double tol) {
        return a != null && Math.abs(a.doubleValue() - b) <= tol;
    }

    private static boolean close(double a, BigDecimal b, double tol) {
        return b != null && Math.abs(a - b.doubleValue()) <= tol;
    }

    private static int nearestAnchor(PdfWord w, List<PdfWord> anchors) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < anchors.size(); i++) {
            double d = Math.abs(anchors.get(i).y0() - w.y0());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static String joinWords(List<PdfWord> ws, String sep) {
        StringBuilder sb = new StringBuilder();
        for (PdfWord w : ws) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(w.t());
        }
        return sb.toString();
    }

    /** Words ordered the way a reader meets them inside a table cell: line by line, left to right. */
    private static void readingOrder(List<PdfWord> ws) {
        ws.sort(Comparator.comparingDouble((PdfWord w) -> Math.rint(w.y0())).thenComparingDouble(PdfWord::x0));
    }

    /** 'Invoice No. 123 Internal Ref No. 456' -> {label: value}; longest labels win. */
    private static Map<String, String> splitLabelled(String text, List<String> labels) {
        List<String> sorted = new ArrayList<>(labels);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder alt = new StringBuilder();
        for (String l : sorted) {
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(l));
        }
        Matcher m = Pattern.compile("(?:(?<=\\s)|^)(" + alt + ")(?=\\s|$)").matcher(text.strip());
        String s = text.strip();
        List<int[]> spans = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[] {m.start(), m.end()});
            names.add(m.group(1));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < spans.size(); i++) {
            int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : s.length();
            out.put(names.get(i), s.substring(spans.get(i)[1], to).strip());
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ raw rows

    /** One table row as read, before it is turned into an {@link Item}. */
    private static final class Raw {
        int page;
        Integer sno;
        String partCell = "";
        String description = "";
        boolean igstLayout;
        BigDecimal qty;
        String uom;
        BigDecimal unit;
        BigDecimal discount;
        BigDecimal handling;
        BigDecimal taxable;
        BigDecimal cgst;
        BigDecimal sgst;
        BigDecimal igst;
        BigDecimal kfc;
        BigDecimal tds;
        BigDecimal total;
        BigDecimal manHr;
        BigDecimal ratePerHr;
        String code = "";
        String hsn = "";
        BigDecimal gstRate;

        Item toItem() {
            return new Item(page, sno == null ? null : String.valueOf(sno), code, hsn, gstRate, description, qty, uom, manHr, ratePerHr,
                unit, discount, handling, taxable, cgst, sgst, igst, kfc, tds, total);
        }
    }

    // ================================================================================================ TAX INVOICE

    private InvoiceExtractResponse taxInvoice(PdfContent pdf, String fileName) {
        List<Field> header = taxHeader(pdf.pages().get(0));
        Map<String, List<Raw>> tables = new LinkedHashMap<>();
        tables.put("spares", new ArrayList<>());
        tables.put("lube", new ArrayList<>());
        tables.put("labour", new ArrayList<>());
        List<String> problems = new ArrayList<>();
        taxTables(pdf.pages(), tables, problems);

        Map<String, BigDecimal> tot = taxTotals(pdf.layoutText());
        List<HsnRow> hsn = hsnSummary(pdf.layoutText());
        List<FreeItem> free = freeItems(pdf.layoutText());
        List<Check> checks = checkTax(tables, tot, hsn);

        boolean igst = tables.values().stream().flatMap(List::stream).anyMatch(r -> r.igstLayout);
        boolean any = tables.values().stream().anyMatch(l -> !l.isEmpty());
        String label = !any ? "Tax invoice — nothing charged" : igst ? "Tax invoice (IGST)" : "Tax invoice (CGST + SGST)";

        List<Total> totals = new ArrayList<>();
        addTotal(totals, "Spares taxable amount", tot.get("spares_taxable"));
        addTotal(totals, "Spares sub total (with tax)", tot.get("spares_total"));
        addTotal(totals, "Lube taxable amount", tot.get("lube_taxable"));
        addTotal(totals, "Lube sub total (with tax)", tot.get("lube_total"));
        addTotal(totals, "Labour taxable amount", tot.get("labour_taxable"));
        addTotal(totals, "Labour sub total (with tax)", tot.get("labour_total"));
        addTotal(totals, "Invoice amount", tot.get("invoice_amount"));
        addTotal(totals, "Net payable", tot.get("net_payable"));

        return new InvoiceExtractResponse(fileName, "TAX_INVOICE", label, header, items(tables.get("spares")), items(tables.get("lube")),
            items(tables.get("labour")), free, totals, hsn, checks, problems, passed(checks), checks.size());
    }

    private static List<Item> items(List<Raw> rows) {
        List<Item> out = new ArrayList<>();
        for (Raw r : rows) out.add(r.toItem());
        return out;
    }

    private static int passed(List<Check> checks) {
        return (int) checks.stream().filter(Check::ok).count();
    }

    private static void addTotal(List<Total> list, String label, BigDecimal v) {
        if (v != null) list.add(new Total(label, v));
    }

    private static void put(List<Field> out, String label, String value) {
        if (value != null && !value.isBlank()) out.add(new Field(label, value.strip()));
    }

    private List<Field> taxHeader(List<PdfWord> pg) {
        Map<String, String> h = new LinkedHashMap<>();
        List<Ln> lines = linesOf(pg);
        double stop = 9999;
        Pattern stopP = Pattern.compile("^(?:Spare Part Details|NIL|Replaced Under)");
        for (Ln l : lines) {
            if (stopP.matcher(l.text).find()) {
                stop = l.y;
                break;
            }
        }
        List<Ln> top = new ArrayList<>();
        for (Ln l : lines) if (l.y > 250 && l.y < stop) top.add(l);
        List<double[]> midY = new ArrayList<>();
        List<String> midT = new ArrayList<>();
        for (Ln l : top) {
            StringBuilder mid = new StringBuilder();
            StringBuilder far = new StringBuilder();
            for (PdfWord w : l.w) {
                if (w.x0() >= HDR_SPLIT && w.x0() < 420) mid.append(mid.length() > 0 ? " " : "").append(w.t());
                else if (w.x0() >= 420) far.append(far.length() > 0 ? " " : "").append(w.t());
            }
            midY.add(new double[] {l.y});
            midT.add(mid.toString());
            splitLabelled(mid.toString(), MID_LABELS).forEach((k, v) -> h.put("mid:" + k, v));
            splitLabelled(far.toString(), FAR_LABELS).forEach((k, v) -> h.put("far:" + k, v));
        }
        // the model description wraps onto the next line(s), starting at the label column
        Double modelY = null;
        Double saleY = null;
        for (int i = 0; i < midT.size(); i++) {
            if (modelY == null && midT.get(i).startsWith("Model")) modelY = midY.get(i)[0];
            if (saleY == null && midT.get(i).startsWith("Sale Date")) saleY = midY.get(i)[0];
        }
        if (modelY != null && saleY != null) {
            StringBuilder extra = new StringBuilder();
            for (int i = 0; i < midT.size(); i++) {
                double y = midY.get(i)[0];
                if (y > modelY + 3 && y < saleY - 2 && !midT.get(i).isEmpty()) extra.append(extra.length() > 0 ? " " : "").append(midT.get(i));
            }
            if (extra.length() > 0) h.put("mid:Model", (h.getOrDefault("mid:Model", "") + " " + extra).strip());
        }
        // left block: customer, contact, chassis, engine, receiver GSTIN
        List<String> leftText = new ArrayList<>();
        for (Ln l : top) {
            StringBuilder sb = new StringBuilder();
            for (PdfWord w : l.w) if (w.x0() < HDR_SPLIT) sb.append(sb.length() > 0 ? " " : "").append(w.t());
            leftText.add(sb.toString());
        }
        int custIdx = -1;
        for (int i = 0; i < leftText.size(); i++) {
            if (leftText.get(i).startsWith("Customer Details")) {
                custIdx = i;
                break;
            }
        }
        if (custIdx >= 0) {
            Matcher m = Pattern.compile("Customer Details:\\s*(\\S+)").matcher(leftText.get(custIdx));
            h.put("customer_id", m.find() ? m.group(1) : "");
            double contactY = 9999;
            for (int i = 0; i < leftText.size(); i++) {
                if (leftText.get(i).startsWith("Contact No")) {
                    contactY = top.get(i).y;
                    break;
                }
            }
            List<String> body = new ArrayList<>();
            for (int i = 0; i < leftText.size(); i++) {
                String t = leftText.get(i);
                if (top.get(i).y > top.get(custIdx).y + 3 && top.get(i).y < contactY - 1 && !t.isEmpty()
                    && !t.matches("^(Contact No|Chassis No|Engine No|Receiver GST).*")) {
                    body.add(t);
                }
            }
            if (!body.isEmpty()) {
                h.put("customer_name", body.get(0));
                h.put("customer_address", String.join(" ", body.subList(1, body.size())));
            }
        }
        for (String txt : leftText) {
            if (txt.startsWith("Contact")) {
                Matcher m = Pattern.compile("^Contact No\\.\\s*(\\S*)\\s*(?:PAN No\\.\\s*(\\S*))?").matcher(txt);
                if (m.find()) {
                    String contact = m.group(1) == null ? "" : m.group(1);
                    h.put("contact_no", contact.equals("PAN") ? "" : contact);   // nothing between the two labels
                    h.put("pan_no", m.group(2) == null ? "" : m.group(2));
                }
            }
            Matcher c = Pattern.compile("^Chassis No\\.\\s*(\\S+)").matcher(txt);
            if (c.find()) h.put("chassis_no", c.group(1));
            Matcher e = Pattern.compile("^Engine No\\.\\s*(\\S+)").matcher(txt);
            if (e.find()) h.put("engine_no", e.group(1));
        }
        Double gstY = null;
        for (Ln l : top) {
            if (l.text.startsWith("Receiver GST")) {
                gstY = l.y;
                break;
            }
        }
        if (gstY != null) {
            String gstin = "";
            StringBuilder irn = new StringBuilder();
            for (Ln l : top) {
                if (l.y <= gstY + 3) continue;
                for (PdfWord w : l.w) {
                    if (w.x0() >= HDR_SPLIT) continue;
                    if (gstin.isEmpty() && w.t().matches("^[0-9]{2}[A-Z0-9]{13}$")) gstin = w.t();
                    if (w.x0() >= 140 && w.t().matches("^[0-9a-f]{16,64}$")) irn.append(w.t());
                }
            }
            h.put("receiver_gstin", gstin);
            h.put("irn", irn.toString());
        }

        List<Field> out = new ArrayList<>();
        put(out, "Invoice No.", h.get("mid:Invoice No."));
        put(out, "Invoice Date", h.get("mid:Invoice Date"));
        put(out, "Job Card No. (Eicher)", h.get("mid:Job Card No."));
        put(out, "Job Card Date", h.get("mid:Job Card Date"));
        put(out, "Job Card Type", h.get("mid:Job Card Type"));
        put(out, "Vehicle Reg No.", h.get("far:Vehicle Reg No."));
        put(out, "Chassis No.", h.get("chassis_no"));
        put(out, "Engine No.", h.get("engine_no"));
        put(out, "Model", h.get("mid:Model"));
        put(out, "Odometer", h.get("mid:Odometer"));
        put(out, "Payment Type", h.get("far:Payment Type"));
        put(out, "Place of Supply", h.get("far:Place of Supply"));
        put(out, "Customer ID", h.get("customer_id"));
        put(out, "Customer", h.get("customer_name"));
        put(out, "Address", h.get("customer_address"));
        put(out, "Contact No.", h.get("contact_no"));
        put(out, "PAN No.", h.get("pan_no"));
        put(out, "Receiver GSTIN", h.get("receiver_gstin"));
        return out;
    }

    private record Geometry(String kind, boolean igst, Double qtyX, Double uomX, Double descX) {
    }

    private void taxTables(List<List<PdfWord>> pages, Map<String, List<Raw>> out, List<String> problems) {
        Geometry carry = null;       // a table that ran into the page footer without its 'Total taxable amount' line
        String kindNow = "spares";
        for (int pi = 0; pi < pages.size(); pi++) {
            List<PdfWord> pg = pages.get(pi);
            List<Ln> lines = linesOf(pg);
            List<double[]> titleY = new ArrayList<>();
            List<String> titleKind = new ArrayList<>();
            for (Ln l : lines) {
                String k = TITLES.get(l.text);
                if (k != null && l.w.get(0).x0() < 70) {
                    titleY.add(new double[] {l.y});
                    titleKind.add(k);
                }
            }
            List<Ln> headers = new ArrayList<>();
            for (Ln l : lines) {
                if (l.w.stream().noneMatch(w -> w.t().equals("S.NO"))) continue;
                boolean freeItemsHeader = pg.stream().anyMatch(w -> w.t().equals("Item") && w.y0() >= l.y - 14 && w.y0() <= l.y + 12);
                if (!freeItemsHeader) headers.add(l);        // the "Replaced Under Warranty/AMC/FOC" list is read separately
            }
            double firstHdrY = headers.isEmpty() ? 9999 : headers.get(0).y - 14;
            List<Object[]> jobs = new ArrayList<>();         // {Geometry, topY}
            boolean anchorAboveHeader = pg.stream().anyMatch(w -> w.x0() < 24 && INT.matcher(w.t()).matches() && w.y0() > 70 && w.y0() < firstHdrY);
            if (carry != null && (headers.isEmpty() || anchorAboveHeader)) {
                jobs.add(new Object[] {carry, 70.0});
            }
            carry = null;
            for (Ln hl : headers) {
                List<PdfWord> band = new ArrayList<>();
                for (PdfWord w : pg) if (w.y0() >= hl.y - 14 && w.y0() <= hl.y + 12) band.add(w);
                List<String> toks = new ArrayList<>();
                for (PdfWord w : band) toks.add(w.t());
                String kind = kindNow;
                for (int i = 0; i < titleY.size(); i++) if (titleY.get(i)[0] < hl.y) kind = titleKind.get(i);
                if (toks.stream().anyMatch(t -> t.startsWith("Labour(SAC)"))) kind = "labour";
                boolean igst = toks.contains("IGST");
                Double qtyX = null;
                Double uomX = null;
                PdfWord descW = null;
                for (PdfWord w : band) {
                    if (qtyX == null && w.t().equals("Qty")) qtyX = w.x0();
                    if (uomX == null && w.t().equals("UoM")) uomX = w.x0();
                    if (descW == null && w.t().equals("Description")) descW = w;
                }
                Double descX = descW == null ? null : descW.x0();
                if (descW != null) {
                    Double min = null;
                    for (PdfWord w : band) {
                        if ((w.t().equals("Part") || w.t().equals("Lube") || w.t().equals("Labour"))
                            && w.x0() >= descW.x0() - 40 && w.x0() <= descW.x0() + 1) {
                            min = min == null ? w.x0() : Math.min(min, w.x0());
                        }
                    }
                    if (min != null) descX = min;
                }
                jobs.add(new Object[] {new Geometry(kind, igst, qtyX, uomX, descX), hl.y + 12});
            }
            for (Object[] job : jobs) {
                Geometry g = (Geometry) job[0];
                double topY = (Double) job[1];
                kindNow = g.kind();
                List<Double> ends = new ArrayList<>();
                double totalEnd = -1;
                for (Ln l : lines) {
                    if (l.y > topY && l.text.startsWith("Total taxable amount")) {
                        ends.add(l.y);
                        if (totalEnd < 0 || l.y < totalEnd) totalEnd = l.y;
                    } else if (l.y > topY && l.text.matches("^GST No\\..*")) {
                        ends.add(l.y);
                    }
                }
                for (Ln h : headers) if (h.y - 14 > topY) ends.add(h.y - 14);
                for (double[] ty : titleY) if (ty[0] > topY) ends.add(ty[0]);
                double endY = ends.isEmpty() ? 9999 : ends.stream().min(Double::compare).get();
                boolean endedByTotal = totalEnd >= 0 && endY == totalEnd;
                if (!endedByTotal) carry = g;
                List<PdfWord> anchors = new ArrayList<>();
                for (PdfWord w : pg) if (w.x0() < 24 && INT.matcher(w.t()).matches() && w.y0() > topY && w.y0() < endY) anchors.add(w);
                if (anchors.isEmpty()) continue;
                if (g.descX() == null) {
                    problems.add(g.kind() + " table on page " + (pi + 1) + ": the 'Description' column heading was not found");
                    continue;
                }
                List<List<PdfWord>> rows = new ArrayList<>();
                for (int i = 0; i < anchors.size(); i++) rows.add(new ArrayList<>());
                for (PdfWord w : pg) {
                    if (w.y0() > topY && w.y0() < endY && w.x0() > 20 && anchors.stream().noneMatch(a -> a == w)) {
                        rows.get(nearestAnchor(w, anchors)).add(w);
                    }
                }
                for (int ai = 0; ai < anchors.size(); ai++) {
                    out.get(g.kind()).add(taxRow(g, pi, anchors.get(ai), rows.get(ai), problems));
                }
            }
        }
    }

    private Raw taxRow(Geometry g, int pi, PdfWord anchor, List<PdfWord> rw, List<String> problems) {
        readingOrder(rw);
        double descX = g.descX();
        List<PdfWord> money = new ArrayList<>();
        for (PdfWord w : rw) {
            if (MONEY.matcher(w.t()).matches() && (g.uomX() == null || w.x0() > g.uomX() + 20) && w.x0() > descX + 20) money.add(w);
        }
        money.sort(Comparator.comparingDouble(PdfWord::x0));
        StringBuilder part = new StringBuilder();
        for (PdfWord w : rw) if (w.x0() < descX - 3) part.append(w.t());
        double descEnd = g.qtyX() != null ? g.qtyX() - 6 : money.stream().mapToDouble(PdfWord::x0).min().orElse(9999) - 3;
        List<PdfWord> descWords = new ArrayList<>();
        for (PdfWord w : rw) {
            if (w.x0() >= descX - 3 && w.x0() < descEnd && !(g.kind().equals("labour") && MONEY.matcher(w.t()).matches())) descWords.add(w);
        }
        Raw r = new Raw();
        r.page = pi + 1;
        r.sno = Integer.parseInt(anchor.t());
        r.partCell = part.toString();
        r.description = joinWords(descWords, " ");
        r.igstLayout = g.igst();
        boolean labour = g.kind().equals("labour");
        String[] names;
        if (!labour) {
            if (g.qtyX() != null && g.uomX() != null) {
                for (PdfWord w : rw) {
                    if (r.qty == null && w.x0() >= g.qtyX() - 8 && w.x0() < g.uomX() - 6 && w.t().matches("^\\d+(\\.\\d+)?$")) r.qty = num(w.t());
                    if (r.uom == null && w.x0() >= g.uomX() - 8 && w.x0() < g.uomX() + 35) r.uom = w.t();
                }
            }
            names = g.igst() ? new String[] {"unit", "discount", "taxable", "igst", "total"}
                : new String[] {"unit", "discount", "taxable", "cgst", "sgst", "kfc", "total"};
        } else {
            names = g.igst() ? new String[] {"unit", "discount", "taxable", "igst", "total"}
                : new String[] {"unit", "discount", "taxable", "cgst", "sgst", "total"};
        }
        if (money.size() != names.length) {
            problems.add(g.kind() + " row " + r.sno + " (page " + (pi + 1) + "): expected " + names.length + " amounts, found " + money.size());
        }
        for (int i = 0; i < Math.min(names.length, money.size()); i++) {
            BigDecimal v = num(money.get(i).t());
            switch (names[i]) {
                case "unit" -> r.unit = v;
                case "discount" -> r.discount = v;
                case "taxable" -> r.taxable = v;
                case "igst" -> r.igst = v;
                case "cgst" -> r.cgst = v;
                case "sgst" -> r.sgst = v;
                case "kfc" -> r.kfc = v;
                default -> r.total = v;
            }
        }
        Matcher m = Pattern.compile("^([A-Za-z0-9]+)\\((\\d+)\\)\\s*([\\d.]+)%$").matcher(r.partCell);
        if (m.matches()) {
            r.code = m.group(1);
            r.hsn = m.group(2);
            r.gstRate = num(m.group(3));
        } else {
            r.code = r.partCell;
            problems.add(g.kind() + " row " + r.sno + " (page " + (pi + 1) + "): part / HSN cell not understood: '" + r.partCell + "'");
        }
        return r;
    }

    private static Map<String, BigDecimal> taxTotals(String text) {
        Map<String, BigDecimal> t = new LinkedHashMap<>();
        String[][] pats = {
            {"spares_taxable", "Total taxable amount-Spares\\s+([\\d,]+\\.\\d\\d)"},
            {"lube_taxable", "Total taxable amount-Lubes\\s+([\\d,]+\\.\\d\\d)"},
            {"labour_taxable", "Total taxable amount-Labour\\s+([\\d,]+\\.\\d\\d)"},
            {"spares_total", "Spare Sub Total \\(with Tax\\)\\s+([\\d,]+\\.\\d\\d)"},
            {"lube_total", "Lubes Spare Sub Total \\(with Tax\\)\\s+([\\d,]+\\.\\d\\d)"},
            {"labour_total", "Labour Spare Sub Total \\(with Tax\\)\\s+([\\d,]+\\.\\d\\d)"},
            {"invoice_amount", "(?m)^\\s*Invoice Amount\\s+([\\d,]+(?:\\.\\d\\d)?)\\s*$"},
            {"net_payable", "Net Payable Amount\\s+([\\d,]+(?:\\.\\d\\d)?)"}};
        for (String[] p : pats) {
            Matcher m = Pattern.compile(p[1]).matcher(text);
            t.put(p[0], m.find() ? num(m.group(1)) : null);
        }
        return t;
    }

    private static List<HsnRow> hsnSummary(String text) {
        List<HsnRow> rows = new ArrayList<>();
        Pattern p = Pattern.compile("^(\\d{6,8})\\s+([\\d,]+\\.\\d\\d)\\s+.*?([\\d,]+\\.\\d\\d)\\s*$");
        for (String ln : text.split("\n")) {
            Matcher m = p.matcher(ln);
            if (m.matches() && !ln.contains("Rate")) rows.add(new HsnRow(m.group(1), num(m.group(2)), num(m.group(3))));
        }
        return rows;
    }

    private static List<FreeItem> freeItems(String text) {
        List<FreeItem> out = new ArrayList<>();
        Matcher seg = Pattern.compile("Replaced Under Warranty/AMC/FOC(.*?)HSN Code Summary", Pattern.DOTALL).matcher(text);
        if (!seg.find()) return out;
        Pattern p = Pattern.compile("^\\s*(\\d+)\\s+(\\S+)\\s+(.+?)\\s+¤([\\d.]+)\\s*$");
        for (String ln : seg.group(1).split("\n")) {
            Matcher m = p.matcher(ln);
            if (m.matches()) out.add(new FreeItem(Integer.parseInt(m.group(1)), m.group(2), m.group(3).strip(), num(m.group(4))));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ tax checks

    private static void add(List<Check> ck, String name, boolean ok, String detail) {
        ck.add(new Check(name, ok, detail));
    }

    private static List<Check> checkTax(Map<String, List<Raw>> tables, Map<String, BigDecimal> tot, List<HsnRow> hsn) {
        List<Check> ck = new ArrayList<>();
        for (Map.Entry<String, List<Raw>> e : tables.entrySet()) {
            String kind = e.getKey();
            List<Raw> rows = e.getValue();
            for (Raw r : rows) {
                double tax = nz(r.igst) + nz(r.cgst) + nz(r.sgst) + nz(r.kfc);
                Double expTaxable = null;
                if (r.unit != null) {
                    expTaxable = kind.equals("labour") ? round2(nz(r.unit) - nz(r.discount)) : round2(nz(r.unit) * nz(r.qty) - nz(r.discount));
                }
                String id = kind + " #" + r.sno;
                add(ck, id + " taxable = amount - discount", expTaxable != null && close(r.taxable, expTaxable, 0.02), r.taxable + " vs " + expTaxable);
                double expTotal = round2(nz(r.taxable) + tax);
                add(ck, id + " total = taxable + tax", close(r.total, expTotal, 0.03), r.total + " vs " + expTotal);
                if (r.gstRate != null && r.taxable != null) {
                    int comp = r.cgst != null || r.sgst != null ? 2 : 1;    // CGST and SGST each carry the printed rate
                    double expTax = round2(r.taxable.doubleValue() * r.gstRate.doubleValue() * comp / 100);
                    add(ck, id + " tax = rate% of taxable", Math.abs(tax - expTax) <= 0.03, round2(tax) + " vs " + expTax);
                }
            }
            if (!rows.isEmpty() || tot.get(kind + "_taxable") != null) {
                double sumTaxable = round2(rows.stream().mapToDouble(r -> nz(r.taxable)).sum());
                double sumTotal = round2(rows.stream().mapToDouble(r -> nz(r.total)).sum());
                add(ck, kind + ": sum of lines taxable = printed total", Math.abs(sumTaxable - nz(tot.get(kind + "_taxable"))) <= 0.05,
                    sumTaxable + " vs " + tot.get(kind + "_taxable"));
                add(ck, kind + ": sum of line totals = printed sub total", Math.abs(sumTotal - nz(tot.get(kind + "_total"))) <= 0.06,
                    sumTotal + " vs " + tot.get(kind + "_total"));
            }
        }
        if (tot.get("invoice_amount") != null) {
            double s = round2(nz(tot.get("spares_total")) + nz(tot.get("lube_total")) + nz(tot.get("labour_total")));
            add(ck, "invoice amount = spares + lube + labour sub totals", close(s, tot.get("invoice_amount"), 0.06), s + " vs " + tot.get("invoice_amount"));
            if (tot.get("net_payable") != null) {
                add(ck, "net payable = invoice amount (no loyalty points)", close(tot.get("net_payable"), tot.get("invoice_amount").doubleValue(), 0.01), "");
            }
        }
        Map<String, Double> byHsn = new LinkedHashMap<>();
        boolean anySpareOrLabour = !tables.get("spares").isEmpty() || !tables.get("labour").isEmpty();
        for (List<Raw> rows : tables.values()) {
            for (Raw r : rows) byHsn.merge(r.hsn, nz(r.taxable), Double::sum);
        }
        for (HsnRow s : hsn) {
            if (s.hsn().equals("998714") && !anySpareOrLabour) continue;
            double got = round2(byHsn.getOrDefault(s.hsn(), 0.0));
            add(ck, "HSN " + s.hsn() + ": summary taxable = lines", close(got, s.taxable(), 0.06), got + " vs " + s.taxable());
        }
        return ck;
    }

    // ================================================================================================ WARRANTY CLAIM

    private record Col(double lo, double hi, String name) {
    }

    private static final List<Col> PART_COLS = List.of(new Col(18, 72, "part_no"), new Col(72, 140, "description"), new Col(140, 178, "hsn"),
        new Col(178, 206, "qty"), new Col(206, 248, "rate"), new Col(248, 284, "ded"), new Col(284, 322, "handling"), new Col(322, 368, "taxable"),
        new Col(368, 408, "cgst"), new Col(408, 446, "sgst"), new Col(446, 490, "igst"), new Col(490, 530, "tds"), new Col(530, 572, "total"),
        new Col(572, 640, "job_code"));
    private static final List<Col> LABOUR_COLS = List.of(new Col(18, 64, "code"), new Col(64, 165, "description"), new Col(165, 198, "hsn"),
        new Col(198, 224, "man_hr"), new Col(224, 258, "rate_per_hr"), new Col(258, 296, "ded"), new Col(296, 340, "tds"), new Col(340, 396, "taxable"),
        new Col(396, 440, "cgst"), new Col(440, 482, "sgst"), new Col(482, 522, "igst"), new Col(522, 566, "total"), new Col(566, 640, "job_code"));

    private InvoiceExtractResponse claim(PdfContent pdf, String fileName) {
        List<Raw> parts = new ArrayList<>();
        List<Raw> labour = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        claimTables(pdf.pages(), parts, labour, problems);
        List<Field> header = claimHeader(pdf.pages().get(0));
        Map<String, BigDecimal> tot = claimTotals(pdf.layoutText());
        List<HsnRow> hsn = claimHsn(pdf.pages());
        List<Check> checks = checkClaim(parts, labour, tot, hsn);

        List<Total> totals = new ArrayList<>();
        addTotal(totals, "Parts net total", tot.get("parts_net_total"));
        addTotal(totals, "Labour net total", tot.get("labour_net_total"));
        addTotal(totals, "Lube net total", tot.get("lube_net_total"));
        addTotal(totals, "Sublet net total", tot.get("sublet_net_total"));
        addTotal(totals, "Grand total", tot.get("grand_total"));
        return new InvoiceExtractResponse(fileName, "WARRANTY_CLAIM", "Warranty claim invoice", header, items(parts), List.of(), items(labour),
            List.of(), totals, hsn, checks, problems, passed(checks), checks.size());
    }

    private void claimTables(List<List<PdfWord>> pages, List<Raw> parts, List<Raw> labour, List<String> problems) {
        for (int pi = 0; pi < pages.size(); pi++) {
            List<PdfWord> pg = pages.get(pi);
            List<Ln> lines = linesOf(pg);
            Ln rep = null;
            Ln lab = null;
            for (Ln l : lines) {
                PdfWord first = l.w.get(0);
                if (rep == null && first.t().equals("Replaced") && first.x0() < 30) rep = l;
                if (lab == null && first.t().equals("Labour") && first.x0() < 30 && l.w.stream().anyMatch(w -> w.t().equals("Man"))) lab = l;
            }
            if (rep != null) {
                final double repY = rep.y;
                double hb = pg.stream().filter(w -> w.y0() >= repY && w.y0() <= repY + 24 && w.x0() >= 320 && w.x0() <= 345 && w.t().equals("Amt"))
                    .mapToDouble(PdfWord::y0).max().orElse(repY + 17);
                double end = endOf(lines, hb, "Parts Net Total", "Labour Details", "Page ");
                List<PdfWord> anchors = new ArrayList<>();
                for (PdfWord w : pg) if (w.x0() >= 178 && w.x0() < 206 && w.t().matches("^\\d+\\.\\d\\d$") && w.y0() > hb && w.y0() < end) anchors.add(w);
                collectClaimRows(pg, pi, hb, end, anchors, PART_COLS, parts, problems, "part");
            }
            if (lab != null) {
                final double labY = lab.y;
                double hb = labY + 11;
                for (Ln l : lines) {
                    if (l.y > labY && l.y < labY + 30 && l.w.get(0).t().equals("Code") && l.w.get(0).x0() < 40) hb = Math.max(hb == labY + 11 ? 0 : hb, l.y);
                }
                double end = endOf(lines, hb, "Labour Net Total", "Page ", "Lube Details");
                List<PdfWord> anchors = new ArrayList<>();
                for (PdfWord w : pg) if (w.x0() >= 566 && w.x0() < 640 && INT.matcher(w.t()).matches() && w.y0() > hb && w.y0() < end) anchors.add(w);
                collectClaimRows(pg, pi, hb, end, anchors, LABOUR_COLS, labour, problems, "labour");
            }
        }
    }

    private static double endOf(List<Ln> lines, double after, String... starts) {
        double end = 9999;
        for (Ln l : lines) {
            if (l.y <= after) continue;
            for (String s : starts) if (l.text.startsWith(s)) end = Math.min(end, l.y);
        }
        return end;
    }

    private void collectClaimRows(List<PdfWord> pg, int pi, double hb, double end, List<PdfWord> anchors, List<Col> cols, List<Raw> out,
                                  List<String> problems, String kind) {
        if (anchors.isEmpty()) return;
        List<List<PdfWord>> rows = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) rows.add(new ArrayList<>());
        for (PdfWord w : pg) {
            if (w.y0() > hb + 3 && w.y0() < end && anchors.stream().noneMatch(a -> a == w)) rows.get(nearestAnchor(w, anchors)).add(w);
        }
        for (int i = 0; i < anchors.size(); i++) {
            List<PdfWord> cell = new ArrayList<>(rows.get(i));
            cell.add(anchors.get(i));
            Map<String, List<PdfWord>> byCol = new LinkedHashMap<>();
            for (PdfWord w : cell) {
                for (Col c : cols) {
                    if (w.x0() >= c.lo() && w.x0() < c.hi()) {
                        byCol.computeIfAbsent(c.name(), k -> new ArrayList<>()).add(w);
                        break;
                    }
                }
            }
            Map<String, String> text = new LinkedHashMap<>();
            for (Map.Entry<String, List<PdfWord>> e : byCol.entrySet()) {
                readingOrder(e.getValue());
                text.put(e.getKey(), joinWords(e.getValue(), e.getKey().equals("description") ? " " : ""));
            }
            Raw r = new Raw();
            r.page = pi + 1;
            r.igstLayout = true;
            r.code = text.getOrDefault(kind.equals("part") ? "part_no" : "code", "");
            r.description = text.getOrDefault("description", "");
            r.hsn = text.getOrDefault("hsn", "");
            r.qty = num(text.get("qty"));
            r.manHr = num(text.get("man_hr"));
            r.ratePerHr = num(text.get("rate_per_hr"));
            r.unit = num(text.get("rate"));
            r.discount = num(text.get("ded"));
            r.handling = num(text.get("handling"));
            r.taxable = num(text.get("taxable"));
            r.cgst = num(text.get("cgst"));
            r.sgst = num(text.get("sgst"));
            r.igst = num(text.get("igst"));
            r.tds = num(text.get("tds"));
            r.total = num(text.get("total"));
            String[] need = kind.equals("part") ? new String[] {"qty", "rate", "taxable", "total"} : new String[] {"man_hr", "rate_per_hr", "taxable", "total"};
            for (String n : need) {
                if (num(text.get(n)) == null) problems.add("claim " + kind + " row on page " + (pi + 1) + " (" + r.code + "): " + n + " not read");
            }
            out.add(r);
        }
    }

    private List<Field> claimHeader(List<PdfWord> pg) {
        List<Ln> lines = linesOf(pg);
        List<String> midLabels = List.of("Chassis No", "Vehicle Regn No", "Model Code", "Model Desc", "Inst.Date", "Repair Date", "Job card Cr", "Kms",
            "Job Card No", "Type of", "Supplier's GST", "Cust GST No", "Dealer PAN");
        List<String> farLabels = List.of("Invoice Dt", "Engine No", "Failure", "Claim Date", "Job Card", "Engine Hr", "Place of Supply");
        Map<String, String> h = new LinkedHashMap<>();
        for (Ln l : lines) {
            if (l.y > 440) break;
            StringBuilder mid = new StringBuilder();
            StringBuilder far = new StringBuilder();
            StringBuilder left = new StringBuilder();
            StringBuilder labelCol = new StringBuilder();
            StringBuilder valueCol = new StringBuilder();
            for (PdfWord w : l.w) {
                if (w.x0() >= 258 && w.x0() < 420) mid.append(mid.length() > 0 ? " " : "").append(w.t());
                if (w.x0() >= 420) far.append(far.length() > 0 ? " " : "").append(w.t());
                if (w.x0() < 258) left.append(left.length() > 0 ? " " : "").append(w.t());
                if (w.x0() >= 258 && w.x0() < 318) labelCol.append(labelCol.length() > 0 ? " " : "").append(w.t());
                if (w.x0() >= 318 && !w.t().equals(":")) valueCol.append(valueCol.length() > 0 ? " " : "").append(w.t());
            }
            Matcher cn = Pattern.compile("^Claim No\\s*:\\s*(\\S+)").matcher(mid.toString());
            if (l.y < 215 && cn.find()) h.put("claim_no", cn.group(1));
            if (labelCol.toString().matches("^Model\\s*Desc\\s*:?\\s*$")) {      // the model description runs on past the middle block
                h.put("mid:Model Desc", valueCol.toString());
                continue;
            }
            for (int k = 0; k < 2; k++) {
                List<String> labels = k == 0 ? midLabels : farLabels;
                String txt = k == 0 ? mid.toString() : far.toString();
                List<String> sorted = new ArrayList<>(labels);
                sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
                StringBuilder alt = new StringBuilder();
                for (String s : sorted) alt.append(alt.length() > 0 ? "|" : "").append(Pattern.quote(s));
                Matcher m = Pattern.compile("^(" + alt + ")\\s*:?\\s*(.*)$").matcher(txt);
                if (m.matches()) h.put((k == 0 ? "mid:" : "far:") + m.group(1), m.group(2).strip());
            }
            Matcher bt = Pattern.compile("^Billed To\\s*:\\s*(.*)$").matcher(left.toString());
            if (bt.matches()) h.put("billed_to", bt.group(1));
        }
        Double ownerY = null;
        for (Ln l : lines) {
            if (l.text.startsWith("Vehicle Owners Name")) {
                ownerY = l.y;
                break;
            }
        }
        if (ownerY != null) {
            List<String> left = new ArrayList<>();
            for (Ln l : lines) {
                if (l.y > ownerY + 3 && l.y < 420) {
                    StringBuilder sb = new StringBuilder();
                    for (PdfWord w : l.w) if (w.x0() < 258) sb.append(sb.length() > 0 ? " " : "").append(w.t());
                    String t = sb.toString();
                    if (!t.isEmpty() && !t.matches("^(Normal GVW|IRN No).*")) left.add(t);
                }
            }
            if (!left.isEmpty()) {
                h.put("owner_name", left.get(0));
                h.put("owner_address", String.join(" ", left.subList(1, left.size())));
            }
        }
        for (Ln l : lines) {
            Matcher m = Pattern.compile("^Customer Complaints/symptoms/Incidence of failure:(.*)$").matcher(l.text);
            if (m.matches()) h.put("complaint", m.group(1).strip());
        }
        List<Field> out = new ArrayList<>();
        put(out, "Claim No.", h.get("claim_no"));
        put(out, "Job Card No. (Eicher)", h.get("mid:Job Card No"));
        put(out, "Job Card Date", h.get("far:Job Card"));
        put(out, "Vehicle Regn No.", h.get("mid:Vehicle Regn No"));
        put(out, "Chassis No.", h.get("mid:Chassis No"));
        put(out, "Engine No.", h.get("far:Engine No"));
        put(out, "Model", h.get("mid:Model Desc"));
        put(out, "Billed To", h.get("billed_to"));
        put(out, "Vehicle Owner", h.get("owner_name"));
        put(out, "Address", h.get("owner_address"));
        put(out, "Customer GST No.", h.get("mid:Cust GST No"));
        put(out, "Supplier's GST", h.get("mid:Supplier's GST"));
        put(out, "Failure Date", h.get("far:Failure"));
        put(out, "Repair Date", h.get("mid:Repair Date"));
        put(out, "Claim Date", h.get("far:Claim Date"));
        put(out, "Kms", h.get("mid:Kms"));
        put(out, "Place of Supply", h.get("far:Place of Supply"));
        put(out, "Type of claim", h.get("mid:Type of"));
        put(out, "Complaint", h.get("complaint"));
        return out;
    }

    private static Map<String, BigDecimal> claimTotals(String text) {
        Map<String, BigDecimal> t = new LinkedHashMap<>();
        String[][] pats = {{"parts_net_total", "Parts Net Total\\s+([\\d,]+\\.\\d\\d)"}, {"labour_net_total", "Labour Net Total\\s+([\\d,]+\\.\\d\\d)"},
            {"lube_net_total", "Oil Lube Net Total\\s+([\\d,]+\\.\\d\\d)"}, {"sublet_net_total", "Sublet Net Total\\s+([\\d,]+\\.\\d\\d)"},
            {"grand_total", "Grand Total\\s+([\\d,]+\\.\\d\\d)"}};
        for (String[] p : pats) {
            Matcher m = Pattern.compile(p[1]).matcher(text);
            t.put(p[0], m.find() ? num(m.group(1)) : null);
        }
        return t;
    }

    private static List<HsnRow> claimHsn(List<List<PdfWord>> pages) {
        List<HsnRow> rows = new ArrayList<>();
        for (List<PdfWord> pg : pages) {
            for (Ln l : linesOf(pg)) {
                List<PdfWord> ws = l.w;
                if (ws.size() > 3 && ws.get(0).t().matches("^\\d{6,8}$") && ws.get(0).x0() < 60
                    && ws.subList(1, ws.size()).stream().allMatch(w -> MONEY.matcher(w.t()).matches())) {
                    rows.add(new HsnRow(ws.get(0).t(), num(ws.get(1).t()), num(ws.get(ws.size() - 1).t())));
                }
            }
        }
        return rows;
    }

    private static List<Check> checkClaim(List<Raw> parts, List<Raw> labour, Map<String, BigDecimal> tot, List<HsnRow> hsn) {
        List<Check> ck = new ArrayList<>();
        int i = 0;
        for (Raw r : parts) {
            i++;
            double base = round2(nz(r.unit) * (r.qty == null ? 1 : r.qty.doubleValue()) + nz(r.handling) - nz(r.discount));
            add(ck, "part #" + i + " taxable = rate x qty + handling - ded", close(r.taxable, base, 0.03), r.taxable + " vs " + base);
            double exp = round2(nz(r.taxable) + nz(r.cgst) + nz(r.sgst) + nz(r.igst) + nz(r.tds));
            add(ck, "part #" + i + " total = taxable + taxes + TDS", close(r.total, exp, 0.03), r.total + " vs " + exp);
        }
        i = 0;
        for (Raw r : labour) {
            i++;
            double base = round2(nz(r.manHr) * nz(r.ratePerHr));
            add(ck, "labour #" + i + " taxable = man-hr x rate", close(r.taxable, base, 0.03), r.taxable + " vs " + base);
            double exp = round2(nz(r.taxable) + nz(r.cgst) + nz(r.sgst) + nz(r.igst) + nz(r.tds));
            add(ck, "labour #" + i + " total = taxable + taxes + TDS", close(r.total, exp, 0.03), r.total + " vs " + exp);
        }
        double sp = round2(parts.stream().mapToDouble(r -> nz(r.total)).sum());
        add(ck, "parts: sum of totals = printed Parts Net Total", close(sp, tot.get("parts_net_total"), 0.05), sp + " vs " + tot.get("parts_net_total"));
        double sl = round2(labour.stream().mapToDouble(r -> nz(r.total)).sum());
        add(ck, "labour: sum of totals = printed Labour Net Total", close(sl, tot.get("labour_net_total"), 0.05), sl + " vs " + tot.get("labour_net_total"));
        double g = round2(nz(tot.get("parts_net_total")) + nz(tot.get("labour_net_total")) + nz(tot.get("lube_net_total")) + nz(tot.get("sublet_net_total")));
        add(ck, "grand total = parts + labour + lube + sublet", close(g, tot.get("grand_total"), 0.05), g + " vs " + tot.get("grand_total"));
        Map<String, Double> by = new LinkedHashMap<>();
        for (Raw r : parts) by.merge(r.hsn, nz(r.taxable), Double::sum);
        for (Raw r : labour) by.merge(r.hsn, nz(r.taxable), Double::sum);
        for (HsnRow s : hsn) {
            double got = round2(by.getOrDefault(s.hsn(), 0.0));
            add(ck, "HSN " + s.hsn() + ": summary taxable = lines", close(got, s.taxable(), 0.06), got + " vs " + s.taxable());
        }
        return ck;
    }
}
