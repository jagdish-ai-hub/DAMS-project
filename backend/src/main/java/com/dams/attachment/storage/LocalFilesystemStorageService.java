package com.dams.attachment.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Dev / test storage backend: bytes go to a local directory, "signed URLs" point at this
 * app's own raw-content endpoint with a short-lived HMAC token ({@link SignedUrlTokens}).
 *
 * Active by default; {@code dams.storage.provider=r2} swaps in {@code R2StorageService}.
 */
@Service
@ConditionalOnProperty(name = "dams.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFilesystemStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemStorageService.class);
    private static final long URL_TTL_MINUTES = 10;

    private final Path root;
    private final String baseUrl;
    private final SignedUrlTokens tokens;

    public LocalFilesystemStorageService(
        @Value("${dams.storage.local.dir:${java.io.tmpdir}/dams-storage}") String dir,
        // Where the signed /api/v1/attachments/raw endpoint is reachable — this backend, NOT
        // the frontend origin. Defaults to the local server; set it in prod to the API's URL.
        @Value("${dams.storage.local.public-base-url:http://localhost:8080}") String baseUrl,
        SignedUrlTokens tokens) {
        this.root = Path.of(dir);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.tokens = tokens;
    }

    @PostConstruct
    void ensureRoot() {
        try {
            Files.createDirectories(root);
            log.info("Local attachment storage at {}", root.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create local storage directory " + root, e);
        }
    }

    @Override
    public String put(String keyHint, String contentType, byte[] content) {
        String objectKey = sanitise(keyHint) + "/" + UUID.randomUUID();
        Path target = root.resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write attachment " + objectKey, e);
        }
        return objectKey;
    }

    @Override
    public String signedUrl(String objectKey, String filename, String contentType) {
        Instant exp = Instant.now().plus(URL_TTL_MINUTES, ChronoUnit.MINUTES);
        return "%s/api/v1/attachments/raw?key=%s&exp=%d&sig=%s".formatted(
            baseUrl, tokens.encodeKey(objectKey), exp.getEpochSecond(), tokens.sign(objectKey, exp));
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(root.resolve(objectKey));
        } catch (IOException e) {
            // Best-effort — log and move on, as the contract says.
            log.warn("Could not delete local attachment {}: {}", objectKey, e.getMessage());
        }
    }

    /** Read bytes back for the raw-content endpoint (local backend only). */
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(root.resolve(objectKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read attachment " + objectKey, e);
        }
    }

    private static String sanitise(String hint) {
        String cleaned = hint == null ? "misc" : hint.replaceAll("[^A-Za-z0-9/_-]", "_");
        return cleaned.isBlank() ? "misc" : cleaned;
    }
}
