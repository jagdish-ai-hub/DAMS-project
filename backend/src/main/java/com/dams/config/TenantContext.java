package com.dams.config;

import com.dams.common.exception.DamsException;

/**
 * ThreadLocal holder for the current request's org_id.
 * Set by TenantFilter from the JWT, once per request. Cleared after the request.
 *
 * TenantFilterActivator reads this to enable the Hibernate "orgFilter" for repository calls.
 * Org-scoped services can also read it directly via {@link #requireOrgId()}.
 *
 * SUPER_ADMIN requests leave this null — their queries are intentionally cross-org.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> ORG_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setOrgId(Long orgId) {
        ORG_ID.set(orgId);
    }

    /** @return the current org_id, or null for SUPER_ADMIN / unauthenticated requests. */
    public static Long getOrgId() {
        return ORG_ID.get();
    }

    /**
     * @return the current org_id, never null.
     * @throws DamsException if no tenant is set (e.g. called on a SUPER_ADMIN request by mistake).
     */
    public static Long requireOrgId() {
        Long orgId = ORG_ID.get();
        if (orgId == null) {
            throw DamsException.forbidden("No organization in context for this request");
        }
        return orgId;
    }

    public static void clear() {
        ORG_ID.remove();
    }
}
