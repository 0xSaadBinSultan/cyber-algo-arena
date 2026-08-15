import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Application entry point for Cyber-Algo Arena.
 *
 * Usage:
 *   java App              → Interactive CLI mode (original)
 *   java App --web         → Web server on port 8080
 *   java App --web 3000    → Web server on custom port
 *   java App --demo        → Run DemoRunner lifecycle simulation
 *   java App [dataDir]     → CLI mode with custom data directory
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0 && "--web".equals(args[0])) {
                startWebMode(args);
            } else if (args.length > 0 && "--demo".equals(args[0])) {
                DemoRunner.main(new String[0]);
            } else {
                startCliMode(args);
            }
        } catch (Exception ex) {
            System.err.println("Fatal startup error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void startWebMode(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                System.err.println("Invalid port: " + args[1] + ". Using default 8080.");
            }
        }

        Path dataDirectory = Path.of("contest_data");
        Files.createDirectories(dataDirectory);

        ContestEngine engine = new ContestEngine(new FileIOManager(dataDirectory.resolve("challenges.csv")));
        engine.load();

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       Cyber-Algo Arena — Web Server Mode        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        new WebServer(engine, port);
        // Javalin runs on daemon threads; keep main alive
    }

    private static void startCliMode(String[] args) throws Exception {
        Path dataDirectory = args.length > 0 ? Path.of(args[0]) : Path.of("contest_data");
        Files.createDirectories(dataDirectory);
        ContestEngine engine = new ContestEngine(new FileIOManager(dataDirectory.resolve("challenges.csv")));
        CLIController controller = new CLIController(engine, new InputHandler(new Scanner(System.in)));
        controller.start();
    }
}
