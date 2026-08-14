package org.igv.data;

/** Numerically stable mean, sample-SD, and SEM calculations for Average tracks. */
public final class AverageErrorStatistics {

    private AverageErrorStatistics() {
    }

    /** Calculates statistics while ignoring NaN values. */
    public static float[] calculate(float[] values) {
        return calculate(values, 0f, false);
    }

    /** Calculates statistics after replacing NaN values with {@code missingValue}. */
    public static float[] calculateReplacingNaN(float[] values, float missingValue) {
        return calculate(values, missingValue, true);
    }

    private static float[] calculate(float[] values, float missingValue, boolean replaceNaN) {
        if (values == null || values.length == 0) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }

        int n = 0;
        double mean = 0;
        double sumSquaredDeviation = 0;
        boolean allNonnegative = true;
        boolean allNonpositive = true;

        // Welford's algorithm avoids the cancellation in sum(x^2) - n*mean^2 that
        // previously left a tiny false SEM at otherwise exact zero boundaries.
        for (float rawValue : values) {
            if (Float.isNaN(rawValue) && !replaceNaN) continue;
            float value = Float.isNaN(rawValue) ? missingValue : rawValue;
            n++;
            double delta = value - mean;
            mean += delta / n;
            sumSquaredDeviation += delta * (value - mean);
            allNonnegative &= value >= 0;
            allNonpositive &= value <= 0;
        }

        if (n == 0) return new float[]{Float.NaN, Float.NaN, Float.NaN};
        float meanFloat = (float) mean;
        if (n < 2) return new float[]{meanFloat, Float.NaN, Float.NaN};

        float sd = (float) Math.sqrt(Math.max(0, sumSquaredDeviation / (n - 1)));
        float sem = (float) (sd / Math.sqrt(n));

        // For a one-signed sample, SEM cannot extend the mean across zero. Floating-point
        // rounding can nevertheless make SEM one ULP larger than |mean| (for example,
        // [0, 0, x]), exposing values such as +/-4.7683716E-7 in an autoscaled range.
        if (allNonnegative && meanFloat >= 0 && sem > meanFloat) sem = meanFloat;
        if (allNonpositive && meanFloat <= 0 && sem > -meanFloat) sem = -meanFloat;

        return new float[]{meanFloat, sd, sem};
    }
}
