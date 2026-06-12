import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Main {

    static List<String> parseTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\') {
                i++;
                if (i < input.length()) {
                    current.append(input.charAt(i));
                    i++;
                }
            } else if (c == '\'') {
                i++;
                while (i < input.length() && input.charAt(i) != '\'') {
                    current.append(input.charAt(i));
                    i++;
                }
                i++;
            } else if (c == '"') {
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
                i++;
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

    // Find redirect output file if > or 1> exists, returns null if none
    static String findRedirectFile(List<String> tokens, List<String> cmdTokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals(">") || t.equals("1>")) {
                // everything before is the command, next token is the file
                for (int j = 0; j < i; j++) cmdTokens.add(tokens.get(j));
                return i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            }
        }
        cmdTokens.addAll(tokens);
        return null;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String[] builtins = {"echo", "exit", "type", "pwd"};
        PrintStream originalOut = System.out;

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

            List<String> allTokens = parseTokens(input);
            if (allTokens.isEmpty()) continue;

            List<String> tokens = new ArrayList<>();
            String redirectFile = findRedirectFile(allTokens, tokens);

            // Set up output stream
            PrintStream outStream = originalOut;
            if (redirectFile != null) {
                outStream = new PrintStream(new FileOutputStream(redirectFile, false));
            }

            String cmd = tokens.get(0);

            if (cmd.equals("echo")) {
                if (tokens.size() == 1) {
                    outStream.println();
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < tokens.size(); i++) {
                        if (i > 1) sb.append(" ");
                        sb.append(tokens.get(i));
                    }
                    outStream.println(sb.toString());
                }
                if (redirectFile != null) outStream.close();
                continue;
            }

            if (cmd.equals("type")) {
                String typeCmd = tokens.size() > 1 ? tokens.get(1) : "";
                boolean isBuiltin = false;
                for (String b : builtins) {
                    if (b.equals(typeCmd)) { isBuiltin = true; break; }
                }
                if (isBuiltin) {
                    outStream.println(typeCmd + " is a shell builtin");
                    if (redirectFile != null) outStream.close();
                    continue;
                }
                String pathEnv = System.getenv("PATH");
                String[] folders = pathEnv.split(":");
                boolean found = false;
                for (String folder : folders) {
                    File f = new File(folder + "/" + typeCmd);
                    if (f.exists() && f.canExecute()) {
                        outStream.println(typeCmd + " is " + folder + "/" + typeCmd);
                        found = true;
                        break;
                    }
                }
                if (!found) outStream.println(typeCmd + ": not found");
                if (redirectFile != null) outStream.close();
                continue;
            }

            // External program
            String pathEnv = System.getenv("PATH");
            String[] folders = pathEnv.split(":");
            boolean found = false;
            for (String folder : folders) {
                File f = new File(folder + "/" + cmd);
                if (f.exists() && f.canExecute()) {
                    ProcessBuilder pb = new ProcessBuilder(tokens);
                    if (redirectFile != null) {
                        pb.redirectOutput(new File(redirectFile));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    Process p = pb.start();
                    p.waitFor();
                    found = true;
                    break;
                }
            }
            if (!found) {
                originalOut.println(input + ": command not found");
            }
            if (redirectFile != null && outStream != originalOut) outStream.close();
        }
    }
}