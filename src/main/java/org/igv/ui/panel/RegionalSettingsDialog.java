package org.igv.ui.panel;

import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.TrackRegionOverride;
import org.igv.renderer.DataRange;
import org.igv.track.DataTrack;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
import org.igv.ui.IGV;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Edits all display-only settings for one region in one place. */
public final class RegionalSettingsDialog extends JDialog {

    private static final int COL_ACTION = 0;
    private static final int COL_Y_AXIS = 1;
    private static final int COL_BACKGROUND = 2;
    private static final int COL_FOREGROUND = 3;
    private static final int COL_POSITIVE = 4;
    private static final int COL_NEGATIVE = 5;

    private enum RegionAction {
        NONE("None"), INVERT("Invert"), COLLAPSE("Collapse / delete");

        private final String label;

        RegionAction(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final RegionOfInterest region;
    private final List<Track> tracks;
    private RegionDisplayRule workingRule;
    private final SettingsTableModel model;
    private final JTable table;
    private final JList<String> rowHeader;
    private final List<JButton> collapseDisabledButtons = new ArrayList<>();
    private TrackRegionOverride copiedTrackFormat;
    private Object copiedCellValue;
    private int copiedCellColumn = -1;

    public static void showDialog(Component parent, RegionOfInterest region, Track initiallySelectedTrack) {
        if (region == null) return;
        Window owner = parent == null ? IGV.getInstance().getMainFrame()
                : SwingUtilities.getWindowAncestor(parent);
        new RegionalSettingsDialog(owner, region, initiallySelectedTrack).setVisible(true);
    }

    private RegionalSettingsDialog(Window owner, RegionOfInterest region, Track initiallySelectedTrack) {
        super(owner, "Regional Settings — " + region.getLocusString(), ModalityType.APPLICATION_MODAL);
        this.region = region;
        this.tracks = collectTracks();
        this.workingRule = region.getDisplayRule() == null
                ? new RegionDisplayRule() : region.getDisplayRule().copy();
        this.model = new SettingsTableModel();
        this.table = new JTable(model);
        this.rowHeader = createRowHeader();
        buildUi();
        selectInitialTrack(initiallySelectedTrack);
        updateEnabledState();
    }

    private static List<Track> collectTracks() {
        List<Track> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Track track : IGV.getInstance().getAllTracks()) {
            String id = track.getId();
            if (id != null && !id.isBlank() && ids.add(id)) result.add(track);
        }
        return result;
    }

    private JList<String> createRowHeader() {
        DefaultListModel<String> names = new DefaultListModel<>();
        names.addElement("Region (all tracks)");
        for (Track track : tracks) names.addElement(track.getName());
        JList<String> list = new JList<>(names);
        list.setFixedCellWidth(210);
        list.setFixedCellHeight(Math.max(24, table.getRowHeight()));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectionModel(table.getSelectionModel());
        list.setCellRenderer(new RowHeaderRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int row = list.locationToIndex(event.getPoint());
                if (row < 0) return;
                // BasicListUI already applies ordinary/Shift/Command selection to the shared
                // row selection model.  Extend that row selection across all setting columns.
                table.setColumnSelectionInterval(0, model.getColumnCount() - 1);
            }
        });
        return list;
    }

    private void buildUi() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setSelectionForeground(Color.BLACK);
        table.setSelectionBackground(new Color(190, 215, 245));
        table.setRowHeight(Math.max(24, table.getRowHeight()));
        int[] widths = {205, 130, 125, 125, 115, 115};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Object.class, new SettingsRenderer());
        table.setDefaultRenderer(Color.class, new SettingsRenderer());
        table.setDefaultRenderer(Boolean.class, new SettingsRenderer());
        table.getColumnModel().getColumn(COL_ACTION).setCellEditor(new ActionCellEditor());
        installSelectionHandlers();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setRowHeaderView(rowHeader);
        JLabel targetHeader = new JLabel("Target", SwingConstants.CENTER);
        targetHeader.setOpaque(true);
        targetHeader.setBackground(UIManager.getColor("TableHeader.background"));
        targetHeader.setForeground(Color.BLACK);
        targetHeader.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, targetHeader);
        add(scrollPane, BorderLayout.CENTER);

        JPanel tools = new JPanel();
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        JPanel firstTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addTool(firstTools, "Set Color...", this::setSelectedColors, true);
        addTool(firstTools, "Copy Setting", this::copySelectedCell, true);
        addTool(firstTools, "Paste Setting", this::pasteSelectedCells, true);
        addTool(firstTools, "Flip Y-axis", this::flipSelectedYAxis, true);
        addTool(firstTools, "Custom Data Range...", this::setSelectedCustomRange, true);
        addTool(firstTools, "Swap positive / negative color", this::swapSelectedColors, true);
        JPanel secondTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addTool(secondTools, "Copy track format", this::copySelectedTrackFormat, true);
        addTool(secondTools, "Apply format to selected tracks", this::applyCopiedTrackFormat, true);
        addTool(secondTools, "Reset selected cells", this::resetSelectedCells, true);
        addTool(secondTools, "Reset all", this::resetAll, false);
        tools.add(firstTools);
        tools.add(secondTools);

        JButton ok = new JButton("OK");
        ok.addActionListener(event -> accept());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JPanel confirmation = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        confirmation.add(ok);
        confirmation.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        south.add(tools, BorderLayout.CENTER);
        south.add(confirmation, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(1060, Math.min(690, 190 + model.getRowCount() * table.getRowHeight()));
        setMinimumSize(new Dimension(880, 330));
        setLocationRelativeTo(getOwner());
    }

    private void addTool(JPanel panel, String label, Runnable action, boolean disabledWhenCollapsed) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        panel.add(button);
        if (disabledWhenCollapsed) collapseDisabledButtons.add(button);
    }

    private void installSelectionHandlers() {
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || model.getRowCount() == 0) return;
                int column = table.columnAtPoint(event.getPoint());
                if (column >= 0) {
                    table.setRowSelectionInterval(0, model.getRowCount() - 1);
                    table.setColumnSelectionInterval(column, column);
                }
            }
        });

        JPopupMenu popup = new JPopupMenu();
        addPopupItem(popup, "Reset selected cells", this::resetSelectedCells);
        addPopupItem(popup, "Reset selected rows", this::resetSelectedRows);
        addPopupItem(popup, "Reset selected columns", this::resetSelectedColumns);
        popup.addSeparator();
        addPopupItem(popup, "Copy Setting", this::copySelectedCell);
        addPopupItem(popup, "Paste Setting", this::pasteSelectedCells);
        addPopupItem(popup, "Set Color...", this::setSelectedColors);
        addPopupItem(popup, "Flip Y-axis", this::flipSelectedYAxis);
        addPopupItem(popup, "Custom Data Range...", this::setSelectedCustomRange);
        addPopupItem(popup, "Swap positive / negative color", this::swapSelectedColors);
        popup.addSeparator();
        addPopupItem(popup, "Copy track format", this::copySelectedTrackFormat);
        addPopupItem(popup, "Apply copied format to selected tracks", this::applyCopiedTrackFormat);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { show(event); }
            @Override public void mouseReleased(MouseEvent event) { show(event); }
            private void show(MouseEvent event) {
                if (!event.isPopupTrigger()) return;
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row >= 0 && column >= 0 && !table.isCellSelected(row, column)) {
                    table.changeSelection(row, column, false, false);
                }
                popup.show(table, event.getX(), event.getY());
            }
        });
    }

    private static void addPopupItem(JPopupMenu popup, String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        popup.add(item);
    }

    private void selectInitialTrack(Track selectedTrack) {
        int row = 0;
        if (selectedTrack != null) {
            for (int i = 0; i < tracks.size(); i++) {
                if (tracks.get(i) == selectedTrack || tracks.get(i).getId().equals(selectedTrack.getId())) {
                    row = i + 1;
                    break;
                }
            }
        }
        table.changeSelection(row, COL_ACTION, false, false);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    private void stopEditing() {
        TableCellEditor editor = table.getCellEditor();
        if (editor != null) editor.stopCellEditing();
    }

    private void accept() {
        stopEditing();
        region.setDisplayRule(workingRule);
        IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
        for (ReferenceFrame frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
        IGV.getInstance().repaint();
        dispose();
    }

    private void updateEnabledState() {
        boolean enabled = !workingRule.isCollapsed();
        for (JButton button : collapseDisabledButtons) button.setEnabled(enabled);
        rowHeader.repaint();
        table.repaint();
    }

    private Track trackForRow(int row) {
        return tracks.get(row - 1);
    }

    private TrackRegionOverride overrideForRow(int row) {
        return row <= 0 ? null : workingRule.getTrackOverride(trackForRow(row).getId());
    }

    private TrackRegionOverride mutableOverrideForRow(int row) {
        TrackRegionOverride value = overrideForRow(row);
        return value == null ? new TrackRegionOverride() : value.copy();
    }

    private void setOverrideForRow(int row, TrackRegionOverride value) {
        workingRule.setTrackOverride(trackForRow(row).getId(), value);
    }

    private boolean allTracksInverted() {
        if (tracks.isEmpty()) return false;
        for (Track track : tracks) {
            TrackRegionOverride value = workingRule.getTrackOverride(track.getId());
            if (value == null || !value.isReverseX()) return false;
        }
        return true;
    }

    private void setAllTracksInverted(boolean inverted) {
        for (int row = 1; row <= tracks.size(); row++) {
            TrackRegionOverride value = mutableOverrideForRow(row);
            value.setReverseX(inverted);
            setOverrideForRow(row, value);
        }
    }

    private RegionAction regionAction() {
        if (workingRule.isCollapsed()) return RegionAction.COLLAPSE;
        return allTracksInverted() ? RegionAction.INVERT : RegionAction.NONE;
    }

    private void setRegionAction(RegionAction action) {
        workingRule.setCollapsed(action == RegionAction.COLLAPSE);
        if (action == RegionAction.INVERT) setAllTracksInverted(true);
        else if (action == RegionAction.NONE) setAllTracksInverted(false);
        updateEnabledState();
        model.fireTableDataChanged();
    }

    private boolean isColorColumn(int column) {
        return column >= COL_BACKGROUND && column <= COL_NEGATIVE;
    }

    private boolean isCellAvailable(int row, int column) {
        if (row == 0 && column == COL_ACTION) return true;
        if (workingRule.isCollapsed()) return false;
        if (row == 0) return column == COL_BACKGROUND || column == COL_FOREGROUND;
        if (column == COL_ACTION || column == COL_BACKGROUND || column == COL_FOREGROUND) return true;
        return trackForRow(row) instanceof DataTrack;
    }

    private List<Point> selectedCells() {
        List<Point> result = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            for (int column = 0; column < model.getColumnCount(); column++) {
                if (table.isCellSelected(row, column)) result.add(new Point(column, row));
            }
        }
        return result;
    }

    private void setSelectedColors() {
        stopEditing();
        List<Point> cells = selectedCells().stream()
                .filter(cell -> isColorColumn(cell.x) && isCellAvailable(cell.y, cell.x)).toList();
        if (cells.isEmpty()) {
            showInfo("Select one or more available color cells first.");
            return;
        }
        Point first = cells.get(0);
        Color current = (Color) model.getValueAt(first.y, first.x);
        Color base = current == null ? defaultColor(first.y, first.x) : current;
        Color chosen = JColorChooser.showDialog(this, "Set selected color", base);
        if (chosen == null) return;
        for (Point cell : cells) {
            Color old = (Color) model.getValueAt(cell.y, cell.x);
            int alpha = old == null ? defaultColor(cell.y, cell.x).getAlpha() : old.getAlpha();
            setCellColor(cell.y, cell.x,
                    new Color(chosen.getRed(), chosen.getGreen(), chosen.getBlue(), alpha));
        }
        model.fireTableDataChanged();
    }

    private static Color defaultColor(int row, int column) {
        if (row == 0 && column == COL_BACKGROUND) return RegionDisplayRule.DEFAULT_HIGHLIGHT_COLOR;
        if (row == 0 && column == COL_FOREGROUND) return RegionDisplayRule.DEFAULT_COVER_COLOR;
        return Color.GRAY;
    }

    private void setCellColor(int row, int column, Color color) {
        if (!isCellAvailable(row, column) || !isColorColumn(column)) return;
        if (row == 0) {
            if (column == COL_BACKGROUND) workingRule.setRegionBackgroundColor(color);
            else if (column == COL_FOREGROUND) workingRule.setRegionForegroundColor(color);
            return;
        }
        TrackRegionOverride value = mutableOverrideForRow(row);
        switch (column) {
            case COL_BACKGROUND -> value.setBackgroundColor(color);
            case COL_FOREGROUND -> value.setForegroundMaskColor(color);
            case COL_POSITIVE -> value.setPositiveColor(color);
            case COL_NEGATIVE -> value.setNegativeColor(color);
            default -> { return; }
        }
        setOverrideForRow(row, value);
    }

    private void copySelectedCell() {
        stopEditing();
        List<Point> cells = selectedCells();
        if (cells.size() != 1) {
            showInfo("Select exactly one cell to copy.");
            return;
        }
        Point cell = cells.get(0);
        if (!isCellAvailable(cell.y, cell.x)) {
            showInfo("That setting is not available for this row.");
            return;
        }
        copiedCellColumn = cell.x;
        copiedCellValue = model.getValueAt(cell.y, cell.x);
    }

    private void pasteSelectedCells() {
        stopEditing();
        if (copiedCellColumn < 0) {
            showInfo("Copy a setting first.");
            return;
        }
        for (Point cell : selectedCells()) {
            if (!isCellAvailable(cell.y, cell.x)) continue;
            if (isColorColumn(copiedCellColumn) && isColorColumn(cell.x)) {
                setCellColor(cell.y, cell.x, copiedCellValue instanceof Color ? (Color) copiedCellValue : null);
            } else if (copiedCellColumn == COL_Y_AXIS && cell.x == COL_Y_AXIS && cell.y > 0
                    && copiedCellValue instanceof TrackRegionOverride.YAxisMode mode) {
                setYAxisMode(cell.y, mode);
            } else if (copiedCellColumn == COL_ACTION && cell.x == COL_ACTION && cell.y > 0
                    && copiedCellValue instanceof Boolean inverted) {
                TrackRegionOverride value = mutableOverrideForRow(cell.y);
                value.setReverseX(inverted);
                setOverrideForRow(cell.y, value);
            }
        }
        model.fireTableDataChanged();
    }

    private void flipSelectedYAxis() {
        stopEditing();
        Set<String> processed = new HashSet<>();
        for (int row : table.getSelectedRows()) {
            if (row == 0 || !(trackForRow(row) instanceof DataTrack)) continue;
            Track track = trackForRow(row);
            if (!processed.add(track.getId())) continue;
            TrackRegionOverride current = mutableOverrideForRow(row);
            TrackRegionOverride.YAxisMode target = current.getYAxisMode() == TrackRegionOverride.YAxisMode.FLIP
                    ? TrackRegionOverride.YAxisMode.DEFAULT : TrackRegionOverride.YAxisMode.FLIP;
            setYAxisMode(row, target);
            Track partner = TrackPairing.findPartner(track, tracks);
            if (partner != null && partner.getId() != null) processed.add(partner.getId());
        }
        model.fireTableDataChanged();
    }

    private void setYAxisMode(int row, TrackRegionOverride.YAxisMode mode) {
        TrackRegionOverride value = mutableOverrideForRow(row);
        value.clearCustomRange();
        value.setYAxisMode(mode);
        setOverrideForRow(row, value);
        synchronizePairYAxis(row, mode);
    }

    private void setSelectedCustomRange() {
        stopEditing();
        List<Integer> rows = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            if (row > 0 && trackForRow(row) instanceof DataTrack) rows.add(row);
        }
        if (rows.isEmpty()) {
            showInfo("Select one or more numeric track rows first.");
            return;
        }
        int firstRow = rows.get(0);
        Track firstTrack = trackForRow(firstRow);
        TrackRegionOverride firstOverride = mutableOverrideForRow(firstRow);
        DataRange base = firstTrack.getDataRange();
        JTextField minimum = new JTextField(valueOrDefault(firstOverride.getRangeMinimum(),
                base == null ? 0 : base.getMinimum()), 8);
        JTextField baseline = new JTextField(valueOrDefault(firstOverride.getRangeBaseline(),
                base == null ? 0 : base.getBaseline()), 8);
        JTextField maximum = new JTextField(valueOrDefault(firstOverride.getRangeMaximum(),
                base == null ? 1 : base.getMaximum()), 8);
        JCheckBox log = new JCheckBox("Log scale", firstOverride.getLogScale() != null
                ? firstOverride.getLogScale() : base != null && base.isLog());
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 4));
        panel.add(new JLabel("Minimum")); panel.add(minimum);
        panel.add(new JLabel("Baseline")); panel.add(baseline);
        panel.add(new JLabel("Maximum")); panel.add(maximum);
        panel.add(new JLabel("")); panel.add(log);
        while (true) {
            int result = JOptionPane.showConfirmDialog(this, panel,
                    "Custom data range", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            try {
                float min = Float.parseFloat(minimum.getText().trim());
                float mid = Float.parseFloat(baseline.getText().trim());
                float max = Float.parseFloat(maximum.getText().trim());
                if (!Float.isFinite(min) || !Float.isFinite(mid) || !Float.isFinite(max)
                        || min >= max || mid < min || mid > max || (log.isSelected() && max <= 0)) {
                    throw new NumberFormatException();
                }
                for (int row : rows) {
                    TrackRegionOverride value = mutableOverrideForRow(row);
                    value.setCustomRange(min, mid, max, log.isSelected());
                    setOverrideForRow(row, value);
                    synchronizePairYAxis(row, TrackRegionOverride.YAxisMode.CUSTOM);
                }
                model.fireTableDataChanged();
                return;
            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(this,
                        "Use finite values with Minimum < Maximum and Baseline inside the range.",
                        "Invalid custom data range", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String valueOrDefault(Float value, float fallback) {
        return Float.toString(value == null ? fallback : value);
    }

    private void synchronizePairYAxis(int sourceRow, TrackRegionOverride.YAxisMode mode) {
        Track source = trackForRow(sourceRow);
        if (!TrackPairing.isPaired(source)) return;
        Track partner = TrackPairing.findPartner(source, tracks);
        if (partner == null || partner.getId() == null) return;
        TrackRegionOverride value = workingRule.getTrackOverride(partner.getId());
        value = value == null ? new TrackRegionOverride() : value.copy();
        if (mode == TrackRegionOverride.YAxisMode.FLIP) {
            value.clearCustomRange();
            value.setYAxisMode(TrackRegionOverride.YAxisMode.FLIP);
        } else if (value.getYAxisMode() == TrackRegionOverride.YAxisMode.FLIP) {
            value.setYAxisMode(TrackRegionOverride.YAxisMode.DEFAULT);
        }
        workingRule.setTrackOverride(partner.getId(), value);
    }

    private void swapSelectedColors() {
        stopEditing();
        for (int row : table.getSelectedRows()) {
            if (row == 0 || !(trackForRow(row) instanceof DataTrack)) continue;
            TrackRegionOverride value = mutableOverrideForRow(row);
            Color positive = value.getPositiveColor();
            value.setPositiveColor(value.getNegativeColor());
            value.setNegativeColor(positive);
            setOverrideForRow(row, value);
        }
        model.fireTableDataChanged();
    }

    private void copySelectedTrackFormat() {
        stopEditing();
        int[] rows = table.getSelectedRows();
        if (rows.length != 1 || rows[0] == 0) {
            showInfo("Select exactly one track row to copy.");
            return;
        }
        TrackRegionOverride source = overrideForRow(rows[0]);
        copiedTrackFormat = source == null ? new TrackRegionOverride() : source.copy();
    }

    private void applyCopiedTrackFormat() {
        stopEditing();
        if (copiedTrackFormat == null) {
            showInfo("Copy a track format first.");
            return;
        }
        for (int row : table.getSelectedRows()) {
            if (row == 0) continue;
            setOverrideForRow(row, copiedTrackFormat.copy());
            synchronizePairYAxis(row, copiedTrackFormat.getYAxisMode());
        }
        model.fireTableDataChanged();
    }

    private void resetSelectedCells() {
        stopEditing();
        for (Point cell : selectedCells()) resetCell(cell.y, cell.x);
        model.fireTableDataChanged();
    }

    private void resetSelectedRows() {
        stopEditing();
        for (int row : table.getSelectedRows()) {
            for (int column = 0; column < model.getColumnCount(); column++) resetCell(row, column);
        }
        model.fireTableDataChanged();
    }

    private void resetSelectedColumns() {
        stopEditing();
        for (int column : table.getSelectedColumns()) {
            for (int row = 0; row < model.getRowCount(); row++) resetCell(row, column);
        }
        model.fireTableDataChanged();
    }

    private void resetCell(int row, int column) {
        if (row == 0) {
            if (column == COL_ACTION) {
                workingRule.setCollapsed(false);
                setAllTracksInverted(false);
            } else if (column == COL_BACKGROUND) workingRule.setRegionBackgroundColor(null);
            else if (column == COL_FOREGROUND) workingRule.setRegionForegroundColor(null);
            updateEnabledState();
            return;
        }
        TrackRegionOverride value = mutableOverrideForRow(row);
        switch (column) {
            case COL_ACTION -> value.setReverseX(false);
            case COL_Y_AXIS -> {
                value.clearCustomRange();
                value.setYAxisMode(TrackRegionOverride.YAxisMode.DEFAULT);
                synchronizePairYAxis(row, TrackRegionOverride.YAxisMode.DEFAULT);
            }
            case COL_BACKGROUND -> value.setBackgroundColor(null);
            case COL_FOREGROUND -> value.setForegroundMaskColor(null);
            case COL_POSITIVE -> value.setPositiveColor(null);
            case COL_NEGATIVE -> value.setNegativeColor(null);
            default -> { return; }
        }
        setOverrideForRow(row, value);
    }

    private void resetAll() {
        stopEditing();
        workingRule = new RegionDisplayRule();
        copiedTrackFormat = null;
        copiedCellColumn = -1;
        copiedCellValue = null;
        updateEnabledState();
        model.fireTableDataChanged();
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Regional Settings", JOptionPane.INFORMATION_MESSAGE);
    }

    private final class SettingsTableModel extends AbstractTableModel {
        private final String[] names = {"Region action / Invert coordinates", "Y axis",
                "Background", "Foreground", "Positive", "Negative"};

        @Override public int getRowCount() { return tracks.size() + 1; }
        @Override public int getColumnCount() { return names.length; }
        @Override public String getColumnName(int column) { return names[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case COL_BACKGROUND, COL_FOREGROUND, COL_POSITIVE, COL_NEGATIVE -> Color.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == COL_ACTION && isCellAvailable(row, column);
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (row == 0) {
                return switch (column) {
                    case COL_ACTION -> regionAction();
                    case COL_BACKGROUND -> workingRule.getRegionBackgroundColor();
                    case COL_FOREGROUND -> workingRule.getRegionForegroundColor();
                    default -> null;
                };
            }
            TrackRegionOverride value = overrideForRow(row);
            return switch (column) {
                case COL_ACTION -> value != null && value.isReverseX();
                case COL_Y_AXIS -> value == null ? TrackRegionOverride.YAxisMode.DEFAULT : value.getYAxisMode();
                case COL_BACKGROUND -> value == null ? null : value.getBackgroundColor();
                case COL_FOREGROUND -> value == null ? null : value.getForegroundMaskColor();
                case COL_POSITIVE -> value == null ? null : value.getPositiveColor();
                case COL_NEGATIVE -> value == null ? null : value.getNegativeColor();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != COL_ACTION || !isCellAvailable(row, column)) return;
            if (row == 0 && value instanceof RegionAction action) setRegionAction(action);
            else if (row > 0 && value instanceof Boolean inverted) {
                TrackRegionOverride override = mutableOverrideForRow(row);
                override.setReverseX(inverted);
                setOverrideForRow(row, override);
                fireTableRowsUpdated(row, row);
            }
        }
    }

    private final class SettingsRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            int modelColumn = table.convertColumnIndexToModel(column);
            boolean available = isCellAvailable(row, modelColumn);
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            setForeground(available ? Color.BLACK : UIManager.getColor("Label.disabledForeground"));
            if (!available) {
                setText("—");
            } else if (value instanceof Color color) {
                setBackground(selected ? table.getSelectionBackground() : color);
                setForeground(selected ? Color.BLACK : contrast(color));
                setText(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
            } else if (value == null) {
                setText("Default");
            } else if (value instanceof Boolean inverted) {
                setText(inverted ? "☑" : "☐");
            } else {
                setText(value.toString());
            }
            return this;
        }

        private Color contrast(Color color) {
            double luminance = .2126 * color.getRed() + .7152 * color.getGreen() + .0722 * color.getBlue();
            return luminance < 128 ? Color.WHITE : Color.BLACK;
        }
    }

    private final class ActionCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JComboBox<RegionAction> regionActions = new JComboBox<>(RegionAction.values());
        private final JCheckBox trackInvert = new JCheckBox("Invert coordinates within this region");
        private int row;
        private boolean configuring;

        private ActionCellEditor() {
            trackInvert.setHorizontalAlignment(SwingConstants.CENTER);
            regionActions.addActionListener(event -> {
                if (!configuring) fireEditingStopped();
            });
            trackInvert.addActionListener(event -> fireEditingStopped());
        }

        @Override
        public Object getCellEditorValue() {
            return row == 0 ? regionActions.getSelectedItem() : trackInvert.isSelected();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean selected,
                                                     int row, int column) {
            this.row = row;
            configuring = true;
            if (row == 0) regionActions.setSelectedItem(value);
            else trackInvert.setSelected(Boolean.TRUE.equals(value));
            configuring = false;
            return row == 0 ? regionActions : trackInvert;
        }
    }

    private final class RowHeaderRenderer extends JLabel implements ListCellRenderer<String> {
        private RowHeaderRenderer() {
            setOpaque(true);
            setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean selected, boolean focused) {
            setText(value);
            setBackground(selected ? table.getSelectionBackground() : UIManager.getColor("TableHeader.background"));
            boolean available = index == 0 || !workingRule.isCollapsed();
            setForeground(available ? Color.BLACK : UIManager.getColor("Label.disabledForeground"));
            return this;
        }
    }
}
