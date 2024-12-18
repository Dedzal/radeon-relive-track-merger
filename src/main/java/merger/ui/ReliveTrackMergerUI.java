package merger.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import merger.controller.ReliveTrackMergerController;
import merger.ffmpeg.FfmpegInstaller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

import static merger.controller.ReliveTrackMergerController.OUTPUT_FOLDER_NAME;
import static merger.ui.UIConstants.*;

public class ReliveTrackMergerUI extends JFrame {

    private JPanel contentPane;
    private JButton buttonProcess;
    private JButton buttonSelectInputFolder;
    private JButton buttonSelectOutputFolder;
    private JTextField textFieldInputFolderPath;
    private JTextField textFieldOutputFolderPath;
    private JCheckBox checkboxCleanOutputFolder;
    private JCheckBox checkboxOpenOutputFolder;
    private JCheckBox checkboxReplaceOriginalVideoInsteadOfCopying;
    private JCheckBox checkboxDeleteMicrophoneTracksAfterCopying;
    private JSeparator checkboxSeparator;
    private JList<String> listVideoView;
    private DefaultListModel<String> listVideoModel;
    private JScrollPane scrollpaneVideoList;
    private JTextArea textareaLog;
    private JScrollPane scrollpaneLog;

    private final ReliveTrackMergerController controller;

    private boolean warnedAboutReplace = false;

    public static void start() {
        setLookAndFeel();
        SwingUtilities.invokeLater(() -> new ReliveTrackMergerUI(new ReliveTrackMergerController()).setVisible(true));
        FfmpegInstaller.checkOrInstallFfmpeg();
    }

    public ReliveTrackMergerUI(ReliveTrackMergerController controller) {
        this.controller = controller;
        initializeFrameSettings();
        initializeContentPane();
        initializeUIElements();
        redirectSystemOutToTextArea();
        pack();
        setLocationRelativeTo(null);
    }

    private void initializeFrameSettings() {
        setTitle(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(APP_WINDOW_SIZE);
    }

    private void initializeContentPane() {
        contentPane = new JPanel(new GridBagLayout());
        setContentPane(contentPane);
    }

    private void initializeUIElements() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 10, 2, 10);

        buttonSelectInputFolder = createButton(BUTTON_INPUT_LABEL, e -> controller.selectInputFolder(this, selectDirectory()));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(buttonSelectInputFolder, constraints);

        textFieldInputFolderPath = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(textFieldInputFolderPath, constraints);

        buttonSelectOutputFolder = createButton(BUTTON_OUTPUT_LABEL, e -> controller.selectOutputFolder(this, selectDirectory()));
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(buttonSelectOutputFolder, constraints);

        textFieldOutputFolderPath = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.weightx = 1.0; // Allow horizontal growth
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(textFieldOutputFolderPath, constraints);

        checkboxCleanOutputFolder = createCheckBox(CHECKBOX_CLEAN_OUTPUT_FOLDER, CHECKBOX_CLEAN_OUTPUT_FOLDER_TOOLTIP);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(checkboxCleanOutputFolder, constraints);

        checkboxOpenOutputFolder = createCheckBox(CHECKBOX_OPEN_OUTPUT_FOLDER, CHECKBOX_OPEN_OUTPUT_FOLDER_TOOLTIP);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(checkboxOpenOutputFolder, constraints);

        checkboxSeparator = createSeparator();
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(checkboxSeparator, constraints);

        checkboxReplaceOriginalVideoInsteadOfCopying = createCheckBox(CHECKBOX_REPLACE_SOURCE_INSTEAD_OF_COPYING, CHECKBOX_REPLACE_SOURCE_INSTEAD_OF_COPYING_TOOLTIP);
        checkboxReplaceOriginalVideoInsteadOfCopying.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buttonSelectOutputFolder.setEnabled(!checkboxReplaceOriginalVideoInsteadOfCopying.isSelected()); // toggle
                checkboxCleanOutputFolder.setEnabled(!checkboxReplaceOriginalVideoInsteadOfCopying.isSelected()); // toggle

                // if selected, set input folder as output folder and update the UI, enable delete mic tracks
                if (checkboxReplaceOriginalVideoInsteadOfCopying.isSelected()) {
                    controller.setInputFolderAsOutputFolder();
                    textFieldOutputFolderPath.setText(textFieldInputFolderPath.getText());
                    checkboxDeleteMicrophoneTracksAfterCopying.setEnabled(true);

                    if (!warnedAboutReplace) {
                        System.out.println("Warning: there will be no way of recovering the original replays after they have been replaced!");
                        warnedAboutReplace = true;
                    }
                } else {
                    // if deselected, set replays_merged as the default output folder and disable mic checkbox
                    if (controller.getInputFolder() != null) {
                        textFieldOutputFolderPath.setText(textFieldInputFolderPath.getText() + File.separator + OUTPUT_FOLDER_NAME);
                    }
                    checkboxDeleteMicrophoneTracksAfterCopying.setEnabled(false);
                }
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(checkboxReplaceOriginalVideoInsteadOfCopying, constraints);

        checkboxDeleteMicrophoneTracksAfterCopying = createCheckBox(CHECKBOX_DELETE_MICROPHONE_TRACKS_AFTER_PROCESSING, CHECKBOX_DELETE_MICROPHONE_TRACKS_AFTER_PROCESSING_TOOLTIP);
        checkboxDeleteMicrophoneTracksAfterCopying.setEnabled(false);
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(checkboxDeleteMicrophoneTracksAfterCopying, constraints);

        scrollpaneVideoList = createVideoListScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 7;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1.0;
        contentPane.add(scrollpaneVideoList, constraints);

        scrollpaneLog = createLogScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 8;
        constraints.gridwidth = 2;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        contentPane.add(scrollpaneLog, constraints);

        buttonProcess = createProcessButton();
        constraints.gridx = 0;
        constraints.gridy = 9;
        constraints.gridwidth = 2; // Span the button across the entire width
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(buttonProcess, constraints);
    }

    private JButton createButton(String label, ActionListener actionListener) {
        JButton button = new JButton(label);
        button.setPreferredSize(DEFAULT_SELECT_BUTTON_SIZE);
        button.setFocusable(false);
        button.addActionListener(actionListener);
        return button;
    }

    private JSeparator createSeparator() {
        return new JSeparator(SwingConstants.HORIZONTAL);
    }

    private JCheckBox createCheckBox(String label, String tooltip) {
        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setToolTipText(tooltip);
        checkBox.setSelected(false);
        checkBox.setFocusable(false);
        return checkBox;
    }

    private JScrollPane createLogScrollPane() {
        textareaLog = new JTextArea();
        textareaLog.setEditable(false);
        textareaLog.setFocusable(false);
        JScrollPane logScrollPane = new JScrollPane(textareaLog);
        logScrollPane.setPreferredSize(new Dimension(0, 100));
        return logScrollPane;
    }

    private JTextField createNonEditableTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        return textField;
    }

    public void cleanLogTextarea() {
        textareaLog.setText("");
    }

    public void disableButtonSelectOutputFolder() {
        buttonSelectOutputFolder.setEnabled(false);
    }

    public void enableButtonSelectOutputFolder() {
        buttonSelectOutputFolder.setEnabled(true);
    }

    public void disableButtonProcess() {
        buttonProcess.setEnabled(false);
        buttonProcess.setToolTipText(BUTTON_PROCESS_TOOLTIP);
    }

    public void enableButtonProcess() {
        buttonProcess.setEnabled(true);
        buttonProcess.setToolTipText(null);
    }

    public void cleanTextFieldInputFolderPath() {
        textFieldInputFolderPath.setText("");
    }

    public void setTextFieldInputFolderPath(String textFieldInputFolderPath) {
        this.textFieldInputFolderPath.setText(textFieldInputFolderPath);
    }

    public void setTextFieldOutputFolderPath(String textFieldOutputFolderPath) {
        this.textFieldOutputFolderPath.setText(textFieldOutputFolderPath);
    }

    public boolean isReplaceOriginalReplaysSelected() {
        return checkboxReplaceOriginalVideoInsteadOfCopying.isSelected();
    }

    public boolean isCleanOutputSelected() {
        return checkboxCleanOutputFolder.isSelected();
    }

    public boolean isOpenOutputFolderSelected() {
        return checkboxOpenOutputFolder.isSelected();
    }

    public boolean isDeleteMicrophoneTracksSelected() {
        return checkboxDeleteMicrophoneTracksAfterCopying.isSelected();
    }

    public void clearVideoList() {
        listVideoModel.clear();
    }

    public void addToVideoList(String replayName) {
        listVideoModel.addElement(replayName);
    }

    public void repaintVideoList() {
        listVideoView.repaint();
    }

    public void updateReplayStatusInList(String updatedStatus) {
        SwingUtilities.invokeLater(() -> {
            String videoName = updatedStatus.substring(2); // Assume status is a single character followed by a space
            for (int i = 0; i < listVideoModel.getSize(); i++) {
                String currentName = listVideoModel.getElementAt(i);
                if (currentName.trim().endsWith(videoName)) {
                    listVideoModel.set(i, updatedStatus);
                    break;
                }
            }
        });
    }

    private JScrollPane createVideoListScrollPane() {
        listVideoModel = new DefaultListModel<>();
        listVideoView = new JList<>(listVideoModel);
        listVideoView.setFocusable(false);
        return new JScrollPane(listVideoView);
    }

    private JButton createProcessButton() {
        JButton button = createButton(BUTTON_PROCESS_LABEL, e -> controller.processReplays(this));
        button.setEnabled(false);
        button.setToolTipText(BUTTON_PROCESS_TOOLTIP);
        return button;
    }

    private File selectDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private void redirectSystemOutToTextArea() {
        OutputStream textAreaStream = new OutputStream() {
            @Override
            public void write(int b) {
                appendLog(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendLog(new String(b, off, len));
            }

            private void appendLog(String text) {
                SwingUtilities.invokeLater(() -> textareaLog.append(text));
            }
        };

        PrintStream printStream = new PrintStream(textAreaStream, true);
        System.setOut(printStream);
        System.setErr(printStream);
    }

    public void showFileSizeWarningPane(double totalSizeOfReplays, double availableDiskSpace) {
        JOptionPane.showMessageDialog(
                null,
                "The total size of the selected files (" + String.format("%.1f", totalSizeOfReplays) + " GB) exceeds the available disk space (" + String.format("%.1f", availableDiskSpace) + " GB).\n\n" +
                        "Please free up some space on the target disk or select a different output directory.",
                "Not Enough Available Disk Space",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private static void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }
}
