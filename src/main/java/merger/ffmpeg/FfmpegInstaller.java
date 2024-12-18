package merger.ffmpeg;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class FfmpegInstaller {

    public static boolean isFfmpegInstalled() {
        return getFfmpegVersion() != null;
    }

    public static String getFfmpegVersion() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();

            // Only get the first line of the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String firstLine = reader.readLine();
            reader.close();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // if the process failed, the ffmpeg command could probably not be found
                System.out.println("Failed to get ffmpeg version. Version check returned first line: ");
                System.out.println(firstLine);
                return null;
            }

            // if the process succeeded, the first line contains the version information of ffmpeg
            return firstLine;
        } catch (Exception e) {
            System.out.println("Failed to get ffmpeg version due to exception: " + e.getMessage());
            return null;
        }
    }

    public static void installFfmpegUsingWinget() {
        try {
            System.out.println("Installing ffmpeg using winget in a Command Prompt with elevated privileges...");

            /*
                Start a cmd process WITH ADMIN RIGHTS to install ffmpeg. The installation can also complete without
                using admin rights, but ffmpeg won't be added to the PATH environment variable. If the cmd process is
                started with admin rights, the installation process automatically adds ffmpeg to the PATH.
             */
            String command = "powershell -Command \"Start-Process cmd -Verb runAs -Wait -ArgumentList '/c winget install ffmpeg && timeout 5'\"";
            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
            Process process = processBuilder.start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("FFmpeg installed successfully!");
                showFfmpegInstallationSuccessfulDialog();
            } else {
                System.out.println("The installation process did not complete successfully.");
                showFfmpegInstallationFailedDialog();
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            showFFmpegInstallationInitFailedDialog();
        }

        System.exit(0);
    }

    public static void checkOrInstallFfmpeg() {
        if (!isFfmpegInstalled()) {
            int choice = showFfmpegInstallationPromptDialog();
            if (choice == JOptionPane.YES_OPTION) {
                installFfmpegUsingWinget();
            } else {
                showFfmpegInstallRejectedDialog();
            }
        } else {
            System.out.println("Using " + getFfmpegVersion());
        }
    }

    private static void showFFmpegInstallationInitFailedDialog() {
        JOptionPane.showMessageDialog(
                null,
                "An error occurred while attempting to start the FFmpeg installation.\n" +
                        "Please install it manually using the following command:\n" +
                        "\"winget install ffmpeg\"",
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private static void showFfmpegInstallationFailedDialog() {
        JOptionPane.showMessageDialog(
                null,
                "FFmpeg installation encountered an error. Please check the Command Prompt window for details.",
                "Installation Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private static void showFfmpegInstallationSuccessfulDialog() {
        JOptionPane.showMessageDialog(
                null,
                "FFmpeg has been installed successfully.\nIn order for it to take effect, please restart the application.",
                "Installation Complete",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static int showFfmpegInstallationPromptDialog() {
        return JOptionPane.showConfirmDialog(
                null,
                "FFmpeg is not installed on your system. Would you like to install it now using winget?",
                "FFmpeg Missing",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
    }

    private static void showFfmpegInstallRejectedDialog() {
        JOptionPane.showMessageDialog(
                null,
                "FFmpeg must be installed to proceed.\nVisit https://ffmpeg.org/download.html for manual installation.",
                "FFmpeg Required",
                JOptionPane.WARNING_MESSAGE
        );
        throw new IllegalStateException("FFmpeg is not installed.");
    }
}