package com.dams.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * v1 email stub: writes the invite link to the log instead of sending mail.
 * The Super Admin copies the link from the log (or from the create-org API response,
 * which also returns it) and passes it to the new owner manually.
 *
 * Replace with a real provider implementation post client sign-off — this class is the
 * only thing that changes.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendOwnerInvite(String toEmail, String orgName, String inviteLink) {
        log.info("""

            ================ OWNER INVITE (no email sent — v1 stub) ================
             organization : {}
             to           : {}
             invite link  : {}
            =======================================================================
            """, orgName, toEmail, inviteLink);
    }

    @Override
    public void sendUserInvite(String toEmail, String orgName, String roleLabel, String inviteLink) {
        log.info("""

            ================ TEAM INVITE (no email sent — v1 stub) ================
             organization : {}
             role         : {}
             to           : {}
             invite link  : {}
            =====================================================================
            """, orgName, roleLabel, toEmail, inviteLink);
    }
}
