package org.igv.prefs;

import org.igv.bedpe.InteractionTrack;
import org.igv.track.DataTrack;
import org.igv.track.FeatureTrack;
import org.igv.track.LoadedDataInterval;
import org.igv.track.RenderContext;
import org.igv.track.Track;
import org.igv.feature.LocusScore;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PreferencesEditorTest {

    @Test
    public void heightPreferencesTargetExistingTracksByType() {
        TestDataTrack numeric = new TestDataTrack();
        FeatureTrack feature = new FeatureTrack();
        InteractionTrack interaction = new InteractionTrack();

        Map<Track, Integer> changes = PreferencesEditor.resolveTrackHeightChanges(
                Map.of("", Map.of(
                        Constants.CHART_TRACK_HEIGHT_KEY, "55",
                        Constants.TRACK_HEIGHT_KEY, "25",
                        Constants.INTERACT_TRACK_HEIGHT, "180")),
                List.of(numeric, feature, interaction));

        assertEquals(Integer.valueOf(55), changes.get(numeric));
        assertEquals(Integer.valueOf(25), changes.get(feature));
        assertEquals(Integer.valueOf(180), changes.get(interaction));
    }

    @Test
    public void unchangedHeightPreferenceDoesNotTouchThatTrackType() {
        TestDataTrack numeric = new TestDataTrack();
        FeatureTrack feature = new FeatureTrack();

        Map<Track, Integer> changes = PreferencesEditor.resolveTrackHeightChanges(
                Map.of("", Map.of(Constants.CHART_TRACK_HEIGHT_KEY, "60")),
                List.of(numeric, feature));

        assertEquals(Map.of(numeric, 60), changes);
    }

    private static final class TestDataTrack extends DataTrack {
        @Override
        public LoadedDataInterval<List<LocusScore>> getSummaryScores(
                String chr, int startLocation, int endLocation, int zoom) {
            return null;
        }

        @Override
        public void render(RenderContext context) {
        }
    }
}
