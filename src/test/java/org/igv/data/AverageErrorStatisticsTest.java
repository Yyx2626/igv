package org.igv.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AverageErrorStatisticsTest {

    @Test
    public void nonnegativeSemStopsExactlyAtZero() {
        float[] stats = AverageErrorStatistics.calculate(new float[]{0f, 0f, 10f});

        assertEquals(0f, stats[0] - stats[2], 0f);
        assertTrue("SD remains statistical SD rather than being clipped", stats[1] > stats[0]);
    }

    @Test
    public void nonpositiveSemStopsExactlyAtZero() {
        float[] stats = AverageErrorStatistics.calculate(new float[]{0f, 0f, -10f});

        assertEquals(0f, stats[0] + stats[2], 0f);
        assertTrue("SD remains statistical SD rather than being clipped", stats[1] > -stats[0]);
    }

    @Test
    public void missingValuesUseConfiguredReplacement() {
        float[] stats = AverageErrorStatistics.calculateReplacingNaN(
                new float[]{6f, Float.NaN, 0f}, 3f);

        assertEquals(3f, stats[0], 0f);
        assertEquals(3f, stats[1], 0f);
        assertEquals((float) Math.sqrt(3), stats[2], 0.000001f);
    }
}
