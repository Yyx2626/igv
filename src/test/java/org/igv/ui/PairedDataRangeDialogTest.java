package org.igv.ui;

import org.igv.renderer.DataRange;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PairedDataRangeDialogTest {

    @Test
    public void oppositeSignedAutoscaleRangesNegateTheTransferredScale() {
        DataRange top = new DataRange(0, 0, 150);
        DataRange bottom = new DataRange(-80, 0, 0);

        assertTrue(PairedDataRangeDialog.rangesHaveOppositeSigns(top, bottom));
        assertArrayEquals(new float[]{-150, 0, 0},
                PairedDataRangeDialog.flippedScale(0, 0, 150, top, bottom), 0f);
    }

    @Test
    public void sameSignedAutoscaleRangesReverseEndpointsWithoutNegating() {
        DataRange top = new DataRange(0, 0, 150);
        DataRange bottom = new DataRange(0, 0, 80);

        assertFalse(PairedDataRangeDialog.rangesHaveOppositeSigns(top, bottom));
        assertArrayEquals(new float[]{150, 0, 0},
                PairedDataRangeDialog.flippedScale(0, 0, 150, top, bottom), 0f);
    }

    @Test
    public void tinyAutoscaleRoundoffDoesNotHideOppositeSigns() {
        DataRange top = new DataRange(-4.7683716E-7f, 0, 150);
        DataRange bottom = new DataRange(-80, 0, 4.7683716E-7f);

        assertTrue(PairedDataRangeDialog.rangesHaveOppositeSigns(top, bottom));
    }

    @Test
    public void reversedSourceRangeStillUsesItsDataSign() {
        DataRange top = new DataRange(150, 0, 0);
        DataRange bottom = new DataRange(-80, 0, 0);

        assertTrue(PairedDataRangeDialog.rangesHaveOppositeSigns(top, bottom));
        assertArrayEquals(new float[]{0, 0, -150},
                PairedDataRangeDialog.flippedScale(150, 0, 0, top, bottom), 0f);
    }
}
