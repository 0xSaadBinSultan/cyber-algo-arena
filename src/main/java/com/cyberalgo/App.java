package com.cyberalgo;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application entry point for Cyber-Algo Arena.
 * Multi-contest platform backed by MongoDB.
 *
 * Usage:
 *   java -jar app.jar                → Runs Web Server on PORT env or 8080 (default)
 *   java -jar app.jar --web 3000     → Runs Web Server on custom port
 *   java -jar app.jar --demo         → Runs automated lifecycle test
 *   java -jar app.jar --cli          → Runs interactive CLI mode
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0 && "--verify-persistence".equals(args[0])) {
                MongoPersistenceCheck.run();
                return;
            }
            if (args.length > 0 && "--demo".equals(args[0])) {
                DemoRunner.main(new String[0]);
                return;
            }

            if (args.length > 0 && "--cli".equals(args[0])) {
                MongoManager cliMongo = new MongoManager();
                MongoRepository cliRepo = new MongoRepository(cliMongo);
                ContestEngine cliEngine = new ContestEngine(cliRepo);
                InputHandler cliInput = new InputHandler(new java.util.Scanner(System.in));
                new CLIController(cliEngine, cliInput).start();
                cliMongo.close();
                return;
            }

            int port = 8080;
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isBlank()) {
                try {
                    port = Integer.parseInt(envPort.trim());
                } catch (NumberFormatException ignored) {}
            }

            if (args.length > 1 && "--web".equals(args[0])) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }

            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║   Cyber-Algo Arena — Multi-Contest Web Engine   ║");
            System.out.println("║   Binding Port: " + port + "                            ║");
            System.out.println("╚══════════════════════════════════════════════════╝");

            // Ensure attachment directories exist
            Files.createDirectories(Path.of("contest_data", "attachments"));

            MongoManager mongoManager = new MongoManager();

            MongoRepository repository = new MongoRepository(mongoManager, false);
            ContestEngine engine = new ContestEngine(repository);
            if (!engine.isDatabaseReady()) {
                System.err.println("[App] Database unavailable; liveness remains available and readiness returns 503.");
            }

            AutoSyncScheduler syncScheduler = new AutoSyncScheduler(engine);
            syncScheduler.start();

            // Register JVM runtime shutdown hook for resource cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[App] JVM shutdown initiated. Releasing resources...");
                try {
                    syncScheduler.shutdown();
                    mongoManager.close();
                    System.out.println("[App] All resources successfully released.");
                } catch (Exception ex) {
                    System.err.println("[App] Error during shutdown.");
                }
            }, "arena-shutdown-hook"));

            new WebServer(engine, port);
        } catch (Exception ex) {
            System.err.println("[App] Startup failed. Check database and application configuration securely.");
            System.exit(1);
        }
    }
}
