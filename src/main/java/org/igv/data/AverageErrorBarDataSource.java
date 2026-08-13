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
 * class's "differing WindowFunctions -> ask the user" rule) but changeable afterward via
 * the track's normal "Windowing Function" menu (see {@link #setWindowFunction}). Each
 * member's own persisted/displayed WindowFunction setting is saved and restored around
 * the fetch, never permanently changed.
 * <p>
 * {@code WindowFunction.none} is a special case, handled entirely by
 * {@link #getSummaryScoresForRangeEnvelope}: rather than one scalar "resolvedFunction"
 * value per member per bin, each member is queried for both its max and min (both exact,
 * straight from the bigwig zoom pyramid - see {@code BBFile.decodeZoomData} -
 * unlike {@code absoluteMax}, this never touches raw data or needs its own fallback
 * resolution). A member whose max is above the baseline contributes that max to a
 * "positive group" mean/SD/SEM (across members); one whose min is below the baseline
 * contributes to a "negative group" the same way. A bin whose members are all on one
 * side of the baseline gets only that one group's statistics; a bin straddling the
 * baseline gets both, exactly mirroring {@code NumericTrackBinner.binEnvelope}'s
 * per-bin classification for an ordinary "None"-windowed track.
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
        if (resolvedFunction == WindowFunction.none) {
            return getSummaryScoresForRangeEnvelope(chr, startLocation, endLocation, zoom);
        }

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
                sum += v;
                sumSq += (double) v * v;
            }

            if (present == 0) {
                continue;
            }

            result.add(buildStat(start, end, sum, sumSq, n));
        }

        return result;
    }

    /**
     * The literal, data-relative zero used to classify each member as contributing to the
     * "positive" or "negative" group in {@link #getSummaryScoresForRangeEnvelope} - not the
     * track's own (possibly manually customized) display data-range baseline, since this is
     * about the sign of the underlying signal itself, computed independently of any display
     * setting.
     */
    private static final float ENVELOPE_BASELINE = 0f;

    private List<LocusScore> getSummaryScoresForRangeEnvelope(String chr, int startLocation, int endLocation, int zoom) {
        int n = members.size();
        List<List<LocusScore>> perMemberMax = new ArrayList<>(n);
        List<List<LocusScore>> perMemberMin = new ArrayList<>(n);
        TreeSet<Integer> boundariesSet = new TreeSet<>();

        for (DataTrack member : members) {
            WindowFunction original = member.getWindowFunction();
            List<LocusScore> maxScores;
            List<LocusScore> minScores;
            try {
                member.setWindowFunction(WindowFunction.max);
                maxScores = member.getSummaryScores(chr, startLocation, endLocation, zoom).getFeatures();
                member.setWindowFunction(WindowFunction.min);
                minScores = member.getSummaryScores(chr, startLocation, endLocation, zoom).getFeatures();
            } finally {
                member.setWindowFunction(original);
            }
            if (maxScores == null) maxScores = Collections.emptyList();
            if (minScores == null) minScores = Collections.emptyList();
            perMemberMax.add(maxScores);
            perMemberMin.add(minScores);
            for (LocusScore s : maxScores) {
                boundariesSet.add(s.getStart());
                boundariesSet.add(s.getEnd());
            }
            for (LocusScore s : minScores) {
                boundariesSet.add(s.getStart());
                boundariesSet.add(s.getEnd());
            }
        }

        if (boundariesSet.isEmpty()) {
            return Collections.emptyList();
        }

        Integer[] boundaries = boundariesSet.toArray(new Integer[0]);
        int[] maxSearchIndex = new int[n];
        int[] minSearchIndex = new int[n];
        List<LocusScore> result = new ArrayList<>(boundaries.length * 2);

        for (int bb = 0; bb < boundaries.length - 1; bb++) {
            int start = boundaries[bb];
            int end = boundaries[bb + 1];

            int presentPos = 0;
            int presentNeg = 0;
            double posSum = 0, posSumSq = 0;
            double negSum = 0, negSumSq = 0;

            for (int m = 0; m < n; m++) {
                List<LocusScore> maxScores = perMemberMax.get(m);
                int maxIdx = findContains(start, end, maxScores, Math.max(maxSearchIndex[m], 0));
                maxSearchIndex[m] = maxIdx;
                float memberMax = Float.NaN;
                if (maxIdx >= 0) {
                    float raw = maxScores.get(maxIdx).getScore();
                    if (!Float.isNaN(raw)) memberMax = raw;
                }

                List<LocusScore> minScores = perMemberMin.get(m);
                int minIdx = findContains(start, end, minScores, Math.max(minSearchIndex[m], 0));
                minSearchIndex[m] = minIdx;
                float memberMin = Float.NaN;
                if (minIdx >= 0) {
                    float raw = minScores.get(minIdx).getScore();
                    if (!Float.isNaN(raw)) memberMin = raw;
                }

                boolean hasPos = !Float.isNaN(memberMax) && memberMax > ENVELOPE_BASELINE;
                float posContribution = hasPos ? memberMax : naValue;
                if (hasPos) presentPos++;
                posSum += posContribution;
                posSumSq += (double) posContribution * posContribution;

                boolean hasNeg = !Float.isNaN(memberMin) && memberMin < ENVELOPE_BASELINE;
                float negContribution = hasNeg ? memberMin : naValue;
                if (hasNeg) presentNeg++;
                negSum += negContribution;
                negSumSq += (double) negContribution * negContribution;
            }

            if (presentPos > 0) {
                result.add(buildStat(start, end, posSum, posSumSq, n));
            }
            if (presentNeg > 0) {
                result.add(buildStat(start, end, negSum, negSumSq, n));
            }
        }

        return result;
    }

    private static AverageErrorLocusScore buildStat(int start, int end, double sum, double sumSq, int n) {
        float mean = (float) (sum / n);
        float sd = Float.NaN;
        float sem = Float.NaN;
        if (n >= 2) {
            double variance = (sumSq - n * (double) mean * mean) / (n - 1);
            sd = (float) Math.sqrt(Math.max(0, variance));
            sem = (float) (sd / Math.sqrt(n));
        }
        return new AverageErrorLocusScore(start, end, mean, sd, sem, n);
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
        if (statType != null) {
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
        return Arrays.asList(WindowFunction.min, WindowFunction.mean, WindowFunction.max, WindowFunction.none);
    }

    @Override
    public void dispose() {
        for (DataTrack member : members) {
            member.unload();
        }
    }
}
