package org.igv.ui.panel;

import org.igv.feature.RegionDisplayBoundarySource;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.TrackRegionOverride;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionDisplayCoordinateMapTest {

    @Test
    public void invertedRegionalSourceIntervalUsesFixedRoiAxisWhenPartlyVisible() {
        assertArrayEquals(new double[]{100, 150},
                DataPanel.invertedSourceInterval(150, 200, 300), 0.0);
    }

    @Test
    public void invertedRegionalSourceIntervalMovesWithViewportInsideRoi() {
        assertArrayEquals(new double[]{120, 180},
                DataPanel.invertedSourceInterval(120, 180, 300), 0.0);
        assertArrayEquals(new double[]{110, 170},
                DataPanel.invertedSourceInterval(130, 190, 300), 0.0);
    }

    @Test
    public void clickMappingUsesTheFixedRoiAxisWhenRegionIsPartlyVisible() {
        RegionOfInterest region = invertedRegion(100, 200, 1, "track");

        assertEquals(300, DataPanel.regionalInversionSumAt(
                175, "track", List.of(region)), 0);
        assertEquals(125, DataPanel.regionalInversionSumAt(
                175, "track", List.of(region)) - 175, 0);
    }

    @Test
    public void nestedSecondInversionRestoresClickOrientation() {
        RegionOfInterest outer = invertedRegion(100, 300, 1, "track");
        RegionOfInterest inner = invertedRegion(150, 200, 2, "track");

        assertEquals(400, DataPanel.regionalInversionSumAt(
                125, "track", List.of(outer, inner)), 0);
        assertEquals(null, DataPanel.regionalInversionSumAt(
                175, "track", List.of(outer, inner)));
    }

    private static RegionOfInterest invertedRegion(int start, int end, int priority,
                                                    String trackId) {
        RegionOfInterest region = new RegionOfInterest("chr1", start, end, "");
        RegionDisplayRule rule = new RegionDisplayRule();
        rule.setPriority(priority);
        TrackRegionOverride override = new TrackRegionOverride();
        override.setReverseX(true);
        rule.setTrackOverride(trackId, override);
        region.setDisplayRule(rule);
        return region;
    }

    @Test
    public void removesCollapsedSpanFromBothDirections() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 600, false,
                List.of(new RegionDisplayBoundarySource.Interval(30, 70)));

        assertTrue(map.hasCollapsedIntervals());
        assertEquals(100, map.getVisibleSpan(), 0);
        assertEquals(180, map.getScreenPosition(30));
        assertEquals(180, map.getScreenPosition(70));
        assertEquals(73.3333333, map.getGenomicPosition(200), 0.0001);
        assertEquals(106.6666667, map.getGenomicPosition(400), 0.0001);
        assertEquals(140, map.getGenomicPosition(600), 0);
        assertTrue(map.isCollapsed(50));
        assertFalse(map.isCollapsed(80));
    }

    @Test
    public void preservesPiecewiseMappingWhenDisplayIsInverted() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 600, true,
                List.of(new RegionDisplayBoundarySource.Interval(30, 70)));

        assertEquals(600, map.getScreenPosition(0));
        assertEquals(420, map.getScreenPosition(30));
        assertEquals(420, map.getScreenPosition(70));
        assertEquals(240, map.getScreenPosition(100));
        assertEquals(73.3333333, map.getGenomicPosition(400), 0.0001);
        assertEquals(106.6666667, map.getGenomicPosition(200), 0.0001);
    }

    @Test
    public void outsideCollapseDoesNotAlterViewport() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 500, false,
                List.of(new RegionDisplayBoundarySource.Interval(100, 120)));

        assertFalse(map.hasCollapsedIntervals());
        assertEquals(50, map.getGenomicPosition(250), 0);
        assertEquals(250, map.getScreenPosition(50));
    }

    @Test
    public void mergesOverlappingCollapsedIntervals() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 500, false,
                List.of(
                        new RegionDisplayBoundarySource.Interval(20, 50),
                        new RegionDisplayBoundarySource.Interval(40, 80)));

        assertEquals(100, map.getVisibleSpan(), 0);
        assertEquals(100, map.getScreenPosition(20));
        assertEquals(100, map.getScreenPosition(80));
        assertEquals(2, map.getSegments().size());
    }

    @Test
    public void panningJumpsAcrossCollapsedCoordinatesWithoutChangingScale() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 600, false,
                List.of(new RegionDisplayBoundarySource.Interval(30, 70)));

        assertEquals(30, map.shiftGenomicPosition(20, 10), 0);
        assertEquals(71, map.shiftGenomicPosition(30, 1), 0);
        assertEquals(20, map.shiftGenomicPosition(75, -15), 0);
        assertEquals(0.1666667, map.getDisplayScale(), 0.0001);
    }

    @Test
    public void collapsedBoundaryMovesAtTheOrdinaryPanRateUntilItLeavesTheViewport() {
        List<RegionDisplayBoundarySource.Interval> collapsed =
                List.of(new RegionDisplayBoundarySource.Interval(30, 70));
        RegionDisplayCoordinateMap atZero = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 600, false, collapsed);
        RegionDisplayCoordinateMap atTen = RegionDisplayCoordinateMap.create(
                "chr1", 10, 110, 600, false, collapsed);
        RegionDisplayCoordinateMap afterRegion = RegionDisplayCoordinateMap.create(
                "chr1", 80, 180, 600, false, collapsed);

        assertEquals(180, atZero.getScreenPosition(30));
        assertEquals(120, atTen.getScreenPosition(30));
        assertEquals(atZero.getDisplayScale(), atTen.getDisplayScale(), 0);
        assertFalse(afterRegion.hasCollapsedIntervals());
        assertEquals(300, afterRegion.getScreenPosition(130));
    }
}
