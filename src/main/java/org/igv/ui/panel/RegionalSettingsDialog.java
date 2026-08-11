package org.igv.ui.panel;

import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.TrackRegionOverride;
import org.igv.renderer.DataRange;
import org.igv.track.AttributeManager;
import org.igv.track.DataTrack;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
import org.igv.ui.IGV;
import org.igv.ui.undo.RegionalSettingsEdit;
import org.igv.ui.PairedDataRangeDialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;
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

    private enum YAxisSetting {
        DEFAULT("Default"),
        FLIP("Flip"),
        CUSTOM("Custom Range"),
        PAIR_SWAP("Pair Swap"),
        PAIR_FLIP("Pair Flip");

        private final String label;

        YAxisSetting(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final RegionOfInterest region;
    private final List<Track> tracks;
    private final RegionDisplayRule originalRule;
    private final Color originalBarColor;
    private Color workingBarColor;
    private RegionDisplayRule workingRule;
    private final SettingsTableModel model;
    private final JTable table;
    private final JList<String> rowHeader;
    private final List<JButton> collapseDisabledButtons = new ArrayList<>();
    private TrackRegionOverride copiedTrackFormat;
    private Object copiedCellValue;
    private int copiedCellColumn = -1;
    private boolean accepted;
    private boolean restored;
    private JButton barColorButton;
    private int rowHeaderWidth = 240;
    private int lastScrollPaneWidth = -1;

    public static void showDialog(Component parent, RegionOfInterest region, Track initiallySelectedTrack) {
        if (region == null) return;
        Window owner = parent == null ? IGV.getInstance().getMainFrame()
                : SwingUtilities.getWindowAncestor(parent);
        new RegionalSettingsDialog(owner, region, initiallySelectedTrack).setVisible(true);
    }

    private RegionalSettingsDialog(Window owner, RegionOfInterest region, Track initiallySelectedTrack) {
        super(owner, dialogTitle(region), ModalityType.APPLICATION_MODAL);
        this.region = region;
        this.tracks = collectTracks();
        this.originalRule = region.getDisplayRule() == null ? null : region.getDisplayRule().copy();
        this.originalBarColor = region.getBackgroundColor();
        this.workingBarColor = originalBarColor;
        this.workingRule = originalRule == null ? new RegionDisplayRule() : originalRule.copy();
        this.model = new SettingsTableModel();
        this.table = new JTable(model);
        this.rowHeader = createRowHeader();
        buildUi();
        model.addTableModelListener(event -> previewWorkingRule());
        selectInitialTrack(initiallySelectedTrack);
        updateEnabledState();
    }

    private static String dialogTitle(RegionOfInterest region) {
        String description = region.getDescription();
        return "Regional Settings — " + region.getLocusString()
                + (description == null || description.isBlank() ? "" : " — " + description.trim());
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
        list.setFixedCellWidth(rowHeaderWidth);
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
        int[] widths = {155, 105, 100, 100, 92, 92};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Object.class, new SettingsRenderer());
        table.setDefaultRenderer(Color.class, new SettingsRenderer());
        table.setDefaultRenderer(Boolean.class, new SettingsRenderer());
        table.getColumnModel().getColumn(COL_ACTION).setCellEditor(new ActionCellEditor());
        table.getColumnModel().getColumn(COL_Y_AXIS).setCellEditor(new YAxisCellEditor());
        installSelectionHandlers();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setRowHeaderView(rowHeader);
        JLabel targetHeader = new JLabel("Track", SwingConstants.CENTER);
        targetHeader.setOpaque(true);
        targetHeader.setBackground(UIManager.getColor("TableHeader.background"));
        targetHeader.setForeground(Color.BLACK);
        targetHeader.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, targetHeader);
        installRowHeaderSizing(scrollPane, targetHeader);

        JPanel tableHeader = new JPanel();
        tableHeader.setLayout(new BoxLayout(tableHeader, BoxLayout.Y_AXIS));
        JPanel barColorRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barColorRow.add(new JLabel("Region Bar Color:"));
        barColorButton = new JButton();
        updateBarColorButton();
        barColorButton.addActionListener(event -> chooseRegionBarColor());
        barColorRow.add(barColorButton);
        JLabel editingHint = new JLabel(
                "Tip for the table below: double-click to edit cells; Column Invert Coordinates supports single-click editing");
        editingHint.setForeground(Color.BLACK);
        editingHint.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        barColorRow.add(editingHint);
        tableHeader.add(barColorRow);
        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.add(tableHeader, BorderLayout.NORTH);
        tableArea.add(scrollPane, BorderLayout.CENTER);
        add(tableArea, BorderLayout.CENTER);

        JPanel tools = new JPanel();
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        JPanel firstTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addTool(firstTools, "Flip Y-Axis", this::flipSelectedYAxis, true);
        addTool(firstTools, "Flip Track Pair", this::flipSelectedTrackPairs, true);
        addTool(firstTools, "Custom Data Range", this::setSelectedCustomRange, true);
        addTool(firstTools, "Set Color", this::setSelectedColors, true);
        addTool(firstTools, "Swap Positive/Negative Color", this::swapSelectedColors, true);
        JPanel secondTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addTool(secondTools, "Copy Setting", this::copySelectedCell, true);
        addTool(secondTools, "Paste Setting", this::pasteSelectedCells, true);
        addTool(secondTools, "Copy Track Format", this::copySelectedTrackFormat, true);
        addTool(secondTools, "Apply Format to Selected Tracks", this::applyCopiedTrackFormat, true);
        JPanel thirdTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addTool(thirdTools, "Reset Selected Cells", this::resetSelectedCells, true);
        addTool(thirdTools, "Reset All", this::resetAll, false);
        tools.add(firstTools);
        tools.add(secondTools);

        JButton ok = new JButton("OK");
        ok.addActionListener(event -> accept());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JPanel confirmation = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        confirmation.add(ok);
        confirmation.add(cancel);
        JPanel thirdRow = new JPanel(new BorderLayout());
        thirdRow.add(thirdTools, BorderLayout.WEST);
        thirdRow.add(confirmation, BorderLayout.EAST);
        tools.add(thirdRow);
        add(tools, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(920, Math.min(690, 215 + model.getRowCount() * table.getRowHeight()));
        setMinimumSize(new Dimension(820, 380));
        setLocationRelativeTo(getOwner());
    }

    private void addTool(JPanel panel, String label, Runnable action, boolean disabledWhenCollapsed) {
        JButton button = new JButton(label);
        button.addActionListener(event -> {
            TableSelection selection = captureTableSelection();
            action.run();
            restoreTableSelection(selection);
            table.requestFocusInWindow();
        });
        panel.add(button);
        if (disabledWhenCollapsed) collapseDisabledButtons.add(button);
    }

    private void installRowHeaderSizing(JScrollPane scrollPane, JLabel targetHeader) {
        MouseAdapter resizeHandler = new MouseAdapter() {
            private boolean dragging;
            private int dragStartX;
            private int dragStartWidth;

            private boolean isResizeHandle(MouseEvent event) {
                return event.getX() >= targetHeader.getWidth() - 7;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                targetHeader.setCursor(Cursor.getPredefinedCursor(isResizeHandle(event)
                        ? Cursor.E_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (!dragging) targetHeader.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || !isResizeHandle(event)) return;
                dragging = true;
                dragStartX = event.getXOnScreen();
                dragStartWidth = rowHeaderWidth;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (!dragging) return;
                setRowHeaderWidth(dragStartWidth + event.getXOnScreen() - dragStartX, scrollPane);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragging = false;
                targetHeader.setCursor(Cursor.getDefaultCursor());
            }
        };
        targetHeader.addMouseListener(resizeHandler);
        targetHeader.addMouseMotionListener(resizeHandler);

        scrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                int width = scrollPane.getWidth();
                if (lastScrollPaneWidth > 0) {
                    setRowHeaderWidth(rowHeaderWidth + width - lastScrollPaneWidth, scrollPane);
                }
                lastScrollPaneWidth = width;
            }
        });
    }

    private void setRowHeaderWidth(int requestedWidth, JScrollPane scrollPane) {
        int maximum = Math.max(160, scrollPane.getWidth() - 300);
        rowHeaderWidth = Math.max(160, Math.min(requestedWidth, maximum));
        rowHeader.setFixedCellWidth(rowHeaderWidth);
        rowHeader.revalidate();
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    private TableSelection captureTableSelection() {
        return new TableSelection(table.getSelectedRows(), table.getSelectedColumns(),
                table.getSelectionModel().getAnchorSelectionIndex(),
                table.getSelectionModel().getLeadSelectionIndex(),
                table.getColumnModel().getSelectionModel().getAnchorSelectionIndex(),
                table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
    }

    private void restoreTableSelection(TableSelection selection) {
        table.clearSelection();
        for (int row : selection.rows) table.addRowSelectionInterval(row, row);
        for (int column : selection.columns) table.addColumnSelectionInterval(column, column);
        if (selection.rows.length > 0) {
            table.getSelectionModel().setAnchorSelectionIndex(selection.rowAnchor);
            table.getSelectionModel().setLeadSelectionIndex(selection.rowLead);
        }
        if (selection.columns.length > 0) {
            ListSelectionModel columns = table.getColumnModel().getSelectionModel();
            columns.setAnchorSelectionIndex(selection.columnAnchor);
            columns.setLeadSelectionIndex(selection.columnLead);
        }
    }

    private static final class TableSelection {
        private final int[] rows;
        private final int[] columns;
        private final int rowAnchor;
        private final int rowLead;
        private final int columnAnchor;
        private final int columnLead;

        private TableSelection(int[] rows, int[] columns, int rowAnchor, int rowLead,
                               int columnAnchor, int columnLead) {
            this.rows = rows;
            this.columns = columns;
            this.rowAnchor = rowAnchor;
            this.rowLead = rowLead;
            this.columnAnchor = columnAnchor;
            this.columnLead = columnLead;
        }
    }

    private void chooseRegionBarColor() {
        Color chosen = JColorChooser.showDialog(this, "Set Region Bar Color", workingBarColor);
        if (chosen == null) return;
        workingBarColor = new Color(chosen.getRed(), chosen.getGreen(), chosen.getBlue(),
                workingBarColor.getAlpha());
        region.setBackgroundColor(workingBarColor);
        updateBarColorButton();
        IGV.getInstance().repaint();
        table.requestFocusInWindow();
    }

    private void updateBarColorButton() {
        if (barColorButton == null) return;
        barColorButton.setText(String.format("#%02X%02X%02X", workingBarColor.getRed(),
                workingBarColor.getGreen(), workingBarColor.getBlue()));
        barColorButton.setOpaque(true);
        barColorButton.setBackground(workingBarColor);
        barColorButton.setForeground(contrastColor(workingBarColor));
    }

    private static Color contrastColor(Color color) {
        double luminance = .2126 * color.getRed() + .7152 * color.getGreen() + .0722 * color.getBlue();
        return luminance < 128 ? Color.WHITE : Color.BLACK;
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
        addPopupItem(popup, "Flip Y-Axis", this::flipSelectedYAxis);
        addPopupItem(popup, "Flip Track Pair", this::flipSelectedTrackPairs);
        addPopupItem(popup, "Custom Data Range...", this::setSelectedCustomRange);
        addPopupItem(popup, "Swap Positive/Negative Color", this::swapSelectedColors);
        popup.addSeparator();
        addPopupItem(popup, "Copy track format", this::copySelectedTrackFormat);
        addPopupItem(popup, "Apply copied format to selected tracks", this::applyCopiedTrackFormat);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { show(event); }
            @Override public void mouseReleased(MouseEvent event) { show(event); }
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2) return;
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) return;
                int modelColumn = table.convertColumnIndexToModel(column);
                if (!isCellAvailable(row, modelColumn)) return;
                if (isColorColumn(modelColumn)) {
                    table.changeSelection(row, column, false, false);
                    setSelectedColors();
                } else if (model.isCellEditable(row, modelColumn)) {
                    table.editCellAt(row, column, event);
                    Component editor = table.getEditorComponent();
                    if (editor != null) editor.requestFocusInWindow();
                }
            }
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
        accepted = true;
        region.setBackgroundColor(workingBarColor);
        publishRule(workingRule);
        RegionDisplayRule acceptedRule = region.getDisplayRule();
        if (RegionalSettingsEdit.differs(originalRule, originalBarColor,
                acceptedRule, workingBarColor)) {
            IGV.getInstance().getUndoManager().addEdit(new RegionalSettingsEdit(
                    region, originalRule, originalBarColor, acceptedRule, workingBarColor));
        }
        dispose();
    }

    private void previewWorkingRule() {
        if (!accepted && !restored) publishRule(workingRule);
    }

    private void publishRule(RegionDisplayRule rule) {
        region.setDisplayRule(rule == null ? null : rule.copy());
        IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
        for (ReferenceFrame frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
        IGV.getInstance().repaint();
    }

    @Override
    public void dispose() {
        if (!accepted && !restored) {
            restored = true;
            region.setBackgroundColor(originalBarColor);
            publishRule(originalRule);
        }
        super.dispose();
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
                    && copiedCellValue instanceof YAxisSetting setting) {
                setYAxisSetting(cell.y, setting);
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
        for (int row : table.getSelectedRows()) {
            if (row == 0 || !(trackForRow(row) instanceof DataTrack)) continue;
            YAxisSetting target = yAxisSetting(overrideForRow(row)) == YAxisSetting.FLIP
                    ? YAxisSetting.DEFAULT : YAxisSetting.FLIP;
            setYAxisSetting(row, target);
        }
        model.fireTableDataChanged();
    }

    private YAxisSetting yAxisSetting(TrackRegionOverride value) {
        if (value == null) return YAxisSetting.DEFAULT;
        if (value.getPairMode() == TrackRegionOverride.PairMode.SWAP) return YAxisSetting.PAIR_SWAP;
        if (value.getPairMode() == TrackRegionOverride.PairMode.FLIP) return YAxisSetting.PAIR_FLIP;
        return switch (value.getYAxisMode()) {
            case DEFAULT -> YAxisSetting.DEFAULT;
            case FLIP -> YAxisSetting.FLIP;
            case CUSTOM -> YAxisSetting.CUSTOM;
        };
    }

    private boolean setYAxisSetting(int row, YAxisSetting setting) {
        if (setting == YAxisSetting.PAIR_SWAP || setting == YAxisSetting.PAIR_FLIP) {
            return setPairModeForRow(row, setting == YAxisSetting.PAIR_FLIP
                    ? TrackRegionOverride.PairMode.FLIP : TrackRegionOverride.PairMode.SWAP, true);
        }
        clearPairModeForRowAndPartner(row);
        TrackRegionOverride value = mutableOverrideForRow(row);
        value.clearCustomRange();
        value.setPairMode(TrackRegionOverride.PairMode.NONE);
        value.setYAxisMode(setting == YAxisSetting.FLIP
                ? TrackRegionOverride.YAxisMode.FLIP : TrackRegionOverride.YAxisMode.DEFAULT);
        setOverrideForRow(row, value);
        return true;
    }

    private void clearPairModeForRowAndPartner(int row) {
        Track track = trackForRow(row);
        TrackRegionOverride current = mutableOverrideForRow(row);
        current.setPairMode(TrackRegionOverride.PairMode.NONE);
        setOverrideForRow(row, current);
        Track partner = TrackPairing.findPartner(track, tracks);
        int partnerRow = partner == null ? 0 : tracks.indexOf(partner) + 1;
        if (partnerRow > 0) {
            TrackRegionOverride partnerOverride = mutableOverrideForRow(partnerRow);
            partnerOverride.setPairMode(TrackRegionOverride.PairMode.NONE);
            setOverrideForRow(partnerRow, partnerOverride);
        }
    }

    private boolean setPairModeForRow(int row, TrackRegionOverride.PairMode mode, boolean validateRange) {
        Track track = trackForRow(row);
        Track partner = TrackPairing.findPartner(track, tracks);
        int partnerRow = partner == null ? 0 : tracks.indexOf(partner) + 1;
        if (!(track instanceof DataTrack) || !(partner instanceof DataTrack) || partnerRow <= 0) {
            showInfo("Pair Swap and Pair Flip require a paired numeric track.");
            return false;
        }
        if (mode != TrackRegionOverride.PairMode.NONE && validateRange
                && !ensurePairRangesCompatible(track, partner, mode)) return false;
        for (int targetRow : new int[]{row, partnerRow}) {
            TrackRegionOverride value = mutableOverrideForRow(targetRow);
            value.clearCustomRange();
            value.setYAxisMode(TrackRegionOverride.YAxisMode.DEFAULT);
            value.setPairMode(mode);
            setOverrideForRow(targetRow, value);
        }
        return true;
    }

    private void flipSelectedTrackPairs() {
        stopEditing();
        Set<String> processedPairs = new HashSet<>();
        boolean foundPair = false;
        boolean changed = false;
        for (int row : table.getSelectedRows()) {
            if (row == 0) continue;
            Track track = trackForRow(row);
            if (!(track instanceof DataTrack) || !TrackPairing.isPaired(track)
                    || !processedPairs.add(track.getPairId())) continue;
            Track partner = TrackPairing.findPartner(track, tracks);
            if (!(partner instanceof DataTrack) || partner.getId() == null) continue;
            foundPair = true;
            TrackRegionOverride first = mutableOverrideForRow(row);
            int partnerRow = tracks.indexOf(partner) + 1;
            if (partnerRow <= 0) continue;
            TrackRegionOverride second = mutableOverrideForRow(partnerRow);
            boolean active = first.getPairMode() == TrackRegionOverride.PairMode.FLIP
                    && second.getPairMode() == TrackRegionOverride.PairMode.FLIP;
            changed |= setPairModeForRow(row, active
                    ? TrackRegionOverride.PairMode.NONE : TrackRegionOverride.PairMode.FLIP, !active);
        }
        if (!foundPair) {
            showInfo("Select at least one paired numeric track row first.");
            return;
        }
        if (changed) model.fireTableDataChanged();
    }

    static boolean pairRangesCompatible(DataRange first, DataRange second) {
        if (first == null || second == null || first.isLog() != second.isLog()) return false;
        boolean same = pairRangesExactlyEqual(first, second);
        boolean signReversed = close(first.getMinimum(), -second.getMaximum())
                && close(first.getBaseline(), -second.getBaseline())
                && close(first.getMaximum(), -second.getMinimum());
        boolean axisReversed = close(first.getMinimum(), second.getMaximum())
                && close(first.getBaseline(), second.getBaseline())
                && close(first.getMaximum(), second.getMinimum());
        return same || signReversed || axisReversed;
    }

    static boolean pairRangesExactlyEqual(DataRange first, DataRange second) {
        return first != null && second != null && first.isLog() == second.isLog()
                && close(first.getMinimum(), second.getMinimum())
                && close(first.getBaseline(), second.getBaseline())
                && close(first.getMaximum(), second.getMaximum());
    }

    private static boolean close(float first, float second) {
        float scale = Math.max(1f, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= scale * 1e-5f;
    }

    private boolean ensurePairRangesCompatible(Track first, Track second,
                                               TrackRegionOverride.PairMode mode) {
        while (!(mode == TrackRegionOverride.PairMode.SWAP
                ? pairRangesExactlyEqual(first.getDataRange(), second.getDataRange())
                : pairRangesCompatible(first.getDataRange(), second.getDataRange()))) {
            String operation = mode == TrackRegionOverride.PairMode.SWAP ? "Pair Swap" : "Pair Flip";
            String message = "The paired tracks have different Y-axis data ranges:\n\n"
                    + first.getName() + ": " + formatRange(first.getDataRange()) + "\n"
                    + second.getName() + ": " + formatRange(second.getDataRange()) + "\n\n"
                    + "Set the paired data ranges before applying " + operation + "?";
            int result = JOptionPane.showConfirmDialog(this, message, "Pair Data Ranges Differ",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION || !showPairedDataRangeDialog(first, second)) return false;
        }
        return true;
    }

    private static String formatRange(DataRange range) {
        if (range == null) return "None";
        return range.getMinimum() + " / " + range.getBaseline() + " / " + range.getMaximum()
                + (range.isLog() ? " (Log)" : "");
    }

    private boolean showPairedDataRangeDialog(Track first, Track second) {
        List<Track> pair = List.of(first, second);
        TrackPairing.Partition partition = TrackPairing.partitionTopBottom(pair);
        DataRange topDefaults = DataRange.getFromTracks(partition.top);
        DataRange bottomDefaults = DataRange.getFromTracks(partition.bottom);
        PairedDataRangeDialog dialog = new PairedDataRangeDialog(
                IGV.getInstance().getMainFrame(), topDefaults, bottomDefaults);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        if (dialog.isCanceled()) return false;
        DataRange topRange = dialog.getTopDataRange(topDefaults.isDrawBaseline());
        DataRange bottomRange = dialog.getBottomDataRange(bottomDefaults.isDrawBaseline());
        for (Track track : partition.top) applyDataRange(track, topRange);
        for (Track track : partition.bottom) applyDataRange(track, bottomRange);
        IGV.getInstance().repaint(pair);
        return true;
    }

    private static void applyDataRange(Track track, DataRange range) {
        track.setDataRange(range.copy());
        track.setAutoScale(false);
        track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
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
                    clearPairModeForRowAndPartner(row);
                    TrackRegionOverride value = mutableOverrideForRow(row);
                    value.setCustomRange(min, mid, max, log.isSelected());
                    value.setPairMode(TrackRegionOverride.PairMode.NONE);
                    setOverrideForRow(row, value);
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

    private void swapSelectedColors() {
        stopEditing();
        for (int row : table.getSelectedRows()) {
            if (row == 0 || !(trackForRow(row) instanceof DataTrack)) continue;
            TrackRegionOverride value = mutableOverrideForRow(row);
            Track track = trackForRow(row);
            Color positive = value.getPositiveColor() == null ? track.getColor() : value.getPositiveColor();
            Color negative = value.getNegativeColor() == null ? track.getAltColor() : value.getNegativeColor();
            value.setPositiveColor(negative);
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
            TrackRegionOverride copy = copiedTrackFormat.copy();
            TrackRegionOverride.PairMode pairMode = copy.getPairMode();
            copy.setPairMode(TrackRegionOverride.PairMode.NONE);
            setOverrideForRow(row, copy);
            if (pairMode != TrackRegionOverride.PairMode.NONE) {
                setPairModeForRow(row, pairMode, true);
            }
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
                setYAxisSetting(row, YAxisSetting.DEFAULT);
                return;
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
        private final String[] names = {"Invert Coordinates", "Y Axis",
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
            return (column == COL_ACTION || column == COL_Y_AXIS) && isCellAvailable(row, column);
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
                case COL_Y_AXIS -> yAxisSetting(value);
                case COL_BACKGROUND -> value == null ? null : value.getBackgroundColor();
                case COL_FOREGROUND -> value == null ? null : value.getForegroundMaskColor();
                case COL_POSITIVE -> value == null ? null : value.getPositiveColor();
                case COL_NEGATIVE -> value == null ? null : value.getNegativeColor();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (!isCellAvailable(row, column)) return;
            if (column == COL_ACTION && row == 0 && value instanceof RegionAction action) {
                setRegionAction(action);
            } else if (column == COL_ACTION && row > 0 && value instanceof Boolean inverted) {
                TrackRegionOverride override = mutableOverrideForRow(row);
                override.setReverseX(inverted);
                setOverrideForRow(row, override);
                fireTableRowsUpdated(row, row);
            } else if (column == COL_Y_AXIS && row > 0
                    && value instanceof YAxisSetting setting) {
                if (setting == YAxisSetting.CUSTOM) {
                    SwingUtilities.invokeLater(() -> {
                        table.setRowSelectionInterval(row, row);
                        setSelectedCustomRange();
                    });
                } else {
                    setYAxisSetting(row, setting);
                    fireTableRowsUpdated(row, row);
                }
            }
        }
    }

    private final class SettingsRenderer extends DefaultTableCellRenderer {
        private final JCheckBox checkBox = new JCheckBox();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            int modelColumn = table.convertColumnIndexToModel(column);
            boolean available = isCellAvailable(row, modelColumn);
            if (value instanceof Boolean inverted && available) {
                checkBox.setSelected(inverted);
                checkBox.setHorizontalAlignment(CENTER);
                checkBox.setOpaque(true);
                checkBox.setEnabled(true);
                checkBox.setBackground(table.getBackground());
                checkBox.setBorder(selected ? BorderFactory.createLineBorder(Color.BLACK)
                        : BorderFactory.createEmptyBorder(1, 1, 1, 1));
                checkBox.setBorderPainted(true);
                return checkBox;
            }
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            setBackground(table.getBackground());
            setForeground(available ? Color.BLACK : UIManager.getColor("Label.disabledForeground"));
            setBorder(selected ? BorderFactory.createLineBorder(Color.BLACK)
                    : BorderFactory.createEmptyBorder(1, 1, 1, 1));
            if (!available) {
                setText("—");
            } else if (value instanceof Color color) {
                setBackground(color);
                setForeground(contrast(color));
                setText(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
            } else if (value == null) {
                setText("Default");
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
        private final JCheckBox trackInvert = new JCheckBox();
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
        public boolean isCellEditable(EventObject event) {
            return !(event instanceof MouseEvent mouseEvent) || mouseEvent.getClickCount() >= 1;
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

    private final class YAxisCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JComboBox<YAxisSetting> modes = new JComboBox<>();
        private boolean configuring;

        private YAxisCellEditor() {
            modes.addActionListener(event -> {
                if (!configuring) fireEditingStopped();
            });
        }

        @Override
        public boolean isCellEditable(EventObject event) {
            return !(event instanceof MouseEvent mouseEvent) || mouseEvent.getClickCount() >= 2;
        }

        @Override
        public Object getCellEditorValue() {
            return modes.getSelectedItem();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean selected,
                                                     int row, int column) {
            configuring = true;
            YAxisSetting[] choices = TrackPairing.isPaired(trackForRow(row))
                    ? YAxisSetting.values()
                    : new YAxisSetting[]{YAxisSetting.DEFAULT, YAxisSetting.FLIP, YAxisSetting.CUSTOM};
            modes.setModel(new DefaultComboBoxModel<>(choices));
            modes.setSelectedItem(value);
            configuring = false;
            return modes;
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
