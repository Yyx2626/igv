package org.igv.track;

import org.igv.DirectoryManager;
import org.igv.Globals;
import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.LocusScore;
import org.igv.prefs.PreferencesManager;
import org.igv.renderer.DataRange;
import org.igv.renderer.ErrorBarStyle;
import org.igv.renderer.ScatterPointStyle;
import org.igv.ui.panel.ReferenceFrame;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AverageErrorBarTrackRangeTest {

    @BeforeClass
    public static void configureHeadlessPreferences() throws IOException {
        Globals.setHeadless(true);
        DirectoryManager.setIgvDirectory(new File(System.getProperty("java.io.tmpdir")));
        File preferences = File.createTempFile("igv-average-range-test", ".properties");
        preferences.deleteOnExit();
        PreferencesManager.setPrefsFile(preferences.getAbsolutePath());
    }

    private static class FixedScoresTrack extends AverageErrorBarTrack {
        private final List<LocusScore> scores;

        FixedScoresTrack(List<LocusScore> scores) {
            super("average", "Average");
            this.scores = scores;
        }

        @Override
        public List<LocusScore> getInViewScores(ReferenceFrame referenceFrame) {
            return scores;
        }
    }

    @Test
    public void positiveTStyleSdOnlyExpandsOutward() {
        FixedScoresTrack track = trackWithSingleSdScore(10, 20);

        Range range = track.getInViewRange(null);

        assertEquals(10, range.min, 0);
        assertEquals(30, range.max, 0);
    }

    @Test
    public void negativeTStyleSdOnlyExpandsOutward() {
        FixedScoresTrack track = trackWithSingleSdScore(-10, 20);

        Range range = track.getInViewRange(null);

        assertEquals(-30, range.min, 0);
        assertEquals(-10, range.max, 0);
    }

    @Test
    public void errorBarIsExcludedBelowConfiguredMinimumN() {
        FixedScoresTrack track = new FixedScoresTrack(List.of(
                new AverageErrorLocusScore(0, 100, 10, 20, 20, 2)));
        track.setDataRange(new DataRange(-100, 0, 100));
        track.setErrorBarType(ErrorBarType.SD);
        track.setMinimumErrorBarN(3);

        Range range = track.getInViewRange(null);

        assertEquals(10, range.min, 0);
        assertEquals(10, range.max, 0);
    }

    @Test
    public void scatterMemberValuesExpandAutoscaleRange() {
        FixedScoresTrack track = new FixedScoresTrack(List.of(
                new AverageErrorLocusScore(0, 100, 20, 10, 7, 3,
                        new float[]{5, 15, 40})));
        track.setDataRange(new DataRange(0, 0, 100));
        track.setErrorBarType(ErrorBarType.NONE);
        track.setScatterPointsEnabled(true);

        Range range = track.getInViewRange(null);

        assertEquals(5, range.min, 0);
        assertEquals(40, range.max, 0);
    }

    @Test
    public void scatterAndMinimumNSettingsRoundTripThroughJson() {
        AverageErrorBarTrack original = new AverageErrorBarTrack("average", "Average");
        original.setMemberTracks(List.of());
        original.setMinimumErrorBarN(4);
        original.setScatterPointsEnabled(true);
        original.getScatterPointStyle().setWidthPercent(50);
        original.getScatterPointStyle().setShape(ScatterPointStyle.Shape.TRIANGLE);
        JSONObject json = new JSONObject();
        original.marshalJSON(json);

        AverageErrorBarTrack restored = new AverageErrorBarTrack("restored", "Restored");
        restored.unmarshalJSON(json);

        assertEquals(4, restored.getMinimumErrorBarN());
        assertEquals(true, restored.isScatterPointsEnabled());
        assertEquals(50, restored.getScatterPointStyle().getWidthPercent());
        assertEquals(ScatterPointStyle.Shape.TRIANGLE,
                restored.getScatterPointStyle().getShape());
    }

    private static FixedScoresTrack trackWithSingleSdScore(float mean, float sd) {
        FixedScoresTrack track = new FixedScoresTrack(List.of(
                new AverageErrorLocusScore(0, 100, mean, sd, sd, 3)));
        track.setDataRange(new DataRange(-100, 0, 100));
        track.setErrorBarType(ErrorBarType.SD);
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.SINGLE);
        track.setErrorBarStyle(style);
        return track;
    }
}
