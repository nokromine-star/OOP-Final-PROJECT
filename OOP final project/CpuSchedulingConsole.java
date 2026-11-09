import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
 
// Entry point
public class CpuSchedulingConsole {
    public static void main(String[] args) throws IOException {
        List<String> flags = Arrays.asList(args);
        boolean demo = flags.contains("--demo");
        Terminal.setup(demo, flags.contains("--ascii"));
 
        ConsoleApp app = new ConsoleApp();
        if (demo) {
            app.runDemo();
        } else {
            app.runInteractive();
        }
    }
}
 
// Interactive menu: reads input and delegates to the model and the view.
class ConsoleApp {
    private final Scanner in = new Scanner(System.in);
    private final List<Job> jobs = new ArrayList<>();
    private final RoundRobinScheduler roundRobin = new RoundRobinScheduler(2);
 
    // All algorithms behind one type, so the menu and comparison never depend on a specific one.
    private final Scheduler[] algorithms = {
        new FcfsScheduler(),
        new SjfScheduler(),
        new SrtfScheduler(),
        new PriorityScheduler(false),
        new PriorityScheduler(true),
        roundRobin
    };
 
    // Non-interactive tour: the sample workload through every algorithm, then the comparison.
    public void runDemo() {
        jobs.addAll(SampleData.jobs());
        roundRobin.setQuantum(3);
        ConsoleView.banner();
        ConsoleView.section("Process table");
        ConsoleView.jobs(jobs);
        for (Scheduler algorithm : algorithms) {
            ConsoleView.result(algorithm.schedule(jobs));
        }
        ConsoleView.comparison(algorithms, jobs);
    }
 
    public void runInteractive() {
        try {
            boolean running = true;
            while (running) {
                showMenu();
                try {
                    running = handle(prompt("Select").trim());
                } catch (InvalidJobException | IllegalArgumentException e) {
                    System.out.println(Terminal.c("  ✖ " + e.getMessage(), Terminal.BAD));
                    pause();
                }
            }
            System.out.println(Terminal.c("\n  Goodbye 👋\n", Terminal.DIM));
        } catch (NoSuchElementException e) {
            System.out.println(Terminal.c("\n\n  Goodbye 👋\n", Terminal.DIM));
        } finally {
            in.close();
        }
    }
 
    private void showMenu() {
        Terminal.clear();
        ConsoleView.banner();
        ConsoleView.section("Process table");
        ConsoleView.jobs(jobs);
        ConsoleView.section("Menu");
        System.out.println("   " + Terminal.c("1", Terminal.ACCENT) + "  Add process           "
                + Terminal.c("5", Terminal.ACCENT) + "  Remove / clear");
        System.out.println("   " + Terminal.c("2", Terminal.ACCENT) + "  Load sample set       "
                + Terminal.c("6", Terminal.ACCENT) + "  Run an algorithm");
        System.out.println("   " + Terminal.c("3", Terminal.ACCENT) + "  Random workload       "
                + Terminal.c("7", Terminal.ACCENT) + "  Compare all algorithms");
        System.out.println("   " + Terminal.c("4", Terminal.ACCENT) + "  Set time quantum (" + roundRobin.getQuantum()
                + ")  " + Terminal.c("8", Terminal.ACCENT) + "  Live simulation");
        System.out.println("   " + Terminal.c("0", Terminal.ACCENT) + "  Exit                  "
                + Terminal.c("9", Terminal.ACCENT) + "  Live race (all algorithms)");
        System.out.println();
    }
 
    private boolean handle(String choice) throws InvalidJobException {
        switch (choice) {
            case "0":
                return false;
            case "1":
                addJob();
                break;
            case "2":
                jobs.clear();
                jobs.addAll(SampleData.jobs());
                break;
            case "3":
                randomJobs();
                break;
            case "4":
                roundRobin.setQuantum(askInt("Time quantum", 1, Job.MAX_VALUE, roundRobin.getQuantum()));
                break;
            case "5":
                removeJob();
                break;
            case "6":
                runOne();
                pause();
                break;
            case "7":
                ConsoleView.comparison(algorithms, jobs);
                pause();
                break;
            case "8":
                liveSimulation();
                pause();
                break;
            case "9":
                liveSimulationAll();
                pause();
                break;
            default:
                System.out.println(Terminal.c("  Unknown option.", Terminal.BAD));
                pause();
        }
        return true;
    }
 
    private void addJob() throws InvalidJobException {
        String pid = nextPid();
        System.out.println(Terminal.c("  Adding " + pid, Terminal.pidColor(pid)));
        int arrival = askInt("Arrival time", 0, Job.MAX_VALUE, 0);
        int burst = askInt("Burst time", 1, Job.MAX_VALUE, 5);
        int priority = askInt("Priority (lower = higher)", 0, Job.MAX_VALUE, 1);
        jobs.add(new Job(pid, arrival, burst, priority));
        System.out.println(Terminal.c("  ✔ " + pid + " added", Terminal.GOOD));
    }
 
    private void randomJobs() throws InvalidJobException {
        int count = askInt("How many processes", 1, 8, 5);
        Random random = new Random();
        List<Job> fresh = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            fresh.add(new Job("P" + i, random.nextInt(11), 1 + random.nextInt(10), 1 + random.nextInt(5)));
        }
        jobs.clear();
        jobs.addAll(fresh);
        System.out.println(Terminal.c("  ✔ random workload generated", Terminal.GOOD));
    }
 
    private void removeJob() {
        if (jobs.isEmpty()) {
            return;
        }
        String pid = prompt("PID to remove (or 'all')").trim().toUpperCase();
        if (pid.equals("ALL")) {
            jobs.clear();
            System.out.println(Terminal.c("  ✔ cleared", Terminal.GOOD));
        } else if (jobs.removeIf(j -> j.getPid().equals(pid))) {
            System.out.println(Terminal.c("  ✔ removed", Terminal.GOOD));
        } else {
            System.out.println(Terminal.c("  PID not found", Terminal.BAD));
        }
    }
 
    private void runOne() {
        ConsoleView.result(chooseAndSchedule());
    }
 
    // Lists the algorithms, asks for a choice (and the quantum for Round Robin) and runs it.
    private ScheduleResult chooseAndSchedule() {
        ConsoleView.section("Choose algorithm");
        for (int i = 0; i < algorithms.length; i++) {
            System.out.println("   " + Terminal.c(String.valueOf(i + 1), Terminal.ACCENT) + "  "
                    + algorithms[i].getName() + " " + Terminal.c("(" + algorithms[i].getCode() + ")", Terminal.DIM));
        }
        Scheduler chosen = algorithms[askInt("Algorithm", 1, algorithms.length, null) - 1];
        if (chosen instanceof RoundRobinScheduler) {
            ((RoundRobinScheduler) chosen).setQuantum(
                    askInt("Time quantum", 1, Job.MAX_VALUE, roundRobin.getQuantum()));
        }
        return chosen.schedule(jobs);
    }
 
    private void liveSimulation() {
        ScheduleResult result = chooseAndSchedule();
        int delay = askInt("Delay per time unit in ms (0 = step with Enter)", 0, 5000, 400);
        LiveSimulation.play(result, delay, in);
    }
 
    // Runs every algorithm on the same workload and replays them side by side on one shared clock.
    private void liveSimulationAll() {
        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("Add at least one process before scheduling.");
        }
        roundRobin.setQuantum(askInt("Round Robin time quantum", 1, Job.MAX_VALUE, roundRobin.getQuantum()));
        int delay = askInt("Delay per time unit in ms (0 = step with Enter)", 0, 5000, 400);
        ScheduleResult[] results = new ScheduleResult[algorithms.length];
        for (int i = 0; i < algorithms.length; i++) {
            results[i] = algorithms[i].schedule(jobs);
        }
        if (LiveSimulation.playAll(algorithms, results, delay, in)) {
            // the race view scrolls, so show every complete Gantt chart once it is over
            ConsoleView.section("Final Gantt charts");
            for (int i = 0; i < algorithms.length; i++) {
                System.out.println(Terminal.c("  " + results[i].getDescription(), Terminal.ACCENT));
                ConsoleView.gantt(results[i].getTimeline());
                System.out.println();
            }
            ConsoleView.comparison(algorithms, jobs);
        }
    }
 
    private String prompt(String label) {
        System.out.print(Terminal.c("  › ", Terminal.ACCENT) + label + ": ");
        return in.nextLine();
    }
 
    // Repeats until a whole number in range is entered; blank accepts the default.
    private int askInt(String label, int lo, int hi, Integer defaultValue) {
        while (true) {
            String suffix = defaultValue != null ? " [" + defaultValue + "]" : "";
            String raw = prompt(label + suffix).trim();
            if (raw.isEmpty() && defaultValue != null) {
                return defaultValue;
            }
            try {
                int value = Integer.parseInt(raw);
                if (value < lo || value > hi) {
                    throw new NumberFormatException("out of range");
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println(Terminal.c("    Enter a whole number " + lo + "-" + hi + ".", Terminal.BAD));
            }
        }
    }
 
    private String nextPid() {
        int n = 1;
        boolean taken = true;
        while (taken) {
            taken = false;
            for (Job j : jobs) {
                if (j.getPidNumber() == n) {
                    taken = true;
                    n++;
                    break;
                }
            }
        }
        return "P" + n;
    }
 
    private void pause() {
        System.out.print(Terminal.c("\n  Press Enter to continue…", Terminal.DIM));
        in.nextLine();
    }
}
 
// All drawing. Reads the model, never changes it.
final class ConsoleView {
    private static final int WIDTH = 78;
 
    private ConsoleView() { }
 
    public static void banner() {
        int inner = WIDTH - 2;
        System.out.println(Terminal.c("╔" + Terminal.rep("═", inner) + "╗", Terminal.ACCENT));
        System.out.println(Terminal.c("║", Terminal.ACCENT)
                + Terminal.bold(Terminal.c(Terminal.center("CPU  SCHEDULING  CONSOLE", inner), Terminal.ACCENT))
                + Terminal.c("║", Terminal.ACCENT));
        System.out.println(Terminal.c("║", Terminal.ACCENT)
                + Terminal.c(Terminal.center("FCFS · SJF · SRTF · Priority · Round Robin", inner), Terminal.DIM)
                + Terminal.c("║", Terminal.ACCENT));
        System.out.println(Terminal.c("╚" + Terminal.rep("═", inner) + "╝", Terminal.ACCENT));
    }
 
    public static void section(String title) {
        System.out.println();
        System.out.println(Terminal.c("━━ ", Terminal.ACCENT) + Terminal.bold(title) + " "
                + Terminal.c(Terminal.rep("━", Math.max(2, WIDTH - title.length() - 4)), Terminal.ACCENT));
    }
 
    // Box table; highlight holds "row,col" cells (best values) to show in green.
    public static void table(String[] headers, List<String[]> rows, String[] rowColors, Set<String> highlight) {
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers[i].length();
            for (String[] row : rows) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
            widths[i] += 2;
        }
        String bar = Terminal.c("│", Terminal.DIM);
 
        System.out.println(Terminal.c(border("┌", "┬", "┐", widths), Terminal.DIM));
        StringBuilder head = new StringBuilder(bar);
        for (int i = 0; i < cols; i++) {
            head.append(Terminal.bold(Terminal.center(headers[i], widths[i]))).append(bar);
        }
        System.out.println(head);
        System.out.println(Terminal.c(border("├", "┼", "┤", widths), Terminal.DIM));
 
        for (int r = 0; r < rows.size(); r++) {
            StringBuilder line = new StringBuilder(bar);
            for (int col = 0; col < cols; col++) {
                String text = Terminal.center(rows.get(r)[col], widths[col]);
                String code = null;
                if (rowColors != null && col == 0) {
                    code = rowColors[r];
                }
                if (highlight != null && highlight.contains(r + "," + col)) {
                    code = Terminal.GOOD;
                }
                line.append(code != null ? Terminal.c(text, code) : text).append(bar);
            }
            System.out.println(line);
        }
        System.out.println(Terminal.c(border("└", "┴", "┘", widths), Terminal.DIM));
    }
 
    private static String border(String left, String middle, String right, int[] widths) {
        StringBuilder b = new StringBuilder(left);
        for (int i = 0; i < widths.length; i++) {
            b.append(Terminal.rep("─", widths[i]));
            b.append(i < widths.length - 1 ? middle : right);
        }
        return b.toString();
    }
 
    // Colored Gantt chart that wraps onto extra lines when wider than the console.
    public static void gantt(List<Segment> segments) {
        int[] widths = new int[segments.size()];
        for (int i = 0; i < widths.length; i++) {
            Segment s = segments.get(i);
            String label = s.isIdle() ? "idle" : s.getPid();
            widths[i] = Math.max(label.length() + 2, Math.min(s.getLength() * 2, 12)) + 2;
        }
 
        int maxLine = WIDTH - 6;
        int from = 0;
        while (from < segments.size()) {
            int used = 0;
            int to = from;
            while (to < segments.size() && (to == from || used + widths[to] <= maxLine)) {
                used += widths[to++];
            }
 
            StringBuilder top = new StringBuilder();
            StringBuilder mid = new StringBuilder();
            StringBuilder bottom = new StringBuilder();
            StringBuilder times = new StringBuilder();
            for (int i = from; i < to; i++) {
                Segment s = segments.get(i);
                int w = widths[i];
                String code = Terminal.pidColor(s.getPid());
                String label = s.isIdle() ? "idle" : s.getPid();
                if (s.isIdle()) {
                    top.append(Terminal.c("┌" + Terminal.rep("╌", w - 2) + "┐", code));
                    mid.append(Terminal.c("┆", code)).append(Terminal.c(Terminal.center(label, w - 2), code))
                       .append(Terminal.c("┆", code));
                    bottom.append(Terminal.c("└" + Terminal.rep("╌", w - 2) + "┘", code));
                } else {
                    top.append(Terminal.c("┏" + Terminal.rep("━", w - 2) + "┓", code));
                    mid.append(Terminal.c("┃", code)).append(Terminal.bold(Terminal.c(Terminal.center(label, w - 2), code)))
                       .append(Terminal.c("┃", code));
                    bottom.append(Terminal.c("┗" + Terminal.rep("━", w - 2) + "┛", code));
                }
                times.append(Terminal.padRight(String.valueOf(s.getStart()), w));
            }
            times.append(segments.get(to - 1).getEnd());
 
            System.out.println(" " + top);
            System.out.println(" " + mid);
            System.out.println(" " + bottom);
            System.out.println(" " + Terminal.c(times, Terminal.DIM));
            from = to;
        }
    }
 
    public static void jobs(List<Job> jobs) {
        if (jobs.isEmpty()) {
            System.out.println(Terminal.c("  (no processes yet — add some or load the sample)", Terminal.WARN));
            return;
        }
        List<String[]> rows = new ArrayList<>();
        String[] colors = new String[jobs.size()];
        for (int i = 0; i < jobs.size(); i++) {
            Job j = jobs.get(i);
            rows.add(new String[] {j.getPid(), "" + j.getArrivalTime(), "" + j.getBurstTime(), "" + j.getPriority()});
            colors[i] = Terminal.pidColor(j.getPid());
        }
        table(new String[] {"PID", "Arrival", "Burst", "Priority"}, rows, colors, null);
    }
 
    public static void result(ScheduleResult r) {
        section(r.getDescription());
        System.out.println(Terminal.c("  Gantt chart", Terminal.DIM));
        gantt(r.getTimeline());
        System.out.println();
 
        List<String[]> rows = new ArrayList<>();
        String[] colors = new String[r.getJobs().size()];
        int i = 0;
        for (Job j : r.getJobs()) {
            rows.add(new String[] {j.getPid(), "" + j.getArrivalTime(), "" + j.getBurstTime(), "" + j.getPriority(),
                    "" + j.getCompletionTime(), "" + j.getTurnaroundTime(), "" + j.getWaitingTime(),
                    "" + j.getResponseTime()});
            colors[i++] = Terminal.pidColor(j.getPid());
        }
        table(new String[] {"PID", "AT", "BT", "PR", "CT", "TAT", "WT", "RT"}, rows, colors, null);
 
        System.out.println("  Avg waiting " + Terminal.c(String.format("%.2f", r.getAverageWaiting()), Terminal.GOOD)
                + "   Avg turnaround " + Terminal.c(String.format("%.2f", r.getAverageTurnaround()), Terminal.GOOD)
                + "   Avg response " + Terminal.c(String.format("%.2f", r.getAverageResponse()), Terminal.GOOD));
        System.out.println("  CPU utilization " + Terminal.c(String.format("%.1f%%", r.getCpuUtilization()), Terminal.ACCENT)
                + "   Throughput " + Terminal.c(String.format("%.3f proc/unit", r.getThroughput()), Terminal.ACCENT));
        int filled = (int) (r.getCpuUtilization() / 100 * 30);
        System.out.println("  CPU  " + Terminal.c(Terminal.rep("█", filled), Terminal.GOOD)
                + Terminal.c(Terminal.rep("░", 30 - filled), Terminal.DIM));
    }
 
    // Runs every scheduler on the same jobs and highlights the best value in each metric.
    public static void comparison(Scheduler[] schedulers, List<Job> jobs) {
        section("Algorithm comparison");
        ScheduleResult[] results = new ScheduleResult[schedulers.length];
        double bestWait = Double.MAX_VALUE;
        double bestTurn = Double.MAX_VALUE;
        double bestResp = Double.MAX_VALUE;
        for (int i = 0; i < schedulers.length; i++) {
            results[i] = schedulers[i].schedule(jobs);
            bestWait = Math.min(bestWait, results[i].getAverageWaiting());
            bestTurn = Math.min(bestTurn, results[i].getAverageTurnaround());
            bestResp = Math.min(bestResp, results[i].getAverageResponse());
        }
 
        List<String[]> rows = new ArrayList<>();
        Set<String> best = new HashSet<>();
        for (int i = 0; i < results.length; i++) {
            ScheduleResult r = results[i];
            rows.add(new String[] {schedulers[i].getCode(), String.format("%.2f", r.getAverageWaiting()),
                    String.format("%.2f", r.getAverageTurnaround()), String.format("%.2f", r.getAverageResponse()),
                    String.format("%.1f%%", r.getCpuUtilization()), String.format("%.3f", r.getThroughput())});
            if (Math.abs(r.getAverageWaiting() - bestWait) < 1e-9) {
                best.add(i + ",1");
            }
            if (Math.abs(r.getAverageTurnaround() - bestTurn) < 1e-9) {
                best.add(i + ",2");
            }
            if (Math.abs(r.getAverageResponse() - bestResp) < 1e-9) {
                best.add(i + ",3");
            }
        }
        table(new String[] {"Algorithm", "Avg WT", "Avg TAT", "Avg RT", "CPU %", "Thruput"}, rows, null, best);
        System.out.println(Terminal.c("  Green = best in column", Terminal.DIM));
    }
}
 
// Colors, text alignment and output encoding.
final class Terminal {
    public static final String ACCENT = "38;5;45";
    public static final String DIM = "38;5;244";
    public static final String GOOD = "38;5;114";
    public static final String WARN = "38;5;221";
    public static final String BAD = "38;5;203";
 
    private static final String[] PALETTE = {
        "38;5;75", "38;5;114", "38;5;215", "38;5;176",
        "38;5;203", "38;5;81", "38;5;221", "38;5;141"
    };
 
    private static boolean color;
 
    private Terminal() { }
 
    // Switches a Windows console to UTF-8 (or falls back to ASCII) and decides whether colors are used.
    public static void setup(boolean forceColor, boolean ascii) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows && !ascii) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001 >nul").inheritIO().start().waitFor();
            } catch (IOException | InterruptedException e) {
                ascii = true;
            }
        }
        OutputStream raw = new FileOutputStream(FileDescriptor.out);
        System.setOut(new PrintStream(ascii ? new AsciiOut(raw) : raw, true, "UTF-8"));
        color = (System.console() != null || forceColor) && System.getenv("NO_COLOR") == null;
    }
 
    public static String c(Object text, String code) {
        return color ? "\033[" + code + "m" + text + "\033[0m" : String.valueOf(text);
    }
 
    public static String bold(String text) {
        return c(text, "1");
    }
 
    public static String pidColor(String pid) {
        if (pid == null) {
            return DIM;
        }
        int number = Integer.parseInt(pid.substring(1));
        return PALETTE[(number - 1) % PALETTE.length];
    }
 
    public static void clear() {
        if (color) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
    }
 
    public static String rep(String s, int times) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < times; i++) {
            b.append(s);
        }
        return b.toString();
    }
 
    public static String center(String s, int width) {
        int space = Math.max(0, width - s.length());
        int left = space / 2;
        return rep(" ", left) + s + rep(" ", space - left);
    }
 
    public static String padRight(String s, int width) {
        return s + rep(" ", Math.max(0, width - s.length()));
    }
 
    // Replaces box and block characters with ASCII for consoles that cannot display them.
    private static class AsciiOut extends OutputStream {
        private final OutputStream raw;
 
        AsciiOut(OutputStream raw) {
            this.raw = raw;
        }
 
        private static char map(char ch) {
            switch (ch) {
                case '═': case '━': return '=';
                case '║': case '│': case '┃': return '|';
                case '┆': return ':';
                case '─': case '╌': case '—': return '-';
                case '╔': case '╗': case '╚': case '╝': case '┌': case '┬': case '┐': case '├':
                case '┼': case '┤': case '└': case '┴': case '┘': case '┏': case '┓': case '┗':
                case '┛': case '✔': return '+';
                case '✖': return 'x';
                case '█': return '#';
                case '░': return '.';
                case '·': return '|';
                case '›': return '>';
                case '…': return '.';
                default: return ch;
            }
        }
 
        @Override
        public void write(int b) throws IOException {
            raw.write(b);
        }
 
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            String s = new String(b, off, len, StandardCharsets.UTF_8).replace("👋", "");
            StringBuilder out = new StringBuilder();
            for (char ch : s.toCharArray()) {
                out.append(map(ch));
            }
            raw.write(out.toString().getBytes(StandardCharsets.US_ASCII));
        }
 
        @Override
        public void flush() throws IOException {
            raw.flush();
        }
    }
}
 
// Contract for every algorithm. schedule() is a template method: validate, copy the jobs, run, package the result.
abstract class Scheduler {
    private final String name;
    private final String code;
 
    protected Scheduler(String name, String code) {
        this.name = name;
        this.code = code;
    }
 
    public String getName() { return name; }
    public String getCode() { return code; }
 
    public String getDescription() { return name; }
 
    public final ScheduleResult schedule(List<Job> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Add at least one process before scheduling.");
        }
        List<Job> jobs = new ArrayList<>();
        for (Job j : input) {
            jobs.add(new Job(j));
        }
        List<Segment> timeline = new ArrayList<>();
        execute(jobs, timeline);
        return new ScheduleResult(getDescription(), jobs, timeline);
    }
 
    protected abstract void execute(List<Job> jobs, List<Segment> timeline);
 
    // Merges back-to-back runs of the same process into one Gantt block.
    protected static void addSegment(List<Segment> timeline, int start, int end, String pid) {
        if (end <= start) {
            return;
        }
        int last = timeline.size() - 1;
        if (last >= 0) {
            Segment prev = timeline.get(last);
            if (prev.getEnd() == start && Objects.equals(prev.getPid(), pid)) {
                timeline.set(last, prev.extendedTo(end));
                return;
            }
        }
        timeline.add(new Segment(start, end, pid));
    }
 
    @Override
    public String toString() {
        return code;
    }
}
 
// Shared loop for FCFS, SJF, SRTF and Priority; subclasses only supply key() and isPreemptive().
abstract class SelectionScheduler extends Scheduler {
    protected SelectionScheduler(String name, String code) {
        super(name, code);
    }
 
    protected abstract int key(Job job);
 
    protected abstract boolean isPreemptive();
 
    @Override
    protected void execute(List<Job> jobs, List<Segment> timeline) {
        int time = 0;
        int left = jobs.size();
 
        while (left > 0) {
            Job current = null;
            for (Job j : jobs) {
                if (j.isFinished() || j.getArrivalTime() > time) {
                    continue;
                }
                if (current == null
                        || key(j) < key(current)
                        || (key(j) == key(current) && j.getArrivalTime() < current.getArrivalTime())) {
                    current = j;
                }
            }
 
            if (current == null) {
                int nextArrival = Integer.MAX_VALUE;
                for (Job j : jobs) {
                    if (!j.isFinished()) {
                        nextArrival = Math.min(nextArrival, j.getArrivalTime());
                    }
                }
                addSegment(timeline, time, nextArrival, null);
                time = nextArrival;
                continue;
            }
 
            int slice = current.getRemainingTime();
            // A preemptive scheduler re-decides at the next arrival.
            if (isPreemptive()) {
                for (Job j : jobs) {
                    if (!j.isFinished() && j.getArrivalTime() > time) {
                        slice = Math.min(slice, j.getArrivalTime() - time);
                    }
                }
            }
            current.run(time, slice);
            addSegment(timeline, time, time + slice, current.getPid());
            time += slice;
            if (current.isFinished()) {
                left--;
            }
        }
    }
}
 
// First Come First Serve: earliest arrival runs first and is never interrupted.
class FcfsScheduler extends SelectionScheduler {
    public FcfsScheduler() {
        super("First Come First Serve", "FCFS");
    }
 
    @Override
    protected int key(Job job) {
        return job.getArrivalTime();
    }
 
    @Override
    protected boolean isPreemptive() {
        return false;
    }
}
 
// Shortest Job First: the ready job with the smallest burst runs next.
class SjfScheduler extends SelectionScheduler {
    public SjfScheduler() {
        super("Shortest Job First (non-preemptive)", "SJF");
    }
 
    @Override
    protected int key(Job job) {
        return job.getBurstTime();
    }
 
    @Override
    protected boolean isPreemptive() {
        return false;
    }
}
 
// Shortest Remaining Time First: SJF that re-decides whenever a job arrives.
class SrtfScheduler extends SelectionScheduler {
    public SrtfScheduler() {
        super("Shortest Remaining Time First", "SRTF");
    }
 
    @Override
    protected int key(Job job) {
        return job.getRemainingTime();
    }
 
    @Override
    protected boolean isPreemptive() {
        return true;
    }
}
 
// Priority scheduling, lower number = higher priority; the constructor flag selects preemptive.
class PriorityScheduler extends SelectionScheduler {
    private final boolean preemptive;
 
    public PriorityScheduler(boolean preemptive) {
        super(preemptive ? "Priority (preemptive)" : "Priority (non-preemptive)",
              preemptive ? "PRIO-P" : "PRIO-NP");
        this.preemptive = preemptive;
    }
 
    @Override
    protected int key(Job job) {
        return job.getPriority();
    }
 
    @Override
    protected boolean isPreemptive() {
        return preemptive;
    }
}
 
// Round Robin: jobs take turns for at most one quantum; arrivals queue before the interrupted job.
class RoundRobinScheduler extends Scheduler {
    private int quantum;
 
    public RoundRobinScheduler(int quantum) {
        super("Round Robin", "RR");
        setQuantum(quantum);
    }
 
    public int getQuantum() {
        return quantum;
    }
 
    public final void setQuantum(int quantum) {
        if (quantum < 1) {
            throw new IllegalArgumentException("Time quantum must be at least 1 (got " + quantum + ")");
        }
        this.quantum = quantum;
    }
 
    @Override
    public String getDescription() {
        return getName() + "  (quantum = " + quantum + ")";
    }
 
    @Override
    protected void execute(List<Job> jobs, List<Segment> timeline) {
        List<Job> byArrival = new ArrayList<>(jobs);
        byArrival.sort(Comparator.comparingInt(Job::getArrivalTime));
 
        Deque<Job> queue = new ArrayDeque<>();
        int next = 0;
        int time = 0;
        int n = byArrival.size();
 
        while (next < n || !queue.isEmpty()) {
            if (queue.isEmpty() && time < byArrival.get(next).getArrivalTime()) {
                int arrival = byArrival.get(next).getArrivalTime();
                addSegment(timeline, time, arrival, null);
                time = arrival;
            }
            while (next < n && byArrival.get(next).getArrivalTime() <= time) {
                queue.add(byArrival.get(next++));
            }
 
            Job current = queue.poll();
            int slice = Math.min(quantum, current.getRemainingTime());
            current.run(time, slice);
            addSegment(timeline, time, time + slice, current.getPid());
            time += slice;
 
            while (next < n && byArrival.get(next).getArrivalTime() <= time) {
                queue.add(byArrival.get(next++));
            }
            if (!current.isFinished()) {
                queue.add(current);
            }
        }
    }
}
 
// One process. Data is validated on creation and scheduling state changes only through run().
class Job {
    public static final int MAX_VALUE = 10_000;
 
    private final String pid;
    private final int arrivalTime;
    private final int burstTime;
    private final int priority;
 
    private int remainingTime;
    private int firstRunTime = -1;
    private int completionTime = -1;
 
    public Job(String pid, int arrivalTime, int burstTime, int priority) throws InvalidJobException {
        if (pid == null || !pid.matches("P[0-9]+")) {
            throw new InvalidJobException("PID must look like P1, P2, ... (got '" + pid + "')");
        }
        if (arrivalTime < 0 || arrivalTime > MAX_VALUE) {
            throw new InvalidJobException("Arrival time must be 0-" + MAX_VALUE + " (got " + arrivalTime + ")");
        }
        if (burstTime < 1 || burstTime > MAX_VALUE) {
            throw new InvalidJobException("Burst time must be 1-" + MAX_VALUE + " (got " + burstTime + ")");
        }
        if (priority < 0 || priority > MAX_VALUE) {
            throw new InvalidJobException("Priority must be 0-" + MAX_VALUE + " (got " + priority + ")");
        }
        this.pid = pid;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;
        this.remainingTime = burstTime;
    }
 
    // Copy constructor: same data, fresh scheduling state, so schedulers never alter the user's jobs.
    public Job(Job other) {
        this.pid = other.pid;
        this.arrivalTime = other.arrivalTime;
        this.burstTime = other.burstTime;
        this.priority = other.priority;
        this.remainingTime = other.burstTime;
    }
 
    public String getPid()          { return pid; }
    public int getPidNumber()       { return Integer.parseInt(pid.substring(1)); }
    public int getArrivalTime()     { return arrivalTime; }
    public int getBurstTime()       { return burstTime; }
    public int getPriority()        { return priority; }
    public int getRemainingTime()   { return remainingTime; }
    public int getCompletionTime()  { return completionTime; }
 
    public boolean isFinished()     { return remainingTime == 0; }
 
    public void run(int startTime, int units) {
        if (units < 1 || units > remainingTime) {
            throw new IllegalArgumentException("Cannot run " + pid + " for " + units
                    + " units (remaining " + remainingTime + ")");
        }
        if (firstRunTime < 0) {
            firstRunTime = startTime;
        }
        remainingTime -= units;
        if (remainingTime == 0) {
            completionTime = startTime + units;
        }
    }
 
    public int getTurnaroundTime() { return completionTime - arrivalTime; }
    public int getWaitingTime()    { return getTurnaroundTime() - burstTime; }
    public int getResponseTime()   { return firstRunTime - arrivalTime; }
 
    @Override
    public String toString() {
        return pid + "(AT=" + arrivalTime + ", BT=" + burstTime + ", PR=" + priority + ")";
    }
}
 
// One Gantt block; pid is null while the CPU is idle.
final class Segment {
    private final int start;
    private final int end;
    private final String pid;
 
    public Segment(int start, int end, String pid) {
        this.start = start;
        this.end = end;
        this.pid = pid;
    }
 
    public int getStart()   { return start; }
    public int getEnd()     { return end; }
    public String getPid()  { return pid; }
    public boolean isIdle() { return pid == null; }
    public int getLength()  { return end - start; }
 
    public Segment extendedTo(int newEnd) {
        return new Segment(start, newEnd, pid);
    }
 
    @Override
    public String toString() {
        return "[" + start + "-" + end + " " + (isIdle() ? "idle" : pid) + "]";
    }
}
 
// Immutable outcome of a run: timeline, finished jobs and summary metrics.
class ScheduleResult {
    private final String description;
    private final List<Job> jobs;
    private final List<Segment> timeline;
    private final double averageWaiting;
    private final double averageTurnaround;
    private final double averageResponse;
    private final double cpuUtilization;
    private final double throughput;
 
    public ScheduleResult(String description, List<Job> finishedJobs, List<Segment> timeline) {
        this.description = description;
 
        List<Job> sorted = new ArrayList<>(finishedJobs);
        sorted.sort(Comparator.comparingInt(Job::getPidNumber));
        this.jobs = Collections.unmodifiableList(sorted);
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
 
        double wait = 0, turn = 0, resp = 0;
        int earliestArrival = Integer.MAX_VALUE;
        for (Job j : jobs) {
            wait += j.getWaitingTime();
            turn += j.getTurnaroundTime();
            resp += j.getResponseTime();
            earliestArrival = Math.min(earliestArrival, j.getArrivalTime());
        }
        int n = jobs.size();
        this.averageWaiting = wait / n;
        this.averageTurnaround = turn / n;
        this.averageResponse = resp / n;
 
        int totalTime = timeline.get(timeline.size() - 1).getEnd() - earliestArrival;
        int busy = 0;
        for (Segment s : timeline) {
            if (!s.isIdle()) {
                busy += s.getLength();
            }
        }
        this.cpuUtilization = totalTime > 0 ? 100.0 * busy / totalTime : 100.0;
        this.throughput = totalTime > 0 ? (double) n / totalTime : 0.0;
    }
 
    public String getDescription()      { return description; }
    public List<Job> getJobs()          { return jobs; }
    public List<Segment> getTimeline()  { return timeline; }
    public double getAverageWaiting()   { return averageWaiting; }
    public double getAverageTurnaround(){ return averageTurnaround; }
    public double getAverageResponse()  { return averageResponse; }
    public double getCpuUtilization()   { return cpuUtilization; }
    public double getThroughput()       { return throughput; }
}
 
// Checked exception for invalid process data.
class InvalidJobException extends Exception {
    private static final long serialVersionUID = 1L;
 
    public InvalidJobException(String message) {
        super(message);
    }
}
 
// Sample workload for the demo and the "load sample" option.
final class SampleData {
    private SampleData() { }
 
    public static List<Job> jobs() {
        int[][] rows = {
            {0, 8, 3}, {1, 4, 1}, {2, 9, 4}, {3, 5, 2}, {12, 2, 1}
        };
        List<Job> list = new ArrayList<>();
        try {
            for (int i = 0; i < rows.length; i++) {
                list.add(new Job("P" + (i + 1), rows[i][0], rows[i][1], rows[i][2]));
            }
        } catch (InvalidJobException e) {
            throw new IllegalStateException("Sample data is invalid", e);
        }
        return list;
    }
}
 
// Unique feature: live simulation. Replays a schedule one time unit at a time, showing the CPU,
// a timeline ribbon and every process's state and progress; runs on a fixed delay or steps with Enter.
class LiveSimulation {
    private static final int BAR_WIDTH = 20;
    private static final int RACE_BAR = 30;
    private static final int RACE_WINDOW = 24;
    private static final int RACE_INDENT = 17;
    private static final int RACE_CELL_BAR = 5;
    private static final int RACE_PER_ROW = 6;
 
    private LiveSimulation() { }
 
    static void play(ScheduleResult result, int delayMs, Scanner in) {
        List<Segment> timeline = result.getTimeline();
        int end = timeline.get(timeline.size() - 1).getEnd();
 
        String[] owner = ownerMap(timeline, end);
 
        for (int t = 0; t <= end; t++) {
            Terminal.clear();
            drawFrame(result, owner, t, end);
            if (t < end && !waitForNextStep(delayMs, in)) {
                return;
            }
        }
    }
 
    private static void drawFrame(ScheduleResult result, String[] owner, int t, int end) {
        String running = t < end ? owner[t] : null;
        String cpuLabel = t == end ? "done" : running == null ? "idle" : running;
        String cpuColor = t == end ? Terminal.GOOD : Terminal.pidColor(running);
        int boxWidth = 8;
 
        System.out.println();
        System.out.println("  " + Terminal.bold(result.getDescription())
                + Terminal.c("    time " + t + " / " + end, Terminal.DIM));
        System.out.println();
        System.out.println("  CPU  " + Terminal.c("┏" + Terminal.rep("━", boxWidth) + "┓", cpuColor));
        System.out.println("       " + Terminal.c("┃", cpuColor)
                + Terminal.bold(Terminal.c(Terminal.center(cpuLabel, boxWidth), cpuColor))
                + Terminal.c("┃", cpuColor));
        System.out.println("       " + Terminal.c("┗" + Terminal.rep("━", boxWidth) + "┛", cpuColor));
        System.out.println();
        System.out.println(Terminal.c("  Gantt chart", Terminal.DIM));
        List<Segment> drawn = clip(result.getTimeline(), t);
        if (drawn.isEmpty()) {
            System.out.println(Terminal.c("  (waiting for the first time unit)", Terminal.DIM));
        } else {
            ConsoleView.gantt(drawn);
        }
        System.out.println();
        System.out.println(Terminal.c("  PID  State        Progress", Terminal.DIM));
 
        for (Job j : result.getJobs()) {
            int executed = 0;
            for (int k = 0; k < t; k++) {
                if (j.getPid().equals(owner[k])) {
                    executed++;
                }
            }
 
            String state;
            String stateColor;
            if (j.getArrivalTime() > t) {
                state = "not arrived";
                stateColor = Terminal.DIM;
            } else if (executed == j.getBurstTime()) {
                state = "done";
                stateColor = Terminal.DIM;
            } else if (j.getPid().equals(running)) {
                state = "running";
                stateColor = Terminal.GOOD;
            } else {
                state = "waiting";
                stateColor = Terminal.WARN;
            }
 
            int filled = executed * BAR_WIDTH / j.getBurstTime();
            System.out.println("  " + Terminal.c(Terminal.padRight(j.getPid(), 5), Terminal.pidColor(j.getPid()))
                    + Terminal.c(Terminal.padRight(state, 13), stateColor)
                    + Terminal.c(Terminal.rep("█", filled), Terminal.pidColor(j.getPid()))
                    + Terminal.c(Terminal.rep("░", BAR_WIDTH - filled), Terminal.DIM)
                    + " " + executed + "/" + j.getBurstTime());
        }
 
        if (t == end) {
            System.out.println();
            System.out.println("  Finished at time " + end
                    + "   avg waiting " + Terminal.c(String.format("%.2f", result.getAverageWaiting()), Terminal.GOOD)
                    + "   avg turnaround " + Terminal.c(String.format("%.2f", result.getAverageTurnaround()), Terminal.GOOD));
        }
    }
 
    // owner[t] = process running during time unit t, null while the CPU is idle
    private static String[] ownerMap(List<Segment> timeline, int end) {
        String[] owner = new String[end];
        for (Segment s : timeline) {
            for (int t = s.getStart(); t < s.getEnd(); t++) {
                owner[t] = s.getPid();
            }
        }
        return owner;
    }
 
    // Live race: all algorithms advance together on one clock, one compact panel each.
    // Returns false if the user stopped it early.
    static boolean playAll(Scheduler[] schedulers, ScheduleResult[] results, int delayMs, Scanner in) {
        int n = results.length;
        String[][] owner = new String[n][];
        int[] end = new int[n];
        int last = 0;
        for (int i = 0; i < n; i++) {
            List<Segment> timeline = results[i].getTimeline();
            end[i] = timeline.get(timeline.size() - 1).getEnd();
            owner[i] = ownerMap(timeline, end[i]);
            last = Math.max(last, end[i]);
        }
 
        for (int t = 0; t <= last; t++) {
            Terminal.clear();
            drawRaceFrame(schedulers, results, owner, end, t, last);
            if (t < last && !waitForNextStep(delayMs, in)) {
                return false;
            }
        }
        return true;
    }
 
    private static void drawRaceFrame(Scheduler[] schedulers, ScheduleResult[] results, String[][] owner,
                                      int[] end, int t, int last) {
        int clock = last == 0 ? RACE_BAR : t * RACE_BAR / last;
        System.out.println();
        System.out.println("  " + Terminal.bold("LIVE RACE - all algorithms")
                + Terminal.c("    time " + t + " / " + last, Terminal.DIM));
        System.out.println("  " + Terminal.c(Terminal.rep("█", clock), Terminal.ACCENT)
                + Terminal.c(Terminal.rep("░", RACE_BAR - clock), Terminal.DIM));
        System.out.println();
 
        for (int i = 0; i < results.length; i++) {
            ScheduleResult r = results[i];
            boolean finished = t >= end[i];
            String running = finished ? null : owner[i][t];
            String label = finished ? "done" : running == null ? "idle" : running;
            String color = finished ? Terminal.GOOD : Terminal.pidColor(running);
 
            int completed = 0;
            for (Job j : r.getJobs()) {
                if (j.getCompletionTime() <= t) {
                    completed++;
                }
            }
            String[] gantt = miniGantt(r.getTimeline(), t);
            String status = finished
                    ? Terminal.c("✔ @" + end[i], Terminal.GOOD)
                    : Terminal.c(completed + "/" + r.getJobs().size() + " done", Terminal.DIM);
 
            System.out.println("  " + Terminal.bold(Terminal.c(Terminal.padRight(schedulers[i].getCode(), 8), Terminal.ACCENT))
                    + Terminal.c("[", color) + Terminal.bold(Terminal.c(Terminal.center(label, 4), color)) + Terminal.c("]", color)
                    + " " + gantt[0] + " " + status);
            System.out.println(Terminal.rep(" ", RACE_INDENT) + gantt[1]);
 
            // one small progress cell per process
            StringBuilder row = new StringBuilder();
            int inRow = 0;
            for (Job j : r.getJobs()) {
                int executed = 0;
                for (int k = 0; k < Math.min(t, owner[i].length); k++) {
                    if (j.getPid().equals(owner[i][k])) {
                        executed++;
                    }
                }
                String state;
                String stateColor;
                if (j.getArrivalTime() > t) {
                    state = "-";
                    stateColor = Terminal.DIM;
                } else if (executed == j.getBurstTime()) {
                    state = "D";
                    stateColor = Terminal.DIM;
                } else if (j.getPid().equals(running)) {
                    state = "R";
                    stateColor = Terminal.GOOD;
                } else {
                    state = "W";
                    stateColor = Terminal.WARN;
                }
                int filled = executed * RACE_CELL_BAR / j.getBurstTime();
                row.append(Terminal.c(j.getPid(), Terminal.pidColor(j.getPid()))).append(' ')
                   .append(Terminal.c(state, stateColor)).append(' ')
                   .append(Terminal.c(Terminal.rep("█", filled), Terminal.pidColor(j.getPid())))
                   .append(Terminal.c(Terminal.rep("░", RACE_CELL_BAR - filled), Terminal.DIM)).append("  ");
                if (++inRow == RACE_PER_ROW) {
                    System.out.println("          " + row);
                    row.setLength(0);
                    inRow = 0;
                }
            }
            if (inRow > 0) {
                System.out.println("          " + row);
            }
        }
 
        System.out.println();
        System.out.println("  " + Terminal.c("R", Terminal.GOOD) + " running   " + Terminal.c("W", Terminal.WARN)
                + " waiting   " + Terminal.c("D", Terminal.DIM) + " done   " + Terminal.c("-", Terminal.DIM) + " not arrived");
 
        if (t == last) {
            Integer[] order = new Integer[results.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, Comparator.comparingDouble(i -> results[i].getAverageWaiting()));
            StringBuilder podium = new StringBuilder();
            for (int rank = 0; rank < order.length; rank++) {
                podium.append(rank == 0 ? "" : "  >  ").append(schedulers[order[rank]].getCode())
                      .append(" (").append(String.format("%.2f", results[order[rank]].getAverageWaiting())).append(")");
            }
            System.out.println();
            System.out.println("  Ranking by avg waiting time: " + Terminal.c(podium, Terminal.GOOD));
        }
    }
 
    // The part of the timeline that has happened by time t (the running block is cut at t, so it grows).
    private static List<Segment> clip(List<Segment> timeline, int t) {
        List<Segment> out = new ArrayList<>();
        for (Segment s : timeline) {
            if (s.getStart() < t) {
                out.add(new Segment(s.getStart(), Math.min(s.getEnd(), t), s.getPid()));
            }
        }
        return out;
    }
 
    // Compact Gantt for the race: 2 characters per time unit, scrolling over the last RACE_WINDOW units.
    // Returns {bar line, time-axis line}; the bar is padded to a fixed width so columns stay aligned.
    private static String[] miniGantt(List<Segment> timeline, int t) {
        int end = timeline.get(timeline.size() - 1).getEnd();
        int shown = Math.min(t, end);
        int start = Math.max(0, shown - RACE_WINDOW);
        char[] axis = new char[RACE_WINDOW * 2 + 6];
        Arrays.fill(axis, ' ');
        StringBuilder bar = new StringBuilder();
        int nextFree = 0;
 
        for (Segment s : timeline) {
            int from = Math.max(s.getStart(), start);
            int to = Math.min(s.getEnd(), shown);
            if (to <= from) {
                continue;
            }
            int w = (to - from) * 2;
            if (s.isIdle()) {
                bar.append(Terminal.c(Terminal.rep("╌", w), Terminal.DIM));
            } else {
                String pid = s.getPid();
                String label = pid.length() <= w - 1 ? pid : String.valueOf(Integer.parseInt(pid.substring(1)) % 10);
                String code = Terminal.pidColor(pid);
                bar.append(Terminal.c("┃", code))
                   .append(Terminal.bold(Terminal.c(label, code)))
                   .append(Terminal.c(Terminal.rep("━", Math.max(0, w - 1 - label.length())), code));
            }
            nextFree = tick(axis, (from - start) * 2, from, nextFree);
        }
        tick(axis, (shown - start) * 2, shown, nextFree);
 
        bar.append(Terminal.rep(" ", RACE_WINDOW * 2 - (shown - start) * 2));
        return new String[] {bar.toString(), Terminal.c(new String(axis).replaceAll("\\s+$", ""), Terminal.DIM)};
    }
 
    // Writes a time label at a column of the axis if it does not collide with the previous one.
    private static int tick(char[] axis, int col, int time, int nextFree) {
        String text = String.valueOf(time);
        if (col < nextFree || col + text.length() > axis.length) {
            return nextFree;
        }
        for (int i = 0; i < text.length(); i++) {
            axis[col + i] = text.charAt(i);
        }
        return col + text.length() + 1;
    }
 
    private static boolean waitForNextStep(int delayMs, Scanner in) {
        if (delayMs == 0) {
            System.out.print(Terminal.c("\n  Enter = next step, q = stop: ", Terminal.DIM));
            return !in.nextLine().trim().equalsIgnoreCase("q");
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
 