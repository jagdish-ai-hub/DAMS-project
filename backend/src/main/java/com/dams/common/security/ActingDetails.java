package com.dams.common.security;

import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * What the JWT says about a switched role (plan.md rev 55). The token's {@code role} claim is the
 * ACTING role (it becomes the Spring authority), so this carries the user's own {@code primaryRole}
 * and the one branch the session is scoped to. {@code actingBranchId} is null when the user is in
 * their own role.
 *
 * The static helpers are what the posting guards call instead of {@code me.getRole()} /
 * {@code me.getHomeBranchId()}: while switched they answer from the token, otherwise they fall
 * back to the stored user — so a user who never switches behaves exactly as before.
 */
public record ActingDetails(Role primaryRole, Long actingBranchId) {

    /** The current request's details, or null when unauthenticated / not a JWT request. */
    public static ActingDetails current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof ActingDetails d ? d : null;
    }

    /** True when the token's acting role differs from the user's primary role. */
    public boolean isSwitched(Role actingRole) {
        return primaryRole != null && primaryRole != actingRole;
    }

    /** The role the caller is acting in right now, or null when there is no authenticated caller. */
    public static Role actingRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            return null;
        }
        return Role.valueOf(auth.getAuthorities().iterator().next().getAuthority());
    }

    /** True while the caller is in a role other than their own. */
    public static boolean isActing() {
        Role acting = actingRole();
        ActingDetails d = current();
        return acting != null && d != null && d.isSwitched(acting);
    }

    /** The branch a switched session is scoped to; null when not switched. */
    public static Long actingBranch() {
        return isActing() ? current().actingBranchId() : null;
    }

    /** The role {@code me} is acting in: the token's acting role while switched, else their own. */
    public static Role effectiveRole(AppUser me) {
        return isActing() ? actingRole() : me.getRole();
    }

    /**
     * The branch a cashier's documents post under: the picked branch while acting as Cashier,
     * otherwise the user's home branch.
     */
    public static Long effectiveHomeBranch(AppUser me) {
        return isActing() && actingRole() == Role.CASHIER ? current().actingBranchId() : me.getHomeBranchId();
    }
}
