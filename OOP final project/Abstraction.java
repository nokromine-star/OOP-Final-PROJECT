import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/*
 * ABSTRACTION
 *   Interfaces and abstract classes: they say WHAT something can do and hide HOW.
 */

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

// [ENUM] A fixed set of menu sections. Every MenuItem belongs to exactly one.
enum MenuGroup {
    WORKLOAD("Workload"), SIMULATE("Simulate"), SYSTEM("System");

    private final String title;

    MenuGroup(String title) {
        this.title = title;
    }

    String getTitle() {
        return title;
    }
}

// [ABSTRACTION]   abstract class: perform() has no body here, every concrete menu entry must supply it.
// [ENCAPSULATION] key, label, summary and group are private final and read through getters.
// Base class of every menu entry. invoke() is the template method; subclasses supply perform().
abstract class MenuItem implements Describable {
    private final String key;
    private final String label;
    private final String summary;
    private final MenuGroup group;

    protected MenuItem(String key, String label, String summary, MenuGroup group) {
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

    public MenuGroup getGroup() { return group; }

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
