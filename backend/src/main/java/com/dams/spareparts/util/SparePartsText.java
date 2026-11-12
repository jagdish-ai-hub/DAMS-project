package com.dams.spareparts.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Small, plain text and number clean-ups for Spare Parts input (typed or from Excel).
 * Excel stores a 10-digit job card number as the number 4010541946 and shows it as 4.01E+09, so any
 * number that arrives as text goes through {@link #cleanNumberText} before it is stored or compared.
 */
public final class SparePartsText {

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

    private SparePartsText() {
    }

    /** Part codes are compared upper-case with no spaces: "ia 306245" and "IA306245" are one part. */
    public static String normalizePartCode(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase();
    }

    public static String normalizeBranchCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    /** A vehicle number is upper-case with no spaces, the same rule DAMS uses everywhere. */
    public static String normalizeVehicleNo(String raw) {
        return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    /**
     * Trims, and turns a number that arrived as text ("4.010541946E9", "7.0") into plain digits
     * ("4010541946", "7"). Anything that is not a number is returned trimmed and unchanged.
     */
    public static String cleanNumberText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!NUMBER.matcher(text).matches()) {
            return text;
        }
        BigDecimal value = new BigDecimal(text);
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /** True when the text is empty after trimming. */
    public static boolean isBlank(String raw) {
        return raw == null || raw.trim().isEmpty();
    }

    /**
     * Reads a whole, non-negative quantity such as "7" or "7.0" (Excel writes whole numbers both ways).
     *
     * @throws IllegalArgumentException with a message fit to show next to the row
     */
    public static int parseQuantity(String raw) {
        String text = cleanNumberText(raw);
        if (!NUMBER.matcher(text).matches()) {
            throw new IllegalArgumentException("Quantity '" + raw.trim() + "' is not a number");
        }
        BigDecimal value = new BigDecimal(text);
        if (value.scale() > 0) {
            throw new IllegalArgumentException("Quantity " + text + " is not a whole number");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Quantity " + text + " is below zero");
        }
        if (value.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw new IllegalArgumentException("Quantity " + text + " is too large");
        }
        return value.intValueExact();
    }

    /**
     * Reads a unit price in rupees, rounded to paise.
     *
     * @throws IllegalArgumentException with a message fit to show next to the row
     */
    public static BigDecimal parsePrice(String raw) {
        String text = raw == null ? "" : raw.trim().replace(",", "");
        if (!NUMBER.matcher(text).matches()) {
            throw new IllegalArgumentException("Price '" + (raw == null ? "" : raw.trim()) + "' is not a number");
        }
        BigDecimal value = new BigDecimal(text);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Price " + text + " is below zero");
        }
        if (value.compareTo(BigDecimal.valueOf(100_000_000)) > 0) {
            throw new IllegalArgumentException("Price " + text + " is too large");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
