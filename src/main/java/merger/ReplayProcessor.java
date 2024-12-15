package merger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ReplayProcessor {

    private final File outputDirectory;
    private final File inputDirectory;

    public ReplayProcessor(File outputDirectory, File inputDirectory) {
        this.outputDirectory = outputDirectory;
        this.inputDirectory = inputDirectory;
    }

    public void process(File videoFile) throws IOException, InterruptedException {
        String videoName = videoFile.getName();
        String videoNameWithoutExtension = videoName.substring(0, videoName.lastIndexOf('.'));
        File microphoneTrack = new File(videoFile.getParent(), videoNameWithoutExtension + ".m4a");

        String outputPath = outputDirectory.getAbsolutePath();
        if (isFromSubdirectory(videoFile)) {
            // if the replay was located in a subdirectory of the input directory, keep the same folder structure
            // at the output location
            outputPath = outputPath + File.separator + videoFile.getParentFile().getName();
        }

        File outputFile = new File(outputPath, videoNameWithoutExtension + "_merged.mp4");
        outputFile.getParentFile().mkdirs(); // create the parent folder if it doesn't exist

        if (microphoneTrack.exists()) {
            System.out.println("Processing file: " + videoName);
            Process process = new ProcessBuilder(
                    "ffmpeg",
                    "-i", videoFile.getAbsolutePath(),
                    "-i", microphoneTrack.getAbsolutePath(),
                    "-nostdin", "-y",
                    "-map", "0",
                    "-map", "1",
                    "-c", "copy",
                    outputFile.getAbsolutePath()
            ).inheritIO().start();
            process.waitFor();
            System.out.println("File: " + videoFile.getName() + " processed");
        } else {
            System.out.println("File does not contain a microphone track, copying: " + videoName);
            Files.copy(videoFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isFromSubdirectory(File videoFile) {
        return !videoFile.getParentFile().getName().equals(inputDirectory.getName());
    }
}