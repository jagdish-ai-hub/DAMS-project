package com.dams.invoiceextract.service;

import java.util.List;

/** The readable content of a PDF: every page's words with positions, and the layout text (for the printed totals). */
public record PdfContent(List<List<PdfWord>> pages, String layoutText) {
}
