package org.igv.track;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import htsjdk.tribble.Feature;
import org.apache.commons.math3.stat.StatUtils;
import org.igv.bedpe.InteractionTrack;
import org.igv.data.AbstractDataSource;
import org.igv.feature.Exon;
import org.igv.feature.IGVFeature;
import org.igv.feature.Range;
import org.igv.feature.Strand;
import org.igv.feature.basepair.BasePairTrack;
import org.igv.feature.genome.Genome;
import org.igv.feature.genome.GenomeManager;
import org.igv.feature.tribble.IGVBEDCodec;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.renderer.*;
import org.igv.alignment.AlignmentDataManager;
import org.igv.alignment.AlignmentTrack;
import org.igv.alignment.SAMWriter;
import org.igv.ui.AverageErrorBarOptionsDialog;
import org.igv.ui.DataRangeDialog;
import org.igv.ui.ErrorBarStyleDialog;
import org.igv.ui.FontManager;
import org.igv.ui.HeatmapScaleDialog;
import org.igv.ui.IGV;
import org.igv.ui.PairedDataRangeDialog;
import org.igv.ui.action.AverageErrorBarMenuAction;
import org.igv.ui.color.ColorUtilities;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.IGVPopupMenu;
import org.igv.ui.panel.ReferenceFrame;
import org.igv.ui.panel.TrackPanel;
import org.igv.ui.panel.TrackPanelScrollPane;
import org.igv.ui.panel.TrackSelectionPanel;
import org.igv.ui.util.FileDialogUtils;
import org.igv.ui.util.MessageUtils;
import org.igv.ui.util.UIUtilities;
import org.igv.util.LongRunningTask;
import org.igv.util.Pair;
import org.igv.util.ResourceLocator;
import org.igv.util.StringUtils;
import org.igv.util.blat.BlatClient;
import org.igv.util.extview.ExtendViewClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.igv.prefs.Constants.SHOW_SINGLE_TRACK_PANE_KEY;

/**
 * @author jrobinso
 */
public class TrackMenuUtils {

    static Logger log = LogManager.getLogger(TrackMenuUtils.class);
    final static String LEADING_HEADING_SPACER = "  ";

    /**
     * Return a popup menu with items applicable to the collection of tracks.
     *
     * @param track
     * @return
     */
    public static IGVPopupMenu getPopupMenu(Track track, String title, TrackClickEvent te) {

        if (log.isDebugEnabled()) {
            log.debug("enter getPopupMenu");
        }

        // Try multi-track menu first.  If multiple tracks are selected a menu with a subset of shared items is
        // created.  If the track clicked is not one of the selected tracks selections are cleared and we proceed
        // as usual with a single-track menu.  This behavior mimics google sheets.
        List<Track> selectedTracks = IGV.getSelectedTracks();
        if (!selectedTracks.isEmpty()) {
            if (selectedTracks.contains(track)) {
                IGVPopupMenu multiMenu = new IGVPopupMenu();
                JLabel multiTitle = new JLabel(LEADING_HEADING_SPACER + title, JLabel.CENTER);
                multiTitle.setFont(FontManager.getFont(Font.BOLD, 12));
                multiMenu.add(multiTitle);
                multiMenu.addSeparator();

                for (Component item : getSharedMenuItems(selectedTracks)) {
                    multiMenu.add(item);
                }
                for (Component item : getColorMenuItems(selectedTracks)) {
                    multiMenu.add(item);
                }

                // Pairing items always sit right after the color items (Unset Track
                // Color, etc.), in both this multi-track menu and the single-track menu
                // below - keeping the position consistent regardless of whether anything
                // is selected, instead of jumping to the very top when nothing is selected.
                addPairingSection(multiMenu, track, selectedTracks, false);

                // Filter to the applicable subset rather than requiring the WHOLE selection
                // to uniformly match - e.g. "select all" via the header checkbox also picks
                // up the always-present reference sequence track, which used to make an
                // otherwise-all-wig selection fail this check and lose the entire Data Menu
                // (Type of Graph, Windowing Function, Set Data Range, etc.) section, unlike
                // manually shift-selecting just the data tracks. Non-matching tracks in the
                // selection (like the sequence track) simply don't get these actions applied.
                List<Track> dataTracksInSelection = selectedTracks.stream()
                        .filter(t -> t.getType() == TrackType.wig || t.getType() == TrackType.merged
                                || t.getType() == TrackType.averageErrorBar)
                        .collect(Collectors.toList());
                if (!dataTracksInSelection.isEmpty()) {
                    multiMenu.addSeparator();
                    for (Component item : getDataMenuItems(dataTracksInSelection)) {
                        multiMenu.add(item);
                    }

                    final List<DataTrack> dataTrackList = Lists.newArrayList(Iterables.filter(dataTracksInSelection, DataTrack.class));
                    if (dataTrackList.size() > 1) {
                        final JMenuItem item = new JMenuItem("Overlay Tracks");
                        item.addActionListener(e -> {
                            MergedTracks mergedTracks = new MergedTracks(UUID.randomUUID().toString(), "Merged Tracks", dataTrackList);
                            mergedTracks.setOrder(dataTrackList.get(0).getOrder());
                            IGV.getInstance().removeTracks(dataTrackList);
                            IGV.getInstance().addTracks(List.of(mergedTracks));
                            IGV.getInstance().repaint();
                        });
                        multiMenu.addSeparator();
                        multiMenu.add(item);

                        JMenuItem averageItem = new JMenuItem("Average With Error Bar...");
                        averageItem.addActionListener(e -> {
                            WindowFunction defaultFn = AverageErrorBarMenuAction.computeDefaultWindowFunction(dataTracksInSelection);
                            AverageErrorBarOptionsDialog dlg = new AverageErrorBarOptionsDialog(IGV.getInstance().getMainFrame(), defaultFn);
                            dlg.setVisible(true);
                            if (!dlg.isCanceled()) {
                                AverageErrorBarMenuAction.createAverageErrorBarTrack(dataTracksInSelection,
                                        dlg.getErrorBarType(), dlg.getWindowFunction(), dlg.getNaValue());
                            }
                        });
                        multiMenu.add(averageItem);
                    }

                    List<AverageErrorBarTrack> avgTracksInSelection = dataTracksInSelection.stream()
                            .filter(t -> t instanceof AverageErrorBarTrack)
                            .map(t -> (AverageErrorBarTrack) t)
                            .collect(Collectors.toList());
                    if (!avgTracksInSelection.isEmpty()) {
                        multiMenu.addSeparator();
                        for (Component item : getAverageErrorBarMenuItems(avgTracksInSelection)) {
                            multiMenu.add(item);
                        }
                    }
                }

                List<Track> annotationTracksInSelection = selectedTracks.stream()
                        .filter(t -> t.getType() == TrackType.annotation)
                        .collect(Collectors.toList());
                if (!annotationTracksInSelection.isEmpty()) {
                    multiMenu.addSeparator();
                    for (Component item : getAnnotationMenuItems(annotationTracksInSelection, te)) {
                        multiMenu.add(item);
                    }
                }

                // Remove
                multiMenu.addSeparator();
                multiMenu.add(TrackMenuUtils.getRemoveMenuItem(selectedTracks));

                return multiMenu;
            }
            // Right-clicking a track outside the current multi-selection falls through to
            // the single-track menu below WITHOUT touching checkbox state - right-click
            // should never change which tracks are selected.
        }

        // Single track menu
        IGVPopupMenu menu = new IGVPopupMenu();
        JLabel popupTitle = new JLabel(LEADING_HEADING_SPACER + title, JLabel.CENTER);
        popupTitle.setFont(FontManager.getFont(Font.BOLD, 12));
        menu.add(popupTitle);
        menu.addSeparator();

        // Items most tracks share
        if (track.getType() != TrackType.sequence && track.getType() != TrackType.merged) {
            for (Component item : getSharedMenuItems(Collections.singleton(track))) {
                menu.add(item);
            }
            for (Component item : getColorMenuItems(Collections.singleton(track))) {
                menu.add(item);
            }
        }

        // Pairing items always sit right after the color items - see the matching
        // comment in the multi-track menu above.
        addPairingSection(menu, track, Collections.singleton(track), true);

        // Add track specific items
        menu.add(new JPopupMenu.Separator());
        List<Component> items = track.getPopupMenuItems(te);
        if (items != null) {
            for (Component item : items) {
                menu.add(item);
            }
        }

        // Add saveImage items
        menu.addSeparator();
        JMenuItem savePng = new JMenuItem("Save PNG image...");
        savePng.addActionListener(e1 -> saveImage(track, "png"));
        menu.add(savePng);
        JMenuItem saveSvg = new JMenuItem("Save SVG image...");
        saveSvg.addActionListener(e1 -> saveImage(track, "svg"));
        menu.add(saveSvg);

        // Add export features
        ReferenceFrame frame = FrameManager.getDefaultFrame();
        JMenuItem exportFeats = TrackMenuUtils.getExportFeatures(track, frame);
        if (exportFeats != null) menu.add(exportFeats);

        // Remove
        menu.addSeparator();
        menu.add(TrackMenuUtils.getRemoveMenuItem(Collections.singleton(track)));

        return menu;

    }

    public static void saveImage(Track track, String extension) {
        IGV.getInstance().saveImage(track.getViewport(), "igv_panel", extension);
    }

    /**
     * Uncheck every track's selection checkbox. Used when clicking empty space below the
     * track stack, or in the name-panel gutter above the tracks (see
     * {@code MainPanel}/{@code NameHeaderPanel}) - NOT by the right-click popup-menu path
     * (right-click must never change checkbox state).
     */
    public static void clearAllTrackSelections() {
        for (TrackPanel tp : IGV.getInstance().getTrackPanels()) {
            TrackPanelScrollPane sp = tp.getScrollPane();
            if (sp == null) continue;
            TrackSelectionPanel selPanel = sp.getSelectionPanel();
            if (selPanel != null) {
                selPanel.setTrackSelected(false);
            }
        }
    }


    /**
     * Return a list of shared menu items (rename, color, height, font size).
     * These are items applicable to both feature and data tracks.
     *
     * @param tracks
     * @return
     */
    public static List<Component> getSharedMenuItems(final Collection<Track> tracks) {
        List<Component> items = new ArrayList<>();

        if (tracks.size() == 1) {
            items.add(getTrackRenameItem(tracks));
        }
        if (!PreferencesManager.getPreferences().getAsBoolean(SHOW_SINGLE_TRACK_PANE_KEY)) {
            JMenuItem changeTrackHeightItem = getChangeTrackHeightItem(tracks);
            items.add(changeTrackHeightItem);
        }
        return items;
    }

    private static List<Component> getColorMenuItems(Collection<Track> tracks) {

        List<Component> items = new ArrayList<>();
        JMenuItem item = new JMenuItem("Set Track Color...");
        item.addActionListener(evt -> changeTrackColor(tracks));
        items.add(item);

        boolean anyAlignment = tracks.stream().anyMatch(t -> t.getType() == TrackType.alignment);
        if (!anyAlignment) {
            boolean allWig = !tracks.isEmpty() && tracks.stream()
                    .allMatch(t -> t.getType() == TrackType.wig || t.getType() == TrackType.averageErrorBar);
            String altLabel = allWig ? "Set Track Color (Negative Values)" : "Set Track Color (Negative Strand)";
            item = new JMenuItem(altLabel);
            item.setToolTipText(
                    "Change the alternate track color.  This color is used when drawing features with negative values or on the negative strand.");
            item.addActionListener(evt -> changeAltTrackColor(tracks));
            items.add(item);
        }

        item = new JMenuItem("Unset Track Color");
        item.setToolTipText("Revert track colors to default.");
        item.addActionListener(evt -> {
            for (Track t : tracks) {
                t.setColor(null);
                t.setAltColor(null);
                t.repaint();
            }
        });
        items.add(item);

        return items;
    }

    /**
     * If {@code track} (the one right-clicked, whether or not it's part of a larger
     * selection) is paired, add an informational "Top/Bottom of the pair with ..." label.
     * Worded from {@code track}'s own role, not its partner's, to avoid the earlier
     * "Paired with X (top)" phrasing where "(top)" read as describing X rather than the
     * clicked track itself. Adds no separator itself - callers group this with the
     * "Pair/Unpair Tracks" items via {@link #addPairingSection}, which owns the
     * surrounding separators so the whole pairing block reads as one section.
     */
    private static void addPairedIndicator(IGVPopupMenu menu, Track track) {
        if (!TrackPairing.isPaired(track)) {
            return;
        }
        Track partner = TrackPairing.findPartner(track, IGV.getInstance().getAllTracks());
        String roleText = track.getPairRole() == PairRole.TOP ? "Top" : "Bottom";
        String partnerName = partner != null ? partner.getName() : "?";
        JLabel pairLabel = new JLabel(LEADING_HEADING_SPACER + roleText + " of the pair with \"" + partnerName + "\"");
        pairLabel.setFont(FontManager.getFont(Font.ITALIC, 11));
        menu.add(pairLabel);
    }

    /**
     * Adds the whole pairing section (paired-indicator label, if applicable, plus
     * Pair/Unpair Tracks items) as one group, right after the color items - always in
     * the same place whether anything is selected or not. Only emits the leading
     * separator (and, for the single-track menu, the trailing one) when there's actually
     * something to show, so an unpaired single track with no 2-track selection gets no
     * empty section.
     */
    private static void addPairingSection(IGVPopupMenu menu, Track track, Collection<Track> tracks, boolean addTrailingSeparator) {
        List<Component> pairingItems = getPairingMenuItems(tracks);
        boolean showLabel = TrackPairing.isPaired(track);
        if (!showLabel && pairingItems.isEmpty()) {
            return;
        }
        menu.addSeparator();
        if (showLabel) {
            addPairedIndicator(menu, track);
        }
        for (Component item : pairingItems) {
            menu.add(item);
        }
        if (addTrailingSeparator) {
            menu.addSeparator();
        }
    }

    /**
     * "Pair Tracks" (exactly 2 selected, not already paired with each other) and/or
     * "Unpair Tracks" (selection includes at least one already-paired track).
     */
    private static List<Component> getPairingMenuItems(Collection<Track> tracks) {
        List<Component> items = new ArrayList<>();

        List<Track> list = new ArrayList<>(tracks);
        boolean anyPaired = list.stream().anyMatch(TrackPairing::isPaired);

        if (list.size() == 2 && !(TrackPairing.isPaired(list.get(0))
                && list.get(0).getPairId().equals(list.get(1).getPairId()))) {
            JMenuItem item = new JMenuItem("Pair Tracks");
            item.addActionListener(evt -> {
                TrackPairing.unpair(list, IGV.getInstance().getAllTracks());
                TrackPairing.pair(list.get(0), list.get(1));
                IGV.getInstance().repaint(list);
            });
            items.add(item);
        }

        if (anyPaired) {
            JMenuItem item = new JMenuItem("Unpair Tracks");
            item.addActionListener(evt -> {
                TrackPairing.unpair(list, IGV.getInstance().getAllTracks());
                IGV.getInstance().repaint(list);
            });
            items.add(item);
        }

        return items;
    }

    /**
     * Batch versions of {@code AverageErrorBarTrack.getPopupMenuItems}'s Error Bar Type /
     * Color / Style / Restore items, for when one or more {@code AverageErrorBarTrack}s
     * are part of the current (checkbox) selection. The single-track {@code
     * track.getPopupMenuItems(te)} path only runs when nothing is selected - a selected
     * average track (alone or with others) otherwise fell through the multi-track branch
     * with none of its own menu items at all. Each action here applies to every average
     * track in {@code avgTracks}.
     */
    private static List<Component> getAverageErrorBarMenuItems(List<AverageErrorBarTrack> avgTracks) {
        List<Component> items = new ArrayList<>();

        JMenu errorBarTypeMenu = new JMenu("Error Bar Type");
        ButtonGroup typeGroup = new ButtonGroup();
        JRadioButtonMenuItem semItem = new JRadioButtonMenuItem("SEM");
        JRadioButtonMenuItem sdItem = new JRadioButtonMenuItem("SD");
        JRadioButtonMenuItem noneItem = new JRadioButtonMenuItem("None");
        typeGroup.add(semItem);
        typeGroup.add(sdItem);
        typeGroup.add(noneItem);
        semItem.addActionListener(e -> {
            avgTracks.forEach(t -> t.setErrorBarType(ErrorBarType.SEM));
            IGV.getInstance().repaint(new ArrayList<Track>(avgTracks));
        });
        sdItem.addActionListener(e -> {
            avgTracks.forEach(t -> t.setErrorBarType(ErrorBarType.SD));
            IGV.getInstance().repaint(new ArrayList<Track>(avgTracks));
        });
        noneItem.addActionListener(e -> {
            avgTracks.forEach(t -> t.setErrorBarType(ErrorBarType.NONE));
            IGV.getInstance().repaint(new ArrayList<Track>(avgTracks));
        });
        errorBarTypeMenu.add(semItem);
        errorBarTypeMenu.add(sdItem);
        errorBarTypeMenu.add(noneItem);
        items.add(errorBarTypeMenu);

        JMenuItem colorItem = new JMenuItem("Set Error Bar Color...");
        colorItem.addActionListener(e -> {
            Color c = UIUtilities.showColorChooserDialog("Select Error Bar Color", avgTracks.get(0).getErrorBarStyle().getColorOverride());
            if (c != null) {
                avgTracks.forEach(t -> t.getErrorBarStyle().setColorOverride(c));
                IGV.getInstance().repaint(new ArrayList<Track>(avgTracks));
            }
        });
        items.add(colorItem);

        JMenuItem styleItem = new JMenuItem("Error Bar Style...");
        styleItem.addActionListener(e -> {
            ErrorBarStyleDialog dlg = new ErrorBarStyleDialog(IGV.getInstance().getMainFrame(), avgTracks.get(0).getErrorBarStyle());
            dlg.setVisible(true);
            if (!dlg.isCanceled()) {
                for (int i = 1; i < avgTracks.size(); i++) {
                    avgTracks.get(i).getErrorBarStyle().copyFrom(avgTracks.get(0).getErrorBarStyle());
                }
                IGV.getInstance().repaint(new ArrayList<Track>(avgTracks));
            }
        });
        items.add(styleItem);

        items.add(new JPopupMenu.Separator());
        JMenuItem restoreItem = new JMenuItem(avgTracks.size() > 1 ? "Restore Original Tracks (each)" : "Restore Original Tracks");
        restoreItem.addActionListener(e -> {
            for (AverageErrorBarTrack avgTrack : avgTracks) {
                long order = avgTrack.getOrder();
                List<DataTrack> members = avgTrack.getMemberTracks();
                for (Track member : members) {
                    member.setOrder(order);
                }
                if (TrackPairing.isPaired(avgTrack)) {
                    TrackPairing.unpair(List.of(avgTrack), IGV.getInstance().getAllTracks());
                }
                IGV.getInstance().deleteTracks(List.of(avgTrack));
                IGV.getInstance().addTracks(new ArrayList<>(members));
            }
            IGV.getInstance().repaint();
        });
        items.add(restoreItem);

        return items;
    }

    public static void addZoomItems(JPopupMenu menu, final ReferenceFrame frame) {

        if (FrameManager.isGeneListMode()) {
            JMenuItem item = new JMenuItem("Reset panel to '" + frame.getName() + "'");
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    frame.reset();
                    // TODO -- paint only panels for this frame
                }
            });
            menu.add(item);
        }


        JMenuItem zoomOutItem = new JMenuItem("Zoom out");
        zoomOutItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                frame.doZoomIncrement(-1);
            }
        });
        menu.add(zoomOutItem);

        JMenuItem zoomInItem = new JMenuItem("Zoom in");
        zoomInItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                frame.doZoomIncrement(1);
            }
        });
        menu.add(zoomInItem);


    }


    /**
     * Return a list of menu items applicable to data tracks.
     * Null entries represent separators.
     */
    public static List<Component> getDataMenuItems(final Collection<Track> tracks) {

        List<Component> items = new ArrayList<>();

        if (log.isTraceEnabled()) {
            log.trace("enter getDataPopupMenu");
        }

        items.addAll(getDataRendererMenuItems(Arrays.asList("Heatmap", "Bar Chart", "Points", "Line Plot", "DynSeq"), tracks));
        items.add(new JPopupMenu.Separator());

        // Get intersection of all valid window functions for selected tracks
        Set<WindowFunction> avaibleWindowFunctions = new LinkedHashSet<>();
        avaibleWindowFunctions.addAll(AbstractDataSource.ORDERED_WINDOW_FUNCTIONS);
        for (Track track : tracks) {
            avaibleWindowFunctions.retainAll(track.getAvailableWindowFunctions());
        }

        WindowFunction currentWindowFunction = null;
        for (Track track : tracks) {
            final WindowFunction twf = track.getWindowFunction();
            if (currentWindowFunction == null) {
                currentWindowFunction = twf;
            } else {
                if (twf != currentWindowFunction) {
                    currentWindowFunction = null;
                    break;
                }
            }
        }

        if (avaibleWindowFunctions.size() > 0) {
            JLabel statisticsHeading = new JLabel(LEADING_HEADING_SPACER + "Windowing Function", JLabel.LEFT);
            statisticsHeading.setFont(FontManager.getFont(Font.BOLD, 12));
            items.add(statisticsHeading);

            for (final WindowFunction wf : avaibleWindowFunctions) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(wf.getValue());
                item.setSelected(currentWindowFunction == wf);
                item.addActionListener(evt -> changeStatType(wf.toString(), tracks));
                items.add(item);
            }

            items.add(new JPopupMenu.Separator());
        }


        items.add(getDataRangeItem(tracks));

        items.add(getHeatmapScaleItem(tracks));

        if (tracks.size() > 0) {
            items.add(getLogScaleItem(tracks));
        }

        items.add(getAutoscaleItem(tracks));

        if (tracks.size() > 1 || (tracks.size() == 1 && tracks.iterator().next() instanceof MergedTracks)) {
            items.add(getGroupAutoscaleItem(tracks));
        }

        items.add(getShowDataRangeItem(tracks));

        return items;
    }

    public static List<Component> getAnnotationMenuItems(final Collection<Track> tracks, TrackClickEvent te) {

        List<Component> items = new ArrayList<>();

        for (Component item : getDisplayModeMenuItems(tracks)) {
            items.add(item);
        }
        items.add(new JSeparator());

        items.add(getGroupByStrandItem(tracks));

        if (tracks.size() == 1) {
            Track t = tracks.iterator().next();
            Feature f = t.getFeatureAtMousePosition(te);

            ReferenceFrame frame = te.getFrame();
            if (frame == null && !FrameManager.isGeneListMode()) {
                frame = FrameManager.getDefaultFrame();
            }

            String featureName = "";
            if (f != null) {
                items.add(new JPopupMenu.Separator());
                items.add(getCopyDetailsItem(f, te));

                Feature sequenceFeature = f;
                if (sequenceFeature instanceof IGVFeature) {
                    featureName = ((IGVFeature) sequenceFeature).getName();
                    double position = te.getChromosomePosition();
                    Collection<Exon> exons = ((IGVFeature) sequenceFeature).getExons();
                    if (exons != null) {
                        for (Exon exon : exons) {
                            if (position > exon.getStart() && position < exon.getEnd()) {
                                sequenceFeature = exon;
                                break;
                            }
                        }
                    }
                }

                items.add(getCopySequenceItem(sequenceFeature));

                if (frame != null && PreferencesManager.getPreferences().get(Constants.EXTVIEW_URL) != null) {
                    Range r = frame.getCurrentRange();
                    items.add(getExtendViewItem(featureName, sequenceFeature, r));
                }

                items.add(getBlatItem(sequenceFeature));
            }
        }

        items.add(new JPopupMenu.Separator());
        items.add(getChangeFeatureWindow(tracks));

        items.add(new JPopupMenu.Separator());
        items.add(getShowFeatureNames(tracks));
        items.add(getFeatureNameAttribute(tracks));

        return items;

    }


    /**
     * Return a list of data renderer menu items (graph type selection).
     */
    public static List<Component> getDataRendererMenuItems(List<String> labels, Collection<Track> tracks) {

        List<Component> items = new ArrayList<>();

        boolean allAverageErrorBar = !tracks.isEmpty() && tracks.stream().allMatch(t -> t instanceof AverageErrorBarTrack);

        final Map<String, Class> rendererMap = new LinkedHashMap<>();
        rendererMap.put("Heatmap", HeatmapRenderer.class);
        rendererMap.put("Bar Chart", allAverageErrorBar ? AverageErrorBarRenderer.class : BarChartRenderer.class);
        rendererMap.put("Points", allAverageErrorBar ? AverageErrorBarPointsRenderer.class : PointsRenderer.class);
        rendererMap.put("Line Plot", allAverageErrorBar ? AverageErrorBarLineplotRenderer.class : LineplotRenderer.class);
        rendererMap.put("DynSeq", DynSeqRenderer.class);

        JLabel rendererHeading = new JLabel(LEADING_HEADING_SPACER + "Type of Graph", JLabel.LEFT);
        rendererHeading.setFont(FontManager.getFont(Font.BOLD, 12));
        items.add(rendererHeading);

        // Get existing selections
        Set<Class> currentRenderers = new HashSet<Class>();
        for (Track track : tracks) {
            if (track.getRenderer() != null) {
                currentRenderers.add(track.getRenderer().getClass());
            }
        }

        // Create renderer menu items
        for (String label : labels) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(label);
            final Class rendererClass = rendererMap.get(label);
            if (currentRenderers.contains(rendererClass)) {
                item.setSelected(true);
            }
            item.addActionListener(evt -> changeRendererClass(tracks, rendererClass));
            items.add(item);
        }

        return items;
    }

    /**
     * Return a list of menu items applicable to BasePairTrack(s).
     * Null entries represent separators.
     */
    public static List<Component> getBasePairMenuItems(final Collection<Track> tracks) {

        List<Component> items = new ArrayList<>();

        final ArrayList<BasePairTrack> bpTracks = new ArrayList<BasePairTrack>();
        for (Track track : tracks) {
            if (track instanceof BasePairTrack) {
                bpTracks.add((BasePairTrack) track);
            }
        }

        JLabel arcColorHeading = new JLabel(LEADING_HEADING_SPACER + "Arc colors (click to change)", JLabel.LEFT);
        arcColorHeading.setFont(FontManager.getFont(Font.BOLD, 12));
        items.add(arcColorHeading);

        // aggregate arc color selector/legends for multiple selected tracks
        ArrayList<Pair<Color, String>> legendList = new ArrayList<Pair<Color, String>>();
        HashSet<String> keys = new HashSet<String>();
        for (BasePairTrack track : bpTracks) {
            List<String> colors = track.getRenderOptions().getColors();
            List<String> colorLabels = track.getRenderOptions().getColorLabels();
            for (int i = colors.size() - 1; i >= 0; --i) {
                String key = colors.get(i) + ' ' + colorLabels.get(i);
                if (!keys.contains(key)) {
                    keys.add(key);
                    legendList.add(new Pair<Color, String>(ColorUtilities.stringToColor(colors.get(i)), colorLabels.get(i)));
                }
            }
        }

        for (Pair<Color, String> pair : legendList) {
            final Color color = pair.getFirst();
            final String label = pair.getSecond();

            JLabel colorBox = new JLabel(LEADING_HEADING_SPACER);
            colorBox.setFont(FontManager.getFont(Font.BOLD, 12));
            colorBox.setForeground(color);

            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
            p.add(colorBox);
            p.add(Box.createHorizontalStrut(1));
            p.add(new JLabel(" " + label));
            p.add(Box.createGlue());
            p.setAlignmentX(Component.LEFT_ALIGNMENT);

            JMenuItem item = new JMenuItem();
            item.add(p);
            double w = p.getPreferredSize().getWidth();
            double h = p.getPreferredSize().getHeight();
            Dimension size = new Dimension();
            size.setSize(w, h + 8);
            item.setPreferredSize(size);

            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    changeBasePairTrackColor(bpTracks, color, label);
                }
            });

            items.add(item);
        }

        items.add(new JPopupMenu.Separator());

        JLabel arcDirectionHeading = new JLabel(LEADING_HEADING_SPACER + "Arc direction", JLabel.LEFT);
        arcDirectionHeading.setFont(FontManager.getFont(Font.BOLD, 12));
        items.add(arcDirectionHeading);

        // preselect up or down if all selected tracks have the same arc direction
        int upCount = 0;
        int downCount = 0;
        BasePairTrack.ArcDirection currentArcDirection = null; // mixed up and down
        for (BasePairTrack track : bpTracks) {
            if (track.getRenderOptions().getArcDirection() == BasePairTrack.ArcDirection.UP) ++upCount;
            if (track.getRenderOptions().getArcDirection() == BasePairTrack.ArcDirection.DOWN) ++downCount;
        }
        if (upCount == 0) currentArcDirection = BasePairTrack.ArcDirection.DOWN;
        if (downCount == 0) currentArcDirection = BasePairTrack.ArcDirection.UP;

        ButtonGroup group = new ButtonGroup();
        Map<String, BasePairTrack.ArcDirection> arcDirections = new LinkedHashMap<String, BasePairTrack.ArcDirection>(3);
        arcDirections.put("Up", BasePairTrack.ArcDirection.UP);
        arcDirections.put("Down", BasePairTrack.ArcDirection.DOWN);

        for (final Map.Entry<String, BasePairTrack.ArcDirection> entry : arcDirections.entrySet()) {
            JRadioButtonMenuItem mm = new JRadioButtonMenuItem(entry.getKey());
            mm.setSelected(currentArcDirection == entry.getValue());
            mm.addActionListener(evt -> {
                for (Track track : tracks) {
                    if (track instanceof BasePairTrack) {
                        ((BasePairTrack) track).getRenderOptions().setArcDirection(entry.getValue());
                    }
                }
                IGV.getInstance().repaint(tracks);
            });
            group.add(mm);
            items.add(mm);
        }
        items.add(new JPopupMenu.Separator());

        return items;
    }

    /**
     * Return a menu item which will export visible features
     * If {@code tracks} is not a single {@code FeatureTrack}, {@code null}
     * is returned (there should be no menu entry)
     *
     * @param track
     * @return
     */
    public static JMenuItem getExportFeatures(Track track, final ReferenceFrame frame) {

        JMenuItem exportData = null;

        if (track instanceof FeatureTrack) {
            exportData = new JMenuItem("Export Features...");
            exportData.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    File outFile = FileDialogUtils.chooseFile("Save Visible Data",
                            PreferencesManager.getPreferences().getLastTrackDirectory(),
                            new File("visibleData.bed"),
                            FileDialogUtils.SAVE);

                    exportVisibleFeatures(outFile.getAbsolutePath(), track, frame);
                }
            });
        } else if (track instanceof AlignmentTrack) {
            exportData = new JMenuItem("Export Alignments...");
            exportData.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    File outFile = FileDialogUtils.chooseFile("Save Visible Data",
                            PreferencesManager.getPreferences().getLastTrackDirectory(),
                            new File("visibleData.sam"),
                            FileDialogUtils.SAVE);

                    int countExp = exportVisibleAlignments(outFile.getAbsolutePath(), (AlignmentTrack) track, frame);
                    String msg = String.format("%d reads written", countExp);
                    MessageUtils.setStatusBarMessage(msg);
                }
            });
        }

        return exportData;
    }

    static int exportVisibleAlignments(String outPath, AlignmentTrack alignmentTrack, ReferenceFrame frame) {

        File outFile = new File(outPath);
        try {
            AlignmentDataManager dataManager = alignmentTrack.getDataManager();
            ResourceLocator inlocator = dataManager.getLocator();
            Range range = frame.getCurrentRange();

            //Read directly from file
            //return SAMWriter.writeAlignmentFilePicard(inlocator, outPath, range.getChr(), range.getStart(), range.getEnd());

            //Export those in memory, overlapping current view
            return SAMWriter.writeAlignmentFilePicard(dataManager, outFile, frame, range.getChr(), range.getStart(), range.getEnd());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

    }

    /**
     * Write features in {@code track} found in {@code range} to {@code outPath},
     * BED format
     * TODO Move somewhere else? run on separate thread?  Probably shouldn't be here
     *
     * @param outPath
     * @param track
     * @param frame
     */
    static void exportVisibleFeatures(String outPath, Track track, ReferenceFrame frame) {
        PrintWriter writer;
        try {
            writer = new PrintWriter(outPath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


        if (track instanceof FeatureTrack) {
            FeatureTrack fTrack = (FeatureTrack) track;

            String trackLine = fTrack.getExportTrackLine();
            if (trackLine != null) {
                writer.println(trackLine);
            }

            //Can't trust FeatureTrack.getFeatures to limit itself, so we filter
            List<Feature> features = fTrack.getVisibleFeatures(frame);
            IGVBEDCodec codec = new IGVBEDCodec();
            for (Feature feat : features) {
                String featString = codec.encode(feat);
                writer.println(featString);
            }
        }

        writer.flush();
        writer.close();
    }

    private static void changeStatType(String statType, Collection<Track> selectedTracks) {
        for (Track track : selectedTracks) {
            track.setWindowFunction(WindowFunction.valueOf(statType));
        }
        IGV.getInstance().repaint(selectedTracks);
    }


    public static JMenuItem getTrackRenameItem(final Collection<Track> selectedTracks) {

        // Change track height by attribute
        JMenuItem item = new JMenuItem("Rename Track...");
        item.addActionListener(evt -> {
            if (selectedTracks.isEmpty()) {
                return;
            }
            Track t = selectedTracks.iterator().next();
            String newName = JOptionPane.showInputDialog(IGV.getInstance().getMainFrame(), "Enter new name: ", t.getName());

            if (newName == null || newName.trim() == "") {
                return;
            }

            t.setName(newName);
            IGV.getInstance().repaintNamePanels();
        });
        if (selectedTracks.size() > 1) {
            item.setEnabled(false);
        }
        return item;
    }

    private static JMenuItem getHeatmapScaleItem(final Collection<Track> selectedTracks) {

        JMenuItem item = new JMenuItem("Set Heatmap Scale...");

        item.addActionListener(evt -> {

            if (selectedTracks.size() > 0) {

                // Find the first non-null color scale among the selected tracks
                ContinuousColorScale colorScale = null;
                for (Track t : selectedTracks) {
                    colorScale = t.getColorScale();
                    if (colorScale != null) {
                        break;
                    }
                }

                // Fallback: if none of the selected tracks provide a color scale, create a simple default
                if (colorScale == null) {
                    colorScale = new ContinuousColorScale(0, 10, Color.white, Color.red);
                }

                HeatmapScaleDialog dlg = new HeatmapScaleDialog(IGV.getInstance().getMainFrame(), colorScale);

                dlg.setVisible(true);
                if (!dlg.isCanceled()) {
                    colorScale = dlg.getColorScale();

                    // dlg.isFlipAxis());
                    for (Track track : selectedTracks) {
                        track.setColorScale(colorScale);
                    }
                    IGV.getInstance().repaint();
                }
            }
        });
        return item;
    }

    public static JMenuItem getDataRangeItem(final Collection<Track> selectedTracks) {
        JMenuItem item = new JMenuItem("Set Data Range...");

        item.addActionListener(evt -> {
            if (selectedTracks.size() > 0) {

                boolean anyPaired = selectedTracks.stream().anyMatch(TrackPairing::isPaired);
                if (anyPaired) {
                    showPairedDataRangeDialog(selectedTracks);
                    return;
                }

                // Create a datarange that spans the extent of prev tracks range
                DataRange prevAxisDefinition = DataRange.getFromTracks(selectedTracks);
                DataRangeDialog dlg = new DataRangeDialog(IGV.getInstance().getMainFrame(), prevAxisDefinition);
                dlg.setVisible(true);
                if (!dlg.isCanceled()) {
                    float min = Math.min(dlg.getMax(), dlg.getMin());
                    float max = Math.max(dlg.getMin(), dlg.getMax());
                    float mid = dlg.getBase();
                    mid = Math.max(min, Math.min(mid, max));

                    DataRange axisDefinition = new DataRange(dlg.getMin(), mid, dlg.getMax(),
                            prevAxisDefinition.isDrawBaseline(), dlg.isLog());

                    for (Track track : selectedTracks) {
                        track.setDataRange(axisDefinition);
                        track.setAutoScale(false);
                        track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
                    }
                    IGV.getInstance().repaint(selectedTracks);
                }

            }
        });

        return item;
    }

    /**
     * Data-Range dialog for a selection that includes at least one paired track: shows
     * two independent Min/Mid/Max/Log groups, one applied to each pair's top track (plus
     * any unpaired tracks in the selection) and one applied to each pair's bottom track.
     */
    private static void showPairedDataRangeDialog(Collection<Track> selectedTracks) {
        TrackPairing.Partition partition = TrackPairing.partitionTopBottom(selectedTracks);

        DataRange topDefaults = DataRange.getFromTracks(partition.top);
        DataRange bottomDefaults = partition.bottom.isEmpty() ? topDefaults : DataRange.getFromTracks(partition.bottom);

        PairedDataRangeDialog dlg = new PairedDataRangeDialog(IGV.getInstance().getMainFrame(), topDefaults, bottomDefaults);
        dlg.setVisible(true);
        if (dlg.isCanceled()) {
            return;
        }

        DataRange topRange = dlg.getTopDataRange(topDefaults.isDrawBaseline());
        DataRange bottomRange = dlg.getBottomDataRange(bottomDefaults.isDrawBaseline());

        for (Track track : partition.top) {
            track.setDataRange(topRange);
            track.setAutoScale(false);
            track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
        }
        for (Track track : partition.bottom) {
            track.setDataRange(bottomRange);
            track.setAutoScale(false);
            track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
        }
        IGV.getInstance().repaint(selectedTracks);
    }


    public static JMenuItem getLogScaleItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute


        final JCheckBoxMenuItem logScaleItem = new JCheckBoxMenuItem("Log scale");
        final boolean logScale = selectedTracks.iterator().next().getDataRange().isLog();
        logScaleItem.setSelected(logScale);
        logScaleItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                DataRange.Type scaleType = logScaleItem.isSelected() ?
                        DataRange.Type.LOG :
                        DataRange.Type.LINEAR;
                for (Track t : selectedTracks) {
                    t.getDataRange().setType(scaleType);
                }
                IGV.getInstance().repaint(selectedTracks);
            }
        });

        return logScaleItem;
    }

    public static JMenuItem getAutoscaleItem(final Collection<Track> selectedTracks) {

        final JCheckBoxMenuItem autoscaleItem = new JCheckBoxMenuItem("Autoscale");
        if (selectedTracks.size() == 0) {
            autoscaleItem.setEnabled(false);

        } else {
            boolean autoScale = checkAutoscale(selectedTracks);

            autoscaleItem.setSelected(autoScale);
            autoscaleItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent evt) {

                    boolean autoScale = autoscaleItem.isSelected();
                    for (Track t : selectedTracks) {
                        t.setAutoScale(autoScale);
                        if (autoScale) {
                            t.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
                        }
                    }
                    IGV.getInstance().repaint(selectedTracks);
                }
            });
        }
        return autoscaleItem;
    }

    private static JMenuItem getGroupAutoscaleItem(final Collection<Track> selectedTracks) {

        final JMenuItem autoscaleItem = new JMenuItem("Group Autoscale");

        autoscaleItem.addActionListener(evt -> {

            boolean anyPaired = selectedTracks.stream().anyMatch(TrackPairing::isPaired);
            if (anyPaired) {
                // Autoscale each pair's top-role tracks (plus any unpaired tracks) together
                // as one group, and each pair's bottom-role tracks together as a separate
                // group, rather than lumping top and bottom into a single shared scale.
                TrackPairing.Partition partition = TrackPairing.partitionTopBottom(selectedTracks);
                if (!partition.top.isEmpty()) {
                    int topGroup = IGV.getInstance().getSession().getNextAutoscaleGroup();
                    for (Track t : partition.top) {
                        t.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, autoscaleGroupLabel(topGroup));
                        t.setAutoScale(false);
                    }
                }
                if (!partition.bottom.isEmpty()) {
                    int bottomGroup = IGV.getInstance().getSession().getNextAutoscaleGroup();
                    for (Track t : partition.bottom) {
                        t.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, autoscaleGroupLabel(bottomGroup));
                        t.setAutoScale(false);
                    }
                }
            } else {
                int nextAutoscaleGroup = IGV.getInstance().getSession().getNextAutoscaleGroup();
                for (Track t : selectedTracks) {
                    t.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, autoscaleGroupLabel(nextAutoscaleGroup));
                    t.setAutoScale(false);
                }
            }

            PreferencesManager.getPreferences().setShowAttributeView(true);
            IGV.getInstance().revalidateTrackPanels();
            IGV.getInstance().repaint(selectedTracks);

        });

        return autoscaleItem;
    }

    /**
     * Formats an autoscale group id as a non-numeric-looking string ("Group 3", not
     * "3"). AttributeManager's color-scale heuristic (ColumnMetaData.isNumeric()) treats
     * a column as continuous-numeric once it's seen 2+ distinct values that all parse as
     * numbers, switching from a discrete, maximally-distinct palette to a light-to-dark
     * blue gradient - which made a low-numbered group (e.g. "0") render as a pale,
     * near-invisible blue once a second group existed. Group ids are inherently
     * categorical (there's no meaningful "in between" group 0.5), so keep this column
     * non-numeric-parseable to keep the palette one it always used before a second
     * distinct value appeared.
     */
    private static String autoscaleGroupLabel(int groupId) {
        return "Group " + groupId;
    }

    private static boolean checkAutoscale(Collection<Track> selectedTracks) {
        boolean autoScale = false;
        for (Track t : selectedTracks) {
            if (t.getAutoScale()) {
                autoScale = true;
                break;
            }
        }
        return autoScale;
    }

    public static JMenuItem getShowDataRangeItem(final Collection<Track> selectedTracks) {

        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show Data Range");
        if (selectedTracks.size() == 0) {
            item.setEnabled(false);

        } else {
            boolean showDataRange = true;
            for (Track t : selectedTracks) {
                if (!t.isShowDataRange()) {
                    showDataRange = false;
                    break;
                }
            }

            item.setSelected(showDataRange);
            item.addActionListener(evt -> {
                boolean showDataRange1 = item.isSelected();
                for (Track t : selectedTracks) {
                    if (t instanceof DataTrack) {
                        ((DataTrack) t).setShowDataRange(showDataRange1);
                    }
                }
                IGV.getInstance().repaint(selectedTracks);
            });
        }
        return item;
    }

    /**
     * Return a list of display mode radio button menu items.
     */
    public static List<Component> getDisplayModeMenuItems(final Collection<Track> tracks) {

        List<Component> items = new ArrayList<>();

        // Find "most representative" state from track collection
        Map<Track.DisplayMode, Integer> counts = new HashMap<Track.DisplayMode, Integer>(Track.DisplayMode.values().length);
        Track.DisplayMode currentMode = null;

        for (Track t : tracks) {
            Track.DisplayMode mode = t.getDisplayMode();
            if (counts.containsKey(mode)) {
                counts.put(mode, counts.get(mode) + 1);
            } else {
                counts.put(mode, 1);
            }
        }

        int maxCount = -1;
        for (Map.Entry<Track.DisplayMode, Integer> count : counts.entrySet()) {
            if (count.getValue() > maxCount) {
                currentMode = count.getKey();
                maxCount = count.getValue();
            }

            ButtonGroup group = new ButtonGroup();
            Map<String, Track.DisplayMode> modes = new LinkedHashMap<String, Track.DisplayMode>(4);
            modes.put("Collapse", Track.DisplayMode.COLLAPSED);
            modes.put("Expand", Track.DisplayMode.EXPANDED);

            for (final Map.Entry<String, Track.DisplayMode> entry : modes.entrySet()) {
                JRadioButtonMenuItem mm = new JRadioButtonMenuItem(entry.getKey());
                mm.setSelected(currentMode == entry.getValue());
                mm.addActionListener(evt -> {
                    for (Track t : tracks) {
                        t.setDisplayMode(entry.getValue());
                    }
                    IGV.getInstance().repaint(tracks);
                });
                group.add(mm);
                items.add(mm);
            }
        }

        items.add(getRowHeightItem(tracks));
        items.add(getMinimizeHeightItem(tracks));

        return items;
    }

    public static JMenuItem getRowHeightItem(Collection<Track> tracks) {
        JMenuItem rowHeightItem = new JMenuItem("Set Row Height...");
        rowHeightItem.addActionListener(evt -> {
            int currentHeight = tracks.iterator().next().getRowHeight();
            Integer newHeight = getIntegerInput("Row Height", currentHeight);
            if (newHeight != null && newHeight > 0) {
                for (Track t : tracks) {
                    t.setRowHeight(newHeight);
                }
                IGV.getInstance().repaint(tracks);
            }
        });
        return rowHeightItem;
    }

    public static JMenuItem getMinimizeHeightItem(Collection<Track> tracks) {
        JMenuItem item = new JMenuItem("Minimize Track Height");
        item.addActionListener(evt -> {
            for (Track t : tracks) {
                TrackPanelScrollPane viewport = t.getViewport();
                if (viewport != null) {
                    t.minimizeHeight();
                }
            }
            IGV.getInstance().repaint(tracks);
        });
        return item;
    }

    /**
     * Return a "Group by strand" menu item.
     */
    public static JRadioButtonMenuItem getGroupByStrandItem(final Collection<Track> tracks) {
        boolean allGrouped = tracks.stream().allMatch(track -> {
            return track instanceof FeatureTrack && ((FeatureTrack) track).isGroupByStrand();
        });

        final JRadioButtonMenuItem groupByItem = new JRadioButtonMenuItem("Group by strand");
        groupByItem.setSelected(allGrouped);
        groupByItem.addActionListener(evt -> {
            tracks.stream().forEach(track -> {
                if (track instanceof FeatureTrack) {
                    ((FeatureTrack) track).setGroupByStrand(groupByItem.isSelected());
                }
            });
            IGV.getInstance().repaint(tracks);
        });

        return groupByItem;
    }

    public static JMenuItem getRemoveMenuItem(final Collection<Track> selectedTracks) {

        boolean multiple = selectedTracks.size() > 1;

        JMenuItem item = new JMenuItem("Remove Track" + (multiple ? "s" : ""));
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                removeTracksAction(selectedTracks);
            }
        });
        return item;
    }

    /**
     * Display a dialog to the user asking to confirm if they want to remove the
     * selected tracks
     *
     * @param selectedTracks
     */
    public static void removeTracksAction(final Collection<Track> selectedTracks) {
        if (selectedTracks.isEmpty()) {
            return;
        }
        IGV.getInstance().deleteTracks(selectedTracks);
    }


    public static void changeRendererClass(final Collection<Track> selectedTracks, Class rendererClass) {
        for (Track track : selectedTracks) {
            track.setRendererClass(rendererClass);
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    public static void changeFeatureVisibilityWindow(final Collection<Track> selectedTracks) {

        Collection<Track> featureTracks = new ArrayList(selectedTracks.size());
        for (Track t : selectedTracks) {
            if (t instanceof FeatureTrack || t instanceof InteractionTrack) {
                featureTracks.add(t);
            }
        }

        if (featureTracks.isEmpty()) {
            return;
        }


        int origValue = featureTracks.iterator().next().getVisibilityWindow();
        double origValueKB = (origValue / 1000.0);
        Double value = getDoubleInput("Enter visibility window in kilo-bases.  To load all data enter zero.", origValueKB);
        if (value == null) {
            return;
        }

        for (Track track : featureTracks) {
            track.setVisibilityWindow((int) (value * 1000));
        }

        IGV.getInstance().repaint(featureTracks);
    }

    public static Integer getIntegerInput(String parameter, int value) {

        while (true) {

            String strValue = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(), parameter + ": ",
                    String.valueOf(value));

            //strValue will be null if dialog cancelled
            if ((strValue == null) || strValue.trim().equals("")) {
                return null;
            }

            try {
                value = Integer.parseInt(strValue);
                return value;
            } catch (NumberFormatException numberFormatException) {
                JOptionPane.showMessageDialog(IGV.getInstance().getMainFrame(),
                        parameter + " must be an integer number.");
            }
        }
    }

    public static Double getDoubleInput(String parameter, double value) {

        while (true) {

            String strValue = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(), parameter + ": ",
                    String.valueOf(value));

            //strValue will be null if dialog cancelled
            if ((strValue == null) || strValue.trim().equals("")) {
                return null;
            }

            try {
                value = Double.parseDouble(strValue);
                return value;
            } catch (NumberFormatException numberFormatException) {
                MessageUtils.showMessage(parameter + " must be a number.");
            }
        }
    }

    public static void changeTrackColor(final Collection<Track> selectedTracks) {

        if (selectedTracks.isEmpty()) {
            return;
        }

        Color currentSelection = selectedTracks.iterator().next().getColor();

        Color color = UIUtilities.showColorChooserDialog(
                "Select Track Color",
                currentSelection);

        if (color == null) {
            return;
        }

        for (Track track : selectedTracks) {
            //We preserve the alpha value. This is motivated by MergedTracks
            int currentAlpha = currentSelection != null ? currentSelection.getAlpha() : 255;
            track.setColor(ColorUtilities.modifyAlpha(color, currentAlpha));
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    public static void changeAltTrackColor(final Collection<Track> selectedTracks) {

        if (selectedTracks.isEmpty()) {
            return;
        }

        Color currentSelection = selectedTracks.iterator().next().getAltColor();

        Color color = UIUtilities.showColorChooserDialog(
                "Select Track Color (Negative Values)",
                currentSelection);

        if (color == null) {
            return;
        }

        for (Track track : selectedTracks) {
            track.setAltColor(ColorUtilities.modifyAlpha(color, currentSelection.getAlpha()));
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    /**
     * @author stevenbusan
     */
    public static void changeBasePairTrackColor(final List<BasePairTrack> tracks,
                                                final Color currentColor,
                                                final String currentLabel) {

        if (tracks.isEmpty()) {
            return;
        }

        Color newColor = UIUtilities.showColorChooserDialog(
                "Select Arc Color (" + currentLabel + ")",
                currentColor);

        if (newColor == null) {
            return;
        }

        for (BasePairTrack t : tracks) {
            t.getRenderOptions().changeColor(currentColor, currentLabel, newColor);
        }
        IGV.getInstance().repaint(tracks);

    }

    public static JMenuItem getCopyDetailsItem(final Feature f, final TrackClickEvent evt) {
        JMenuItem item = new JMenuItem("Copy Details to Clipboard");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {

                ReferenceFrame frame = evt.getFrame();
                int mouseX = evt.getMouseEvent().getX();

                double location = frame.getChromosomePosition(mouseX);
                if (f instanceof IGVFeature) {
                    String details = f.getChr() + ":" + (f.getStart() + 1) + "-" + f.getEnd() +
                            System.getProperty("line.separator") + System.getProperty("line.separator");
                    String valueString = ((IGVFeature) f).getValueString(location, mouseX, null);
                    if (details != null) {
                        details += valueString;
                        details = details.replace("<br>", System.getProperty("line.separator"));
                        details = details.replace("<br/>", System.getProperty("line.separator"));
                        details = details.replace("<b>", "");
                        details = details.replace("</b>", "");
                        details = details.replace("&nbsp;", " ");
                        details = details.replace("<hr>",
                                System.getProperty("line.separator") + "--------------------------" + System.getProperty("line.separator"));
                        StringUtils.copyTextToClipboard(details);
                    }
                }
            }
        });
        return item;
    }

    public static JMenuItem getCopySequenceItem(final Feature f) {

        final Strand strand;
        if (f instanceof IGVFeature) {
            strand = ((IGVFeature) f).getStrand();
        } else {
            strand = Strand.NONE;
        }

        JMenuItem item = new JMenuItem("Copy Sequence");
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                Genome genome = GenomeManager.getInstance().getCurrentGenome();
                IGV.copySequenceToClipboard(genome, f.getChr(), f.getStart(), f.getEnd(), strand);
            }
        });
        return item;
    }

    public static JMenuItem getExtendViewItem(final String featureName, final Feature f, final Range r) {
        JMenuItem item = new JMenuItem("ExtView");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                ExtendViewClient.postExtendView(featureName, f.getStart(), f.getEnd(), r.getChr(), r.getStart(), r.getEnd());
            }
        });
        return item;
    }

    public static JMenuItem getBlatItem(final Feature f) {
        JMenuItem item = new JMenuItem("BLAT Sequence");
        final int start = f.getStart();
        final int end = f.getEnd();

        if ((end - start) > 20 && (end - start) < 8000) {

            item.addActionListener(evt -> {

                final Strand strand;
                if (f instanceof IGVFeature) {
                    strand = ((IGVFeature) f).getStrand();
                } else {
                    strand = Strand.NONE;
                }
                BlatClient.doBlatQueryFromRegion(f.getChr(), start, end, strand);
            });
        } else {
            item.setEnabled(false);
        }
        return item;
    }


    /**
     * Return a representative track height to use as the default.  For now
     * using the median track height.
     *
     * @return
     */
    public static int getRepresentativeTrackHeight(Collection<Track> tracks) {

        double[] heights = new double[tracks.size()];
        int i = 0;
        for (Track track : tracks) {
            heights[i] = track.getHeight();
            i++;
        }
        int medianTrackHeight = (int) Math.round(StatUtils.percentile(heights, 50));
        if (medianTrackHeight > 0) {
            return medianTrackHeight;
        }

        return PreferencesManager.getPreferences().getAsInt(Constants.INITIAL_TRACK_HEIGHT);

    }

    public static JMenuItem getChangeTrackHeightItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Set Track Height...");
        item.addActionListener(evt -> {
            if (selectedTracks.isEmpty()) {
                return;
            }

            final String parameter = "Track height";
            Integer value = getIntegerInput(parameter, getRepresentativeTrackHeight(selectedTracks));
            if (value == null) {
                return;
            }

            value = Math.max(0, value);
            for (Track track : selectedTracks) {
                track.setHeight(value);
            }
        });
        return item;
    }


    public static JMenuItem getChangeFeatureWindow(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Set Feature Visibility Window...");
        item.addActionListener(evt -> changeFeatureVisibilityWindow(selectedTracks));
        return item;
    }

    public static JMenuItem getShowFeatureNames(final Collection<Track> selectedTracks) {
        boolean currentValue = selectedTracks.stream().allMatch(t -> t.isShowFeatureNames());
        String label = currentValue ? "Hide Feature Names" : "Show Feature Names";
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(evt -> selectedTracks.stream().forEach(t -> {
            t.setShowFeatureNames(!currentValue);
            IGV.getInstance().repaint(selectedTracks);
        }));
        return item;
    }

    public static JMenuItem getFeatureNameAttribute(final Collection<Track> selectedTracks) {

        JMenuItem item = new JMenuItem("Set Feature Label Field...");
        item.addActionListener(evt -> {
            String currentVal = selectedTracks.iterator().next().getLabelField();
            if (currentVal == null) currentVal = "";
            final String newVal = JOptionPane.showInputDialog(IGV.getInstance().getMainFrame(), "Feature Label Field: ", currentVal);
            if (newVal == null) {
                return; // Dialog canceled
            }
            selectedTracks.stream().forEach(t -> {
                if (t instanceof FeatureTrack) {
                    ((FeatureTrack) t).setLabelField(newVal);
                }
            });
            IGV.getInstance().repaint(selectedTracks);
        });

        return item;
    }

    public static JMenuItem getChangeFontSizeItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Set Font Size...");
        item.addActionListener(evt -> {
            if (selectedTracks.isEmpty()) {
                return;
            }

            final String parameter = "Font size";
            int defaultValue = selectedTracks.iterator().next().getFontSize();
            Integer value = getIntegerInput(parameter, defaultValue);
            if (value == null) {
                return;
            }

            for (Track track : selectedTracks) {
                track.setFontSize(value);
            }

            IGV.getInstance().repaintNamePanels();
            IGV.getInstance().repaint(selectedTracks);
        });
        return item;
    }


    // Experimental methods follow

    public static JMenuItem getShowSortFramesItem(final Track track) {

        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("Sort frames");

        item.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                Runnable runnable = new Runnable() {
                    public void run() {
                        FrameManager.sortFrames(track);
                        IGV.getInstance().resetFrames();
                    }
                };
                LongRunningTask.submit(runnable);
            }

        });
        return item;
    }

    public static boolean hasDisplayModes(Collection<Track> tracks) {
        return tracks.stream().allMatch(t -> t.getDisplayMode() != null);
    }

}

