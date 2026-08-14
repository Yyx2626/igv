package org.igv.ui;

import org.igv.renderer.DataRange;

import javax.swing.*;
import java.awt.*;

/**
 * Data-Range dialog variant used when the current selection includes paired tracks
 * (see {@link org.igv.track.TrackPairing}). Shows two independent groups of
 * Min/Mid/Max/Log-scale controls - one for the "top" group (top-of-pair tracks plus any
 * unpaired tracks in the selection) and one for the "bottom" group (bottom-of-pair
 * tracks) - instead of a single shared range applied to every selected track.
 */
public class PairedDataRangeDialog extends IGVDialog {

    private boolean canceled;

    private final RangeFieldsPanel topPanel;
    private final RangeFieldsPanel bottomPanel;

    public PairedDataRangeDialog(Frame parent, DataRange topDefaults, DataRange bottomDefaults) {
        super(parent, true);
        setTitle("Data Range");
        setLocationRelativeTo(parent);

        topPanel = new RangeFieldsPanel("Top track(s) / unpaired", topDefaults);
        bottomPanel = new RangeFieldsPanel("Bottom track(s)", bottomDefaults);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(topPanel);

        JButton copyDownButton = new JButton("\u2193");
        JButton copyUpButton = new JButton("\u2191");
        // Keep focus in the numeric field. On macOS, transferring focus through the
        // input-method bridge can visibly delay an otherwise constant-time operation.
        copyDownButton.setFocusable(false);
        copyDownButton.setRequestFocusEnabled(false);
        copyUpButton.setFocusable(false);
        copyUpButton.setRequestFocusEnabled(false);
        copyDownButton.setToolTipText("Flip the Top scale and apply it to Bottom");
        copyUpButton.setToolTipText("Flip the Bottom scale and apply it to Top");
        copyDownButton.addActionListener(e -> transferScale(topPanel, bottomPanel));
        copyUpButton.addActionListener(e -> transferScale(bottomPanel, topPanel));
        boolean transferEnabled = topDefaults != null && bottomDefaults != null;
        copyDownButton.setEnabled(transferEnabled);
        copyUpButton.setEnabled(transferEnabled);
        JPanel transferPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        JLabel transferLabel = new JLabel("Auto apply:");
        transferLabel.setEnabled(transferEnabled);
        transferPanel.add(transferLabel);
        transferPanel.add(copyDownButton);
        transferPanel.add(copyUpButton);
        content.add(Box.createVerticalStrut(4));
        content.add(transferPanel);
        content.add(Box.createVerticalStrut(4));
        content.add(bottomPanel);
        content.add(Box.createVerticalStrut(10));

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> {
            if ((!topPanel.isEnabled() || topPanel.convertParms())
                    && (!bottomPanel.isEnabled() || bottomPanel.convertParms())) {
                setVisible(false);
            }
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

    public DataRange getTopDataRange(boolean drawBaseline) {
        return topPanel.isEnabled() ? topPanel.toDataRange(drawBaseline) : null;
    }

    public DataRange getBottomDataRange(boolean drawBaseline) {
        return bottomPanel.isEnabled() ? bottomPanel.toDataRange(drawBaseline) : null;
    }

    private static void transferScale(RangeFieldsPanel source, RangeFieldsPanel target) {
        // These are only the six numbers already displayed in the dialog. No track data
        // is queried or autoscaled when an arrow is pressed.
        if (!source.convertParms() || !target.convertParms()) return;
        DataRange sourceRange = source.toDataRange(true);
        DataRange targetRange = target.toDataRange(true);
        float[] values = flippedScale(source.min, source.mid, source.max,
                sourceRange, targetRange);
        target.setValues(values[0], values[1], values[2], source.isLog);
    }

    /**
     * Flips a scale for the other side of a pair. Opposite one-sided displayed ranges
     * indicate opposite-signed data and therefore require sign inversion; otherwise the
     * same values are retained while the axis endpoints are reversed.
     */
    static float[] flippedScale(float min, float mid, float max,
                                DataRange sourceReference, DataRange targetReference) {
        return rangesHaveOppositeSigns(sourceReference, targetReference)
                ? new float[]{-max, -mid, -min}
                : new float[]{max, mid, min};
    }

    static boolean rangesHaveOppositeSigns(DataRange first, DataRange second) {
        int firstSign = oneSidedSign(first);
        int secondSign = oneSidedSign(second);
        return firstSign != 0 && secondSign != 0 && firstSign == -secondSign;
    }

    private static int oneSidedSign(DataRange range) {
        if (range == null) return 0;
        float low = Math.min(range.getMinimum(), range.getMaximum());
        float high = Math.max(range.getMinimum(), range.getMaximum());
        float scale = Math.max(Math.abs(low), Math.abs(high));
        float tolerance = scale == 0 ? 0 : 8 * Math.ulp(scale);
        if (low >= -tolerance && high > tolerance) return 1;
        if (high <= tolerance && low < -tolerance) return -1;
        return 0;
    }

    private static class RangeFieldsPanel extends JPanel {
        private final JTextField minField = new JTextField(8);
        private final JTextField midField = new JTextField(8);
        private final JTextField maxField = new JTextField(8);
        private final JCheckBox logCheckBox = new JCheckBox("Log scale");

        private float min, mid, max;
        private boolean isLog;

        RangeFieldsPanel(String title, DataRange defaults) {
            setBorder(BorderFactory.createTitledBorder(title));
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 4, 2, 4);
            gbc.anchor = GridBagConstraints.WEST;

            addRow(gbc, 0, "Min", minField);
            addRow(gbc, 1, "Mid", midField);
            addRow(gbc, 2, "Max", maxField);

            gbc.gridx = 1;
            gbc.gridy = 3;
            add(logCheckBox, gbc);

            if (defaults != null) {
                min = defaults.getMinimum();
                mid = defaults.getBaseline();
                max = defaults.getMaximum();
                isLog = defaults.isLog();
                minField.setText(String.valueOf(min));
                midField.setText(String.valueOf(mid));
                maxField.setText(String.valueOf(max));
                logCheckBox.setSelected(isLog);
            } else {
                setControlsEnabled(false);
            }
        }

        private void setControlsEnabled(boolean enabled) {
            setEnabled(enabled);
            for (Component component : getComponents()) {
                component.setEnabled(enabled);
            }
        }

        private void addRow(GridBagConstraints gbc, int row, String label, JTextField field) {
            gbc.gridx = 0;
            gbc.gridy = row;
            add(new JLabel(label), gbc);
            gbc.gridx = 1;
            add(field, gbc);
        }

        boolean convertParms() {
            try {
                min = Float.parseFloat(minField.getText());
                mid = Float.parseFloat(midField.getText());
                max = Float.parseFloat(maxField.getText());
                isLog = logCheckBox.isSelected();
                return true;
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Min, mid, and max must be numeric values");
                return false;
            }
        }

        DataRange toDataRange(boolean drawBaseline) {
            // Mirrors TrackMenuUtils.getDataRangeItem(): min/max are only sorted to clamp
            // mid into range - the DataRange itself keeps min/max in whatever order the
            // user typed them, since min > max is how the regular (non-paired) dialog lets
            // you flip the axis direction. Sorting them here (as an earlier version of this
            // method did) silently disabled that for paired tracks.
            float lo = Math.min(min, max);
            float hi = Math.max(min, max);
            float m = Math.max(lo, Math.min(mid, hi));
            return new DataRange(min, m, max, drawBaseline, isLog);
        }

        void setValues(float min, float mid, float max, boolean isLog) {
            this.min = min;
            this.mid = mid;
            this.max = max;
            this.isLog = isLog;
            minField.setText(String.valueOf(min));
            midField.setText(String.valueOf(mid));
            maxField.setText(String.valueOf(max));
            logCheckBox.setSelected(isLog);
        }
    }
}
