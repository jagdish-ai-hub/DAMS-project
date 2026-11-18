package com.dams.invoiceextract.service;

import com.dams.common.exception.DamsException;
import com.dams.invoiceextract.dto.InvoiceExtractResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Finance Manager's "Extract invoice" test tool (rev 98): read an uploaded Eicher dealer PDF and return what was found.
 * Saves nothing — the file lives in memory and a temp file that is deleted, and only counts are logged, never content.
 */
@Service
public class InvoiceExtractService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractService.class);
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_PAGES = 30;

    private final PdfTextReader reader;
    private final InvoiceExtractor extractor;

    public InvoiceExtractService(PdfTextReader reader, InvoiceExtractor extractor) {
        this.reader = reader;
        this.extractor = extractor;
    }

    public InvoiceExtractResponse extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw DamsException.badRequest("Choose a PDF file to read.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw DamsException.badRequest("This file is larger than 5 MB — invoices are much smaller than that.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw DamsException.badRequest("The upload could not be read — try again.");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            throw DamsException.badRequest("This is not a PDF file.");
        }
        PdfContent content = reader.read(bytes);
        if (content.pages().size() > MAX_PAGES) {
            throw DamsException.badRequest("This PDF has " + content.pages().size() + " pages — an invoice has a handful.");
        }
        String name = file.getOriginalFilename() == null ? "invoice.pdf" : file.getOriginalFilename();
        InvoiceExtractResponse r = extractor.extract(content, name);
        log.info("Invoice extract (test tool): type={} pages={} spares={} lube={} labour={} checks={}/{}", r.documentType(), content.pages().size(),
            r.spares().size(), r.lube().size(), r.labour().size(), r.checksPassed(), r.checksTotal());
        return r;
    }
}
