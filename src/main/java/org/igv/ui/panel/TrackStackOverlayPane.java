package org.igv.ui.panel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper placed between {@link ScrollableTrackContainer} and the {@code JScrollPane}
 * that scrolls it (see {@code MainPanel}). Exists solely to host a floating, transparent
 * {@link DividerHoverOverlay} for each {@link TrackPanelDivider} in the wrapped container,
 * positioned to straddle that divider's real (possibly zero-height) bounds - see
 * {@link DividerHoverOverlay} for why. The wrapped container's own {@code BoxLayout} and all
 * of its existing add/remove/reorder logic in {@code MainPanel} are completely unaffected;
 * this class only ever reads its children's bounds after they're laid out, never manages them
 * directly.
 * <p>
 * Two things this class does differently from an earlier, reverted version, both aimed at a
 * DnD-loading regression that version caused (confirmed via live testing: first attempt broke
 * file drops outright; a second attempt, after fixing an unrelated DnD-action bug, instead
 * caused only some of several simultaneously-dropped files to load, then got stuck for
 * further drops):
 * <ol>
 *   <li><b>Deferred sync.</b> {@code validateTree()} schedules {@link #syncOverlays} via
 *   {@code SwingUtilities.invokeLater} instead of calling it inline. Modifying this
 *   container's children (adding/removing overlay components) synchronously, re-entrantly,
 *   from inside its own {@code validateTree()} - which is itself often invoked from inside
 *   `MainPanel.rebuildDividers()`'s own add/remove/revalidate sequence when tracks change -
 *   is exactly the kind of nested container-mutation-during-layout pattern that can leave
 *   Swing's layout/RepaintManager bookkeeping inconsistent in ways that only surface
 *   intermittently. Running it on a later, separate event-queue turn avoids that
 *   re-entrancy entirely. Multiple rapid triggers (e.g. one rebuildDividers() call per file
 *   when several files are dropped at once) coalesce into a single deferred sync via the
 *   {@code syncPending} flag, rather than running once per trigger.</li>
 *   <li><b>Reused, position-indexed overlay pool.</b> {@code TrackPanelDivider} instances
 *   themselves get discarded and recreated by {@code MainPanel.rebuildDividers()} on every
 *   track add/remove/reorder, so overlays can't be matched to "the same divider" by identity
 *   across calls - but the *count* of dividers usually doesn't change as often as the
 *   dividers themselves. {@link #overlayPool} is indexed by position (top-to-bottom order):
 *   an existing pooled overlay is simply {@link DividerHoverOverlay#retarget retargeted} to
 *   whatever divider now occupies that slot and repositioned, with no add/remove on this
 *   container at all; only a change in the *number* of dividers adds/removes the difference,
 *   instead of tearing down and rebuilding every overlay on every sync.</li>
 * </ol>
 */
public class TrackStackOverlayPane extends JLayeredPane implements Scrollable {

    private final ScrollableTrackContainer inner;
    private final List<DividerHoverOverlay> overlayPool = new ArrayList<>();
    private boolean syncPending = false;

    public TrackStackOverlayPane(ScrollableTrackContainer inner) {
        this.inner = inner;
        add(inner, DEFAULT_LAYER);
    }

    @Override
    public void doLayout() {
        inner.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public void validateTree() {
        // Lays out `this` (sizes `inner` to fill it, via doLayout() above) and then
        // recursively validates `inner`, which runs ITS OWN BoxLayout and positions the
        // TrackPanelScrollPane/TrackPanelDivider children - by the time this returns, those
        // children have correct, current bounds. syncOverlays() itself is deferred - see
        // class javadoc - so it isn't called from here directly.
        super.validateTree();
        scheduleSync();
    }

    /**
     * Forces an immediate (non-deferred) re-sync. Called by {@link DividerHoverOverlay} on
     * {@code dragEnter} for an in-progress track-panel-reorder drag: unlike a plain mouse
     * gesture, a drag's {@code dragEnter}/{@code dragOver}/{@code drop} sequence reads
     * {@code realDivider} fresh every time rather than snapshotting it once (see that class -
     * a drop legitimately wants the freshest possible divider, not a snapshot), so if the
     * normal deferred sync scheduled by validateTree() hasn't run yet by drag time - plausible
     * since native DnD tracking can run a loop that starves queued invokeLater callbacks until
     * the drag ends - the overlay could still be retarget()ed to a divider instance discarded
     * by a since-superseded MainPanel.rebuildDividers() call, whose stale abovePane no longer
     * matches any current track panel. That silently drops the dragged track out of the
     * reordered list entirely instead of reinserting it - calling this before the drop can
     * land closes that window.
     */
    void syncOverlaysNow() {
        syncOverlays();
    }

    private void scheduleSync() {
        if (syncPending) {
            return;
        }
        syncPending = true;
        SwingUtilities.invokeLater(() -> {
            syncPending = false;
            syncOverlays();
        });
    }

    private void syncOverlays() {
        int width = getWidth();
        int margin = TrackPanelDivider.HOVER_MARGIN;
        int totalHeight = getHeight();

        List<TrackPanelDivider> dividers = new ArrayList<>();
        for (Component c : inner.getComponents()) {
            if (c instanceof TrackPanelDivider divider) {
                dividers.add(divider);
            }
        }

        int i = 0;
        for (; i < dividers.size(); i++) {
            TrackPanelDivider divider = dividers.get(i);
            Rectangle bounds = divider.getBounds();
            int y = Math.max(0, bounds.y - margin);
            int bottom = Math.min(totalHeight, bounds.y + bounds.height + margin);

            DividerHoverOverlay overlay;
            if (i < overlayPool.size()) {
                overlay = overlayPool.get(i);
                overlay.retarget(divider);
            } else {
                overlay = new DividerHoverOverlay(divider);
                overlayPool.add(overlay);
                add(overlay, PALETTE_LAYER);
            }

            if (bottom > y) {
                overlay.setBounds(0, y, width, bottom - y);
                overlay.setVisible(true);
            } else {
                overlay.setVisible(false);
            }
        }

        // Fewer dividers than before - drop the now-unneeded tail of the pool.
        for (int j = overlayPool.size() - 1; j >= i; j--) {
            DividerHoverOverlay extra = overlayPool.remove(j);
            remove(extra);
        }
    }

    // Scrollable - delegates entirely to the wrapped container, so the enclosing JScrollPane
    // behaves exactly as it did when it wrapped `inner` directly.

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return inner.getPreferredScrollableViewportSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return inner.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return inner.getScrollableBlockIncrement(visibleRect, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return inner.getScrollableTracksViewportWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return inner.getScrollableTracksViewportHeight();
    }

    @Override
    public Dimension getPreferredSize() {
        return inner.getPreferredSize();
    }
}
