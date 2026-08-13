package org.igv.data;

/**
 * A {@link LocusScore}-like bin produced by {@link AverageErrorBarDataSource}: the mean
 * (via {@link #getScore()}) plus the sample standard deviation / standard error of the
 * mean across the Average track's members. Missing member values are represented using the
 * replacement configured in the Average options and therefore remain part of N.
 */
public class AverageErrorLocusScore extends BasicScore {

    public enum Group {NONE, RAW, POSITIVE, NEGATIVE}

    private final float sd;
    private final float sem;
    private final int n;
    private final float[] memberValues;
    private final Group group;
    private final float missingValue;

    public AverageErrorLocusScore(int start, int end, float mean, float sd, float sem, int n) {
        this(start, end, mean, sd, sem, n, null);
    }

    public AverageErrorLocusScore(int start, int end, float mean, float sd, float sem, int n,
                                  float[] memberValues) {
        this(start, end, mean, sd, sem, n, memberValues, Group.NONE, 0f);
    }

    public AverageErrorLocusScore(int start, int end, float mean, float sd, float sem, int n,
                                  float[] memberValues, Group group, float missingValue) {
        super(start, end, mean);
        this.sd = sd;
        this.sem = sem;
        this.n = n;
        this.memberValues = memberValues == null ? null : memberValues.clone();
        this.group = group == null ? Group.NONE : group;
        this.missingValue = missingValue;
    }

    /** Sample standard deviation across contributing members, or NaN if n &lt; 2. */
    public float getSd() {
        return sd;
    }

    /** Standard error of the mean across contributing members, or NaN if n &lt; 2. */
    public float getSem() {
        return sem;
    }

    /** Number of member tracks that contributed a value to this bin. */
    public int getN() {
        return n;
    }

    /** Values actually used to compute this score, in Average-track member order. */
    public float[] getMemberValues() {
        return memberValues == null ? null : memberValues.clone();
    }

    public float getMemberValue(int index) {
        return memberValues == null || index < 0 || index >= memberValues.length
                ? Float.NaN : memberValues[index];
    }

    public Group getGroup() {
        return group;
    }

    public float getMissingValue() {
        return missingValue;
    }
}
