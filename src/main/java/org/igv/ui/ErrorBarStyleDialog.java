package org.igv.ui;

import org.igv.renderer.ErrorBarStyle;

import javax.swing.*;
import java.awt.*;

/**
 * "Error Bar Style..." dialog for an {@code AverageErrorBarTrack}: choose Bar vs. thin
 * Line, and the shape-specific width/cap settings.
 */
public class ErrorBarStyleDialog extends IGVDialog {

    private boolean canceled;
    private final ErrorBarStyle style;

    private final JRadioButton barButton = new JRadioButton("Bar");
    private final JRadioButton lineButton = new JRadioButton("Thin line");
    private final JSpinner barWidthSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 100, 5));
    private final JSpinner lineWidthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JRadioButton singleCapButton = new JRadioButton("T-shape (single cap)");
    private final JRadioButton doubleCapButton = new JRadioButton("I-beam (double cap)");

    public ErrorBarStyleDialog(Frame parent, ErrorBarStyle style) {
        super(parent, true);
        this.style = style;
        setTitle("Error Bar Style");
        setLocationRelativeTo(parent);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ButtonGroup shapeGroup = new ButtonGroup();
        shapeGroup.add(barButton);
        shapeGroup.add(lineButton);

        JPanel barWidthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barWidthPanel.add(barButton);
        barWidthPanel.add(new JLabel("Width (% of bar):"));
        barWidthPanel.add(barWidthSpinner);
        content.add(barWidthPanel);

        JPanel linePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linePanel.add(lineButton);
        linePanel.add(new JLabel("Line width (px):"));
        linePanel.add(lineWidthSpinner);
        content.add(linePanel);

        ButtonGroup capGroup = new ButtonGroup();
        capGroup.add(singleCapButton);
        capGroup.add(doubleCapButton);
        JPanel capPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capPanel.add(new JLabel("Cap style:"));
        capPanel.add(singleCapButton);
        capPanel.add(doubleCapButton);
        content.add(capPanel);

        if (style.getShape() == ErrorBarStyle.Shape.BAR) {
            barButton.setSelected(true);
        } else {
            lineButton.setSelected(true);
        }
        barWidthSpinner.setValue(style.getBarWidthPercent());
        lineWidthSpinner.setValue(style.getLineWidthPx());
        if (style.getCapStyle() == ErrorBarStyle.CapStyle.DOUBLE) {
            doubleCapButton.setSelected(true);
        } else {
            singleCapButton.setSelected(true);
        }

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> {
            style.setShape(barButton.isSelected() ? ErrorBarStyle.Shape.BAR : ErrorBarStyle.Shape.LINE);
            style.setBarWidthPercent((Integer) barWidthSpinner.getValue());
            style.setLineWidthPx((Integer) lineWidthSpinner.getValue());
            style.setCapStyle(doubleCapButton.isSelected() ? ErrorBarStyle.CapStyle.DOUBLE : ErrorBarStyle.CapStyle.SINGLE);
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
        content.add(buttonPanel);

        getContentPane().add(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
    }

    public boolean isCanceled() {
        return canceled;
    }
}
