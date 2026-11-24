import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/*
 * POLYMORPHISM
 *   One call, many behaviours: code uses the PARENT type (Scheduler, MenuItem, JPanel ...)
 *   and the real subclass decides what happens. No big switch statements.
 */

// [ABSTRACTION]   implements the AppContext interface - menu items see only that interface.
// [ENCAPSULATION] every field is private; the outside world can only call the AppContext methods.
// Interactive menu: reads input and delegates to menu items, the model and the view.
class ConsoleApp implements AppContext {
    private static final String[] TIPS = {
        "Press Enter at any prompt to accept the [default] shown in brackets.",
        "Try option 9 to watch every algorithm race on the same clock.",
        "Lower priority number = higher priority.",
        "Round Robin with a huge quantum behaves exactly like FCFS.",
        "SRTF gives the best average waiting time but can starve long jobs.",
        "HRRN fixes starvation: the longer a job waits, the higher its ratio climbs.",
        "In live mode, enter 0 ms delay to step through with the Enter key."
    };

    private final Scanner in = new Scanner(System.in);
    private final Workload workload = new Workload();
    private final RoundRobinScheduler roundRobin = new RoundRobinScheduler(2);

    // [POLYMORPHISM] one array of the PARENT type Scheduler holds FCFS, SJF, SRTF, Priority, RR and HRRN objects.
    // [ABSTRACTION]  callers only use the Scheduler type; they never need to know the concrete class.
    // All algorithms behind one type, so the menu and comparison never depend on a specific one.
    private final Scheduler[] algorithms = {
        new FcfsScheduler(),
        new SjfScheduler(),
        new SrtfScheduler(),
        new PriorityScheduler(false),
        new PriorityScheduler(true),
        roundRobin,
        new HrrnScheduler()
    };

    // Every menu entry is an object; the menu is just a list of them.
    private final List<MenuItem> menu = Arrays.asList(
        new AddProcessItem(),
        new LoadSampleItem(),
        new RandomWorkloadItem(),
        new SetQuantumItem(),
        new RemoveProcessItem(),
        new RunAlgorithmItem(),
        new CompareItem(),
        new LiveSimulationItem(),
        new LiveRaceItem(),
        new WorkingItem(),
        new HelpItem(),
        new ExitItem()
    );

    private String noticeText;
    private String noticeColor;
    private int tipIndex;

    // Non-interactive tour: the sample workload through every algorithm, then the comparison.
    public void runDemo() {
        workload.replaceAll(SampleData.jobs());
        roundRobin.setQuantum(3);
        ConsoleView.banner();
        ConsoleView.section("Process table");
        ConsoleView.jobs(workload.view());
        for (Scheduler algorithm : algorithms) {
            ConsoleView.result(algorithm.schedule(workload.view()));
        }
        ConsoleView.comparison(algorithms, workload.view());
    }

    public void runInteractive() {
        try {
            boolean running = true;
            while (running) {
                showMenu();
                try {
                    String choice = prompt("Select an option").trim();
                    if (choice.isEmpty()) {
                        continue;
                    }
                    MenuItem item = find(choice);
                    if (item == null) {
                        error("Unknown option '" + choice + "'. Type h for help.");
                    } else {
                        running = item.invoke(this);
                    }
                } catch (InvalidJobException | IllegalArgumentException e) {
                    error(e.getMessage());
                }
            }
            System.out.println(Terminal.c("\n  Goodbye 👋\n", Terminal.DIM));
        } catch (NoSuchElementException e) {
            System.out.println(Terminal.c("\n\n  Goodbye 👋\n", Terminal.DIM));
        } finally {
            in.close();
        }
    }

    private MenuItem find(String key) {
        for (MenuItem item : menu) {
            if (item.getCode().equalsIgnoreCase(key)) {
                return item;
            }
        }
        return null;
    }

    private void showMenu() {
        Terminal.clear();
        ConsoleView.banner();
        ConsoleView.section("Process table");
        ConsoleView.jobs(workload.view());
        ConsoleView.status(workload.size(), roundRobin.getQuantum(), algorithms.length);
        ConsoleView.section("Menu");
        ConsoleView.menu(menu, this);
        System.out.println();
        if (noticeText != null) {
            System.out.println("  " + Terminal.c(noticeText, noticeColor));
            System.out.println();
            noticeText = null;
        }
        System.out.println(Terminal.c("  💡 " + TIPS[tipIndex++ % TIPS.length], Terminal.DIM));
    }

    // ---- AppContext ----

    @Override
    public Workload workload() {
        return workload;
    }

    @Override
    public Scheduler[] algorithms() {
        return algorithms.clone();
    }

    @Override
    public RoundRobinScheduler roundRobin() {
        return roundRobin;
    }

    @Override
    public Scanner scanner() {
        return in;
    }

    @Override
    public void success(String message) {
        noticeText = "✔ " + message;
        noticeColor = Terminal.GOOD;
    }

    @Override
    public void warn(String message) {
        noticeText = "! " + message;
        noticeColor = Terminal.WARN;
    }

    @Override
    public void error(String message) {
        noticeText = "✖ " + message;
        noticeColor = Terminal.BAD;
    }

    // Lists the algorithms, asks for a choice (and the quantum for Round Robin) and runs it.
    @Override
    public ScheduleResult chooseAndSchedule() {
        ConsoleView.section("Choose algorithm");
        for (int i = 0; i < algorithms.length; i++) {
            System.out.println("   " + Terminal.c("[" + (i + 1) + "]", Terminal.ACCENT) + " "
                    + Terminal.padRight(algorithms[i].getName(), 38)
                    + Terminal.c(algorithms[i].getCode(), Terminal.DIM));
        }
        Scheduler chosen = algorithms[askInt("Algorithm", 1, algorithms.length, null) - 1];
        if (chosen instanceof RoundRobinScheduler) {
            ((RoundRobinScheduler) chosen).setQuantum(
                    askInt("Time quantum", 1, Job.MAX_VALUE, roundRobin.getQuantum()));
        }
        return chosen.schedule(workload.view());
    }

    @Override
    public String prompt(String label) {
        System.out.print(Terminal.c("  › ", Terminal.ACCENT) + label + ": ");
        return in.nextLine();
    }

    @Override
    public boolean confirm(String question) {
        String answer = prompt(question + Terminal.c(" (y/N)", Terminal.DIM)).trim();
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    // Repeats until a whole number in range is entered; blank accepts the default.
    @Override
    public int askInt(String label, int lo, int hi, Integer defaultValue) {
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

    @Override
    public void pause() {
        System.out.print(Terminal.c("\n  Press Enter to continue…", Terminal.DIM));
        in.nextLine();
    }
}

// All drawing. Reads the model, never changes it.
final class ConsoleView {
    static final int WIDTH = 78;

    private ConsoleView() { }

    public static void banner() {
        int inner = WIDTH - 2;
        System.out.println(Terminal.c("╔" + Terminal.rep("═", inner) + "╗", Terminal.ACCENT));
        System.out.println(Terminal.c("║", Terminal.ACCENT)
                + Terminal.bold(Terminal.c(Terminal.center("CPU  SCHEDULING  CONSOLE", inner), Terminal.ACCENT))
                + Terminal.c("║", Terminal.ACCENT));
        System.out.println(Terminal.c("║", Terminal.ACCENT)
                + Terminal.c(Terminal.center("FCFS · SJF · SRTF · Priority · Round Robin · HRRN", inner), Terminal.DIM)
                + Terminal.c("║", Terminal.ACCENT));
        System.out.println(Terminal.c("╚" + Terminal.rep("═", inner) + "╝", Terminal.ACCENT));
    }

    public static void section(String title) {
        System.out.println();
        System.out.println(Terminal.c("━━ ", Terminal.ACCENT) + Terminal.bold(title) + " "
                + Terminal.c(Terminal.rep("━", Math.max(2, WIDTH - title.length() - 4)), Terminal.ACCENT));
    }

    // One-line dashboard under the process table.
    public static void status(int processes, int quantum, int algorithms) {
        System.out.println("  " + Terminal.c("●", processes > 0 ? Terminal.GOOD : Terminal.WARN) + " "
                + processes + (processes == 1 ? " process" : " processes")
                + Terminal.c("   ●", Terminal.ACCENT) + " RR quantum " + quantum
                + Terminal.c("   ●", Terminal.ACCENT) + " " + algorithms + " algorithms");
    }

    // Draws any list of MenuItems, grouped under headings. Works for every subclass (polymorphism).
    public static void menu(List<MenuItem> items, AppContext ctx) {
        MenuGroup last = null;
        for (MenuItem item : items) {
            if (item.getGroup() != last) {
                last = item.getGroup();
                System.out.println();
                System.out.println("  " + Terminal.bold(Terminal.c(last.getTitle().toUpperCase(), Terminal.DIM)));
            }
            System.out.println("   " + Terminal.c("[" + item.getCode() + "]", Terminal.ACCENT) + " "
                    + Terminal.padRight(item.label(ctx), 26) + Terminal.c(item.getSummary(), Terminal.DIM));
        }
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
            System.out.println(Terminal.c("  (no processes yet — press 2 for the sample set or 1 to add one)", Terminal.WARN));
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

    // Runs every scheduler on the same jobs, highlights the best value per metric, then charts and summarises.
    public static void comparison(Scheduler[] schedulers, List<Job> jobs) {
        section("Algorithm comparison");
        ScheduleResult[] results = new ScheduleResult[schedulers.length];
        double bestWait = Double.MAX_VALUE;
        double bestTurn = Double.MAX_VALUE;
        double bestResp = Double.MAX_VALUE;
        double worstWait = 0;
        for (int i = 0; i < schedulers.length; i++) {
            results[i] = schedulers[i].schedule(jobs);
            bestWait = Math.min(bestWait, results[i].getAverageWaiting());
            bestTurn = Math.min(bestTurn, results[i].getAverageTurnaround());
            bestResp = Math.min(bestResp, results[i].getAverageResponse());
            worstWait = Math.max(worstWait, results[i].getAverageWaiting());
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

        // bar chart of average waiting time (shorter is better)
        System.out.println();
        System.out.println(Terminal.c("  Average waiting time (shorter is better)", Terminal.DIM));
        for (int i = 0; i < results.length; i++) {
            double wait = results[i].getAverageWaiting();
            int len = worstWait > 0 ? (int) Math.round(wait / worstWait * 30) : 0;
            boolean isBest = Math.abs(wait - bestWait) < 1e-9;
            System.out.println("  " + Terminal.padRight(schedulers[i].getCode(), 8)
                    + Terminal.c(Terminal.rep("█", Math.max(1, len)), isBest ? Terminal.GOOD : Terminal.ACCENT)
                    + " " + String.format("%.2f", wait));
        }

        // plain-language verdict
        System.out.println();
        System.out.println("  " + Terminal.bold("Verdict"));
        System.out.println("   Lowest waiting     " + Terminal.c(winners(schedulers, results, ScheduleResult::getAverageWaiting), Terminal.GOOD));
        System.out.println("   Lowest turnaround  " + Terminal.c(winners(schedulers, results, ScheduleResult::getAverageTurnaround), Terminal.GOOD));
        System.out.println("   Lowest response    " + Terminal.c(winners(schedulers, results, ScheduleResult::getAverageResponse), Terminal.GOOD));
    }

    // Prints the step-by-step working built by CalcExplainer.
    public static void working(ScheduleResult r) {
        section("How it was calculated");
        for (String line : CalcExplainer.explain(r)) {
            if (line.matches("  P[0-9]+ .*")) {
                System.out.println(Terminal.bold(Terminal.c(line, Terminal.pidColor(line.trim().split(" ")[0]))));
            } else if (line.startsWith("Step") || line.equals("Formulas")) {
                System.out.println(Terminal.bold(Terminal.c(line, Terminal.ACCENT)));
            } else {
                System.out.println(line);
            }
        }
    }

    // Codes of every scheduler that ties for the lowest value of a metric.
    static String winners(Scheduler[] schedulers, ScheduleResult[] results, ToDoubleFunction<ScheduleResult> metric) {
        double best = Double.MAX_VALUE;
        for (ScheduleResult r : results) {
            best = Math.min(best, metric.applyAsDouble(r));
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < results.length; i++) {
            if (Math.abs(metric.applyAsDouble(results[i]) - best) < 1e-9) {
                names.append(names.length() == 0 ? "" : ", ").append(schedulers[i].getCode());
            }
        }
        return names + "  (" + String.format("%.2f", best) + ")";
    }
}

// Replays a schedule one time unit at a time, showing the CPU, a timeline ribbon and every process's
// state and progress; runs on a fixed delay or steps with Enter.
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
        int clock = end == 0 ? 30 : t * 30 / end;
        System.out.println("  " + Terminal.c(Terminal.rep("█", clock), Terminal.ACCENT)
                + Terminal.c(Terminal.rep("░", 30 - clock), Terminal.DIM));
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
            String[] medal = {"1st", "2nd", "3rd"};
            StringBuilder podium = new StringBuilder();
            for (int rank = 0; rank < Math.min(3, order.length); rank++) {
                podium.append(rank == 0 ? "" : "   ").append(medal[rank]).append(' ')
                      .append(schedulers[order[rank]].getCode())
                      .append(" (").append(String.format("%.2f", results[order[rank]].getAverageWaiting())).append(")");
            }
            System.out.println();
            System.out.println("  Podium by avg waiting time: " + Terminal.c(podium, Terminal.GOOD));
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

// ═════════════════════════════════════════════════════════════════════════════
//                                   SWING  GUI
// Reuses the exact same model and schedulers as the console version
// (Workload, Job, Scheduler, ScheduleResult ...). Only the drawing code is new.
// ═════════════════════════════════════════════════════════════════════════════

// [ENCAPSULATION] every field is private; the panels only draw what this class hands them.
// The main window. Holds the Workload and the schedulers; panels only draw what they are given.
final class SchedulerGui {
    private final JPanel root = new JPanel(new BorderLayout());

    private final Workload workload = new Workload();
    private final RoundRobinScheduler roundRobin = new RoundRobinScheduler(2);
    private final Scheduler[] algorithms = {
        new FcfsScheduler(), new SjfScheduler(), new SrtfScheduler(),
        new PriorityScheduler(false), new PriorityScheduler(true), roundRobin, new HrrnScheduler()
    };

    private final ProcessTableModel tableModel = new ProcessTableModel(workload);
    private final JTable processTable = new JTable(tableModel);
    private final JSpinner arrivalSpin = Theme.spinner(0, 0, Job.MAX_VALUE);
    private final JSpinner burstSpin = Theme.spinner(5, 1, Job.MAX_VALUE);
    private final JSpinner prioritySpin = Theme.spinner(1, 0, Job.MAX_VALUE);
    private final JSpinner quantumSpin = Theme.spinner(2, 1, Job.MAX_VALUE);
    private final JComboBox<String> algoBox = new JComboBox<>();

    private final SimulationPanel sim = new SimulationPanel();
    private final RacePanel race = new RacePanel();
    private final ComparePanel compare = new ComparePanel();
    private final DefaultTableModel resultModel = new DefaultTableModel(
            new String[] {"PID", "AT", "BT", "PR", "CT", "TAT", "WT", "RT"}, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final JLabel averages = Theme.label(" ", Font.BOLD, 13, Theme.GOOD);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final FlatButton simTab = new FlatButton("Simulation", Theme.ACCENT);
    private final FlatButton compareTab = new FlatButton("Compare", Theme.PANEL2);
    private final FlatButton raceTab = new FlatButton("Live Race", Theme.PANEL2);
    private final FlatButton calcTab = new FlatButton("How it's solved", Theme.PANEL2);
    private final JTextArea calcArea = new JTextArea();

    private final FlatButton playButton = new FlatButton("Play", Theme.GOOD);
    private final JSlider speed = new JSlider(1, 10, 5);
    private final JLabel clockLabel = Theme.label("t = 0 / 0", Font.BOLD, 13, Theme.TEXT);
    private final JLabel status = Theme.label("Ready.", Font.PLAIN, 12, Theme.DIM);
    private final javax.swing.Timer timer = new javax.swing.Timer(600, null);

    private int t;
    private int tEnd;
    private boolean fillingForm;

    SchedulerGui() {
        root.setBackground(Theme.BG);
        root.add(buildTop(), BorderLayout.NORTH);
        root.add(buildProcessPanel(), BorderLayout.WEST);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        timer.addActionListener(e -> tick());
        speed.addChangeListener(e -> timer.setDelay(1100 - speed.getValue() * 100));
        timer.setDelay(1100 - speed.getValue() * 100);

        workload.replaceAll(SampleData.jobs());
        tableModel.refresh();
        say("Sample workload loaded. Pick an algorithm and press Run.", Theme.DIM);
    }

    static void launch(boolean intro) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            Runnable open = () -> new SchedulerGui().show();
            if (intro) {
                new SplashWindow(open).setVisible(true);
            } else {
                open.run();
            }
        });
    }

    JPanel root() {
        return root;
    }

    void show() {
        JFrame frame = new JFrame("CPU Scheduling Studio");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setSize(1220, 780);
        frame.setMinimumSize(new Dimension(1000, 640));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ---------- layout ----------

    private JPanel buildTop() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        bar.setBackground(Theme.PANEL);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, Theme.BORDER));

        bar.add(Theme.label("CPU Scheduling Studio", Font.BOLD, 18, Theme.ACCENT));
        bar.add(Box.createHorizontalStrut(18));
        bar.add(Theme.label("Algorithm", Font.PLAIN, 12, Theme.DIM));
        for (Scheduler s : algorithms) {
            algoBox.addItem(s.getName());
        }
        algoBox.setFont(Theme.font(Font.PLAIN, 13));
        algoBox.setPreferredSize(new Dimension(260, 32));
        algoBox.setToolTipText("Choose the scheduling algorithm");
        algoBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setBackground(selected ? new Color(0, 105, 135) : Theme.PANEL2);
                setForeground(Theme.TEXT);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        algoBox.addActionListener(e -> {
            String summary = algorithms[algoBox.getSelectedIndex()].getSummary();
            say(summary, Theme.DIM);
        });
        bar.add(algoBox);

        bar.add(Theme.label("RR quantum", Font.PLAIN, 12, Theme.DIM));
        quantumSpin.setPreferredSize(new Dimension(70, 32));
        quantumSpin.setToolTipText("Time slice used by Round Robin");
        bar.add(quantumSpin);

        FlatButton run = new FlatButton("Run", Theme.ACCENT);
        run.setToolTipText("Run the chosen algorithm and animate it");
        run.addActionListener(e -> runSelected());
        FlatButton cmp = new FlatButton("Compare all", new Color(215, 135, 215));
        cmp.setToolTipText("Run every algorithm and compare the metrics");
        cmp.addActionListener(e -> compareAll());
        FlatButton rc = new FlatButton("Race", new Color(255, 175, 95));
        rc.setToolTipText("Animate every algorithm side by side");
        rc.addActionListener(e -> runRace());
        bar.add(run);
        bar.add(cmp);
        bar.add(rc);
        return bar;
    }

    private JPanel buildProcessPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.BG);
        panel.setBorder(new EmptyBorder(14, 14, 10, 6));
        panel.setPreferredSize(new Dimension(340, 100));

        panel.add(Theme.label("Processes", Font.BOLD, 15, Theme.TEXT), BorderLayout.NORTH);

        Theme.styleTable(processTable, new CellRenderer());
        processTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        processTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && processTable.getSelectedRowCount() == 1) {
                Job j = workload.view().get(processTable.getSelectedRow());
                fillingForm = true;
                arrivalSpin.setValue(j.getArrivalTime());
                burstSpin.setValue(j.getBurstTime());
                prioritySpin.setValue(j.getPriority());
                fillingForm = false;
            }
        });
        panel.add(Theme.scroll(processTable), BorderLayout.CENTER);

        JPanel form = new JPanel(new BorderLayout(0, 10));
        form.setOpaque(false);

        JPanel fields = new JPanel(new GridLayout(3, 2, 8, 8));
        fields.setOpaque(false);
        fields.add(Theme.label("Arrival time", Font.PLAIN, 13, Theme.DIM));
        fields.add(arrivalSpin);
        fields.add(Theme.label("Burst time", Font.PLAIN, 13, Theme.DIM));
        fields.add(burstSpin);
        fields.add(Theme.label("Priority (lower = higher)", Font.PLAIN, 13, Theme.DIM));
        fields.add(prioritySpin);
        form.add(fields, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(2, 3, 6, 6));
        buttons.setOpaque(false);
        buttons.add(action("Add", Theme.GOOD, "Add a process with these values", this::addProcess));
        buttons.add(action("Update", Theme.ACCENT, "Change the selected process", this::updateSelected));
        buttons.add(action("Remove", Theme.BAD, "Delete the selected processes", this::removeSelected));
        buttons.add(action("Sample", Theme.PANEL2, "Load the 5-process sample set", this::loadSample));
        buttons.add(action("Random", Theme.PANEL2, "Generate 5 random processes", this::randomWorkload));
        buttons.add(action("Clear", Theme.PANEL2, "Remove every process", this::clearAll));
        form.add(buttons, BorderLayout.SOUTH);

        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private FlatButton action(String text, Color color, String tip, Runnable r) {
        FlatButton b = new FlatButton(text, color);
        b.setToolTipText(tip);
        b.setBorder(new EmptyBorder(7, 6, 7, 6));
        b.addActionListener(e -> r.run());
        return b;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(Theme.BG);
        center.setBorder(new EmptyBorder(14, 6, 10, 14));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        simTab.addActionListener(e -> showCard("sim"));
        // The Compare tab computes the comparison itself, so it is never empty (it always uses the current processes).
        compareTab.addActionListener(e -> compareAll());
        raceTab.addActionListener(e -> {
            if (race.last() == 0) {
                runRace();
            } else {
                showCard("race");
            }
        });
        calcTab.addActionListener(e -> showCard("calc"));
        tabs.add(simTab);
        tabs.add(compareTab);
        tabs.add(raceTab);
        tabs.add(calcTab);
        center.add(tabs, BorderLayout.NORTH);

        // simulation card: animation on top, results table below
        JTable resultTable = new JTable(resultModel);
        Theme.styleTable(resultTable, new CellRenderer());
        JPanel results = new JPanel(new BorderLayout(0, 6));
        results.setBackground(Theme.BG);
        JScrollPane resultScroll = Theme.scroll(resultTable);
        resultScroll.setPreferredSize(new Dimension(400, 150));
        results.add(resultScroll, BorderLayout.CENTER);
        results.add(averages, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Theme.scroll(sim), results);
        split.setBorder(null);
        split.setResizeWeight(0.72);
        split.setDividerSize(8);
        split.setBackground(Theme.BG);

        cards.add(split, "sim");
        cards.add(compare, "cmp");
        cards.add(Theme.scroll(race), "race");

        calcArea.setEditable(false);
        calcArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        calcArea.setBackground(Theme.PANEL);
        calcArea.setForeground(Theme.TEXT);
        calcArea.setCaretColor(Theme.TEXT);
        calcArea.setMargin(new Insets(14, 18, 14, 18));
        calcArea.setText("Press Run first. The step-by-step working for that run (CT, TAT, WT, RT, averages,\n"
                + "CPU utilization and throughput) will appear here.");
        cards.add(Theme.scroll(calcArea), "calc");
        center.add(cards, BorderLayout.CENTER);

        center.add(buildPlaybackBar(), BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildPlaybackBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setBackground(Theme.PANEL);
        bar.setBorder(new LineBorder(Theme.BORDER));

        playButton.setToolTipText("Play / pause the animation");
        playButton.addActionListener(e -> togglePlay());
        FlatButton step = action("Step", Theme.PANEL2, "Advance one time unit", this::step);
        FlatButton finish = action("Finish", Theme.PANEL2, "Jump to the end", this::finish);
        FlatButton reset = action("Reset", Theme.PANEL2, "Back to time 0", this::resetPlayback);
        step.setBorder(new EmptyBorder(7, 14, 7, 14));
        finish.setBorder(new EmptyBorder(7, 14, 7, 14));
        reset.setBorder(new EmptyBorder(7, 14, 7, 14));

        speed.setOpaque(false);
        speed.setPreferredSize(new Dimension(130, 24));
        speed.setToolTipText("Animation speed");

        bar.add(playButton);
        bar.add(step);
        bar.add(finish);
        bar.add(reset);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(Theme.label("Speed", Font.PLAIN, 12, Theme.DIM));
        bar.add(speed);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(clockLabel);
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL);
        bar.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER), new EmptyBorder(6, 14, 6, 14)));
        bar.add(status, BorderLayout.WEST);
        return bar;
    }

    // ---------- process editing ----------

    private void addProcess() {
        try {
            String pid = workload.nextPid();
            workload.add(new Job(pid, intOf(arrivalSpin), intOf(burstSpin), intOf(prioritySpin)));
            tableModel.refresh();
            int last = workload.size() - 1;
            processTable.setRowSelectionInterval(last, last);
            say(pid + " added.", Theme.GOOD);
        } catch (InvalidJobException e) {
            say(e.getMessage(), Theme.BAD);
        }
    }

    private void updateSelected() {
        int row = processTable.getSelectedRow();
        if (row < 0 || fillingForm) {
            say("Select a process in the table first.", Theme.WARN);
            return;
        }
        try {
            Job old = workload.view().get(row);
            workload.set(row, new Job(old.getPid(), intOf(arrivalSpin), intOf(burstSpin), intOf(prioritySpin)));
            tableModel.refresh();
            processTable.setRowSelectionInterval(row, row);
            say(old.getPid() + " updated.", Theme.GOOD);
        } catch (InvalidJobException e) {
            say(e.getMessage(), Theme.BAD);
        }
    }

    private void removeSelected() {
        int[] rows = processTable.getSelectedRows();
        if (rows.length == 0) {
            say("Select one or more processes to remove.", Theme.WARN);
            return;
        }
        List<String> pids = new ArrayList<>();
        for (int r : rows) {
            pids.add(workload.view().get(r).getPid());
        }
        for (String pid : pids) {
            workload.remove(pid);
        }
        tableModel.refresh();
        say(pids.size() + (pids.size() == 1 ? " process removed." : " processes removed."), Theme.GOOD);
    }

    private void loadSample() {
        workload.replaceAll(SampleData.jobs());
        tableModel.refresh();
        say("Sample workload loaded (5 processes).", Theme.GOOD);
    }

    private void randomWorkload() {
        try {
            Random random = new Random();
            List<Job> fresh = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                fresh.add(new Job("P" + i, random.nextInt(11), 1 + random.nextInt(10), 1 + random.nextInt(5)));
            }
            workload.replaceAll(fresh);
            tableModel.refresh();
            say("Random workload generated (5 processes).", Theme.GOOD);
        } catch (InvalidJobException e) {
            say(e.getMessage(), Theme.BAD);
        }
    }

    private void clearAll() {
        if (workload.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(root, "Remove all " + workload.size() + " processes?",
                "Clear workload", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            workload.clear();
            tableModel.refresh();
            say("All processes cleared.", Theme.GOOD);
        }
    }

    private static int intOf(JSpinner s) {
        return ((Number) s.getValue()).intValue();
    }

    // ---------- running algorithms ----------

    private boolean ready() {
        if (workload.isEmpty()) {
            say("Add some processes first.", Theme.WARN);
            return false;
        }
        roundRobin.setQuantum(intOf(quantumSpin));
        return true;
    }

    void runSelected() {
        if (!ready()) {
            return;
        }
        // [POLYMORPHISM] 's' has the PARENT type Scheduler; the chosen subclass decides how schedule() behaves.
        Scheduler s = algorithms[algoBox.getSelectedIndex()];
        ScheduleResult r = s.schedule(workload.view());
        sim.setResult(r);
        fillResults(r);
        calcArea.setText(String.join("\n", CalcExplainer.explain(r)));
        calcArea.setCaretPosition(0);
        showCard("sim");
        startPlayback(sim.end());
        say("Running " + r.getDescription() + ".", Theme.ACCENT);
    }

    void compareAll() {
        if (!ready()) {
            return;
        }
        compare.setResults(algorithms, scheduleAll());
        timer.stop();
        updatePlayButton();
        showCard("cmp");
        say("Green marks the best value in each column.", Theme.DIM);
    }

    void runRace() {
        if (!ready()) {
            return;
        }
        race.setResults(algorithms, scheduleAll());
        showCard("race");
        startPlayback(race.last());
        say("Racing all " + algorithms.length + " algorithms on one clock.", Theme.ACCENT);
    }

    private ScheduleResult[] scheduleAll() {
        ScheduleResult[] results = new ScheduleResult[algorithms.length];
        for (int i = 0; i < algorithms.length; i++) {
            results[i] = algorithms[i].schedule(workload.view());
        }
        return results;
    }

    private void fillResults(ScheduleResult r) {
        resultModel.setRowCount(0);
        for (Job j : r.getJobs()) {
            resultModel.addRow(new Object[] {j.getPid(), j.getArrivalTime(), j.getBurstTime(), j.getPriority(),
                    j.getCompletionTime(), j.getTurnaroundTime(), j.getWaitingTime(), j.getResponseTime()});
        }
        averages.setText(String.format("  Avg waiting %.2f    Avg turnaround %.2f    Avg response %.2f    CPU %.1f%%    Throughput %.3f proc/unit",
                r.getAverageWaiting(), r.getAverageTurnaround(), r.getAverageResponse(),
                r.getCpuUtilization(), r.getThroughput()));
    }

    // ---------- playback ----------

    private void startPlayback(int end) {
        t = 0;
        tEnd = end;
        applyTime();
        timer.restart();
        updatePlayButton();
    }

    private void tick() {
        if (t < tEnd) {
            t++;
            applyTime();
        }
        if (t >= tEnd) {
            timer.stop();
            updatePlayButton();
        }
    }

    private void togglePlay() {
        if (tEnd == 0) {
            say("Press Run or Race first.", Theme.WARN);
            return;
        }
        if (timer.isRunning()) {
            timer.stop();
        } else {
            if (t >= tEnd) {
                t = 0;
                applyTime();
            }
            timer.start();
        }
        updatePlayButton();
    }

    private void step() {
        timer.stop();
        if (t < tEnd) {
            t++;
            applyTime();
        }
        updatePlayButton();
    }

    private void finish() {
        timer.stop();
        t = tEnd;
        applyTime();
        updatePlayButton();
    }

    void resetPlayback() {
        timer.stop();
        t = 0;
        applyTime();
        updatePlayButton();
    }

    private void applyTime() {
        sim.setTime(t);
        race.setTime(t);
        clockLabel.setText("t = " + t + " / " + tEnd);
    }

    private void updatePlayButton() {
        if (timer.isRunning()) {
            playButton.setText("Pause");
            playButton.setBase(Theme.WARN);
        } else if (tEnd > 0 && t >= tEnd) {
            playButton.setText("Replay");
            playButton.setBase(Theme.ACCENT);
        } else {
            playButton.setText("Play");
            playButton.setBase(Theme.GOOD);
        }
    }

    // ---------- small helpers ----------

    private void showCard(String name) {
        cardLayout.show(cards, name);
        simTab.setBase(name.equals("sim") ? Theme.ACCENT : Theme.PANEL2);
        compareTab.setBase(name.equals("cmp") ? Theme.ACCENT : Theme.PANEL2);
        raceTab.setBase(name.equals("race") ? Theme.ACCENT : Theme.PANEL2);
        calcTab.setBase(name.equals("calc") ? Theme.ACCENT : Theme.PANEL2);
    }

    private void say(String message, Color color) {
        status.setForeground(color);
        status.setText(message);
    }
}

// [INHERITANCE] extends JWindow (a borderless window); LogoPanel is a nested JPanel subclass.
// Loading screen: chip logo, rotating spinner, status text and a progress bar.
final class SplashWindow extends JWindow {
    private static final String[] STEPS = {
        "Booting scheduler kernel…",
        "Registering 7 scheduling algorithms…",
        "Building process table…",
        "Calibrating Gantt chart renderer…",
        "Warming up live simulator…",
        "Ready"
    };

    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel status = Theme.label(STEPS[0], Font.PLAIN, 13, Theme.DIM);
    private final LogoPanel logo = new LogoPanel();
    private double progress;

    SplashWindow(Runnable onDone) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Theme.BG);
        content.setBorder(new CompoundBorder(new LineBorder(Theme.ACCENT, 2), new EmptyBorder(20, 34, 24, 34)));
        content.add(logo, BorderLayout.CENTER);

        bar.setBorderPainted(false);
        bar.setBackground(Theme.PANEL2);
        bar.setForeground(Theme.ACCENT);
        bar.setPreferredSize(new Dimension(10, 8));
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        south.add(status, BorderLayout.NORTH);
        south.add(bar, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(540, 360);
        setLocationRelativeTo(null);

        javax.swing.Timer timer = new javax.swing.Timer(30, null);
        timer.addActionListener(e -> {
            progress += 1.25;
            logo.spin();
            bar.setValue((int) Math.min(100, progress));
            status.setText(STEPS[Math.min(STEPS.length - 1, (int) (progress / 100.0 * STEPS.length))]);
            if (progress >= 105) {
                timer.stop();
                dispose();
                onDone.run();
            }
        });
        timer.start();
    }

    private static final class LogoPanel extends JPanel {
        private int angle;

        LogoPanel() {
            setOpaque(false);
        }

        void spin() {
            angle = (angle + 9) % 360;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            int cx = getWidth() / 2;
            int size = 92;
            int cy = 62;
            int x = cx - size / 2;
            int y = cy - size / 2 + 10;

            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 5; i++) {
                int p = 12 + i * 17;
                g2.drawLine(x + p, y - 10, x + p, y);
                g2.drawLine(x + p, y + size, x + p, y + size + 10);
                g2.drawLine(x - 10, y + p, x, y + p);
                g2.drawLine(x + size, y + p, x + size + 10, y + p);
            }
            g2.setColor(Theme.PANEL2);
            g2.fillRoundRect(x, y, size, size, 18, 18);
            g2.setColor(Theme.ACCENT);
            g2.drawRoundRect(x, y, size, size, 18, 18);
            g2.setFont(Theme.font(Font.BOLD, 28));
            Theme.centered(g2, "CPU", x, y, size, size);

            g2.setColor(Theme.TEXT);
            g2.setFont(Theme.font(Font.BOLD, 24));
            Theme.centered(g2, "CPU Scheduling Studio", 0, y + size + 22, getWidth(), 30);
            g2.setColor(Theme.DIM);
            g2.setFont(Theme.font(Font.PLAIN, 12));
            Theme.centered(g2, "FCFS · SJF · SRTF · Priority · Round Robin · HRRN", 0, y + size + 54, getWidth(), 20);

            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(cx - 11, y + size + 84, 22, 22, angle, 110);
            g2.dispose();
        }
    }
}

// [INHERITANCE + POLYMORPHISM] extends JPanel and overrides paintComponent() to draw the animation.
// Single-algorithm view: CPU box, ready queue, Gantt chart and one progress bar per process.
final class SimulationPanel extends JPanel {
    private ScheduleResult result;
    private String[] owner = new String[0];
    private int end;
    private int t;

    SimulationPanel() {
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(400, 300));
    }

    int end() {
        return end;
    }

    void setResult(ScheduleResult r) {
        result = r;
        end = GanttPainter.endOf(r);
        owner = GanttPainter.ownerMap(r);
        t = 0;
        setPreferredSize(new Dimension(400, 330 + 30 * r.getJobs().size()));
        revalidate();
        repaint();
    }

    void setTime(int time) {
        t = time;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.aa(g);
        g2.setColor(Theme.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (result == null) {
            g2.setColor(Theme.DIM);
            g2.setFont(Theme.font(Font.PLAIN, 15));
            Theme.centered(g2, "Choose an algorithm and press Run to see the schedule.", 0, 0, getWidth(), getHeight());
            g2.dispose();
            return;
        }

        int pad = 22;
        int w = getWidth() - pad * 2;
        int y = pad;
        boolean done = t >= end;
        String running = done ? null : owner[t];

        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.font(Font.BOLD, 17));
        g2.drawString(result.getDescription(), pad, y + 14);
        y += 34;

        // CPU box
        Color cpuColor = done ? Theme.GOOD : running == null ? Theme.DIM : Theme.pid(running);
        g2.setColor(new Color(cpuColor.getRed(), cpuColor.getGreen(), cpuColor.getBlue(), 45));
        g2.fillRoundRect(pad, y, 130, 58, 14, 14);
        g2.setColor(cpuColor);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(pad, y, 130, 58, 14, 14);
        g2.setStroke(new BasicStroke(1f));
        g2.setFont(Theme.font(Font.PLAIN, 11));
        g2.drawString("CPU", pad + 10, y + 16);
        g2.setFont(Theme.font(Font.BOLD, 24));
        Theme.centered(g2, done ? "DONE" : running == null ? "IDLE" : running, pad, y + 12, 130, 46);

        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.font(Font.BOLD, 26));
        g2.drawString("t = " + t, pad + 156, y + 28);
        g2.setColor(Theme.DIM);
        g2.setFont(Theme.font(Font.PLAIN, 13));
        g2.drawString("of " + end + " time units", pad + 156, y + 48);

        // ready queue
        StringBuilder queue = new StringBuilder();
        for (Job j : result.getJobs()) {
            if (j.getArrivalTime() <= t && !j.getPid().equals(running) && executed(j.getPid()) < j.getBurstTime()) {
                queue.append(queue.length() == 0 ? "" : ", ").append(j.getPid());
            }
        }
        g2.setColor(Theme.DIM);
        g2.drawString("Waiting: " + (queue.length() == 0 ? "-" : queue), pad + 320, y + 28);
        y += 78;

        // Gantt
        g2.setColor(Theme.DIM);
        g2.setFont(Theme.font(Font.BOLD, 12));
        g2.drawString("GANTT CHART", pad, y);
        y += 10;
        GanttPainter.paint(g2, result.getTimeline(), t, end, pad, y, w, 46, true);
        y += 46 + 40;

        // processes
        g2.setColor(Theme.DIM);
        g2.setFont(Theme.font(Font.BOLD, 12));
        g2.drawString("PROCESSES", pad, y);
        y += 10;
        for (Job j : result.getJobs()) {
            int ex = executed(j.getPid());
            String state;
            Color stateColor;
            if (j.getArrivalTime() > t) {
                state = "not arrived";
                stateColor = Theme.DIM;
            } else if (ex == j.getBurstTime()) {
                state = "done";
                stateColor = Theme.GOOD;
            } else if (j.getPid().equals(running)) {
                state = "running";
                stateColor = Theme.ACCENT;
            } else {
                state = "waiting";
                stateColor = Theme.WARN;
            }
            g2.setFont(Theme.font(Font.BOLD, 14));
            g2.setColor(Theme.pid(j.getPid()));
            g2.drawString(j.getPid(), pad, y + 17);
            g2.setFont(Theme.font(Font.PLAIN, 12));
            g2.setColor(stateColor);
            g2.drawString(state, pad + 46, y + 17);

            int bx = pad + 140;
            int bw = Math.max(40, w - 140 - 60);
            g2.setColor(Theme.PANEL2);
            g2.fillRoundRect(bx, y + 5, bw, 14, 8, 8);
            int fill = bw * ex / j.getBurstTime();
            if (fill > 0) {
                g2.setColor(Theme.pid(j.getPid()));
                g2.fillRoundRect(bx, y + 5, Math.max(8, fill), 14, 8, 8);
            }
            g2.setColor(Theme.DIM);
            g2.setFont(Theme.font(Font.PLAIN, 12));
            g2.drawString(ex + "/" + j.getBurstTime(), bx + bw + 10, y + 17);
            y += 30;
        }

        if (done) {
            y += 14;
            g2.setFont(Theme.font(Font.BOLD, 13));
            g2.setColor(Theme.GOOD);
            g2.drawString(String.format("Finished at %d   avg waiting %.2f   avg turnaround %.2f   avg response %.2f",
                    end, result.getAverageWaiting(), result.getAverageTurnaround(), result.getAverageResponse()), pad, y);
        }
        g2.dispose();
    }

    private int executed(String pid) {
        int n = 0;
        for (int k = 0; k < Math.min(t, owner.length); k++) {
            if (pid.equals(owner[k])) {
                n++;
            }
        }
        return n;
    }
}

// [INHERITANCE + POLYMORPHISM] extends JPanel and overrides paintComponent().
// Race view: every algorithm gets a lane and all lanes advance on one shared clock.
final class RacePanel extends JPanel {
    private static final int LANE = 62;
    private Scheduler[] algos = new Scheduler[0];
    private ScheduleResult[] results = new ScheduleResult[0];
    private int last;
    private int t;

    RacePanel() {
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(400, 300));
    }

    int last() {
        return last;
    }

    void setResults(Scheduler[] a, ScheduleResult[] r) {
        algos = a;
        results = r;
        last = 0;
        for (ScheduleResult x : r) {
            last = Math.max(last, GanttPainter.endOf(x));
        }
        t = 0;
        setPreferredSize(new Dimension(400, 150 + LANE * r.length));
        revalidate();
        repaint();
    }

    void setTime(int time) {
        t = time;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.aa(g);
        g2.setColor(Theme.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (results.length == 0) {
            g2.setColor(Theme.DIM);
            g2.setFont(Theme.font(Font.PLAIN, 15));
            Theme.centered(g2, "Press Race to run every algorithm side by side.", 0, 0, getWidth(), getHeight());
            g2.dispose();
            return;
        }
        int pad = 22;
        int labelW = 84;
        int statusW = 120;
        int x = pad + labelW;
        int w = Math.max(100, getWidth() - pad * 2 - labelW - statusW);
        int top = 52;

        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.font(Font.BOLD, 17));
        g2.drawString("Live race - all algorithms", pad, 28);
        g2.setColor(Theme.DIM);
        g2.setFont(Theme.font(Font.PLAIN, 13));
        g2.drawString("t = " + t + " / " + last, pad + 250, 28);

        // grid
        int step = Math.max(1, (int) Math.ceil(last / 10.0));
        for (int v = 0; v <= last; v += step) {
            int gx = x + (int) Math.round((double) v * w / last);
            g2.setColor(new Color(40, 47, 62));
            g2.drawLine(gx, top - 6, gx, top + LANE * results.length - 18);
            g2.setColor(Theme.DIM);
            g2.setFont(Theme.font(Font.PLAIN, 11));
            g2.drawString(String.valueOf(v), gx - 3, top + LANE * results.length - 4);
        }

        for (int i = 0; i < results.length; i++) {
            ScheduleResult r = results[i];
            int ly = top + i * LANE;
            int end = GanttPainter.endOf(r);
            boolean finished = t >= end;
            g2.setColor(Theme.ACCENT);
            g2.setFont(Theme.font(Font.BOLD, 14));
            g2.drawString(algos[i].getCode(), pad, ly + 24);
            GanttPainter.paint(g2, r.getTimeline(), t, last, x, ly + 4, w, 30, false);

            int completed = 0;
            for (Job j : r.getJobs()) {
                if (j.getCompletionTime() <= t) {
                    completed++;
                }
            }
            g2.setFont(Theme.font(Font.BOLD, 12));
            g2.setColor(finished ? Theme.GOOD : Theme.DIM);
            g2.drawString(finished ? "done @ " + end : completed + "/" + r.getJobs().size() + " done", x + w + 14, ly + 24);
        }

        if (t >= last) {
            Integer[] order = new Integer[results.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, Comparator.comparingDouble(i -> results[i].getAverageWaiting()));
            StringBuilder podium = new StringBuilder("Podium by avg waiting:  ");
            String[] place = {"1st", "2nd", "3rd"};
            for (int k = 0; k < Math.min(3, order.length); k++) {
                podium.append(k == 0 ? "" : "     ").append(place[k]).append(' ').append(algos[order[k]].getCode())
                      .append(String.format(" (%.2f)", results[order[k]].getAverageWaiting()));
            }
            g2.setColor(Theme.GOOD);
            g2.setFont(Theme.font(Font.BOLD, 14));
            g2.drawString(podium.toString(), pad, top + LANE * results.length + 24);
        }
        g2.dispose();
    }
}

// [INHERITANCE] extends JPanel; its BarChart is a nested JPanel subclass that overrides paintComponent().
// Comparison view: metrics table (best values in green), grouped bar chart and a plain-language verdict.
final class ComparePanel extends JPanel {
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] {"Algorithm", "Avg WT", "Avg TAT", "Avg RT", "CPU %", "Throughput"}, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final CellRenderer renderer = new CellRenderer();
    private final JTable table = new JTable(model);
    private final BarChart chart = new BarChart();
    private final JLabel verdict = new JLabel(" ");

    ComparePanel() {
        super(new BorderLayout(0, 10));
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        Theme.styleTable(table, renderer);

        JScrollPane sp = Theme.scroll(table);
        sp.setPreferredSize(new Dimension(400, 7 * 28 + 34));
        add(sp, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        verdict.setFont(Theme.font(Font.PLAIN, 13));
        verdict.setBorder(new EmptyBorder(4, 2, 0, 2));
        add(verdict, BorderLayout.SOUTH);
    }

    void setResults(Scheduler[] algos, ScheduleResult[] results) {
        model.setRowCount(0);
        double[] bestMin = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        for (ScheduleResult r : results) {
            bestMin[0] = Math.min(bestMin[0], r.getAverageWaiting());
            bestMin[1] = Math.min(bestMin[1], r.getAverageTurnaround());
            bestMin[2] = Math.min(bestMin[2], r.getAverageResponse());
        }
        Set<String> best = new HashSet<>();
        for (int i = 0; i < results.length; i++) {
            ScheduleResult r = results[i];
            model.addRow(new Object[] {algos[i].getCode(), String.format("%.2f", r.getAverageWaiting()),
                    String.format("%.2f", r.getAverageTurnaround()), String.format("%.2f", r.getAverageResponse()),
                    String.format("%.1f%%", r.getCpuUtilization()), String.format("%.3f", r.getThroughput())});
            if (Math.abs(r.getAverageWaiting() - bestMin[0]) < 1e-9) {
                best.add(i + ",1");
            }
            if (Math.abs(r.getAverageTurnaround() - bestMin[1]) < 1e-9) {
                best.add(i + ",2");
            }
            if (Math.abs(r.getAverageResponse() - bestMin[2]) < 1e-9) {
                best.add(i + ",3");
            }
        }
        renderer.setHighlight(best);
        chart.setData(algos, results);
        verdict.setText("<html><font color='#87929F'>Lowest waiting</font> <b><font color='#6EDC8C'>"
                + ConsoleView.winners(algos, results, ScheduleResult::getAverageWaiting)
                + "</font></b> &nbsp;&nbsp; <font color='#87929F'>turnaround</font> <b><font color='#6EDC8C'>"
                + ConsoleView.winners(algos, results, ScheduleResult::getAverageTurnaround)
                + "</font></b> &nbsp;&nbsp; <font color='#87929F'>response</font> <b><font color='#6EDC8C'>"
                + ConsoleView.winners(algos, results, ScheduleResult::getAverageResponse) + "</font></b></html>");
        table.repaint();
    }

    // Grouped bars: waiting / turnaround / response per algorithm (shorter is better).
    private static final class BarChart extends JPanel {
        private static final Color[] SERIES = {Theme.ACCENT, new Color(215, 135, 215), Theme.GOOD};
        private static final String[] NAMES = {"Waiting", "Turnaround", "Response"};
        private Scheduler[] algos = new Scheduler[0];
        private ScheduleResult[] results = new ScheduleResult[0];

        BarChart() {
            setBackground(Theme.PANEL);
            setBorder(new LineBorder(Theme.BORDER));
        }

        void setData(Scheduler[] a, ScheduleResult[] r) {
            algos = a;
            results = r;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(Theme.PANEL);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (results.length == 0) {
                g2.setColor(Theme.DIM);
                g2.setFont(Theme.font(Font.PLAIN, 14));
                Theme.centered(g2, "Add some processes and open this tab to compare all algorithms.", 0, 0, getWidth(), getHeight());
                g2.dispose();
                return;
            }
            int left = 46;
            int bottom = 34;
            int top = 34;
            int plotW = getWidth() - left - 20;
            int plotH = getHeight() - top - bottom;
            double max = 0;
            for (ScheduleResult r : results) {
                max = Math.max(max, Math.max(r.getAverageWaiting(), Math.max(r.getAverageTurnaround(), r.getAverageResponse())));
            }
            max = Math.max(1, max);

            for (int i = 0; i <= 4; i++) {
                int gy = top + plotH - plotH * i / 4;
                g2.setColor(new Color(40, 47, 62));
                g2.drawLine(left, gy, left + plotW, gy);
                g2.setColor(Theme.DIM);
                g2.setFont(Theme.font(Font.PLAIN, 11));
                g2.drawString(String.format("%.1f", max * i / 4), 10, gy + 4);
            }

            // legend
            int lx = left;
            g2.setFont(Theme.font(Font.PLAIN, 12));
            for (int s = 0; s < 3; s++) {
                g2.setColor(SERIES[s]);
                g2.fillRoundRect(lx, 12, 12, 12, 4, 4);
                g2.setColor(Theme.TEXT);
                g2.drawString(NAMES[s], lx + 18, 23);
                lx += 100;
            }

            int groupW = plotW / results.length;
            int barW = Math.max(6, Math.min(26, (groupW - 14) / 3));
            for (int i = 0; i < results.length; i++) {
                double[] v = {results[i].getAverageWaiting(), results[i].getAverageTurnaround(), results[i].getAverageResponse()};
                int gx = left + i * groupW + (groupW - barW * 3) / 2;
                for (int s = 0; s < 3; s++) {
                    int bh = (int) Math.round(plotH * v[s] / max);
                    g2.setColor(SERIES[s]);
                    g2.fillRoundRect(gx + s * barW, top + plotH - bh, barW - 2, Math.max(2, bh), 5, 5);
                }
                g2.setColor(Theme.TEXT);
                g2.setFont(Theme.font(Font.BOLD, 12));
                Theme.centered(g2, algos[i].getCode(), left + i * groupW, top + plotH + 6, groupW, 20);
            }
            g2.dispose();
        }
    }
}

// [INHERITANCE + POLYMORPHISM] extends AbstractTableModel; JTable calls getValueAt() without knowing about Workload.
// Table model that simply mirrors the Workload (the Workload stays the single source of truth).
final class ProcessTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"PID", "Arrival", "Burst", "Priority"};
    private final Workload workload;

    ProcessTableModel(Workload workload) {
        this.workload = workload;
    }

    @Override
    public int getRowCount() {
        return workload.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int col) {
        return COLUMNS[col];
    }

    @Override
    public Object getValueAt(int row, int col) {
        Job j = workload.view().get(row);
        switch (col) {
            case 0: return j.getPid();
            case 1: return j.getArrivalTime();
            case 2: return j.getBurstTime();
            default: return j.getPriority();
        }
    }

    void refresh() {
        fireTableDataChanged();
    }
}

// [INHERITANCE + POLYMORPHISM] extends DefaultTableCellRenderer and overrides getTableCellRendererComponent().
// Table cell renderer: centred text, zebra rows, PID colours and optional green "best value" cells.
final class CellRenderer extends DefaultTableCellRenderer {
    private Set<String> highlight = new HashSet<>();

    void setHighlight(Set<String> cells) {
        highlight = cells;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean focus, int row, int col) {
        super.getTableCellRendererComponent(table, value, selected, false, row, col);
        setHorizontalAlignment(CENTER);
        setBorder(new EmptyBorder(0, 6, 0, 6));
        String text = String.valueOf(value);
        if (!selected) {
            setBackground(row % 2 == 0 ? Theme.PANEL : new Color(29, 35, 47));
            Color fg = Theme.TEXT;
            if (col == 0 && text.matches("P[0-9]+")) {
                fg = Theme.pid(text);
            }
            if (highlight.contains(row + "," + col)) {
                fg = Theme.GOOD;
            }
            setForeground(fg);
        }
        setFont(Theme.font(col == 0 || highlight.contains(row + "," + col) ? Font.BOLD : Font.PLAIN, 13));
        return this;
    }
}

// [INHERITANCE + POLYMORPHISM] same idea for the table header.
final class HeaderRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean focus, int row, int col) {
        super.getTableCellRendererComponent(table, value, false, false, row, col);
        setBackground(Theme.PANEL2);
        setForeground(Theme.DIM);
        setFont(Theme.font(Font.BOLD, 12));
        setHorizontalAlignment(CENTER);
        setBorder(new CompoundBorder(new MatteBorder(0, 0, 2, 0, Theme.BORDER), new EmptyBorder(6, 4, 6, 4)));
        return this;
    }
}

// [INHERITANCE]  FlatButton extends JButton (it IS a button, so it gets clicks, tooltips and focus for free).
// [POLYMORPHISM] it overrides paintComponent(): Swing calls the JButton method and our version runs instead.
// Rounded flat button with hover / pressed colours (ignores the look and feel, so it looks the same everywhere).
final class FlatButton extends JButton {
    private Color base;
    private boolean hover;

    FlatButton(String text, Color base) {
        super(text);
        this.base = base;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setFont(Theme.font(Font.BOLD, 13));
        setBorder(new EmptyBorder(8, 14, 8, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
        applyForeground();
    }

    void setBase(Color color) {
        base = color;
        applyForeground();
        repaint();
    }

    private void applyForeground() {
        double luminance = 0.299 * base.getRed() + 0.587 * base.getGreen() + 0.114 * base.getBlue();
        setForeground(luminance > 140 ? Theme.INK : Theme.TEXT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.aa(g);
        Color c = !isEnabled() ? Theme.PANEL2
                : getModel().isPressed() ? base.darker()
                : hover ? base.brighter() : base;
        g2.setColor(c);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }
}
