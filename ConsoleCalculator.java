// FIXED: Renamed class from calculatorf to ConsoleCalculator for clarity and convention.
import java.util.*;

public class ConsoleCalculator {

    private static int precedence(char op) {
        if (op == '+' || op == '-') return 1;
        if (op == '*' || op == '/') return 2;
        return 0; // For parentheses
    }

    private static double applyOperation(double a, double b, char op) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new UnsupportedOperationException("Cannot divide by zero");
                return a / b;
            default:
                throw new IllegalArgumentException("Invalid operator: " + op);
        }
    }

    private static boolean isParenthesesBalanced(String expr) {
        int balance = 0;
        for (char c : expr.toCharArray()) {
            if (c == '(') balance++;
            else if (c == ')') balance--;
            if (balance < 0) return false;
        }
        return balance == 0;
    }

    private static boolean isValidExpression(String expr) {
        // Allow only valid characters
        for (char c : expr.toCharArray()) {
            if (!Character.isDigit(c) && "+-*/() .".indexOf(c) == -1) {
                return false;
            }
        }
        // FIXED: Corrected regex to properly detect consecutive operators.
        // The original `.[+\\-/]{2,}.*` was incorrect.
        if (expr.matches(".*[+\\-*/]{2,}.*")) {
            return false;
        }
        return true;
    }

    private static double evaluateExpression(String expression, Collection<Double> even, Collection<Double> odd) {
        even.clear();
        odd.clear();
        String expr = expression.replaceAll("\\s+", "");
        LinkedList<Double> values = new LinkedList<>();
        LinkedList<Character> ops = new LinkedList<>();

        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                StringBuilder sbuf = new StringBuilder();
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sbuf.append(expr.charAt(i++));
                }
                i--; // Decrement to not skip the next character
                double num = Double.parseDouble(sbuf.toString());
                // Use Math.round for consistent rounding before checking parity
                if (Math.round(num) % 2 == 0) {
                    even.add(num);
                } else {
                    odd.add(num);
                }
                values.add(num);
            } else if (c == '(') {
                ops.add(c);
            } else if (c == ')') {
                while (ops.getLast() != '(') {
                    evaluateTop(values, ops);
                }
                ops.removeLast(); // Pop the opening parenthesis
            } else { // Operator
                while (!ops.isEmpty() && precedence(ops.getLast()) >= precedence(c)) {
                    evaluateTop(values, ops);
                }
                ops.add(c);
            }
            i++;
        }

        while (!ops.isEmpty()) {
            evaluateTop(values, ops);
        }
        return values.getLast();
    }

    // Helper method to reduce code duplication in evaluateExpression
    private static void evaluateTop(LinkedList<Double> values, LinkedList<Character> ops) {
        if (values.size() < 2 || ops.isEmpty()) {
            throw new IllegalArgumentException("Invalid expression format");
        }
        char op = ops.removeLast();
        double b = values.removeLast();
        double a = values.removeLast();
        values.add(applyOperation(a, b, op));
    }


    private static double arrayListMode(String expr, ArrayList<Double> even, ArrayList<Double> odd) {
        return evaluateExpression(expr, even, odd);
    }

    private static double linkedListMode(String expr, LinkedList<Double> even, LinkedList<Double> odd) {
        return evaluateExpression(expr, even, odd);
    }

    private static void addToQueueList(LinkedList<Queue<Double>> list, double number, int capacity) {
        if (list.isEmpty() || list.getLast().size() >= capacity) {
            list.add(new LinkedList<>());
        }
        list.getLast().add(number);
    }

    private static void extractNumbersToQueues(String expression, LinkedList<Queue<Double>> inputQueues, int capacity) {
        String expr = expression.replaceAll("\\s+", "");
        int i = 0;
        while (i < expr.length()) {
            if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.') {
                StringBuilder sbuf = new StringBuilder();
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sbuf.append(expr.charAt(i++));
                }
                i--; // Decrement to not skip the next character
                double num = Double.parseDouble(sbuf.toString());
                addToQueueList(inputQueues, num, capacity);
            }
            i++;
        }
    }

    private static void distributeToEvenOddQueues(LinkedList<Queue<Double>> inputQueues,
                                                  LinkedList<Queue<Double>> evenQueues,
                                                  LinkedList<Queue<Double>> oddQueues,
                                                  int capacity) {
        for (Queue<Double> q : inputQueues) {
            for (double number : q) {
                if (Math.round(number) % 2 == 0) {
                    addToQueueList(evenQueues, number, capacity);
                } else {
                    addToQueueList(oddQueues, number, capacity);
                }
            }
        }
    }

    private static void processQueueMode(String expr, int inputCap, int eoCap,
                                         LinkedList<Queue<Double>> inputQ,
                                         LinkedList<Queue<Double>> evenQ,
                                         LinkedList<Queue<Double>> oddQ) {
        extractNumbersToQueues(expr, inputQ, inputCap);
        distributeToEvenOddQueues(inputQ, evenQ, oddQ, eoCap);
    }

    private static void printQueueList(String label, LinkedList<Queue<Double>> queues) {
        System.out.println(label + ":");
        if (queues.isEmpty()) {
            System.out.println("  (None)");
            return;
        }
        int i = 1;
        for (Queue<Double> q : queues) {
            System.out.println("  Queue " + (i++) + " => " + q);
        }
    }

    private static String fixParentheses(String expr, Scanner scanner) {
        int open = 0, close = 0;
        for (char c : expr.toCharArray()) {
            if (c == '(') open++;
            else if (c == ')') close++;
        }
        String missingChar = (open > close) ? ")" : "(";
        int missingCount = Math.abs(open - close);
        while (true) {
            System.out.printf("--> Unbalanced expression: %d missing '%s' character(s).\n", missingCount, missingChar);
            System.out.printf("--> Enter a position (0 to %d) to insert one '%s', or type 'cancel' to re-enter expression: ", expr.length(), missingChar);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("cancel")) {
                return null;
            }
            try {
                int pos = Integer.parseInt(input);
                if (pos >= 0 && pos <= expr.length()) {
                    String newExpr = expr.substring(0, pos) + missingChar + expr.substring(pos);
                    System.out.println("✅ Updated Expression: " + newExpr);
                    return newExpr;
                } else {
                    System.err.println("❌ Error: Position out of bounds. Please enter a number between 0 and " + expr.length() + ".");
                }
            } catch (NumberFormatException e) {
                System.err.println("❌ Error: Invalid input. Please enter a number for the position or 'cancel'.");
            }
        }
    }

    public static void run(Scanner scanner) {
        System.out.println("\n Welcome to the Multi-Mode Expression Calculator!");
        System.out.println(" (Type 'exit' at any time to return to the main menu)");

        mainLoop:
        while (true) {
            System.out.print("\nEnter a mathematical expression: ");
            String expr = scanner.nextLine();

            if (expr.equalsIgnoreCase("exit")) {
                System.out.println("Returning to main menu...");
                return;
            }

            if (!isValidExpression(expr)) {
                System.err.println("❌ Invalid characters or format in expression. Please use numbers, +, -, *, /, (), and spaces, and do not use consecutive operators.");
                continue;
            }

            while (!isParenthesesBalanced(expr)) {
                expr = fixParentheses(expr, scanner);
                if (expr == null) {
                    System.out.println("Fix canceled. Please enter a new expression.");
                    continue mainLoop;
                }
            }

            boolean stayOnThisExpression = true;
            while (stayOnThisExpression) {
                System.out.println("\nSelect a Calculation Mode for: " + expr);
                System.out.println("1. ArrayList Mode");
                System.out.println("2. LinkedList Mode");
                System.out.println("3. Queue Mode");
                System.out.println("4. Enter a New Expression");
                System.out.println("5. Exit to Main Menu");
                System.out.print("Enter your choice (1-5): ");
                String modeChoice = scanner.nextLine();

                switch (modeChoice) {
                    case "1": {
                        ArrayList<Double> even = new ArrayList<>();
                        ArrayList<Double> odd = new ArrayList<>();
                        try {
                            double result = arrayListMode(expr, even, odd);
                            System.out.println("Result: " + result);
                            System.out.println("Even Numbers: " + even);
                            System.out.println("Odd Numbers: " + odd);
                        } catch (Exception e) {
                            System.err.println("❌ Error during calculation: " + e.getMessage());
                        }
                        break;
                    }
                    case "2": {
                        LinkedList<Double> even = new LinkedList<>();
                        LinkedList<Double> odd = new LinkedList<>();
                        try {
                            double result = linkedListMode(expr, even, odd);
                            System.out.println("Result: " + result);
                            System.out.print("Even Numbers: ");
                            even.forEach(e -> System.out.print(e + " -> "));
                            System.out.println("null");
                            System.out.print("Odd Numbers: ");
                            odd.forEach(o -> System.out.print(o + " -> "));
                            System.out.println("null");
                        } catch (Exception e) {
                            System.err.println("❌ Error during calculation: " + e.getMessage());
                        }
                        break;
                    }
                    case "3": {
                        try {
                            System.out.print("Enter Input Queue Capacity: ");
                            int inputCapacity = Integer.parseInt(scanner.nextLine());
                            System.out.print("Enter Even/Odd Queue Capacity: ");
                            int evenOddCapacity = Integer.parseInt(scanner.nextLine());

                            if (inputCapacity <= 0 || evenOddCapacity <= 0) {
                                System.err.println("❌ Error: Capacities must be positive numbers.");
                                continue;
                            }

                            LinkedList<Queue<Double>> inputQueues = new LinkedList<>();
                            LinkedList<Queue<Double>> evenQueues = new LinkedList<>();
                            LinkedList<Queue<Double>> oddQueues = new LinkedList<>();

                            processQueueMode(expr, inputCapacity, evenOddCapacity, inputQueues, evenQueues, oddQueues);

                            // NOTE: Re-evaluating is slightly inefficient but keeps the logic separate.
                            double result = evaluateExpression(expr, new ArrayList<>(), new ArrayList<>());
                            System.out.println("\nResult: " + result);

                            printQueueList("Input Queues", inputQueues);
                            printQueueList("Even Queues", evenQueues);
                            printQueueList("Odd Queues", oddQueues);
                            System.out.println("--- Queue Summary ---" +
                                    "\n  ➤ Input Queues: " + inputQueues.size() +
                                    "\n  ➤ Even Queues: " + evenQueues.size() +
                                    "\n  ➤ Odd Queues: " + oddQueues.size());

                        } catch (NumberFormatException e) {
                            System.err.println("❌ Error: Invalid input. Please enter valid whole numbers for capacities.");
                        } catch (Exception e) {
                            System.err.println("❌ An unexpected error occurred: " + e.getMessage());
                        }
                        break;
                    }
                    case "4":
                        stayOnThisExpression = false;
                        break;
                    case "5":
                        System.out.println("Returning to main menu...");
                        return;
                    default:
                        System.err.println("Invalid choice. Please enter a number between 1 and 5.");
                }
            }
        }
    }
}