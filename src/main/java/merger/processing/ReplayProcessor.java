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

    public void process(File videoFile) throws IOException, InterruptedException {
        String videoName = videoFile.getName();
        String videoNameWithoutExtension = videoName.substring(0, videoName.lastIndexOf('.'));
        File microphoneTrack = new File(videoFile.getParent(), videoNameWithoutExtension + ".m4a");

        String outputPath;
        if (isReplaceSourceReplaysSelected()) {
            outputPath = videoFile.getParent() + File.separator + videoNameWithoutExtension + "_temp.mp4";
        } else {
            outputPath = outputDirectory.getAbsolutePath();
            if (isFromSubdirectory(videoFile)) {
                // if the replay was located in a subdirectory of the input directory, keep the same folder structure
                // at the output location
                outputPath = outputPath + File.separator + videoFile.getParentFile().getName();
            }
            outputPath = outputPath + File.separator + videoNameWithoutExtension + "_merged.mp4";
        }

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs(); // create the parent folder if it doesn't exist

        if (microphoneTrack.exists()) {
            System.out.println("Processing file: " + videoName);
            Process process = startFfmpegAudioMergingProcess(videoFile, microphoneTrack, outputFile);
            process.waitFor();

            if (isReplaceSourceReplaysSelected()) {
                String originalReplayPath = videoFile.getAbsolutePath();
                videoFile.delete(); // delete original file
                outputFile.renameTo(new File(originalReplayPath));

                if (isDeleteMicrophoneTracksSelected()) {
                    microphoneTrack.delete();
                }
            }

            System.out.println("File: " + videoFile.getName() + " processed");
        } else {
            if (!isReplaceSourceReplaysSelected()) {
                System.out.println("File does not contain a microphone track, copying: " + videoName);
                Files.copy(videoFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                System.out.println("File does not contain a microphone track, nothing to do!: " + videoName);
            }
        }
    }

    private static Process startFfmpegAudioMergingProcess(File videoFile, File microphoneTrack, File outputFile) throws IOException {
        Process process = new ProcessBuilder(
                "ffmpeg",
                "-i", videoFile.getAbsolutePath(),          // input video file
                "-i", microphoneTrack.getAbsolutePath(),    // input microphone track
                "-nostdin", "-y",                            // auto-yes to overwrite
                "-map", "0",                                // map input video stream
                "-map", "1",                                // map input microphone stream
                "-c", "copy",                               // copy streams without re-encoding
                outputFile.getAbsolutePath()                 // overwrite the original video file
        ).inheritIO().start();
        return process;
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