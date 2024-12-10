package merger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ReplayProcessor {

    private final File outputDirectory;

    public ReplayProcessor(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void process(File videoFile) throws IOException, InterruptedException {
        String videoName = videoFile.getName();
        String videoNameWithoutExtension = videoName.substring(0, videoName.lastIndexOf('.'));
        File microphoneTrack = new File(videoFile.getParent(), videoNameWithoutExtension + ".m4a");
        File outputFile = new File(outputDirectory, videoNameWithoutExtension + "_merged.mp4");

        if (microphoneTrack.exists()) {
            System.out.println("Processing file: " + videoName);
            Process process = new ProcessBuilder(
                    "ffmpeg",
                    "-i", videoFile.getAbsolutePath(),
                    "-i", microphoneTrack.getAbsolutePath(),
                    "-nostdin", "-y", "-map", "0", "-map", "1", "-c", "copy",
                    outputFile.getAbsolutePath()
            ).inheritIO().start();
            process.waitFor();
            System.out.println("File: " + videoFile.getName() + " processed");
        } else {
            System.out.println("File does not contain a microphone track, copying: " + videoName);
            Files.copy(videoFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}