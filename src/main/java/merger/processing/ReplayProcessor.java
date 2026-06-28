package merger.processing;

import merger.util.ProcessingLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges a Radeon ReLive replay video with its separately-recorded microphone track
 * by remuxing them into a single MP4 using FFmpeg (stream copy, no re-encoding).
 * <p>
 * ReLive saves the gameplay capture (e.g. {@code foo_replay_01.mp4}) and the microphone
 * audio (e.g. {@code foo_replay_01.m4a}) as two separate files. This class locates the
 * matching microphone track for a replay and embeds it as an additional audio stream.
 * <p>
 * Two output modes are supported, controlled by {@code replaceSourceReplays}:
 * <ul>
 *     <li><b>Replace mode:</b> the merged file is written to a temporary file and then
 *         atomically moved over the original replay. The microphone track is optionally
 *         deleted afterwards.</li>
 *     <li><b>Copy mode:</b> the merged file is written to {@code outputDirectory} (mirroring
 *         any subdirectory structure of the input) with a {@code _merged} suffix, leaving
 *         the source files untouched.</li>
 * </ul>
 * Replays without a matching microphone track are copied to the output folder (copy mode)
 * or left in place (replace mode).
 * <p>
 * A single instance processes files sequentially on one background thread.
 * {@link #requestShutdown()} may be called from another thread to abort processing and
 * terminate the running FFmpeg process gracefully.
 */
public class ReplayProcessor {

    private static final String MICROPHONE_TRACK_EXTENSION = ".m4a";
    private static final String MERGED_REPLAY_SUFFIX = "_merged.mp4";
    private static final String TEMP_REPLAY_SUFFIX = "_temp.mp4";

    /** Multiplier applied to a replay's size to estimate peak disk usage (input + output coexist briefly). */
    private static final int DISK_SPACE_SAFETY_MULTIPLIER = 2;

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

    /**
     * Processes a single replay file: merges its microphone track if one exists, otherwise
     * copies (copy mode) or leaves it untouched (replace mode).
     *
     * @param replayFile the replay video to process
     * @throws InterruptedException if a shutdown was requested before or during processing
     * @throws IOException          if disk space is insufficient, file operations fail, or FFmpeg fails
     */
    public void process(File replayFile) throws IOException, InterruptedException {
        if (shutdownRequested.get()) {
            throw new InterruptedException("Shutdown requested before processing file: " + replayFile.getName());
        }

        processingConfig.checkAndWaitIfPaused();
        validateDiskSpaceBeforeProcessing(replayFile);

        String replayName = replayFile.getName();
        String replayNameWithoutExtension = getFileNameWithoutExtension(replayName);
        File microphoneTrack = new File(replayFile.getParent(), replayNameWithoutExtension + MICROPHONE_TRACK_EXTENSION);
        File outputFile = prepareOutputFile(replayFile, replayNameWithoutExtension);

        if (microphoneTrack.exists()) {
            mergeMicrophoneTrackIntoReplay(replayFile, microphoneTrack, outputFile);
        } else {
            handleReplayWithNoMicrophoneTrack(replayFile, replayName, outputFile);
        }
    }

    /**
     * Signals that processing should stop and terminates the running FFmpeg process, if any.
     * Safe to call from a thread other than the one running {@link #process(File)}.
     */
    public void requestShutdown() {
        shutdownRequested.set(true);
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            ProcessingLogger.info("Terminating current FFmpeg process...");
            process.destroy();
        }
    }

    private void handleReplayWithNoMicrophoneTrack(File replayFile, String replayName, File outputFile) throws IOException {
        if (replaceSourceReplays) {
            ProcessingLogger.info("Replay does not contain a microphone track, nothing to do! - " + replayName);
        } else {
            ProcessingLogger.info("Replay does not contain a microphone track, copying to output folder - " + replayName);
            Files.copy(replayFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void mergeMicrophoneTrackIntoReplay(File replayFile, File microphoneTrack, File outputFile) throws IOException, InterruptedException {
        if (shutdownRequested.get()) {
            throw new InterruptedException("Shutdown requested before processing: " + replayFile.getName());
        }

        ProcessingLogger.info("Processing replay: " + replayFile.getName());
        long startTime = System.currentTimeMillis();

        int exitCode;
        currentProcess = startFfmpegMerge(replayFile, microphoneTrack, outputFile);
        try {
            exitCode = currentProcess.waitFor();
        } finally {
            currentProcess = null;
        }

        // A shutdown destroys the FFmpeg process (yielding a non-zero exit code); treat that as
        // an interruption rather than a processing failure, and never touch the source files.
        if (shutdownRequested.get()) {
            throw new InterruptedException("Shutdown requested during processing: " + replayFile.getName());
        }

        // Guard against replacing a good source replay with a partial/corrupt output: only proceed
        // once FFmpeg reports success.
        if (exitCode != 0) {
            throw new IOException("FFmpeg failed with exit code " + exitCode + " for replay: " + replayFile.getName());
        }

        if (replaceSourceReplays) {
            // FFmpeg cannot edit a file in place, so it wrote to a temp file; move it over the original.
            replaceSourceReplayWithProcessedReplay(replayFile, outputFile);
            deleteMicrophoneTrackIfSelected(microphoneTrack);
        }

        double processingTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        ProcessingLogger.info("Replay: " + replayFile.getName() + " processed in " + processingTimeSeconds + " seconds");
    }

    private void deleteMicrophoneTrackIfSelected(File microphoneTrack) {
        if (deleteMicrophoneTracks && !microphoneTrack.delete()) {
            ProcessingLogger.warn("Failed to delete microphone track: " + microphoneTrack.getAbsolutePath());
        }
    }

    /**
     * Computes the output file for a replay and ensures its parent directory exists.
     * <p>
     * In replace mode the output is a temporary file alongside the source. In copy mode it is
     * placed under {@code outputDirectory}, preserving any one-level subdirectory the replay
     * came from (e.g. {@code input/game/replay.mp4} -> {@code output/game/replay_merged.mp4}).
     */
    private File prepareOutputFile(File replayFile, String replayNameWithoutExtension) throws IOException {
        String outputPath;
        if (replaceSourceReplays) {
            outputPath = replayFile.getParent() + File.separator + replayNameWithoutExtension + TEMP_REPLAY_SUFFIX;
        } else {
            outputPath = outputDirectory.getAbsolutePath();
            if (isFromSubdirectory(replayFile)) {
                outputPath = outputPath + File.separator + replayFile.getParentFile().getName();
            }
            outputPath = outputPath + File.separator + replayNameWithoutExtension + MERGED_REPLAY_SUFFIX;
        }

        File outputFile = new File(outputPath);
        File parentDirectory = outputFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.isDirectory() && !parentDirectory.mkdirs()) {
            throw new IOException("Failed to create output directory: " + parentDirectory.getAbsolutePath());
        }
        return outputFile;
    }

    private static void replaceSourceReplayWithProcessedReplay(File originalReplay, File processedReplay) throws IOException {
        // Files.move replaces the original in a single step, avoiding the data-loss window of a
        // separate delete-then-rename (where a failed rename would leave no replay at all).
        Files.move(processedReplay.toPath(), originalReplay.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Process startFfmpegMerge(File replayFile, File microphoneTrack, File outputFile) throws IOException {
        return new ProcessBuilder(
                "ffmpeg",
                "-i", replayFile.getAbsolutePath(),         // input replay video
                "-i", microphoneTrack.getAbsolutePath(),    // input microphone track
                "-nostdin", "-y",                           // never read stdin; overwrite the output file if present
                "-map", "0",                                // include all streams from the replay
                "-map", "1",                                // include all streams from the microphone track
                "-c", "copy",                               // remux only — no re-encoding
                outputFile.getAbsolutePath()
        ).inheritIO().start();
    }

    private boolean isFromSubdirectory(File replayFile) {
        return !replayFile.getParentFile().getName().equals(inputDirectory.getName());
    }

    /**
     * Verifies there is enough free space at the output location before processing, since merging
     * briefly requires both the input and output files to exist at once.
     */
    private void validateDiskSpaceBeforeProcessing(File replayFile) throws IOException {
        if (outputDirectory == null) {
            return;
        }

        long availableSpace = outputDirectory.getFreeSpace();
        long requiredSpace = replayFile.length() * DISK_SPACE_SAFETY_MULTIPLIER;
        long minRequiredSpace = ProcessingConfig.MIN_FREE_SPACE_MB * 1024 * 1024;

        if (requiredSpace > availableSpace) {
            throw new IOException("Insufficient disk space. Required: " + toMegabytes(requiredSpace)
                    + " MB, Available: " + toMegabytes(availableSpace) + " MB");
        }

        if (availableSpace < minRequiredSpace) {
            ProcessingLogger.warn("Low disk space: " + toMegabytes(availableSpace) + " MB remaining");
        }
    }

    private static String toMegabytes(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }

    private static String getFileNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex < 0 ? fileName : fileName.substring(0, lastDotIndex);
    }
}
