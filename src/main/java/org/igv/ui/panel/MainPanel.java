package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.prefs.PreferencesManager;
import org.igv.track.AttributeManager;
import org.igv.track.Track;
import org.igv.track.DataType;
import org.igv.track.TrackMenuUtils;
import org.igv.ui.IGV;
import org.igv.ui.util.UIUtilities;
import org.igv.util.LongRunningTask;
import org.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import static org.igv.prefs.Constants.*;

/**
 * @author jrobinso
 * @date Sep 10, 2010
 */
public class MainPanel extends JPanel implements Paintable, DropTargetListener {

    private static Logger log = LogManager.getLogger(MainPanel.class);

    IGV igv;

    // Guards against processing the same native drop gesture twice - e.g. if it reaches
    // drop() both directly (MainPanel's own top-level DropTarget) and via a descendant's
    // forwarding DropTarget (see installDropTargetRecursively). A genuine new drop always
    // creates a new DropTargetDropEvent instance, so this can't block legitimate drops.
    private DropTargetDropEvent lastProcessedDrop;

    // private static final int DEFAULT_NAME_PANEL_WIDTH = 160;

    private int namePanelX;
    private int namePanelWidth = PreferencesManager.getPreferences().getAsInt(NAME_PANEL_WIDTH);
    private int attributePanelX;
    private int attributePanelWidth;
    private int dataPanelX;
    private int dataPanelWidth;

    public IGVPanel applicationHeaderPanel;
    public HeaderPanelContainer headerPanelContainer;
    private ScrollableTrackContainer trackPanelContainer;
    private JScrollPane trackPanelScrollPane;
    private NameHeaderPanel nameHeaderPanel;
    private AttributeHeaderPanel attributeHeaderPanel;
    private HeaderSelectAllPanel headerSelectAllPanel;
    private GroupTabsPanel groupTabsPanel;

    private int hgap = 5;
    private JScrollPane headerScrollPane;


    public MainPanel(IGV igv) {
        this.igv = igv;

        initComponents();

        // Enable drag-and-drop for files and URLs.
        // DropTarget events don't bubble from child to parent in AWT/Swing, so we
        // install MainPanel's own DropTarget first, then recursively install
        // forwarding DropTargets on all descendants that don't already have one.
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, this, true);
        installDropTargetRecursively(this);

        //Load IGV logo
//        try {
//            BufferedImage logo = ImageIO.read(getClass().getResource("resources/IGV_64.png"));
//            JLabel picLabel = new JLabel(new ImageIcon(logo));
//            picLabel.setVerticalAlignment(SwingConstants.CENTER);
//            nameHeaderPanel.add(picLabel);
//        } catch (IOException e) {
//            //pass
//        }

        addComponentListener(new ComponentListener() {

            public void componentResized(ComponentEvent componentEvent) {
                revalidateTrackPanels();
                igv.repaint();
            }

            public void componentMoved(ComponentEvent componentEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void componentShown(ComponentEvent componentEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void componentHidden(ComponentEvent componentEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
    }


    public void collapseNamePanel() {
        namePanelWidth = 0;
        revalidateTrackPanels();
    }

    public void expandNamePanel() {
        namePanelWidth = PreferencesManager.getPreferences().getAsInt(NAME_PANEL_WIDTH);
        revalidateTrackPanels();
    }

    public void setNamePanelWidth(int width) {
        this.namePanelWidth = width;
        revalidateTrackPanels();
    }

    public void revalidateTrackPanels() {
        updatePanelDimensions();
        UIUtilities.invokeOnEventThread(() -> {
            this.applicationHeaderPanel.invalidate();
            for (TrackPanel tp : this.getTrackPanels()) {
                tp.invalidate();
            }
            this.invalidate(); // this should not be neccessary, but is harmless
            this.validate();
            this.repaint();  // Repaint to update divider lines (horizontal separator + track area vertical dividers)
        });
    }

    /** Repaint ROI bars without waiting for asynchronous track loading. */
    public void repaintRegionOfInterestPanels() {
        UIUtilities.invokeAndWaitOnEventThread(headerPanelContainer::repaintRegionOfInterestPanels);
    }

    public void removeHeader() {
        remove(headerScrollPane);
        trackPanelScrollPane.setBorder(null);
        revalidate();
    }

    public void restoreHeader() {
        add(headerScrollPane, BorderLayout.NORTH);
        trackPanelScrollPane.setBorder(createHeaderSeparatorBorder());
        revalidate();
    }

    /**
     * Creates a border with a 1px top line to visually separate the header from the track area.
     */
    private static javax.swing.border.Border createHeaderSeparatorBorder() {
        Color dividerColor = Globals.isDarkMode() ? Color.GRAY : Color.LIGHT_GRAY;
        return BorderFactory.createMatteBorder(1, 0, 0, 0, dividerColor);
    }


    @Override
    public void doLayout() {
        super.doLayout();
        applicationHeaderPanel.doLayout();
        for (TrackPanel tp : getTrackPanels()) {
            tp.getScrollPane().doLayout();
        }
    }


    private void initComponents() {

        setPreferredSize(new java.awt.Dimension(1021, 510));
        setLayout(new java.awt.BorderLayout());

        nameHeaderPanel = new NameHeaderPanel();
        nameHeaderPanel.setMinimumSize(new java.awt.Dimension(0, 0));
        nameHeaderPanel.setPreferredSize(new java.awt.Dimension(0, 0));

        attributeHeaderPanel = new AttributeHeaderPanel();
        attributeHeaderPanel.setDebugGraphicsOptions(javax.swing.DebugGraphics.NONE_OPTION);
        attributeHeaderPanel.setMinimumSize(new java.awt.Dimension(0, 0));
        attributeHeaderPanel.setPreferredSize(new java.awt.Dimension(0, 0));


        headerPanelContainer = new HeaderPanelContainer();
        headerScrollPane = new JScrollPane();
        headerScrollPane.setBorder(null);
        // headerScrollPane.setForeground(new java.awt.Color(153, 153, 153));
        headerScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        headerScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        headerScrollPane.setPreferredSize(new java.awt.Dimension(1021, 130));
        add(headerScrollPane, java.awt.BorderLayout.NORTH);

        applicationHeaderPanel = new IGVPanel(this);

        // Leftmost slot: hosts the select-all checkbox (when SHOW_SELECTION_PANEL is on)
        // and provides spacing for the drag-handle column. IGVPanel.doLayout() sizes
        // this child to the full leftOffset width (selection + drag handle).
        headerSelectAllPanel = new HeaderSelectAllPanel();
        headerSelectAllPanel.setCheckBoxVisible(
                PreferencesManager.getPreferences().getAsBoolean(SHOW_SELECTION_PANEL));
        applicationHeaderPanel.add(headerSelectAllPanel);

        applicationHeaderPanel.add(nameHeaderPanel);
        applicationHeaderPanel.add(attributeHeaderPanel);
        applicationHeaderPanel.add(headerPanelContainer);
        headerScrollPane.setViewportView(applicationHeaderPanel);


        // Custom panel that implements Scrollable to prevent viewport from stretching it
        trackPanelContainer = new ScrollableTrackContainer(this);

        // Wraps trackPanelContainer purely to host floating DividerHoverOverlay components -
        // see TrackStackOverlayPane's javadoc. trackPanelContainer's own add/remove/reorder
        // logic below is completely unaffected; this wrapper never manages its children.
        TrackStackOverlayPane trackStackOverlayPane = new TrackStackOverlayPane(trackPanelContainer);

        trackPanelScrollPane = new JScrollPane(trackStackOverlayPane);
        trackPanelScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        trackPanelScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        trackPanelScrollPane.setBorder(createHeaderSeparatorBorder());
        trackPanelScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        trackPanelScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Rectangle viewRect = trackPanelScrollPane.getViewport().getViewRect();
                for (Component c : trackPanelContainer.getComponents()) {
                    if (c instanceof TrackPanelScrollPane) {
                        TrackPanelScrollPane tsp = (TrackPanelScrollPane) c;
                        if (c.getBounds().intersects(viewRect)) {
                            tsp.trackPanel.getNamePanel().repaint();
                        }
                    }
                }
            }
        });
        add(trackPanelScrollPane, BorderLayout.CENTER);

        // trackPanelContainer doesn't stretch to fill the viewport (see
        // ScrollableTrackContainer.getScrollableTracksViewportHeight()), so when there's
        // more viewport height than track content, the leftover dead space below the last
        // track belongs to the JViewport itself, not to trackPanelContainer - the listener
        // added in ScrollableTrackContainer's own constructor never sees those clicks.
        trackPanelScrollPane.getViewport().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    TrackMenuUtils.clearAllTrackSelections();
                }
            }
        });

        groupTabsPanel = new GroupTabsPanel();
        add(groupTabsPanel, BorderLayout.SOUTH);

        setBackground(computeGeneralBackground());
        // trackPanelScrollPane's JViewport has no background of its own by default (it just
        // shows the look-and-feel's plain Viewport.background) - it was never wired to
        // Constants.BACKGROUND_COLOR at all, unlike every other "general background" consumer,
        // which is why the empty space below the last track (when the viewport is taller than
        // the track content - see the comment above on trackPanelScrollPane.getViewport()) never
        // reflected this preference, with or without a restart.
        trackPanelScrollPane.getViewport().setBackground(computeGeneralBackground());

    }

    private Color computeGeneralBackground() {
        boolean darkMode = Globals.isDarkMode();
        return darkMode && !PreferencesManager.getPreferences().hasExplicitValue(BACKGROUND_COLOR)
                ? UIManager.getColor("Panel.background")
                : PreferencesManager.getPreferences().getAsColor(BACKGROUND_COLOR);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = computeGeneralBackground();
        setBackground(background);
        trackPanelScrollPane.getViewport().setBackground(background);
        super.paintComponent(g);
    }

    /**
     * Add a track panel at the position determined by the track's order property.
     * Tracks are inserted to maintain ascending order by the order property.
     *
     * @param track the track to add
     * @return the TrackPanelScrollPane containing the track
     */
    public synchronized TrackPanelScrollPane addTrackPanel(Track track) {
        final TrackPanelScrollPane[] result = new TrackPanelScrollPane[1];
        UIUtilities.invokeAndWaitOnEventThread(() -> {
            TrackPanelScrollPane sp = createTrackPanel(track);
            long trackOrder = track.getOrder();
            if (trackOrder == 0) {
                track.setOrder(getTrackPanels().size());
            }
            int insertPosition = findInsertPosition(track.getOrder());
            trackPanelContainer.add(sp, insertPosition);
            rebuildDividers();
            result[0] = sp;
        });
        return result[0];
    }

    /**
     * Append newly loaded tracks in locator/load order and rebuild the Swing layout once.
     * Explicit order values from genome/session tracks are intentionally not used here:
     * this path is for interactive file/URL loading, where a new track should not jump above
     * an already visible genome annotation merely because that annotation has a large order
     * value in the genome JSON.
     */
    public synchronized void appendTrackPanels(Collection<? extends Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        UIUtilities.invokeAndWaitOnEventThread(() -> {
            long nextOrder = nextAppendOrder();
            for (Track track : tracks) {
                track.setOrder(nextOrder++);
                trackPanelContainer.add(createTrackPanel(track));
            }
            rebuildDividers();
        });
    }

    /**
     * Replace any visible selected panels with replacement tracks at the first selected
     * panel's exact visual position. Non-contiguous selected panels are all removed; the
     * replacements remain adjacent and retain their list order.
     */
    public synchronized void replaceTrackPanels(Collection<? extends Track> tracksToReplace,
                                                List<? extends Track> replacements) {
        if (tracksToReplace == null || tracksToReplace.isEmpty()) {
            return;
        }
        UIUtilities.invokeAndWaitOnEventThread(() -> {
            Set<Track> replacementSet = Collections.newSetFromMap(new IdentityHashMap<>());
            replacementSet.addAll(tracksToReplace);

            List<TrackPanelScrollPane> current = new ArrayList<>();
            for (Component component : trackPanelContainer.getComponents()) {
                if (component instanceof TrackPanelScrollPane) {
                    current.add((TrackPanelScrollPane) component);
                }
            }

            boolean hasMatch = current.stream().anyMatch(pane -> {
                Track track = pane.getTrackPanel().getTrack();
                return track != null && replacementSet.contains(track);
            });
            if (!hasMatch) {
                return;
            }

            List<TrackPanelScrollPane> replacementPanes = new ArrayList<>();
            if (replacements != null) {
                for (Track replacement : replacements) {
                    replacementPanes.add(createTrackPanel(replacement));
                }
            }
            List<TrackPanelScrollPane> retained = replaceAtFirstMatch(current, pane -> {
                Track track = pane.getTrackPanel().getTrack();
                return track != null && replacementSet.contains(track);
            }, replacementPanes);

            trackPanelContainer.removeAll();
            for (int i = 0; i < retained.size(); i++) {
                TrackPanelScrollPane pane = retained.get(i);
                Track track = pane.getTrackPanel().getTrack();
                if (track != null) {
                    // Start at 1 because addTrackPanel treats 0 as "order not assigned".
                    track.setOrder(i + 1L);
                }
                trackPanelContainer.add(pane);
            }
            rebuildDividers();
        });
    }

    /** Pure layout helper kept package-visible for regression tests. */
    static <T> List<T> replaceAtFirstMatch(List<T> current, Predicate<T> remove,
                                           List<? extends T> replacements) {
        List<T> result = new ArrayList<>();
        int insertionIndex = -1;
        for (T item : current) {
            if (remove.test(item)) {
                if (insertionIndex < 0) {
                    insertionIndex = result.size();
                }
            } else {
                result.add(item);
            }
        }
        if (insertionIndex >= 0) {
            result.addAll(insertionIndex, replacements);
        }
        return result;
    }

    private TrackPanelScrollPane createTrackPanel(Track track) {
        TrackPanel trackPanel = new TrackPanel(track.getName(), this);
        trackPanel.addTrack(track);
        TrackPanelScrollPane sp = new TrackPanelScrollPane();
        sp.setViewportView(trackPanel);
        track.setViewport(sp);
        return sp;
    }

    private long nextAppendOrder() {
        long maximum = 0;
        for (TrackPanel panel : getTrackPanels()) {
            Track track = panel.getTrack();
            if (track != null) {
                maximum = Math.max(maximum, track.getOrder());
            }
        }
        if (maximum >= Globals.JS_MAX_SAFE_INTEGER) {
            long order = 1;
            for (TrackPanel panel : getTrackPanels()) {
                Track track = panel.getTrack();
                if (track != null) {
                    track.setOrder(order++);
                }
            }
            return order;
        }
        return maximum + 1;
    }

    /**
     * Find the correct insertion position for a track with the given order value.
     * Returns the index where the track should be inserted to maintain ascending order.
     *
     * @param order the order value of the track to insert
     * @return the index at which to insert the track panel
     */
    private int findInsertPosition(long order) {
        Component[] components = trackPanelContainer.getComponents();
        for (int i = 0; i < components.length; i++) {
            Component c = components[i];
            if (c instanceof TrackPanelScrollPane) {
                TrackPanelScrollPane tsp = (TrackPanelScrollPane) c;
                List<Track> tracks = tsp.getTrackPanel().getTracks();
                if (!tracks.isEmpty()) {
                    Track existingTrack = tracks.get(0);
                    if (existingTrack.getOrder() > order) {
                        return i;
                    }
                }
            }
        }
        // If no track has a higher order, insert at the end
        return components.length;
    }

    /**
     * Updates the order property of a track that was moved via drag & drop.
     * Calculates a new order value that places it between its new neighbors,
     * preserving any pinned tracks with extreme order values.
     *
     * @param movedPanel the track panel that was moved
     */
    public void updateMovedTrackOrder(TrackPanel movedPanel) {
        List<TrackPanel> trackPanels = getTrackPanels();
        int newIndex = trackPanels.indexOf(movedPanel);
        if (newIndex < 0) return;

        Track movedTrack = movedPanel.getTrack();
        if (movedTrack == null) return;

        long prevOrder = Globals.JS_MIN_SAFE_INTEGER;
        long nextOrder = Globals.JS_MAX_SAFE_INTEGER;

        // Get the order of the previous track (if any)
        if (newIndex > 0) {
            Track prevTrack = trackPanels.get(newIndex - 1).getTrack();
            if (prevTrack != null) {
                prevOrder = prevTrack.getOrder();
            }
        }

        // Get the order of the next track (if any)
        if (newIndex < trackPanels.size() - 1) {
            Track nextTrack = trackPanels.get(newIndex + 1).getTrack();
            if (nextTrack != null) {
                nextOrder = nextTrack.getOrder();
            }
        }

        // Calculate new order as midpoint between neighbors
        long newOrder;
        if (prevOrder == Globals.JS_MIN_SAFE_INTEGER && nextOrder == Globals.JS_MAX_SAFE_INTEGER) {
            // Only track, use 0
            newOrder = 0;
        } else if (prevOrder == Globals.JS_MIN_SAFE_INTEGER) {
            // No previous track, place before next
            newOrder = nextOrder - 1;
        } else if (nextOrder == Globals.JS_MAX_SAFE_INTEGER) {
            // No next track, place after previous
            newOrder = prevOrder + 1;
        } else {
            // Between two tracks, use midpoint
            newOrder = prevOrder + (nextOrder - prevOrder) / 2;
            // If midpoint equals prevOrder (no room), just use prevOrder + 1
            if (newOrder == prevOrder) {
                newOrder = prevOrder + 1;
            }
        }

        movedTrack.setOrder(newOrder);
    }

    /**
     * Computes an order value that sorts strictly between {@code referenceTrack}'s current
     * previous and next neighbors, for inserting replacement tracks at the exact slot
     * {@code referenceTrack} currently occupies (before it's removed) - e.g. "Restore
     * Original Tracks" on an AverageErrorBarTrack, which needs its member tracks to land back
     * where the average track was rather than wherever {@code referenceTrack.getOrder()}
     * happens to be. That value can be stale (this codebase doesn't guarantee getOrder()
     * always matches the track's current on-screen position) and, worse, addTrackPanel()
     * silently treats a literal 0 as "never explicitly assigned" and appends to the bottom of
     * the list instead of inserting there - deriving a fresh value from the *current* neighbors
     * (and steering away from 0 the same way {@link #updateMovedTrackOrder} implicitly can, by
     * using -1/+1 offsets) sidesteps both problems.
     */
    public long computeOrderForCurrentPosition(Track referenceTrack) {
        List<TrackPanel> trackPanels = getTrackPanels();
        int index = -1;
        for (int i = 0; i < trackPanels.size(); i++) {
            if (referenceTrack.equals(trackPanels.get(i).getTrack())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return referenceTrack.getOrder();
        }

        long prevOrder = Globals.JS_MIN_SAFE_INTEGER;
        long nextOrder = Globals.JS_MAX_SAFE_INTEGER;
        if (index > 0) {
            Track prevTrack = trackPanels.get(index - 1).getTrack();
            if (prevTrack != null) {
                prevOrder = prevTrack.getOrder();
            }
        }
        if (index < trackPanels.size() - 1) {
            Track nextTrack = trackPanels.get(index + 1).getTrack();
            if (nextTrack != null) {
                nextOrder = nextTrack.getOrder();
            }
        }

        if (prevOrder == Globals.JS_MIN_SAFE_INTEGER && nextOrder == Globals.JS_MAX_SAFE_INTEGER) {
            // referenceTrack is the only track in the panel - 0 is fine here since
            // addTrackPanel() would compute the same value (getTrackPanels().size() == 0)
            // once referenceTrack is actually removed.
            return 0;
        }

        long newOrder;
        if (prevOrder == Globals.JS_MIN_SAFE_INTEGER) {
            newOrder = nextOrder - 1;
        } else if (nextOrder == Globals.JS_MAX_SAFE_INTEGER) {
            newOrder = prevOrder + 1;
        } else {
            long mid = prevOrder + (nextOrder - prevOrder) / 2;
            newOrder = mid == prevOrder ? prevOrder + 1 : mid;
        }
        // Other tracks remain in the panel here, so - unlike the only-track case above - a
        // literal 0 would trip addTrackPanel()'s "never explicitly assigned" fallback.
        return newOrder == 0 ? -1 : newOrder;
    }

    /**
     * Finds the track panel whose current on-screen bounds sit immediately above the given
     * drop point, using the live layout rather than any cached divider/pane reference - see
     * {@code TrackPanelDivider.handleTrackPanelDrop()}, which used to trust its own
     * {@code abovePane} field (set once at construction) for this instead. That field is only
     * as fresh as the specific {@code TrackPanelDivider} instance it lives on, but a drop
     * reaching that method through {@link DividerHoverOverlay}'s reused/retargeted pool can be
     * routed to an instance {@code MainPanel.rebuildDividers()} already discarded - whose
     * {@code abovePane} then no longer matches any panel in the current layout - silently
     * dropping the dragged track out of the reordered list entirely instead of reinserting it.
     * {@code dropComponent}'s and every track panel's actual current on-screen position are
     * unaffected by any of that bookkeeping, so deriving the answer from them instead is
     * immune to it.
     *
     * @param dropComponent the component the DnD system delivered the drop event to (used only
     *                       to translate dropPoint into this panel's coordinate space)
     * @param dropPoint     the drop location, in dropComponent's own coordinate space
     * @return the track panel whose scroll pane's vertical center is at or above the drop
     *         point, or {@code null} if the drop point is above every current track panel
     */
    public TrackPanel findTrackPanelAbovePoint(Component dropComponent, Point dropPoint) {
        if (dropComponent == null) {
            return null;
        }
        Point inContainer = SwingUtilities.convertPoint(dropComponent, dropPoint, trackPanelContainer);
        TrackPanel above = null;
        for (Component c : trackPanelContainer.getComponents()) {
            if (c instanceof TrackPanelScrollPane) {
                TrackPanelScrollPane sp = (TrackPanelScrollPane) c;
                Rectangle bounds = sp.getBounds();
                if (bounds.y + bounds.height / 2 <= inContainer.y) {
                    above = sp.getTrackPanel();
                } else {
                    break;
                }
            }
        }
        return above;
    }

    /**
     * Get the index of the track panel containing the specified track.
     *
     * @param track the track to find
     * @return the index of the track panel, or -1 if not found
     */
    public int getTrackPanelIndex(Track track) {
        Component[] components = trackPanelContainer.getComponents();
        for (int i = 0; i < components.length; i++) {
            Component c = components[i];
            if (c instanceof TrackPanelScrollPane) {
                TrackPanelScrollPane tsp = (TrackPanelScrollPane) c;
                if (tsp.getTrackPanel().containsTrack(track)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** A live track pane temporarily removed from the layout for an undoable deletion. */
    public record DetachedTrackPanel(Track track, TrackPanelScrollPane pane, int index) {
    }

    /**
     * Remove visible track panes without unloading tracks or discarding their Swing state.
     * The returned placements can be restored by {@link #restoreDetachedTrackPanels(List)}.
     */
    public synchronized List<DetachedTrackPanel> detachTrackPanels(
            Collection<? extends Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return List.of();
        List<DetachedTrackPanel> detached = new ArrayList<>();
        UIUtilities.invokeAndWaitOnEventThread(() -> {
            Set<Track> targets = Collections.newSetFromMap(new IdentityHashMap<>());
            targets.addAll(tracks);
            List<TrackPanelScrollPane> panes = currentTrackPanes();
            List<TrackPanelScrollPane> retained = new ArrayList<>();
            for (int i = 0; i < panes.size(); i++) {
                TrackPanelScrollPane pane = panes.get(i);
                Track track = pane.getTrackPanel().getTrack();
                if (track != null && targets.contains(track)) {
                    detached.add(new DetachedTrackPanel(track, pane, i));
                } else {
                    retained.add(pane);
                }
            }
            if (!detached.isEmpty()) setTrackPanes(retained);
        });
        return List.copyOf(detached);
    }

    /** Restore panes detached by an undoable deletion at their former visual positions. */
    public synchronized void restoreDetachedTrackPanels(List<DetachedTrackPanel> placements) {
        if (placements == null || placements.isEmpty()) return;
        UIUtilities.invokeAndWaitOnEventThread(() -> {
            List<TrackPanelScrollPane> panes = currentTrackPanes();
            Set<TrackPanelScrollPane> present = Collections.newSetFromMap(new IdentityHashMap<>());
            present.addAll(panes);
            List<DetachedTrackPanel> ordered = placements.stream()
                    .sorted(Comparator.comparingInt(DetachedTrackPanel::index)).toList();
            for (DetachedTrackPanel placement : ordered) {
                if (present.add(placement.pane())) {
                    int index = Math.max(0, Math.min(placement.index(), panes.size()));
                    panes.add(index, placement.pane());
                }
            }
            setTrackPanes(panes);
        });
    }

    private List<TrackPanelScrollPane> currentTrackPanes() {
        List<TrackPanelScrollPane> panes = new ArrayList<>();
        for (Component component : trackPanelContainer.getComponents()) {
            if (component instanceof TrackPanelScrollPane pane) panes.add(pane);
        }
        return panes;
    }

    /** Immutable snapshot of the current live track-pane order for structural undo. */
    public synchronized List<TrackPanelScrollPane> snapshotTrackPanes() {
        final List<TrackPanelScrollPane>[] result = new List[]{List.of()};
        UIUtilities.invokeAndWaitOnEventThread(() -> result[0] = List.copyOf(currentTrackPanes()));
        return result[0];
    }

    /** Restore an exact live track-pane layout captured by {@link #snapshotTrackPanes()}. */
    public synchronized void restoreTrackPanes(List<TrackPanelScrollPane> panes) {
        if (panes == null) return;
        UIUtilities.invokeAndWaitOnEventThread(() -> setTrackPanes(new ArrayList<>(panes)));
    }

    private void setTrackPanes(List<TrackPanelScrollPane> panes) {
        trackPanelContainer.removeAll();
        for (TrackPanelScrollPane pane : panes) trackPanelContainer.add(pane);
        rebuildDividers();
        trackPanelContainer.revalidate();
        trackPanelContainer.repaint();
    }

    public void clearTrackPanels() {
        trackPanelContainer.removeAll();
        rebuildDividers();
    }

    /**
     * Return an ordered list of TrackPanels.  This method is provided primarily for storing sessions, where
     * TrackPanels need to be stored in proper order
     *
     * @return
     */
    public java.util.List<TrackPanel> getTrackPanels() {
        ArrayList<TrackPanel> panels = new ArrayList<TrackPanel>();
        for (Component c : trackPanelContainer.getComponents()) {
            if (c instanceof TrackPanelScrollPane) {
                panels.add(((TrackPanelScrollPane) c).getTrackPanel());
            }
        }
        return panels;
    }

    /**
     * Reorder track panels using direct scroll pane references.
     * This avoids any name-matching issues that could cause panels to be lost.
     *
     * @param orderedPanes the scroll panes in the desired order
     */
    public void reorderPanels(java.util.List<TrackPanelScrollPane> orderedPanes) {
        trackPanelContainer.removeAll();
        for (TrackPanelScrollPane pane : orderedPanes) {
            if (pane != null) {
                trackPanelContainer.add(pane);
            }
        }
        rebuildDividers();
    }

    /**
     * Reorder track panels by name. Used by {@link ReorderPanelsDialog}.
     * Delegates to {@link #reorderPanels(List)} with resolved scroll pane references.
     *
     * @param names the panel names in the desired order
     */
    public void reorderPanelsByName(java.util.List<String> names) {
        Map<String, TrackPanelScrollPane> panesByName = new HashMap<>();
        for (Component c : trackPanelContainer.getComponents()) {
            if (c instanceof TrackPanelScrollPane) {
                TrackPanelScrollPane tsp = (TrackPanelScrollPane) c;
                panesByName.put(tsp.getTrackPanelName(), tsp);
            }
        }

        java.util.List<TrackPanelScrollPane> orderedPanes = new ArrayList<>();
        for (String name : names) {
            TrackPanelScrollPane pane = panesByName.get(name);
            if (pane != null) {
                orderedPanes.add(pane);
            }
        }
        reorderPanels(orderedPanes);
    }

    public void removeEmptyDataPanels() {
        List<TrackPanelScrollPane> emptyPanels = new ArrayList();
        for (TrackPanel tp : getTrackPanels()) {
            if (tp.getTracks().isEmpty()) {
                emptyPanels.add(tp.getScrollPane());
            }
        }
        for (TrackPanelScrollPane panel : emptyPanels) {
            if (panel != null) {
                trackPanelContainer.remove(panel);
            }
        }
        if (!emptyPanels.isEmpty()) {
            rebuildDividers();
        }
    }

    public void removeDataPanel(String name) {

        for (TrackPanel tp : getTrackPanels()) {
            if (name.equals(tp.getName())) {
                removeTrackPanel(tp);
                return;
            }
        }
    }

    public void removeTrackPanel(TrackPanel trackPanel) {
        TrackPanelScrollPane sp = trackPanel.getScrollPane();
        if (sp != null) {
            trackPanelContainer.remove(sp);
            rebuildDividers();
            trackPanelContainer.revalidate();
        }
    }


    public boolean panelIsRemovable(TrackPanel trackPanel) {
        return true;
    }

    /**
     * Rebuild dividers between TrackPanelScrollPanes. Removes all existing
     * {@link TrackPanelDivider} instances from the container and inserts a new
     * divider after each TrackPanelScrollPane (including the last one, so that
     * the last track can also be resized).
     */
    private void rebuildDividers() {
        // Collect the current TrackPanelScrollPanes in order
        List<TrackPanelScrollPane> panes = new ArrayList<>();
        for (Component c : trackPanelContainer.getComponents()) {
            if (c instanceof TrackPanelScrollPane) {
                panes.add((TrackPanelScrollPane) c);
            }
        }

        // Re-add panes, each followed by a divider
        trackPanelContainer.removeAll();
        for (int i = 0; i < panes.size(); i++) {
            TrackPanelScrollPane above = panes.get(i);
            TrackPanelScrollPane below = (i + 1 < panes.size()) ? panes.get(i + 1) : null;
            trackPanelContainer.add(above);
            trackPanelContainer.add(new TrackPanelDivider(above, below));
        }

        trackPanelContainer.revalidate();
        trackPanelContainer.repaint();
    }

    public void updatePanelDimensions() {
        Insets insets = applicationHeaderPanel.getInsets();
        namePanelX = insets.left;
        attributePanelWidth = calculateAttributeWidth();
        if (attributePanelWidth > 0) {
            attributePanelX = namePanelX + namePanelWidth + hgap;
            dataPanelX = attributePanelX + attributePanelWidth + hgap;
        } else {
            attributePanelX = namePanelX + namePanelWidth + hgap;
            dataPanelX = attributePanelX;
        }
        dataPanelWidth = applicationHeaderPanel.getWidth() - insets.right - dataPanelX;
    }

    public int calculateAttributeWidth() {

        if (!PreferencesManager.getPreferences().getAsBoolean(SHOW_ATTRIBUTE_VIEWS_KEY)) {
            return 0;
        }
        Collection<String> attributeKeys = AttributeManager.getInstance().getVisibleAttributes();
        int attributeCount = attributeKeys.size();
        if (attributeCount == 0) {
            return 0;
        }
        int packWidth = (attributeCount) * (AttributeHeaderPanel.ATTRIBUTE_COLUMN_WIDTH +
                AttributeHeaderPanel.COLUMN_BORDER_WIDTH) + AttributeHeaderPanel.COLUMN_BORDER_WIDTH;
        return packWidth;
    }

    public boolean isExpanded() {
        return namePanelWidth > 0;
    }

    public int getAttributePanelWidth() {
        return attributePanelWidth;
    }

    public int getNamePanelX() {
        return namePanelX;
    }

    public int getNamePanelWidth() {
        return namePanelWidth;
    }

    public int getAttributePanelX() {
        return attributePanelX;
    }


    public int getDataPanelX() {
        return dataPanelX;
    }


    public int getDataPanelWidth() {
        return dataPanelWidth;
    }

    public int getGenomicHeaderHeight() {
        return applicationHeaderPanel.getHeight();
    }

    public void setCoordinatesInverted(boolean inverted) {
        UIUtilities.invokeAndWaitOnEventThread(() -> nameHeaderPanel.setCoordinatesInverted(inverted));
    }

    /**
     * Returns the total left offset for panels, which includes the drag handle width
     * plus the selection panel width if selection panels are visible.
     */
    public int getLeftOffset() {
        int offset = DragHandlePanel.DRAG_HANDLE_WIDTH;
        if (PreferencesManager.getPreferences().getAsBoolean(SHOW_SELECTION_PANEL)) {
            offset += TrackSelectionPanel.SELECTION_PANEL_WIDTH;
        }
        return offset;
    }

    public ScrollableTrackContainer getTrackPanelContainer() {
        return trackPanelContainer;
    }

    /**
     * The outer scroll pane that scrolls the entire stack of track panels.
     * Used by {@link TrackPanelScrollPane} to hand off wheel scrolling once an
     * internally-scrollable track reaches its top or bottom limit.
     */
    public JScrollPane getTrackPanelScrollPane() {
        return trackPanelScrollPane;
    }

    public HeaderSelectAllPanel getHeaderSelectAllPanel() {
        return headerSelectAllPanel;
    }

    /**
     * Show or hide the track selection checkboxes across all track panels and the header
     * select-all checkbox, and persist the state in preferences.  Shared by the "Show
     * Selection Checkboxes" View menu item and the single-click select action in the
     * track name panel.
     */
    public void setSelectionPanelsVisible(boolean show) {
        PreferencesManager.getPreferences().put(SHOW_SELECTION_PANEL, show);
        for (TrackPanel tp : getTrackPanels()) {
            TrackPanelScrollPane sp = tp.getScrollPane();
            if (sp != null) {
                sp.setSelectionPanelVisible(show);
            }
        }
        if (headerSelectAllPanel != null) {
            headerSelectAllPanel.setCheckBoxVisible(show);
        }
        revalidateTrackPanels();
    }

    public int getTrackPanelViewportHeight() {
        return trackPanelScrollPane.getViewport().getHeight();
    }

    /** Sum of preferred heights of non-track components (dividers, margins) in the track container. */
    public int getTrackContainerOverhead() {
        int overhead = 0;
        for (Component c : trackPanelContainer.getComponents()) {
            if (!(c instanceof TrackPanelScrollPane)) {
                overhead += c.getPreferredSize().height;
            }
        }
        return overhead;
    }



    public void paintOffscreen(Graphics2D g, Rectangle rect, boolean batch) {

        paintOffscreen(g, rect, batch, true);
    }

    /** Paints a publication screenshot without relying on a destructive vertical crop. */
    public void paintOffscreen(Graphics2D g, Rectangle rect, boolean batch, boolean includeGenomicHeader) {

        Graphics2D contentGraphics = (Graphics2D) g.create();
        contentGraphics.setColor(computeGeneralBackground());
        contentGraphics.fillRect(rect.x, rect.y, rect.width, rect.height);

        // Header
        int width = applicationHeaderPanel.getWidth();
        int height = applicationHeaderPanel.getHeight();

        if (includeGenomicHeader) {
            Graphics2D headerGraphics = (Graphics2D) contentGraphics.create();
            Rectangle headerRect = new Rectangle(0, 0, width, height);
            applicationHeaderPanel.paintOffscreen(headerGraphics, headerRect, batch);
            headerGraphics.dispose();
            contentGraphics.translate(0, height);
        }

        // Now loop through track panels
        // Get the components of the center pane and sort by Y position.
        Component[] components = trackPanelContainer.getComponents();
        if (components.length == 0) {
            contentGraphics.dispose();
            return;
        }
        Arrays.sort(components, Comparator.comparingInt(Component::getY));

        int dy = components[0].getY();
        for (Component c : components) {

            Graphics2D g2d = (Graphics2D) contentGraphics.create();
            g2d.translate(0, dy);

            if (c instanceof TrackPanelScrollPane) {
                TrackPanelScrollPane tsp = (TrackPanelScrollPane) c;

                int panelHeight = tsp.getSnapshotHeight(batch);

                Rectangle tspRect = new Rectangle(0, 0, tsp.getWidth(), panelHeight);

                g2d.setClip(tspRect);
                tsp.paintOffscreen(g2d, tspRect, batch);
                dy += tspRect.height;

            } else {
                g2d.setClip(new Rectangle(0, 0, c.getWidth(), c.getHeight()));
                c.paint(g2d);
                dy += c.getHeight();
            }

            g2d.dispose();

        }
        contentGraphics.dispose();
    }

    /**
     * Return the image height required to paint this component with current options.  This is used to size bitmap
     * images for offscreen drawing.
     *
     * @return
     */
    @Override
    public int getSnapshotHeight(boolean batch) {

        if (batch) {
            int height = applicationHeaderPanel.getHeight();

            for (Component c : trackPanelContainer.getComponents()) {

                if (c instanceof TrackPanelScrollPane) {

                    TrackPanelScrollPane tsp = (TrackPanelScrollPane) c;

                    //Skip if panel has no tracks
                    if (tsp.getTrackPanel().getTracks().size() == 0) {
                        continue;
                    }

                    height += tsp.getSnapshotHeight(batch);

                } else {
                    height += c.getHeight();
                }

            }
            return height;
        } else {
            return getHeight();
        }
    }

    public void repaintHeaderPanels() {
        headerPanelContainer.repaint();
    }

    // DropTargetListener implementation

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        // No action needed
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        // No action needed
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        // No action needed
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        if (dtde == lastProcessedDrop) {
            // Already handled this exact drop gesture via another forwarding path.
            dtde.dropComplete(true);
            return;
        }
        lastProcessedDrop = dtde;
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();

            List<File> droppedFiles = new ArrayList<>();
            List<String> droppedUrls = new ArrayList<>();

            // Try to get files
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                droppedFiles.addAll(files);
            }

            // Try to get URLs/URIs (for URL drops, including from browsers)
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String data = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                if (data != null && !data.trim().isEmpty()) {
                    // Parse potential URLs (one per line)
                    String[] lines = data.split("\\r?\\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (isUrl(line)) {
                            droppedUrls.add(line);
                        }
                    }
                }
            }

            // Try URI list flavor (common on Linux)
            DataFlavor uriListFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
            if (transferable.isDataFlavorSupported(uriListFlavor)) {
                String data = (String) transferable.getTransferData(uriListFlavor);
                if (data != null) {
                    String[] uris = data.split("\\r?\\n");
                    for (String uriStr : uris) {
                        uriStr = uriStr.trim();
                        if (!uriStr.isEmpty() && !uriStr.startsWith("#")) {
                            try {
                                URI uri = new URI(uriStr);
                                if ("file".equals(uri.getScheme())) {
                                    droppedFiles.add(new File(uri));
                                } else {
                                    droppedUrls.add(uriStr);
                                }
                            } catch (Exception e) {
                                // Ignore invalid URIs
                            }
                        }
                    }
                }
            }

            // A single Finder drag commonly exposes BOTH javaFileListFlavor and
            // text/uri-list for the same file(s) - the block above adds a file from
            // javaFileListFlavor and then adds it AGAIN from the file:// URI in
            // text/uri-list, so droppedFiles ends up with every dropped file duplicated.
            // Deduplicate by absolute path, preserving first-seen order.
            List<File> dedupedFiles = new ArrayList<>();
            Set<String> seenPaths = new HashSet<>();
            for (File f : droppedFiles) {
                if (seenPaths.add(f.getAbsolutePath())) {
                    dedupedFiles.add(f);
                }
            }
            droppedFiles = dedupedFiles;

            // Load dropped files and URLs as tracks or sessions
            List<ResourceLocator> locators = new ArrayList<>();

            // First check for a session
            String sessionPath = null;
            if (droppedFiles.size() == 1 && droppedFiles.get(0).getName().endsWith(".xml")) {
                // If it's a single XML file, treat it as a session file
                sessionPath = droppedFiles.get(0).getAbsolutePath();
            } else if (droppedUrls.size() == 1 && droppedUrls.get(0).endsWith(".xml")) {
                // If it's a single URL ending in .xml, treat it as a session URL
                sessionPath = droppedUrls.get(0);
            }
            if (sessionPath != null) {
                final String sp = sessionPath;
                LongRunningTask.submit(() -> this.igv.loadSession(sp, null));
                return;
            }

            // Add file locators
            if (!droppedFiles.isEmpty()) {
                locators.addAll(ResourceLocator.getLocators(droppedFiles));
            }

            // Add URL locators
            for (String url : droppedUrls) {
                locators.add(new ResourceLocator(url));
            }

            // A local path can reach here twice through genuinely separate routes: once as
            // a File from javaFileListFlavor, and again as a plain (non "file://") path
            // string via the text/uri-list flavor - that string fails the file-scheme check
            // above and lands in droppedUrls instead, so the droppedFiles-level dedup above
            // doesn't catch it. Dedupe the final locator list by resolved path as the
            // authoritative guard, regardless of which branch a path arrived through.
            List<ResourceLocator> dedupedLocators = new ArrayList<>();
            Set<String> seenLocatorPaths = new HashSet<>();
            for (ResourceLocator loc : locators) {
                if (seenLocatorPaths.add(loc.getPath())) {
                    dedupedLocators.add(loc);
                }
            }
            locators = dedupedLocators;

            if (!locators.isEmpty() && !isDuplicateLoad(locators)) {
                igv.loadTracks(locators);
            }

            dtde.dropComplete(true);
        } catch (Exception e) {
            dtde.dropComplete(false);
        }
    }

    private List<String> lastLoadedPaths;
    private long lastLoadTimeMs;

    /**
     * True if this exact set of paths/URLs was just requested moments ago. Guards against
     * double-loading a drag-and-dropped file: the same-object check on {@code dtde} above
     * only catches the exact same event instance reaching drop() twice, but IGV also has a
     * separate, older DropTarget-forwarding mechanism (installDropTargetRecursively) that
     * can, for a single drag gesture, end up delivering to drop() via more than one path
     * with logically-equivalent-but-distinct event objects. Comparing the resolved locator
     * paths within a short window catches that case too, without needing to pin down
     * exactly which forwarding path double-fires.
     */
    private boolean isDuplicateLoad(List<ResourceLocator> locators) {
        List<String> paths = new ArrayList<>();
        for (ResourceLocator loc : locators) {
            paths.add(loc.getPath());
        }
        long now = System.currentTimeMillis();
        boolean duplicate = paths.equals(lastLoadedPaths) && (now - lastLoadTimeMs) < 1500;
        lastLoadedPaths = paths;
        lastLoadTimeMs = now;
        return duplicate;
    }

    private boolean isDragAcceptable(DropTargetDragEvent dtde) {
        return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                dtde.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    private boolean isUrl(String str) {
        if (str == null) return false;
        String lower = str.toLowerCase();
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("ftp://") ||
                lower.startsWith("s3://") ||
                lower.startsWith("gs://");
    }

    /**
     * Recursively install a DropTarget on the given component and all its
     * descendants so that drop events anywhere in the UI are handled.
     * <p>
     * Components that already have their own DropTarget (e.g. TrackPanel for reorder D&D,
     * HeaderPanel for frame reorder D&D) are left untouched — their listeners already
     * delegate unhandled flavors (like file drops) to {@link #drop}.
     * <p>
     * For components without a DropTarget, a forwarding listener is installed that
     * locates the nearest ancestor with a DropTarget and dispatches the event there.
     * This preserves the existing D&D behaviour (e.g. track reorder) while also making
     * file drops work everywhere.
     * <p>
     * A ContainerListener is added to every Container so that dynamically added
     * children (e.g. new track panels) are handled automatically.
     */
    private void installDropTargetRecursively(Component comp) {
        if (comp.getDropTarget() == null) {
            // Install a forwarding DropTarget that bubbles the event up to the nearest
            // ancestor that has its own DropTarget (ultimately reaching MainPanel).
            DropTargetListener forwarder = new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    DropTarget ancestorDt = findAncestorDropTarget(comp);
                    if (ancestorDt != null) {
                        ancestorDt.drop(dtde);
                    }
                }
            };
            new DropTarget(comp, DnDConstants.ACTION_COPY_OR_MOVE, forwarder, true);
        }
        if (comp instanceof Container) {
            Container container = (Container) comp;
            for (Component child : container.getComponents()) {
                installDropTargetRecursively(child);
            }
            container.addContainerListener(new ContainerListener() {
                @Override
                public void componentAdded(ContainerEvent e) {
                    installDropTargetRecursively(e.getChild());
                }

                @Override
                public void componentRemoved(ContainerEvent e) {
                    // No action needed on removal
                }
            });
        }
    }

    /**
     * Walk up the component hierarchy and return the DropTarget of the first
     * ancestor that has one.  Returns {@code null} if none is found.
     */
    private static DropTarget findAncestorDropTarget(Component comp) {
        Container parent = comp.getParent();
        while (parent != null) {
            DropTarget dt = parent.getDropTarget();
            if (dt != null) {
                return dt;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
