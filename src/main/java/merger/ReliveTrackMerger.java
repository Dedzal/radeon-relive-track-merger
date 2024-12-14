package merger;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ReliveTrackMerger extends JFrame {

    private static final String APP_TITLE = "Relive Track Merger";
    private static final String SELECT_FOLDER_BUTTON_LABEL = "Select Input Folder";
    private static final String OUTPUT_FOLDER_TEXT = "Select Output Folder";
    private static final String PROCESS_BUTTON_LABEL = "Process";

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
    private JTextArea logTextArea;
    private JList<String> videoList;
    private DefaultListModel<String> listModel;

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
        initializeContentPaneWithGridBag();
        initializeTopPanelWithGridBag();
        initializeCenterPanelWithGridBag();
        initializeProcessButton();
        redirectSystemOutToTextArea();
        pack();
        setLocationRelativeTo(null);
    }

    private void initializeFrameSettings() {
        setTitle(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private void initializeContentPaneWithGridBag() {
        contentPane = new JPanel(new GridBagLayout());
        setContentPane(contentPane);
    }

    private void initializeTopPanelWithGridBag() {
        GridBagConstraints constraints = new GridBagConstraints();

        // Add the "Select Folder" button
        selectInputFolderButton = createSelectFolderButton();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(selectInputFolderButton, constraints);

        // Add the non-editable text field for the selected folder
        selectedInputFolderTextField = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0; // Allow horizontal growth
        contentPane.add(selectedInputFolderTextField, constraints);
    }

    private void initializeCenterPanelWithGridBag() {
        GridBagConstraints constraints = new GridBagConstraints();

        // Add output folder panel
        JPanel outputFolderPanel = createOutputFolderPanel();
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2; // Span across two columns
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(outputFolderPanel, constraints);

        // Add video list scroll pane
        JScrollPane videoListScrollPane = createVideoListScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1.0; // Allow vertical growth
        contentPane.add(videoListScrollPane, constraints);

        // Add log scroll pane
        JScrollPane logScrollPane = createLogScrollPane();
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        contentPane.add(logScrollPane, constraints);
    }

    private void initializeProcessButton() {
        GridBagConstraints constraints = new GridBagConstraints();
        processButton = new JButton(PROCESS_BUTTON_LABEL);
        processButton.addActionListener(e -> processSelectedFiles());
        processButton.setEnabled(selectedInputFolder != null);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2; // Span the button across the entire width
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(processButton, constraints);
    }

    // Helper methods for creating reusable components
    private JButton createSelectFolderButton() {
        JButton button = new JButton(SELECT_FOLDER_BUTTON_LABEL);
        button.setPreferredSize(DEFAULT_SELECT_BUTTON_SIZE);
        button.addActionListener(e -> selectInputFolder());
        return button;
    }

    private JTextField createNonEditableTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        return textField;
    }

    private JPanel createOutputFolderPanel() {
        JPanel outputFolderPanel = new JPanel(new BorderLayout());

        selectOutputFolderButton = new JButton(OUTPUT_FOLDER_TEXT);
        selectOutputFolderButton.addActionListener(e -> selectOutputFolder());
        selectOutputFolderButton.setPreferredSize(DEFAULT_SELECT_BUTTON_SIZE);

        selectedOutputFolderTextField = createNonEditableTextField();

        outputFolderPanel.add(selectOutputFolderButton, BorderLayout.WEST);
        outputFolderPanel.add(selectedOutputFolderTextField, BorderLayout.CENTER);
        return outputFolderPanel;
    }

    private JScrollPane createVideoListScrollPane() {
        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);
        return new JScrollPane(videoList);
    }

    private JScrollPane createLogScrollPane() {
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
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

    private void selectInputFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            selectedInputFolder = fileChooser.getSelectedFile();
            selectedInputFolderTextField.setText(selectedInputFolder.getAbsolutePath());
            loadVideoFiles();

            if (selectedOutputFolder == null) {
                selectedOutputFolderTextField.setText(selectedInputFolder.getAbsolutePath() + File.separator + OUTPUT_FOLDER_NAME);
            }

            selectOutputFolderButton.setEnabled(true);
            processButton.setEnabled(true);
        } else {
            selectedInputFolder = null;
            selectedInputFolderTextField.setText("");

            processButton.setEnabled(false);
        }
    }

    private void selectOutputFolder() {
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

        File outputFolder = new File(selectedOutputFolder, OUTPUT_FOLDER_NAME);
        if (outputFolder.exists()) {
            System.out.println("Output folder already exists at selected location. Deleting...");
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
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
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