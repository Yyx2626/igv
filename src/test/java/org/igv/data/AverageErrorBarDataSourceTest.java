package org.igv.data;

import org.igv.feature.LocusScore;
import org.igv.track.LoadedDataInterval;
import org.igv.track.DataTrack;
import org.igv.track.DisplayBinPlan;
import org.igv.track.NumericTrackBinner;
import org.igv.track.WindowFunction;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AverageErrorBarDataSourceTest {

    /** Minimal track fixture that does not initialize application preferences. */
    private static class FixedTrack extends DataTrack {
        private final Float rawValue;
        private final float zoomMax;
        private WindowFunction windowFunction = WindowFunction.none;

        FixedTrack(Float rawValue) {
            this(rawValue, rawValue == null ? Float.NaN : rawValue);
        }

        FixedTrack(Float rawValue, float zoomMax) {
            super();
            this.rawValue = rawValue;
            this.zoomMax = zoomMax;
        }

        @Override
        public LoadedDataInterval<List<LocusScore>> getSummaryScores(
                String chr, int start, int end, int zoom) {
            float value = windowFunction == WindowFunction.max ? zoomMax
                    : rawValue == null ? Float.NaN : rawValue;
            List<LocusScore> scores = Float.isNaN(value) ? Collections.emptyList()
                    : List.of(new BasicScore(start, end, value));
            return new LoadedDataInterval<>(chr, start, end, zoom, scores);
        }

        @Override
        public void setWindowFunction(WindowFunction type) {
            windowFunction = type;
        }

        @Override
        public WindowFunction getWindowFunction() {
            return windowFunction;
        }

        @Override
        public Collection<WindowFunction> getAvailableWindowFunctions() {
            return List.of(WindowFunction.none, WindowFunction.mean, WindowFunction.max, WindowFunction.min);
        }
    }

    private static DataTrack member(Float rawValue) {
        return new FixedTrack(rawValue);
    }

    @Test
    public void envelopeSplitsIntoPositiveAndNegativeGroupsWhenMembersDisagreeOnSign() {
        // naValue defaults to 0, so the negative member contributes 0 to the positive group,
        // while both positive members contribute 0 to the negative group.
        List<DataTrack> members = List.of(
                member(6f),
                member(4f),
                member(-5f));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        List<LocusScore> result = NumericTrackBinner.binAverageEnvelope(
                source.getSummaryScoresForRange("chr1", 0, 100, 0),
                DisplayBinPlan.create(0, 100, 1, List.of()));

        assertEquals(2, result.size());
        AverageErrorLocusScore pos = (AverageErrorLocusScore) result.get(0);
        AverageErrorLocusScore neg = (AverageErrorLocusScore) result.get(1);
        assertTrue("first entry must be the positive group", pos.getScore() > 0);
        assertTrue("second entry must be the negative group", neg.getScore() < 0);
        assertEquals(3, pos.getN());
        assertEquals((6f + 4f + 0f) / 3f, pos.getScore(), 0.0001f);
        assertEquals(3, neg.getN());
        assertEquals((-5f + 0f + 0f) / 3f, neg.getScore(), 0.0001f);
        assertEquals(6f, pos.getMemberValue(0), 0f);
        assertEquals(4f, pos.getMemberValue(1), 0f);
        assertEquals(0f, pos.getMemberValue(2), 0f);
    }

    @Test
    public void envelopeEmitsOnlyOneGroupWhenEveryMemberAgreesOnSign() {
        List<DataTrack> members = List.of(
                member(6f),
                member(4f),
                member(9f));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        List<LocusScore> result = NumericTrackBinner.binAverageEnvelope(
                source.getSummaryScoresForRange("chr1", 0, 100, 0),
                DisplayBinPlan.create(0, 100, 1, List.of()));

        assertEquals(1, result.size());
        AverageErrorLocusScore pos = (AverageErrorLocusScore) result.get(0);
        assertEquals((6f + 4f + 9f) / 3f, pos.getScore(), 0.0001f);
        assertEquals(3, pos.getN());
    }

    @Test
    public void envelopeEmitsNothingWhenNoMemberHasDataOnEitherSide() {
        List<DataTrack> members = List.of(
                member(null),
                member(null));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        List<LocusScore> result = NumericTrackBinner.binAverageEnvelope(
                source.getSummaryScoresForRange("chr1", 0, 100, 0),
                DisplayBinPlan.create(0, 100, 1, List.of()));

        assertTrue(result.isEmpty());
    }

    @Test
    public void noneUsesRawValuesRatherThanOverlappingZoomMaxima() {
        List<DataTrack> members = List.of(
                new FixedTrack(2f, 100f),
                new FixedTrack(4f, 200f));
        AverageErrorBarDataSource source = new AverageErrorBarDataSource(members, WindowFunction.none);

        AverageErrorLocusScore score = (AverageErrorLocusScore)
                source.getSummaryScoresForRange("chr1", 0, 100, 0).get(0);

        assertEquals(3f, score.getScore(), 0f);
        assertEquals(2f, score.getMemberValue(0), 0f);
        assertEquals(4f, score.getMemberValue(1), 0f);
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
