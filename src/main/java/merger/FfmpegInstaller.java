package merger;

import javax.swing.*;
import java.io.*;

public class FfmpegInstaller {

    /**
     * Detects if ffmpeg is installed by attempting to execute the `ffmpeg -version` command.
     *
     * @return true if ffmpeg is installed, false otherwise
     */
    public static boolean isFfmpegInstalled() {
        try {
            // Try to execute `ffmpeg -version` to confirm installation
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true) // Combine error and output streams
                    .start();

            // Wait for the command to complete
            int exitCode = process.waitFor();
            return exitCode == 0; // Success indicates ffmpeg is installed
        } catch (Exception e) {
            return false; // If an exception occurs, ffmpeg is not installed
        }
    }

    /**
     * Installs ffmpeg using Windows Package Manager (winget).
     */
    public static void installFfmpegUsingWinget() {
        try {
            // Inform the user about the installation
            System.out.println("Installing ffmpeg using winget...");

            // Execute the `winget` command to install ffmpeg
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "winget",
                    "install",
                    "--id",
                    "Gyan.FFmpeg", // FFmpeg package from winget repository
                    "--silent"
            );

            // Start the process
            Process process = processBuilder
                    .redirectErrorStream(true) // Combine error stream into the output stream
                    .start();

            // Display the output of the command in real-time
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line); // Log the output of the command
                }
            }

            // Wait for the process to finish
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(
                        null,
                        "FFmpeg installation completed successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE
                );
                System.out.println("FFmpeg installed successfully.");
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "FFmpeg installation failed.\nPlease try installing it manually.",
                        "Installation Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                System.err.println("FFmpeg installation failed with exit code: " + exitCode);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace(); // Show error stack trace for debugging
            JOptionPane.showMessageDialog(
                    null,
                    "An error occurred while attempting to install ffmpeg.\n" +
                            "Please install it manually using the following command:\n" +
                            "\"winget install --id Gyan.FFmpeg\"",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Checks if ffmpeg is installed, and installs it if it is missing.
     */
    public static void checkOrInstallFfmpeg() {
        if (isFfmpegInstalled()) {
            int choice = JOptionPane.showConfirmDialog(
                    null,
                    "FFmpeg is not installed on your system. Would you like to install it now using winget?",
                    "FFmpeg Missing",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                installFfmpegUsingWinget();
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "FFmpeg must be installed to proceed.\nVisit https://ffmpeg.org/download.html for manual installation.",
                        "FFmpeg Required",
                        JOptionPane.WARNING_MESSAGE
                );
                throw new IllegalStateException("FFmpeg is not installed.");
            }
        } else {
            System.out.println("FFmpeg is already installed and ready to use.");
        }
    }
}