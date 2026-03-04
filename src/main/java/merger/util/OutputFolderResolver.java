package merger.util;

import merger.controller.ReliveTrackMergerController;

import java.io.File;

/**
 * Small utility to centralize output-folder resolution rules.
 */
public final class OutputFolderResolver {

    private OutputFolderResolver() {}

    /**
     * Resolve the internal output folder when a user selects an output folder.
     * If replaceOriginals is true, the selected folder is used as-is.
     * If false, selected/replays_merged is used unless selected already points to
     * a folder named `replays_merged`.
     */
    public static File resolveSelectedOutput(File selectedOutputFolder, boolean replaceOriginals) {
        if (selectedOutputFolder == null) return null;
        if (replaceOriginals) return selectedOutputFolder;
        if (ReliveTrackMergerController.REPLAYS_MERGED.equalsIgnoreCase(selectedOutputFolder.getName())) {
            return selectedOutputFolder;
        }
        return new File(selectedOutputFolder, ReliveTrackMergerController.REPLAYS_MERGED);
    }
}

