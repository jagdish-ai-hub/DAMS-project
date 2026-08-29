package com.dams.email;

/**
 * Outbound email. v1 has no SMTP provider wired — see {@link LoggingEmailService}.
 * Swapping in a real provider means adding one implementation of this interface and
 * nothing else. Keep this interface small and provider-agnostic.
 */
public interface EmailService {

    /**
     * Send (or, in v1, log) an organization-owner invitation.
     *
     * @param toEmail     the invited owner's email address
     * @param orgName     the organization they will own
     * @param inviteLink  the full accept-invite URL the recipient should open
     */
    void sendOwnerInvite(String toEmail, String orgName, String inviteLink);

    /**
     * Send (or, in v1, log) a team-member invitation created by an Owner.
     *
     * @param toEmail     the invited user's email address
     * @param orgName     the organization they are joining
     * @param roleLabel   their role, e.g. "Accountant"
     * @param inviteLink  the full accept-invite URL the recipient should open
     */
    void sendUserInvite(String toEmail, String orgName, String roleLabel, String inviteLink);
}
