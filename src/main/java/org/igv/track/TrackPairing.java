package org.igv.track;

import org.igv.prefs.PreferencesManager;
import org.igv.ui.IGV;
import org.igv.ui.panel.TrackPanel;

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

    public static class Partition {
        public final List<Track> top;
        public final List<Track> bottom;

        public Partition(List<Track> top, List<Track> bottom) {
            this.top = top;
            this.bottom = bottom;
        }
    }
}
