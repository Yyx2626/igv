/*
 * GenomeManager.java
 *
 * Created on November 9, 2007, 9:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.igv.feature.genome;


import org.igv.DirectoryManager;
import org.igv.Globals;
import org.igv.event.GenomeChangeEvent;
import org.igv.event.IGVEventBus;
import org.igv.exceptions.DataLoadException;
import org.igv.feature.genome.load.ChromAliasParser;
import org.igv.feature.genome.load.GenomeConfig;
import org.igv.feature.genome.load.GenomeLoader;
import org.igv.feature.genome.load.TrackConfig;
import org.igv.circview.CircularViewUtilities;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.track.FeatureTrack;
import org.igv.track.Track;
import org.igv.ucsc.hub.Hub;
import org.igv.ucsc.hub.HubParser;
import org.igv.ucsc.hub.TrackSelectionDialog;
import org.igv.ui.IGV;
import org.igv.ui.IGVMenuBar;
import org.igv.ui.WaitCursorManager;
import org.igv.ui.genome.GenomeLoadingDialog;
import org.igv.ui.genome.GenomeListItem;
import org.igv.ui.genome.GenomeListManager;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.util.MessageUtils;
import org.igv.ui.util.UIUtilities;
import org.igv.util.ResourceLocator;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jrobinso
 */
public class GenomeManager {

    public static final String SELECT_ANNOTATIONS_MESSAGE = "Select default annotation tracks for this genome.  " +
            "You can change these selections later using the 'Genomes > Select Default Annotations...' menu.";
    private static Logger log = LogManager.getLogger(GenomeManager.class);

    private static GenomeManager theInstance;

    private static GenomeListManager genomeListManager;

    private Genome currentGenome;
    private final AtomicLong genomeLoadGeneration = new AtomicLong();


    /**
     * Map from genomeID -> GenomeTableRecord
     * ID comparison will be case insensitive
     */

    public synchronized static GenomeManager getInstance() {
        if (theInstance == null) {
            theInstance = new GenomeManager();
        }
        return theInstance;
    }

    private GenomeManager() {
        genomeListManager = GenomeListManager.getInstance();
        GenomeLoader.loadSequenceMap();
    }


    public String getGenomeId() {
        return currentGenome == null ? null : currentGenome.getId();
    }

    /**
     * IGV always has exactly 1 genome loaded at a time.
     * This returns the currently loaded genome
     *
     * @return
     * @api
     */
    public Genome getCurrentGenome() {
        return currentGenome;
    }

    /**
     * Load a genome by ID, which might be a file path or URL
     *
     * @param genomeId - ID for an IGV hosted genome, or file path or url
     * @return boolean flag indicating success
     * @throws IOException
     */
    public boolean loadGenomeById(String genomeId) throws IOException {
        return loadGenomeById(genomeId, false);
    }

    public boolean loadGenomeById(String genomeId, boolean force) throws IOException {

        final Genome currentGenome = getCurrentGenome();
        if (force == false && currentGenome != null && genomeId.equals(currentGenome.getId())) {
            return false;
        }

        String genomePath;
        if (org.igv.util.ParsingUtils.fileExists(genomeId)) {
            genomePath = genomeId;
        } else {
            GenomeListItem item = getGenomeTableRecord(genomeId);
            if (item == null) {
                MessageUtils.showMessage("Could not locate genome with ID: " + genomeId);
                return false;
            } else {
                genomePath = item.getPath();
            }
        }
        return loadGenome(genomePath, genomeId) != null; // monitor[0]);
    }

    /**
     * The main load method -- loads a genome from a file or url path.  Note this is a long running operation and
     * should not be done on the Swing event thread as it will block the UI.
     * <p>
     * NOTE: The member 'currentGenome' is set here as a side effect.
     *
     * @param genomePath
     * @return
     * @throws IOException
     */
    public Genome loadGenome(String genomePath) throws IOException {
        String label = genomePath;
        int slash = Math.max(label.lastIndexOf('/'), label.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < label.length()) {
            label = label.substring(slash + 1);
        }
        int extension = label.lastIndexOf('.');
        if (extension > 0) {
            label = label.substring(0, extension);
        }
        return loadGenome(genomePath, label);
    }

    private Genome loadGenome(String genomePath, String genomeLabel) throws IOException {

        WaitCursorManager.CursorToken cursorToken = null;
        long loadGeneration = genomeLoadGeneration.incrementAndGet();
        GenomeLoadingDialog loadingDialog = null;
        boolean committed = false;
        Thread loadingThread = Thread.currentThread();
        try {
            log.info("Loading genome: " + genomePath);
            if (IGV.hasInstance()) {
                IGVMenuBar.getInstance().setAllMenusEnabled(false);
                IGV.getInstance().setStatusBarMessage("<html><font color=blue>Loading genome: " + genomeLabel + "</font></html>");
                cursorToken = WaitCursorManager.showWaitCursor();
                if (!Globals.isBatch()) {
                    loadingDialog = GenomeLoadingDialog.show(
                            IGV.getInstance().getMainFrame(), genomeLabel,
                            () -> {
                                loadingThread.interrupt();
                                cancelGenomeLoad(loadGeneration);
                            });
                }
            }

            Genome newGenome = GenomeLoader.getLoader(genomePath).loadGenome();

            // Load user-defined chr aliases, if any.  This is done last so they have priority
            final File genomeCacheDirectory = DirectoryManager.getGenomeCacheDirectory();
            try {
                String aliasPath = (new File(genomeCacheDirectory, newGenome.getId() + "_alias.tab")).getAbsolutePath();
                if (!(new File(aliasPath)).exists()) {
                    aliasPath = (new File(genomeCacheDirectory, newGenome.getId() + "_alias.tab.txt")).getAbsolutePath();
                }
                if ((new File(aliasPath)).exists()) {
                    newGenome.addChrAliases(ChromAliasParser.loadChrAliases(aliasPath));
                }
            } catch (Exception e) {
                log.error("Failed to load user defined alias", e);
            }

            // Cancel and newer genome requests invalidate this result before it can clear the
            // existing session or change the current genome.
            if (loadGeneration != genomeLoadGeneration.get()) {
                log.info("Canceled loading genome: " + genomeLabel);
                return null;
            }

            if (IGV.hasInstance()) {
                IGV.getInstance().resetSession(null);
            }

            // Add an entry to the pulldown
            GenomeListItem genomeListItem = new GenomeListItem(newGenome.getDisplayName(), genomePath, newGenome.getId());
            GenomeListManager.getInstance().addGenomeItem(genomeListItem);

            if (!setCurrentGenome(newGenome, () -> loadGeneration == genomeLoadGeneration.get())) {
                log.info("Canceled loading genome annotations: " + genomeLabel);
                return null;
            }
            committed = true;

            // Keep the dialog visible until the genome's default annotation tracks (normally
            // RefSeq) have been loaded and added to the UI by setCurrentGenome().
            if (loadingDialog != null) {
                loadingDialog.close();
                loadingDialog = null;
            }

            return currentGenome;

        } catch (SocketException e) {
            if (loadGeneration != genomeLoadGeneration.get()) {
                return null;
            }
            throw new RuntimeException("Server connection error", e);
        } catch (IOException | RuntimeException e) {
            if (loadGeneration != genomeLoadGeneration.get()) {
                return null;
            }
            throw e;
        } finally {
            if (loadingDialog != null) {
                loadingDialog.close();
            }
            if (IGV.hasInstance()) {
                if (loadGeneration == genomeLoadGeneration.get()) {
                    IGV.getInstance().setStatusBarMessage("");
                    if (!committed) {
                        if (currentGenome != null) {
                            IGVEventBus.getInstance().post(new GenomeChangeEvent(currentGenome));
                        } else {
                            IGVMenuBar.getInstance().setAllMenusEnabled(true);
                            IGV.getInstance().refreshGenomeSelection();
                        }
                    }
                }
                WaitCursorManager.removeWaitCursor(cursorToken);
            }
        }
    }

    private void cancelGenomeLoad(long loadGeneration) {
        if (!genomeLoadGeneration.compareAndSet(loadGeneration, loadGeneration + 1) || !IGV.hasInstance()) {
            return;
        }
        IGV.getInstance().setStatusBarMessage("");
        if (currentGenome != null) {
            // Restore controls and menu state to the genome that remains active.
            IGVEventBus.getInstance().post(new GenomeChangeEvent(currentGenome));
        } else {
            IGVMenuBar.getInstance().setAllMenusEnabled(true);
            IGV.getInstance().refreshGenomeSelection();
        }
    }

    public void setCurrentGenome(Genome newGenome) {
        setCurrentGenome(newGenome, () -> true);
    }

    private boolean setCurrentGenome(Genome newGenome, BooleanSupplier continueLoading) {

        if (!continueLoading.getAsBoolean()) {
            return false;
        }

        this.currentGenome = newGenome;

        // hasInstance() check to filters unit test
        if (IGV.hasInstance()) {
            // The genome model is now authoritative. Reflect that in the combo box before
            // loading sequence/annotation tracks so progressive track loading never appears
            // under the previous genome's label.
            IGV.getInstance().refreshGenomeSelection();
            IGV.getInstance().goToLocus(newGenome.getHomeChromosome()); //  newGenome.getDefaultPos());
            FrameManager.getDefaultFrame().setChromosomeName(newGenome.getHomeChromosome(), true);

            if (!restoreGenomeTracks(newGenome, continueLoading)) {
                return false;
            }

            if (!continueLoading.getAsBoolean()) {
                return false;
            }

            IGV.getInstance().resetFrames();
            IGV.getInstance().getSession().clearHistory();

            if (newGenome != Genome.NULL_GENOME) {
                // This should only occur on startup failure
                PreferencesManager.getPreferences().setLastGenome(newGenome.getId());
            }

            CircularViewUtilities.changeGenome(newGenome);

            IGVEventBus.getInstance().post(new GenomeChangeEvent(newGenome));
        }
        return true;
    }

    /**
     * @param genome
     */
    public void restoreGenomeTracks(Genome genome) {
        restoreGenomeTracks(genome, () -> true);
    }

    private boolean restoreGenomeTracks(Genome genome, BooleanSupplier continueLoading) {

        if (!continueLoading.getAsBoolean()) {
            return false;
        }

        IGV.getInstance().setSequenceTrack();

        // Fetch the gene track, defined by .genome files.  In this format the genome data is encoded in the .genome file
        FeatureTrack geneFeatureTrack = genome.getGeneTrack();   // Used for .genome and .gbk formats.  Otherwise null
        if (geneFeatureTrack != null) {
            if (!continueLoading.getAsBoolean()) {
                return false;
            }
            IGV.getInstance().addTrack(geneFeatureTrack);
        }

        List<ResourceLocator> resources = genome.getAnnotationResources();
        List<Track> annotationTracks = new ArrayList<>();
        if (resources != null) {
            for (ResourceLocator locator : resources) {
                if (!continueLoading.getAsBoolean()) {
                    return false;
                }
                try {
                    if (locator != null) {
                        List<Track> tracks = IGV.getInstance().load(locator);
                        if (!continueLoading.getAsBoolean()) {
                            return false;
                        }
                        annotationTracks.addAll(tracks);
                    }
                } catch (DataLoadException e) {
                    log.error("Error loading genome annotations", e);
                }
            }
        }

        if (annotationTracks.size() > 0) {
            if (!continueLoading.getAsBoolean()) {
                return false;
            }
            IGV.getInstance().addTracks(annotationTracks);
            for (Track track : annotationTracks) {
                ResourceLocator locator = track.getResourceLocator();
                if (locator != null) {
                    String fn = "";
                    if (locator != null) {
                        fn = locator.getPath();
                        int lastSlashIdx = fn.lastIndexOf("/");
                        if (lastSlashIdx < 0) {
                            lastSlashIdx = fn.lastIndexOf("\\");
                        }
                        if (lastSlashIdx > 0) {
                            fn = fn.substring(lastSlashIdx + 1);
                        }
                    }
                }
            }
        }

        IGV.getInstance().revalidateTrackPanels();
        return continueLoading.getAsBoolean();
    }

    /**
     * Update the annotation tracks for the current genome.  This will prompt the user to select tracks from the
     * genomes default hub.  The selected tracks will be saved in the genome config file, and loaded.  Deselected
     * tracks will be removed.
     *
     * @throws IOException
     */
    public void updateAnnotations() throws IOException {
        if (currentGenome != null) {
            GenomeConfig config = currentGenome.getConfig();

            if (config != null) {

                final List<TrackConfig> trackConfigs = config.getTrackConfigs();
                List<String> currentAnnotationPaths = trackConfigs == null ? Collections.EMPTY_LIST :
                        trackConfigs.stream().map(t -> t.url).toList();

                String message = "Select default annotations for " + config.getName();
                List<TrackConfig> selectedConfigs = selectAnnotationTracks(config, message);
                if (selectedConfigs == null) {
                    return;
                }

                config.setTracks(selectedConfigs);
                GenomeDownloadUtils.saveLocalGenome(config);

                Set<String> selectedTrackPaths = selectedConfigs.stream().map(t -> t.url).collect(Collectors.toSet());

                // Unload deselected tracks
                Set<String> pathsToRemove = new HashSet<>();
                for (String p : currentAnnotationPaths) {
                    if (!selectedTrackPaths.contains(p)) {
                        pathsToRemove.add(p);
                    }
                }
                IGV.getInstance().deleteTracksByPath(pathsToRemove);

                // Load selected tracks.Filter out tracks already loaded
                Set<String> loadedTrackPaths = IGV.getInstance().getAllTracks().stream()
                        .filter(t -> t.getResourceLocator() != null)
                        .map(t -> t.getResourceLocator().getPath())
                        .collect(Collectors.toSet());
                List<TrackConfig> tracksToLoad = selectedConfigs.stream()
                        .filter(trackConfig -> !loadedTrackPaths.contains(trackConfig.url))
                        .collect(Collectors.toList());

                List<ResourceLocator> locators = tracksToLoad.stream().map(t -> ResourceLocator.fromTrackConfig(t)).toList();

                IGV.getInstance().loadTracks(locators);
            }
        }
    }

    /**
     * Prompt the user to select annotation tracks from the genome's default hub.
     *
     * @param config
     * @return
     * @throws IOException
     */

    public static List<TrackConfig> selectAnnotationTracks(GenomeConfig config, String message) throws IOException {

        String annotationHub = config.getHubs().get(0);  // IGV convention
        Hub hub = HubParser.loadHub(annotationHub);

        Set<String> currentSelections = config.getTrackConfigs() == null ? Collections.emptySet() :
                config.getTrackConfigs().stream()
                        .map(trackConfig -> trackConfig.url)
                        .collect(Collectors.toSet());
        TrackSelectionDialog dlg = TrackSelectionDialog.getTrackHubSelectionDialog(hub, config.getUcscID(), currentSelections, true, message);
        try {
            UIUtilities.invokeAndWaitOnEventThread(() -> dlg.setVisible(true));
            if (dlg.isCanceled()) {
                return null;
            } else {
                return dlg.getSelectedConfigs();
            }
        } catch (Exception e) {
            log.error("Error opening or using TrackHubSelectionDialog: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete the specified genome files
     *
     * @param removedValuesList
     */
    public void deleteDownloadedGenomes(List<GenomeListItem> removedValuesList) throws IOException {

        for (GenomeListItem item : removedValuesList) {

            String loc = item.getPath();
            File genomeFile = new File(loc);

            if (genomeFile.exists() && DirectoryManager.isChildOf(DirectoryManager.getGenomeCacheDirectory(), genomeFile)) {

                genomeFile.delete();

                // Delete associated data files
                File dataFileDirectory = new File(DirectoryManager.getGenomeCacheDirectory(), item.getId());
                File localFasta = DotGenomeUtils.getLocalFasta(item.getId());  //  (Legacy .genome convention)

                if ((dataFileDirectory.isDirectory() || localFasta != null) &&
                        MessageUtils.confirm("Delete downloaded data files?")) {

                    if (dataFileDirectory.isDirectory()) {
                        try (Stream<Path> paths = Files.walk(dataFileDirectory.toPath())) {
                            paths.sorted(Comparator.reverseOrder())
                                    .map(Path::toFile)
                                    .forEach(File::delete);
                        }
                        dataFileDirectory.delete();
                    }

                    if (localFasta != null) {
                        // If fasta file is in the "igv/genomes" directory delete it
                        DotGenomeUtils.removeLocalFasta(item.getId());
                        if (DirectoryManager.isChildOf(DirectoryManager.getGenomeCacheDirectory(), localFasta)) {
                            if (MessageUtils.confirm("Delete fasta file: " + localFasta.getAbsolutePath() + "?")) {
                                localFasta.delete();
                                File indexFile = new File(localFasta.getAbsolutePath() + ".fai");
                                if (indexFile.exists()) {
                                    indexFile.delete();
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Searches through currently loaded GenomeTableRecords and returns
     * that with a matching ID. If not found, searches server and
     * user defined lists
     *
     * @param genomeId
     * @return
     */
    public GenomeListItem getGenomeTableRecord(String genomeId) {

        GenomeListItem matchingItem = GenomeListManager.getInstance().getGenomeTableRecord(genomeId);
        if (matchingItem == null) {
            // If genome archive was not found, search hosted genomes
            matchingItem = HostedGenomes.getGenomeListItem(genomeId);
        }
        return matchingItem;
    }

    // Setter provided for unit tests
    public void setCurrentGenomeForTest(Genome genome) {
        this.currentGenome = genome;
    }


}
