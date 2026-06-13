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

    static String[] ALL_BUILTINS = {"echo", "exit", "type", "pwd", "cd", "jobs"};

    static class Job {
        int jobNumber;
        long pid;
        String command;
        Process process;

        Job(int jobNumber, long pid, String command, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    static List<Job> jobList = new ArrayList<>();

    static int nextJobNumber() {
        Set<Integer> used = new HashSet<>();
        for (Job j : jobList) used.add(j.jobNumber);
        int n = 1;
        while (used.contains(n)) n++;
        return n;
    }

    static char getMarker(Job job) {
        List<Job> sorted = new ArrayList<>(jobList);
        sorted.sort((a, b) -> a.jobNumber - b.jobNumber);
        int size = sorted.size();
        if (size == 0) return ' ';
        if (job.jobNumber == sorted.get(size - 1).jobNumber) return '+';
        if (size >= 2 && job.jobNumber == sorted.get(size - 2).jobNumber) return '-';
        return ' ';
    }

    static String formatStatus(String status) {
        return String.format("%-24s", status);
    }

    static void reapJobs(PrintStream out) {
        List<Job> sorted = new ArrayList<>(jobList);
        sorted.sort((a, b) -> a.jobNumber - b.jobNumber);
        List<Job> toRemove = new ArrayList<>();
        for (Job j : sorted) {
            if (!j.process.isAlive()) {
                char marker = getMarker(j);
                out.println("[" + j.jobNumber + "]" + marker + "  " + formatStatus("Done") + j.command);
                toRemove.add(j);
            }
        }
        jobList.removeAll(toRemove);
    }

    static String findInPath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String folder : pathEnv.split(":")) {
            File f = new File(folder + "/" + cmd);
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    static boolean isBuiltin(String cmd) {
        for (String b : ALL_BUILTINS) if (b.equals(cmd)) return true;
        return false;
    }

    // Split input by | respecting quotes
    static List<String> splitByPipe(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '|' && !inSingle && !inDouble) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString().trim());
        return parts;
    }

    // Execute builtin and write output to a PrintStream
    static void execBuiltin(List<String> tokens, PrintStream out, PrintStream err, String currentDir) {
        String cmd = tokens.get(0);
        if (cmd.equals("echo")) {
            if (tokens.size() == 1) { out.println(); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < tokens.size(); i++) {
                if (i > 1) sb.append(" ");
                sb.append(tokens.get(i));
            }
            out.println(sb.toString());
        } else if (cmd.equals("type")) {
            String typeCmd = tokens.size() > 1 ? tokens.get(1) : "";
            if (isBuiltin(typeCmd)) {
                out.println(typeCmd + " is a shell builtin");
            } else {
                String path = findInPath(typeCmd);
                if (path != null) out.println(typeCmd + " is " + path);
                else out.println(typeCmd + ": not found");
            }
        } else if (cmd.equals("pwd")) {
            out.println(currentDir);
        }
    }

    static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        currentDir = System.getProperty("user.dir");

        while (true) {
            reapJobs(originalOut);

            System.out.print("$ ");
            System.out.flush();
            String input = scanner.nextLine().trim();

            if (input.equals("exit 0") || input.equals("exit")) {
                System.exit(0);
            }

            // Check for pipeline
            List<String> pipelineParts = splitByPipe(input);

            if (pipelineParts.size() > 1) {
                // PIPELINE execution
                executePipeline(pipelineParts, originalOut, originalErr);
                continue;
            }

            // Single command
            List<String> allTokens = parseTokens(input);
            if (allTokens.isEmpty()) continue;

            boolean isBackground = false;
            if (allTokens.get(allTokens.size() - 1).equals("&")) {
                isBackground = true;
                allTokens.remove(allTokens.size() - 1);
            }

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
                List<Job> allSorted = new ArrayList<>(jobList);
                allSorted.sort((a, b) -> a.jobNumber - b.jobNumber);
                List<Job> doneJobs = new ArrayList<>();
                for (Job j : allSorted) if (!j.process.isAlive()) doneJobs.add(j);
                for (Job j : allSorted) {
                    char marker = getMarker(j);
                    boolean isDone = !j.process.isAlive();
                    if (isDone) originalOut.println("[" + j.jobNumber + "]" + marker + "  " + formatStatus("Done") + j.command);
                    else originalOut.println("[" + j.jobNumber + "]" + marker + "  " + formatStatus("Running") + j.command + " &");
                }
                jobList.removeAll(doneJobs);
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
                    System.out.println("cd: " + path + ": No such file or directory");
                }
                continue;
            }

            if (cmd.equals("pwd")) {
                outStream.println(currentDir);
                continue;
            }

            if (cmd.equals("echo")) {
                if (tokens.size() == 1) { outStream.println(); }
                else {
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
                if (isBuiltin(typeCmd)) {
                    outStream.println(typeCmd + " is a shell builtin");
                } else {
                    String path = findInPath(typeCmd);
                    if (path != null) outStream.println(typeCmd + " is " + path);
                    else outStream.println(typeCmd + ": not found");
                }
                if (redirect.stdoutFile != null) outStream.close();
                if (redirect.stderrFile != null) errStream.close();
                continue;
            }

            // External program
            String exePath = findInPath(cmd);
            if (exePath != null) {
                ProcessBuilder pb = new ProcessBuilder(tokens);
                pb.directory(new File(currentDir));
                if (redirect.stdoutFile != null)
                    pb.redirectOutput(redirect.stdoutAppend ? ProcessBuilder.Redirect.appendTo(new File(redirect.stdoutFile)) : ProcessBuilder.Redirect.to(new File(redirect.stdoutFile)));
                else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                if (redirect.stderrFile != null)
                    pb.redirectError(redirect.stderrAppend ? ProcessBuilder.Redirect.appendTo(new File(redirect.stderrFile)) : ProcessBuilder.Redirect.to(new File(redirect.stderrFile)));
                else pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                Process p = pb.start();
                if (isBackground) {
                    long pid = p.pid();
                    int jobNum = nextJobNumber();
                    jobList.add(new Job(jobNum, pid, String.join(" ", tokens), p));
                    System.out.println("[" + jobNum + "] " + pid);
                } else {
                    p.waitFor();
                }
            } else {
                originalOut.println(cmd + ": command not found");
            }
            if (redirect.stdoutFile != null) outStream.close();
            if (redirect.stderrFile != null) errStream.close();
        }
    }

    static void executePipeline(List<String> parts, PrintStream originalOut, PrintStream originalErr) throws Exception {
        int n = parts.size();
        List<List<String>> allTokens = new ArrayList<>();
        for (String part : parts) allTokens.add(parseTokens(part));

        // Create pipes between commands
        List<PipedInputStream> pipeIns = new ArrayList<>();
        List<PipedOutputStream> pipeOuts = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            PipedOutputStream po = new PipedOutputStream();
            PipedInputStream pi = new PipedInputStream(po);
            pipeOuts.add(po);
            pipeIns.add(pi);
        }

        List<Process> processes = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<String> tokens = allTokens.get(i);
            String cmd = tokens.get(0);

            InputStream stdinStream = (i == 0) ? null : pipeIns.get(i - 1);
            OutputStream stdoutStream = (i == n - 1) ? null : pipeOuts.get(i);

            if (isBuiltin(cmd)) {
                // Run builtin in a thread with piped I/O
                final int idx = i;
                final InputStream finalIn = stdinStream;
                final OutputStream finalOut = stdoutStream;
                final List<String> finalTokens = tokens;

                Thread t = new Thread(() -> {
                    try {
                        PrintStream out;
                        if (finalOut != null) out = new PrintStream(finalOut);
                        else out = originalOut;

                        execBuiltin(finalTokens, out, originalErr, currentDir);

                        if (finalOut != null) finalOut.close();
                        if (finalIn != null) finalIn.close();
                    } catch (Exception e) {}
                });
                threads.add(t);
                t.start();
            } else {
                String exePath = findInPath(cmd);
                if (exePath == null) {
                    originalErr.println(cmd + ": command not found");
                    continue;
                }
                ProcessBuilder pb = new ProcessBuilder(tokens);
                pb.directory(new File(currentDir));

                if (stdinStream != null) pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                else pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

                if (stdoutStream != null) pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

                pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                Process p = pb.start();
                processes.add(p);

                // Feed stdin from pipe
                if (stdinStream != null) {
                    final InputStream src = stdinStream;
                    final OutputStream dst = p.getOutputStream();
                    Thread t = new Thread(() -> {
                        try {
                            src.transferTo(dst);
                            dst.close();
                        } catch (Exception e) {}
                    });
                    threads.add(t);
                    t.start();
                }

                // Feed stdout to next pipe
                if (stdoutStream != null) {
                    final InputStream src = p.getInputStream();
                    final OutputStream dst = stdoutStream;
                    Thread t = new Thread(() -> {
                        try {
                            src.transferTo(dst);
                            dst.close();
                        } catch (Exception e) {}
                    });
                    threads.add(t);
                    t.start();
                }
            }
        }

        // Wait for all threads and processes
        for (Thread t : threads) t.join();
        for (Process p : processes) p.waitFor();
    }
}