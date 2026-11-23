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
import java.util.function.ToDoubleFunction;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/*
 * CPU SCHEDULING CONSOLE
 *
 * Where the four OOP pillars live in this program:
 *
 *  ABSTRACTION    Describable (interface), AppContext (interface), Scheduler / SelectionScheduler /
 *                 MenuItem (abstract classes). Callers use the abstract type and never need to know
 *                 which concrete algorithm or menu entry they are holding.
 *
 *  INHERITANCE    Scheduler -> SelectionScheduler -> Fcfs / Sjf / Srtf / Priority / Hrrn
 *                 Scheduler -> RoundRobinScheduler
 *                 MenuItem  -> WorkloadItem / SimulationItem / SystemItem -> concrete menu entries
 *
 *  POLYMORPHISM   algorithm.schedule(jobs) runs whichever algorithm is behind the Scheduler reference;
 *                 item.invoke(ctx) runs whichever menu action was chosen; item.label(ctx) is
 *                 overridden by entries whose text changes at run time. There is no big switch.
 *
 *  ENCAPSULATION  Job, Segment, ScheduleResult and Workload keep their fields private, validate input
 *                 and only hand out unmodifiable views; RoundRobinScheduler guards its quantum;
 *                 Terminal hides the colour/encoding state.
 *
 * HOW THE METRICS ARE SOLVED
 *   (a full worked example for your own data is printed by console option W and shown in the GUI tab
 *    "How it's solved"; the formulas are also explained next to the code in Job and ScheduleResult)
 *      CT  = clock value when the job's last time unit finishes
 *      TAT = CT - AT                 WT = TAT - BT                RT = first CPU time - AT
 *      Avg X = (sum of X over all jobs) / n
 *      CPU % = busy time / total time x 100          Throughput = n / total time
 *
 * Flags:  (none)       Swing GUI                   --console    text UI instead of the GUI
 *         --demo       non-interactive text tour   --ascii      plain ASCII borders (text UI)
 *         --no-intro   skip the loading screen
 */

// Entry point: opens the GUI by default; --console / --demo (or a machine with no display) use the text UI.
public class CpuSchedulingConsole {
    public static void main(String[] args) throws IOException {
        List<String> flags = Arrays.asList(args);
        boolean demo = flags.contains("--demo");
        boolean console = demo || flags.contains("--console") || GraphicsEnvironment.isHeadless();
        if (!console) {
            SchedulerGui.launch(!flags.contains("--no-intro"));
            return;
        }

        Terminal.setup(demo, flags.contains("--ascii"));
        ConsoleApp app = new ConsoleApp();
        if (demo) {
            app.runDemo();
        } else {
            if (!flags.contains("--no-intro")) {
                LoadingScreen.show(520);
            }
            app.runInteractive();
        }
    }
}

// ───────────────────────────── ABSTRACTION: contracts ─────────────────────────────

// [ABSTRACTION] An interface says WHAT something can do (getName / getCode / getSummary) and hides HOW.
// Scheduler and MenuItem both implement it, so code that only needs a name can accept either one.
// Anything that has a name, a short code and a one-line explanation (algorithms and menu entries).
interface Describable {
    String getName();

    String getCode();

    String getSummary();
}

// [ABSTRACTION]   Menu items talk to this small interface instead of the big ConsoleApp class.
// [ENCAPSULATION] ConsoleApp keeps its fields private; only the methods listed here are exposed.
// What a menu action is allowed to ask of the application. Actions never touch ConsoleApp directly.
interface AppContext {
    Workload workload();

    Scheduler[] algorithms();

    RoundRobinScheduler roundRobin();

    Scanner scanner();

    String prompt(String label);

    int askInt(String label, int lo, int hi, Integer defaultValue);

    boolean confirm(String question);

    void pause();

    void success(String message);

    void warn(String message);

    void error(String message);

    ScheduleResult chooseAndSchedule();
}

// ───────────────────────────── Loading screen ─────────────────────────────

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

// ───────────────────────────── Application ─────────────────────────────

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

// ───────────────────────────── Menu items (inheritance + polymorphism) ─────────────────────────────

// [ABSTRACTION]   abstract class: perform() has no body here, every concrete menu entry must supply it.
// [ENCAPSULATION] key, label, summary and group are private final and read through getters.
// Base class of every menu entry. invoke() is the template method; subclasses supply perform().
abstract class MenuItem implements Describable {
    enum Group {
        WORKLOAD("Workload"), SIMULATE("Simulate"), SYSTEM("System");

        private final String title;

        Group(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }
    }

    private final String key;
    private final String label;
    private final String summary;
    private final Group group;

    protected MenuItem(String key, String label, String summary, Group group) {
        this.key = key;
        this.label = label;
        this.summary = summary;
        this.group = group;
    }

    @Override
    public String getName() { return label; }

    @Override
    public String getCode() { return key; }

    @Override
    public String getSummary() { return summary; }

    public Group getGroup() { return group; }

    // Overridable: lets an entry show live state (e.g. the current quantum).
    public String label(AppContext ctx) {
        return label;
    }

    protected boolean requiresJobs() {
        return false;
    }

    protected boolean pauseAfter() {
        return false;
    }

    // Returns false when the application should quit.
    protected abstract boolean perform(AppContext ctx) throws InvalidJobException;

    // [POLYMORPHISM] perform() is called on the abstract type; the real action depends on which subclass was picked.
    public final boolean invoke(AppContext ctx) throws InvalidJobException {
        if (requiresJobs() && ctx.workload().isEmpty()) {
            throw new IllegalArgumentException("Add some processes first (option 1, 2 or 3).");
        }
        boolean keepRunning = perform(ctx);
        if (keepRunning && pauseAfter()) {
            ctx.pause();
        }
        return keepRunning;
    }
}

// [INHERITANCE] second level of the hierarchy: MenuItem -> WorkloadItem -> AddProcessItem, LoadSampleItem ...
// Entries that edit the process list.
abstract class WorkloadItem extends MenuItem {
    protected WorkloadItem(String key, String label, String summary) {
        super(key, label, summary, Group.WORKLOAD);
    }
}

// [INHERITANCE] SimulationItem overrides requiresJobs() and pauseAfter() once for every entry below it.
// Entries that run schedulers: they need processes and leave their output on screen until Enter.
abstract class SimulationItem extends MenuItem {
    protected SimulationItem(String key, String label, String summary) {
        super(key, label, summary, Group.SIMULATE);
    }

    @Override
    protected boolean requiresJobs() {
        return true;
    }

    @Override
    protected boolean pauseAfter() {
        return true;
    }
}

// Entries about the program itself.
abstract class SystemItem extends MenuItem {
    protected SystemItem(String key, String label, String summary) {
        super(key, label, summary, Group.SYSTEM);
    }
}

class AddProcessItem extends WorkloadItem {
    AddProcessItem() {
        super("1", "Add process", "enter arrival, burst and priority");
    }

    @Override
    protected boolean perform(AppContext ctx) throws InvalidJobException {
        String pid = ctx.workload().nextPid();
        System.out.println(Terminal.c("  Adding " + pid, Terminal.pidColor(pid)));
        int arrival = ctx.askInt("Arrival time", 0, Job.MAX_VALUE, 0);
        int burst = ctx.askInt("Burst time", 1, Job.MAX_VALUE, 5);
        int priority = ctx.askInt("Priority (lower = higher)", 0, Job.MAX_VALUE, 1);
        ctx.workload().add(new Job(pid, arrival, burst, priority));
        ctx.success(pid + " added (AT=" + arrival + ", BT=" + burst + ", PR=" + priority + ")");
        return true;
    }
}

class LoadSampleItem extends WorkloadItem {
    LoadSampleItem() {
        super("2", "Load sample set", "5 ready-made processes");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ctx.workload().replaceAll(SampleData.jobs());
        ctx.success("Sample workload loaded (" + ctx.workload().size() + " processes)");
        return true;
    }
}

class RandomWorkloadItem extends WorkloadItem {
    RandomWorkloadItem() {
        super("3", "Random workload", "generate 1-8 random processes");
    }

    @Override
    protected boolean perform(AppContext ctx) throws InvalidJobException {
        int count = ctx.askInt("How many processes", 1, 8, 5);
        Random random = new Random();
        List<Job> fresh = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            fresh.add(new Job("P" + i, random.nextInt(11), 1 + random.nextInt(10), 1 + random.nextInt(5)));
        }
        ctx.workload().replaceAll(fresh);
        ctx.success("Random workload generated (" + count + " processes)");
        return true;
    }
}

class SetQuantumItem extends WorkloadItem {
    SetQuantumItem() {
        super("4", "Set time quantum", "used by Round Robin");
    }

    // Polymorphism in action: this entry's text depends on live state.
    @Override
    public String label(AppContext ctx) {
        return "Set time quantum (" + ctx.roundRobin().getQuantum() + ")";
    }

    @Override
    protected boolean perform(AppContext ctx) {
        int quantum = ctx.askInt("Time quantum", 1, Job.MAX_VALUE, ctx.roundRobin().getQuantum());
        ctx.roundRobin().setQuantum(quantum);
        ctx.success("Round Robin quantum is now " + quantum);
        return true;
    }
}

class RemoveProcessItem extends WorkloadItem {
    RemoveProcessItem() {
        super("5", "Remove / clear", "delete one process or all of them");
    }

    @Override
    protected boolean requiresJobs() {
        return true;
    }

    @Override
    protected boolean perform(AppContext ctx) {
        String pid = ctx.prompt("PID to remove (or 'all')").trim().toUpperCase();
        if (pid.equals("ALL")) {
            if (ctx.confirm("Remove all " + ctx.workload().size() + " processes?")) {
                ctx.workload().clear();
                ctx.success("All processes cleared");
            } else {
                ctx.warn("Nothing removed");
            }
        } else if (ctx.workload().remove(pid)) {
            ctx.success(pid + " removed");
        } else {
            ctx.error("No process named '" + pid + "'");
        }
        return true;
    }
}

class RunAlgorithmItem extends SimulationItem {
    RunAlgorithmItem() {
        super("6", "Run an algorithm", "Gantt chart + metrics for one algorithm");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ConsoleView.result(ctx.chooseAndSchedule());
        return true;
    }
}

class CompareItem extends SimulationItem {
    CompareItem() {
        super("7", "Compare all algorithms", "side-by-side table, bars and verdict");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ConsoleView.comparison(ctx.algorithms(), ctx.workload().view());
        return true;
    }
}

class WorkingItem extends SimulationItem {
    WorkingItem() {
        super("W", "Show the working", "step-by-step: how WT, TAT and RT are solved");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ScheduleResult r = ctx.chooseAndSchedule();
        ConsoleView.result(r);
        ConsoleView.working(r);
        return true;
    }
}

class LiveSimulationItem extends SimulationItem {
    LiveSimulationItem() {
        super("8", "Live simulation", "watch one algorithm tick by tick");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ScheduleResult result = ctx.chooseAndSchedule();
        int delay = ctx.askInt("Delay per time unit in ms (0 = step with Enter)", 0, 5000, 400);
        LiveSimulation.play(result, delay, ctx.scanner());
        return true;
    }
}

class LiveRaceItem extends SimulationItem {
    LiveRaceItem() {
        super("9", "Live race", "all algorithms on one shared clock");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ctx.roundRobin().setQuantum(ctx.askInt("Round Robin time quantum", 1, Job.MAX_VALUE,
                ctx.roundRobin().getQuantum()));
        int delay = ctx.askInt("Delay per time unit in ms (0 = step with Enter)", 0, 5000, 400);

        Scheduler[] algorithms = ctx.algorithms();
        ScheduleResult[] results = new ScheduleResult[algorithms.length];
        for (int i = 0; i < algorithms.length; i++) {
            results[i] = algorithms[i].schedule(ctx.workload().view());
        }
        if (LiveSimulation.playAll(algorithms, results, delay, ctx.scanner())) {
            // the race view scrolls, so show every complete Gantt chart once it is over
            ConsoleView.section("Final Gantt charts");
            for (int i = 0; i < algorithms.length; i++) {
                System.out.println(Terminal.c("  " + results[i].getDescription(), Terminal.ACCENT));
                ConsoleView.gantt(results[i].getTimeline());
                System.out.println();
            }
            ConsoleView.comparison(algorithms, ctx.workload().view());
        }
        return true;
    }
}

class HelpItem extends SystemItem {
    HelpItem() {
        super("H", "Help & glossary", "what the columns and algorithms mean");
    }

    @Override
    protected boolean pauseAfter() {
        return true;
    }

    @Override
    protected boolean perform(AppContext ctx) {
        ConsoleView.section("Glossary");
        String[][] terms = {
            {"AT", "Arrival time: when the process enters the ready queue"},
            {"BT", "Burst time: total CPU time the process needs"},
            {"PR", "Priority: lower number = more important"},
            {"CT", "Completion time: when the process finished"},
            {"TAT", "Turnaround = CT - AT"},
            {"WT", "Waiting = TAT - BT (time spent ready but not running)"},
            {"RT", "Response = first run - AT"},
            {"W", "Menu option W prints the full step-by-step working"}
        };
        for (String[] t : terms) {
            System.out.println("   " + Terminal.c(Terminal.padRight(t[0], 5), Terminal.ACCENT) + t[1]);
        }
        ConsoleView.section("Algorithms");
        for (Scheduler s : ctx.algorithms()) {
            System.out.println("   " + Terminal.c(Terminal.padRight(s.getCode(), 8), Terminal.ACCENT) + s.getSummary());
        }
        return true;
    }
}

class ExitItem extends SystemItem {
    ExitItem() {
        super("0", "Exit", "leave the program");
    }

    @Override
    protected boolean perform(AppContext ctx) {
        return false;
    }
}

// ───────────────────────────── View ─────────────────────────────

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
        MenuItem.Group last = null;
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

// ───────────────────────────── Terminal ─────────────────────────────

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

// ───────────────────────────── Schedulers (abstraction, inheritance, polymorphism) ─────────────────────────────

// [ABSTRACTION]   abstract class: it cannot be created with 'new'; getSummary() and execute() have no body,
//                 so every real algorithm is forced to provide them.
// [ENCAPSULATION] name and code are private final and only readable through getters.
// Contract for every algorithm. schedule() is a template method: validate, copy the jobs, run, package the result.
abstract class Scheduler implements Describable {
    private final String name;
    private final String code;

    protected Scheduler(String name, String code) {
        this.name = name;
        this.code = code;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getCode() { return code; }

    // Each algorithm explains itself in one line (used by the help screen).
    @Override
    public abstract String getSummary();

    public String getDescription() { return name; }

    // [ENCAPSULATION] the caller's jobs are copied (new Job(j)), so an algorithm can never change the user's data.
    // [POLYMORPHISM]  execute() is abstract: this one call runs a different algorithm for every subclass.
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
        return new ScheduleResult(getDescription(), getSummary(), jobs, timeline);
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

// [INHERITANCE] SelectionScheduler extends Scheduler and adds the shared 'pick the lowest key' loop.
// [ABSTRACTION] key() and isPreemptive() are abstract - the loop does not know which rule it is running.
// Shared loop for FCFS, SJF, SRTF, Priority and HRRN; subclasses only supply key() and isPreemptive().
// The job with the LOWEST key is chosen; key() may depend on the current time (HRRN does).
abstract class SelectionScheduler extends Scheduler {
    protected SelectionScheduler(String name, String code) {
        super(name, code);
    }

    protected abstract double key(Job job, int time);

    protected abstract boolean isPreemptive();

    @Override
    protected void execute(List<Job> jobs, List<Segment> timeline) {
        // HOW THE SCHEDULE IS BUILT (FCFS / SJF / SRTF / Priority / HRRN)
        //  1. 'time' is the clock, starting at 0.
        //  2. Look at every unfinished job that has already arrived (arrival <= time) and pick the one with the
        //     LOWEST key (ties: earlier arrival). FCFS key = arrival, SJF = burst, SRTF = remaining,
        //     Priority = priority number, HRRN = -(wait + burst) / burst.
        //  3. If nobody has arrived yet, the CPU is idle until the next arrival (an 'idle' Gantt block).
        //  4. Run the chosen job: to the end if non-preemptive, or only until the next arrival if preemptive
        //     (then step 2 is repeated, so a shorter / more important newcomer can take over).
        //  5. Add the block to the Gantt chart, move the clock forward, repeat until every job is finished.
        int time = 0;
        int left = jobs.size();

        while (left > 0) {
            Job current = null;
            for (Job j : jobs) {
                if (j.isFinished() || j.getArrivalTime() > time) {
                    continue;
                }
                if (current == null) {
                    current = j;
                    continue;
                }
                int cmp = Double.compare(key(j, time), key(current, time));
                if (cmp < 0 || (cmp == 0 && j.getArrivalTime() < current.getArrivalTime())) {
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

// [INHERITANCE]  FcfsScheduler extends SelectionScheduler: it reuses the whole loop and only supplies its rule.
// [POLYMORPHISM] it overrides key(), isPreemptive() and getSummary(): same method names, different behaviour.
// First Come First Serve: earliest arrival runs first and is never interrupted.
class FcfsScheduler extends SelectionScheduler {
    public FcfsScheduler() {
        super("First Come First Serve", "FCFS");
    }

    @Override
    public String getSummary() {
        return "Runs jobs in arrival order and never interrupts them.";
    }

    @Override
    protected double key(Job job, int time) {
        return job.getArrivalTime();
    }

    @Override
    protected boolean isPreemptive() {
        return false;
    }
}

// [INHERITANCE + POLYMORPHISM] extends SelectionScheduler; overrides key() to compare burst times.
// Shortest Job First: the ready job with the smallest burst runs next.
class SjfScheduler extends SelectionScheduler {
    public SjfScheduler() {
        super("Shortest Job First (non-preemptive)", "SJF");
    }

    @Override
    public String getSummary() {
        return "Picks the ready job with the smallest burst; runs it to the end.";
    }

    @Override
    protected double key(Job job, int time) {
        return job.getBurstTime();
    }

    @Override
    protected boolean isPreemptive() {
        return false;
    }
}

// [INHERITANCE + POLYMORPHISM] extends SelectionScheduler; key() = remaining time and isPreemptive() = true.
// Shortest Remaining Time First: SJF that re-decides whenever a job arrives.
class SrtfScheduler extends SelectionScheduler {
    public SrtfScheduler() {
        super("Shortest Remaining Time First", "SRTF");
    }

    @Override
    public String getSummary() {
        return "Preemptive SJF: always runs the job with the least time left.";
    }

    @Override
    protected double key(Job job, int time) {
        return job.getRemainingTime();
    }

    @Override
    protected boolean isPreemptive() {
        return true;
    }
}

// [INHERITANCE + POLYMORPHISM] extends SelectionScheduler; one class, two behaviours chosen by the constructor flag.
// Priority scheduling, lower number = higher priority; the constructor flag selects preemptive.
class PriorityScheduler extends SelectionScheduler {
    private final boolean preemptive;

    public PriorityScheduler(boolean preemptive) {
        super(preemptive ? "Priority (preemptive)" : "Priority (non-preemptive)",
              preemptive ? "PRIO-P" : "PRIO-NP");
        this.preemptive = preemptive;
    }

    @Override
    public String getSummary() {
        return preemptive
                ? "Lowest priority number first; a more important arrival takes over the CPU."
                : "Lowest priority number first; a running job is never interrupted.";
    }

    @Override
    protected double key(Job job, int time) {
        return job.getPriority();
    }

    @Override
    protected boolean isPreemptive() {
        return preemptive;
    }
}

// [INHERITANCE + POLYMORPHISM] extends SelectionScheduler; a brand-new algorithm needed only key() (no new loop).
// Worked example at time 10: a job with AT=2, BT=4 has waited 10 - 2 = 8, so ratio = (8 + 4) / 4 = 3.0.
//                            a job with AT=9, BT=2 has waited 1,         so ratio = (1 + 2) / 2 = 1.5  -> the first one runs.
// Highest Response Ratio Next: ratio = (waiting + burst) / burst. Long waits raise the ratio, so no starvation.
class HrrnScheduler extends SelectionScheduler {
    public HrrnScheduler() {
        super("Highest Response Ratio Next", "HRRN");
    }

    @Override
    public String getSummary() {
        return "Picks the highest (wait + burst) / burst, so waiting jobs age upward.";
    }

    @Override
    protected double key(Job job, int time) {
        double waiting = Math.max(0, time - job.getArrivalTime());
        return -((waiting + job.getBurstTime()) / job.getBurstTime());   // negated: lowest key wins
    }

    @Override
    protected boolean isPreemptive() {
        return false;
    }
}

// [INHERITANCE]  extends Scheduler directly (it does not use the 'lowest key' loop).
// [POLYMORPHISM] overrides execute() and getDescription() - the description includes the current quantum.
// Round Robin: jobs take turns for at most one quantum; arrivals queue before the interrupted job.
class RoundRobinScheduler extends Scheduler {
    private int quantum;

    public RoundRobinScheduler(int quantum) {
        super("Round Robin", "RR");
        setQuantum(quantum);
    }

    @Override
    public String getSummary() {
        return "Each job runs for one time quantum, then waits at the back of the queue.";
    }

    public int getQuantum() {
        return quantum;
    }

    // [ENCAPSULATION] quantum is private; this setter is the only way to change it and it rejects values below 1.
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
        // HOW ROUND ROBIN WORKS: jobs wait in a FIFO queue. The job at the front runs for min(quantum, remaining).
        // Jobs that arrive while it runs join the queue BEFORE it; if it is not finished it goes to the back.
        // Example (quantum 2): P1 needs 5 -> runs 0-2, goes to the back, runs again later, and so on.
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

// ───────────────────────────── Step-by-step working ─────────────────────────────

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

// ───────────────────────────── Model (encapsulation) ─────────────────────────────

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

// ───────────────────────────── Live simulation ─────────────────────────────

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