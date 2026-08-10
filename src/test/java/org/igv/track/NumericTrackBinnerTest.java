package org.igv.track;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NumericTrackBinnerTest {

    @Test
    public void resamplesIntoRequestedEqualBins() {
        List<LocusScore> input = List.of(
                new BasicScore(0, 50, 2),
                new BasicScore(50, 100, 4));

        List<LocusScore> output = NumericTrackBinner.bin(input, 0, 100, 4);

        assertEquals(4, output.size());
        assertEquals(0, output.get(0).getStart());
        assertEquals(25, output.get(0).getEnd());
        assertEquals(2f, output.get(0).getScore(), 0f);
        assertEquals(4f, output.get(3).getScore(), 0f);
    }

    @Test
    public void preservesAverageErrorScoreType() {
        List<LocusScore> input = List.of(new AverageErrorLocusScore(0, 100, 5, 2, 1, 4));

        List<LocusScore> output = NumericTrackBinner.bin(input, 0, 100, 2);

        assertTrue(output.get(0) instanceof AverageErrorLocusScore);
        AverageErrorLocusScore score = (AverageErrorLocusScore) output.get(0);
        assertEquals(4, score.getN());
        assertEquals(2f, score.getSd(), 0f);
        assertEquals(1f, score.getSem(), 0f);
    }
}
