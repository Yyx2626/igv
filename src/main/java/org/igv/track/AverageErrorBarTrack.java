package org.igv.track;

import org.igv.data.AverageErrorBarDataSource;
import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.FeatureUtils;
import org.igv.feature.LocusScore;
import org.igv.renderer.AverageErrorBarLineplotRenderer;
import org.igv.renderer.AverageErrorBarPointsRenderer;
import org.igv.renderer.AverageErrorBarRenderer;
import org.igv.renderer.ErrorBarStyle;
import org.igv.renderer.XYPlotRenderer;
import org.igv.ui.ErrorBarStyleDialog;
import org.igv.ui.IGV;
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

    /** Special constructor for session unmarshalling - mirrors {@code CombinedDataTrack}. */
    public AverageErrorBarTrack(String id, String name) {
        super(null, id, name, null);
    }

    public AverageErrorBarTrack(String id, String name, List<DataTrack> memberTracks,
                                 WindowFunction resolvedFunction, ErrorBarType errorBarType) {
        super(null, id, name, new AverageErrorBarDataSource(memberTracks, resolvedFunction));
        this.memberTracks = memberTracks;
        this.errorBarType = errorBarType;
        setRendererClass(AverageErrorBarRenderer.class);
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
            long order = getOrder();
            for (Track member : memberTracks) {
                member.setOrder(order);
            }
            if (TrackPairing.isPaired(this)) {
                TrackPairing.unpair(List.of(this), IGV.getInstance().getAllTracks());
            }
            IGV.getInstance().deleteTracks(List.of(this));
            IGV.getInstance().addTracks(new ArrayList<>(memberTracks));
            IGV.getInstance().repaint();
        });
        items.add(restoreItem);

        return items;
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
