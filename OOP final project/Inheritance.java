import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;

/*
 * INHERITANCE
 *   Child classes reuse a parent with "extends":
 *   Scheduler -> SelectionScheduler -> Fcfs / Sjf / Srtf / Priority / Hrrn
 *   Scheduler -> RoundRobinScheduler
 *   MenuItem  -> WorkloadItem / SimulationItem / SystemItem -> concrete menu entries
 */

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

// [INHERITANCE] second level of the hierarchy: MenuItem -> WorkloadItem -> AddProcessItem, LoadSampleItem ...
// Entries that edit the process list.
abstract class WorkloadItem extends MenuItem {
    protected WorkloadItem(String key, String label, String summary) {
        super(key, label, summary, MenuGroup.WORKLOAD);
    }
}

// [INHERITANCE] SimulationItem overrides requiresJobs() and pauseAfter() once for every entry below it.
// Entries that run schedulers: they need processes and leave their output on screen until Enter.
abstract class SimulationItem extends MenuItem {
    protected SimulationItem(String key, String label, String summary) {
        super(key, label, summary, MenuGroup.SIMULATE);
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
        super(key, label, summary, MenuGroup.SYSTEM);
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

// Checked exception for invalid process data.
class InvalidJobException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidJobException(String message) {
        super(message);
    }
}
