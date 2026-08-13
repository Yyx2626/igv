package org.igv.ui.util;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;
import org.igv.feature.Strand;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScreenshotDataExporterTest {

    @Test
    public void regularValuesAreOverlapWeightedIntoRequestedBin() {
        List<LocusScore> scores = List.of(new BasicScore(0, 50, 2), new BasicScore(50, 100, 4));
        assertEquals("3.00000000", ScreenshotDataExporter.valueFor(
                scores, 25, 75, ScreenshotDataExporter.ValueKind.VALUE, 0f, null));
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
}
