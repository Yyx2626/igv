package org.igv.ui;

import org.igv.renderer.ScatterPointStyle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.BiConsumer;

/** Configures the member-value scatter points drawn on an Average track. */
public class ScatterPointStyleDialog extends IGVDialog {

    private static final int COL_POSITIVE = 0;
    private static final int COL_NEGATIVE = 1;
    private static final int COL_INNER = 2;

    private boolean canceled;
    private boolean scatterPointsEnabled;
    private final ScatterPointStyle target;
    private final ScatterPointStyle working;
    private final Color defaultPositive;
    private final Color defaultNegative;
    private final JTable colorTable;
    private final BiConsumer<Boolean, ScatterPointStyle> previewCallback;
    private final Runnable cancelPreviewCallback;

    public ScatterPointStyleDialog(Frame parent, boolean scatterPointsEnabled,
                                   ScatterPointStyle style, Color defaultPositive,
                                   Color defaultNegative) {
        this(parent, scatterPointsEnabled, style, defaultPositive, defaultNegative,
                null, null);
    }

    public ScatterPointStyleDialog(Frame parent, boolean scatterPointsEnabled,
                                   ScatterPointStyle style, Color defaultPositive,
                                   Color defaultNegative,
                                   BiConsumer<Boolean, ScatterPointStyle> previewCallback,
                                   Runnable cancelPreviewCallback) {
        super(parent, true);
        this.scatterPointsEnabled = scatterPointsEnabled;
        this.target = style;
        this.working = style.copy();
        this.defaultPositive = defaultPositive == null ? Color.BLUE.darker() : defaultPositive;
        this.defaultNegative = defaultNegative == null ? this.defaultPositive : defaultNegative;
        this.colorTable = new JTable(new ColorTableModel());
        this.previewCallback = previewCallback;
        this.cancelPreviewCallback = cancelPreviewCallback;

        setTitle("Scatter Points Settings");

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JCheckBox enabledCheckBox = new JCheckBox("", scatterPointsEnabled);
        JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(
                working.getWidthPercent(), 1, 100, 5));
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(
                working.getPointSizePx(), 0.1, 100.0, 0.1));
        JSpinner borderWidthSpinner = new JSpinner(new SpinnerNumberModel(
                working.getBorderLineWidthPx(), 0.0, 10.0, 0.05));
        JComboBox<ScatterPointStyle.Shape> shapeCombo =
                new JComboBox<>(ScatterPointStyle.Shape.values());
        shapeCombo.setSelectedItem(working.getShape());

        enabledCheckBox.addActionListener(e -> {
            this.scatterPointsEnabled = enabledCheckBox.isSelected();
            publishPreview();
        });
        widthSpinner.addChangeListener(e -> {
            working.setWidthPercent((Integer) widthSpinner.getValue());
            publishPreview();
        });
        shapeCombo.addActionListener(e -> {
            working.setShape((ScatterPointStyle.Shape) shapeCombo.getSelectedItem());
            publishPreview();
        });
        sizeSpinner.addChangeListener(e -> {
            working.setPointSizePx(((Number) sizeSpinner.getValue()).doubleValue());
            publishPreview();
        });
        borderWidthSpinner.addChangeListener(e -> {
            working.setBorderLineWidthPx(
                    ((Number) borderWidthSpinner.getValue()).doubleValue());
            publishPreview();
        });

        int row = 0;
        addRow(content, row++, "Add scatter points:", enabledCheckBox);
        addRow(content, row++, "Scatter width (% of bar):", widthSpinner);
        addRow(content, row++, "Point shape:", shapeCombo);
        addRow(content, row++, "Point size (px):", sizeSpinner);
        addRow(content, row++, "Point border thickness (px):", borderWidthSpinner);

        GridBagConstraints colorLabelConstraints = new GridBagConstraints();
        colorLabelConstraints.gridx = 0;
        colorLabelConstraints.gridy = row++;
        colorLabelConstraints.gridwidth = 2;
        colorLabelConstraints.anchor = GridBagConstraints.WEST;
        colorLabelConstraints.insets = new Insets(12, 0, 4, 0);
        content.add(new JLabel("Point color settings:"), colorLabelConstraints);

        configureColorTable();
        JScrollPane colorScrollPane = new JScrollPane(colorTable);
        colorScrollPane.setPreferredSize(new Dimension(390,
                colorTable.getRowHeight() + colorTable.getTableHeader().getPreferredSize().height + 4));
        GridBagConstraints tableConstraints = new GridBagConstraints();
        tableConstraints.gridx = 0;
        tableConstraints.gridy = row++;
        tableConstraints.gridwidth = 2;
        tableConstraints.fill = GridBagConstraints.HORIZONTAL;
        tableConstraints.insets = new Insets(0, 0, 0, 0);
        content.add(colorScrollPane, tableConstraints);

        JButton resetSelected = new JButton("Reset Selected Cell");
        resetSelected.addActionListener(e -> resetSelectedCell());
        JPanel colorTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        colorTools.add(resetSelected);
        GridBagConstraints toolsConstraints = new GridBagConstraints();
        toolsConstraints.gridx = 0;
        toolsConstraints.gridy = row++;
        toolsConstraints.gridwidth = 2;
        toolsConstraints.anchor = GridBagConstraints.WEST;
        toolsConstraints.insets = new Insets(6, 0, 0, 0);
        content.add(colorTools, toolsConstraints);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> {
            this.scatterPointsEnabled = enabledCheckBox.isSelected();
            int selectedWidthPercent = (Integer) widthSpinner.getValue();
            double selectedPointSizePx = ((Number) sizeSpinner.getValue()).doubleValue();
            double selectedBorderLineWidthPx =
                    ((Number) borderWidthSpinner.getValue()).doubleValue();
            working.setWidthPercent(selectedWidthPercent);
            working.setShape((ScatterPointStyle.Shape) shapeCombo.getSelectedItem());
            working.setPointSizePx(selectedPointSizePx);
            working.setBorderLineWidthPx(selectedBorderLineWidthPx);
            target.copyFrom(working);
            setVisible(false);
        });
        cancelButton.addActionListener(e -> cancelAndClose());
        getRootPane().setDefaultButton(okButton);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        GridBagConstraints buttons = new GridBagConstraints();
        buttons.gridx = 0;
        buttons.gridy = row;
        buttons.gridwidth = 2;
        buttons.fill = GridBagConstraints.HORIZONTAL;
        buttons.insets = new Insets(10, 0, 0, 0);
        content.add(buttonPanel, buttons);

        getContentPane().add(content);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cancelAndClose();
            }
        });
        pack();
        setLocationRelativeTo(parent);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public boolean isScatterPointsEnabled() {
        return scatterPointsEnabled;
    }

    private void publishPreview() {
        if (previewCallback != null) {
            previewCallback.accept(scatterPointsEnabled, working.copy());
        }
    }

    private void cancelAndClose() {
        canceled = true;
        if (cancelPreviewCallback != null) {
            cancelPreviewCallback.run();
        }
        setVisible(false);
    }

    private void configureColorTable() {
        colorTable.setCellSelectionEnabled(true);
        colorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        colorTable.setRowHeight(Math.max(28, colorTable.getRowHeight()));
        colorTable.setDefaultRenderer(Color.class, new ColorCellRenderer());
        colorTable.setSelectionForeground(Color.BLACK);
        colorTable.setSelectionBackground(new Color(190, 215, 245));
        colorTable.getTableHeader().setReorderingAllowed(false);
        colorTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2) return;
                int row = colorTable.rowAtPoint(event.getPoint());
                int column = colorTable.columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) return;
                colorTable.changeSelection(row, column, false, false);
                chooseSelectedColor();
            }
        });
    }

    private void chooseSelectedColor() {
        int column = colorTable.getSelectedColumn();
        if (column < 0) return;
        Color current = colorAt(column);
        Color chosen = JColorChooser.showDialog(this, "Set Scatter Point Color",
                current == null ? defaultColorAt(column) : current);
        if (chosen == null) return;
        setColorAt(column, chosen);
        ((AbstractTableModel) colorTable.getModel()).fireTableCellUpdated(0, column);
        publishPreview();
    }

    private void resetSelectedCell() {
        int column = colorTable.getSelectedColumn();
        if (column < 0) return;
        setColorAt(column, null);
        ((AbstractTableModel) colorTable.getModel()).fireTableCellUpdated(0, column);
        publishPreview();
    }

    private Color colorAt(int column) {
        return switch (column) {
            case COL_POSITIVE -> working.getPositiveColorOverride();
            case COL_NEGATIVE -> working.getNegativeColorOverride();
            case COL_INNER -> working.getInnerColorOverride();
            default -> null;
        };
    }

    private Color defaultColorAt(int column) {
        return switch (column) {
            case COL_POSITIVE -> defaultPositive;
            case COL_NEGATIVE -> defaultNegative;
            case COL_INNER -> ScatterPointStyle.DEFAULT_INNER_COLOR;
            default -> Color.GRAY;
        };
    }

    private void setColorAt(int column, Color color) {
        switch (column) {
            case COL_POSITIVE -> working.setPositiveColorOverride(color);
            case COL_NEGATIVE -> working.setNegativeColorOverride(color);
            case COL_INNER -> working.setInnerColorOverride(color);
            default -> { }
        }
    }

    private static void addRow(JPanel content, int row, String label, Component control) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(6, 0, 6, 10);
        content.add(new JLabel(label), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.anchor = GridBagConstraints.WEST;
        right.insets = new Insets(6, 0, 6, 0);
        content.add(control, right);
    }

    private final class ColorTableModel extends AbstractTableModel {
        private final String[] names = {"Positive", "Negative", "Inner"};

        @Override public int getRowCount() { return 1; }
        @Override public int getColumnCount() { return names.length; }
        @Override public String getColumnName(int column) { return names[column]; }
        @Override public Class<?> getColumnClass(int column) { return Color.class; }
        @Override public Object getValueAt(int row, int column) { return colorAt(column); }
    }

    private final class ColorCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            setBorder(selected ? BorderFactory.createLineBorder(Color.BLACK)
                    : BorderFactory.createEmptyBorder(1, 1, 1, 1));
            if (value instanceof Color color) {
                setToolTipText(null);
                setBackground(color);
                setForeground(contrast(color));
                int opacity = Math.round(color.getAlpha() * 100f / 255f);
                setText(String.format("#%02X%02X%02X%s", color.getRed(), color.getGreen(),
                        color.getBlue(), opacity < 100 ? " (" + opacity + "%)" : ""));
            } else {
                setBackground(table.getBackground());
                setForeground(Color.BLACK);
                setText("Default");
                setToolTipText(column == COL_INNER
                        ? "Opaque white" : "Follow the track color");
            }
            return this;
        }

        private Color contrast(Color color) {
            double luminance = .2126 * color.getRed() + .7152 * color.getGreen()
                    + .0722 * color.getBlue();
            return luminance < 128 ? Color.WHITE : Color.BLACK;
        }
    }
}
