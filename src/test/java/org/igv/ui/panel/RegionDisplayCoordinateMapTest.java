package org.igv.ui.panel;

import org.igv.feature.RegionDisplayBoundarySource;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionDisplayCoordinateMapTest {

    @Test
    public void removesCollapsedSpanFromBothDirections() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 600, false,
                List.of(new RegionDisplayBoundarySource.Interval(30, 70)));

        assertTrue(map.hasCollapsedIntervals());
        assertEquals(60, map.getVisibleSpan(), 0);
        assertEquals(300, map.getScreenPosition(30));
        assertEquals(300, map.getScreenPosition(70));
        assertEquals(20, map.getGenomicPosition(200), 0);
        assertEquals(80, map.getGenomicPosition(400), 0);
        assertTrue(map.isCollapsed(50));
        assertFalse(map.isCollapsed(80));
    }

    @Test
    public void preservesPiecewiseMappingWhenDisplayIsInverted() {
        RegionDisplayCoordinateMap map = RegionDisplayCoordinateMap.create(
                "chr1", 0, 100, 600, true,
                List.of(new RegionDisplayBoundarySource.Interval(30, 70)));

        assertEquals(600, map.getScreenPosition(0));
        assertEquals(300, map.getScreenPosition(30));
        assertEquals(300, map.getScreenPosition(70));
        assertEquals(0, map.getScreenPosition(100));
        assertEquals(20, map.getGenomicPosition(400), 0);
        assertEquals(80, map.getGenomicPosition(200), 0);
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

        assertEquals(40, map.getVisibleSpan(), 0);
        assertEquals(250, map.getScreenPosition(20));
        assertEquals(250, map.getScreenPosition(80));
        assertEquals(2, map.getSegments().size());
    }
}
