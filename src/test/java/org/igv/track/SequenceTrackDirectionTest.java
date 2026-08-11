package org.igv.track;

import org.igv.feature.Strand;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.TrackRegionOverride;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SequenceTrackDirectionTest {

    @Test
    public void coordinateInversionReversesDisplayedArrow() {
        assertTrue(SequenceTrack.isArrowPointingRight(Strand.POSITIVE, false));
        assertFalse(SequenceTrack.isArrowPointingRight(Strand.POSITIVE, true));
        assertFalse(SequenceTrack.isArrowPointingRight(Strand.NEGATIVE, false));
        assertTrue(SequenceTrack.isArrowPointingRight(Strand.NEGATIVE, true));
    }

    @Test
    public void disjointNormalAndInvertedSequenceRangesRemainSeparatelyLoadable() {
        assertEquals(List.of(
                        new SequenceTrack.IntRange(100, 160),
                        new SequenceTrack.IntRange(840, 900)),
                SequenceTrack.mergeRanges(List.of(
                        new SequenceTrack.IntRange(120, 160),
                        new SequenceTrack.IntRange(100, 130),
                        new SequenceTrack.IntRange(840, 900))));
    }

    @Test
    public void regionalSequenceSourceUsesFixedRoiBoundaries() {
        String trackId = "sequence-id";
        RegionOfInterest region = new RegionOfInterest("chr1", 100, 900, "inverted");
        RegionDisplayRule rule = new RegionDisplayRule();
        TrackRegionOverride override = new TrackRegionOverride();
        override.setReverseX(true);
        rule.setTrackOverride(trackId, override);
        region.setDisplayRule(rule);

        assertEquals(List.of(new SequenceTrack.IntRange(840, 900)),
                SequenceTrack.requiredRegionalSourceIntervals(List.of(region), trackId, 100, 160));
    }

    @Test
    public void regionalInversionDisplaysReverseComplementStrand() {
        assertEquals(Strand.NEGATIVE,
                SequenceTrack.effectiveRenderStrand(Strand.POSITIVE, true));
        assertEquals(Strand.POSITIVE,
                SequenceTrack.effectiveRenderStrand(Strand.NEGATIVE, true));
        assertEquals(Strand.POSITIVE,
                SequenceTrack.effectiveRenderStrand(Strand.POSITIVE, false));
    }
}
