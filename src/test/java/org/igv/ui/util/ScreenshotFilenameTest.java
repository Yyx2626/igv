package org.igv.ui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenshotFilenameTest {

    @Test
    public void usesOneBasedInclusiveCoordinates() {
        assertEquals("chr1_101_200", ScreenshotFilename.rangeToken("chr1", 100, 200, false));
    }

    @Test
    public void reversesCoordinateOrderWhenInverted() {
        assertEquals("chr1_200_101", ScreenshotFilename.rangeToken("chr1", 100, 200, true));
    }

    @Test
    public void sanitizesChromosomeForFilesystemUse() {
        assertEquals("chr_1_1_10", ScreenshotFilename.rangeToken("chr/1", 0, 10, false));
    }
}
