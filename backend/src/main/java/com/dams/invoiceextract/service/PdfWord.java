package com.dams.invoiceextract.service;

/** One word of a PDF page with its position (PDF points, origin top-left), as printed by {@code pdftotext -bbox}. */
public record PdfWord(String t, double x0, double y0, double x1, double y1) {
}
