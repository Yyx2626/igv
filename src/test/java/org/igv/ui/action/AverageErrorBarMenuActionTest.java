package org.igv.ui.action;

import org.igv.DirectoryManager;
import org.igv.Globals;
import org.igv.feature.TrackRegionOverride;
import org.igv.prefs.PreferencesManager;
import org.igv.renderer.DataRange;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.DataSourceTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.WindowFunction;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AverageErrorBarMenuActionTest {

    @BeforeClass
    public static void configureHeadlessPreferences() throws IOException {
        Globals.setHeadless(true);
        DirectoryManager.setIgvDirectory(new File(System.getProperty("java.io.tmpdir")));
        File preferences = File.createTempFile("igv-average-test", ".properties");
        preferences.deleteOnExit();
        PreferencesManager.setPrefsFile(preferences.getAbsolutePath());
    }

    @Test
    public void regionalOverridesMustMatchSemanticallyBeforeAveraging() {
        TrackRegionOverride first = new TrackRegionOverride();
        first.setReverseX(true);
        first.setPositiveColor(new Color(1, 2, 3, 4));
        TrackRegionOverride same = first.copy();
        TrackRegionOverride different = first.copy();
        different.setReverseX(false);

        assertTrue(RegionalTrackSettingsTransfer.sameOverride(first, same));
        assertFalse(RegionalTrackSettingsTransfer.sameOverride(first, different));
        assertTrue(RegionalTrackSettingsTransfer.sameOverride(null, new TrackRegionOverride()));
        assertFalse(RegionalTrackSettingsTransfer.sameOverride(first, null));
    }

    @Test
    public void independentMembersInheritEverythingExceptRegionalPairMode() {
        TrackRegionOverride source = new TrackRegionOverride();
        source.setReverseX(true);
        source.setPairMode(TrackRegionOverride.PairMode.FLIP);
        source.setNegativeColor(Color.RED);

        TrackRegionOverride inherited =
                RegionalTrackSettingsTransfer.copyForTransfer(source, false);

        assertTrue(inherited.isReverseX());
        assertEquals(Color.RED, inherited.getNegativeColor());
        assertEquals(TrackRegionOverride.PairMode.NONE, inherited.getPairMode());
        assertEquals(TrackRegionOverride.PairMode.FLIP, source.getPairMode());
    }

    @Test
    public void averageInheritsACommonManualDataRange() {
        DataSourceTrack first = new DataSourceTrack(null, "first", "first", null);
        DataSourceTrack second = new DataSourceTrack(null, "second", "second", null);
        first.setAutoScale(false);
        second.setAutoScale(false);
        first.setDataRange(new DataRange(0, 0, 150));
        second.setDataRange(new DataRange(0, 0, 150));

        AverageErrorBarTrack average = new AverageErrorBarTrack(
                "average", "Average", List.of(first, second),
                WindowFunction.mean, ErrorBarType.SEM, 0);

        assertFalse(average.getAutoScale());
        assertEquals(0, average.getDataRange().getMinimum(), 0);
        assertEquals(0, average.getDataRange().getBaseline(), 0);
        assertEquals(150, average.getDataRange().getMaximum(), 0);
    }

    @Test
    public void averageKeepsAutoscaleForMixedManualDataRanges() {
        DataSourceTrack first = new DataSourceTrack(null, "first", "first", null);
        DataSourceTrack second = new DataSourceTrack(null, "second", "second", null);
        first.setAutoScale(false);
        second.setAutoScale(false);
        first.setDataRange(new DataRange(0, 0, 150));
        second.setDataRange(new DataRange(0, 0, 200));

        AverageErrorBarTrack average = new AverageErrorBarTrack(
                "average", "Average", List.of(first, second),
                WindowFunction.mean, ErrorBarType.SEM, 0);

        assertTrue(average.getAutoScale());
    }

    @Test
    public void averageInheritsCommonPositiveAndNegativeColors() {
        DataSourceTrack first = new DataSourceTrack(null, "first", "first", null);
        DataSourceTrack second = new DataSourceTrack(null, "second", "second", null);
        Color positive = new Color(10, 20, 30);
        Color negative = new Color(40, 50, 60);
        first.setColor(positive);
        second.setColor(positive);
        first.setAltColor(negative);
        second.setAltColor(negative);

        AverageErrorBarTrack average = new AverageErrorBarTrack(
                "average", "Average", List.of(first, second),
                WindowFunction.mean, ErrorBarType.SEM, 0);

        assertEquals(positive, average.getColor());
        assertEquals(negative, average.getAltColor());
    }

    @Test
    public void averageDoesNotChooseBetweenDifferentPositiveColors() {
        DataSourceTrack first = new DataSourceTrack(null, "first", "first", null);
        DataSourceTrack second = new DataSourceTrack(null, "second", "second", null);
        first.setColor(Color.RED);
        second.setColor(Color.BLUE);

        AverageErrorBarTrack average = new AverageErrorBarTrack(
                "average", "Average", List.of(first, second),
                WindowFunction.mean, ErrorBarType.SEM, 0);

        assertFalse(Color.RED.equals(average.getColor()));
        assertFalse(Color.BLUE.equals(average.getColor()));
    }
}
