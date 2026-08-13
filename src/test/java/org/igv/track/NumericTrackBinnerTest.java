package org.igv.track;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.TrackRegionOverride;
import org.junit.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NumericTrackBinnerTest {

    @Test
    public void resamplesIntoRequestedEqualBins() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 50, 2),
                new BasicScore(50, 100, 4));

        List<LocusScore> output = NumericTrackBinner.bin(input, 0, 100, 4);

        assertEquals(4, output.size());
        assertEquals(0, output.get(0).getStart());
        assertEquals(25, output.get(0).getEnd());
        assertEquals(2f, output.get(0).getScore(), 0f);
        assertEquals(4f, output.get(3).getScore(), 0f);
    }

    @Test
    public void preservesAverageErrorScoreType() {
        List<LocusScore> input = List.of(new AverageErrorLocusScore(0, 100, 5, 2, 1, 4));

        List<LocusScore> output = NumericTrackBinner.bin(input, 0, 100, 2);

        assertTrue(output.get(0) instanceof AverageErrorLocusScore);
        AverageErrorLocusScore score = (AverageErrorLocusScore) output.get(0);
        assertEquals(4, score.getN());
        assertEquals(2f, score.getSd(), 0f);
        assertEquals(1f, score.getSem(), 0f);
    }

    @Test
    public void noBinCrossesActiveRegionBoundary() {
        RegionOfInterest region = new RegionOfInterest("chr1", 30, 70, "transform");
        region.getOrCreateDisplayRule().setMode(RegionDisplayRule.Mode.HIGHLIGHT_BACKGROUND);
        DisplayBinPlan plan = RegionDisplayBinPlanner.create(
                List.of(region), "chr1", 0, 100, 4, Set.of());

        assertEquals(4, plan.getBins().size());
        assertTrue(plan.getBins().stream().noneMatch(bin -> bin.start() < 30 && bin.end() > 30));
        assertTrue(plan.getBins().stream().noneMatch(bin -> bin.start() < 70 && bin.end() > 70));
    }

    @Test
    public void regionOutsideViewportDoesNotChangeBins() {
        RegionOfInterest left = new RegionOfInterest("chr1", -100, 0, "left");
        left.getOrCreateDisplayRule().setMode(RegionDisplayRule.Mode.COVER_FOREGROUND);
        RegionOfInterest right = new RegionOfInterest("chr1", 100, 200, "right");
        right.getOrCreateDisplayRule().setMode(RegionDisplayRule.Mode.COLLAPSE);

        DisplayBinPlan plan = RegionDisplayBinPlanner.create(
                List.of(left, right), "chr1", 0, 100, 4, Set.of());

        assertEquals(List.of(
                new DisplayBinPlan.Bin(0, 25),
                new DisplayBinPlan.Bin(25, 50),
                new DisplayBinPlan.Bin(50, 75),
                new DisplayBinPlan.Bin(75, 100)), plan.getBins());
    }

    @Test
    public void overrideForHiddenTrackDoesNotChangeSharedBinGrid() {
        RegionOfInterest region = new RegionOfInterest("chr1", 30, 70, "hidden track only");
        TrackRegionOverride override = new TrackRegionOverride();
        override.setPositiveColor(Color.RED);
        region.getOrCreateDisplayRule().setTrackOverride("hidden-track", override);

        DisplayBinPlan plan = RegionDisplayBinPlanner.create(
                List.of(region), "chr1", 0, 100, 4, Set.of("visible-track"));

        assertEquals(25, plan.getBins().get(0).end());
        assertEquals(50, plan.getBins().get(1).end());
    }

    @Test
    public void moreBoundedSegmentsThanRequestedBinsKeepsEveryBoundary() {
        DisplayBinPlan plan = DisplayBinPlan.create(0, 100, 2, List.of(10, 20, 30));

        assertEquals(4, plan.getBins().size());
        assertEquals(List.of(10, 20, 30, 100),
                plan.getBins().stream().map(DisplayBinPlan.Bin::end).toList());
    }

    @Test
    public void collapsedRegionGetsNoBins() {
        RegionOfInterest collapsed = new RegionOfInterest("chr1", 30, 70, "remove from display");
        collapsed.getOrCreateDisplayRule().setMode(RegionDisplayRule.Mode.COLLAPSE);

        DisplayBinPlan plan = RegionDisplayBinPlanner.create(
                List.of(collapsed), "chr1", 0, 100, 4, Set.of());

        assertTrue(plan.getBins().stream().noneMatch(bin -> bin.start() < 70 && bin.end() > 30));
        assertEquals(4, plan.getBins().size());
        assertEquals(30, plan.getBins().get(1).end());
        assertEquals(70, plan.getBins().get(2).start());
    }

    @Test
    public void fullyCollapsedRangeProducesNoScores() {
        DisplayBinPlan plan = DisplayBinPlan.create(
                0, 100, 4, List.of(), List.of(new DisplayBinPlan.Interval(0, 100)));

        List<LocusScore> output = NumericTrackBinner.bin(
                List.of(new BasicScore(0, 100, 5)), plan);

        assertTrue(output.isEmpty());
    }

    @Test
    public void envelopeBinWithOnlyPositiveValuesGetsOneEntryAtItsMax() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 10, 2),
                new BasicScore(10, 20, 7),
                new BasicScore(20, 30, 4));

        List<LocusScore> output = NumericTrackBinner.binEnvelope(
                input, DisplayBinPlan.create(0, 30, 1, List.of()), 0f);

        assertEquals(1, output.size());
        assertEquals(7f, output.get(0).getScore(), 0f);
    }

    @Test
    public void envelopeBinWithOnlyNegativeValuesGetsOneEntryAtItsMin() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 10, -2),
                new BasicScore(10, 20, -7),
                new BasicScore(20, 30, -4));

        List<LocusScore> output = NumericTrackBinner.binEnvelope(
                input, DisplayBinPlan.create(0, 30, 1, List.of()), 0f);

        assertEquals(1, output.size());
        assertEquals(-7f, output.get(0).getScore(), 0f);
    }

    @Test
    public void mixedSignBinGetsBothAMaxAndAMinEntry() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 10, 3),
                new BasicScore(10, 20, -5),
                new BasicScore(20, 30, 8),
                new BasicScore(30, 40, -1));

        List<LocusScore> output = NumericTrackBinner.binEnvelope(
                input, DisplayBinPlan.create(0, 40, 1, List.of()), 0f);

        assertEquals(2, output.size());
        assertEquals(8f, output.get(0).getScore(), 0f);
        assertEquals(-5f, output.get(1).getScore(), 0f);
    }

    @Test
    public void binWithOnlyBaselineValuesGetsNoEntry() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 10, 0),
                new BasicScore(10, 20, 0));

        List<LocusScore> output = NumericTrackBinner.binEnvelope(
                input, DisplayBinPlan.create(0, 20, 1, List.of()), 0f);

        assertTrue(output.isEmpty());
    }

    @Test
    public void envelopeRespectsNonZeroBaseline() {
        // Baseline 10: only the value above 10 counts as the positive side, only the one
        // below counts as the negative side; the value exactly at baseline contributes to
        // neither.
        List<LocusScore> input = List.of(
                new BasicScore(0, 10, 15),
                new BasicScore(10, 20, 10),
                new BasicScore(20, 30, 5));

        List<LocusScore> output = NumericTrackBinner.binEnvelope(
                input, DisplayBinPlan.create(0, 30, 1, List.of()), 10f);

        assertEquals(2, output.size());
        assertEquals(15f, output.get(0).getScore(), 0f);
        assertEquals(5f, output.get(1).getScore(), 0f);
    }

    @Test
    public void envelopeClassifiesEachOutputBinIndependently() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 50, 3),
                new BasicScore(50, 100, -6));

        List<LocusScore> output = NumericTrackBinner.binEnvelope(
                input, DisplayBinPlan.create(0, 100, 2, List.of()), 0f);

        assertEquals(2, output.size());
        assertEquals(0, output.get(0).getStart());
        assertEquals(3f, output.get(0).getScore(), 0f);
        assertEquals(50, output.get(1).getStart());
        assertEquals(-6f, output.get(1).getScore(), 0f);
    }

    @Test
    public void averageEnvelopePreservesSdSemAndNUnlikePlainEnvelope() {
        // Re-binning an already-averaged ("Average With Error Bar", Windowing Function None)
        // track's scores must keep SD/SEM/N - binEnvelope() would silently discard them by
        // re-wrapping whatever extreme it finds in a bare BasicScore, dropping the error bar
        // entirely on display.
        List<LocusScore> input = List.of(new AverageErrorLocusScore(0, 100, 5, 2, 1, 4));

        List<LocusScore> output = NumericTrackBinner.binAverageEnvelope(
                input, DisplayBinPlan.create(0, 100, 1, List.of()));

        assertEquals(1, output.size());
        assertTrue(output.get(0) instanceof AverageErrorLocusScore);
        AverageErrorLocusScore score = (AverageErrorLocusScore) output.get(0);
        assertEquals(5f, score.getScore(), 0f);
        assertEquals(2f, score.getSd(), 0f);
        assertEquals(1f, score.getSem(), 0f);
        assertEquals(4, score.getN());
    }

    @Test
    public void averageEnvelopeKeepsPositiveAndNegativeGroupsFromBlendingInAWiderDisplayBin() {
        // Two native (fine-grained) entries fall in the same wider display bin: one from the
        // positive group, one from the negative. Averaging them together would produce a
        // meaningless blended mean/SD - each group must stay in its own output entry.
        List<LocusScore> input = List.of(
                new AverageErrorLocusScore(0, 50, 6, 1, 0.5f, 3),
                new AverageErrorLocusScore(50, 100, -4, 2, 1f, 3));

        List<LocusScore> output = NumericTrackBinner.binAverageEnvelope(
                input, DisplayBinPlan.create(0, 100, 1, List.of()));

        assertEquals(2, output.size());
        AverageErrorLocusScore pos = (AverageErrorLocusScore) output.get(0);
        AverageErrorLocusScore neg = (AverageErrorLocusScore) output.get(1);
        assertEquals(6f, pos.getScore(), 0f);
        assertEquals(1f, pos.getSd(), 0f);
        assertEquals(-4f, neg.getScore(), 0f);
        assertEquals(2f, neg.getSd(), 0f);
    }

    @Test
    public void averageEnvelopeReportsThePeakNotAWidthWeightedAverageOfPeaks() {
        // A wide native entry covering most of the display bin at a low value (e.g. a long
        // near-zero-coverage stretch) alongside a narrow one at a real peak: overlap-weighted
        // averaging (bin()'s approach) would let the wide low entry dilute the peak toward
        // zero - report the single highest-magnitude entry's own stats instead, exactly like
        // binEnvelope() finds the true extreme among plain raw values instead of averaging them.
        List<LocusScore> input = List.of(
                new AverageErrorLocusScore(0, 90, 0.05f, 0.01f, 0.005f, 3),
                new AverageErrorLocusScore(90, 100, 5f, 1f, 0.5f, 3));

        List<LocusScore> output = NumericTrackBinner.binAverageEnvelope(
                input, DisplayBinPlan.create(0, 100, 1, List.of()));

        assertEquals(1, output.size());
        AverageErrorLocusScore peak = (AverageErrorLocusScore) output.get(0);
        assertEquals(5f, peak.getScore(), 0f);
        assertEquals(1f, peak.getSd(), 0f);
        assertEquals(0.5f, peak.getSem(), 0f);
    }

    @Test
    public void averageEnvelopeIgnoresNonAverageScoresRatherThanMisreadingThem() {
        List<LocusScore> input = List.of(new BasicScore(0, 100, 5));

        List<LocusScore> output = NumericTrackBinner.binAverageEnvelope(
                input, DisplayBinPlan.create(0, 100, 1, List.of()));

        assertTrue(output.isEmpty());
    }
}
