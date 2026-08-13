package org.igv.ui.util;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;
import org.igv.feature.Strand;
import org.igv.track.DataTrack;
import org.igv.track.LoadedDataInterval;
import org.igv.track.RenderContext;
import org.igv.track.Track;
import org.junit.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ScreenshotDataExporterTest {

    @Test
    public void selectedOnlyFilterPreservesDisplayOrderAndExcludesUnselectedTracks() {
        TestDataTrack first = new TestDataTrack();
        TestDataTrack second = new TestDataTrack();
        Set<Track> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.add(second);

        assertEquals(List.of(second), ScreenshotDataExporter.filterExportableTracks(
                List.of(first, second), selected));
        assertEquals(List.of(first, second), ScreenshotDataExporter.filterExportableTracks(
                List.of(first, second), null));
    }

    @Test
    public void regularValuesAreOverlapWeightedIntoRequestedBin() {
        List<LocusScore> scores = List.of(new BasicScore(0, 50, 2), new BasicScore(50, 100, 4));
        assertEquals("3.00000000", ScreenshotDataExporter.valueFor(
                scores, 25, 75, ScreenshotDataExporter.ValueKind.VALUE, 0f, null));
    }

    @Test
    public void regularTsvValueUsesTheSelectedWindowFunctionInTheFinalBin() {
        List<LocusScore> scores = List.of(
                new BasicScore(0, 10, 2f),
                new BasicScore(10, 20, 8f),
                new BasicScore(20, 30, -5f));

        assertEquals("8.00000000", ScreenshotDataExporter.valueFor(
                scores, 5, 25, ScreenshotDataExporter.ValueKind.VALUE, 0f,
                null, -1, org.igv.track.WindowFunction.max));
        assertEquals("-5.00000000", ScreenshotDataExporter.valueFor(
                scores, 5, 25, ScreenshotDataExporter.ValueKind.VALUE, 0f,
                null, -1, org.igv.track.WindowFunction.min));
        assertEquals("3.25000000", ScreenshotDataExporter.valueFor(
                scores, 5, 25, ScreenshotDataExporter.ValueKind.VALUE, 0f,
                null, -1, org.igv.track.WindowFunction.mean));
    }

    @Test
    public void averageColumnsExposeNMeanSdAndSem() {
        List<LocusScore> scores = List.of(new AverageErrorLocusScore(0, 100, 5, 2, 1, 4));
        assertEquals("4", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.N, 0f, null));
        assertEquals("5.00000000", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, null));
        assertEquals("2.00000000", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.SD, 0f, null));
        assertEquals("1.00000000", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.SEM, 0f, null));
    }

    @Test
    public void posAndNegColumnsReportTheExtremeOnEachSideOfBaseline() {
        List<LocusScore> scores = List.of(
                new BasicScore(0, 10, 3), new BasicScore(10, 20, -5),
                new BasicScore(20, 30, 8), new BasicScore(30, 40, -1));
        assertEquals("8.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 40, ScreenshotDataExporter.ValueKind.POS, 0f, null));
        assertEquals("-5.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 40, ScreenshotDataExporter.ValueKind.NEG, 0f, null));
    }

    @Test
    public void posColumnIsNaWhenBinHasNoValueAboveBaseline() {
        List<LocusScore> scores = List.of(new BasicScore(0, 10, -5));
        assertEquals(null, ScreenshotDataExporter.valueFor(
                scores, 0, 10, ScreenshotDataExporter.ValueKind.POS, 0f, null));
    }

    @Test
    public void signFilterPicksTheMatchingGroupWhenABinHasBothAnAverageAboveAndBelowBaseline() {
        List<LocusScore> scores = List.of(
                new AverageErrorLocusScore(0, 100, 5, 2, 1, 4),
                new AverageErrorLocusScore(0, 100, -3, 1, 0.5f, 4));
        assertEquals("5.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, true));
        assertEquals("2.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.SD, 0f, true));
        assertEquals("-3.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, false));
        assertEquals("0.500000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.SEM, 0f, false));
    }

    @Test
    public void signFilteredColumnsReportThePeakNotAWidthWeightedAverageOfPeaks() {
        // Same rationale as NumericTrackBinner's averageEnvelopeReportsThePeakNotAWidthWeightedAverageOfPeaks:
        // a wide near-zero native entry must not dilute a narrow real peak toward zero when both
        // overlap the same output bin.
        List<LocusScore> scores = List.of(
                new AverageErrorLocusScore(0, 90, 0.05f, 0.01f, 0.005f, 3),
                new AverageErrorLocusScore(90, 100, 5f, 1f, 0.5f, 3));
        assertEquals("5.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, true));
        assertEquals("3", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.N, 0f, true));
        assertEquals("1.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.SD, 0f, true));
    }

    @Test
    public void averageMemberColumnsComeFromTheSameSelectedNonePeak() {
        List<LocusScore> scores = List.of(
                new AverageErrorLocusScore(0, 50, 3f, 2f, 1f, 3,
                        new float[]{1f, 3f, 5f}),
                new AverageErrorLocusScore(50, 100, 8f, 2f, 1f, 3,
                        new float[]{6f, 8f, 10f}));

        assertEquals("6.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.MEMBER, 0f, true, 0));
        assertEquals("8.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.MEMBER, 0f, true, 1));
        assertEquals("10.0000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.MEMBER, 0f, true, 2));
        assertEquals("8.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, true));
    }

    @Test
    public void tsvAverageUsesEachMembersOwnMaximumWithinTheOutputBin() {
        List<LocusScore> scores = List.of(
                new AverageErrorLocusScore(0, 10, 4f, 0f, 0f, 3,
                        new float[]{10f, 1f, 1f}, AverageErrorLocusScore.Group.RAW, 0f),
                new AverageErrorLocusScore(10, 20, 4f, 0f, 0f, 3,
                        new float[]{1f, 10f, 1f}, AverageErrorLocusScore.Group.RAW, 0f),
                new AverageErrorLocusScore(20, 30, 4f, 0f, 0f, 3,
                        new float[]{1f, 1f, 10f}, AverageErrorLocusScore.Group.RAW, 0f));

        for (int member = 0; member < 3; member++) {
            assertEquals("10.0000000", ScreenshotDataExporter.valueFor(
                    scores, 0, 30, ScreenshotDataExporter.ValueKind.MEMBER, 0f, true, member));
        }
        assertEquals("10.0000000", ScreenshotDataExporter.valueFor(
                scores, 0, 30, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, true));
        assertEquals("0.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 30, ScreenshotDataExporter.ValueKind.SEM, 0f, true));
    }

    @Test
    public void tsvSelectsAverageGroupByIdentityNotByMeanSign() {
        List<LocusScore> scores = List.of(
                new AverageErrorLocusScore(0, 10, 0f, 0f, 0f, 2,
                        new float[]{-10f, Float.NaN}, AverageErrorLocusScore.Group.RAW, 20f));

        assertEquals("-10.0000000", ScreenshotDataExporter.valueFor(
                scores, 0, 10, ScreenshotDataExporter.ValueKind.MEMBER, 0f, false, 0));
        assertEquals("20.0000000", ScreenshotDataExporter.valueFor(
                scores, 0, 10, ScreenshotDataExporter.ValueKind.MEMBER, 0f, false, 1));
        assertEquals("5.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 10, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, false));
    }

    @Test
    public void regularAverageStatsAreRecomputedFromExportedMemberValues() {
        List<LocusScore> scores = List.of(
                new AverageErrorLocusScore(0, 50, 2f, 1f, 0.5f, 2,
                        new float[]{1f, 3f}),
                new AverageErrorLocusScore(50, 100, 6f, 1f, 0.5f, 2,
                        new float[]{5f, 7f}));

        assertEquals("3.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.MEMBER, 0f, null, 0));
        assertEquals("5.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.MEMBER, 0f, null, 1));
        assertEquals("4.00000000", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE, 0f, null));
        assertEquals("1.41421354", ScreenshotDataExporter.valueFor(
                scores, 0, 100, ScreenshotDataExporter.ValueKind.SD, 0f, null));
    }

    @Test
    public void sequenceColumnUsesOneBaseBinsAndDisplayedStrand() {
        byte[] sequence = "ACGT".getBytes();
        assertEquals("Sequence_genomic_plus", ScreenshotDataExporter.sequenceHeader(Strand.POSITIVE));
        assertEquals("Sequence_genomic_minus", ScreenshotDataExporter.sequenceHeader(Strand.NEGATIVE));
        assertEquals("C", ScreenshotDataExporter.sequenceValue(sequence, 100, 101, 102, Strand.POSITIVE));
        assertEquals("G", ScreenshotDataExporter.sequenceValue(sequence, 100, 101, 102, Strand.NEGATIVE));
        assertEquals(null, ScreenshotDataExporter.sequenceValue(sequence, 100, 101, 103, Strand.POSITIVE));
        assertEquals("T", ScreenshotDataExporter.sequenceValue(
                "A".getBytes(), 100, 100, 101, Strand.POSITIVE, true));
    }

    @Test
    public void sourceColumnUsesCompactSelfAndPairFormats() {
        assertEquals("chr1:840-850",
                ScreenshotDataExporter.formatSource(null, "chr1", 840, 850));
        assertEquals("[negative track]chr1:840-850",
                ScreenshotDataExporter.formatSource("negative track", "chr1", 840, 850));
    }

    @Test
    public void tsvHeadersUseUnderscoresInsideNamesAndDotsBetweenFields() {
        assertEquals("my_track.average",
                ScreenshotDataExporter.cleanHeader("my track.average"));
        assertEquals("my_track.source",
                ScreenshotDataExporter.cleanHeader("my track.source"));
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
