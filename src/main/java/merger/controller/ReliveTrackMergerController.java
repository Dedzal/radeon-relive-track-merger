package merger.controller;

import merger.ffmpeg.FfmpegInstaller;
import merger.processing.ReplayProcessorWorker;
import merger.processing.ReplayProcessor;
import merger.ui.ReliveTrackMergerUI;
import merger.util.ReplayUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class ReliveTrackMergerController {

    public static final String OUTPUT_FOLDER_NAME = "replays_merged";

    private File inputFolder;
    private File outputFolder;
    private List<File> filesToProcess;

    public void selectInputFolder(ReliveTrackMergerUI ui, File selectedInputFolder) {
        if (selectedInputFolder == null) {
            inputFolder = null;
            ui.cleanTextFieldInputFolderPath();
            ui.disableButtonProcess();
        } else {
            inputFolder = selectedInputFolder;
            String inputFolderPath = inputFolder.getAbsolutePath();
            ui.setTextFieldInputFolderPath(inputFolderPath);

            if (outputFolder == null) {
                outputFolder = inputFolder;
                if (!ui.isCheckboxReplaceOriginalVideoInsteadOfCopyingSelected()) {
                    ui.setTextFieldOutputFolderPath(inputFolderPath + File.separator + OUTPUT_FOLDER_NAME);
                    ui.enableButtonSelectOutputFolder();
                } else {
                    ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath());
                    ui.disableButtonSelectOutputFolder();
                }

            }

            ui.enableButtonProcess();
            fetchReplaysToProcessAndUpdateView(ui);

            if (filesToProcess != null && !filesToProcess.isEmpty() && outputFolder != null) {
                printFileSizeInformation(getTotalSizeOfSelectedReplays(), getAvailableDiskSpaceOfOutputDirectory());
            }
        }
    }

    public void selectOutputFolder(ReliveTrackMergerUI ui, File selectedOutputFolder) {
        if (selectedOutputFolder != null) {
            outputFolder = selectedOutputFolder;
            ui.setTextFieldOutputFolderPath(outputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);
        }
    }

    private void fetchReplaysToProcessAndUpdateView(ReliveTrackMergerUI ui) {
        ui.clearVideoList();
        if (inputFolder != null && inputFolder.isDirectory()) {
            List<File> unprocessedReplays = ReplayUtils.findUnprocessedReplays(inputFolder);
            filesToProcess = unprocessedReplays.stream()
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());

            for (File replay : filesToProcess) {
                ui.addToVideoList(replay.getName());
            }

            ui.repaintVideoList();
        }
    }

    public void processReplays(ReliveTrackMergerUI ui) {
        long startTime = System.currentTimeMillis();

        try {
            FfmpegInstaller.checkOrInstallFfmpeg();
        } catch (IllegalStateException e) {
            return;
        }

        ui.cleanLogTextarea();

        if (filesToProcess == null || filesToProcess.isEmpty()) {
            System.out.println("No files to process");
            return;
        }

        ui.disableButtonProcess();

        // if we are not replacing the original replays, we need to create the output (replays_merged) folder
        if (!ui.isCheckboxReplaceOriginalVideoInsteadOfCopyingSelected()) {
            this.outputFolder = new File(this.outputFolder, OUTPUT_FOLDER_NAME);
            if (ui.isCheckboxCleanOutputSelected() && outputFolder.exists()) {
                System.out.println("Cleaning output folder...");
                ReplayUtils.deleteDirectory(outputFolder);
            }
            outputFolder.mkdirs();
        }

        double replaysSize = getTotalSizeOfSelectedReplays();
        double availableDiskSpace = getAvailableDiskSpaceOfOutputDirectory();
        if (replaysSize >= availableDiskSpace) {
            ui.showFileSizeWarningPane(replaysSize, availableDiskSpace);
            ui.enableButtonProcess();
            return;
        }

        printAmountOfFilesToProcess();
        printOutputFolderPath(outputFolder);
        printSeparator();

        CountDownLatch latch = new CountDownLatch(filesToProcess.size());
        ReplayProcessor processor = new ReplayProcessor(
                outputFolder,
                inputFolder,
                ui.isCheckboxReplaceOriginalVideoInsteadOfCopyingSelected(),
                ui.isCheckboxDeleteMicrophoneTracksAfterCopyingSelected()
        );


        filesToProcess.forEach(file -> {
            ReplayProcessorWorker worker = new ReplayProcessorWorker(
                    file,
                    processor,
                    ui::updateReplayStatusInList,
                    latch);
            worker.execute();
        });

        startFinishingLogThread(latch, startTime);

        ui.enableButtonProcess();
        if (ui.isCheckboxOpenOutputFolderSelected()) {
            openOutputDirectory();
        }

    }

    private static void startFinishingLogThread(CountDownLatch latch, long startTime) {
        new Thread(() -> {
            try {
                latch.await(); // Wait until all workers finish
                SwingUtilities.invokeLater(() -> {
                    System.out.println();
                    System.out.println("Done!");
                    System.out.println("Processing took a total of " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");
                });
            } catch (InterruptedException e) {
                System.err.println("Processing was interrupted!");
            }
        }).start();
    }

    private static void printFileSizeInformation(double replaysSize, double availableDiskSpace) {
        System.out.println("Total file size of selected replays: " + String.format("%.1f", replaysSize) + " GB");
        System.out.println("Available storage on disk: " + String.format("%.1f", availableDiskSpace) + " GB");
    }

    private void printAmountOfFilesToProcess() {
        System.out.println("Processing " + filesToProcess.size() + " file(s)");
    }

    private static void printOutputFolderPath(File outputFolder) {
        System.out.println("Output folder is: " + outputFolder.getAbsolutePath());
    }

    private static void printSeparator() {
        System.out.println();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println();
    }


    public File getInputFolder() {
        return inputFolder;
    }

    public File getOutputFolder() {
        return outputFolder;
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
        try {
            Desktop.getDesktop().open(outputFolder);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
