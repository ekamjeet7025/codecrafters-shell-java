import java.io.*;
import java.util.*;

public class Main {

    static List<String> parseTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\') {
                i++;
                if (i < input.length()) { current.append(input.charAt(i)); i++; }
            } else if (c == '\'') {
                i++;
                while (i < input.length() && input.charAt(i) != '\'') { current.append(input.charAt(i)); i++; }
                i++;
            } else if (c == '"') {
                i++;
                while (i < input.length() && input.charAt(i) != '"') {
                    if (input.charAt(i) == '\\' && i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"') { current.append(next); i += 2; }
                        else { current.append(input.charAt(i)); i++; }
                    } else { current.append(input.charAt(i)); i++; }
                }
                i++;
            } else if (c == ' ') {
                if (current.length() > 0) { tokens.add(current.toString()); current = new StringBuilder(); }
                i++;
            } else { current.append(c); i++; }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    static class RedirectInfo {
        String stdoutFile = null;
        boolean stdoutAppend = false;
        String stderrFile = null;
        boolean stderrAppend = false;
        List<String> cmdTokens = new ArrayList<>();
    }

    static RedirectInfo parseRedirects(List<String> tokens) {
        RedirectInfo info = new RedirectInfo();
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if ((t.equals(">") || t.equals("1>")) && i + 1 < tokens.size()) {
                info.stdoutFile = tokens.get(i + 1); info.stdoutAppend = false; i += 2;
            } else if ((t.equals(">>") || t.equals("1>>")) && i + 1 < tokens.size()) {
                info.stdoutFile = tokens.get(i + 1); info.stdoutAppend = true; i += 2;
            } else if (t.equals("2>") && i + 1 < tokens.size()) {
                info.stderrFile = tokens.get(i + 1); info.stderrAppend = false; i += 2;
            } else if (t.equals("2>>") && i + 1 < tokens.size()) {
                info.stderrFile = tokens.get(i + 1); info.stderrAppend = true; i += 2;
            } else { info.cmdTokens.add(t); i++; }
        }
        return info;
    }

    static void setRawMode() {
        try {
            new ProcessBuilder("/bin/sh", "-c", "stty -echo raw </dev/tty")
                .inheritIO().start().waitFor();
        } catch (Exception e) {}
    }

    static void setNormalMode() {
        try {
            new ProcessBuilder("/bin/sh", "-c", "stty sane </dev/tty")
                .inheritIO().start().waitFor();
        } catch (Exception e) {}
    }

    static String[] ALL_BUILTINS = {"echo", "exit", "type", "pwd", "cd", "jobs"};

    static List<String> getMatches(String partial) {
        List<String> matches = new ArrayList<>();
        for (String b : ALL_BUILTINS) {
            if (b.startsWith(partial)) matches.add(b);
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String folder : pathEnv.split(":")) {
                File dir = new File(folder);
                if (dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().startsWith(partial) && f.canExecute() && !matches.contains(f.getName())) {
                                matches.add(f.getName());
                            }
                        }
                    }
                }
            }
        }
        Collections.sort(matches);
        return matches;
    }

    static String longestCommonPrefix(List<String> matches) {
        if (matches.isEmpty()) return "";
        String prefix = matches.get(0);
        for (String m : matches) {
            while (!m.startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) return "";
            }
        }
        return prefix;
    }

    static String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        FileInputStream tty = new FileInputStream("/dev/tty");
        int tabCount = 0;

        while (true) {
            int ch = tty.read();
            if (ch == '\r' || ch == '\n') {
                System.out.print("\r\n");
                System.out.flush();
                tabCount = 0;
                break;
            } else if (ch == 127 || ch == 8) {
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    System.out.print("\b \b");
                    System.out.flush();
                }
                tabCount = 0;
            } else if (ch == '\t') {
                String partial = sb.toString();
                List<String> matches = getMatches(partial);
                if (matches.size() == 0) {
                    System.out.print("\007");
                    System.out.flush();
                    tabCount = 0;
                } else if (matches.size() == 1) {
                    String completion = matches.get(0);
                    for (int i = 0; i < sb.length(); i++) System.out.print("\b \b");
                    System.out.print(completion + " ");
                    System.out.flush();
                    sb = new StringBuilder(completion + " ");
                    tabCount = 0;
                } else {
                    String lcp = longestCommonPrefix(matches);
                    if (lcp.length() > partial.length()) {
                        for (int i = 0; i < sb.length(); i++) System.out.print("\b \b");
                        System.out.print(lcp);
                        System.out.flush();
                        sb = new StringBuilder(lcp);
                        tabCount = 0;
                    } else {
                        tabCount++;
                        if (tabCount == 1) {
                            System.out.print("\007");
                            System.out.flush();
                        } else {
                            System.out.print("\r\n");
                            System.out.print(String.join("  ", matches));
                            System.out.print("\r\n$ " + partial);
                            System.out.flush();
                            tabCount = 0;
                        }
                    }
                }
            } else if (ch >= 32) {
                sb.append((char) ch);
                System.out.print((char) ch);
                System.out.flush();
                tabCount = 0;
            }
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        String currentDir = System.getProperty("user.dir");
        List<int[]> jobs = new ArrayList<>(); // [jobNumber, pid]

        setRawMode();

        try {
            while (true) {
                System.out.print("$ ");
                System.out.flush();
                String input = readLine().trim();

                if (input.equals("exit 0") || input.equals("exit")) {
                    setNormalMode();
                    System.exit(0);
                }

                if (input.equals("pwd")) {
                    System.out.print(currentDir + "\r\n");
                    continue;
                }

                List<String> allTokens = parseTokens(input);
                if (allTokens.isEmpty()) continue;

                RedirectInfo redirect = parseRedirects(allTokens);
                List<String> tokens = redirect.cmdTokens;
                if (tokens.isEmpty()) continue;

                PrintStream outStream = originalOut;
                PrintStream errStream = originalErr;
                if (redirect.stdoutFile != null)
                    outStream = new PrintStream(new FileOutputStream(redirect.stdoutFile, redirect.stdoutAppend));
                if (redirect.stderrFile != null)
                    errStream = new PrintStream(new FileOutputStream(redirect.stderrFile, redirect.stderrAppend));

                String cmd = tokens.get(0);

                if (cmd.equals("jobs")) {
                    // empty for now
                    continue;
                }

                if (cmd.equals("cd")) {
                    String path = tokens.size() > 1 ? tokens.get(1) : System.getenv("HOME");
                    if (path.equals("~")) path = System.getenv("HOME");
                    File dir = new File(path);
                    if (!dir.isAbsolute()) dir = new File(currentDir, path);
                    if (dir.exists() && dir.isDirectory()) {
                        currentDir = dir.getCanonicalPath();
                    } else {
                        System.out.print("cd: " + path + ": No such file or directory\r\n");
                    }
                    continue;
                }

                if (cmd.equals("echo")) {
                    if (tokens.size() == 1) {
                        outStream.print("\r\n");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < tokens.size(); i++) {
                            if (i > 1) sb.append(" ");
                            sb.append(tokens.get(i));
                        }
                        outStream.print(sb.toString() + "\r\n");
                    }
                    if (redirect.stdoutFile != null) outStream.close();
                    if (redirect.stderrFile != null) errStream.close();
                    continue;
                }

                if (cmd.equals("type")) {
                    String typeCmd = tokens.size() > 1 ? tokens.get(1) : "";
                    boolean isBuiltin = false;
                    for (String b : ALL_BUILTINS) {
                        if (b.equals(typeCmd)) { isBuiltin = true; break; }
                    }
                    if (isBuiltin) {
                        outStream.print(typeCmd + " is a shell builtin\r\n");
                        if (redirect.stdoutFile != null) outStream.close();
                        continue;
                    }
                    String pathEnv = System.getenv("PATH");
                    String[] folders = pathEnv.split(":");
                    boolean found = false;
                    for (String folder : folders) {
                        File f = new File(folder + "/" + typeCmd);
                        if (f.exists() && f.canExecute()) {
                            outStream.print(typeCmd + " is " + folder + "/" + typeCmd + "\r\n");
                            found = true;
                            break;
                        }
                    }
                    if (!found) outStream.print(typeCmd + ": not found\r\n");
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
                        setNormalMode();
                        ProcessBuilder pb = new ProcessBuilder(tokens);
                        pb.directory(new File(currentDir));
                        if (redirect.stdoutFile != null)
                            pb.redirectOutput(redirect.stdoutAppend ? ProcessBuilder.Redirect.appendTo(new File(redirect.stdoutFile)) : ProcessBuilder.Redirect.to(new File(redirect.stdoutFile)));
                        else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        if (redirect.stderrFile != null)
                            pb.redirectError(redirect.stderrAppend ? ProcessBuilder.Redirect.appendTo(new File(redirect.stderrFile)) : ProcessBuilder.Redirect.to(new File(redirect.stderrFile)));
                        else pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        Process p = pb.start();
                        p.waitFor();
                        setRawMode();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    originalOut.print(input + ": command not found\r\n");
                }
                if (redirect.stdoutFile != null) outStream.close();
                if (redirect.stderrFile != null) errStream.close();
            }
        } finally {
            setNormalMode();
        }
    }
}