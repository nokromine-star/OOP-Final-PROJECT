import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
public class Main {
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
