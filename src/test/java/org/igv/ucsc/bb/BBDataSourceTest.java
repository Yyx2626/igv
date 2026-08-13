package org.igv.ucsc.bb;

import org.igv.data.DataTile;
import org.igv.feature.LocusScore;
import org.igv.feature.genome.Genome;
import org.igv.track.WindowFunction;
import org.igv.util.TestUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class BBDataSourceTest {

    @Test
    public void rawQueryBypassesPyramidWithoutChangingSelectedWindowFunction() throws IOException {
        Genome genome = TestUtils.mockUCSCGenome();
        BBFile bbFile = new BBFile(TestUtils.DATA_DIR + "bb/fixedStep.bw", genome);
        BBDataSource source = new BBDataSource(bbFile, genome);
        source.setWindowFunction(WindowFunction.max);

        DataTile rawTile = source.getRawData("1", 0, Integer.MAX_VALUE);
        List<LocusScore> rawScores = source.getRawScoresForRange("1", 0, Integer.MAX_VALUE, 0);

        assertNotNull(rawTile);
        assertEquals(rawTile.getValues().length, rawScores.size());
        for (int i = 0; i < rawScores.size(); i++) {
            assertEquals(rawTile.getValues()[i], rawScores.get(i).getScore(), 0f);
        }
        assertEquals(WindowFunction.max, source.getWindowFunction());
    }

    /**
     * A zoom record carries enough information to decode its own absolute maximum. This checks
     * that record-level decoding only; exact aggregation into caller-defined display/TSV bins is
     * tested separately from raw values because a zoom record can cross a final-bin boundary.
     */
    @Test
    public void absoluteMaxIsServedFromZoomPyramidNotRawFallback() throws IOException {
        Genome genome = TestUtils.mockUCSCGenome();
        String chr = "1";

        for (int zoomLevel = 0; zoomLevel <= 5; zoomLevel++) {
            BBFile bbFile = new BBFile(TestUtils.DATA_DIR + "bb/fixedStep.bw", genome);
            BBDataSource bbDataSource = new BBDataSource(bbFile, genome);

            bbDataSource.setWindowFunction(WindowFunction.min);
            List<LocusScore> minScores = bbDataSource.getPrecomputedSummaryScores(chr, 0, Integer.MAX_VALUE, zoomLevel);
            bbDataSource.setWindowFunction(WindowFunction.max);
            List<LocusScore> maxScores = bbDataSource.getPrecomputedSummaryScores(chr, 0, Integer.MAX_VALUE, zoomLevel);
            bbDataSource.setWindowFunction(WindowFunction.absoluteMax);
            List<LocusScore> absMaxScores = bbDataSource.getPrecomputedSummaryScores(chr, 0, Integer.MAX_VALUE, zoomLevel);

            assertNotNull("absoluteMax must be servable from the zoom pyramid like every other "
                    + "window function, not fall back to a different (coarser) data path", absMaxScores);
            assertEquals(minScores.size(), absMaxScores.size());
            for (int i = 0; i < absMaxScores.size(); i++) {
                float min = minScores.get(i).getScore();
                float max = maxScores.get(i).getScore();
                float expected = Math.abs(min) > Math.abs(max) ? min : max;
                assertEquals("zoom=" + zoomLevel + " bin=" + i,
                        expected, absMaxScores.get(i).getScore(), 0.000000001f);
            }
        }
    }


    @Test
    public void testBigWigZoom() throws IOException {

        //chr21:19,146,376-19,193,466
        String url = "https://www.encodeproject.org/files/ENCFF000ARZ/@@download/ENCFF000ARZ.bigWig";

        //String url = TestUtils.DATA_DIR + "bb/fixedStep.bw";
        String chr = "1";

        Genome genome = TestUtils.mockUCSCGenome();
        BBFile bbFile = new BBFile(url, genome);
        BBDataSource bbDataSource = new BBDataSource(bbFile, genome);

        int zoomLevel = 1;
        List<LocusScore> scores = bbDataSource.getPrecomputedSummaryScores(chr, 0, Integer.MAX_VALUE, zoomLevel);
        assertNotNull(scores);
        assertTrue(scores.size() > 0);

        //High resolutions -- there should be no precomputed scores (i.e. no zoom data).

        zoomLevel = 20;
        scores = bbDataSource.getPrecomputedSummaryScores(chr, 0, Integer.MAX_VALUE, zoomLevel);
        assertNull(scores);

    }

    @Test
    public void testBigWigWig() throws IOException {

        //chr21:19,146,376-19,193,466
        String url = "https://www.encodeproject.org/files/ENCFF000ARZ/@@download/ENCFF000ARZ.bigWig";

        //String url = TestUtils.DATA_DIR + "bb/fixedStep.bw";
        String chr = "1";
        int start = 72464570;
        int end = 72464687;

        // Expected values -- from manual inspection at igv.org/app
        float [] expectedValues =  {1.0f, 1.96f, 3.0f, 3.0f, 4.16f, 6.0f};

        Genome genome = TestUtils.mockUCSCGenome();
        BBFile bbFile = new BBFile(url, genome);
        BBDataSource bbDataSource = new BBDataSource(bbFile, genome);

        DataTile data = bbDataSource.getRawData(chr, start, end);
        assertNotNull(data);
        assertTrue(data.getValues().length == expectedValues.length);
        for(int i=0; i<expectedValues.length; i++) {
            assertEquals(expectedValues[i], data.getValues()[i], 0.000000001f);
        }

    }
}
