package merger.processing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ReplayProcessor {

    private final File outputDirectory;
    private final File inputDirectory;
    private final boolean replaceSourceReplays;
    private final boolean deleteMicrophoneTracks;

    public ReplayProcessor(File outputDirectory, File inputDirectory, boolean replaceSourceReplays, boolean deleteMicrophoneTracks) {
        this.outputDirectory = outputDirectory;
        this.inputDirectory = inputDirectory;
        this.replaceSourceReplays = replaceSourceReplays;
        this.deleteMicrophoneTracks = deleteMicrophoneTracks;
    }

    public void process(File replayFile) throws IOException, InterruptedException {
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

    private static String getFileNameWithoutExtension(String videoName) {
        return videoName.substring(0, videoName.lastIndexOf('.'));
    }

    private void handleReplayWithNoMicrophoneTrack(File replayFile, String replayName, File outputFile) throws IOException {
        if (!isReplaceSourceReplaysSelected()) {
            System.out.println("Replay does not contain a microphone track, copying to output folder - " + replayName);
            Files.copy(replayFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            System.out.println("Replay does not contain a microphone track, nothing to do! - " + replayName);
        }
    }

    private void embedMicrophoneTrackToReplay(File replayFile, String replayName, File microphoneTrack, File outputFile) throws IOException, InterruptedException {
        System.out.println("Processing replay: " + replayName);
        Process process = embedMicrophoneTrackToReplayAndSaveOutput(replayFile, microphoneTrack, outputFile);
        process.waitFor();

        if (isReplaceSourceReplaysSelected()) {
            /*
                Ffmpeg cannot change files in-place, that means we need to delete the old replay and rename it to
                the original replay name.
             */
            replaceSourceReplayWithProcessedReplay(replayFile, outputFile);
            deleteMicrophoneTrackIfSelected(microphoneTrack);
        }

        System.out.println("Replay: " + replayFile.getName() + " processed");
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

    private static void replaceSourceReplayWithProcessedReplay(File videoFile, File outputFile) {
        String originalReplayPath = videoFile.getAbsolutePath();
        videoFile.delete(); // delete original replay
        outputFile.renameTo(new File(originalReplayPath)); // rename new replay to the old replay
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
}