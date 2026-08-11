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
                scores, 25, 75, ScreenshotDataExporter.ValueKind.VALUE));
    }

    @Test
    public void averageColumnsExposeNMeanSdAndSem() {
        List<LocusScore> scores = List.of(new AverageErrorLocusScore(0, 100, 5, 2, 1, 4));
        assertEquals("4", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.N));
        assertEquals("5.00000000", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.AVERAGE));
        assertEquals("2.00000000", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.SD));
        assertEquals("1.00000000", ScreenshotDataExporter.valueFor(scores, 0, 100, ScreenshotDataExporter.ValueKind.SEM));
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
