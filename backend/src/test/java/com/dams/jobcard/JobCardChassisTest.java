package com.dams.jobcard;

import com.dams.jobcard.entity.JobCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** rev 68: how a typed chassis number is stored — uppercase, no spaces, blank means "not given". */
class JobCardChassisTest {

    @Test
    void uppercasesAndDropsSpaces() {
        assertThat(JobCard.normaliseChassis("  mc2 abc 123  ")).isEqualTo("MC2ABC123");
    }

    @Test
    void blankOrNullMeansNotGiven() {
        assertThat(JobCard.normaliseChassis("   ")).isNull();
        assertThat(JobCard.normaliseChassis("")).isNull();
        assertThat(JobCard.normaliseChassis(null)).isNull();
    }
}
