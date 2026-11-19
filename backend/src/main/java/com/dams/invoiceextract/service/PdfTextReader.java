package com.dams.invoiceextract.service;

import com.dams.common.exception.DamsException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a PDF's words and positions with poppler's {@code pdftotext} (no OCR, no network, no extra Java library).
 * The PDF goes to a temp file that is always deleted; the binary is run directly (never through a shell) with a time limit.
 * These dealer PDFs are Adobe LiveCycle forms, so they carry real text and every word has a position.
 */
@Component
public class PdfTextReader {

    private static final long TIMEOUT_SECONDS = 25;
    private static final long MAX_OUTPUT_BYTES = 25L * 1024 * 1024;
    private static final Pattern PAGE = Pattern.compile("<page\\b");
    private static final Pattern WORD = Pattern.compile(
        "<word xMin=\"([\\d.]+)\" yMin=\"([\\d.]+)\" xMax=\"([\\d.]+)\" yMax=\"([\\d.]+)\">(.*?)</word>");

    private final String pdftotext;

    public PdfTextReader() {
        this("pdftotext");
    }

    public PdfTextReader(String pdftotext) {
        this.pdftotext = pdftotext;
    }

    public PdfContent read(byte[] pdf) {
        Path file = null;
        Path bbox = null;
        Path layout = null;
        try {
            file = Files.createTempFile("invoice-extract-", ".pdf");
            bbox = Files.createTempFile("invoice-extract-", ".bbox");
            layout = Files.createTempFile("invoice-extract-", ".txt");
            Files.write(file, pdf);
            run(List.of(pdftotext, "-bbox", file.toString(), bbox.toString()));
            run(List.of(pdftotext, "-layout", file.toString(), layout.toString()));
            requireSize(bbox);
            requireSize(layout);
            List<List<PdfWord>> pages = parseBbox(Files.readString(bbox, StandardCharsets.UTF_8));
            if (pages.isEmpty() || pages.stream().allMatch(List::isEmpty)) {
                throw DamsException.badRequest("No text could be read from this PDF — it may be a scanned image. "
                    + "This tool reads PDFs that carry real text, like the Eicher dealer invoices.");
            }
            return new PdfContent(pages, Files.readString(layout, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DamsException(HttpStatus.SERVICE_UNAVAILABLE,
                "The PDF reader (poppler-utils / pdftotext) is not available on this server: " + e.getMessage());
        } finally {
            delete(file);
            delete(bbox);
            delete(layout);
        }
    }

    private void run(List<String> command) throws IOException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw DamsException.badRequest("Reading this PDF took too long — it was stopped.");
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw DamsException.badRequest("Reading this PDF was interrupted.");
        }
        if (p.exitValue() != 0) {
            throw DamsException.badRequest("This file could not be read as a PDF (it may be damaged or password-protected).");
        }
    }

    private static void requireSize(Path p) throws IOException {
        if (Files.size(p) > MAX_OUTPUT_BYTES) {
            throw DamsException.badRequest("This PDF is too large to read here.");
        }
    }

    private static void delete(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // a leftover temp file is harmless; the OS temp cleaner takes it
            }
        }
    }

    /** Parse {@code pdftotext -bbox} output into pages of words. Package-visible so tests can feed it a sanitised fixture. */
    public static List<List<PdfWord>> parseBbox(String html) {
        List<List<PdfWord>> pages = new ArrayList<>();
        List<PdfWord> cur = null;
        for (String raw : html.split("\n")) {
            String line = raw.strip();
            if (PAGE.matcher(line).find() && line.startsWith("<page")) {
                cur = new ArrayList<>();
                pages.add(cur);
                continue;
            }
            Matcher m = WORD.matcher(line);
            if (cur != null && m.matches()) {
                cur.add(new PdfWord(HtmlUtils.htmlUnescape(m.group(5)), Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                    Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4))));
            }
        }
        return pages;
    }
}
