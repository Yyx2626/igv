package org.igv.ui;

import javax.swing.*;
import java.awt.*;

/** Sets the current IGV window size without introducing a second startup-size preference. */
public final class WindowSizeDialog {

    private WindowSizeDialog() {
    }

    public static Dimension show(Frame owner) {
        JSpinner width = new JSpinner(new SpinnerNumberModel(owner.getWidth(), 300, 10000, 1));
        JSpinner height = new JSpinner(new SpinnerNumberModel(owner.getHeight(), 300, 10000, 1));

        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 6));
        panel.add(new JLabel("Width (pixels)"));
        panel.add(width);
        panel.add(new JLabel("Height (pixels)"));
        panel.add(height);

        int result = JOptionPane.showConfirmDialog(owner, panel, "Set IGV Window Size",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        return new Dimension((Integer) width.getValue(), (Integer) height.getValue());
    }
}
