import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String[] builtins = {"echo", "exit", "type", "pwd"};

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.equals("exit 0") || input.equals("exit")) {
                System.exit(0);
            }

            if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
                continue;
            }

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
                continue;
            }

            if (input.startsWith("type ")) {
                String cmd = input.substring(5).trim();
                boolean isBuiltin = false;
                for (String b : builtins) {
                    if (b.equals(cmd)) { isBuiltin = true; break; }
                }
                if (isBuiltin) {
                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }
                String pathEnv = System.getenv("PATH");
                String[] folders = pathEnv.split(":");
                boolean found = false;
                for (String folder : folders) {
                    File f = new File(folder + "/" + cmd);
                    if (f.exists() && f.canExecute()) {
                        System.out.println(cmd + " is " + folder + "/" + cmd);
                        found = true;
                        break;
                    }
                }
                if (!found) System.out.println(cmd + ": not found");
                continue;
            }

            // Try to run as external program
            String[] parts = input.split(" ");
            String pathEnv = System.getenv("PATH");
            String[] folders = pathEnv.split(":");
            boolean found = false;
            for (String folder : folders) {
                File f = new File(folder + "/" + parts[0]);
                if (f.exists() && f.canExecute()) {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.inheritIO();
                    Process p = pb.start();
                    p.waitFor();
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println(input + ": command not found");
            }
        }
    }
}