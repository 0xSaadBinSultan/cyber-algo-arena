import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application entry point for Cyber-Algo Arena.
 * Multi-contest platform backed by MongoDB.
 *
 * Usage:
 *   java -jar app.jar                → Runs Web Server on port 8080 (default)
 *   java -jar app.jar --web 3000     → Runs Web Server on custom port
 *   java -jar app.jar --demo         → Runs automated lifecycle test
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0 && "--demo".equals(args[0])) {
                DemoRunner.main(new String[0]);
                return;
            }

            int port = 8080;
            if (args.length > 1 && "--web".equals(args[0])) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }

            String mongoUri = System.getenv().getOrDefault("MONGODB_URI", MongoManager.DEFAULT_URI);
            String mongoDb = System.getenv().getOrDefault("MONGODB_DATABASE_NAME", MongoManager.DEFAULT_DB_NAME);

            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║   Cyber-Algo Arena — Multi-Contest Web Engine   ║");
            System.out.println("║   MongoDB Cutover: " + mongoDb + "             ║");
            System.out.println("╚══════════════════════════════════════════════════╝");

            // Ensure attachment directories exist
            Files.createDirectories(Path.of("contest_data", "attachments"));

            MongoManager mongoManager = new MongoManager(mongoUri, mongoDb);
            MongoRepository repository = new MongoRepository(mongoManager);
            ContestEngine engine = new ContestEngine(repository);
            engine.load();

            new WebServer(engine, port);
        } catch (Exception ex) {
            System.err.println("Fatal startup error: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
