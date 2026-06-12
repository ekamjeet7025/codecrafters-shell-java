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

    static class RedirectInfo {
        String stdoutFile = null;  // for > or 1>
        String stderrFile = null;  // for 2>
        List<String> cmdTokens = new ArrayList<>();
    }

    static RedirectInfo parseRedirects(List<String> tokens) {
        RedirectInfo info = new RedirectInfo();
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if ((t.equals(">") || t.equals("1>")) && i + 1 < tokens.size()) {
                info.stdoutFile = tokens.get(i + 1);
                i += 2;
            } else if (t.equals("2>") && i + 1 < tokens.size()) {
                info.stderrFile = tokens.get(i + 1);
                i += 2;
            } else {
                info.cmdTokens.add(t);
                i++;
            }
        }
        return info;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String[] builtins = {"echo", "exit", "type", "pwd"};
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

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

            RedirectInfo redirect = parseRedirects(allTokens);
            List<String> tokens = redirect.cmdTokens;
            if (tokens.isEmpty()) continue;

            // Set up streams
            PrintStream outStream = originalOut;
            PrintStream errStream = originalErr;
            if (redirect.stdoutFile != null) {
                outStream = new PrintStream(new FileOutputStream(redirect.stdoutFile, false));
            }
            if (redirect.stderrFile != null) {
                errStream = new PrintStream(new FileOutputStream(redirect.stderrFile, false));
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
                if (redirect.stdoutFile != null) outStream.close();
                if (redirect.stderrFile != null) errStream.close();
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
                    if (redirect.stdoutFile != null) outStream.close();
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
                if (redirect.stdoutFile != null) outStream.close();
                if (redirect.stderrFile != null) errStream.close();
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
                    if (redirect.stdoutFile != null) {
                        pb.redirectOutput(new File(redirect.stdoutFile));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                    if (redirect.stderrFile != null) {
                        pb.redirectError(new File(redirect.stderrFile));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    Process p = pb.start();
                    p.waitFor();
                    found = true;
                    break;
                }
            }
            if (!found) {
                originalOut.println(input + ": command not found");
            }
            if (redirect.stdoutFile != null) outStream.close();
            if (redirect.stderrFile != null) errStream.close();
        }
    }
}