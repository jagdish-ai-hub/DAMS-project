package com.dams.spareparts.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SparePartsTextTest {

    @Test
    void aPartCode_isUpperCaseWithNoSpaces() {
        assertThat(SparePartsText.normalizePartCode(" ia 306245 ")).isEqualTo("IA306245");
        assertThat(SparePartsText.normalizePartCode(null)).isEmpty();
    }

    @Test
    void aVehicleNumber_isUpperCaseWithNoSpacesOrDashes() {
        assertThat(SparePartsText.normalizeVehicleNo("od 02-ab 1234")).isEqualTo("OD02AB1234");
    }

    @Test
    void aJobCardNumberExcelShowsInScientificForm_isRestoredToPlainDigits() {
        assertThat(SparePartsText.cleanNumberText("4.010541946E9")).isEqualTo("4010541946");
        assertThat(SparePartsText.cleanNumberText("4010541946")).isEqualTo("4010541946");
        assertThat(SparePartsText.cleanNumberText("7.0")).isEqualTo("7");
        assertThat(SparePartsText.cleanNumberText("0.0")).isEqualTo("0");
        assertThat(SparePartsText.cleanNumberText("  JC-77 ")).isEqualTo("JC-77");   // not a number: only trimmed
        assertThat(SparePartsText.cleanNumberText(null)).isEmpty();
    }

    @Test
    void aQuantity_mustBeAWholeNonNegativeNumber() {
        assertThat(SparePartsText.parseQuantity("7")).isEqualTo(7);
        assertThat(SparePartsText.parseQuantity("7.0")).isEqualTo(7);
        assertThat(SparePartsText.parseQuantity("0")).isZero();
        assertThatThrownBy(() -> SparePartsText.parseQuantity("2.5")).hasMessageContaining("not a whole number");
        assertThatThrownBy(() -> SparePartsText.parseQuantity("-1")).hasMessageContaining("below zero");
        assertThatThrownBy(() -> SparePartsText.parseQuantity("two")).hasMessageContaining("not a number");
        assertThatThrownBy(() -> SparePartsText.parseQuantity("99999999")).hasMessageContaining("too large");
    }

    @Test
    void aPrice_isRoundedToPaise_andCannotBeNegative() {
        assertThat(SparePartsText.parsePrice("700.0")).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(SparePartsText.parsePrice("1,250.505")).isEqualByComparingTo(new BigDecimal("1250.51"));
        assertThatThrownBy(() -> SparePartsText.parsePrice("-5")).hasMessageContaining("below zero");
        assertThatThrownBy(() -> SparePartsText.parsePrice("abc")).hasMessageContaining("not a number");
    }
}
