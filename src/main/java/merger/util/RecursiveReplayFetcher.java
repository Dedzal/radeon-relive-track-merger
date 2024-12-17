package merger.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for recursively fetching unprocessed replays from a root directory.
 */
public class RecursiveReplayFetcher {

    /**
     * Fetch replays recursively from the given folder
     *
     * @param folder The root folder to search in.
     * @return List of filtered replays.
     */
    public static List<File> fetchUnprocessedFiles(File folder) {
        List<File> matchingFiles = new ArrayList<>();
        searchFilesRecursively(folder, matchingFiles);
        return matchingFiles;
    }

    /**
     * Helper method to recursively search for files in a folder.
     *
     * @param folder        The starting folder.
     * @param matchingFiles The list to add matching files to.
     */
    private static void searchFilesRecursively(File folder, List<File> matchingFiles) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    searchFilesRecursively(file, matchingFiles);
                } else if (isUnprocessedFile(file)) {
                    matchingFiles.add(file);
                }
            }
        }
    }

    /**
     * Determines if a replay is considered "unprocessed."
     * Current criteria:
     * - file is an MP4
     * - is a replay (contains "_replay_")
     * - isn't an already processed file (doesn't contain "_merged" in the name)
     *
     * @param file The file to check.
     * @return `true` if the file is unprocessed, `false` otherwise.
     */
    private static boolean isUnprocessedFile(File file) {
        String fileName = file.getName();
        return fileName.endsWith(".mp4") && fileName.contains("_replay_") && !fileName.contains("_merged");
    }
}