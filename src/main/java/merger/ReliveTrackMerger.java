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
    private static final String SELECT_FOLDER_BUTTON_LABEL = "Select Folder";
    private static final String OUTPUT_FOLDER_TEXT = "Output folder:";
    private static final String PROCESS_BUTTON_LABEL = "Process";

    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 400;

    private JPanel contentPane;
    private JButton selectFolderButton;
    private JButton processButton;
    private JLabel outputFolderLabel;
    private JTextField selectedFolderTextField;
    private JTextField outputPathField;
    private JTextArea logTextArea;
    private JList<String> videoList;
    private DefaultListModel<String> listModel;

    private File selectedFolder;
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
        selectFolderButton = createSelectFolderButton();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(selectFolderButton, constraints);

        // Add the non-editable text field for the selected folder
        selectedFolderTextField = createNonEditableTextField();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0; // Allow horizontal growth
        contentPane.add(selectedFolderTextField, constraints);
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
        processButton.setEnabled(selectedFolder != null);

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
        button.addActionListener(e -> selectVideoFolder());
        return button;
    }

    private JTextField createNonEditableTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        return textField;
    }

    private JPanel createOutputFolderPanel() {
        JPanel outputFolderPanel = new JPanel(new BorderLayout());
        outputFolderLabel = new JLabel(OUTPUT_FOLDER_TEXT);
        outputFolderLabel.setBorder(BorderFactory.createEmptyBorder(0, 13, 0, 13));
        outputPathField = createNonEditableTextField();

        outputFolderPanel.add(outputFolderLabel, BorderLayout.WEST);
        outputFolderPanel.add(outputPathField, BorderLayout.CENTER);
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

    private void selectVideoFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            selectedFolder = fileChooser.getSelectedFile();
            selectedFolderTextField.setText(selectedFolder.getAbsolutePath());
            loadVideoFiles();

            outputPathField.setVisible(true);
            outputPathField.setText(selectedFolder.getAbsolutePath() + File.separator + "merged");

            processButton.setEnabled(true);
        } else {
            selectedFolder = null;
            selectedFolderTextField.setText("");
            processButton.setEnabled(false);
        }
    }

    private void loadVideoFiles() {
        listModel.clear();
        if (selectedFolder != null && selectedFolder.isDirectory()) {
            List<File> videoFiles = RecursiveReplayFetcher.fetchUnprocessedFiles(selectedFolder);
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

        String outputFolderName = "merged";
        File mergedFolder = new File(selectedFolder, outputFolderName);
        if (mergedFolder.exists()) {
            System.out.println("Merged folder exists already, deleting it and creating a new one");
            deleteDirectory(mergedFolder);
        }
        mergedFolder.mkdirs();

        System.out.println("Processing " + filesToProcess.size() + " file(s)");
        System.out.println("Output folder is: " + mergedFolder.getAbsolutePath());

        System.out.println();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println();

        CountDownLatch latch = new CountDownLatch(filesToProcess.size());
        ReplayProcessor processor = new ReplayProcessor(mergedFolder, selectedFolder);

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