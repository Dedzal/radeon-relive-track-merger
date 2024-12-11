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
     * Installs ffmpeg using Windows Package Manager (winget) in a separate Command Prompt window.
     */
    public static void installFfmpegUsingWinget() {
        try {
            // Inform the user about the installation
            System.out.println("Installing ffmpeg using winget in a separate Command Prompt window...");

            // Execute the `winget` command in a new Command Prompt (cmd) window
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd", "/c", "start", "cmd", "/k",
                    "winget install --id Gyan.FFmpeg --silent"
            );

            // Start the process, which opens a new cmd window and runs the command
            processBuilder.start();

            JOptionPane.showMessageDialog(
                    null,
                    "The FFmpeg installation process is running in a new Command Prompt window.\n" +
                            "Please check the window for progress.",
                    "Installation Started",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "An error occurred while attempting to start the FFmpeg installation.\n" +
                            "Please install it manually using the following command:\n" +
                            "\"winget install ffmpeg\"",
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
            }
            throw new IllegalStateException("FFmpeg is not installed.");
        } else {
            System.out.println("FFmpeg is already installed and ready to use.");
        }
    }
}