package com.dams.auth.util;

import com.dams.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Builds and parses DAMS JWTs.
 *
 * Claims carried:
 *   sub          — userId (string)
 *   orgId        — Long, null for SUPER_ADMIN
 *   role         — the ACTING role (Role enum name); equals primaryRole unless switched (rev 55)
 *   primaryRole  — the user's own role
 *   actingBranchId — Long, the one branch a switched session is scoped to (null otherwise)
 *   branchIds    — List<Long>, the ACCOUNTANT's assigned branches (empty for other roles)
 *   homeBranchId — Long, the CASHIER's single posting branch (null for other roles)
 *
 * v1 has ONE token type: a short-lived access token (default 8h). There is no refresh
 * token — on expiry the client returns to the login screen. Refresh / rotation / "remember
 * me" are a post-client-review hardening stage. See AGENT.md and plan.md rev 3.
 */
@Component
public class JwtUtil {

    private static final String CLAIM_ORG_ID         = "orgId";
    private static final String CLAIM_ROLE           = "role";
    private static final String CLAIM_PRIMARY_ROLE   = "primaryRole";
    private static final String CLAIM_ACTING_BRANCH_ID = "actingBranchId";
    private static final String CLAIM_BRANCH_IDS     = "branchIds";
    private static final String CLAIM_HOME_BRANCH_ID = "homeBranchId";

    private final SecretKey secretKey;
    private final long accessTtlMs;

    public JwtUtil(
            @Value("${dams.jwt.secret:local-dev-secret-change-in-prod-must-be-at-least-32-chars-long}") String secret,
            @Value("${dams.jwt.access-token-expiry-hours:8}") long accessTtlHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = accessTtlHours * 60 * 60 * 1000;
    }

    /** Token for a user in their own (primary) role. */
    public String generateAccessToken(Long userId, Long orgId, Role role, List<Long> branchIds, Long homeBranchId) {
        return generateAccessToken(userId, orgId, role, role, null, branchIds, homeBranchId);
    }

    /**
     * @param role           the ACTING role — this is what authorises every request
     * @param primaryRole    the user's own role; differs from {@code role} while switched
     * @param actingBranchId the one branch a switched session is scoped to (null when not switched)
     */
    public String generateAccessToken(Long userId, Long orgId, Role role, Role primaryRole,
                                      Long actingBranchId, List<Long> branchIds, Long homeBranchId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTtlMs);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_ORG_ID, orgId)
            .claim(CLAIM_ROLE, role.name())
            .claim(CLAIM_PRIMARY_ROLE, primaryRole.name())
            .claim(CLAIM_ACTING_BRANCH_ID, actingBranchId)
            .claim(CLAIM_BRANCH_IDS, branchIds)
            .claim(CLAIM_HOME_BRANCH_ID, homeBranchId)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }

    /**
     * Parses and validates a token. Throws JwtException on any problem.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public Long getOrgId(Claims claims) {
        return toLong(claims.get(CLAIM_ORG_ID));
    }

    public Long getHomeBranchId(Claims claims) {
        return toLong(claims.get(CLAIM_HOME_BRANCH_ID));
    }

    public Role getRole(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    /** The user's own role; tokens issued before role switching have no claim, so it equals {@code role}. */
    public Role getPrimaryRole(Claims claims) {
        String raw = claims.get(CLAIM_PRIMARY_ROLE, String.class);
        return raw == null ? getRole(claims) : Role.valueOf(raw);
    }

    public Long getActingBranchId(Claims claims) {
        return toLong(claims.get(CLAIM_ACTING_BRANCH_ID));
    }

    public List<Long> getBranchIds(Claims claims) {
        List<?> raw = claims.get(CLAIM_BRANCH_IDS, List.class);
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
            .map(v -> ((Number) v).longValue())
            .toList();
    }

    /**
     * Returns true if the token is structurally valid and not expired.
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }
}
