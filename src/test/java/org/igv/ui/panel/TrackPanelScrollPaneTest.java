package org.igv.ui.panel;

import org.igv.track.AbstractTrack;
import org.igv.track.RenderContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TrackPanelScrollPaneTest {

    @Test
    public void normalLayoutUsesRequestedHeightEvenWhenContentIsTaller() {
        TestTrack track = new TestTrack(120);
        track.setHeight(40);

        assertEquals(40, TrackPanelScrollPane.getPreferredTrackHeight(track, false));
        assertEquals(120, TrackPanelScrollPane.getPreferredTrackHeight(track, true));

        track.setHeight(75);
        assertEquals(75, TrackPanelScrollPane.getPreferredTrackHeight(track, false));
    }

    private static final class TestTrack extends AbstractTrack {
        private final int contentHeight;

        private TestTrack(int contentHeight) {
            this.contentHeight = contentHeight;
        }

        @Override
        public int getContentHeight() {
            return contentHeight;
        }

        @Override
        public void render(RenderContext context) {
        }
    }
}
