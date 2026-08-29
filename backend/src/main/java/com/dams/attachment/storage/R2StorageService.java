package com.dams.attachment.storage;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Cloudflare R2 backend (S3-compatible), used when {@code dams.storage.provider=r2}. Bytes
 * go to an R2 bucket; retrieval is via a short-lived presigned GET URL (never a public
 * link). All R2/S3 specifics stay inside this class — callers only see {@link StorageService}.
 *
 * Config ({@code application-prod.yml} / env):
 *   dams.storage.r2.endpoint           https://&lt;accountid&gt;.r2.cloudflarestorage.com
 *   dams.storage.r2.access-key-id
 *   dams.storage.r2.secret-access-key
 *   dams.storage.r2.bucket
 */
@Service
@ConditionalOnProperty(name = "dams.storage.provider", havingValue = "r2")
public class R2StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);
    private static final Duration URL_TTL = Duration.ofMinutes(10);

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;

    public R2StorageService(
        @Value("${dams.storage.r2.endpoint}") String endpoint,
        @Value("${dams.storage.r2.access-key-id}") String accessKeyId,
        @Value("${dams.storage.r2.secret-access-key}") String secretAccessKey,
        @Value("${dams.storage.r2.bucket}") String bucket) {

        this.bucket = bucket;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        URI uri = URI.create(endpoint);
        // R2 ignores the region but the SDK requires one; path-style avoids vhost/DNS issues
        // against the account endpoint.
        S3Configuration cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.s3 = S3Client.builder()
            .endpointOverride(uri)
            .region(Region.of("auto"))
            .credentialsProvider(credentials)
            .serviceConfiguration(cfg)
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(uri)
            .region(Region.of("auto"))
            .credentialsProvider(credentials)
            .serviceConfiguration(cfg)
            .build();

        log.info("R2 storage backend active (bucket={})", bucket);
    }

    @Override
    public String put(String keyHint, String contentType, byte[] content) {
        String objectKey = sanitise(keyHint) + "/" + UUID.randomUUID();
        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build(),
            RequestBody.fromBytes(content));
        return objectKey;
    }

    @Override
    public String signedUrl(String objectKey, String filename, String contentType) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .responseContentType(contentType)
            .responseContentDisposition("inline; filename=\"" + filename.replace("\"", "") + "\"")
            .build();
        return presigner.presignGetObject(
                GetObjectPresignRequest.builder().signatureDuration(URL_TTL).getObjectRequest(get).build())
            .url()
            .toString();
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (NoSuchKeyException alreadyGone) {
            // Best-effort — nothing to do.
        } catch (RuntimeException e) {
            log.warn("Could not delete R2 object {}: {}", objectKey, e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        s3.close();
        presigner.close();
    }

    private static String sanitise(String hint) {
        String cleaned = hint == null ? "misc" : hint.replaceAll("[^A-Za-z0-9/_-]", "_");
        return cleaned.isBlank() ? "misc" : cleaned;
    }
}
