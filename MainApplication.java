import javafx.application.Application;
import java.util.Scanner;

public class MainApplication {
    public static void main(String[] args) {
        // Use a single Scanner for the entire application.
        try (Scanner scanner = new Scanner(System.in)) {
            boolean keepRunning = true;
            while (keepRunning) {
                System.out.println("\n========= UNIFIED MAIN MENU =========");
                System.out.println("1. Launch GUI Database Manager (JavaFX)");
                System.out.println("2. Launch Console Database Manager");
                System.out.println("3. Launch Console Calculator");
                System.out.println("4. Exit");
                System.out.print("Please choose an application to run (1-4): ");

                if (!scanner.hasNextLine()) {
                    System.out.println("No more input detected. Exiting.");
                    break;
                }
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        System.out.println("Launching GUI Database Manager...");
                        try {
                            // FIXED: This is the correct way to launch a JavaFX application.
                            // It will block until the GUI is closed.
                            Application.launch(FxDb.class, args);
                        } catch (Exception e) {
                            System.err.println("❌ An error occurred launching the GUI. Make sure you have JavaFX configured.");
                            e.printStackTrace();
                        }
                        break;

                    case "2":
                        System.out.println("\n--- Entering Console Database Manager ---");
                        try {
                            DatabaseManager.run(scanner);
                        } catch (Exception e) {
                            System.err.println("❌ An error occurred in Console Database Manager: " + e.getMessage());
                            e.printStackTrace();
                        }
                        System.out.println("--- Returned to Main Menu ---");
                        break;

                    case "3":
                        System.out.println("\n--- Entering Console Calculator ---");
                        try {
                            // FIXED: Added the missing call to the calculator's run method.
                            // Using the new class name ConsoleCalculator.
                            ConsoleCalculator.run(scanner);
                        } catch (Exception e) {
                            System.err.println("❌ An error occurred in Console Calculator: " + e.getMessage());
                            e.printStackTrace();
                        }
                        System.out.println("--- Returned to Main Menu ---");
                        break;

                    case "4":
                        System.out.println("Exiting program. Goodbye!");
                        keepRunning = false; // Exit the while loop.
                        break;

                    default:
                        System.err.println("Invalid choice. Please enter a number between 1 and 4.");
                }
            }
        } // The scanner is automatically closed here by try-with-resources.
    }
}