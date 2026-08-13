package org.igv.ui.util;

import org.igv.ui.panel.MainPanel;
import org.igv.ui.panel.Paintable;
import org.igv.track.Track;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/** A non-destructive cropped view over MainPanel's existing offscreen renderer. */
public final class ScreenshotView extends JComponent implements Paintable {

    private final MainPanel source;
    private final int xOffset;
    private final boolean includeCoordinates;
    private final Set<Track> includedTracks;

    public ScreenshotView(MainPanel source, boolean includeCoordinates, boolean includeTrackNames) {
        this(source, includeCoordinates, includeTrackNames, null);
    }

    public ScreenshotView(MainPanel source, boolean includeCoordinates, boolean includeTrackNames,
                          Set<Track> includedTracks) {
        this.source = source;
        source.updatePanelDimensions();
        this.xOffset = includeTrackNames ? 0 : source.getLeftOffset() + source.getDataPanelX();
        this.includeCoordinates = includeCoordinates;
        this.includedTracks = includedTracks;
        setSize(Math.max(1, source.getWidth() - xOffset),
                Math.max(1, getSnapshotHeight(false)));
    }

    @Override
    public void paintOffscreen(Graphics2D g, Rectangle rect, boolean batch) {
        Graphics2D cropped = (Graphics2D) g.create();
        cropped.clipRect(0, 0, getWidth(), getSnapshotHeight(batch));
        cropped.translate(-xOffset, 0);
        source.paintOffscreen(cropped,
                new Rectangle(0, 0, source.getWidth(), getSnapshotHeight(batch)),
                batch, includeCoordinates, includedTracks);
        cropped.dispose();
    }

    @Override
    public int getSnapshotHeight(boolean batch) {
        int fullHeight = includedTracks == null
                ? source.getSnapshotHeight(batch)
                : source.getSnapshotHeight(batch, includedTracks);
        int headerHeight = includeCoordinates ? 0 : source.getGenomicHeaderHeight();
        return Math.max(1, fullHeight - headerHeight);
    }
}
