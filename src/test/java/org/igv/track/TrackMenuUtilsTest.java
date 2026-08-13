package org.igv.track;

import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TrackMenuUtilsTest {

    @Test
    public void pairBorderSelectionAlwaysResolvesToOneTopTrackPerPair() {
        TestTrack top = track("pair-1", PairRole.TOP);
        TestTrack bottom = track("pair-1", PairRole.BOTTOM);
        TestTrack unrelated = new TestTrack();

        assertEquals(List.of(top), TrackMenuUtils.resolvePairBorderTracks(
                List.of(bottom, top, unrelated), List.of(top, bottom, unrelated)));
    }

    @Test
    public void malformedPairWithoutTopIsIgnored() {
        TestTrack bottom = track("pair-1", PairRole.BOTTOM);
        assertEquals(List.of(), TrackMenuUtils.resolvePairBorderTracks(
                List.of(bottom), List.of(bottom)));
    }

    @Test
    public void pairFlipKeepsBorderOverridesAtTheirScreenBoundaries() {
        TestTrack oldTop = new TestTrack();
        TestTrack oldBottom = new TestTrack();
        oldTop.setBorderHeightOverride(8);
        oldTop.setBorderColorOverride(Color.RED);
        oldBottom.setBorderHeightOverride(2);
        oldBottom.setBorderColorOverride(Color.BLUE);

        TrackPairing.swapBorderOverrides(oldTop, oldBottom);

        assertEquals(Integer.valueOf(2), oldTop.getBorderHeightOverride());
        assertEquals(Color.BLUE, oldTop.getBorderColorOverride());
        assertEquals(Integer.valueOf(8), oldBottom.getBorderHeightOverride());
        assertEquals(Color.RED, oldBottom.getBorderColorOverride());
    }

    private static TestTrack track(String pairId, PairRole role) {
        TestTrack track = new TestTrack();
        track.setPairId(pairId);
        track.setPairRole(role);
        return track;
    }

    private static final class TestTrack extends AbstractTrack {
        @Override
        public void render(RenderContext context) {
        }
    }
}
