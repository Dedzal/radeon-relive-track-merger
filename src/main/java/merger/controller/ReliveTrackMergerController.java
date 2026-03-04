package merger.controller;

import merger.ffmpeg.FfmpegInstaller;
import merger.processing.ProcessingConfig;
import merger.processing.ReplayProcessor;
import merger.ui.ReliveTrackMergerUI;
import merger.util.OutputFolderResolver;
import merger.util.ProcessingLogger;
import merger.util.ReplayUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Controller responsible for wiring the UI and the processing logic.
 * <p>
 * Responsibilities:
 * - Track the currently selected input and output folders.
 * - Discover and manage the list of replay files that need processing.
 * - Validate disk space and UI state before starting processing.
 * - Launch the processing task on a background thread and report progress back to the UI.
 * <p>
 * Notes on concurrency and threading:
 * - Long-running operations (actual file processing) are performed on a separate thread
 *   so the Swing UI thread remains responsive.
 * - UI updates are dispatched with SwingUtilities.invokeLater(...) to ensure they run
 *   on the Event Dispatch Thread (EDT).
 * <p>
 * Output folder rules are centralized in OutputFolderResolver; the controller relies on
 * that resolver to ensure the UI and processing logic share the same canonical rules.
 */
public class ReliveTrackMergerController {

    // Constant folder name used when we copy processed replays into a sibling directory.
    public static final String REPLAYS_MERGED = "replays_merged";

    // Currently selected input folder (where original replays are found)
    private File inputFolder;

    // The actual internal output folder used by processing. This is the "source of truth"
    // for where processed files will go. It may be equaled to `inputFolder` (if replacing
    // originals) or to a child folder (input/replays_merged) or a folder chosen by the user.
    private File outputFolder;

    // Cached list of files to process (discovered under inputFolder)
    private List<File> filesToProcess;

    // Flags used for graceful shutdown and cancellation
    // AtomicBoolean is used to safely share these flags across the UI thread and the background processing thread.
    private final AtomicBoolean processingCancelled = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    // Background thread performing the sequential processing of files.
    private Thread processingThread;

    // Short-lived processing configuration/state object used while processing runs.
    private ProcessingConfig processingConfig;

    // Simple counters for reporting results in the final log output.
    private int filesProcessedCount = 0;
    private int filesFailedCount = 0;

    /**
     * Called when the user selects (or changes) the input folder in the UI.
     * <p>
     * Behavior:
     * - If the user canceled the folder dialog (selectedInputFolder == null) we reset
     *   controller state and clear UI elements.
     * - Otherwise we set the inputFolder, update the UI text field and compute a
     *   canonical outputFolder using the OutputFolderResolver. This ensures the UI
     *   displays the same internal path that the processing will actually use.
     * <p>
     * Important: this method updates the UI and also performs some validations
     * (presence of replays, available disk space) so that the "Process" button will
     * only be enabled when processing can reasonably proceed.
     */
    public void selectInputFolder(ReliveTrackMergerUI ui, File selectedInputFolder) {
        ui.cleanLogTextarea();

        if (selectedInputFolder == null) {
            // User canceled - reset everything (safe no-op if already null)
            inputFolder = null;
            outputFolder = null;
            filesToProcess = null;
            ui.cleanTextFieldInputFolderPath();
            ui.cleanTextFieldOutputFolderPath();
            ui.clearVideoList();
            ui.disableButtonProcess();
        } else {
            // Persist the selected input folder and show it in the UI
            inputFolder = selectedInputFolder;
            String inputFolderPath = inputFolder.getAbsolutePath();
            ui.setTextFieldInputFolderPath(inputFolderPath);

            // Compute the canonical output folder using the resolver helper.
            // This centralizes the rule: when replaceOriginals==true the output is
            // the input folder; otherwise it's input/replays_merged (avoiding double-append).
            outputFolder = OutputFolderResolver.resolveSelectedOutput(inputFolder, ui.isReplaceOriginalReplaysSelected());
            ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath());

            // Toggle the availability of the output-folder selection control depending on
            // whether the user has chosen to replace originals. When replacing originals we
            // prefer to disable the selection so the user doesn't accidentally pick another folder.
            if (ui.isReplaceOriginalReplaysSelected()) {
                ui.disableButtonSelectOutputFolder();
            } else {
                ui.enableButtonSelectOutputFolder();
            }

            // Log the internal canonical output folder so the log matches what processing uses.
            ProcessingLogger.info("Output folder set to: " + outputFolder);

            // Refresh the list of replays in the UI and validate the state (replays found, disk space)
            updateReplayListAndView(ui);
            validateReplaysToProcessFound(ui);
            validateEnoughStorageAvailableForProcessing(ui);
        }
    }

    // Convenience helper reflecting the UI checkbox semantics.
    private static boolean dontReplaceOriginalReplays(ReliveTrackMergerUI ui) {
        return !ui.isReplaceOriginalReplaysSelected();
    }

    /**
     * Called when the user explicitly selects a different output folder.
     * <p>
     * We must be careful to keep the UI-visible text and the internal `outputFolder`
     * aligned. The resolver is used again here to guarantee consistent behavior
     * (append `replays_merged` only when appropriate and avoid double-append).
     */
    public void selectOutputFolder(ReliveTrackMergerUI ui, File selectedOutputFolder) {
        if (selectedOutputFolder != null) {
            outputFolder = OutputFolderResolver.resolveSelectedOutput(selectedOutputFolder, ui.isReplaceOriginalReplaysSelected());

            // Update the UI from the canonical internal value — do not compose paths in the UI layer.
            ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath());

            // Log the real (internal) value of outputFolder so logs and UI remain consistent
            ProcessingLogger.info("Output folder set to: " + outputFolder.getAbsolutePath());

            // Re-validate disk space for the newly selected output
            validateEnoughStorageAvailableForProcessing(ui);
        }
    }

    /**
     * Discover and present unprocessed replay files in the UI list. This method
     * keeps the UI listing in sync with the controller's `filesToProcess` cache.
     */
    private void updateReplayListAndView(ReliveTrackMergerUI ui) {
        ui.clearVideoList();
        if (inputFolder != null && inputFolder.isDirectory()) {
            List<File> unprocessedReplays = ReplayUtils.getUnprocessedReplays(inputFolder);
            filesToProcess = unprocessedReplays.stream()
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());

            for (File replay : filesToProcess) {
                ui.addToVideoList(replay.getName());
            }

            // Force a repaint so the UI list immediately reflects the new contents
            ui.repaintVideoList();
        }
    }

    /**
     * Enable/disable the "Process" button depending on whether we found any
     * replay files to operate on.
     */
    private void validateReplaysToProcessFound(ReliveTrackMergerUI ui) {
        if (filesToProcess == null || filesToProcess.isEmpty()) {
            ProcessingLogger.info("No replays found in selected directory or any of its subdirectories");
            ui.disableButtonProcess();
        } else {
            ui.enableButtonProcess();
        }
    }

    /**
     * Run a set of validations and refreshes that are expected whenever the
     * input/output folders change. This includes recomputing displayed sizes
     * and ensuring enough free disk space.
     */
    private void validateEnoughStorageAvailableForProcessing(ReliveTrackMergerUI ui) {
        // Clear the log area — we show fresh messages for each selection change
        ui.cleanLogTextarea();
        displayFileSizeInfo();
        validateStorageSpaceAtOutputDisk(ui);
    }

    /**
     * Entry point used by UI to start processing. This prepares state, ensures
     * FFmpeg is installed, creates the output dir if required, then starts the
     * background processing thread.
     *
     * Error handling and early returns are used to keep the UI responsive and
     * present clear diagnostic messages if something goes wrong (missing ffmpeg,
     * failed directory creation, insufficient disk space, etc.).
     */
    public void executeReplayProcessing(ReliveTrackMergerUI ui) {
        processingCancelled.set(false);
        shutdownRequested.set(false);
        filesProcessedCount = 0;
        filesFailedCount = 0;

        long startTime = System.currentTimeMillis();
        ui.cleanLogTextarea();

        // Ensure FFmpeg is available before heavy work begins — it will throw
        // an IllegalStateException if installation/check fails.
        try {
            FfmpegInstaller.checkOrInstallFfmpeg();
        } catch (IllegalStateException e) {
            ProcessingLogger.error("FFmpeg installation failed: " + e.getMessage(), e);
            ui.setButtonProcessToInitialState();
            return;
        }

        // If the user selected the "do not replace originals" mode we will ensure
        // the output folder points to a `replays_merged` folder. This block also
        // performs optional cleaning of that folder if the user requested it.
        if (dontReplaceOriginalReplays(ui) && outputFolder != null) {
            // Guard against accidental double-append by checking the last segment name
            if (!REPLAYS_MERGED.equalsIgnoreCase(outputFolder.getName())) {
                outputFolder = new File(outputFolder, REPLAYS_MERGED);
            }
            if (ui.isCleanOutputSelected() && outputFolder.exists()) {
                ProcessingLogger.info("Cleaning output folder...");
                ReplayUtils.deleteProcessedReplaysInDirectory(outputFolder);
            }
            // Try to create the output folder if it doesn't exist. If we can't create it,
            // abort processing and inform the user.
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                ProcessingLogger.error("Failed to create output folder.");
                ui.setButtonProcessToInitialState();
                return;
            }
        }

        // Log a summary and start the processing thread
        printAmountOfFilesToProcess();
        printOutputFolderPath();
        printSeparator();

        processingConfig = new ProcessingConfig();

        ReplayProcessor processor = new ReplayProcessor(
                outputFolder,
                inputFolder,
                ui.isReplaceOriginalReplaysSelected(),
                ui.isDeleteMicrophoneTracksSelected(),
                processingConfig
        );

        // Run processing on a single dedicated background thread so we can keep
        // the logic sequential (no parallelism for simplicity) and still be able
        // to respond to user-initiated cancel/pause requests.
        processingThread = new Thread(() -> {
            processReplaysSequentially(processor, ui, startTime);
        });
        processingThread.setName("ReplayProcessingThread");
        processingThread.start();
    }

    /**
     * Check available disk space on the disk where the output folder will be created.
     * If the total size of selected replays exceeds the available disk space we
     * disable the Process button and inform the user.
     */
    private void validateStorageSpaceAtOutputDisk(ReliveTrackMergerUI ui) {
        if (inputFolder != null && outputFolder != null && filesToProcess != null && !filesToProcess.isEmpty()) {
            double totalSizeOfReplays = getTotalSizeOfSelectedReplays();
            double availableDiskSpace = getAvailableDiskSpaceOfOutputDirectory();
            if (totalSizeOfReplays >= availableDiskSpace) {
                ProcessingLogger.info("The total file size of the selected replays (" + String.format("%.1f", totalSizeOfReplays) + " GB) exceeds the available disk space (" + String.format("%.1f", availableDiskSpace) + " GB).");
                ProcessingLogger.info("Please free up some space on the target disk or select a different output directory.");
                ui.disableButtonProcess();
            } else {
                ui.enableButtonProcess();
            }
        }
    }

    /**
     * Request a graceful stop of processing. We signal the processing thread to
     * shutdown and wait briefly (30s) for it to finish.
     */
    public void cancelReplayProcessing() {
        if (processingThread != null && processingThread.isAlive()) {
            ProcessingLogger.info("Requesting graceful shutdown of replay processing...");
            processingCancelled.set(true);
            shutdownRequested.set(true);

            // Wait for thread to finish gracefully (max 30 seconds)
            try {
                processingThread.join(30000);
                if (processingThread.isAlive()) {
                    ProcessingLogger.error("Processing thread did not shut down gracefully within timeout.");
                }
            } catch (InterruptedException e) {
                ProcessingLogger.error("Interrupted while waiting for processing thread to shut down: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Pause/resume helpers simply delegate to the ProcessingConfig, which exposes
     * a pause/resume primitive used by the background thread.
     */
    public void pauseReplayProcessing() {
        if (processingConfig != null && processingThread != null && processingThread.isAlive()) {
            ProcessingLogger.info("Pausing replay processing...");
            processingConfig.requestPause();
        }
    }

    public void resumeReplayProcessing() {
        if (processingConfig != null && processingThread != null && processingThread.isAlive()) {
            ProcessingLogger.info("Resuming replay processing...");
            processingConfig.resume();
        }
    }

    /**
     * The core sequential processing loop. Each replay is processed in-order, and
     * the UI is updated with a small status emoji prefix to indicate progress.
     * <p>
     * Error handling in the loop catches InterruptedException (used for shutdown)
     * separately so we can perform the correct UI update and bookkeeping.
     */
    private void processReplaysSequentially(ReplayProcessor processor, ReliveTrackMergerUI ui, long startTime) {
        int totalFiles = filesToProcess.size();

        try {
            for (int fileIndex = 0; fileIndex < filesToProcess.size(); fileIndex++) {
                File replayFile = filesToProcess.get(fileIndex);

                // Check for graceful shutdown request
                if (shutdownRequested.get()) {
                    processor.requestShutdown();
                    processingCancelled.set(true);
                    break;
                }

                // Check and wait if pause is requested
                try {
                    processingConfig.checkAndWaitIfPaused();
                } catch (InterruptedException e) {
                    if (shutdownRequested.get()) {
                        break;
                    }
                    ProcessingLogger.error("Processing interrupted: " + e.getMessage());
                    break;
                }

                try {
                    // Update UI to show processing status; updates must run on the EDT
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("🔁 " + replayFile.getName());
                    });

                    // Process the replay file once (no retries) and update counters
                    processor.process(replayFile);

                    filesProcessedCount++;
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("✅ " + replayFile.getName());
                    });

                } catch (InterruptedException e) {
                    // InterruptedException commonly signifies a requested shutdown — handle specially
                    ProcessingLogger.error("Processing interrupted: " + replayFile.getName());
                    if (shutdownRequested.get()) {
                        SwingUtilities.invokeLater(() -> {
                            ui.updateReplayStatusInList("⏹️ " + replayFile.getName());
                        });
                        break;
                    }
                    filesFailedCount++;
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("❌ " + replayFile.getName());
                    });

                } catch (Exception e) {
                    // Generic exception for a single file should not abort the whole run; we record it
                    ProcessingLogger.error("Error processing: " + replayFile.getName() + " - " + e.getMessage(), e);
                    filesFailedCount++;
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("❌ " + replayFile.getName());
                    });
                }

            }
        } finally {
            // Ensure a graceful shutdown of processor resources
            processor.requestShutdown();

            // Finalize processing on the UI thread: write totals and reset UI controls
            SwingUtilities.invokeLater(() -> {
                logFinalProcessingResult(this, startTime);
                ui.setButtonProcessToInitialState();

                if (!processingCancelled.get() && ui.isOpenOutputFolderSelected()) {
                    openOutputDirectory();
                }
            });
        }
    }


    // Helper that logs a short summary on completion or cancellation.
    private static void logFinalProcessingResult(ReliveTrackMergerController controller, long startTime) {
        if (controller.isProcessingCancelled()) {
            ProcessingLogger.info("Replay processing stopped by user");
        } else {
            ProcessingLogger.info("");
            ProcessingLogger.info("Done!");
            ProcessingLogger.info("Files processed: " + controller.filesProcessedCount);
            ProcessingLogger.info("Files failed: " + controller.filesFailedCount);
            ProcessingLogger.info("Processing took a total of " + String.format("%.1f", (System.currentTimeMillis() - startTime) / 1000.0) + " seconds");
        }
    }

    /**
     * Show some size information to the user. This logs both the total size of
     * the selected replays and the available size on disk (in GB) to help users
     * judge whether they need to free space or pick another output location.
     */
    private void displayFileSizeInfo() {
        if (inputFolder != null && outputFolder != null && filesToProcess != null && !filesToProcess.isEmpty()) {
            ProcessingLogger.info("Total file size of selected replays: " + String.format("%.1f", getTotalSizeOfSelectedReplays()) + " GB");
            ProcessingLogger.info("Available storage on disk: " + String.format("%.1f", getAvailableDiskSpaceOfOutputDirectory()) + " GB");
        }
    }

    private void printAmountOfFilesToProcess() {
        ProcessingLogger.info("Processing " + filesToProcess.size() + " file(s)");
    }

    private void printOutputFolderPath() {
        ProcessingLogger.info("Output folder is: " + outputFolder.getAbsolutePath());
    }

    private void printSeparator() {
        ProcessingLogger.info("");
        ProcessingLogger.info("-----------------------------------------------------------------------------");
        ProcessingLogger.info("");
    }

    public boolean isProcessingCancelled() {
        return processingCancelled.get();
    }

    public File getInputFolder() {
        return inputFolder;
    }

    /**
     * Convenience helper used by the UI when the user toggles the "replace originals"
     * checkbox. We also log the change so the log is always consistent with the UI.
     */
    public void setInputFolderAsOutputFolder() {
        outputFolder = inputFolder;
        ProcessingLogger.info("Output folder set to: " + outputFolder);
    }

    /**
     * External setter that allows tests or other components to set the output folder
     * programmatically. We log the canonical value so callers can rely on the log.
     */
    public void setOutputFolder(File file) {
        outputFolder = file;
        ProcessingLogger.info("Output folder set to: " + outputFolder);
    }

    /**
     * Compute the total size (in GB) of the selected replay files. This is used
     * for a quick preflight disk-space check.
     */
    private double getTotalSizeOfSelectedReplays() {
        long totalSizeInBytes = filesToProcess.stream()
                .mapToLong(File::length) // Get file size in bytes
                .sum();
        return totalSizeInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
    }

    /**
     * Probe the filesystem to get the available free space for the output folder's
     * drive. If the output folder doesn't exist yet we walk up the parent chain until
     * we find an existing directory and probe that — this handles the common case
     * where the user has selected a new directory that hasn't been created yet.
     */
    private double getAvailableDiskSpaceOfOutputDirectory() {
        File probe = outputFolder;
        if (probe == null) return 0.0;
        // If the output folder doesn't yet exist use the nearest existing parent to probe free space
        while (probe != null && !probe.exists()) probe = probe.getParentFile();
        if (probe == null) return 0.0;
        long freeSpaceInBytes = probe.getFreeSpace(); // Returns free space in bytes
        return freeSpaceInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
    }

    private void openOutputDirectory() {
        if (outputFolder != null && outputFolder.exists()) {
            openFile(outputFolder);
        } else {
            ProcessingLogger.error("Output folder does not exist or is not set.");
        }
    }

    private void openFile(File file) {
        if (file == null || !file.exists()) {
            ProcessingLogger.error("File does not exist or is null: " + file);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                ProcessingLogger.error("Desktop API is not supported on this system.");
                return;
            }
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            ProcessingLogger.error("Failed to open file: " + e.getMessage(), e);
        }
    }
}
