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

    /**
     * absoluteMax used to be unreconstructable from bigwig zoom records (BBFile.decodeZoomData
     * had no case for it) and BBDataSource special-cased it to always return null, forcing a
     * fallback to a raw-data path with its own, much coarser resolution than min/max/mean get
     * from the same zoom level - the actual cause of a since-fixed bug where an "Average With
     * Error Bar" track computed wildly inflated values for members left on their default
     * WindowFunction.none (resolved to absoluteMax). Zoom records already carry both min and
     * max, so absoluteMax needs no extra data - this exercises the local, network-independent
     * fixedStep.bw fixture (which does have zoom data, unlike the other local bigwig fixtures)
     * at every zoom level it has data for.
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