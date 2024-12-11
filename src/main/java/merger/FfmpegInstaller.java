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
        return getFfmpegVersion() != null;
    }

    public static String getFfmpegVersion() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();

            // Read the output from the process
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String firstLine = reader.readLine(); // Read only the first line

            // Close resources
            reader.close();
            int exitCode = process.waitFor();// Wait for the process to complete
            if (exitCode != 0) {
                return null;
            }

            return firstLine;
        } catch (Exception e) {
            System.out.println("Failed to get ffmpeg version");
            return null;
        }
    }

    /**
     * Installs ffmpeg using Windows Package Manager (winget) in a separate Command Prompt window.
     */
    public static void installFfmpegUsingWinget() {
        try {
            System.out.println("Installing ffmpeg using winget in a Command Prompt with elevated privileges...");

            String command = "powershell -Command \"Start-Process cmd -Verb runAs -Wait -ArgumentList '/c winget install ffmpeg && timeout 5'\"";

            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
            Process process = processBuilder.start();

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                if (isFfmpegInstalled()) {
                    System.out.println("FFmpeg installed successfully!");
                    JOptionPane.showMessageDialog(
                            null,
                            "FFmpeg has been installed successfully and is ready to use.",
                            "Installation Complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } else {
                    System.out.println("FFmpeg installation failed. Please check the Command Prompt window for errors.");
                    JOptionPane.showMessageDialog(
                            null,
                            "FFmpeg installation failed. Please try installing it manually using the following command:\n" +
                                    "\"winget install ffmpeg\"",
                            "Installation Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            } else {
                System.out.println("The installation process did not complete successfully.");
                JOptionPane.showMessageDialog(
                        null,
                        "FFmpeg installation encountered an error. Please check the Command Prompt window for details.",
                        "Installation Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }

        } catch (IOException | InterruptedException e) {
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
        if (!isFfmpegInstalled()) {
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
            System.out.println("Using " + getFfmpegVersion());
        }
    }
}