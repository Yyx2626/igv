package org.igv.ui.panel;

import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.event.ViewChange;
import org.igv.feature.Range;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.Strand;
import org.igv.feature.genome.GenomeManager;
import org.igv.lists.GeneList;
import org.igv.ui.IGV;
import org.igv.util.StringUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/** Compact ROI navigator.  Regional display details live in {@link RegionalSettingsDialog}. */
public class RegionNavigatorDialog extends org.igv.ui.IGVDialog implements Observer, IGVEventObserver {

    private static final int COL_CHR = 0;
    private static final int COL_START = 1;
    private static final int COL_END = 2;
    private static final int COL_DESCRIPTION = 3;
    private static final int COL_SETTINGS = 4;
    private static final int MAX_SEQUENCE_LENGTH = 1_000_000;

    public static RegionNavigatorDialog activeInstance;

    private final RegionTableModel model = new RegionTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = new JTextField(18);
    private final JCheckBox showAllChromosomes = new JCheckBox("Show all chromosomes", true);
    private final JCheckBox zoomToRegion = new JCheckBox("Zoom to Region", true);
    private final JButton remove = new JButton("Remove");
    private final JButton view = new JButton("View");
    private final TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
    private boolean synchronizing;

    public static RegionNavigatorDialog getOrCreateInstance(Frame owner) {
        if (activeInstance == null) activeInstance = new RegionNavigatorDialog(owner);
        return activeInstance;
    }

    public static RegionNavigatorDialog getInstance() {
        return activeInstance;
    }

    public static boolean destroyInstance() {
        if (activeInstance == null) return false;
        RegionNavigatorDialog old = activeInstance;
        activeInstance = null;
        old.dispose();
        return true;
    }

    private RegionNavigatorDialog(Frame owner) {
        super(owner);
        buildUi();
        initializeListeners();
        synchRegions();
    }

    @Override
    public void update(Observable observable, Object value) {
        synchRegions();
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        synchRegions();
    }

    private void buildUi() {
        setTitle("Regions of Interest");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        add.addActionListener(event -> addRegion());
        remove.addActionListener(event -> removeSelectedRegions());
        showAllChromosomes.addActionListener(event -> sorter.sort());
        top.add(showAllChromosomes);
        top.add(add);
        top.add(remove);
        add(top, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setForeground(Color.BLACK);
        table.setSelectionForeground(Color.BLACK);
        table.getTableHeader().setForeground(Color.BLACK);
        table.setDefaultRenderer(RegionalSettingsValue.class, new SettingsRenderer());
        table.setDefaultRenderer(String.class, new TextRenderer());
        table.setDefaultRenderer(Integer.class, new CoordinateRenderer());
        int[] widths = {65, 95, 95, 180, 155};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.addMouseListener(new TableMouseHandler());
        JScrollPane scroll = new JScrollPane(table);
        table.setPreferredScrollableViewportSize(new Dimension(590, Math.max(120, table.getRowHeight() * 6)));
        add(scroll, BorderLayout.CENTER);

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT));
        view.addActionListener(event -> viewSelectedRegions());
        navigation.add(view);
        navigation.add(zoomToRegion);
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search"));
        searchPanel.add(search);
        JButton clear = new JButton("Clear Search");
        clear.addActionListener(event -> search.setText(""));
        searchPanel.add(clear);
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(navigation);
        bottom.add(searchPanel);
        add(bottom, BorderLayout.SOUTH);

        setSize(680, 370);
        setMinimumSize(new Dimension(600, 300));
        setLocationRelativeTo(getOwner());
    }

    private void initializeListeners() {
        model.addTableModelListener(new RegionTableListener());
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                String chromosome = (String) entry.getValue(COL_CHR);
                String description = (String) entry.getValue(COL_DESCRIPTION);
                if (chromosome == null) return false;
                String term = search.getText() == null ? "" : search.getText().trim().toLowerCase();
                if (!term.isEmpty() && (description == null || !description.toLowerCase().contains(term))) return false;
                String current = FrameManager.getDefaultFrame().getChrName();
                return showAllChromosomes.isSelected() || current == null || current.isEmpty()
                        || chromosome.equals(current);
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshFilter(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowActivated(WindowEvent event) { synchRegions(); }
            @Override public void windowClosed(WindowEvent event) {
                IGV.getInstance().getSession().getRegionsOfInterestObservable().deleteObserver(RegionNavigatorDialog.this);
                IGVEventBus.getInstance().unsubscribe(RegionNavigatorDialog.this);
                if (activeInstance == RegionNavigatorDialog.this) activeInstance = null;
            }
        });
        IGVEventBus.getInstance().subscribe(ViewChange.class, this);
        IGV.getInstance().getSession().getRegionsOfInterestObservable().addObserver(this);
        updateButtonsEnabled();
    }

    private void refreshFilter() {
        sorter.sort();
        model.fireTableDataChanged();
    }

    public void updateChromosomeDisplayed() {
        refreshFilter();
    }

    /** Commit an active cell edit before callers read/export the session ROI collection. */
    public void updateROIsFromRegionTable() {
        if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
        }
        boolean changed = false;
        for (int row = 0; row < model.getRowCount(); row++) changed |= updateRegion(row);
        if (changed) IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
    }

    public void synchRegions() {
        synchronizing = true;
        try {
            List<RegionOfInterest> regions = regions();
            model.setRowCount(0);
            for (RegionOfInterest region : regions) {
                model.addRow(new Object[]{region.getChr(), region.getDisplayStart(), region.getDisplayEnd(),
                        region.getDescription(), new RegionalSettingsValue(region)});
            }
        } finally {
            synchronizing = false;
        }
        model.fireTableDataChanged();
        updateButtonsEnabled();
    }

    private List<RegionOfInterest> regions() {
        return new ArrayList<>(IGV.getInstance().getSession().getAllRegionsOfInterest());
    }

    private RegionOfInterest regionAtModelRow(int modelRow) {
        List<RegionOfInterest> regions = regions();
        return modelRow >= 0 && modelRow < regions.size() ? regions.get(modelRow) : null;
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) updateButtonsEnabled();
    }

    private void updateButtonsEnabled() {
        int count = table.getSelectedRowCount();
        remove.setEnabled(count > 0);
        view.setEnabled(count > 0);
        zoomToRegion.setEnabled(count == 1);
    }

    private void addRegion() {
        String chromosome = FrameManager.getDefaultFrame().getChrName();
        if (FrameManager.isGeneListMode()) {
            showInfo("Regions cannot be created in gene list or split-screen views.");
        } else if (chromosome == null || chromosome.isEmpty() || chromosome.equalsIgnoreCase("All")) {
            showInfo("Select a chromosome before creating a region.");
        } else {
            Range range = FrameManager.getDefaultFrame().getCurrentRange();
            IGV.getInstance().addRegionOfInterestUndoable(
                    new RegionOfInterest(range.getChr(), range.getStart(), range.getEnd(), ""));
        }
    }

    private void removeSelectedRegions() {
        List<RegionOfInterest> selected = selectedRegions();
        if (selected.isEmpty()) {
            showInfo("No regions are selected.");
            return;
        }
        IGV.getInstance().removeRegionsOfInterestUndoable(selected);
    }

    private List<RegionOfInterest> selectedRegions() {
        List<RegionOfInterest> result = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            RegionOfInterest region = regionAtModelRow(table.convertRowIndexToModel(viewRow));
            if (region != null) result.add(region);
        }
        return result;
    }

    private void viewSelectedRegions() {
        List<RegionOfInterest> selected = selectedRegions();
        if (selected.isEmpty()) return;
        List<String> loci = new ArrayList<>();
        if (zoomToRegion.isSelected() || selected.size() > 1 || FrameManager.isGeneListMode()) {
            for (RegionOfInterest region : selected) loci.add(region.getLocusString());
        } else {
            RegionOfInterest region = selected.get(0);
            Range current = FrameManager.getDefaultFrame().getCurrentRange();
            int start = Math.max(0, region.getCenter() - current.getLength() / 2);
            loci.add(region.getChr() + ":" + (start + 1) + "-" + (start + current.getLength()));
        }
        IGV.getInstance().setGeneList(new GeneList("Regions of Interest", loci));
        IGV.getInstance().resetFrames();
    }

    private void openSettings(int modelRow) {
        RegionOfInterest region = regionAtModelRow(modelRow);
        if (region != null) RegionalSettingsDialog.showDialog(this, region, null);
    }

    private void resetSettings(int modelRow) {
        RegionOfInterest region = regionAtModelRow(modelRow);
        if (region == null || !region.hasActiveDisplayRule()) return;
        region.setDisplayRule(null);
        IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
        for (ReferenceFrame frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
        IGV.getInstance().repaint();
    }

    private void showPopup(MouseEvent event) {
        int viewRow = table.rowAtPoint(event.getPoint());
        if (viewRow < 0) return;
        if (!table.isRowSelected(viewRow)) table.setRowSelectionInterval(viewRow, viewRow);
        int modelRow = table.convertRowIndexToModel(viewRow);
        RegionOfInterest region = regionAtModelRow(modelRow);
        if (region == null) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem settings = new JMenuItem("Regional settings...");
        settings.addActionListener(action -> openSettings(modelRow));
        menu.add(settings);
        JMenuItem reset = new JMenuItem("Reset regional settings");
        reset.setEnabled(region.hasActiveDisplayRule());
        reset.addActionListener(action -> resetSettings(modelRow));
        menu.add(reset);
        menu.addSeparator();
        JMenuItem copySequence = new JMenuItem("Copy Sequence");
        copySequence.setEnabled(region.getLength() <= MAX_SEQUENCE_LENGTH);
        copySequence.addActionListener(action -> IGV.copySequenceToClipboard(
                GenomeManager.getInstance().getCurrentGenome(), region.getChr(),
                region.getStart(), region.getEnd(), Strand.NONE));
        menu.add(copySequence);
        JMenuItem copyDetails = new JMenuItem("Copy Details");
        copyDetails.addActionListener(action -> {
            String details = region.getLocusString();
            if (region.getDescription() != null && !region.getDescription().isEmpty()) {
                details += ", " + region.getDescription();
            }
            StringUtils.copyTextToClipboard(details);
        });
        menu.add(copyDetails);
        menu.show(table, event.getX(), event.getY());
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Regions of Interest", JOptionPane.INFORMATION_MESSAGE);
    }

    private final class TableMouseHandler extends MouseAdapter {
        @Override public void mousePressed(MouseEvent event) { handle(event); }
        @Override public void mouseReleased(MouseEvent event) { handle(event); }

        private void handle(MouseEvent event) {
            if (event.isPopupTrigger()) {
                showPopup(event);
                return;
            }
            if (!SwingUtilities.isLeftMouseButton(event) || event.getID() != MouseEvent.MOUSE_RELEASED) return;
            int viewRow = table.rowAtPoint(event.getPoint());
            int viewColumn = table.columnAtPoint(event.getPoint());
            if (viewRow >= 0 && viewColumn >= 0
                    && table.convertColumnIndexToModel(viewColumn) == COL_SETTINGS) {
                openSettings(table.convertRowIndexToModel(viewRow));
            }
        }
    }

    private final class RegionTableListener implements TableModelListener {
        @Override
        public void tableChanged(TableModelEvent event) {
            if (synchronizing || event.getFirstRow() < 0) return;
            int last = event.getLastRow() == Integer.MAX_VALUE
                    ? model.getRowCount() - 1 : Math.min(event.getLastRow(), model.getRowCount() - 1);
            boolean changed = false;
            for (int row = event.getFirstRow(); row <= last; row++) changed |= updateRegion(row);
            if (changed) IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
        }
    }

    private boolean updateRegion(int row) {
        RegionOfInterest region = regionAtModelRow(row);
        if (region == null) return false;
        boolean changed = false;
        Object description = model.getValueAt(row, COL_DESCRIPTION);
        String newDescription = description == null ? null : description.toString();
        if (!java.util.Objects.equals(region.getDescription(), newDescription)) {
            region.setDescription(newDescription);
            changed = true;
        }
        if (!region.hasActiveDisplayRule()) {
            Object chromosomeValue = model.getValueAt(row, COL_CHR);
            String requestedChromosome = chromosomeValue == null ? "" : chromosomeValue.toString().trim();
            var genome = GenomeManager.getInstance().getCurrentGenome();
            String canonicalChromosome = genome == null || requestedChromosome.isEmpty()
                    ? requestedChromosome : genome.getCanonicalChrName(requestedChromosome);
            boolean validChromosome = !canonicalChromosome.isEmpty()
                    && (genome == null || genome.getChromosome(canonicalChromosome) != null);
            if (!validChromosome) {
                synchronizing = true;
                try {
                    model.setValueAt(region.getChr(), row, COL_CHR);
                } finally {
                    synchronizing = false;
                }
                Toolkit.getDefaultToolkit().beep();
            } else if (!canonicalChromosome.equals(region.getChr())) {
                changed |= IGV.getInstance().getSession()
                        .moveRegionOfInterestWithoutNotification(region, canonicalChromosome);
            }
            Object start = model.getValueAt(row, COL_START);
            Object end = model.getValueAt(row, COL_END);
            if (start instanceof Number && end instanceof Number) {
                int newStart = Math.max(0, ((Number) start).intValue() - 1);
                int newEnd = Math.max(newStart + 1, ((Number) end).intValue());
                if (newStart != region.getStart() || newEnd != region.getEnd()) {
                    region.setStart(newStart);
                    region.setEnd(newEnd);
                    changed = true;
                }
            }
        }
        if (changed) IGV.getInstance().repaint();
        return changed;
    }

    private final class RegionTableModel extends DefaultTableModel {
        private RegionTableModel() {
            super(new Object[]{"Chr", "Start", "End", "Description", "Regional Settings"}, 0);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case COL_CHR, COL_DESCRIPTION -> String.class;
                case COL_START, COL_END -> Integer.class;
                case COL_SETTINGS -> RegionalSettingsValue.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            if (column == COL_SETTINGS) return false;
            RegionOfInterest region = regionAtModelRow(row);
            return column == COL_DESCRIPTION || region == null || !region.hasActiveDisplayRule();
        }
    }

    private record RegionalSettingsValue(RegionOfInterest region) {
        @Override
        public String toString() {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || !rule.hasAnyEffect()) return "Set...";
            int tracks = rule.getTrackOverrides().size();
            List<String> effects = new ArrayList<>();
            if (rule.isCollapsed()) effects.add("Collapse");
            if (rule.getRegionBackgroundColor() != null) effects.add("Background");
            if (rule.getRegionForegroundColor() != null) effects.add("Foreground");
            if (tracks > 0) effects.add(tracks + " track" + (tracks == 1 ? "" : "s"));
            return String.join(" + ", effects) + "  •  Edit...";
        }
    }

    private final class SettingsRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            label.setForeground(Color.BLACK);
            label.setHorizontalAlignment(CENTER);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(2, 4, 2, 4),
                    BorderFactory.createLineBorder(Color.BLACK)));
            label.setToolTipText("Click to edit all regional display settings");
            return label;
        }
    }

    private final class CoordinateRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            RegionOfInterest region = regionAtModelRow(table.convertRowIndexToModel(row));
            boolean locked = region != null && region.hasActiveDisplayRule();
            label.setForeground(locked && !selected
                    ? UIManager.getColor("Label.disabledForeground") : Color.BLACK);
            label.setToolTipText(locked
                    ? "Reset regional settings before changing this boundary." : null);
            return label;
        }
    }

    private final class TextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            RegionOfInterest region = regionAtModelRow(table.convertRowIndexToModel(row));
            int modelColumn = table.convertColumnIndexToModel(column);
            boolean lockedChromosome = modelColumn == COL_CHR
                    && region != null && region.hasActiveDisplayRule();
            label.setForeground(lockedChromosome && !selected
                    ? UIManager.getColor("Label.disabledForeground") : Color.BLACK);
            label.setToolTipText(lockedChromosome
                    ? "Reset regional settings before changing this chromosome." : null);
            return label;
        }
    }
}
