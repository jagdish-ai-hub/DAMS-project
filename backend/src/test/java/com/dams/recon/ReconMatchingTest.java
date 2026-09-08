package com.dams.recon;

import com.dams.recon.service.ReconService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEAT-40 matching rules: UTR normalisation (last-12 alphanumerics) is what
 * makes bank-format variance disappear before comparison.
 */
class ReconMatchingTest {

    @Test
    void normaliseUtr_keepsLastTwelveAlphanumerics_upperCased() {
        assertThat(ReconService.normaliseUtr("UTR: 1234-5678-9012")).isEqualTo("123456789012");
        assertThat(ReconService.normaliseUtr("abc")).isEqualTo("ABC");
        assertThat(ReconService.normaliseUtr(null)).isEqualTo("");
        assertThat(ReconService.normaliseUtr("  0000111122223333  ")).isEqualTo("111122223333");
    }

    @Test
    void normaliseUtr_samePayment_differentBankFormats_match() {
        // Slip shows the bare UTR, the statement prefixes the channel — both
        // end in the same 12 digits. (Trailing junk after the UTR shifts the
        // window and falls back to amount+date matching instead — by design.)
        String slip = "612345678901";
        String statement = "UPI612345678901";
        assertThat(ReconService.normaliseUtr(slip)).isEqualTo(ReconService.normaliseUtr(statement));
    }
}
