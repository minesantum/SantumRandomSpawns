package com.donkolia.santumrandomspawns.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents either a fixed integer value (e.g. "5") or an inclusive range (e.g. "1-5").
 */
public final class IntRange {

    private final int min;
    private final int max;

    public IntRange(int min, int max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    public static IntRange parse(Object raw, int defaultValue) {
        if (raw == null) {
            return new IntRange(defaultValue, defaultValue);
        }
        if (raw instanceof Number num) {
            int val = Math.max(0, num.intValue());
            return new IntRange(val, val);
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return new IntRange(defaultValue, defaultValue);
        }
        int dashIndex = text.indexOf('-');
        if (dashIndex > 0 && dashIndex < text.length() - 1) {
            try {
                int a = Integer.parseInt(text.substring(0, dashIndex).trim());
                int b = Integer.parseInt(text.substring(dashIndex + 1).trim());
                return new IntRange(Math.max(0, a), Math.max(0, b));
            } catch (NumberFormatException ignored) {
                return new IntRange(defaultValue, defaultValue);
            }
        }
        try {
            int val = Math.max(0, Integer.parseInt(text));
            return new IntRange(val, val);
        } catch (NumberFormatException ignored) {
            return new IntRange(defaultValue, defaultValue);
        }
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public int random() {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @Override
    public String toString() {
        return min == max ? String.valueOf(min) : (min + "-" + max);
    }
}
