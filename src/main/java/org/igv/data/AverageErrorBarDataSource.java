package org.igv.data;

import org.igv.feature.LocusScore;
import org.igv.track.DataTrack;
import org.igv.track.DataType;
import org.igv.track.WindowFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Data source for {@code AverageErrorBarTrack}: for each aligned genomic bin, computes
 * the mean, sample standard deviation, and standard error of the mean across every
 * member track. A member with no data covering a given bin (a gap/"NA") is zero-filled
 * with {@link #naValue} (default 0, user-configurable via
 * {@code AverageErrorBarOptionsDialog}) rather than excluded - excluding it instead
 * shrinks the effective sample size for that bin alone (down to n=1 whenever only one
 * member has a gap there), which silently drops the error bar and skews the mean toward
 * whichever member happened to have data.
 * <p>
 * Modeled on {@link CombinedDataSource}'s boundary-union technique, generalized from 2
 * to N member tracks. Each member is queried using a single {@code resolvedFunction}
 * windowing function - initially resolved by {@code AverageErrorBarMenuAction} (see that
 * class for the "differing WindowFunctions -> ask the user" / "None -> Max" rules) but
 * changeable afterward via the track's normal "Windowing Function" menu (see
 * {@link #setWindowFunction}). Each member's own persisted/displayed WindowFunction
 * setting is saved and restored around the fetch, never permanently changed.
 */
public class AverageErrorBarDataSource implements DataSource {

    private final List<DataTrack> members;
    private WindowFunction resolvedFunction;
    private float naValue;

    public AverageErrorBarDataSource(List<DataTrack> members, WindowFunction resolvedFunction) {
        this(members, resolvedFunction, 0f);
    }

    public AverageErrorBarDataSource(List<DataTrack> members, WindowFunction resolvedFunction, float naValue) {
        this.members = members;
        this.resolvedFunction = resolvedFunction;
        this.naValue = naValue;
    }

    public List<DataTrack> getMembers() {
        return members;
    }

    public WindowFunction getResolvedFunction() {
        return resolvedFunction;
    }

    public float getNaValue() {
        return naValue;
    }

    public void setNaValue(float naValue) {
        this.naValue = naValue;
    }

    @Override
    public List<LocusScore> getSummaryScoresForRange(String chr, int startLocation, int endLocation, int zoom) {

        int n = members.size();
        List<List<LocusScore>> perMember = new ArrayList<>(n);
        TreeSet<Integer> boundariesSet = new TreeSet<>();

        for (DataTrack member : members) {
            WindowFunction original = member.getWindowFunction();
            member.setWindowFunction(resolvedFunction);
            List<LocusScore> scores;
            try {
                scores = member.getSummaryScores(chr, startLocation, endLocation, zoom).getFeatures();
            } finally {
                member.setWindowFunction(original);
            }
            if (scores == null) {
                scores = Collections.emptyList();
            }
            perMember.add(scores);
            for (LocusScore s : scores) {
                boundariesSet.add(s.getStart());
                boundariesSet.add(s.getEnd());
            }
        }

        if (boundariesSet.isEmpty()) {
            return Collections.emptyList();
        }

        Integer[] boundaries = boundariesSet.toArray(new Integer[0]);
        int[] searchIndex = new int[n];
        List<LocusScore> result = new ArrayList<>(boundaries.length);

        for (int bb = 0; bb < boundaries.length - 1; bb++) {
            int start = boundaries[bb];
            int end = boundaries[bb + 1];

            int present = 0; // members with an actual (non-NaN) score covering this bin
            float[] memberValues = new float[n]; // missing members are zero-filled, not excluded
            double sum = 0;
            double sumSq = 0;

            for (int m = 0; m < n; m++) {
                List<LocusScore> scores = perMember.get(m);
                int idx = findContains(start, end, scores, Math.max(searchIndex[m], 0));
                searchIndex[m] = idx;
                float v = naValue;
                if (idx >= 0) {
                    float raw = scores.get(idx).getScore();
                    if (!Float.isNaN(raw)) {
                        v = raw;
                        present++;
                    }
                }
                memberValues[m] = v;
                sum += v;
                sumSq += (double) v * v;
            }

            if (present == 0) {
                continue;
            }

            float mean = (float) (sum / n);
            float sd = Float.NaN;
            float sem = Float.NaN;
            if (n >= 2) {
                double variance = (sumSq - n * (double) mean * mean) / (n - 1);
                sd = (float) Math.sqrt(Math.max(0, variance));
                sem = (float) (sd / Math.sqrt(n));
            }

            result.add(new AverageErrorLocusScore(start, end, mean, sd, sem, n));
        }

        return result;
    }

    /**
     * Search {@code scoresList} (sorted by start) for the score overlapping [start, end),
     * starting from {@code startIndex}. Since {@code start}/{@code end} come from the
     * boundary-union of every member's own breakpoints, a well-formed (non-overlapping,
     * consecutive) member score set should never have a boundary strictly inside this
     * sub-interval - so "overlaps" and "fully contains" are equivalent for well-formed
     * input, but overlap is used here (rather than requiring exact containment, as
     * {@code CombinedDataSource.findContains} does) to stay robust against boundary
     * rounding differences between members (e.g. different bigWig zoom-pyramid
     * resolutions at different zoom levels).
     */
    private int findContains(int start, int end, List<LocusScore> scoresList, int startIndex) {
        for (int ii = startIndex; ii < scoresList.size(); ii++) {
            LocusScore score = scoresList.get(ii);
            if (score.getStart() < end && score.getEnd() > start) {
                return ii;
            } else if (score.getStart() >= end) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public double getDataMax() {
        return 0;
    }

    @Override
    public double getDataMin() {
        return 0;
    }

    @Override
    public DataType getDataType() {
        return DataType.PLUGIN;
    }

    /**
     * Changes the aggregation function applied to every member track when computing this
     * average (via the track's normal "Windowing Function" menu) - unlike
     * {@code CombinedDataSource}, this IS the meaningful "statistic" control for an
     * average track, so it's fully mutable, not a no-op.
     */
    @Override
    public void setWindowFunction(WindowFunction statType) {
        if (statType != null && statType != WindowFunction.none) {
            this.resolvedFunction = statType;
        }
    }

    @Override
    public boolean isLogNormalized() {
        return false;
    }

    @Override
    public WindowFunction getWindowFunction() {
        return resolvedFunction;
    }

    @Override
    public Collection<WindowFunction> getAvailableWindowFunctions() {
        return Arrays.asList(WindowFunction.min, WindowFunction.mean, WindowFunction.max, WindowFunction.absoluteMax);
    }

    @Override
    public void dispose() {
        for (DataTrack member : members) {
            member.unload();
        }
    }
}
