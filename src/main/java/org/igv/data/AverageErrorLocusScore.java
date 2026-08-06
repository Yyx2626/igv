package org.igv.data;

/**
 * A {@link LocusScore}-like bin produced by {@link AverageErrorBarDataSource}: the mean
 * (via {@link #getScore()}) plus the sample standard deviation / standard error of the
 * mean across whichever member tracks contributed a value to this bin.
 */
public class AverageErrorLocusScore extends BasicScore {

    private final float sd;
    private final float sem;
    private final int n;

    public AverageErrorLocusScore(int start, int end, float mean, float sd, float sem, int n) {
        super(start, end, mean);
        this.sd = sd;
        this.sem = sem;
        this.n = n;
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
}
