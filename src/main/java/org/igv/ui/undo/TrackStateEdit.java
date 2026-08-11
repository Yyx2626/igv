package org.igv.ui.undo;

import org.igv.renderer.DataRange;
import org.igv.track.AttributeManager;
import org.igv.track.PairRole;
import org.igv.track.Track;
import org.igv.track.WindowFunction;
import org.igv.track.MergedTracks;
import org.igv.ui.IGV;

import javax.swing.undo.AbstractUndoableEdit;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** One atomic change to display properties of one or more existing tracks. */
public final class TrackStateEdit extends AbstractUndoableEdit {

    public record State(Track track, DataRange dataRange, boolean autoScale,
                        String autoscaleGroup, Color color, Color altColor,
                        Color backgroundColor, Integer borderHeight, Color borderColor,
                        int height, Class<?> rendererClass, WindowFunction windowFunction,
                        String pairId, PairRole pairRole, long order, Double overlayAlpha) {

        static State capture(Track track) {
            DataRange range = track.getDataRange();
            return new State(track, range == null ? null : range.copy(), track.getAutoScale(),
                    track.getAutoscaleGroup(),
                    track.getColorOverride(), track.getAltColorOverride(),
                    track.getBackgroundColorOverride(), track.getBorderHeightOverride(),
                    track.getBorderColorOverride(), track.getHeight(),
                    track.getRenderer() == null ? null : track.getRenderer().getClass(),
                    track.getWindowFunction(), track.getPairId(), track.getPairRole(),
                    track.getOrder(), track instanceof MergedTracks merged
                            ? merged.getTrackAlpha() : null);
        }

        void apply() {
            track.setDataRange(dataRange == null ? null : dataRange.copy());
            track.setAutoScale(autoScale);
            if (autoscaleGroup == null) track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
            else track.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, autoscaleGroup);
            track.setColor(color);
            track.setAltColor(altColor);
            track.setBackgroundColorOverride(backgroundColor);
            track.setBorderHeightOverride(borderHeight);
            track.setBorderColorOverride(borderColor);
            track.setHeight(height);
            if (rendererClass != null) track.setRendererClass(rendererClass);
            if (windowFunction != null) track.setWindowFunction(windowFunction);
            if (pairId == null) track.removeAttribute(AttributeManager.PAIR_GROUP);
            else track.setAttributeValue(AttributeManager.PAIR_GROUP, pairId);
            track.setPairRole(pairRole);
            track.setOrder(order);
            if (overlayAlpha != null && track instanceof MergedTracks merged) {
                merged.setTrackAlphas(overlayAlpha);
            }
        }

        String signature() {
            return rangeSignature(dataRange) + "|" + autoScale + "|" + autoscaleGroup + "|"
                    + colorSignature(color) + "|" + colorSignature(altColor) + "|"
                    + colorSignature(backgroundColor) + "|" + borderHeight + "|"
                    + colorSignature(borderColor) + "|" + height + "|" + rendererClass + "|"
                    + windowFunction + "|" + pairId + "|" + pairRole + "|" + order
                    + "|" + overlayAlpha;
        }

        private static String rangeSignature(DataRange range) {
            if (range == null) return "null";
            return range.getMinimum() + "," + range.getBaseline() + "," + range.getMaximum()
                    + "," + range.getType() + "," + range.isFlipAxis() + ","
                    + range.isDrawBaseline() + "," + colorSignature(range.getMidlineColor());
        }

        private static String colorSignature(Color color) {
            return color == null ? "null" : Integer.toUnsignedString(color.getRGB());
        }
    }

    private final String name;
    private final List<State> before;
    private final List<State> after;

    public TrackStateEdit(String name, List<State> before, List<State> after) {
        this.name = name;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    public static List<State> capture(Collection<? extends Track> tracks) {
        List<State> result = new ArrayList<>();
        if (tracks != null) {
            for (Track track : tracks) if (track != null) result.add(State.capture(track));
        }
        return result;
    }

    public static boolean differs(List<State> first, List<State> second) {
        if (first.size() != second.size()) return true;
        for (int i = 0; i < first.size(); i++) {
            State a = first.get(i);
            State b = second.get(i);
            if (a.track() != b.track() || !Objects.equals(a.signature(), b.signature())) return true;
        }
        return false;
    }

    @Override
    public void undo() {
        super.undo();
        apply(before);
    }

    @Override
    public void redo() {
        super.redo();
        apply(after);
    }

    @Override
    public String getPresentationName() {
        return name;
    }

    private static void apply(List<State> states) {
        for (State state : states) state.apply();
        IGV igv = IGV.getInstance();
        igv.revalidateTrackPanels();
        igv.repaint();
    }
}
