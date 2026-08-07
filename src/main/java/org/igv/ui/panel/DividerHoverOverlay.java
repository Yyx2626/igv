package org.igv.ui.panel;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;

/**
 * A fully transparent, absolutely-positioned overlay that straddles one {@link
 * TrackPanelDivider}'s current position (see {@link TrackStackOverlayPane}, which creates,
 * positions, and discards these - deferred, never re-entrantly during its own layout pass;
 * see that class's javadoc for why that distinction turned out to matter). Exists purely so
 * a divider can have a genuinely zero-height layout footprint (tracks above/below sit flush
 * against each other, no reserved gap) while remaining draggable/right-clickable: this
 * overlay's own bounds extend {@link TrackPanelDivider#HOVER_MARGIN} pixels beyond the real
 * divider's bounds on both sides, and every mouse/drop event it receives is simply forwarded
 * to the real divider unchanged (translated to its coordinate space) - all the actual
 * drag-resize, right-click menu, and drop-to-reorder logic still lives entirely in {@link
 * TrackPanelDivider}.
 * <p>
 * Paints nothing (fully transparent): the real divider underneath still does its own
 * border-color painting when its configured height is &gt; 0, and this overlay just needs to
 * not obscure it.
 * <p>
 * The DropTarget below declares ACTION_COPY_OR_MOVE (not just ACTION_MOVE, unlike the real
 * divider's own DropTarget which only ever needs to accept internal track-panel-reorder
 * drags) since {@link TrackPanelDivider#handleTrackPanelDrop} falls back to forwarding
 * unrecognized transfers (e.g. a file dragged in from Finder, which arrives as ACTION_COPY)
 * to {@code MainPanel.drop(...)} - if this overlay ever sits under the drop point, it must
 * accept whatever action the drag source actually proposes.
 */
class DividerHoverOverlay extends JComponent {

    private TrackPanelDivider realDivider;

    /**
     * Snapshot of {@link #realDivider} taken at mousePressed, used for the rest of that one
     * gesture (drag/release) instead of re-reading realDivider each time. Without this, a
     * {@code retarget()} landing between this overlay's mousePressed and a later
     * mouseDragged/mouseReleased - entirely possible, since retargeting happens on a
     * deferred, independent timer (see TrackStackOverlayPane) with no awareness of an
     * in-progress gesture - would forward those later events to a DIFFERENT
     * TrackPanelDivider instance than the one that actually received the press. That
     * instance's own dragStartY/originalAboveHeight fields were never initialized (its own
     * mousePressed never ran), defaulting to 0, which made TrackPanelDivider.mouseDragged
     * compute a wildly wrong delta and snap the track above to some huge, spurious height -
     * this was the "border-height randomly jumps huge" bug reported after this overlay was
     * (re)introduced.
     */
    private TrackPanelDivider gestureDivider;

    DividerHoverOverlay(TrackPanelDivider realDivider) {
        this.realDivider = realDivider;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));

        MouseAdapter forwarder = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                forward(e);
            }
        };
        addMouseListener(forwarder);
        addMouseMotionListener(forwarder);

        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                resync();
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                realDivider.handleTrackPanelDrop(dtde);
            }
        }, true);
    }

    /**
     * Forces the enclosing TrackStackOverlayPane to re-sync immediately, so that by the time
     * this gesture's drop() runs, realDivider is guaranteed current - see
     * TrackStackOverlayPane.syncOverlaysNow() for why a drop can't just trust whatever
     * realDivider already happens to be, the way the mouse-gesture path's gestureDivider
     * snapshot does.
     */
    private void resync() {
        Container parent = getParent();
        if (parent instanceof TrackStackOverlayPane) {
            ((TrackStackOverlayPane) parent).syncOverlaysNow();
        }
    }

    /**
     * Re-targets this (reused, not recreated) overlay to a different TrackPanelDivider
     * instance - see TrackStackOverlayPane.syncOverlays(), which only actually adds/removes
     * overlay components when the set of dividers changes shape, but always re-targets +
     * repositions existing ones to whichever divider now occupies that slot (dividers
     * themselves get recreated by MainPanel.rebuildDividers() on every track add/remove, so
     * "the same divider" doesn't stay valid across those calls even though "a divider at
     * roughly this position" does).
     */
    void retarget(TrackPanelDivider newDivider) {
        this.realDivider = newDivider;
    }

    private void forward(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            gestureDivider = realDivider;
        }
        TrackPanelDivider target = gestureDivider != null ? gestureDivider : realDivider;
        target.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, target));
        if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            gestureDivider = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Intentionally blank - see class javadoc.
    }
}
