package adder;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ReliveTrackMerger extends JFrame {

    private static final String APP_TITLE = "Relive Track Merger";

    private JPanel contentPane;
    private JButton selectFolderButton;
    private JButton processButton;
    private JLabel outputFolderLabel;
    private JTextField seletedFolderTextField;
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
        setPreferredSize(new Dimension(600, 400));

        contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        selectFolderButton = new JButton("Select Folder");
        selectFolderButton.addActionListener(e -> selectVideoFolder());

        seletedFolderTextField = new JTextField();
        seletedFolderTextField.setEditable(false);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(selectFolderButton, BorderLayout.WEST);
        topPanel.add(seletedFolderTextField, BorderLayout.CENTER);

        // Create panel for center area
        JPanel centerPanel = new JPanel(new BorderLayout());

        // New panel for output folder information
        JPanel outputFolderPanel = new JPanel(new BorderLayout());
        outputFolderLabel = new JLabel("Output folder:");
        outputFolderLabel.setBorder(BorderFactory.createEmptyBorder(0, 13, 0, 13));
        outputPathField = new JTextField();
        outputPathField.setEditable(false);
        outputFolderPanel.add(outputFolderLabel, BorderLayout.WEST);
        outputFolderPanel.add(outputPathField, BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);

        JScrollPane scrollPane = new JScrollPane(videoList);

        // Add both outputFolderPanel and scrollPane in the new center panel
        centerPanel.add(outputFolderPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(centerPanel, BorderLayout.CENTER);

        processButton = new JButton("Process");
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
            seletedFolderTextField.setText(selectedFolder.getAbsolutePath());
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
        return fileName.endsWith(".mp4") && !fileName.contains("_merged.mp4");
    }

    private void processSelectedFiles() {
        logTextArea.setText("");

        String outputFolderName = "merged";
        File mergedFolder = new File(selectedFolder, outputFolderName);
        if (mergedFolder.exists()) {
            System.out.println("Merged folder exists already, deleting it and creating a new one");
            mergedFolder.delete();
        }
        mergedFolder.mkdirs();
        System.out.println("Processing files");
        System.out.println("Output folder is: " + mergedFolder.getAbsolutePath());

        System.out.println();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println();

        for (File videoFile : filesToProcess) {
            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    try {
                        publish("🔁 " + videoFile.getName()); // Initial status
                        processSingleFile(videoFile, mergedFolder);
                    } catch (Exception e) {
                        publish("❌ " + videoFile.getName()); // Error status
                    }
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    // Update the status in the JList during the process
                    for (String chunk : chunks) {
                        updateStatus(chunk);
                    }
                }

                @Override
                protected void done() {
                    try {
                        get(); // Ensures that exceptions are thrown if any occur
                        publish("✅ " + videoFile.getName()); // Complete status
                    } catch (Exception e) {
                        publish("❌ " + videoFile.getName()); // Error status
                    }
                }
            };
            worker.execute();
        }
    }


    private void processSingleFile(File videoFile, File mergedFolder) throws IOException, InterruptedException {
        var videoName = videoFile.getName();
        var videoNameNoExtension = videoName.substring(0, videoName.lastIndexOf('.'));
        var videoDirectory = videoFile.getParentFile();
        var microphoneTrack = new File(videoDirectory, videoNameNoExtension + ".m4a");
        var outputFile = new File(mergedFolder, videoNameNoExtension + "_merged.mp4");

        System.out.println("Processing file: " + videoName);

        if (microphoneTrack.exists()) {
            Process process = new ProcessBuilder(
                    "ffmpeg",
                    "-i", videoFile.getAbsolutePath(),
                    "-i", microphoneTrack.getAbsolutePath(),
                    "-nostdin", "-y", "-map", "0", "-map", "1", "-c", "copy",
                    outputFile.getAbsolutePath()
            )
                    .inheritIO()
                    .start();
            process.waitFor();
            System.out.println("File: " + videoFile.getName() + " processed");
        } else {
            System.out.println("File: " + videoFile.getName() + " does not contain a microphone track, copying it to the output folder");
            Files.copy(videoFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
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