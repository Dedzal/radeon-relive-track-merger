package merger.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReplayUtils {

    public static void deleteProcessedReplaysInDirectory(File directory) {
        File[] filesInDirectory = directory.listFiles();
        if (filesInDirectory != null) {
            for (File subfolder : filesInDirectory) {
                deleteProcessedReplaysInDirectory(subfolder);
            }
        }
        if (isProcessedReplay(directory) || isEmptyDirectory(directory)) {
            directory.delete();
        }
    }

    public static List<File> getUnprocessedReplays(File inputFolder) {
        List<File> unprocessedReplays = new ArrayList<>();
        collectUnprocessedReplays(inputFolder, unprocessedReplays);
        return unprocessedReplays;
    }

    private static void collectUnprocessedReplays(File folder, List<File> collectedReplays) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectUnprocessedReplays(file, collectedReplays);
                } else if (isUnprocessedReplay(file)) {
                    collectedReplays.add(file);
                }
            }
        }
    }

    private static boolean isEmptyDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return true;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            return files.length == 0;
        }
        return false;
    }

    private static boolean isProcessedReplay(File replay) {
        return isReplay(replay) && !isUnprocessedReplay(replay);
    }

    private static boolean isUnprocessedReplay(File replay) {
        return isReplay(replay) && !replay.getName().contains("_merged");
    }

    private static boolean isReplay(File file) {
        String name = file.getName();
        return name.endsWith(".mp4") && name.contains("_replay_");
    }

}
