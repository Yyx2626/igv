package org.igv.data;

import org.igv.feature.LocusScore;
import org.igv.track.DataSourceTrack;
import org.igv.track.DataTrack;
import org.igv.track.DataType;
import org.igv.track.WindowFunction;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AverageErrorBarDataSourceTest {

    /** Reports a fixed max and min for the whole queried range, whichever the caller last selected. */
    private static class FixedMinMaxSource implements DataSource {
        private final float max;
        private final float min;
        private WindowFunction windowFunction = WindowFunction.mean;

        FixedMinMaxSource(float max, float min) {
            this.max = max;
            this.min = min;
        }

        @Override
        public List<LocusScore> getSummaryScoresForRange(String chr, int start, int end, int zoom) {
            float value = windowFunction == WindowFunction.max ? max
                    : windowFunction == WindowFunction.min ? min : Float.NaN;
            return Float.isNaN(value) ? Collections.emptyList()
                    : List.of(new BasicScore(start, end, value));
        }

        @Override
        public double getDataMax() {
            return max;
        }

        @Override
        public double getDataMin() {
            return min;
        }

        @Override
        public DataType getDataType() {
            return DataType.OTHER;
        }

        @Override
        public void setWindowFunction(WindowFunction statType) {
            this.windowFunction = statType;
        }

        @Override
        public boolean isLogNormalized() {
            return false;
        }

        @Override
        public WindowFunction getWindowFunction() {
            return windowFunction;
        }

        @Override
        public void dispose() {
        }
    }

    private static DataTrack member(float max, float min) {
        return new DataSourceTrack(null, "m", "m", new FixedMinMaxSource(max, min));
    }

    @Test
    public void envelopeSplitsIntoPositiveAndNegativeGroupsWhenMembersDisagreeOnSign() {
        // Member 1 itself crosses zero (max 6, min -2); member 2 is positive-only (its own
        // min never goes below its baseline in this bin - modeled here as NaN/no negative
        // data); member 3 is negative-only. naValue defaults to 0, so member 3 contributes 0
        // to the positive group's sum, and member 2 contributes 0 to the negative group's.
        List<DataTrack> members = List.of(
                member(6f, -2f),
                member(4f, Float.NaN),
                member(-1f, -5f));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        List<LocusScore> result = source.getSummaryScoresForRange("chr1", 0, 100, 0);

        assertEquals(2, result.size());
        AverageErrorLocusScore pos = (AverageErrorLocusScore) result.get(0);
        AverageErrorLocusScore neg = (AverageErrorLocusScore) result.get(1);
        assertTrue("first entry must be the positive group", pos.getScore() > 0);
        assertTrue("second entry must be the negative group", neg.getScore() < 0);
        assertEquals(3, pos.getN());
        assertEquals((6f + 4f + 0f) / 3f, pos.getScore(), 0.0001f);
        assertEquals(3, neg.getN());
        assertEquals((-2f - 5f + 0f) / 3f, neg.getScore(), 0.0001f);
    }

    @Test
    public void envelopeEmitsOnlyOneGroupWhenEveryMemberAgreesOnSign() {
        List<DataTrack> members = List.of(
                member(6f, Float.NaN),
                member(4f, Float.NaN),
                member(9f, Float.NaN));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        List<LocusScore> result = source.getSummaryScoresForRange("chr1", 0, 100, 0);

        assertEquals(1, result.size());
        AverageErrorLocusScore pos = (AverageErrorLocusScore) result.get(0);
        assertEquals((6f + 4f + 9f) / 3f, pos.getScore(), 0.0001f);
        assertEquals(3, pos.getN());
    }

    @Test
    public void envelopeEmitsNothingWhenNoMemberHasDataOnEitherSide() {
        List<DataTrack> members = List.of(
                member(Float.NaN, Float.NaN),
                member(Float.NaN, Float.NaN));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        List<LocusScore> result = source.getSummaryScoresForRange("chr1", 0, 100, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    public void availableWindowFunctionsOfferNoneInsteadOfAbsoluteMax() {
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(List.of(), WindowFunction.mean);
        Collection<WindowFunction> available = source.getAvailableWindowFunctions();
        assertTrue(available.contains(WindowFunction.none));
        assertTrue(!available.contains(WindowFunction.absoluteMax));
    }

    @Test
    public void setWindowFunctionAcceptsNone() {
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(List.of(), WindowFunction.mean);
        source.setWindowFunction(WindowFunction.none);
        assertEquals(WindowFunction.none, source.getWindowFunction());
    }
}
