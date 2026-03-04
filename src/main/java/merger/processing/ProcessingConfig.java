package merger.processing;

/**
 * Configuration class for replay processing parameters.
 * Simple pause/resume logic for local single-threaded processing.
 */
public class ProcessingConfig {

    // Minimum free disk space required before processing (in MB)
    public static final long MIN_FREE_SPACE_MB = 500;

    // Pause/resume check interval in milliseconds
    public static final long PAUSE_CHECK_INTERVAL_MS = 100;

    private boolean pauseRequested = false;
    private final Object pauseLock = new Object();

    /**
     * Request pause of processing. Processing will pause at the next safe point.
     */
    public void requestPause() {
        synchronized (pauseLock) {
            pauseRequested = true;
        }
    }

    /**
     * Resume processing after pause.
     */
    public void resume() {
        synchronized (pauseLock) {
            pauseRequested = false;
            pauseLock.notifyAll();
        }
    }

    /**
     * Check if pause is requested and wait if needed.
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void checkAndWaitIfPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (pauseRequested) {
                pauseLock.wait(PAUSE_CHECK_INTERVAL_MS);
            }
        }
    }
}

