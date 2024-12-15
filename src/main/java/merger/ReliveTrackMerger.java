package merger;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class ReliveTrackMerger extends JFrame {

    private static final String APP_TITLE = "Relive Track Merger";
    private static final String BUTTON_INPUT_LABEL = "Select Input Folder";
    private static final String BUTTON_OUTPUT_LABEL = "Select Output Folder";
    private static final String BUTTON_PROCESS_LABEL = "Process";
    private static final String BUTTON_PROCESS_TOOLTIP = "Please select an input folder first before processing.";
    private static final String CHECKBOX_CLEAN_OUTPUT_FOLDER = "Clean output folder before processing";
    private static final String CHECKBOX_CLEAN_OUTPUT_FOLDER_TOOLTIP = "Leaving this unchecked will overwrite existing files in the output folder. Check this box to clean the output folder before processing.";
    private static final String CHECKBOX_OPEN_OUTPUT_FOLDER = "Open output folder after processing";
    private static final String CHECKBOX_OPEN_OUTPUT_FOLDER_TOOLTIP = "Automatically open the output folder after processing is complete.";

    private static final String OUTPUT_FOLDER_NAME = "replays_merged";

    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 600;
    private static final Dimension DEFAULT_SELECT_BUTTON_SIZE = new Dimension(150, 25);

    private JPanel contentPane;
    private JButton buttonProcess;
    private JButton buttonSelectInputFolder;
    private JButton buttonSelectOutputFolder;
    private JTextField textfieldInputFolderPath;
    private JTextField textfieldOutputFolderPath;
    private JCheckBox checkboxCleanOutputFolder;
    private JCheckBox checkboxOpenOutputFolder;
    private JList<String> listVideoView;
    private DefaultListModel<String> listVideoModel;
    private JScrollPane scrollpaneVideoList;
    private JTextArea textareaLog;
    private JScrollPane scrollpaneLog;

    private File selectedInputFolder;
    private File selectedOutputFolder;
    private List<File> filesToProcess;

    public static void main(String[] args) {
        setLookAndFeel();
        SwingUtilities.invokeLater(() -> new ReliveTrackMerger().setVisible(true));
        FfmpegInstaller.checkOrInstallFfmpeg();
    }


    public ReliveTrackMerger() {
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
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private void initializeContentPane() {
        contentPane = new JPanel(new GridBagLayout());
        setContentPane(contentPane);
    }

    private JButton createButton(String label, ActionListener actionListener) {
        JButton button = new JButton(label);
        button.setPreferredSize(ReliveTrackMerger.DEFAULT_SELECT_BUTTON_SIZE);
        button.setFocusable(false);
        button.addActionListener(actionListener);
        return button;
    }

    private JCheckBox createCheckBox(String label, String tooltip) {
        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setToolTipText(tooltip);
        checkBox.setSelected(false);
        checkBox.setFocusable(false);
        return checkBox;
    }

    private void initializeUIElements() {
        GridBagConstraints constraints = new GridBagConstraints();

        buttonSelectInputFolder = createButton(BUTTON_INPUT_LABEL, e -> selectInputFolderAction());
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(buttonSelectInputFolder, constraints);

        textfieldInputFolderPath = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(textfieldInputFolderPath, constraints);

        buttonSelectOutputFolder = createButton(BUTTON_OUTPUT_LABEL, e -> selectOutputFolderAction());
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(buttonSelectOutputFolder, constraints);

        textfieldOutputFolderPath = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.weightx = 1.0; // Allow horizontal growth
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(textfieldOutputFolderPath, constraints);

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

        scrollpaneVideoList = createVideoListScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1.0;
        contentPane.add(scrollpaneVideoList, constraints);

        scrollpaneLog = createLogScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        contentPane.add(scrollpaneLog, constraints);

        buttonProcess = createProcessButton();
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2; // Span the button across the entire width
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(buttonProcess, constraints);
    }

    private JScrollPane createVideoListScrollPane() {
        listVideoModel = new DefaultListModel<>();
        listVideoView = new JList<>(listVideoModel);
        listVideoView.setFocusable(false);
        return new JScrollPane(listVideoView);
    }

    private JButton createProcessButton() {
        JButton button = createButton(BUTTON_PROCESS_LABEL, e -> processSelectedFiles());
        button.setEnabled(selectedInputFolder != null);
        button.setToolTipText(BUTTON_PROCESS_TOOLTIP);
        return button;
    }

    private JTextField createNonEditableTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        textField.setFocusable(false);
        return textField;
    }

    private JScrollPane createLogScrollPane() {
        textareaLog = new JTextArea();
        textareaLog.setEditable(false);
        textareaLog.setFocusable(false);
        JScrollPane logScrollPane = new JScrollPane(textareaLog);
        logScrollPane.setPreferredSize(new Dimension(0, 100));
        return logScrollPane;
    }

    private static void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }

    private void selectInputFolderAction() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            selectedInputFolder = fileChooser.getSelectedFile();
            textfieldInputFolderPath.setText(selectedInputFolder.getAbsolutePath());
            loadVideoFiles();

            if (selectedOutputFolder == null) {
                selectedOutputFolder = selectedInputFolder;
                textfieldOutputFolderPath.setText(selectedInputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);
            }

            buttonSelectOutputFolder.setEnabled(true);
            buttonProcess.setEnabled(true);
            buttonProcess.setToolTipText(null);
        } else {
            selectedInputFolder = null;
            textfieldInputFolderPath.setText("");

            buttonProcess.setEnabled(false);
            buttonProcess.setToolTipText(BUTTON_PROCESS_TOOLTIP);
        }
    }

    private void selectOutputFolderAction() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            selectedOutputFolder = fileChooser.getSelectedFile();
            textfieldOutputFolderPath.setText(selectedOutputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);
        }
    }

    private void loadVideoFiles() {
        listVideoModel.clear();
        if (selectedInputFolder != null && selectedInputFolder.isDirectory()) {
            List<File> videoFiles = RecursiveReplayFetcher.fetchUnprocessedFiles(selectedInputFolder);
            filesToProcess = videoFiles.stream().sorted(Comparator.comparing(File::getName)).collect(Collectors.toList());

            for (File videoFile : videoFiles) {
                listVideoModel.addElement(videoFile.getName());
            }

            listVideoView.repaint();
        }
    }

    private void processSelectedFiles() {
        // check if ffmpeg is installed before processing is initiated just in case
        try {
            FfmpegInstaller.checkOrInstallFfmpeg();
        } catch (IllegalStateException e) {
            return;
        }

        long startTime = System.currentTimeMillis();

        // clean the log text area before processing
        textareaLog.setText("");

        if (filesToProcess == null || filesToProcess.isEmpty()) {
            System.out.println("No files to process");
            return;
        }

        buttonProcess.setEnabled(false);

        File outputFolder = new File(selectedOutputFolder, OUTPUT_FOLDER_NAME);
        if (checkboxCleanOutputFolder.isSelected() && outputFolder.exists()) {
            System.out.println("Cleaning output folder...");
            deleteDirectory(outputFolder);
        }
        outputFolder.mkdirs();

        double replaysSize = getTotalSizeOfSelectedReplays();
        double availableDiskSpace = getAvailableDiskSpaceOfOutputDirectory();
        if (replaysSize >= availableDiskSpace) {
            JOptionPane.showMessageDialog(
                    null,
                    "The total size of the selected files (" + String.format("%.1f", replaysSize) + " GB) exceeds the available disk space (" + String.format("%.1f", availableDiskSpace) + " GB).\n\n" +
                            "Please free up some space on the target disk or select a different output directory.",
                    "Not Enough Available Disk Space",
                    JOptionPane.WARNING_MESSAGE
            );
            buttonProcess.setEnabled(true);
            return;
        }
        System.out.println("Total file size of selected replays: " + String.format("%.1f", replaysSize) + " GB");
        System.out.println("Available storage on disk: " + String.format("%.1f", availableDiskSpace) + " GB");

        System.out.println("Processing " + filesToProcess.size() + " file(s)");
        System.out.println("Output folder is: " + outputFolder.getAbsolutePath());

        System.out.println();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println();

        CountDownLatch latch = new CountDownLatch(filesToProcess.size());
        ReplayProcessor processor = new ReplayProcessor(outputFolder, selectedInputFolder);

        filesToProcess.forEach(file -> {
            ReplayProcessingWorker worker = new ReplayProcessingWorker(file, processor, this::updateStatus, latch);
            worker.execute();
        });

        startFinishingLogThread(latch, startTime);
        buttonProcess.setEnabled(true);

        if (checkboxOpenOutputFolder.isSelected()) {
            openOutputDirectory();
        }
    }

    private void openOutputDirectory() {
        try {
            Desktop.getDesktop().open(selectedOutputFolder);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private double getTotalSizeOfSelectedReplays() {
        long totalSizeInBytes = filesToProcess.stream()
                .mapToLong(File::length) // Get file size in bytes
                .sum();
        return totalSizeInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
    }

    private double getAvailableDiskSpaceOfOutputDirectory() {
        if (selectedOutputFolder != null) {
            long freeSpaceInBytes = selectedOutputFolder.getFreeSpace(); // Returns free space in bytes
            return freeSpaceInBytes / (1024.0 * 1024.0 * 1024.0); // Convert bytes to gigabytes
        }
        return 0.0;
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    private static void startFinishingLogThread(CountDownLatch latch, long startTime) {
        new Thread(() -> {
            try {
                latch.await(); // Wait until all workers finish
                SwingUtilities.invokeLater(() -> {
                    System.out.println();
                    System.out.println("Done!");
                    System.out.println("Processing took a total of " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");
                });
            } catch (InterruptedException e) {
                System.err.println("Processing was interrupted!");
            }
        }).start();
    }


    private void updateStatus(String updatedStatus) {
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

    private void redirectSystemOutToTextArea() {
        // Create an OutputStream that appends text to the JTextArea
        OutputStream textAreaStream = new OutputStream() {
            @Override
            public void write(int b) {
                // Writes a single character
                appendLog(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                // Writes an array of characters as a String
                appendLog(new String(b, off, len));
            }

            private void appendLog(String text) {
                SwingUtilities.invokeLater(() -> textareaLog.append(text));
            }
        };

        // Redirect System.out to the JTextArea's OutputStream
        PrintStream printStream = new PrintStream(textAreaStream, true);
        System.setOut(printStream);
        System.setErr(printStream); // Redirect error stream as well
    }


}