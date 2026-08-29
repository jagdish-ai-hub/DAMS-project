package com.dams.attachment.storage;

/**
 * The seam between attachment handling and wherever bytes actually live. Prod uses
 * Cloudflare R2 (S3-compatible); dev uses the local filesystem. Business logic only ever
 * sees this interface — no R2/S3 type leaks past it (AGENT.md tech stack).
 */
public interface StorageService {

    /**
     * Store {@code content} and return the opaque object key to persist on the attachment
     * row. {@code keyHint} is a human-ish path fragment (org / parent / filename) the
     * implementation may use to build the key; callers must not parse the returned key.
     */
    String put(String keyHint, String contentType, byte[] content);

    /**
     * A URL a browser can GET directly for a short window (minutes), then it expires.
     * Never a public, permanent link.
     */
    String signedUrl(String objectKey, String filename, String contentType);

    /** Best-effort delete. Does not throw if the object is already gone. */
    void delete(String objectKey);
}
