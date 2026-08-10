package org.igv.track;

import org.igv.prefs.PreferencesManager;
import org.igv.renderer.DataRange;
import org.igv.ui.IGV;
import org.igv.ui.panel.TrackPanel;
import org.igv.ui.panel.TrackPanelScrollPane;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stateless helper for the "Pair Tracks" feature. Two tracks can be paired so that
 * batch operations (e.g. Set Data Range) can treat the pair's top/bottom members
 * independently instead of applying one shared setting to both.
 * <p>
 * Pairing state lives directly on {@link Track} as a {@code pairId}/{@code pairRole}
 * pair (see {@link AbstractTrack}), analogous to how {@code autoscaleGroup} is stored.
 */
public class TrackPairing {

    private TrackPairing() {
    }

    /**
     * Pair two tracks. Whichever one is currently rendered higher up on screen becomes
     * {@link PairRole#TOP}, the other {@link PairRole#BOTTOM}. Determined from the actual
     * current visual track order (same source {@code TrackSelectionPanel.selectRange} uses),
     * NOT {@link Track#getOrder()} directly - that field is frequently tied (e.g. multiple
     * freshly-loaded tracks all default to order 0), which made top/bottom assignment
     * unreliable (a track's own {@code getOrder()} doesn't always match its current
     * on-screen position).
     * <p>
     * Shows up as its own "PAIR GROUP" column in the attribute panel, the same way
     * {@code AttributeManager.GROUP_AUTOSCALE} does - via {@code setAttributeValue}
     * below, which is special-cased in {@code AbstractTrack} (getAttributeValue/
     * setAttributeValue/removeAttribute/renderAttributes) to read and write
     * {@code pairId} directly rather than the generic row-keyed attribute table. That
     * table is keyed by {@code Track.getSample()}, which falls back to the track's
     * *name* when no explicit sample id is set - an earlier version that stored the
     * pair id there directly (without the special-casing) made any two tracks sharing a
     * name appear paired together.
     */
    public static void pair(Track a, Track b) {
        if (a == b) {
            return;
        }
        List<Track> visualOrder = IGV.getInstance().getMainPanel().getTrackPanels().stream()
                .map(TrackPanel::getTrack).collect(Collectors.toList());
        int indexA = visualOrder.indexOf(a);
        int indexB = visualOrder.indexOf(b);
        Track top = indexA <= indexB ? a : b;
        Track bottom = top == a ? b : a;

        String pairId = UUID.randomUUID().toString();
        top.setAttributeValue(AttributeManager.PAIR_GROUP, pairId);
        top.setPairRole(PairRole.TOP);
        bottom.setAttributeValue(AttributeManager.PAIR_GROUP, pairId);
        bottom.setPairRole(PairRole.BOTTOM);

        // The PAIR GROUP column lives in the attribute panel, which has zero width/is
        // hidden unless this preference is on - make sure it's visible, mirroring what
        // Group Autoscale already does.
        PreferencesManager.getPreferences().setShowAttributeView(true);
        IGV.getInstance().revalidateTrackPanels();
        IGV.getInstance().repaint(List.of(top, bottom));
    }

    public static boolean isPaired(Track t) {
        return t != null && t.getPairId() != null;
    }

    /**
     * Clear pairing for every track in {@code tracks} that is currently paired, along
     * with its partner (even if the partner is not itself present in {@code tracks}).
     */
    public static void unpair(Collection<Track> tracks, Collection<Track> allTracks) {
        for (Track t : new ArrayList<>(tracks)) {
            if (!isPaired(t)) {
                continue;
            }
            Track partner = findPartner(t, allTracks);
            t.removeAttribute(AttributeManager.PAIR_GROUP);
            t.setPairRole(null);
            if (partner != null) {
                partner.removeAttribute(AttributeManager.PAIR_GROUP);
                partner.setPairRole(null);
            }
        }
    }

    /**
     * Find the other track sharing {@code t}'s pairId, or null if {@code t} is unpaired
     * or has no partner in {@code allTracks}.
     */
    public static Track findPartner(Track t, Collection<Track> allTracks) {
        if (!isPaired(t)) {
            return null;
        }
        for (Track other : allTracks) {
            if (other != t && t.getPairId().equals(other.getPairId())) {
                return other;
            }
        }
        return null;
    }

    /**
     * Called after a member track is restored out of an AverageErrorBarTrack (see
     * "Restore Original Tracks" in AverageErrorBarTrack/TrackMenuUtils). A member keeps
     * whatever pairId/PairRole it had *before* being averaged - averaging never touches it,
     * since the member object itself is preserved unchanged for exactly this kind of
     * restore - but by the time it comes back, that old relationship may no longer make
     * sense:
     * <ul>
     *   <li>the original partner may still be dormant inside a *different*, not-yet-restored
     *       AverageErrorBarTrack, so it isn't a currently-displayed track at all;</li>
     *   <li>the original partner may since have been re-paired with some other track under a
     *       fresh pairId, so it no longer shares this member's stale id either;</li>
     *   <li>(defensively) more than one currently-displayed track could claim to share this
     *       stale id, which shouldn't happen given pairId's normal 1:1 invariant, but isn't
     *       safe to guess at if it does.</li>
     * </ul>
     * In all three cases the stale pairing is cleared rather than trusted. Otherwise - the
     * partner genuinely still exists, standalone, and shares no one else's pairId - the pair
     * is re-established via {@link #pair}, which recomputes top/bottom from current on-screen
     * order; restoring both members to their original positions first makes that come out the
     * same as their original relationship.
     */
    public static void reconcilePairingAfterRestore(Track member, Collection<Track> allTracks) {
        if (member.getPairId() == null) {
            return;
        }
        List<Track> candidates = new ArrayList<>();
        for (Track other : allTracks) {
            if (other != member && member.getPairId().equals(other.getPairId())) {
                candidates.add(other);
            }
        }
        if (candidates.size() != 1) {
            member.removeAttribute(AttributeManager.PAIR_GROUP);
            member.setPairRole(null);
            return;
        }
        pair(member, candidates.get(0));
    }

    /**
     * Partition a selection into a "top" group (tracks with {@link PairRole#TOP}, plus
     * any unpaired tracks) and a "bottom" group (tracks with {@link PairRole#BOTTOM}).
     * Used both by the paired Data-Range dialog and by the Average-With-Error-Bar
     * feature.
     */
    public static Partition partitionTopBottom(Collection<Track> selection) {
        List<Track> top = new ArrayList<>();
        List<Track> bottom = new ArrayList<>();
        for (Track t : selection) {
            if (t.getPairRole() == PairRole.BOTTOM) {
                bottom.add(t);
            } else {
                // TOP or unpaired
                top.add(t);
            }
        }
        return new Partition(top, bottom);
    }

    /** Reverse selected numeric axes; if a pair is touched, flip and exchange the complete pair. */
    public static void flipVertically(Collection<Track> selection) {
        List<Track> allTracks = IGV.getInstance().getAllTracks();
        java.util.LinkedHashSet<Track> targets = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> pairIds = new java.util.LinkedHashSet<>();
        for (Track track : selection) {
            if (track instanceof DataTrack) {
                targets.add(track);
                if (isPaired(track)) {
                    pairIds.add(track.getPairId());
                    Track partner = findPartner(track, allTracks);
                    if (partner instanceof DataTrack) {
                        targets.add(partner);
                    }
                }
            }
        }

        flipDataRanges(targets);

        List<TrackPanelScrollPane> panes = IGV.getInstance().getMainPanel().getTrackPanels().stream()
                .map(TrackPanel::getScrollPane).collect(Collectors.toList());
        for (String pairId : pairIds) {
            Track top = null;
            Track bottom = null;
            for (Track track : allTracks) {
                if (pairId.equals(track.getPairId())) {
                    if (track.getPairRole() == PairRole.TOP) top = track;
                    if (track.getPairRole() == PairRole.BOTTOM) bottom = track;
                }
            }
            if (top == null || bottom == null) continue;
            int topIndex = panes.indexOf(top.getViewport());
            int bottomIndex = panes.indexOf(bottom.getViewport());
            if (topIndex >= 0 && bottomIndex >= 0) {
                java.util.Collections.swap(panes, topIndex, bottomIndex);
            }
            swapPersistentOrder(top, bottom);
            top.setPairRole(PairRole.BOTTOM);
            bottom.setPairRole(PairRole.TOP);
        }
        if (!pairIds.isEmpty()) {
            IGV.getInstance().getMainPanel().reorderPanels(panes);
            IGV.getInstance().getMainPanel().revalidateTrackPanels();
        }
        // Flipping an explicit range never requires loading data or recalculating
        // autoscale.  IGV.repaint(Collection) performs both against every visible track,
        // which made this constant-time operation occasionally pause on large sessions.
        // Repaint only the affected Swing viewports; paired tracks already had their
        // panel layout revalidated above after exchanging top/bottom positions.
        for (Track target : targets) {
            target.repaint();
        }
    }

    static void flipDataRanges(Collection<Track> targets) {
        for (Track track : targets) {
            DataRange range = track.getDataRange();
            if (range != null) {
                track.setAutoScale(false);
                // Group autoscaling takes precedence over getAutoScale() and would replace
                // the flipped range during the next repaint unless explicitly removed.
                track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
                track.setDataRange(range.flipped());
            }
        }
    }

    static void swapPersistentOrder(Track first, Track second) {
        long firstOrder = first.getOrder();
        first.setOrder(second.getOrder());
        second.setOrder(firstOrder);
    }

    public static class Partition {
        public final List<Track> top;
        public final List<Track> bottom;

        public Partition(List<Track> top, List<Track> bottom) {
            this.top = top;
            this.bottom = bottom;
        }
    }
}
