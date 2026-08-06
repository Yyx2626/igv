package org.igv.ui;

import org.igv.track.WindowFunction;

import javax.swing.*;
import java.awt.*;

/**
 * Shown by {@code AverageErrorBarMenuAction} when the selected tracks don't all share
 * the same {@link WindowFunction}: forces the user to pick one aggregation function
 * (Min / Mean / Max) to use uniformly when computing the Average-With-Error-Bar track.
 */
public class WindowFunctionChooserDialog extends IGVDialog {

    private boolean canceled;
    private WindowFunction selected = WindowFunction.mean;

    public WindowFunctionChooserDialog(Frame parent) {
        super(parent, true);
        setTitle("Choose Aggregation Function");
        setLocationRelativeTo(parent);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("<html>Selected tracks don't all use the same Windowing<br>"
                + "Function. Choose one to use for this average:</html>");
        content.add(label);
        content.add(Box.createVerticalStrut(8));

        ButtonGroup group = new ButtonGroup();
        JRadioButton minButton = new JRadioButton("Min");
        JRadioButton meanButton = new JRadioButton("Mean", true);
        JRadioButton maxButton = new JRadioButton("Max");
        group.add(minButton);
        group.add(meanButton);
        group.add(maxButton);
        minButton.addActionListener(e -> selected = WindowFunction.min);
        meanButton.addActionListener(e -> selected = WindowFunction.mean);
        maxButton.addActionListener(e -> selected = WindowFunction.max);
        content.add(minButton);
        content.add(meanButton);
        content.add(maxButton);
        content.add(Box.createVerticalStrut(8));

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> setVisible(false));
        cancelButton.addActionListener(e -> {
            canceled = true;
            setVisible(false);
        });
        getRootPane().setDefaultButton(okButton);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        content.add(buttonPanel);

        getContentPane().add(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
    }

    public boolean isCanceled() {
        return canceled;
    }

    public WindowFunction getSelected() {
        return selected;
    }
}
