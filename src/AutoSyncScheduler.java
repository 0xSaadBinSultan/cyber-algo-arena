import java.util.concurrent.*;

/**
 * Background scheduler that periodically syncs external educational exercises.
 * Runs CodeforcesSyncService and SecurityPuzzleSyncService every 12 hours.
 */
public final class AutoSyncScheduler {

    private final ScheduledExecutorService executor;
    private final ContestEngine engine;
    private final CodeforcesSyncService codeforcesSyncService;
    private final SecurityPuzzleSyncService securityPuzzleSyncService;
    private volatile boolean running;

    public AutoSyncScheduler(ContestEngine engine) {
        this.engine = engine;
        this.codeforcesSyncService = new CodeforcesSyncService();
        this.securityPuzzleSyncService = new SecurityPuzzleSyncService();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-sync-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    /** Starts the periodic sync. Initial run after 30 seconds, then every 12 hours. */
    public void start() {
        if (running) return;
        running = true;
        executor.scheduleAtFixedRate(this::runSync, 30, 12 * 60 * 60, TimeUnit.SECONDS);
        System.out.println("[AutoSyncScheduler] Started. Syncing every 12 hours.");
    }

    private void runSync() {
        try {
            System.out.println("[AutoSyncScheduler] Running scheduled sync...");
            CodeforcesSyncService.SyncResult cfResult = codeforcesSyncService.sync(engine, 10, 800, 1400);
            System.out.println("[AutoSyncScheduler] Codeforces: " + cfResult.syncedCount() + " problems synced.");

            SecurityPuzzleSyncService.SyncResult secResult = securityPuzzleSyncService.sync(engine, "ALL");
            System.out.println("[AutoSyncScheduler] Security Exercises: " + secResult.syncedCount() + " puzzles synced.");
        } catch (Exception ex) {
            System.err.println("[AutoSyncScheduler] Sync error: " + ex.getMessage());
        }
    }

    /** Gracefully shuts down the scheduler. */
    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[AutoSyncScheduler] Shutdown complete.");
    }

    public boolean isRunning() {
        return running;
    }
}
