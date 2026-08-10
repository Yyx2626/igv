package org.igv.renderer;

import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;

public class DataRangeFlipTest {

    @Test
    public void flippedRangePreservesSettingsAndExchangesEndpoints() {
        DataRange original = new DataRange(-2, 1, 8, false, true);
        original.setMidlineColor(Color.MAGENTA);

        DataRange flipped = original.flipped();

        assertEquals(8, flipped.getMinimum(), 0);
        assertEquals(-2, flipped.getMaximum(), 0);
        assertEquals(1, flipped.getBaseline(), 0);
        assertFalse(flipped.isDrawBaseline());
        assertTrue(flipped.isLog());
        assertTrue(flipped.isFlipAxis());
        assertEquals(Color.MAGENTA, flipped.getMidlineColor());
        assertEquals(-2, original.getMinimum(), 0);
    }
}
