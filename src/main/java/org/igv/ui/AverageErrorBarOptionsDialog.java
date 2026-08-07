package org.igv.ui;

import org.igv.track.ErrorBarType;
import org.igv.track.WindowFunction;

import javax.swing.*;
import java.awt.*;

/**
 * Shown when the user chooses "Average With Error Bar..." from the track context menu.
 * A 3-row, 2-column table: label column (left-aligned) then control column
 * (left-aligned), in the order the user reasons about them - what value to substitute
 * for a missing member ("NA", i.e. a member with no data covering a given bin - defaults
 * to 0, so a gap in one bigwig counts as an observed zero rather than shrinking the
 * effective sample size for that bin), which Windowing Function to apply to every member
 * before averaging (defaults to the members' own shared setting if they agree, else
 * Mean; "Absolute Max" - Max where the member's bin value is positive, Min where it's
 * negative - is offered because it's what a member's own "None" windowing looks like
 * once bigwig zoom-pyramid summaries kick in at low zoom, so it's the natural match for
 * members left on "None"), and which error-bar statistic to draw (SEM / SD / None).
 */
public class AverageErrorBarOptionsDialog extends IGVDialog {

    private boolean canceled;
    private float naValue = 0f;
    private WindowFunction windowFunction;
    private ErrorBarType errorBarType = ErrorBarType.SEM;
    private final JTextField naField;

    public AverageErrorBarOptionsDialog(Frame parent, WindowFunction defaultWindowFunction) {
        super(parent, true);
        this.windowFunction = defaultWindowFunction;
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
        JRadioButton absMaxButton = new JRadioButton("Absolute Max", defaultWindowFunction == WindowFunction.absoluteMax);
        wfGroup.add(minButton);
        wfGroup.add(meanButton);
        wfGroup.add(maxButton);
        wfGroup.add(absMaxButton);
        minButton.addActionListener(e -> windowFunction = WindowFunction.min);
        meanButton.addActionListener(e -> windowFunction = WindowFunction.mean);
        maxButton.addActionListener(e -> windowFunction = WindowFunction.max);
        absMaxButton.addActionListener(e -> windowFunction = WindowFunction.absoluteMax);
        wfPanel.add(minButton);
        wfPanel.add(meanButton);
        wfPanel.add(maxButton);
        wfPanel.add(absMaxButton);
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
        buttonC.gridy = 3;
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
}
