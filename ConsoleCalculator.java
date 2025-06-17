import java.sql.*;
import java.util.*;
import java.util.NoSuchElementException;

public class Cons {

    // --- DATABASE CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/crudop";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Prasad@01";

    private static Connection connection = null;

    //<editor-fold desc="Database Management Methods">
    private static boolean connectToDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            return true;
        } catch (Exception e) {
            System.err.println("\n❌ DATABASE ERROR: Could not connect. Calculator will run without history features.");
            return false;
        }
    }

    private static void setupDatabaseTable() {
        if (connection == null) return;
        String createTableSQL = "CREATE TABLE IF NOT EXISTS calculation_history ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "expression VARCHAR(255) NOT NULL,"
                + "result DOUBLE NOT NULL,"
                + "calculation_mode VARCHAR(50) NOT NULL,"
                + "calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            System.err.println("❌ Could not create or verify database table: " + e.getMessage());
            connection = null;
        }
    }

    private static void closeDatabaseConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // Error not critical on shutdown, can be ignored.
        }
    }

    private static void saveCalculation(String expression, double result, String mode) {
        if (connection == null) return;
        String insertSQL = "INSERT INTO calculation_history (expression, result, calculation_mode) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setString(1, expression);
            pstmt.setDouble(2, result);
            pstmt.setString(3, mode);
            pstmt.executeUpdate();
            System.out.println("--> Calculation saved to history.");
        } catch (SQLException e) {
            System.err.println("❌ Could not save calculation to history: " + e.getMessage());
        }
    }

    private static void viewHistory() {
        if (connection == null) {
            System.out.println("Database connection not available. History feature is disabled.");
            return;
        }
        String selectSQL = "SELECT id, expression, result, calculation_mode, calculated_at FROM calculation_history ORDER BY calculated_at DESC LIMIT 20";
        System.out.println("\n--- Calculation History (Last 20 Entries) ---");
        System.out.println("=========================================================================================");
        System.out.printf("%-5s | %-30s | %-15s | %-15s | %-20s%n", "ID", "Expression", "Result", "Mode", "Timestamp");
        System.out.println("-----------------------------------------------------------------------------------------");
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {
            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                System.out.printf("%-5d | %-30s | %-15.4f | %-15s | %s%n",
                        rs.getInt("id"),
                        rs.getString("expression"),
                        rs.getDouble("result"),
                        rs.getString("calculation_mode"),
                        rs.getTimestamp("calculated_at").toString());
            }
            if (!hasRows) {
                System.out.println("No history found in the database.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Could not retrieve history: " + e.getMessage());
        }
        System.out.println("=========================================================================================");
    }
    //</editor-fold>

    //<editor-fold desc="Calculation Logic (unchanged)">
    private static int precedence(char op) { if (op == '+' || op == '-') return 1; if (op == '*' || op == '/') return 2; return 0; }
    private static double applyOperation(double a, double b, char op) { switch (op) { case '+': return a + b; case '-': return a - b; case '*': return a * b; case '/': if (b == 0) throw new UnsupportedOperationException("Cannot divide by zero"); return a / b; default: throw new IllegalArgumentException("Invalid operator: " + op); } }
    private static int checkBalance(String expr) { int balance = 0; for (char c : expr.toCharArray()) { if (c == '(') balance++; else if (c == ')') balance--; } return balance; }
    private static boolean isValidExpression(String expr) { String cleanExpr = expr.replaceAll("\\s+", ""); if (cleanExpr.isEmpty()) return false; if (cleanExpr.contains("()")) return false; if (cleanExpr.matches(".*[+\\-*/]\\).*")) return false; if (cleanExpr.matches(".*\\([*/].*")) return false; if (cleanExpr.matches("^[*/+].*|.*[+\\-*/]$")) return false; if (cleanExpr.matches(".*[+\\-*/]{2,}.*")) return false; for (char c : cleanExpr.toCharArray()) { if (!Character.isDigit(c) && "+-*/() .".indexOf(c) == -1) return false; } return true; }
    private static double evaluateExpression(String expression, Collection<Double> even, Collection<Double> odd) { even.clear(); odd.clear(); String expr = expression.replaceAll("\\s+", "").replaceAll("(?<=\\d)(?=\\()", "*").replaceAll("(?<=\\))(?=\\d)", "*").replaceAll("(?<=\\))(?=\\()", "*"); LinkedList<Double> values = new LinkedList<>(); LinkedList<Character> ops = new LinkedList<>(); int i = 0; while (i < expr.length()) { char c = expr.charAt(i); if (Character.isDigit(c) || c == '.') { StringBuilder sbuf = new StringBuilder(); while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) { sbuf.append(expr.charAt(i++)); } i--; double num = Double.parseDouble(sbuf.toString()); if (Math.round(num) % 2 == 0) even.add(num); else odd.add(num); values.add(num); } else if (c == '(') { ops.add(c); } else if (c == ')') { while (ops.getLast() != '(') { char op = ops.removeLast(); try { double b = values.removeLast(); double a = values.removeLast(); values.add(applyOperation(a, b, op)); } catch (NoSuchElementException e) { throw new IllegalArgumentException("Invalid syntax near '" + op + "'."); } } ops.removeLast(); } else { if (c == '-' && (i == 0 || "+-*/(".indexOf(expr.charAt(i - 1)) != -1)) { values.add(0.0); } while (!ops.isEmpty() && precedence(ops.getLast()) >= precedence(c)) { char op = ops.removeLast(); try { double b = values.removeLast(); double a = values.removeLast(); values.add(applyOperation(a, b, op)); } catch (NoSuchElementException e) { throw new IllegalArgumentException("Invalid syntax near '" + op + "'."); } } ops.add(c); } i++; } while (!ops.isEmpty()) { char op = ops.removeLast(); try { double b = values.removeLast(); double a = values.removeLast(); values.add(applyOperation(a, b, op)); } catch (NoSuchElementException e) { throw new IllegalArgumentException("Invalid syntax near '" + op + "'."); } } if (values.size() != 1) { throw new IllegalArgumentException("Invalid expression: Leftover numbers."); } return values.getLast(); }
    private static double arrayListMode(String expr, ArrayList<Double> even, ArrayList<Double> odd) { return evaluateExpression(expr, even, odd); }
    private static double linkedListMode(String expr, LinkedList<Double> even, LinkedList<Double> odd) { return evaluateExpression(expr, even, odd); }
    private static void addToQueueList(LinkedList<Queue<Double>> list, double number, int capacity) { if (list.isEmpty() || list.getLast().size() >= capacity) { list.add(new LinkedList<>()); } list.getLast().add(number); }
    private static void extractNumbersToQueues(String expression, LinkedList<Queue<Double>> inputQueues, int capacity) { String expr = expression.replaceAll("\\s+", "").replaceAll("(?<=\\d)(?=\\()", "").replaceAll("(?<=\\))(?=\\d)", "").replaceAll("(?<=\\))(?=\\()", "*"); int i = 0; while (i < expr.length()) { if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.') { StringBuilder sbuf = new StringBuilder(); while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) { sbuf.append(expr.charAt(i++)); } i--; double num = Double.parseDouble(sbuf.toString()); addToQueueList(inputQueues, num, capacity); } i++; } }
    private static void distributeToEvenOddQueues(LinkedList<Queue<Double>> inputQueues, LinkedList<Queue<Double>> evenQueues, LinkedList<Queue<Double>> oddQueues, int capacity) { for (Queue<Double> q : inputQueues) { for (double number : q) { if (Math.round(number) % 2 == 0) { addToQueueList(evenQueues, number, capacity); } else { addToQueueList(oddQueues, number, capacity); } } } }
    private static void processQueueMode(String expr, int inputCap, int eoCap, LinkedList<Queue<Double>> inputQ, LinkedList<Queue<Double>> evenQ, LinkedList<Queue<Double>> oddQ) { extractNumbersToQueues(expr, inputQ, inputCap); distributeToEvenOddQueues(inputQ, evenQ, oddQ, eoCap); }
    private static void printQueueList(String label, LinkedList<Queue<Double>> queues) { System.out.println(label + ":"); if (queues.isEmpty()) { System.out.println("  (None)"); return; } int i = 1; for (Queue<Double> q : queues) { System.out.println("  Queue " + (i++) + " => " + q); } }
    private static String fixParentheses(String expr, Scanner scanner) { int balance = checkBalance(expr); if (balance < 0) { System.out.printf("--> Unbalanced expression: %d missing '('. Please re-enter.\n", Math.abs(balance)); return null; } String missingChar = ")"; int missingCount = balance; String pluralSuffix = missingCount > 1 ? "s" : ""; System.out.printf("--> Unbalanced expression: %d missing '%s' character%s.\n", missingCount, missingChar, pluralSuffix); while (true) { System.out.printf("--> Enter a position (0 to %d) to insert one '%s', or type 'cancel': ", expr.length(), missingChar); String input = scanner.nextLine().trim(); if (input.equalsIgnoreCase("cancel")) return null; try { int pos = Integer.parseInt(input); if (pos >= 0 && pos <= expr.length()) { String newExpr = expr.substring(0, pos) + missingChar + expr.substring(pos); System.out.println("? Updated Expression: " + newExpr); return newExpr; } else { System.out.println("? Error: Position must be between 0 and " + expr.length() + "."); } } catch (NumberFormatException e) { System.out.println("? Input recognized as a new expression."); System.out.println("? Updated Expression: " + input); return input; } } }
    //</editor-fold>

    /**
     * The main application loop for the console calculator. It is called by MainApplication.
     * @param scanner The Scanner object passed from the main application to use for input.
     */
    public static void run(Scanner scanner) {
        if (connectToDatabase()) {
            setupDatabaseTable();
        }

        mainLoop:
        while (true) {
            System.out.print("\nEnter a mathematical expression (or 'back' to return to menu): ");
            String expr = scanner.nextLine();

            if (expr.equalsIgnoreCase("back")) {
                closeDatabaseConnection(); // Clean up DB connection before returning
                return; // This exits the run() method and returns control to MainApplication
            }

            while (checkBalance(expr) != 0) {
                expr = fixParentheses(expr, scanner);
                if (expr == null) {
                    continue mainLoop;
                }
            }
             if (!isValidExpression(expr)) {
                System.out.println("❌ Invalid Expression. Please check your syntax.");
                continue;
            }

            boolean stayOnThisExpression = true;
            while (stayOnThisExpression) {
                System.out.println("\nSelect a Mode for: " + expr);
                System.out.println("1. ArrayList Mode");
                System.out.println("2. LinkedList Mode");
                System.out.println("3. Queue Mode");
                System.out.println("4. View Calculation History");
                System.out.println("5. Enter a New Expression");
                System.out.println("6. Back to Main Menu");
                System.out.print("Enter your choice (1-6): ");
                String modeChoice = scanner.nextLine();

                switch (modeChoice) {
                    case "1": { // ArrayList Mode
                        try {
                            ArrayList<Double> even = new ArrayList<>();
                            ArrayList<Double> odd = new ArrayList<>();
                            double result = arrayListMode(expr, even, odd);
                            System.out.println("\nResult: " + result);
                            System.out.println("Even Numbers: " + even);
                            System.out.println("Odd Numbers: " + odd);
                            saveCalculation(expr, result, "ArrayList");
                        } catch (Exception e) {
                             System.out.println("❌ Error during calculation: " + e.getMessage());
                        }
                        break;
                    }
                    case "2": { // LinkedList Mode
                        try {
                            LinkedList<Double> even = new LinkedList<>();
                            LinkedList<Double> odd = new LinkedList<>();
                            double result = linkedListMode(expr, even, odd);
                            System.out.println("\nResult: " + result);
                            System.out.print("Even Numbers: ");
                            even.forEach(e -> System.out.print(e + " -> "));
                            System.out.println("null");
                            System.out.print("Odd Numbers: ");
                            odd.forEach(o -> System.out.print(o + " -> "));
                            System.out.println("null");
                            saveCalculation(expr, result, "LinkedList");
                        } catch (Exception e) {
                             System.out.println("❌ Error during calculation: " + e.getMessage());
                        }
                        break;
                    }
                    case "3": { // Queue Mode
                        try {
                            System.out.print("Enter Input Queue Capacity: ");
                            int inputCapacity = Integer.parseInt(scanner.nextLine());
                            System.out.print("Enter Even/Odd Queue Capacity: ");
                            int evenOddCapacity = Integer.parseInt(scanner.nextLine());
                            if (inputCapacity <= 0 || evenOddCapacity <= 0) {
                                System.out.println("❌ Error: Capacities must be positive.");
                                continue;
                            }
                            LinkedList<Queue<Double>> iQ = new LinkedList<>(), eQ = new LinkedList<>(), oQ = new LinkedList<>();
                            processQueueMode(expr, inputCapacity, evenOddCapacity, iQ, eQ, oQ);
                            double result = evaluateExpression(expr, new ArrayList<>(), new ArrayList<>());
                            System.out.println("\nResult: " + result);
                            printQueueList("Input Queues", iQ);
                            printQueueList("Even Queues", eQ);
                            printQueueList("Odd Queues", oQ);
                            saveCalculation(expr, result, "Queue");
                        } catch (Exception e) {
                            System.out.println("❌ An unexpected error occurred: " + e.getMessage());
                        }
                        break;
                    }
                    case "4":
                        viewHistory();
                        break;
                    case "5":
                        stayOnThisExpression = false; // Go back to asking for a new expression
                        break;
                    case "6":
                        closeDatabaseConnection();
                        return; // This exits the run() method and returns control to MainApplication
                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 6.");
                }
            }
        }
    }

    // FIXED: The main() method has been REMOVED.
    // The program's only entry point is MainApplication.main().
}
