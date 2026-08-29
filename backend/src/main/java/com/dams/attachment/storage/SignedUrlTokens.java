package com.dams.attachment.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signer for the local-filesystem storage backend's "signed URLs". A URL carries
 * {@code key}, {@code exp} (epoch seconds) and {@code sig}; the raw-content endpoint accepts
 * it only while the signature matches and {@code exp} is in the future. Same idea as an S3
 * presigned URL, so dev behaves like prod (short-lived, unguessable, no bearer header needed).
 *
 * Not used when {@code dams.storage.provider=r2} — R2 issues its own presigned URLs.
 */
@Component
public class SignedUrlTokens {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;

    public SignedUrlTokens(@Value("${dams.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String objectKey, Instant expiry) {
        long exp = expiry.getEpochSecond();
        return HexFormat.of().formatHex(mac(objectKey + "\n" + exp));
    }

    public boolean isValid(String objectKey, long exp, String signature) {
        if (Instant.now().getEpochSecond() > exp) {
            return false;
        }
        String expected = HexFormat.of().formatHex(mac(objectKey + "\n" + exp));
        return constantTimeEquals(expected, signature);
    }

    /** URL-safe encoding of the object key so it survives a query string cleanly. */
    public String encodeKey(String objectKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectKey.getBytes(StandardCharsets.UTF_8));
    }

    public String decodeKey(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private byte[] mac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute signed-URL HMAC", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
