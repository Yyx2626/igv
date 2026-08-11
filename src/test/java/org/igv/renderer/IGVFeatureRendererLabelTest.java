package org.igv.renderer;

import org.junit.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IGVFeatureRendererLabelTest {

    private static final Rectangle SEGMENT_CLIP = new Rectangle(100, 0, 80, 20);

    @Test
    public void drawsLabelFullyInsideVisibleSegment() {
        assertTrue(IGVFeatureRenderer.isLabelFullyInsideClip(110, 50, SEGMENT_CLIP));
    }

    @Test
    public void suppressesLabelCrossingLeftCollapseBoundary() {
        assertFalse(IGVFeatureRenderer.isLabelFullyInsideClip(90, 50, SEGMENT_CLIP));
    }

    @Test
    public void suppressesLabelCrossingRightCollapseBoundary() {
        assertFalse(IGVFeatureRenderer.isLabelFullyInsideClip(150, 50, SEGMENT_CLIP));
    }

    @Test
    public void regionalPassKeepsLabelWhenTextIsWiderThanRegion() {
        assertTrue(IGVFeatureRenderer.isLabelAllowed(90, 120, 120, SEGMENT_CLIP, true));
        assertFalse(IGVFeatureRenderer.isLabelAllowed(90, 120, 120, SEGMENT_CLIP, false));
    }

    @Test
    public void regionalPassRejectsCompletelyInvisibleLabel() {
        assertFalse(IGVFeatureRenderer.isLabelAllowed(10, 40, 40, SEGMENT_CLIP, true));
        assertFalse(IGVFeatureRenderer.isLabelAllowed(190, 40, 40, SEGMENT_CLIP, true));
    }

    @Test
    public void finalLabelLayerRemovesOnlyScreenOverlaps() {
        List<Integer> selected = FeatureLabelCollector.selectNonOverlappingIndices(List.of(
                new Rectangle(0, 0, 50, 10),
                new Rectangle(40, 0, 50, 10),
                new Rectangle(40, 20, 50, 10),
                new Rectangle(55, 0, 30, 10)));

        assertEquals(List.of(0, 2, 3), selected);
    }

}
