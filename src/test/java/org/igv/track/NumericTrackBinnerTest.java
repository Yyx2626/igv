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
}
