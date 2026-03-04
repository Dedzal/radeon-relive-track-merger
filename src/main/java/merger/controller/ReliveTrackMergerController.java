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

public class ReliveTrackMergerController {

    public static final String REPLAYS_MERGED = "replays_merged";

    private File inputFolder;
    private File outputFolder;
    private List<File> filesToProcess;

    private volatile AtomicBoolean processingCancelled = new AtomicBoolean(false);
    private volatile AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private Thread processingThread;
    private ProcessingConfig processingConfig;
    private int filesProcessedCount = 0;
    private int filesFailedCount = 0;

    public void selectInputFolder(ReliveTrackMergerUI ui, File selectedInputFolder) {
        ui.cleanLogTextarea();

        if (selectedInputFolder == null) {
            // User canceled - reset everything
            inputFolder = null;
            outputFolder = null;
            filesToProcess = null;
            ui.cleanTextFieldInputFolderPath();
            ui.cleanTextFieldOutputFolderPath();
            ui.clearVideoList();
            ui.disableButtonProcess();
        } else {
            inputFolder = selectedInputFolder;
            String inputFolderPath = inputFolder.getAbsolutePath();
            ui.setTextFieldInputFolderPath(inputFolderPath);

            outputFolder = OutputFolderResolver.resolveSelectedOutput(inputFolder, ui.isReplaceOriginalReplaysSelected());
            ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath());

            // Toggle availability of output-folder selection/button
            if (ui.isReplaceOriginalReplaysSelected()) {
                ui.disableButtonSelectOutputFolder();
            } else {
                ui.enableButtonSelectOutputFolder();
            }

            // Log the real (internal) value of outputFolder so logs and UI remain consistent
            ProcessingLogger.info("Output folder set to: " + outputFolder);

            updateReplayListAndView(ui);
            validateReplaysToProcessFound(ui);
            validateEnoughStorageAvailableForProcessing(ui);
        }
    }

    private static boolean dontReplaceOriginalReplays(ReliveTrackMergerUI ui) {
        return !ui.isReplaceOriginalReplaysSelected();
    }

    public void selectOutputFolder(ReliveTrackMergerUI ui, File selectedOutputFolder) {
        if (selectedOutputFolder != null) {
            outputFolder = OutputFolderResolver.resolveSelectedOutput(selectedOutputFolder, ui.isReplaceOriginalReplaysSelected());

            // Update UI with the canonical internal value
            ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath());

            // Log the real (internal) value of outputFolder so logs and UI remain consistent
            ProcessingLogger.info("Output folder set to: " + outputFolder.getAbsolutePath());

            validateEnoughStorageAvailableForProcessing(ui);
        }
    }

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

            ui.repaintVideoList();
        }
    }

    private void validateReplaysToProcessFound(ReliveTrackMergerUI ui) {
        if (filesToProcess == null || filesToProcess.isEmpty()) {
            ProcessingLogger.info("No replays found in selected directory or any of its subdirectories");
            ui.disableButtonProcess();
        } else {
            ui.enableButtonProcess();
        }
    }


    private void validateEnoughStorageAvailableForProcessing(ReliveTrackMergerUI ui) {
        ui.cleanLogTextarea();
        displayFileSizeInfo();
        validateStorageSpaceAtOutputDisk(ui);
    }

    public void executeReplayProcessing(ReliveTrackMergerUI ui) {
        processingCancelled.set(false);
        shutdownRequested.set(false);
        filesProcessedCount = 0;
        filesFailedCount = 0;

        long startTime = System.currentTimeMillis();
        ui.cleanLogTextarea();

        try {
            FfmpegInstaller.checkOrInstallFfmpeg();
        } catch (IllegalStateException e) {
            ProcessingLogger.error("FFmpeg installation failed: " + e.getMessage(), e);
            ui.setButtonProcessToInitialState();
            return;
        }

        // if we are not replacing the original replays, we need to create the output (replays_merged) folder
        if (dontReplaceOriginalReplays(ui) && outputFolder != null) {
            // Use a name-based check to avoid accidental double-appending of the folder name
            if (!REPLAYS_MERGED.equalsIgnoreCase(outputFolder.getName())) {
                outputFolder = new File(outputFolder, REPLAYS_MERGED);
            }
            if (ui.isCleanOutputSelected() && outputFolder.exists()) {
                ProcessingLogger.info("Cleaning output folder...");
                ReplayUtils.deleteProcessedReplaysInDirectory(outputFolder);
            }
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                ProcessingLogger.error("Failed to create output folder.");
                ui.setButtonProcessToInitialState();
                return;
            }
        }

        printAmountOfFilesToProcess();
        printOutputFolderPath();
        printSeparator();

        // Initialize processing configuration
        processingConfig = new ProcessingConfig();

        ReplayProcessor processor = new ReplayProcessor(
                outputFolder,
                inputFolder,
                ui.isReplaceOriginalReplaysSelected(),
                ui.isDeleteMicrophoneTracksSelected(),
                processingConfig
        );

        // Launch single-threaded sequential processing on a background thread to keep UI responsive
        processingThread = new Thread(() -> {
            processReplaysSequentially(processor, ui, startTime);
        });
        processingThread.setName("ReplayProcessingThread");
        processingThread.start();
    }

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
                    // Update UI to show processing status
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("🔁 " + replayFile.getName());
                    });

                    // Process the replay file once (no retries for local app)
                    processor.process(replayFile);

                    // Update UI to show success status
                    filesProcessedCount++;
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("✅ " + replayFile.getName());
                    });

                } catch (InterruptedException e) {
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
                    ProcessingLogger.error("Error processing: " + replayFile.getName() + " - " + e.getMessage(), e);
                    filesFailedCount++;
                    SwingUtilities.invokeLater(() -> {
                        ui.updateReplayStatusInList("❌ " + replayFile.getName());
                    });
                }

                // Update progress
                SwingUtilities.invokeLater(() -> {
                    updateProgressDisplay(ui, totalFiles);
                });
            }
        } finally {
            // Ensure graceful shutdown of processor
            processor.requestShutdown();

            // Finalize processing on the UI thread
            SwingUtilities.invokeLater(() -> {
                logFinalProcessingResult(this, startTime);
                ui.setButtonProcessToInitialState();

                if (!processingCancelled.get() && ui.isOpenOutputFolderSelected()) {
                    openOutputDirectory();
                }
            });
        }
    }

    private void updateProgressDisplay(ReliveTrackMergerUI ui, int totalFiles) {
        // Silent progress update - no debug logging needed for local app
    }

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

    public void setInputFolderAsOutputFolder() {
        outputFolder = inputFolder;
        ProcessingLogger.info("Output folder set to: " + outputFolder);
    }

    public void setOutputFolder(File file) {
        outputFolder = file;
        ProcessingLogger.info("Output folder set to: " + outputFolder);
    }

    private double getTotalSizeOfSelectedReplays() {
        long totalSizeInBytes = filesToProcess.stream()
                .mapToLong(File::length) // Get file size in bytes
                .sum();
        return totalSizeInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
    }

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
