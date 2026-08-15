package org.igv.track;

import org.json.JSONObject;
import org.junit.Test;
import org.igv.renderer.DataRange;

import java.awt.Color;
import java.awt.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionStatePersistenceTest {

    @Test
    public void mergedTrackRestoresSavedTransparency() {
        DataSourceTrack originalMember = track("original");
        MergedTracks original = new MergedTracks("merged", "Merged", List.of(originalMember));
        original.setTrackAlphas(0.73);
        JSONObject json = new JSONObject();
        original.marshalJSON(json);

        DataSourceTrack restoredMember = track("restored");
        MergedTracks restored = new MergedTracks("merged", "Merged", List.of(restoredMember));
        restored.unmarshalJSON(json);

        assertEquals(0.73, restored.getTrackAlpha(), 0.0001);
        assertEquals((int) Math.floor(0.73 * 255), restoredMember.getColor().getAlpha());
    }

    @Test
    public void pairFlipSwapsPersistentTrackOrder() {
        DataSourceTrack top = track("top");
        DataSourceTrack bottom = track("bottom");
        top.setOrder(10);
        bottom.setOrder(20);

        TrackPairing.swapPersistentOrder(top, bottom);

        assertEquals(20, top.getOrder());
        assertEquals(10, bottom.getOrder());
    }

    @Test
    public void flipRangePreservesBothIndividualAndGroupAutoscaling() {
        DataSourceTrack first = track("first");
        DataSourceTrack second = track("second");
        DataRange shared = new DataRange(0, 5, 10);
        first.setDataRange(shared);
        second.setDataRange(shared);
        first.setAutoScale(true);
        second.setAutoScale(true);
        first.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, "Group 1");
        second.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, "Group 1");

        TrackPairing.flipDataRanges(List.of(first, second));

        assertEquals(10f, first.getDataRange().getMinimum(), 0f);
        assertEquals(0f, first.getDataRange().getMaximum(), 0f);
        assertEquals(10f, second.getDataRange().getMinimum(), 0f);
        assertEquals(0f, second.getDataRange().getMaximum(), 0f);
        assertTrue(first.getAutoScale());
        assertTrue(second.getAutoScale());
        assertEquals("Group 1", first.getAttributeValue(AttributeManager.GROUP_AUTOSCALE));
        assertEquals("Group 1", second.getAttributeValue(AttributeManager.GROUP_AUTOSCALE));
    }

    @Test
    public void mixedAutoscaleSelectionIsInitiallyUnchecked() {
        DataSourceTrack enabled = track("enabled");
        DataSourceTrack disabled = track("disabled");
        enabled.setAutoScale(true);
        disabled.setAutoScale(false);

        javax.swing.JCheckBoxMenuItem item = (javax.swing.JCheckBoxMenuItem)
                TrackMenuUtils.getAutoscaleItem(List.of(enabled, disabled));

        assertFalse(item.isSelected());
    }

    @Test
    public void overlayMenuUsesNumericControlsWithoutUnusedRendererChoices() {
        MergedTracks merged = new MergedTracks("merged", "Merged", List.of(track("member")));

        List<Component> items = merged.getPopupMenuItems(null);

        assertFalse(hasText(items, "Type of Graph"));
        assertFalse(hasText(items, "Bar Chart"));
        assertTrue(hasText(items, "Flip Y-Axis"));
        assertTrue(hasText(items, "Adjust Overlay Transparency..."));
        assertTrue(hasText(items, "Separate Tracks"));
    }

    @Test
    public void regularNumericMenuRetainsRendererChoices() {
        List<Component> items = TrackMenuUtils.getDataMenuItems(List.of(menuTrack("regular")));

        assertTrue(hasText(items, "Type of Graph"));
        assertTrue(hasText(items, "Bar Chart"));
    }

    @Test
    public void mixedOverlaySelectionRetainsRendererChoicesForRegularTracks() {
        MergedTracks merged = new MergedTracks("merged", "Merged", List.of(track("member")));

        List<Component> items = TrackMenuUtils.getDataMenuItems(List.of(merged, menuTrack("regular")));

        assertTrue(hasText(items, "Type of Graph"));
        assertTrue(hasText(items, "Bar Chart"));
    }

    private static boolean hasText(List<Component> items, String expected) {
        return items.stream().anyMatch(item -> {
            if (item instanceof javax.swing.AbstractButton) {
                return expected.equals(((javax.swing.AbstractButton) item).getText().trim());
            }
            return item instanceof javax.swing.JLabel
                    && expected.equals(((javax.swing.JLabel) item).getText().trim());
        });
    }

    private static DataSourceTrack menuTrack(String name) {
        return new DataSourceTrack(null, name, name, null) {
            @Override
            public Collection<WindowFunction> getAvailableWindowFunctions() {
                return Collections.emptyList();
            }
        };
    }

    private static DataSourceTrack track(String name) {
        DataSourceTrack track = new DataSourceTrack(null, name, name, null);
        track.setColor(new Color(10, 20, 30));
        return track;
    }
}
