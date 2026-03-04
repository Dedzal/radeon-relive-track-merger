package merger.processing;

import merger.util.ProcessingLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReplayProcessor {

    private final File outputDirectory;
    private final File inputDirectory;
    private final boolean replaceSourceReplays;
    private final boolean deleteMicrophoneTracks;
    private final ProcessingConfig processingConfig;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private volatile Process currentProcess = null;

    public ReplayProcessor(File outputDirectory, File inputDirectory, boolean replaceSourceReplays, boolean deleteMicrophoneTracks, ProcessingConfig processingConfig) {
        this.outputDirectory = outputDirectory;
        this.inputDirectory = inputDirectory;
        this.replaceSourceReplays = replaceSourceReplays;
        this.deleteMicrophoneTracks = deleteMicrophoneTracks;
        this.processingConfig = processingConfig;
    }

    public void process(File replayFile) throws IOException, InterruptedException {
        // Check if shutdown was requested before starting
        if (shutdownRequested.get()) {
            throw new InterruptedException("Shutdown requested before processing file: " + replayFile.getName());
        }

        // Check and wait if pause is requested
        processingConfig.checkAndWaitIfPaused();

        // Validate available disk space before processing
        validateDiskSpaceBeforeProcessing(replayFile);

        String replayName = replayFile.getName();
        String replayNameWithoutExtension = getFileNameWithoutExtension(replayName);
        File microphoneTrack = new File(replayFile.getParent(), replayNameWithoutExtension + ".m4a");
        File outputFile = prepareOutputFile(replayFile, replayNameWithoutExtension);

        if (microphoneTrack.exists()) {
            embedMicrophoneTrackToReplay(replayFile, replayName, microphoneTrack, outputFile);
        } else {
            handleReplayWithNoMicrophoneTrack(replayFile, replayName, outputFile);
        }
    }

    public void requestShutdown() {
        shutdownRequested.set(true);
        // Gracefully terminate the current FFmpeg process if running
        if (currentProcess != null && currentProcess.isAlive()) {
            ProcessingLogger.info("Terminating current FFmpeg process...");
            currentProcess.destroy();
        }
    }

    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    private static String getFileNameWithoutExtension(String videoName) {
        return videoName.substring(0, videoName.lastIndexOf('.'));
    }

    private void handleReplayWithNoMicrophoneTrack(File replayFile, String replayName, File outputFile) throws IOException {
        if (!isReplaceSourceReplaysSelected()) {
            ProcessingLogger.info("Replay does not contain a microphone track, copying to output folder - " + replayName);
            Files.copy(replayFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            ProcessingLogger.info("Replay does not contain a microphone track, nothing to do! - " + replayName);
        }
    }

    private void embedMicrophoneTrackToReplay(File replayFile, String replayName, File microphoneTrack, File outputFile) throws IOException, InterruptedException {
        // Check if shutdown was requested before starting FFmpeg
        if (shutdownRequested.get()) {
            throw new InterruptedException("Shutdown requested before processing: " + replayName);
        }

        ProcessingLogger.info("Processing replay: " + replayName);
        long startTime = System.currentTimeMillis();
        currentProcess = embedMicrophoneTrackToReplayAndSaveOutput(replayFile, microphoneTrack, outputFile);

        try {
            // Wait for FFmpeg process to complete
            currentProcess.waitFor();
        } finally {
            currentProcess = null;
        }

        // Check if shutdown was requested during processing
        if (shutdownRequested.get()) {
            throw new InterruptedException("Shutdown requested during processing: " + replayName);
        }


        if (isReplaceSourceReplaysSelected()) {
            /*
                Ffmpeg cannot change files in-place, that means we need to delete the old replay and rename it to
                the original replay name.
             */
            replaceSourceReplayWithProcessedReplay(replayFile, outputFile);
            deleteMicrophoneTrackIfSelected(microphoneTrack);
        }

        long processingTimeMs = System.currentTimeMillis() - startTime;
        ProcessingLogger.info("Replay: " + replayFile.getName() + " processed in " + (processingTimeMs / 1000.0) + " seconds");
    }

    private void deleteMicrophoneTrackIfSelected(File microphoneTrack) {
        if (isDeleteMicrophoneTracksSelected()) {
            microphoneTrack.delete();
        }
    }

    private File prepareOutputFile(File videoFile, String videoNameWithoutExtension) {
        String outputPath;
        if (isReplaceSourceReplaysSelected()) {
            outputPath = videoFile.getParent() + File.separator + videoNameWithoutExtension + "_temp.mp4";
        } else {
            outputPath = outputDirectory.getAbsolutePath();
            if (isFromSubdirectory(videoFile)) {
                /*
                    If the replay was located in a subdirectory (input_directory/game/replay),
                    retain the same folder structure at the output location (output_directory/game/processed_replay)
                 */
                outputPath = outputPath + File.separator + videoFile.getParentFile().getName();
            }
            outputPath = outputPath + File.separator + videoNameWithoutExtension + "_merged.mp4";
        }

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs(); // create the parent folder if it doesn't exist
        return outputFile;
    }

    private static void replaceSourceReplayWithProcessedReplay(File unprocessedReplay, File processedReplay) {
        String unprocessedReplayPath = unprocessedReplay.getAbsolutePath();
        unprocessedReplay.delete(); // delete original replay
        processedReplay.renameTo(new File(unprocessedReplayPath)); // rename new replay to the old replay
    }

    private static Process embedMicrophoneTrackToReplayAndSaveOutput(File videoFile, File microphoneTrack, File outputFile) throws IOException {
        return new ProcessBuilder(
                "ffmpeg",
                "-i", videoFile.getAbsolutePath(),          // input video file
                "-i", microphoneTrack.getAbsolutePath(),    // input microphone track
                "-nostdin", "-y",                            // auto-yes to overwrite inputs
                "-map", "0",                                // map input video stream
                "-map", "1",                                // map input microphone stream
                "-c", "copy",                               // copy streams without re-encoding
                outputFile.getAbsolutePath()                // save result to outputFile path
        ).inheritIO().start();
    }

    private boolean isReplaceSourceReplaysSelected() {
        return replaceSourceReplays;
    }

    private boolean isDeleteMicrophoneTracksSelected() {
        return deleteMicrophoneTracks;
    }

    private boolean isFromSubdirectory(File videoFile) {
        return !videoFile.getParentFile().getName().equals(inputDirectory.getName());
    }

    private void validateDiskSpaceBeforeProcessing(File replayFile) throws IOException {
        if (outputDirectory == null) {
            return;
        }

        long availableSpace = outputDirectory.getFreeSpace();
        long requiredSpace = replayFile.length() * 2; // Need space for both input and output during processing
        long minRequiredSpace = ProcessingConfig.MIN_FREE_SPACE_MB * 1024 * 1024;

        if (requiredSpace > availableSpace) {
            String availableMB = String.format("%.1f", availableSpace / (1024.0 * 1024.0));
            String requiredMB = String.format("%.1f", requiredSpace / (1024.0 * 1024.0));
            throw new IOException("Insufficient disk space. Required: " + requiredMB + " MB, Available: " + availableMB + " MB");
        }

        if (availableSpace < minRequiredSpace) {
            ProcessingLogger.warn("Low disk space: " + String.format("%.1f", availableSpace / (1024.0 * 1024.0)) + " MB remaining");
        }
    }

    public ProcessingConfig getProcessingConfig() {
        return processingConfig;
    }
}