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


    public static final String OUTPUT_FOLDER_NAME = "replays_merged";

    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 400;
    private static final Dimension DEFAULT_SELECT_BUTTON_SIZE = new Dimension(150, 25);

    private JPanel contentPane;
    private JButton processButton;
    private JButton selectInputFolderButton;
    private JButton selectOutputFolderButton;
    private JTextField selectedInputFolderTextField;
    private JTextField selectedOutputFolderTextField;
    private JCheckBox cleanOutputFolderCheckBox;
    private JCheckBox openOutputFolderCheckBox;
    private JList<String> videoList;
    private DefaultListModel<String> listModel;
    private JScrollPane videoListScrollPane;
    private JTextArea logTextArea;
    private JScrollPane logScrollPane;

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

        selectInputFolderButton = createButton(BUTTON_INPUT_LABEL, e -> selectInputFolderAction());
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(selectInputFolderButton, constraints);

        selectedInputFolderTextField = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(selectedInputFolderTextField, constraints);

        selectOutputFolderButton = createButton(BUTTON_OUTPUT_LABEL, e -> selectOutputFolderAction());
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(selectOutputFolderButton, constraints);

        selectedOutputFolderTextField = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.weightx = 1.0; // Allow horizontal growth
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(selectedOutputFolderTextField, constraints);

        cleanOutputFolderCheckBox = createCheckBox(CHECKBOX_CLEAN_OUTPUT_FOLDER, CHECKBOX_CLEAN_OUTPUT_FOLDER_TOOLTIP);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(cleanOutputFolderCheckBox, constraints);

        openOutputFolderCheckBox = createCheckBox(CHECKBOX_OPEN_OUTPUT_FOLDER, CHECKBOX_OPEN_OUTPUT_FOLDER_TOOLTIP);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.anchor = GridBagConstraints.WEST;
        contentPane.add(openOutputFolderCheckBox, constraints);

        videoListScrollPane = createVideoListScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1.0;
        contentPane.add(videoListScrollPane, constraints);

        logScrollPane = createLogScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        contentPane.add(logScrollPane, constraints);

        processButton = createProcessButton();
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2; // Span the button across the entire width
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(processButton, constraints);
    }

    private JScrollPane createVideoListScrollPane() {
        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);
        videoList.setFocusable(false);
        return new JScrollPane(videoList);
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
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFocusable(false);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
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
            selectedInputFolderTextField.setText(selectedInputFolder.getAbsolutePath());
            loadVideoFiles();

            if (selectedOutputFolder == null) {
                selectedOutputFolder = selectedInputFolder;
                selectedOutputFolderTextField.setText(selectedInputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);
            }

            selectOutputFolderButton.setEnabled(true);
            processButton.setEnabled(true);
            processButton.setToolTipText(null);
        } else {
            selectedInputFolder = null;
            selectedInputFolderTextField.setText("");

            processButton.setEnabled(false);
            processButton.setToolTipText(BUTTON_PROCESS_TOOLTIP);
        }
    }

    private void selectOutputFolderAction() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            selectedOutputFolder = fileChooser.getSelectedFile();
            selectedOutputFolderTextField.setText(selectedOutputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);
        }
    }

    private void loadVideoFiles() {
        listModel.clear();
        if (selectedInputFolder != null && selectedInputFolder.isDirectory()) {
            List<File> videoFiles = RecursiveReplayFetcher.fetchUnprocessedFiles(selectedInputFolder);
            filesToProcess = videoFiles.stream().sorted(Comparator.comparing(File::getName)).toList();

            for (File videoFile : videoFiles) {
                listModel.addElement(videoFile.getName());
            }

            videoList.repaint();
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
        logTextArea.setText("");

        if (filesToProcess == null || filesToProcess.isEmpty()) {
            System.out.println("No files to process");
            return;
        }

        processButton.setEnabled(false);

        File outputFolder = new File(selectedOutputFolder, OUTPUT_FOLDER_NAME);
        if (cleanOutputFolderCheckBox.isSelected() && outputFolder.exists()) {
            System.out.println("Cleaning output folder...");
            deleteDirectory(outputFolder);
        }
        outputFolder.mkdirs();

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
        processButton.setEnabled(true);

        if (openOutputFolderCheckBox.isSelected()) {
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
            for (int i = 0; i < listModel.getSize(); i++) {
                String currentName = listModel.getElementAt(i);
                if (currentName.trim().endsWith(videoName)) {
                    listModel.set(i, updatedStatus);
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
                SwingUtilities.invokeLater(() -> logTextArea.append(text));
            }
        };

        // Redirect System.out to the JTextArea's OutputStream
        PrintStream printStream = new PrintStream(textAreaStream, true);
        System.setOut(printStream);
        System.setErr(printStream); // Redirect error stream as well
    }
}