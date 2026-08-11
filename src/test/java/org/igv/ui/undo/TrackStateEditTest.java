package org.igv.ui.undo;

import org.igv.renderer.DataRange;
import org.igv.track.AbstractTrack;
import org.igv.track.AttributeManager;
import org.igv.track.PairRole;
import org.igv.track.RenderContext;
import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.*;

public class TrackStateEditTest {

    @Test
    public void restoresExplicitAndInheritedTrackProperties() {
        TestTrack track = new TestTrack();
        track.setDataRange(new DataRange(-2, 1, 8));
        track.setAutoScale(false);
        track.setColor(null);
        track.setAltColor(null);
        track.setHeight(45);
        track.setOrder(7);

        TrackStateEdit.State before = TrackStateEdit.capture(List.of(track)).get(0);
        track.setDataRange(new DataRange(0, 5, 10));
        track.setAutoScale(true);
        track.setColor(Color.RED);
        track.setAltColor(Color.BLUE);
        track.setBackgroundColorOverride(Color.YELLOW);
        track.setBorderHeightOverride(4);
        track.setBorderColorOverride(Color.BLACK);
        track.setHeight(90);
        track.setPairId("pair");
        track.setPairRole(PairRole.TOP);
        track.setOrder(12);

        assertTrue(TrackStateEdit.differs(List.of(before), TrackStateEdit.capture(List.of(track))));
        before.apply();

        assertEquals(-2f, track.getDataRange().getMinimum(), 0f);
        assertEquals(1f, track.getDataRange().getBaseline(), 0f);
        assertEquals(8f, track.getDataRange().getMaximum(), 0f);
        assertFalse(track.getAutoScale());
        assertNull(track.getColorOverride());
        assertNull(track.getAltColorOverride());
        assertNull(track.getBackgroundColorOverride());
        assertNull(track.getBorderHeightOverride());
        assertNull(track.getBorderColorOverride());
        assertEquals(45, track.getHeight());
        assertNull(track.getPairId());
        assertNull(track.getPairRole());
        assertEquals(7, track.getOrder());
    }

    private static final class TestTrack extends AbstractTrack {
        private String autoscaleGroup;

        private TestTrack() {
            super();
            setName("test");
        }

        @Override
        public void render(RenderContext context) {
        }

        @Override
        public String getAutoscaleGroup() {
            return autoscaleGroup;
        }

        @Override
        public void setAttributeValue(String name, String value) {
            if (AttributeManager.GROUP_AUTOSCALE.equalsIgnoreCase(name)) autoscaleGroup = value;
            else if (AttributeManager.PAIR_GROUP.equalsIgnoreCase(name)) setPairId(value);
        }

        @Override
        public void removeAttribute(String name) {
            if (AttributeManager.GROUP_AUTOSCALE.equalsIgnoreCase(name)) autoscaleGroup = null;
            else if (AttributeManager.PAIR_GROUP.equalsIgnoreCase(name)) setPairId(null);
        }
    }
}
