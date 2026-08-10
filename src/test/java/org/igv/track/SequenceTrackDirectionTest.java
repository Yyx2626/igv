package org.igv.track;

import org.igv.feature.Strand;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SequenceTrackDirectionTest {

    @Test
    public void coordinateInversionReversesDisplayedArrow() {
        assertTrue(SequenceTrack.isArrowPointingRight(Strand.POSITIVE, false));
        assertFalse(SequenceTrack.isArrowPointingRight(Strand.POSITIVE, true));
        assertFalse(SequenceTrack.isArrowPointingRight(Strand.NEGATIVE, false));
        assertTrue(SequenceTrack.isArrowPointingRight(Strand.NEGATIVE, true));
    }
}
