package merger.processing;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class ReplayProcessorWorker extends SwingWorker<Void, String> {
    private final File videoFile;
    private final ReplayProcessor processor;
    private final Consumer<String> progressCallback;
    private final CountDownLatch latch;

    public ReplayProcessorWorker(File videoFile, ReplayProcessor processor, Consumer<String> progressCallback, CountDownLatch latch) {
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
            if (isCancelled()) {
                publish("⏹️ " + videoFile.getName());
            } else {
                get();
                publish("✅ " + videoFile.getName());
            }
        } catch (Exception e) {
            publish("❌ " + videoFile.getName());
        } finally {
            latch.countDown();
        }
    }
}