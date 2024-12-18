package merger.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReplayUtils {

    public static void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    public static List<File> findUnprocessedReplays(File inputFolder) {
        List<File> unprocessedReplays = new ArrayList<>();
        findReplaysRecursively(inputFolder, unprocessedReplays);
        return unprocessedReplays;
    }

    private static void findReplaysRecursively(File inputFolder, List<File> unprocessedReplays) {
        File[] files = inputFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findReplaysRecursively(file, unprocessedReplays);
                } else if (isUnprocessedReplay(file)) {
                    unprocessedReplays.add(file);
                }
            }
        }
    }

    private static boolean isUnprocessedReplay(File replay) {
        String replayName = replay.getName();
        return replayName.endsWith(".mp4") && replayName.contains("_replay_") && !replayName.contains("_merged");
    }

}
