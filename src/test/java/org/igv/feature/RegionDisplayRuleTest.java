package org.igv.feature;

import org.json.JSONObject;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionDisplayRuleTest {

    @Test
    public void jsonRoundTripPreservesTrackOverridesAndAlpha() {
        RegionDisplayRule rule = new RegionDisplayRule();
        rule.setMode(RegionDisplayRule.Mode.HIGHLIGHT_BACKGROUND);
        rule.setRegionColor(new Color(10, 20, 30, 80));
        rule.setPriority(7);
        TrackRegionOverride override = new TrackRegionOverride();
        override.setReverseX(true);
        override.setCustomRange(-3, 0, 9, false);
        override.setPositiveColor(new Color(1, 2, 3, 4));
        rule.setTrackOverride("track-id", override);

        RegionDisplayRule restored = RegionDisplayRule.fromJson(new JSONObject(rule.toJson().toString()));

        assertEquals(RegionDisplayRule.Mode.HIGHLIGHT_BACKGROUND, restored.getMode());
        assertEquals(new Color(10, 20, 30, 80), restored.getRegionColor());
        assertEquals(7, restored.getPriority());
        TrackRegionOverride restoredOverride = restored.getTrackOverride("track-id");
        assertTrue(restoredOverride.isReverseX());
        assertEquals(TrackRegionOverride.YAxisMode.CUSTOM, restoredOverride.getYAxisMode());
        assertEquals(Float.valueOf(-3), restoredOverride.getRangeMinimum());
        assertEquals(Float.valueOf(0), restoredOverride.getRangeBaseline());
        assertEquals(Float.valueOf(9), restoredOverride.getRangeMaximum());
        assertEquals(new Color(1, 2, 3, 4), restoredOverride.getPositiveColor());
    }

    @Test
    public void emptyRuleDoesNotActivateRegion() {
        RegionOfInterest region = new RegionOfInterest("chr1", 10, 20, "plain ROI");
        region.getOrCreateDisplayRule();

        assertFalse(region.hasActiveDisplayRule());
    }

    @Test
    public void regionBarColorsAreIndependent() {
        RegionOfInterest first = new RegionOfInterest("chr1", 10, 20, "first");
        RegionOfInterest second = new RegionOfInterest("chr1", 30, 40, "second");
        first.setBackgroundColor(new Color(1, 2, 3, 128));

        assertEquals(new Color(1, 2, 3, 128), first.getBackgroundColor());
        assertEquals(RegionOfInterest.DEFAULT_BAR_COLOR, second.getBackgroundColor());
    }

    @Test
    public void regionBackgroundAndForegroundAreIndependentAndPersisted() {
        RegionDisplayRule rule = new RegionDisplayRule();
        rule.setRegionBackgroundColor(new Color(10, 20, 30, 80));
        rule.setRegionForegroundColor(new Color(40, 50, 60, 200));
        rule.setCollapsed(true);

        RegionDisplayRule restored = RegionDisplayRule.fromJson(new JSONObject(rule.toJson().toString()));

        assertEquals(new Color(10, 20, 30, 80), restored.getRegionBackgroundColor());
        assertEquals(new Color(40, 50, 60, 200), restored.getRegionForegroundColor());
        assertTrue(restored.isCollapsed());
    }
}
