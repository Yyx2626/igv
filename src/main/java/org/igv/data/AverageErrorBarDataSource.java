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
 * {@link #getSummaryScoresForRangeEnvelope}: every member is queried with None so the
 * calculation uses raw values, just like an ordinary None-windowed track. A zoom-pyramid
 * max/min record cannot be used here because its value can originate outside the requested
 * display/export bin even when the record overlaps that bin. This source aligns members at
 * raw boundaries and preserves those raw member values; {@code NumericTrackBinner} then takes
 * each member's non-negative maximum and/or non-positive minimum within the actual display bin
 * before computing the two possible Average groups.
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
            float[] memberValues = new float[n];

            for (int m = 0; m < n; m++) {
                List<LocusScore> scores = perMember.get(m);
                int idx = findCandidate(start, scores, searchIndex[m]);
                searchIndex[m] = idx;
                float v = naValue;
                if (idx < scores.size() && scores.get(idx).getStart() < end) {
                    float raw = scores.get(idx).getScore();
                    if (!Float.isNaN(raw)) {
                        v = raw;
                        present++;
                    }
                }
                memberValues[m] = v;
            }

            if (present == 0) {
                continue;
            }

            result.add(buildStat(start, end, memberValues));
        }

        return result;
    }

    @Override
    public List<LocusScore> getRawScoresForRange(String chr, int startLocation, int endLocation, int zoom) {
        return getSummaryScoresForRangeEnvelope(chr, startLocation, endLocation, zoom);
    }

    private List<LocusScore> getSummaryScoresForRangeEnvelope(String chr, int startLocation, int endLocation, int zoom) {
        int n = members.size();
        List<List<LocusScore>> perMember = new ArrayList<>(n);
        TreeSet<Integer> boundariesSet = new TreeSet<>();

        for (DataTrack member : members) {
            List<LocusScore> scores = member
                    .getRawSummaryScores(chr, startLocation, endLocation, zoom).getFeatures();
            if (scores == null) scores = Collections.emptyList();
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

            int present = 0;
            float[] rawValues = new float[n];
            Arrays.fill(rawValues, Float.NaN);

            for (int m = 0; m < n; m++) {
                List<LocusScore> scores = perMember.get(m);
                int idx = findCandidate(start, scores, searchIndex[m]);
                searchIndex[m] = idx;
                float raw = idx < scores.size() && scores.get(idx).getStart() < end
                        ? scores.get(idx).getScore() : Float.NaN;
                rawValues[m] = raw;
                if (!Float.isNaN(raw)) present++;
            }

            if (present > 0) {
                result.add(buildStat(start, end, rawValues,
                        AverageErrorLocusScore.Group.RAW, naValue));
            }
        }

        return result;
    }

    private static AverageErrorLocusScore buildStat(int start, int end, float[] memberValues) {
        return buildStat(start, end, memberValues,
                AverageErrorLocusScore.Group.NONE, 0f);
    }

    private static AverageErrorLocusScore buildStat(int start, int end, float[] memberValues,
                                                    AverageErrorLocusScore.Group group,
                                                    float missingValue) {
        float[] stats = group == AverageErrorLocusScore.Group.RAW
                ? AverageErrorStatistics.calculateReplacingNaN(memberValues, missingValue)
                : AverageErrorStatistics.calculate(memberValues);
        return new AverageErrorLocusScore(
                start, end, stats[0], stats[1], stats[2], memberValues.length,
                memberValues, group, missingValue);
    }

    /**
     * Advances monotonically to the first raw score that could overlap {@code start}. Keeping
     * the insertion position even when the current interval is a gap avoids rescanning every
     * earlier score for each boundary-union interval.
     */
    private int findCandidate(int start, List<LocusScore> scoresList, int startIndex) {
        int index = Math.max(0, startIndex);
        while (index < scoresList.size() && scoresList.get(index).getEnd() <= start) index++;
        return index;
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
