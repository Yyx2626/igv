package org.igv.ui;

import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.ui.util.ImageFileTypes;
import org.igv.ui.util.ScreenshotFilename;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/** Collects publication-oriented screenshot and optional data-export settings. */
public final class ScreenshotDialog {

    private ScreenshotDialog() {
    }

    public record Options(File outputPrefix, ImageFileTypes.Type format,
                          boolean includeCoordinates, boolean includeTrackNames,
                          boolean outputDataTsv, boolean addCoordinateRange) {
        private String outputStem() {
            String suffix = addCoordinateRange ? ScreenshotFilename.currentCoordinateSuffix() : "";
            return outputPrefix.getAbsolutePath() + (suffix.isEmpty() ? "" : "." + suffix);
        }

        public File imageFile() {
            return new File(outputStem() + format.getExtension());
        }

        public File dataFile() {
            return new File(outputStem() + ".tsv");
        }
    }

    public static Options show(Frame owner) {
        File lastDirectory = PreferencesManager.getPreferences().getLastSnapshotDirectory();
        File defaultPrefix = new File(lastDirectory == null ? new File(".") : lastDirectory, "igv_snapshot");

        JTextField prefixField = new JTextField(defaultPrefix.getAbsolutePath(), 32);
        JButton browseButton = new JButton("Browse...");
        JComboBox<ImageFileTypes.Type> formatBox = new JComboBox<>(
                new ImageFileTypes.Type[]{ImageFileTypes.Type.PNG, ImageFileTypes.Type.SVG});
        formatBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == ImageFileTypes.Type.PNG ? "PNG (raster)" : "SVG (vector)");
                return this;
            }
        });
        JCheckBox includeCoordinates = new JCheckBox("Include top genomic coordinates", true);
        JCheckBox includeTrackNames = new JCheckBox("Include track names", true);
        JCheckBox outputData = new JCheckBox("Output underlying data TSV", false);
        JCheckBox addCoordinateRange = new JCheckBox(
                "Add genomic coordinate range to output filename", true);
        int bins = Math.max(1, PreferencesManager.getPreferences().getAsInt(Constants.SCREENSHOT_DATA_BINS));
        JLabel binsLabel = new JLabel("TSV uses " + bins + " equal bins (change in Preferences > General)");
        binsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        browseButton.addActionListener(e -> {
            File current = new File(prefixField.getText().trim());
            FileDialog chooser = new FileDialog(owner, "Choose output prefix", FileDialog.SAVE);
            if (current.getParentFile() != null) chooser.setDirectory(current.getParentFile().getAbsolutePath());
            chooser.setFile(current.getName());
            chooser.setVisible(true);
            if (chooser.getFile() != null) {
                prefixField.setText(stripKnownExtension(new File(chooser.getDirectory(), chooser.getFile())).getAbsolutePath());
            }
        });

        JPanel prefixPanel = new JPanel(new BorderLayout(5, 0));
        prefixPanel.add(prefixField, BorderLayout.CENTER);
        prefixPanel.add(browseButton, BorderLayout.EAST);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Output prefix"), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(prefixPanel, c);
        c.gridy++;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel("Output image format"), c);
        c.gridx = 1;
        panel.add(formatBox, c);
        c.gridy++;
        panel.add(addCoordinateRange, c);
        c.gridy++;
        panel.add(includeCoordinates, c);
        c.gridy++;
        panel.add(includeTrackNames, c);
        c.gridy++;
        panel.add(outputData, c);
        c.gridy++;
        panel.add(binsLabel, c);

        while (true) {
            int result = JOptionPane.showConfirmDialog(owner, panel, "Save Screenshot",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;
            String prefix = prefixField.getText().trim();
            if (prefix.isEmpty()) {
                JOptionPane.showMessageDialog(owner, "Output prefix cannot be empty.");
                continue;
            }
            File prefixFile = stripKnownExtension(new File(prefix));
            return new Options(prefixFile, (ImageFileTypes.Type) formatBox.getSelectedItem(),
                    includeCoordinates.isSelected(), includeTrackNames.isSelected(), outputData.isSelected(),
                    addCoordinateRange.isSelected());
        }
    }

    private static File stripKnownExtension(File file) {
        String path = file.getAbsolutePath();
        for (String extension : new String[]{".png", ".svg", ".tsv"}) {
            if (path.toLowerCase().endsWith(extension)) {
                return new File(path.substring(0, path.length() - extension.length()));
            }
        }
        return file;
    }
}
