package merger.controller;

import merger.ffmpeg.FfmpegInstaller;
import merger.processing.ReplayProcessor;
import merger.processing.ReplayProcessorWorker;
import merger.ui.ReliveTrackMergerUI;
import merger.util.ReplayUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ReliveTrackMergerController {

    public static final String OUTPUT_FOLDER_NAME = "replays_merged";

    private File inputFolder;
    private File outputFolder;
    private List<File> filesToProcess;

    private volatile AtomicBoolean processingCancelled = new AtomicBoolean(false);
    private volatile List<ReplayProcessorWorker> workers = new CopyOnWriteArrayList<>();
    private CountDownLatch latch;

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
            System.out.println("No replays found in selected directory or any of its subdirectories");
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

        long startTime = System.currentTimeMillis();
        ui.cleanLogTextarea();

        try {
            FfmpegInstaller.checkOrInstallFfmpeg();
        } catch (IllegalStateException e) {
            System.err.println("FFmpeg installation failed: " + e.getMessage());
            ui.setButtonProcessToInitialState();
            return;
        }

        // if we are not replacing the original replays, we need to create the output (replays_merged) folder
        if (isProcessingWithOutputFolderCreation(ui) && outputFolder != null) {
            if (!outputFolder.getAbsolutePath().endsWith(OUTPUT_FOLDER_NAME)) {
                outputFolder = new File(outputFolder, OUTPUT_FOLDER_NAME);
            }
            if (ui.isCleanOutputSelected() && outputFolder.exists()) {
                System.out.println("Cleaning output folder...");
                ReplayUtils.deleteProcessedReplaysInDirectory(outputFolder);
            }
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                System.err.println("Failed to create output folder.");
                ui.setButtonProcessToInitialState();
                return;
            }
        }

        printAmountOfFilesToProcess();
        printOutputFolderPath();
        printSeparator();

        latch = new CountDownLatch(filesToProcess.size());
        ReplayProcessor processor = new ReplayProcessor(
                outputFolder,
                inputFolder,
                ui.isReplaceOriginalReplaysSelected(),
                ui.isDeleteMicrophoneTracksSelected()
        );

        workers = filesToProcess.stream().map(file -> new ReplayProcessorWorker(
                file,
                processor,
                ui::updateReplayStatusInList,
                latch)
        ).collect(Collectors.toCollection(CopyOnWriteArrayList::new));

        for (ReplayProcessorWorker worker : workers) {
            worker.execute();
        }

        finalizeProcessing(this, latch, startTime, ui);
    }

    private void validateStorageSpaceAtOutputDisk(ReliveTrackMergerUI ui) {
        if (inputFolder != null && outputFolder != null && filesToProcess != null && !filesToProcess.isEmpty()) {
            double totalSizeOfReplays = getTotalSizeOfSelectedReplays();
            double availableDiskSpace = getAvailableDiskSpaceOfOutputDirectory();
            if (totalSizeOfReplays >= availableDiskSpace) {
                System.out.println("The total file size of the selected replays (" + String.format("%.1f", totalSizeOfReplays) + " GB) exceeds the available disk space (" + String.format("%.1f", availableDiskSpace) + " GB).");
                System.out.println("Please free up some space on the target disk or select a different output directory.");
                ui.disableButtonProcess();
            } else {
                ui.enableButtonProcess();
            }
        }
    }

    public void cancelReplayProcessing() {
        if (workers != null && !workers.isEmpty()) {
            System.out.println("Cancelling replay processing...");

            processingCancelled.set(true);
            for (ReplayProcessorWorker worker : workers) {
                worker.cancel(true);
            }
            releaseLatch(latch);
        }
    }

    private void releaseLatch(CountDownLatch latch) {
        while (latch.getCount() > 0) {
            latch.countDown();
        }
    }

    private static void finalizeProcessing(ReliveTrackMergerController controller, CountDownLatch latch, long startTime, ReliveTrackMergerUI ui) {
        new Thread(() -> {
            try {
                latch.await(); // Wait until all workers finish
                SwingUtilities.invokeLater(() -> {
                    logFinalProcessingResult(controller, startTime);
                    ui.setButtonProcessToInitialState();
                });

                if (ui.isOpenOutputFolderSelected()) {
                    controller.openOutputDirectory();
                }

            } catch (InterruptedException e) {
                System.err.println("Processing was interrupted!");
            }
        }).start();
    }

    private static void logFinalProcessingResult(ReliveTrackMergerController controller, long startTime) {
        if (controller.isProcessingCancelled()) {
            System.out.println("Replay processing stopped");
        } else {
            System.out.println();
            System.out.println("Done!");
            System.out.println("Processing took a total of " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");
        }
    }

    private void displayFileSizeInfo() {
        if (inputFolder != null && outputFolder != null && filesToProcess != null && !filesToProcess.isEmpty()) {
            System.out.println("Total file size of selected replays: " + String.format("%.1f", getTotalSizeOfSelectedReplays()) + " GB");
            System.out.println("Available storage on disk: " + String.format("%.1f", getAvailableDiskSpaceOfOutputDirectory()) + " GB");
        }
    }

    private void printAmountOfFilesToProcess() {
        System.out.println("Processing " + filesToProcess.size() + " file(s)");
    }

    private void printOutputFolderPath() {
        System.out.println("Output folder is: " + outputFolder.getAbsolutePath());
    }

    private void printSeparator() {
        System.out.println();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println();
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
