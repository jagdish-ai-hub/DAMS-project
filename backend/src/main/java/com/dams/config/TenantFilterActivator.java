package com.dams.config;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate "orgFilter" (defined on {@link com.dams.user.entity.AppUser})
 * on the current session before every repository call, using the org_id that
 * {@link TenantFilter} put in {@link TenantContext} for this request.
 *
 * This is the single enforcement seam for multi-tenancy at the persistence layer.
 * Tenant-scoped entities opt in with {@code @Filter(name = "orgFilter", condition = "org_id = :orgId")}
 * starting in Stage 3 (Customer, Vehicle, …). Until an entity opts in, enabling the filter
 * is a harmless no-op — but the wiring is real and covered by tests.
 *
 * Runs at LOWEST_PRECEDENCE so it executes inside Spring Data's transaction (a Session exists),
 * before every method on every Spring Data repository. Enabling the filter for a repository
 * whose entity does not use it (organization, app_user) is a harmless no-op.
 * SUPER_ADMIN and unauthenticated requests have no org_id in context and are skipped.
 *
 * Note: a Hibernate @Filter still does NOT apply to EntityManager.find() by primary key —
 * that is why services read single rows via findByIdAndOrgId(...), not bare findById.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterActivator {

    static final String FILTER_NAME = "orgFilter";
    static final String PARAM_ORG_ID = "orgId";

    private static final Logger log = LoggerFactory.getLogger(TenantFilterActivator.class);

    private final EntityManager entityManager;

    public TenantFilterActivator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("execution(* org.springframework.data.repository.Repository+.*(..))")
    public void enableOrgFilter() {
        Long orgId = TenantContext.getOrgId();
        if (orgId == null) {
            return; // SUPER_ADMIN or unauthenticated — no tenant scoping
        }

        try {
            Session session = entityManager.unwrap(Session.class);
            Filter filter = session.getEnabledFilter(FILTER_NAME);
            if (filter == null) {
                filter = session.enableFilter(FILTER_NAME);
            }
            filter.setParameter(PARAM_ORG_ID, orgId);
        } catch (RuntimeException e) {
            // No active Hibernate session (call made outside a transaction). Nothing tenant-scoped
            // can be read here anyway; a real query would open its own transaction and re-trigger this.
            log.trace("orgFilter not enabled — no active session for this repository call: {}", e.getMessage());
        }
    }
}
