import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

/*
 * ENCAPSULATION
 *   Private fields, validation, and read-only views: data is only reached through methods.
 */

// [ENCAPSULATION] all fields are private; the constructor validates them; scheduling state changes only through run().
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

    public int getFirstRunTime()   { return firstRunTime; }

    // ---- HOW EACH METRIC IS CALCULATED ------------------------------------------------------------------
    // Worked example (FCFS, sample data): P2 has AT = 1, BT = 4. It first gets the CPU at time 8 and finishes at 12.
    //
    //   CT  (completion time) = the clock value when the job's last unit ends                     -> 12
    //   TAT (turnaround time) = CT - AT      total time from arrival until the job is done        -> 12 - 1 = 11
    //   WT  (waiting time)    = TAT - BT     time spent ready but NOT running                      ->  11 - 4 = 7
    //   RT  (response time)   = first run - AT   how long until the CPU is first given to the job  ->   8 - 1 = 7
    //
    // Why WT = TAT - BT: of the 11 time units the job was in the system, 4 were spent running (its burst),
    // so the other 11 - 4 = 7 units must have been spent waiting.
    public int getTurnaroundTime() { return completionTime - arrivalTime; }
    public int getWaitingTime()    { return getTurnaroundTime() - burstTime; }
    public int getResponseTime()   { return firstRunTime - arrivalTime; }

    @Override
    public String toString() {
        return pid + "(AT=" + arrivalTime + ", BT=" + burstTime + ", PR=" + priority + ")";
    }
}

// [ENCAPSULATION] immutable: final fields and no setters. 'extending' a block creates a NEW Segment (extendedTo).
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

// [ENCAPSULATION] all fields final; lists are wrapped as unmodifiable, so a result can never be altered after it is built.
// Immutable outcome of a run: timeline, finished jobs and summary metrics.
class ScheduleResult {
    private final String description;
    private final String rule;
    private final int startTime;
    private final int endTime;
    private final int busyTime;
    private final List<Job> jobs;
    private final List<Segment> timeline;
    private final double averageWaiting;
    private final double averageTurnaround;
    private final double averageResponse;
    private final double cpuUtilization;
    private final double throughput;

    public ScheduleResult(String description, String rule, List<Job> finishedJobs, List<Segment> timeline) {
        this.description = description;
        this.rule = rule;

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
        // AVERAGES: add the metric of every job, then divide by the number of jobs n.
        //   FCFS sample: WT = 0, 7, 10, 18, 14  ->  Avg WT = (0 + 7 + 10 + 18 + 14) / 5 = 49 / 5 = 9.80
        int n = jobs.size();
        this.averageWaiting = wait / n;
        this.averageTurnaround = turn / n;
        this.averageResponse = resp / n;

        // CPU UTILIZATION: the share of the total time that the CPU was busy (not idle).
        //   total time = end of the last Gantt block - first arrival      (28 - 0 = 28)
        //   busy time  = sum of the lengths of every non-idle block       (28)
        //   utilization = busy / total x 100                              (28 / 28 x 100 = 100 %)
        // THROUGHPUT: how many processes finish per time unit = n / total time   (5 / 28 = 0.179)
        int totalTime = timeline.get(timeline.size() - 1).getEnd() - earliestArrival;
        int busy = 0;
        for (Segment s : timeline) {
            if (!s.isIdle()) {
                busy += s.getLength();
            }
        }
        this.startTime = earliestArrival;
        this.endTime = timeline.get(timeline.size() - 1).getEnd();
        this.busyTime = busy;
        this.cpuUtilization = totalTime > 0 ? 100.0 * busy / totalTime : 100.0;
        this.throughput = totalTime > 0 ? (double) n / totalTime : 0.0;
    }

    public String getRule()             { return rule; }
    public int getStartTime()           { return startTime; }
    public int getEndTime()             { return endTime; }
    public int getBusyTime()            { return busyTime; }

    public String getDescription()      { return description; }
    public List<Job> getJobs()          { return jobs; }
    public List<Segment> getTimeline()  { return timeline; }
    public double getAverageWaiting()   { return averageWaiting; }
    public double getAverageTurnaround(){ return averageTurnaround; }
    public double getAverageResponse()  { return averageResponse; }
    public double getCpuUtilization()   { return cpuUtilization; }
    public double getThroughput()       { return throughput; }
}

// [ENCAPSULATION] the raw list is private; view() hands out an unmodifiable view so outside code cannot corrupt it.
// The user's list of processes. The raw list is private; outsiders get a read-only view.
final class Workload {
    private final List<Job> jobs = new ArrayList<>();

    public List<Job> view() {
        return Collections.unmodifiableList(jobs);
    }

    public void add(Job job) {
        jobs.add(Objects.requireNonNull(job));
    }

    public void set(int index, Job job) {
        jobs.set(index, Objects.requireNonNull(job));
    }

    public boolean remove(String pid) {
        return jobs.removeIf(j -> j.getPid().equals(pid));
    }

    public void clear() {
        jobs.clear();
    }

    public void replaceAll(List<Job> fresh) {
        List<Job> copy = new ArrayList<>(fresh);   // build first so a failure never leaves a half-empty list
        jobs.clear();
        jobs.addAll(copy);
    }

    public boolean isEmpty() {
        return jobs.isEmpty();
    }

    public int size() {
        return jobs.size();
    }

    // Smallest unused P-number, so removed ids are reused.
    public String nextPid() {
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

// [ENCAPSULATION] the 'color' flag is private static; only setup() writes it and isColor() reads it.
// Colors, text alignment and output encoding. Its state is private and set only through setup().
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

    public static boolean isColor() {
        return color;
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
                case '●': return '*';
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
            String s = new String(b, off, len, StandardCharsets.UTF_8).replace("👋", "").replace("💡 ", "");
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

// Animated boot sequence: spinner per task plus an overall progress bar.
final class LoadingScreen {
    private static final String[] SPINNER = {"|", "/", "-", "\\"};
    private static final String[] LOGO = {
        "  ____ ____  _   _ ",
        " / ___|  _ \\| | | |",
        "| |   | |_) | | | |",
        "| |___|  __/| |_| |",
        " \\____|_|    \\___/ "
    };
    private static final String[] TASKS = {
        "Booting scheduler kernel",
        "Registering 7 scheduling algorithms",
        "Building process table",
        "Calibrating Gantt chart renderer",
        "Warming up live simulator"
    };
    private static final int BAR = 20;
    private static final int FRAME_MS = 60;

    private LoadingScreen() { }

    static void show(int msPerTask) {
        Terminal.clear();
        System.out.println();
        for (String line : LOGO) {
            System.out.println(Terminal.bold(Terminal.c(Terminal.center(line, ConsoleView.WIDTH), Terminal.ACCENT)));
        }
        System.out.println();
        System.out.println(Terminal.c(Terminal.center("S C H E D U L I N G   C O N S O L E", ConsoleView.WIDTH), Terminal.DIM));
        System.out.println();

        boolean animated = Terminal.isColor() && msPerTask > 0;
        int n = TASKS.length;
        for (int i = 0; i < n; i++) {
            if (animated) {
                int frames = Math.max(1, msPerTask / FRAME_MS);
                for (int f = 0; f < frames; f++) {
                    double fraction = (i + (f + 1.0) / frames) / n;
                    System.out.print("\r  " + Terminal.c(SPINNER[f % SPINNER.length], Terminal.ACCENT) + " "
                            + Terminal.padRight(TASKS[i], 40) + progress(fraction));
                    System.out.flush();
                    sleep(FRAME_MS);
                }
                System.out.println("\r  " + Terminal.c("✔", Terminal.GOOD) + " "
                        + Terminal.padRight(TASKS[i], 40) + progress((i + 1.0) / n));
            } else {
                System.out.println("  " + Terminal.c("✔", Terminal.GOOD) + " " + TASKS[i]);
                sleep(msPerTask / 4);
            }
        }
        System.out.println();
        System.out.println(Terminal.c("  Ready. Starting…", Terminal.GOOD));
        sleep(msPerTask > 0 ? 450 : 0);
    }

    private static String progress(double fraction) {
        int filled = (int) Math.round(fraction * BAR);
        return Terminal.c("[", Terminal.DIM) + Terminal.c(Terminal.rep("█", filled), Terminal.ACCENT)
                + Terminal.c(Terminal.rep("░", BAR - filled), Terminal.DIM) + Terminal.c("] ", Terminal.DIM)
                + Terminal.padRight(Math.round(fraction * 100) + "%", 4);
    }

    private static void sleep(int ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// [ENCAPSULATION] private constructor + static helpers: one place decides every colour and font in the GUI.
// Colours, fonts and small styling helpers shared by every GUI component.
final class Theme {
    static final Color BG = new Color(16, 20, 28);
    static final Color PANEL = new Color(25, 30, 41);
    static final Color PANEL2 = new Color(34, 40, 54);
    static final Color BORDER = new Color(52, 60, 78);
    static final Color TEXT = new Color(226, 232, 242);
    static final Color DIM = new Color(135, 146, 166);
    static final Color ACCENT = new Color(0, 200, 255);
    static final Color GOOD = new Color(110, 220, 140);
    static final Color WARN = new Color(255, 205, 90);
    static final Color BAD = new Color(255, 105, 105);
    static final Color INK = new Color(14, 18, 25);

    private static final Color[] PIDS = {
        new Color(95, 175, 255), new Color(135, 215, 135), new Color(255, 175, 95), new Color(215, 135, 215),
        new Color(255, 95, 95), new Color(95, 215, 255), new Color(255, 215, 95), new Color(175, 135, 255)
    };

    private Theme() { }

    static Color pid(String pid) {
        if (pid == null) {
            return new Color(62, 70, 88);
        }
        return PIDS[(Integer.parseInt(pid.substring(1)) - 1) % PIDS.length];
    }

    static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    static void centered(Graphics2D g, String s, int x, int y, int w, int h) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep the default look and feel
        }
        UIManager.put("ComboBox.background", PANEL2);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", new Color(0, 110, 140));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.buttonBackground", PANEL2);
        UIManager.put("Spinner.background", PANEL2);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", PANEL);
        UIManager.put("ToolTip.background", PANEL2);
        UIManager.put("ToolTip.foreground", TEXT);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    static JSpinner spinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        JTextField f = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
        f.setBackground(PANEL2);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(font(Font.PLAIN, 13));
        s.setBorder(new LineBorder(BORDER));
        return s;
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(BORDER));
        sp.getViewport().setBackground(PANEL);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    static void styleTable(JTable table, CellRenderer renderer) {
        table.setBackground(PANEL);
        table.setForeground(TEXT);
        table.setGridColor(BORDER);
        table.setRowHeight(27);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setFont(font(Font.PLAIN, 13));
        table.setSelectionBackground(new Color(0, 105, 135));
        table.setSelectionForeground(Color.WHITE);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);
        table.setDefaultRenderer(String.class, renderer);
    }
}

// [ABSTRACTION] one painter reused by the single simulation and by every race lane.
// Draws a Gantt strip. Shared by the single simulation and the race lanes.
final class GanttPainter {
    private GanttPainter() { }

    static int endOf(ScheduleResult r) {
        List<Segment> tl = r.getTimeline();
        return tl.get(tl.size() - 1).getEnd();
    }

    // owner[t] = process running during time unit t (null = idle)
    static String[] ownerMap(ScheduleResult r) {
        String[] owner = new String[endOf(r)];
        for (Segment s : r.getTimeline()) {
            for (int i = s.getStart(); i < s.getEnd(); i++) {
                owner[i] = s.getPid();
            }
        }
        return owner;
    }

    // Paints every segment up to time t onto a track that represents 0..end.
    static void paint(Graphics2D g, List<Segment> timeline, int t, int end, int x, int y, int w, int h, boolean ticks) {
        g.setColor(Theme.PANEL2);
        g.fillRoundRect(x, y, w, h, 10, 10);
        if (end <= 0) {
            return;
        }
        double scale = (double) w / end;
        int lastLabelRight = -100;
        g.setFont(Theme.font(Font.PLAIN, 11));
        for (Segment s : timeline) {
            if (s.getStart() >= t) {
                break;
            }
            int x1 = x + (int) Math.round(s.getStart() * scale);
            int x2 = x + (int) Math.round(Math.min(s.getEnd(), t) * scale);
            int bw = Math.max(2, x2 - x1);
            g.setColor(s.isIdle() ? new Color(58, 66, 84) : Theme.pid(s.getPid()));
            g.fillRoundRect(x1 + 1, y + 3, Math.max(1, bw - 2), h - 6, 8, 8);
            if (bw > 26) {
                g.setColor(s.isIdle() ? Theme.DIM : Theme.INK);
                g.setFont(Theme.font(Font.BOLD, 12));
                Theme.centered(g, s.isIdle() ? "idle" : s.getPid(), x1, y, bw, h);
            }
            if (ticks) {
                String label = String.valueOf(s.getStart());
                g.setFont(Theme.font(Font.PLAIN, 11));
                int tw = g.getFontMetrics().stringWidth(label);
                if (x1 >= lastLabelRight + 4) {
                    g.setColor(Theme.DIM);
                    g.drawString(label, x1, y + h + 15);
                    lastLabelRight = x1 + tw;
                }
            }
        }
        // cursor
        int cx = x + (int) Math.round(Math.min(t, end) * scale);
        g.setColor(Theme.ACCENT);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(cx, y - 4, cx, y + h + 4);
        g.setStroke(new BasicStroke(1f));
        if (ticks) {
            String label = String.valueOf(Math.min(t, end));
            int tw = g.getFontMetrics().stringWidth(label);
            if (cx >= lastLabelRight + 4 && cx + tw < x + w + 30) {
                g.setColor(Theme.ACCENT);
                g.setFont(Theme.font(Font.BOLD, 11));
                g.drawString(label, cx, y + h + 15);
            }
        }
    }
}

// Builds the "show your working" text for a finished run. Pure text (no printing), so the console and the GUI share it.
// [ABSTRACTION] callers just ask for the lines; they do not need to know any formula.
final class CalcExplainer {
    private CalcExplainer() { }

    static List<String> explain(ScheduleResult r) {
        List<String> out = new ArrayList<>();
        out.add("HOW IT WAS SOLVED - " + r.getDescription());
        out.add("");
        out.add("Rule used: " + r.getRule());
        out.add("");
        out.add("Formulas");
        out.add("  AT = arrival time, BT = burst time (CPU time needed)");
        out.add("  CT  (completion)  = clock value when the job's last time unit ends (end of its last Gantt block)");
        out.add("  TAT (turnaround)  = CT - AT");
        out.add("  WT  (waiting)     = TAT - BT      (time the job was ready but not on the CPU)");
        out.add("  RT  (response)    = first CPU time - AT");
        out.add("");
        out.add("Step 1 - read each process off the Gantt chart, then apply the formulas");

        int sumWait = 0;
        int sumTurn = 0;
        int sumResp = 0;
        StringBuilder waits = new StringBuilder();
        StringBuilder turns = new StringBuilder();
        StringBuilder resps = new StringBuilder();
        for (Job j : r.getJobs()) {
            out.add("");
            out.add("  " + j.getPid() + "   (AT=" + j.getArrivalTime() + ", BT=" + j.getBurstTime()
                    + ", PR=" + j.getPriority() + ")");
            out.add("      runs during : " + slices(r, j.getPid()));
            out.add("      CT  = " + j.getCompletionTime() + "     (its last block ends at " + j.getCompletionTime() + ")");
            out.add("      TAT = CT - AT      = " + j.getCompletionTime() + " - " + j.getArrivalTime()
                    + " = " + j.getTurnaroundTime());
            out.add("      WT  = TAT - BT     = " + j.getTurnaroundTime() + " - " + j.getBurstTime()
                    + " = " + j.getWaitingTime());
            out.add("      RT  = first - AT   = " + j.getFirstRunTime() + " - " + j.getArrivalTime()
                    + " = " + j.getResponseTime());
            sumWait += j.getWaitingTime();
            sumTurn += j.getTurnaroundTime();
            sumResp += j.getResponseTime();
            String plus = waits.length() == 0 ? "" : " + ";
            waits.append(plus).append(j.getWaitingTime());
            turns.append(plus).append(j.getTurnaroundTime());
            resps.append(plus).append(j.getResponseTime());
        }

        int n = r.getJobs().size();
        out.add("");
        out.add("Step 2 - averages: add the values of all " + n + " processes and divide by " + n);
        out.add("  Avg WT  = (" + waits + ") / " + n + " = " + sumWait + " / " + n + " = "
                + String.format("%.2f", r.getAverageWaiting()));
        out.add("  Avg TAT = (" + turns + ") / " + n + " = " + sumTurn + " / " + n + " = "
                + String.format("%.2f", r.getAverageTurnaround()));
        out.add("  Avg RT  = (" + resps + ") / " + n + " = " + sumResp + " / " + n + " = "
                + String.format("%.2f", r.getAverageResponse()));

        int total = r.getEndTime() - r.getStartTime();
        out.add("");
        out.add("Step 3 - CPU utilization and throughput");
        out.add("  total time  = last block end - first arrival = " + r.getEndTime() + " - " + r.getStartTime()
                + " = " + total);
        out.add("  busy time   = sum of all non-idle blocks      = " + r.getBusyTime()
                + (r.getBusyTime() == total ? "   (the CPU never sat idle)" : "   (the rest was idle)"));
        out.add("  utilization = busy / total x 100              = " + r.getBusyTime() + " / " + total
                + " x 100 = " + String.format("%.1f%%", r.getCpuUtilization()));
        out.add("  throughput  = processes / total time          = " + n + " / " + total
                + " = " + String.format("%.3f", r.getThroughput()) + " processes per time unit");
        return out;
    }

    // "0-8, 12-14" : every Gantt block of this process.
    private static String slices(ScheduleResult r, String pid) {
        StringBuilder b = new StringBuilder();
        for (Segment s : r.getTimeline()) {
            if (pid.equals(s.getPid())) {
                b.append(b.length() == 0 ? "" : ", ").append(s.getStart()).append('-').append(s.getEnd());
            }
        }
        return b.toString();
    }
}
