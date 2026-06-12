import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String[] builtins = {"echo", "exit", "type"};

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.equals("exit 0") || input.equals("exit")) {
                System.exit(0);
            }

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
                continue;
            }

            if (input.startsWith("type ")) {
                String cmd = input.substring(5).trim();
                boolean isBuiltin = false;
                for (String b : builtins) {
                    if (b.equals(cmd)) {
                        isBuiltin = true;
                        break;
                    }
                }
                if (isBuiltin) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    System.out.println(cmd + ": not found");
                }
                continue;
            }

            System.out.println(input + ": command not found");
        }
    }
}