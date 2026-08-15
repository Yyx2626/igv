package org.igv.ui;

import org.igv.renderer.ScatterPointStyle;
import org.igv.track.ErrorBarType;
import org.igv.track.WindowFunction;
import org.igv.ui.action.AverageErrorBarMenuAction;

import javax.swing.*;
import java.awt.*;

/**
 * Shown when the user chooses "Average With Error Bar..." from the track context menu.
 * A two-column table: label column (left-aligned) then control column
 * (left-aligned), in the order the user reasons about them - what value to substitute
 * for a missing member ("NA", i.e. a member with no data covering a given bin - defaults
 * to 0, so a gap in one bigwig counts as an observed zero rather than shrinking the
 * effective sample size for that bin), which Windowing Function to apply to every member
 * before averaging (defaults to the members' own shared setting if they agree, else
 * Mean; "None" reports each member's actual max where it's positive and min where it's
 * negative - see {@code AverageErrorBarDataSource}'s class javadoc), and which error-bar
 * statistic to draw (SEM / SD / None), the minimum N required to draw it, and
 * whether to overlay the contributing member values as scatter points.
 */
public class AverageErrorBarOptionsDialog extends IGVDialog {

    private boolean canceled;
    private float naValue = 0f;
    private WindowFunction windowFunction;
    private ErrorBarType errorBarType = ErrorBarType.SEM;
    private int minimumErrorBarN = 2;
    private boolean scatterPointsEnabled;
    private final ScatterPointStyle scatterPointStyle;
    private final Color defaultScatterPositive;
    private final Color defaultScatterNegative;
    private final int scatterRepeatCount;
    private final JTextField naField;

    public AverageErrorBarOptionsDialog(Frame parent, WindowFunction defaultWindowFunction) {
        this(parent, defaultWindowFunction, null, null, new ScatterPointStyle(), 1);
    }

    public AverageErrorBarOptionsDialog(Frame parent, WindowFunction defaultWindowFunction,
                                        Color defaultScatterPositive,
                                        Color defaultScatterNegative) {
        this(parent, defaultWindowFunction, defaultScatterPositive,
                defaultScatterNegative, new ScatterPointStyle(), 1);
    }

    public AverageErrorBarOptionsDialog(Frame parent, WindowFunction defaultWindowFunction,
                                        Color defaultScatterPositive,
                                        Color defaultScatterNegative,
                                        ScatterPointStyle defaultScatterPointStyle,
                                        int scatterRepeatCount) {
        super(parent, true);
        this.windowFunction = defaultWindowFunction;
        this.defaultScatterPositive = defaultScatterPositive;
        this.defaultScatterNegative = defaultScatterNegative;
        this.scatterPointStyle = defaultScatterPointStyle == null
                ? new ScatterPointStyle() : defaultScatterPointStyle.copy();
        this.scatterRepeatCount = Math.max(1, scatterRepeatCount);
        setTitle("Average With Error Bar");
        setLocationRelativeTo(parent);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints labelC = new GridBagConstraints();
        labelC.gridx = 0;
        labelC.anchor = GridBagConstraints.WEST;
        labelC.insets = new Insets(6, 0, 6, 10);
        GridBagConstraints controlC = new GridBagConstraints();
        controlC.gridx = 1;
        controlC.anchor = GridBagConstraints.WEST;
        controlC.insets = new Insets(6, 0, 6, 0);

        naField = new JTextField("0", 5);
        naField.setMaximumSize(naField.getPreferredSize());
        addRow(content, labelC, controlC, 0, "Treat missing values (NA) as:", naField);

        JPanel wfPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ButtonGroup wfGroup = new ButtonGroup();
        JRadioButton minButton = new JRadioButton("Min", defaultWindowFunction == WindowFunction.min);
        JRadioButton meanButton = new JRadioButton("Mean", defaultWindowFunction == WindowFunction.mean);
        JRadioButton maxButton = new JRadioButton("Max", defaultWindowFunction == WindowFunction.max);
        JRadioButton wfNoneButton = new JRadioButton("None", defaultWindowFunction == WindowFunction.none);
        wfGroup.add(minButton);
        wfGroup.add(meanButton);
        wfGroup.add(maxButton);
        wfGroup.add(wfNoneButton);
        minButton.addActionListener(e -> windowFunction = WindowFunction.min);
        meanButton.addActionListener(e -> windowFunction = WindowFunction.mean);
        maxButton.addActionListener(e -> windowFunction = WindowFunction.max);
        wfNoneButton.addActionListener(e -> windowFunction = WindowFunction.none);
        wfPanel.add(minButton);
        wfPanel.add(meanButton);
        wfPanel.add(maxButton);
        wfPanel.add(wfNoneButton);
        addRow(content, labelC, controlC, 1, "Windowing Function:", wfPanel);

        JPanel ebPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ButtonGroup ebGroup = new ButtonGroup();
        JRadioButton semButton = new JRadioButton("SEM", true);
        JRadioButton sdButton = new JRadioButton("SD");
        JRadioButton noneButton = new JRadioButton("None");
        ebGroup.add(semButton);
        ebGroup.add(sdButton);
        ebGroup.add(noneButton);
        semButton.addActionListener(e -> errorBarType = ErrorBarType.SEM);
        sdButton.addActionListener(e -> errorBarType = ErrorBarType.SD);
        noneButton.addActionListener(e -> errorBarType = ErrorBarType.NONE);
        ebPanel.add(semButton);
        ebPanel.add(sdButton);
        ebPanel.add(noneButton);
        addRow(content, labelC, controlC, 2, "Error bar:", ebPanel);

        JSpinner minimumNSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 999, 1));
        addRow(content, labelC, controlC, 3, "Plot SD/SEM only when N ≥", minimumNSpinner);

        JButton scatterSettingsButton = new JButton("Settings...");
        scatterSettingsButton.addActionListener(e -> {
            scatterPointStyle.initializeDefaultsForFirstSettingsOpen(
                    AverageErrorBarMenuAction.estimateCurrentBinWidthPixels(),
                    scatterRepeatCount);
            ScatterPointStyleDialog dialog = new ScatterPointStyleDialog(
                    parent, scatterPointsEnabled, scatterPointStyle,
                    this.defaultScatterPositive, this.defaultScatterNegative);
            dialog.setVisible(true);
            if (!dialog.isCanceled()) {
                scatterPointsEnabled = dialog.isScatterPointsEnabled();
            }
        });
        addRow(content, labelC, controlC, 4, "Scatter points:", scatterSettingsButton);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> {
            try {
                naValue = Float.parseFloat(naField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "\"" + naField.getText() + "\" is not a valid number.",
                        "Invalid value", JOptionPane.ERROR_MESSAGE);
                return;
            }
            minimumErrorBarN = (Integer) minimumNSpinner.getValue();
            setVisible(false);
        });
        cancelButton.addActionListener(e -> {
            canceled = true;
            setVisible(false);
        });
        getRootPane().setDefaultButton(okButton);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        GridBagConstraints buttonC = new GridBagConstraints();
        buttonC.gridx = 0;
        buttonC.gridy = 5;
        buttonC.gridwidth = 2;
        buttonC.fill = GridBagConstraints.HORIZONTAL;
        buttonC.insets = new Insets(10, 0, 0, 0);
        content.add(buttonPanel, buttonC);

        getContentPane().add(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
    }

    private static void addRow(JPanel content, GridBagConstraints labelC, GridBagConstraints controlC,
                                int row, String labelText, Component control) {
        labelC.gridy = row;
        content.add(new JLabel(labelText), labelC);
        controlC.gridy = row;
        content.add(control, controlC);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public float getNaValue() {
        return naValue;
    }

    public WindowFunction getWindowFunction() {
        return windowFunction;
    }

    public ErrorBarType getErrorBarType() {
        return errorBarType;
    }

    public int getMinimumErrorBarN() {
        return minimumErrorBarN;
    }

    public boolean isScatterPointsEnabled() {
        return scatterPointsEnabled;
    }

    public ScatterPointStyle getScatterPointStyle() {
        return scatterPointStyle.copy();
    }
}
