package com.dams.spareparts.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.spareparts.dto.SparePartsDtos.PermissionGrid;
import com.dams.spareparts.dto.SparePartsDtos.PermissionInfo;
import com.dams.spareparts.dto.SparePartsDtos.RoleGrid;
import com.dams.spareparts.entity.InvRolePermission;
import com.dams.spareparts.entity.SparePermission;
import com.dams.spareparts.repository.InvRolePermissionRepository;
import com.dams.user.entity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Owner-editable permission grid: what an Inventory Manager and a Store Keeper may do.
 * A cell with no row means the default in {@link SparePermission}, so a brand-new organization
 * works with no setup and the grid only stores what the Owner actually changed.
 */
@Service
public class SparePartsPermissionService {

    private static final Logger log = LoggerFactory.getLogger(SparePartsPermissionService.class);

    /** The two roles that have a grid. */
    private static final List<Role> GRID_ROLES = List.of(Role.INVENTORY_MANAGER, Role.STORE_KEEPER);

    private final InvRolePermissionRepository repo;
    private final AuditService auditService;

    public SparePartsPermissionService(InvRolePermissionRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    /** What {@code role} may do in this organization right now (stored cells over the defaults). */
    @Transactional(readOnly = true)
    public Set<SparePermission> effective(Long orgId, Role role) {
        Set<SparePermission> allowed = EnumSet.noneOf(SparePermission.class);
        if (!role.isSparePartsRole()) {
            return allowed;
        }
        Map<String, Boolean> stored = new LinkedHashMap<>();
        for (InvRolePermission row : repo.findByOrgIdAndRole(orgId, role.name())) {
            stored.put(row.getPermission(), row.isAllowed());
        }
        for (SparePermission permission : SparePermission.values()) {
            boolean value = stored.getOrDefault(permission.name(), permission.defaultFor(role));
            if (value) {
                allowed.add(permission);
            }
        }
        return allowed;
    }

    @Transactional(readOnly = true)
    public PermissionGrid grid() {
        Long orgId = TenantContext.requireOrgId();
        List<PermissionInfo> permissions = new ArrayList<>();
        for (SparePermission p : SparePermission.values()) {
            permissions.add(new PermissionInfo(p.name(), p.label()));
        }
        List<RoleGrid> roles = new ArrayList<>();
        for (Role role : GRID_ROLES) {
            Set<SparePermission> allowed = effective(orgId, role);
            Map<String, Boolean> values = new LinkedHashMap<>();
            for (SparePermission p : SparePermission.values()) {
                values.put(p.name(), allowed.contains(p));
            }
            roles.add(new RoleGrid(role.name(), label(role), values));
        }
        return new PermissionGrid(permissions, roles);
    }

    /**
     * Applies the cells sent (role -> permission -> allowed). Unknown roles or permissions are refused,
     * never ignored, so a typo cannot silently leave a permission on. Every real change is audited.
     */
    @Transactional
    public PermissionGrid update(Map<String, Map<String, Boolean>> requested, Long actorUserId) {
        Long orgId = TenantContext.requireOrgId();
        List<Map<String, Object>> changes = new ArrayList<>();

        for (Map.Entry<String, Map<String, Boolean>> roleEntry : requested.entrySet()) {
            Role role = parseGridRole(roleEntry.getKey());
            Set<SparePermission> before = effective(orgId, role);
            for (Map.Entry<String, Boolean> cell : roleEntry.getValue().entrySet()) {
                SparePermission permission = parsePermission(cell.getKey());
                boolean value = Boolean.TRUE.equals(cell.getValue());
                if (before.contains(permission) == value) {
                    continue; // already the effective value, nothing to store or audit
                }
                upsert(orgId, role, permission, value, actorUserId);
                changes.add(Map.of("role", role.name(), "permission", permission.name(), "allowed", value));
            }
        }

        if (!changes.isEmpty()) {
            auditService.recordUserEvent("SparePartsPermissions", orgId, EventType.SPARE_PERMISSIONS_CHANGED,
                actorUserId, Map.of("changes", changes));
            log.info("Spare Parts permissions changed: orgId={} userId={} changes={}", orgId, actorUserId, changes);
        }
        return grid();
    }

    private void upsert(Long orgId, Role role, SparePermission permission, boolean value, Long actorUserId) {
        InvRolePermission row = repo.findByOrgIdAndRole(orgId, role.name()).stream()
            .filter(r -> r.getPermission().equals(permission.name()))
            .findFirst()
            .orElseGet(() -> {
                InvRolePermission fresh = new InvRolePermission();
                fresh.setOrgId(orgId);
                fresh.setRole(role.name());
                fresh.setPermission(permission.name());
                return fresh;
            });
        row.setAllowed(value);
        row.setUpdatedBy(actorUserId);
        row.setUpdatedAt(Instant.now());
        repo.save(row);
    }

    private static Role parseGridRole(String name) {
        for (Role role : GRID_ROLES) {
            if (role.name().equals(name)) {
                return role;
            }
        }
        throw DamsException.badRequest("'" + name + "' is not a Spare Parts role (use INVENTORY_MANAGER or STORE_KEEPER)");
    }

    private static SparePermission parsePermission(String name) {
        try {
            return SparePermission.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw DamsException.badRequest("'" + name + "' is not a Spare Parts permission");
        }
    }

    private static String label(Role role) {
        return role == Role.INVENTORY_MANAGER ? "Inventory Manager" : "Store Keeper";
    }
}
