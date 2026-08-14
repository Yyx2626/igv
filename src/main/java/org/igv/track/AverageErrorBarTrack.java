package org.igv.track;

import org.igv.data.AverageErrorBarDataSource;
import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.FeatureUtils;
import org.igv.feature.LocusScore;
import org.igv.renderer.AverageErrorBarLineplotRenderer;
import org.igv.renderer.AverageErrorBarPointsRenderer;
import org.igv.renderer.AverageErrorBarRenderer;
import org.igv.renderer.DataRange;
import org.igv.renderer.ErrorBarStyle;
import org.igv.renderer.XYPlotRenderer;
import org.igv.ui.ErrorBarStyleDialog;
import org.igv.ui.IGV;
import org.igv.ui.action.RegionalTrackSettingsTransfer;
import org.igv.ui.undo.TrackStructureEdit;
import org.igv.ui.panel.ReferenceFrame;
import org.igv.ui.util.UIUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Synthetic track produced by the "Average With Error Bar" context-menu action:
 * renders the per-bin mean across its member tracks plus a configurable SD/SEM error
 * bar (see {@link AverageErrorBarDataSource} for the per-bin math and
 * {@link AverageErrorBarRenderer} for the drawing).
 * <p>
 * Modeled on {@link CombinedDataTrack} (wraps a custom {@code DataSource}) plus
 * {@link MergedTracks}'s session-persistence pattern (nests each member's full JSON
 * under a {@code "tracks"} array, reconstructed by {@code JSONSessionReader} the same
 * way it special-cases {@code "merged"} tracks).
 */
public class AverageErrorBarTrack extends DataSourceTrack {

    private List<DataTrack> memberTracks;
    private ErrorBarType errorBarType = ErrorBarType.SEM;
    private ErrorBarStyle errorBarStyle = new ErrorBarStyle();
    private float naValue = 0f;

    /** Special constructor for session unmarshalling - mirrors {@code CombinedDataTrack}. */
    public AverageErrorBarTrack(String id, String name) {
        super(null, id, name, null);
    }

    public AverageErrorBarTrack(String id, String name, List<DataTrack> memberTracks,
                                 WindowFunction resolvedFunction, ErrorBarType errorBarType, float naValue) {
        super(null, id, name, new AverageErrorBarDataSource(memberTracks, resolvedFunction, naValue));
        this.memberTracks = memberTracks;
        this.errorBarType = errorBarType;
        this.naValue = naValue;
        setRendererClass(AverageErrorBarRenderer.class);
        inheritSharedManualDataRange(memberTracks);
        inheritSharedTrackColors(memberTracks);
    }

    /** Preserve a common explicit member scale; mixed or autoscaled inputs remain autoscaled. */
    boolean inheritSharedManualDataRange(List<? extends Track> members) {
        if (members == null || members.isEmpty()) return false;
        DataRange shared = null;
        for (Track member : members) {
            if (member.getAutoScale()
                    || member.getAttributeValue(AttributeManager.GROUP_AUTOSCALE) != null) {
                return false;
            }
            DataRange candidate = member.getDataRange();
            if (candidate == null) return false;
            if (shared == null) shared = candidate;
            else if (!sameDataRange(shared, candidate)) return false;
        }
        setAutoScale(false);
        removeAttribute(AttributeManager.GROUP_AUTOSCALE);
        setDataRange(shared.copy());
        return true;
    }

    private static boolean sameDataRange(DataRange first, DataRange second) {
        return Float.compare(first.getMinimum(), second.getMinimum()) == 0
                && Float.compare(first.getBaseline(), second.getBaseline()) == 0
                && Float.compare(first.getMaximum(), second.getMaximum()) == 0
                && first.getType() == second.getType()
                && first.isFlipAxis() == second.isFlipAxis()
                && first.isDrawBaseline() == second.isDrawBaseline()
                && Objects.equals(first.getMidlineColor(), second.getMidlineColor());
    }

    /** Inherit positive and negative colors independently when each is common to the group. */
    void inheritSharedTrackColors(List<? extends Track> members) {
        if (members == null || members.isEmpty()) return;
        Color positive = members.get(0).getColor();
        Color negative = members.get(0).getAltColor();
        boolean samePositive = true;
        boolean sameNegative = true;
        for (int i = 1; i < members.size(); i++) {
            samePositive &= Objects.equals(positive, members.get(i).getColor());
            sameNegative &= Objects.equals(negative, members.get(i).getAltColor());
        }
        if (samePositive) setColor(positive);
        if (sameNegative) setAltColor(negative);
    }

    @Override
    public TrackType getType() {
        return TrackType.averageErrorBar;
    }

    public List<DataTrack> getMemberTracks() {
        return memberTracks;
    }

    public void setMemberTracks(List<DataTrack> memberTracks) {
        this.memberTracks = memberTracks;
    }

    public ErrorBarType getErrorBarType() {
        return errorBarType;
    }

    public void setErrorBarType(ErrorBarType errorBarType) {
        this.errorBarType = errorBarType;
    }

    public ErrorBarStyle getErrorBarStyle() {
        return errorBarStyle;
    }

    public void setErrorBarStyle(ErrorBarStyle errorBarStyle) {
        this.errorBarStyle = errorBarStyle;
    }

    public float getNaValue() {
        return naValue;
    }

    public void setNaValue(float naValue) {
        this.naValue = naValue;
        if (dataSource instanceof AverageErrorBarDataSource) {
            ((AverageErrorBarDataSource) dataSource).setNaValue(naValue);
        }
    }

    @Override
    public List<Component> getPopupMenuItems(TrackClickEvent te) {
        List<Component> items = new ArrayList<>(super.getPopupMenuItems(te));

        items.add(new JPopupMenu.Separator());

        JMenu errorBarTypeMenu = new JMenu("Error Bar Type");
        ButtonGroup typeGroup = new ButtonGroup();
        JRadioButtonMenuItem semItem = new JRadioButtonMenuItem("SEM", errorBarType == ErrorBarType.SEM);
        JRadioButtonMenuItem sdItem = new JRadioButtonMenuItem("SD", errorBarType == ErrorBarType.SD);
        JRadioButtonMenuItem noneItem = new JRadioButtonMenuItem("None", errorBarType == ErrorBarType.NONE);
        typeGroup.add(semItem);
        typeGroup.add(sdItem);
        typeGroup.add(noneItem);
        semItem.addActionListener(e -> {
            errorBarType = ErrorBarType.SEM;
            IGV.getInstance().repaint(List.of(this));
        });
        sdItem.addActionListener(e -> {
            errorBarType = ErrorBarType.SD;
            IGV.getInstance().repaint(List.of(this));
        });
        noneItem.addActionListener(e -> {
            errorBarType = ErrorBarType.NONE;
            IGV.getInstance().repaint(List.of(this));
        });
        errorBarTypeMenu.add(semItem);
        errorBarTypeMenu.add(sdItem);
        errorBarTypeMenu.add(noneItem);
        items.add(errorBarTypeMenu);

        JMenuItem colorItem = new JMenuItem("Set Error Bar Color...");
        colorItem.addActionListener(e -> {
            Color c = UIUtilities.showColorChooserDialog("Select Error Bar Color", errorBarStyle.getColorOverride());
            if (c != null) {
                errorBarStyle.setColorOverride(c);
                IGV.getInstance().repaint(List.of(this));
            }
        });
        items.add(colorItem);

        JMenuItem styleItem = new JMenuItem("Error Bar Style...");
        styleItem.addActionListener(e -> {
            ErrorBarStyleDialog dlg = new ErrorBarStyleDialog(IGV.getInstance().getMainFrame(), errorBarStyle);
            dlg.setVisible(true);
            if (!dlg.isCanceled()) {
                IGV.getInstance().repaint(List.of(this));
            }
        });
        items.add(styleItem);

        items.add(new JPopupMenu.Separator());
        JMenuItem restoreItem = new JMenuItem("Restore Original Tracks");
        restoreItem.addActionListener(e -> {
            IGV igv = IGV.getInstance();
            TrackStructureEdit.Snapshot before = igv.captureTrackStructure(List.of(this));
            // Derived from the current neighbors in the panel rather than trusting
            // getOrder() directly - see MainPanel.computeOrderForCurrentPosition().
            long order = IGV.getInstance().getMainPanel().computeOrderForCurrentPosition(this);
            WindowFunction windowFunction = getWindowFunction();
            DataRange dataRange = getDataRange();
            for (Track member : memberTracks) {
                member.setOrder(order);
                // Members keep whatever WindowFunction/DataRange they had before being
                // averaged for as long as the average track lives (AverageErrorBarDataSource
                // only ever borrows a member's WindowFunction transiently, for one fetch, then
                // restores it - see that class). Restoring adopts the average's own settings
                // instead, since that's what the user was actually looking at just before
                // asking to split it back apart.
                if (windowFunction != null) {
                    member.setWindowFunction(windowFunction);
                }
                // Freeze the average's current data range onto the member (even if the
                // average got there via its own autoscale - getDataRange() reflects the
                // latest computed range either way) rather than leaving the member's own
                // autoScale on: a member left with autoScale=true (its state from before
                // averaging) would otherwise recompute its *own* range from just its own
                // values on the very next repaint, discarding what was just copied and
                // potentially landing on a different scale than its former sibling members.
                member.setAutoScale(false);
                if (dataRange != null) {
                    member.setDataRange(dataRange.copy());
                }
            }
            RegionalTrackSettingsTransfer.TransferResult regionalTransfer =
                    RegionalTrackSettingsTransfer.inheritCompositeSettings(this, memberTracks);
            if (TrackPairing.isPaired(this)) {
                TrackPairing.unpair(List.of(this), IGV.getInstance().getAllTracks());
            }
            igv.replaceTracksPreserving(List.of(this), new ArrayList<>(memberTracks));
            List<Track> allTracks = igv.getAllTracks();
            for (Track member : memberTracks) {
                TrackPairing.reconcilePairingAfterRestore(member, allTracks);
            }
            if (regionalTransfer.changed()) RegionalTrackSettingsTransfer.publishChanges();
            igv.repaint();
            igv.recordUndoableTrackStructureChange(
                    "Restore Original Tracks", before, List.of(this));
            RegionalTrackSettingsTransfer.showPairModeWarning(
                    "restoring the average track", regionalTransfer.pairModesRemoved());
        });
        items.add(restoreItem);

        return items;
    }

    /**
     * Autoscale range, widened to the mean ± error bar extent rather than just the mean
     * (DataTrack's own implementation only ever looks at {@code score.getScore()}, i.e. the
     * plain mean - it has no notion of an error bar at all). Without this, autoscale clips
     * the error bars right at the plot's min/max edge whenever they'd otherwise stick out
     * past the range the mean values alone would need.
     */
    @Override
    public Range getInViewRange(ReferenceFrame referenceFrame) {
        List<LocusScore> scores = getInViewScores(referenceFrame);
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float baseline = getDataRange() == null ? 0 : getDataRange().getBaseline();
        for (LocusScore score : scores) {
            float value = score.getScore();
            if (Float.isNaN(value)) {
                continue;
            }
            float err = 0;
            if (errorBarType != ErrorBarType.NONE && score instanceof AverageErrorLocusScore) {
                AverageErrorLocusScore es = (AverageErrorLocusScore) score;
                if (es.getN() >= 2) {
                    float e = errorBarType == ErrorBarType.SD ? es.getSd() : es.getSem();
                    if (!Float.isNaN(e)) {
                        err = e;
                    }
                }
            }
            float[] span = errorBarStyle.dataSpan(baseline, value, err);
            min = Math.min(min, span[0]);
            max = Math.max(max, span[1]);
        }
        return min > max ? null : new Range(min, max);
    }

    /**
     * Tooltip text for hovering/clicking the track: "Value: average X ± Y (SEM|SD) at
     * position P" instead of the generic DataTrack wording, since a plain "Window
     * function: ..." line doesn't convey that this is an average-with-error-bar value.
     */
    @Override
    public String getValueStringAt(String chr, double position, int mouseX, int mouseY, ReferenceFrame frame) {
        int zoom = Math.max(0, frame.getZoom());
        List<LocusScore> scores = getSummaryScores(chr, (int) position - 10, (int) position + 10, zoom).getFeatures();
        if (scores == null) {
            return null;
        }
        double bpPerPixel = frame.getScale();
        int buffer = (int) (2 * bpPerPixel);
        LocusScore score = (LocusScore) FeatureUtils.getFeatureAt(position, buffer, scores);
        if (score == null) {
            return null;
        }

        StringBuilder buf = new StringBuilder();
        buf.append(getName()).append("<br>");
        if (getDataRange() != null && getRenderer() instanceof XYPlotRenderer) {
            buf.append("Data scale: ").append(getDataRange().getMinimum())
                    .append(" - ").append(getDataRange().getMaximum()).append("<br>");
        }

        if (score instanceof AverageErrorLocusScore) {
            AverageErrorLocusScore es = (AverageErrorLocusScore) score;
            buf.append(String.format("Value: average %g", es.getScore()));
            if (errorBarType != ErrorBarType.NONE && es.getN() >= 2) {
                float err = errorBarType == ErrorBarType.SD ? es.getSd() : es.getSem();
                if (!Float.isNaN(err)) {
                    buf.append(String.format(" ± %g (%s)", err, errorBarType));
                }
            }
            buf.append(String.format(" at position %d", (int) position));
            buf.append("<br>n = ").append(es.getN());
        } else {
            buf.append(score.getValueString(position, mouseX, getWindowFunction()));
        }
        return buf.toString();
    }

    @Override
    public void marshalJSON(JSONObject json) {
        super.marshalJSON(json);
        json.put("errorBarType", errorBarType.toString());
        json.put("naValue", naValue);
        errorBarStyle.marshalJSON(json);
        if (dataSource instanceof AverageErrorBarDataSource) {
            json.put("windowFunction", ((AverageErrorBarDataSource) dataSource).getResolvedFunction().toString());
        }

        // RendererFactory doesn't know our custom render classes, so DataTrack's generic
        // "graphType" round-trip (used for stock Heatmap/DynSeq selections) silently skips
        // them - persist our own selection explicitly whenever it's one of ours.
        if (getRenderer() instanceof AverageErrorBarPointsRenderer) {
            json.put("averageRendererClass", "points");
        } else if (getRenderer() instanceof AverageErrorBarLineplotRenderer) {
            json.put("averageRendererClass", "linePlot");
        } else if (getRenderer() instanceof AverageErrorBarRenderer) {
            json.put("averageRendererClass", "bar");
        }

        JSONArray tracksArray = new JSONArray();
        for (DataTrack track : memberTracks) {
            JSONObject trackJson = new JSONObject();
            track.marshalJSON(trackJson);
            tracksArray.put(trackJson);
        }
        json.put("tracks", tracksArray);
    }

    @Override
    public void unmarshalJSON(JSONObject jsonObject) {
        super.unmarshalJSON(jsonObject);
        if (jsonObject.has("errorBarType")) {
            try {
                this.errorBarType = ErrorBarType.valueOf(jsonObject.getString("errorBarType"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (jsonObject.has("naValue")) {
            this.naValue = (float) jsonObject.getDouble("naValue");
        }
        this.errorBarStyle = ErrorBarStyle.fromJSON(jsonObject);

        // super.unmarshalJSON already restored a stock renderer (Heatmap/DynSeq) via the
        // generic "graphType" field, if that's what was saved. Our own custom render
        // classes aren't known to RendererFactory, so they're persisted separately above
        // (marshalJSON) and restored here; only fall back to Bar if neither matched.
        if (jsonObject.has("averageRendererClass")) {
            switch (jsonObject.getString("averageRendererClass")) {
                case "points":
                    setRendererClass(AverageErrorBarPointsRenderer.class);
                    break;
                case "linePlot":
                    setRendererClass(AverageErrorBarLineplotRenderer.class);
                    break;
                default:
                    setRendererClass(AverageErrorBarRenderer.class);
                    break;
            }
        } else if (getRenderer() == null) {
            setRendererClass(AverageErrorBarRenderer.class);
        }
        // Member-track reconstruction is handled by JSONSessionReader (mirrors the
        // "merged" track special-casing), which then calls setMemberTracks(...) and
        // setDatasource(new AverageErrorBarDataSource(members, resolvedFunction)).
    }
}
