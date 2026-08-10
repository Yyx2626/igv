package org.igv.ui.util;

import org.igv.feature.Range;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.ReferenceFrame;

import java.util.List;
import java.util.stream.Collectors;

/** Builds filesystem-safe, display-coordinate suffixes for screenshot outputs. */
public final class ScreenshotFilename {

    private ScreenshotFilename() {
    }

    public static String currentCoordinateSuffix() {
        List<ReferenceFrame> frames = FrameManager.getFrames().stream()
                .filter(ReferenceFrame::isVisible)
                .toList();
        if (frames.isEmpty()) return "";
        return frames.stream()
                .map(ScreenshotFilename::rangeToken)
                .collect(Collectors.joining("__"));
    }

    private static String rangeToken(ReferenceFrame frame) {
        Range range = frame.getCurrentRange();
        return rangeToken(range.getChr(), range.getStart(), range.getEnd(), frame.isInverted());
    }

    static String rangeToken(String chromosome, int start0, int end0, boolean inverted) {
        String safeChromosome = chromosome == null ? "unknown"
                : chromosome.replaceAll("[^A-Za-z0-9._-]", "_");
        int start1 = Math.max(0, start0) + 1;
        int end1 = Math.max(start1, end0);
        return inverted
                ? safeChromosome + "_" + end1 + "_" + start1
                : safeChromosome + "_" + start1 + "_" + end1;
    }
}
