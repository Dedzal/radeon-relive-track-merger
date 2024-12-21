package merger.ui;

import java.awt.*;

public class UIConstants {

    static final String APP_TITLE = "Relive Track Merger";
    static final String BUTTON_INPUT_LABEL = "Select Input Folder";
    static final String BUTTON_OUTPUT_LABEL = "Select Output Folder";
    static final String BUTTON_PROCESS_LABEL = "Process";
    static final String BUTTON_PROCESS_TOOLTIP = "Please select an input folder first before processing.";
    static final String BUTTON_CANCEL_LABEL = "Cancel";
    static final String CHECKBOX_CLEAN_OUTPUT_FOLDER = "Clean output folder if it exists";
    static final String CHECKBOX_CLEAN_OUTPUT_FOLDER_TOOLTIP = "Leaving this unchecked will overwrite existing files in the output folder. Check this box to clean the output folder before processing.";
    static final String CHECKBOX_OPEN_OUTPUT_FOLDER = "Open output folder after processing";
    static final String CHECKBOX_OPEN_OUTPUT_FOLDER_TOOLTIP = "Automatically open the output folder after processing is complete.";
    static final String CHECKBOX_REPLACE_SOURCE_INSTEAD_OF_COPYING = "Replace original replays with processed replays";
    static final String CHECKBOX_REPLACE_SOURCE_INSTEAD_OF_COPYING_TOOLTIP = "Instead of being copied separately to an output folder, processed replays will replace the source video.";
    static final String CHECKBOX_DELETE_MICROPHONE_TRACKS_AFTER_PROCESSING = "Delete microphone tracks after processing";
    static final String CHECKBOX_DELETE_MICROPHONE_TRACKS_AFTER_PROCESSING_TOOLTIP = "Delete microphone tracks after they have been added to their replay.";

    static final Dimension APP_WINDOW_SIZE = new Dimension(600, 600);
    static final Dimension DEFAULT_SELECT_BUTTON_SIZE = new Dimension(150, 25);

}
