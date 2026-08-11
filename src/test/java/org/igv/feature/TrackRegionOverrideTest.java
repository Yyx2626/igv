package org.igv.feature;

import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TrackRegionOverrideTest {

    @Test
    public void nestedCoordinateInversionsCancel() {
        TrackRegionOverride outer = new TrackRegionOverride();
        outer.setReverseX(true);
        TrackRegionOverride inner = new TrackRegionOverride();
        inner.setReverseX(true);

        TrackRegionOverride effective = TrackRegionOverride.compose(List.of(outer, inner));

        assertFalse(effective.isReverseX());
    }

    @Test
    public void nestedYAxisFlipsCancelAndInnerColorWins() {
        TrackRegionOverride outer = new TrackRegionOverride();
        outer.setYAxisMode(TrackRegionOverride.YAxisMode.FLIP);
        outer.setPositiveColor(Color.RED);
        TrackRegionOverride inner = new TrackRegionOverride();
        inner.setYAxisMode(TrackRegionOverride.YAxisMode.FLIP);
        inner.setPositiveColor(Color.BLUE);

        TrackRegionOverride effective = TrackRegionOverride.compose(List.of(outer, inner));

        assertEquals(TrackRegionOverride.YAxisMode.DEFAULT, effective.getYAxisMode());
        assertEquals(Color.BLUE, effective.getPositiveColor());
    }

    @Test
    public void nestedPairSwapsCancelAndRoundTripThroughJson() {
        TrackRegionOverride outer = new TrackRegionOverride();
        outer.setPairMode(TrackRegionOverride.PairMode.FLIP);
        TrackRegionOverride restored = TrackRegionOverride.fromJson(outer.toJson());
        assertEquals(TrackRegionOverride.PairMode.FLIP, restored.getPairMode());

        TrackRegionOverride inner = new TrackRegionOverride();
        inner.setPairMode(TrackRegionOverride.PairMode.FLIP);
        TrackRegionOverride effective = TrackRegionOverride.compose(List.of(outer, inner));
        assertEquals(TrackRegionOverride.PairMode.NONE, effective.getPairMode());
        assertEquals(TrackRegionOverride.YAxisMode.DEFAULT, effective.getYAxisMode());
    }

    @Test
    public void pairSwapAndPairFlipRemainDistinct() {
        TrackRegionOverride swap = new TrackRegionOverride();
        swap.setPairMode(TrackRegionOverride.PairMode.SWAP);
        TrackRegionOverride flip = new TrackRegionOverride();
        flip.setPairMode(TrackRegionOverride.PairMode.FLIP);

        assertEquals(TrackRegionOverride.PairMode.SWAP,
                TrackRegionOverride.fromJson(swap.toJson()).getPairMode());
        assertEquals(TrackRegionOverride.PairMode.FLIP,
                TrackRegionOverride.fromJson(flip.toJson()).getPairMode());
    }
}
