package com.dams.common.time;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The single fixed organization timezone. Every date-sensitive boundary — document-number
 * month key, My Entries "today", the cash drawer day (Stage 6), day-close — is computed in
 * this zone, never the server's default (plan.md "Today boundary").
 */
public final class OrgTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private OrgTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
