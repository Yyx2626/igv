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

    private static Options lastAcceptedOptions;

    private ScreenshotDialog() {
    }

    public record Options(File outputPrefix, ImageFileTypes.Type format,
                          boolean includeCoordinates, boolean includeTrackNames,
                          boolean outputDataTsv, boolean addCoordinateRange,
                          boolean onlySelectedTracks) {
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
        return show(owner, false);
    }

    public static Options show(Frame owner, boolean preferSelectedTracks) {
        File lastDirectory = PreferencesManager.getPreferences().getLastSnapshotDirectory();
        Options previous = lastAcceptedOptions;
        File defaultPrefix = previous == null
                ? new File(lastDirectory == null ? new File(".") : lastDirectory, "igv_snapshot")
                : previous.outputPrefix();

        JTextField prefixField = new JTextField(defaultPrefix.getAbsolutePath(), 32);
        JButton browseButton = new JButton("Browse...");
        JComboBox<ImageFileTypes.Type> formatBox = new JComboBox<>(
                new ImageFileTypes.Type[]{ImageFileTypes.Type.PNG, ImageFileTypes.Type.PDF,
                        ImageFileTypes.Type.SVG});
        formatBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == ImageFileTypes.Type.PNG) setText("PNG (raster)");
                else if (value == ImageFileTypes.Type.SVG) setText("SVG (vector)");
                else if (value == ImageFileTypes.Type.PDF) setText("PDF (vector)");
                return this;
            }
        });
        if (previous != null) formatBox.setSelectedItem(previous.format());
        JCheckBox includeCoordinates = new JCheckBox("Include top genomic coordinates",
                previous == null || previous.includeCoordinates());
        JCheckBox includeTrackNames = new JCheckBox("Include track names",
                previous == null || previous.includeTrackNames());
        JCheckBox outputData = new JCheckBox("Output underlying data TSV",
                previous != null && previous.outputDataTsv());
        boolean hasSelectedTracks = IGV.hasInstance() && !IGV.getSelectedTracks().isEmpty();
        JCheckBox onlySelectedTracks = new JCheckBox("Only export selected tracks",
                hasSelectedTracks && (preferSelectedTracks
                        || previous != null && previous.onlySelectedTracks()));
        onlySelectedTracks.setEnabled(hasSelectedTracks);
        JCheckBox addCoordinateRange = new JCheckBox(
                "Add genomic coordinate range to output filename",
                previous == null || previous.addCoordinateRange());
        int bins = Math.max(1, PreferencesManager.getPreferences().getAsInt(Constants.SCREENSHOT_DATA_BINS));
        JLabel binsLabel = new JLabel("Display and export using " + bins +
                " equal genomic bins (change in Preferences > General)");
        binsLabel.setForeground(Color.BLACK);
        JLabel windowSizeLabel = new JLabel("Current IGV window size: " + owner.getWidth() + " × " +
                owner.getHeight() + " pixels");
        windowSizeLabel.setForeground(Color.BLACK);
        JButton setWindowSize = new JButton("Set...");
        setWindowSize.addActionListener(e -> {
            Dimension requestedSize = WindowSizeDialog.show(owner);
            if (requestedSize != null && IGV.hasInstance()) {
                IGV.getInstance().setApplicationWindowSize(requestedSize);
                windowSizeLabel.setText("Current IGV window size: " + owner.getWidth() + " × " +
                        owner.getHeight() + " pixels");
            }
        });
        Box windowSizePanel = Box.createHorizontalBox();
        windowSizePanel.add(windowSizeLabel);
        windowSizePanel.add(Box.createHorizontalStrut(8));
        windowSizePanel.add(setWindowSize);
        windowSizePanel.add(Box.createHorizontalGlue());

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
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel("Output options"), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(addCoordinateRange, c);
        c.gridy++;
        panel.add(includeCoordinates, c);
        c.gridy++;
        panel.add(includeTrackNames, c);
        c.gridy++;
        panel.add(outputData, c);
        c.gridy++;
        panel.add(onlySelectedTracks, c);
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        panel.add(Box.createVerticalStrut(8), c);
        c.gridy++;
        panel.add(binsLabel, c);
        c.gridy++;
        panel.add(windowSizePanel, c);

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
            Options options = new Options(prefixFile, (ImageFileTypes.Type) formatBox.getSelectedItem(),
                    includeCoordinates.isSelected(), includeTrackNames.isSelected(), outputData.isSelected(),
                    addCoordinateRange.isSelected(), onlySelectedTracks.isSelected());
            lastAcceptedOptions = options;
            return options;
        }
    }

    private static File stripKnownExtension(File file) {
        String path = file.getAbsolutePath();
        for (String extension : new String[]{".png", ".svg", ".pdf", ".tsv"}) {
            if (path.toLowerCase().endsWith(extension)) {
                return new File(path.substring(0, path.length() - extension.length()));
            }
        }
        return file;
    }
}
