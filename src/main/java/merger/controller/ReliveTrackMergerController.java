package merger.controller;

import merger.ffmpeg.FfmpegInstaller;
import merger.processing.ProcessingConfig;
import merger.processing.ReplayProcessor;
import merger.ui.ReliveTrackMergerUI;
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

    public static final String OUTPUT_FOLDER_NAME = "replays_merged";

    private File inputFolder;
    private File outputFolder;
    private List<File> filesToProcess;

    private volatile AtomicBoolean processingCancelled = new AtomicBoolean(false);
    private volatile AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private volatile AtomicBoolean pauseRequested = new AtomicBoolean(false);
    private Thread processingThread;
    private ProcessingConfig processingConfig;
    private long processingStartTime = 0;
    private int filesProcessedCount = 0;
    private int filesFailedCount = 0;

    public void selectInputFolder(ReliveTrackMergerUI ui, File selectedInputFolder) {
        ui.cleanLogTextarea();

        if (selectedInputFolder == null) {
            inputFolder = null;
            ui.cleanTextFieldInputFolderPath();
            ui.disableButtonProcess();
        } else {
            inputFolder = selectedInputFolder;
            String inputFolderPath = inputFolder.getAbsolutePath();
            ui.setTextFieldInputFolderPath(inputFolderPath);

            if (outputFolder == null) {
                /*
                    If no output folder has previously been selected, it is automatically set to
                    input_folder/OUTPUT_FOLDER_NAME
                 */
                setInputFolderAsOutputFolder();

                /*
                    If the user chose not to replace the original replays, processed replays are put in a new folder
                    called OUTPUT_FOLDER_NAME at the selected output directory.

                    Otherwise, if the user chooses to replace the original replays with processed ones, the input
                    folder is also the output folder. Both paths in the text fields are equal in this case.
                 */
                if (isProcessingWithOutputFolderCreation(ui)) {
                    ui.setTextFieldOutputFolderPath(inputFolderPath + File.separator + OUTPUT_FOLDER_NAME);
                    ui.enableButtonSelectOutputFolder();
                } else {
                    ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath());
                    ui.disableButtonSelectOutputFolder();
                }

            }

            updateReplayListAndView(ui);
            validateReplaysToProcessFound(ui);
            validateEnoughStorageAvailableForProcessing(ui);
        }
    }

    private static boolean isProcessingWithOutputFolderCreation(ReliveTrackMergerUI ui) {
        return !ui.isReplaceOriginalReplaysSelected();
    }

    public void selectOutputFolder(ReliveTrackMergerUI ui, File selectedOutputFolder) {
        if (selectedOutputFolder != null) {
            outputFolder = selectedOutputFolder;
            ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);

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
        pauseRequested.set(false);
        filesProcessedCount = 0;
        filesFailedCount = 0;

        long startTime = System.currentTimeMillis();
        processingStartTime = startTime;
        ui.cleanLogTextarea();

        try {
            FfmpegInstaller.checkOrInstallFfmpeg();
        } catch (IllegalStateException e) {
            ProcessingLogger.error("FFmpeg installation failed: " + e.getMessage(), e);
            ui.setButtonProcessToInitialState();
            return;
        }

        // if we are not replacing the original replays, we need to create the output (replays_merged) folder
        if (isProcessingWithOutputFolderCreation(ui) && outputFolder != null) {
            if (!outputFolder.getAbsolutePath().endsWith(OUTPUT_FOLDER_NAME)) {
                outputFolder = new File(outputFolder, OUTPUT_FOLDER_NAME);
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
            pauseRequested.set(true);
        }
    }

    public void resumeReplayProcessing() {
        if (processingConfig != null && processingThread != null && processingThread.isAlive()) {
            ProcessingLogger.info("Resuming replay processing...");
            processingConfig.resume();
            pauseRequested.set(false);
        }
    }

    public boolean isPauseRequested() {
        return pauseRequested.get();
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
        int filesRemaining = totalFiles - filesProcessedCount - filesFailedCount;
        long elapsedTimeMs = System.currentTimeMillis() - processingStartTime;
        long estimatedTotalTimeMs = (filesProcessedCount > 0) ? (elapsedTimeMs * totalFiles) / (filesProcessedCount) : -1;
        long estimatedRemainingMs = (estimatedTotalTimeMs > 0) ? estimatedTotalTimeMs - elapsedTimeMs : -1;
        long estimatedRemainingMin = (estimatedRemainingMs > 0) ? estimatedRemainingMs / 60000 : -1;

        ProcessingLogger.debug("Progress: " + filesProcessedCount + " completed, " + filesFailedCount + " failed, " + filesRemaining + " remaining, ETA: " +
                (estimatedRemainingMin > 0 ? estimatedRemainingMin + " min" : "calculating..."));
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
    }

    private double getTotalSizeOfSelectedReplays() {
        long totalSizeInBytes = filesToProcess.stream()
                .mapToLong(File::length) // Get file size in bytes
                .sum();
        return totalSizeInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
    }

    private double getAvailableDiskSpaceOfOutputDirectory() {
        if (outputFolder != null) {
            long freeSpaceInBytes = outputFolder.getFreeSpace(); // Returns free space in bytes
            return freeSpaceInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
        }
        return 0.0;
    }

    private void openOutputDirectory() {
        if (outputFolder != null && outputFolder.exists()) {
            openFile(outputFolder);
        } else {
            System.err.println("Output folder does not exist or is not set.");
        }
    }

    public void openReplay(String replayName) {
        if (replayName != null && !filesToProcess.isEmpty()) {
            filesToProcess.stream()
                    .filter(replay -> replay.getName().equals(replayName))
                    .findFirst().ifPresent(this::openFile);
        }
    }

    private void openFile(File file) {
        if (file == null || !file.exists()) {
            System.err.println("File does not exist or is null: " + file);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                System.err.println("Desktop API is not supported on this system.");
                return;
            }
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            System.err.println("Failed to open file: " + e.getMessage());
        }
    }
}
