package org.igv.ui.panel;

import org.igv.renderer.DataRange;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegionalSettingsDialogTest {

    @Test
    public void pairRangesAcceptEqualAndSignReversedTriples() {
        assertTrue(RegionalSettingsDialog.pairRangesCompatible(
                new DataRange(0, 5, 10), new DataRange(0, 5, 10)));
        assertTrue(RegionalSettingsDialog.pairRangesCompatible(
                new DataRange(0, 0, 10), new DataRange(-10, 0, 0)));
        assertTrue(RegionalSettingsDialog.pairRangesCompatible(
                new DataRange(0, 0, 10), new DataRange(10, 0, 0)));
    }

    @Test
    public void pairRangesRequireEquivalentMidpointAndScaleType() {
        assertFalse(RegionalSettingsDialog.pairRangesCompatible(
                new DataRange(0, 2, 10), new DataRange(-10, -3, 0)));
        assertFalse(RegionalSettingsDialog.pairRangesCompatible(
                new DataRange(0, 0, 10, true, false),
                new DataRange(-10, 0, 0, true, true)));
    }

    @Test
    public void pairSwapRequiresOneToOneRangeEquality() {
        assertTrue(RegionalSettingsDialog.pairRangesExactlyEqual(
                new DataRange(-10, 0, 20), new DataRange(-10, 0, 20)));
        assertFalse(RegionalSettingsDialog.pairRangesExactlyEqual(
                new DataRange(0, 0, 10), new DataRange(-10, 0, 0)));
    }
}
