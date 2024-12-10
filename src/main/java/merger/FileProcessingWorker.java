package merger;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class FileProcessingWorker extends SwingWorker<Void, String> {
    private final File videoFile;
    private final VideoFileProcessor processor;
    private final Consumer<String> progressCallback;
    private  final CountDownLatch latch;

    public FileProcessingWorker(File videoFile, VideoFileProcessor processor, Consumer<String> progressCallback, CountDownLatch latch) {
        this.videoFile = videoFile;
        this.processor = processor;
        this.progressCallback = progressCallback;
        this.latch = latch;
    }

    @Override
    protected Void doInBackground() throws Exception {
        publish("🔁 " + videoFile.getName());
        processor.process(videoFile);
        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        for (String message : chunks) {
            progressCallback.accept(message);
        }
    }

    @Override
    protected void done() {
        try {
            get(); // handle exceptions
            publish("✅ " + videoFile.getName());
        } catch (Exception e) {
            publish("❌ " + videoFile.getName());
        }
        latch.countDown();
    }
}