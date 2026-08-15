import java.util.Objects;
import java.util.Scanner;

/** Safe line-based console input. Invalid values reprompt without terminating the state machine. */
public final class InputHandler {

    private final Scanner scanner;

    public InputHandler(Scanner scanner) {
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
    }

    public String readNonEmpty(String prompt) {
        while (true) {
            String value = readRaw(prompt).trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Input must not be empty.");
        }
    }

    public String readOptional(String prompt) {
        return readRaw(prompt).trim();
    }

    public int readInt(String prompt) {
        while (true) {
            String value = readNonEmpty(prompt);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                System.out.println("Enter a valid integer.");
            }
        }
    }

    public long readLong(String prompt) {
        while (true) {
            String value = readNonEmpty(prompt);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                System.out.println("Enter a valid long integer.");
            }
        }
    }

    public int readMenuChoice(String prompt, int minInclusive, int maxInclusive) {
        while (true) {
            int choice = readInt(prompt);
            if (choice >= minInclusive && choice <= maxInclusive) {
                return choice;
            }
            System.out.println("Choose an option between " + minInclusive + " and " + maxInclusive + ".");
        }
    }

    public boolean readConfirmation(String prompt) {
        while (true) {
            String value = readNonEmpty(prompt + " [y/n]: ").toLowerCase();
            if (value.equals("y") || value.equals("yes")) {
                return true;
            }
            if (value.equals("n") || value.equals("no")) {
                return false;
            }
            System.out.println("Enter y or n.");
        }
    }

    private String readRaw(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println();
            throw new IllegalStateException("Input stream closed");
        }
        return scanner.nextLine();
    }
}
