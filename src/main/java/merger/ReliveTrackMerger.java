package merger;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
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
    }


    public ReliveTrackMerger() {
        setTitle(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));

        contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        JPanel inputPanel = initializeInputFolderPanel();
        JPanel outputPanel = initializeOutputFolderPanel();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(outputPanel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);
        JScrollPane videoListScrollPane = new JScrollPane(videoList);
        centerPanel.add(videoListScrollPane, BorderLayout.CENTER);


        contentPane.add(inputPanel, BorderLayout.NORTH);
        contentPane.add(centerPanel, BorderLayout.CENTER);

        processButton = new JButton(PROCESS_BUTTON_LABEL);
        processButton.addActionListener(e -> processSelectedFiles());
        contentPane.add(processButton, BorderLayout.SOUTH);


        JPanel logPanel = new JPanel(new BorderLayout());
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logPanel.add(logTextArea, BorderLayout.CENTER);
        JScrollPane scrollPaneForLogs = new JScrollPane(logTextArea);
        scrollPaneForLogs.setPreferredSize(new Dimension(0, 100));
        centerPanel.add(scrollPaneForLogs, BorderLayout.SOUTH);

        redirectSystemOutToTextArea();

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel initializeOutputFolderPanel() {
        JPanel outputFolderPanel = new JPanel(new BorderLayout());

        outputFolderLabel = new JLabel(OUTPUT_FOLDER_TEXT);
        outputFolderLabel.setBorder(BorderFactory.createEmptyBorder(0, 13, 0, 13));
        outputFolderPanel.add(outputFolderLabel, BorderLayout.WEST);

        outputPathField = new JTextField();
        outputPathField.setEditable(false);
        outputFolderPanel.add(outputPathField, BorderLayout.CENTER);

        return outputFolderPanel;
    }

    private JPanel initializeInputFolderPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());

        selectFolderButton = new JButton(SELECT_FOLDER_BUTTON_LABEL);
        selectFolderButton.addActionListener(_ -> selectVideoFolder());
        topPanel.add(selectFolderButton, BorderLayout.WEST);

        selectedFolderTextField = new JTextField();
        selectedFolderTextField.setEditable(false);
        topPanel.add(selectedFolderTextField, BorderLayout.CENTER);

        return topPanel;
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
        }
    }

    private void updateListModel() {
        listModel.clear();
        if (selectedFolder != null && selectedFolder.isDirectory()) {
            List<File> videoFiles = new ArrayList<>();
            searchVideoFilesRecursively(selectedFolder, videoFiles);

            filesToProcess = videoFiles.stream().sorted(Comparator.comparing(File::getName)).toList();

            for (File videoFile : videoFiles) {
                listModel.addElement(videoFile.getName());
            }

            videoList.repaint();
        }
    }

    private void loadVideoFiles() {
        updateListModel();
    }

    private void searchVideoFilesRecursively(File folder, List<File> videoFiles) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isUnprocessedFile(file)) {
                    videoFiles.add(file);
                } else if (file.isDirectory()) {
                    searchVideoFilesRecursively(file, videoFiles);
                }
            }
        }
    }

    private boolean isUnprocessedFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".mp4") && fileName.contains("_replay_") && !fileName.contains("_merged.mp4");
    }

    private void processSelectedFiles() {
        long startTime = System.currentTimeMillis();

        logTextArea.setText("");

        String outputFolderName = "merged";
        File mergedFolder = new File(selectedFolder, outputFolderName);
        if (mergedFolder.exists()) {
            System.out.println("Merged folder exists already, deleting it and creating a new one");
            mergedFolder.delete();
        }
        mergedFolder.mkdirs();
        System.out.println("Processing " + filesToProcess.size() + " file(s)");
        System.out.println("Output folder is: " + mergedFolder.getAbsolutePath());

        System.out.println();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println();

        CountDownLatch latch = new CountDownLatch(filesToProcess.size());
        VideoFileProcessor processor = new VideoFileProcessor(mergedFolder);

        filesToProcess.forEach(file -> {
            FileProcessingWorker worker = new FileProcessingWorker(file, processor, this::updateStatus, latch);
            worker.execute();
        });

        startFinishingLogThread(latch, startTime);
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