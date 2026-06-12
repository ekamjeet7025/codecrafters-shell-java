import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class Main {

    static List<String> parseTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\') {
                // Backslash outside quotes: skip backslash, keep next char literally
                i++;
                if (i < input.length()) {
                    current.append(input.charAt(i));
                    i++;
                }
            } else if (c == '\'') {
                // Single quotes: everything literal including backslashes
                i++;
                while (i < input.length() && input.charAt(i) != '\'') {
                    current.append(input.charAt(i));
                    i++;
                }
                i++; // skip closing quote
            } else if (c == '"') {
                // Double quotes: only \\ and \" are special
                i++;
                while (i < input.length() && input.charAt(i) != '"') {
                    if (input.charAt(i) == '\\' && i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"') {
                            current.append(next);
                            i += 2;
                        } else {
                            current.append(input.charAt(i));
                            i++;
                        }
                    } else {
                        current.append(input.charAt(i));
                        i++;
                    }
                }
                i++; // skip closing quote
            } else if (c == ' ') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

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

            List<String> tokens = parseTokens(input);
            if (tokens.isEmpty()) continue;
            String cmd = tokens.get(0);

            if (cmd.equals("echo")) {
                if (tokens.size() == 1) {
                    System.out.println();
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < tokens.size(); i++) {
                        if (i > 1) sb.append(" ");
                        sb.append(tokens.get(i));
                    }
                    System.out.println(sb.toString());
                }
                continue;
            }

            if (cmd.equals("type")) {
                String typeCmd = tokens.size() > 1 ? tokens.get(1) : "";
                boolean isBuiltin = false;
                for (String b : builtins) {
                    if (b.equals(typeCmd)) { isBuiltin = true; break; }
                }
                if (isBuiltin) {
                    System.out.println(typeCmd + " is a shell builtin");
                    continue;
                }
                String pathEnv = System.getenv("PATH");
                String[] folders = pathEnv.split(":");
                boolean found = false;
                for (String folder : folders) {
                    File f = new File(folder + "/" + typeCmd);
                    if (f.exists() && f.canExecute()) {
                        System.out.println(typeCmd + " is " + folder + "/" + typeCmd);
                        found = true;
                        break;
                    }
                }
                if (!found) System.out.println(typeCmd + ": not found");
                continue;
            }

            // Try to run as external program
            String pathEnv = System.getenv("PATH");
            String[] folders = pathEnv.split(":");
            boolean found = false;
            for (String folder : folders) {
                File f = new File(folder + "/" + cmd);
                if (f.exists() && f.canExecute()) {
                    ProcessBuilder pb = new ProcessBuilder(tokens);
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