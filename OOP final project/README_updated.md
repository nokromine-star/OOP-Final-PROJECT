# CPU Scheduling Simulator

A Java-based, console application that simulates and compares common CPU
scheduling algorithms. The project helps students understand how
operating systems allocate CPU time by showing scheduling timelines,
Gantt charts, performance metrics, and step-by-step execution.

> **Course:** OOP 101 -- Object-Oriented Programming\
> **Institution:** Batangas State University -- The National Engineering
> University, Alangilan Campus\
> **Academic Year:** 1st Semester, AY 2026--2027\
> **Repository:**
> [OOP-Final-PROJECT](https://github.com/nokromine-star/OOP-Final-PROJECT)

## Project Team

| No. | Member Name | Assigned Role |
|---:|---|---|---|---|
| 1 | BAJA, JOSH EMMANUEL M.  | **Lead Programmer** |
| 2 | ARASULA, CYRIL KENCHI B.|  **Team Leader** |
| 3 | ADAME, JOHN FLOYDGERALD H. |  **Documentation Leader** |
| 4 | MENDOZA, RONALD ANDREI D. |  **System / Design Lead** |

> Replace the placeholders with the members' actual details. Arrange names alphabetically by last name, as requested in the project proposal.

------------------------------------------------------------------------

## Table of Contents

-   [Project Overview](#project-overview)
-   [Objectives](#objectives)
-   [Features](#features)
-   [Scheduling Algorithms](#scheduling-algorithms)
-   [Performance Metrics](#performance-metrics)
-   [How It Works](#how-it-works)
-   [Object-Oriented Programming](#object-oriented-programming)
-   [Proposed Class Design](#proposed-class-design)
-   [Requirements](#requirements)
-   [Getting Started](#getting-started)
-   [Example Process Input](#example-process-input)
-   [Project Limitations](#project-limitations)
-   [Team](#team)
-   [Development Schedule](#development-schedule)

------------------------------------------------------------------------

## Project Overview

CPU scheduling is an essential operating-system function. It determines
which process receives CPU time and when. Different scheduling
algorithms can produce different waiting times, turnaround times,
response times, and CPU utilization---even when given the same group of
processes.

This project is a **console-based Java CPU Scheduling Simulator**. Users
can create or generate a workload, run scheduling algorithms, inspect
Gantt charts and timing results, and watch a schedule execute one time
unit at a time. A live race feature is also proposed to compare all
supported algorithms on the same clock.

The simulator is intended as a learning and demonstration tool for
students, instructors, and self-learners studying operating systems.

## Objectives

### General Objective

To develop a Java-based application that simulates and compares FCFS,
SJF, SRTF, Priority, and Round Robin CPU scheduling, displaying Gantt
charts, performance metrics, and a live step-by-step simulation in a
console interface.

### Specific Objectives

-   Allow users to create, remove, load, and randomly generate processes
    with validated arrival time, burst time, and priority.
-   Implement FCFS, SJF, SRTF, Priority (non-preemptive and preemptive),
    and Round Robin with an adjustable time quantum.
-   Display colored Gantt charts, idle time, and per-process completion,
    turnaround, waiting, and response times.
-   Calculate average waiting, turnaround, and response times, CPU
    utilization, and throughput.
-   Compare scheduling algorithms using the same workload.
-   Provide a live simulation and a live race that runs all algorithms
    together on the same clock.
-   Apply object-oriented programming concepts throughout the
    application.

## Features

  -----------------------------------------------------------------------
  Feature                             Description
  ----------------------------------- -----------------------------------
  Process Management                  Add and remove processes, clear the
                                      process list, load sample data, and
                                      generate random workloads.

  Input Validation                    Validate process arrival time,
                                      burst time, priority, and Round
                                      Robin quantum.

  Multiple Algorithms                 Run FCFS, SJF, SRTF, Priority, and
                                      Round Robin scheduling.

  Gantt Chart                         Display a colored scheduling
                                      timeline, including CPU idle
                                      periods.

  Timing Table                        Show completion, turnaround,
                                      waiting, and response times for
                                      each process.

  Performance Summary                 Calculate averages, CPU
                                      utilization, and throughput.

  Algorithm Comparison                Run algorithms against the same
                                      workload and compare their results.

  Live Simulation                     Replay one scheduling result one
                                      time unit at a time.

  Live Race                           Display all algorithms progressing
                                      side by side on a shared clock.
  -----------------------------------------------------------------------

## Scheduling Algorithms

The simulator is designed to support the following algorithms:

  -----------------------------------------------------------------------
  Algorithm               Scheduling Type         Description
  ----------------------- ----------------------- -----------------------
  First Come, First       Non-preemptive          Executes processes in
  Served (FCFS)                                   arrival order.

  Shortest Job First      Non-preemptive          Selects the available
  (SJF)                                           process with the
                                                  shortest burst time.

  Shortest Remaining Time Preemptive              Selects the available
  First (SRTF)                                    process with the
                                                  shortest remaining
                                                  execution time.

  Priority Scheduling     Preemptive and          Selects a process
                          non-preemptive          according to its
                                                  priority.

  Round Robin (RR)        Preemptive              Gives each ready
                                                  process a time slice
                                                  based on the configured
                                                  time quantum.
  -----------------------------------------------------------------------

All algorithms are intended to run against the same workload so their
results can be compared fairly.

## Performance Metrics

The simulator reports the following scheduling measurements:

  -----------------------------------------------------------------------
  Metric                              Meaning
  ----------------------------------- -----------------------------------
  Completion Time (CT)                Time at which a process finishes
                                      execution.

  Turnaround Time (TAT)               Total time from process arrival
                                      until completion.

  Waiting Time (WT)                   Total time a process spends waiting
                                      in the ready queue.

  Response Time (RT)                  Time from process arrival until its
                                      first CPU execution.

  Average Waiting Time                Sum of process waiting times
                                      divided by the number of processes.

  Average Turnaround Time             Sum of process turnaround times
                                      divided by the number of processes.

  Average Response Time               Sum of process response times
                                      divided by the number of processes.

  CPU Utilization                     Percentage of the elapsed schedule
                                      time during which the CPU is busy.

  Throughput                          Number of completed processes per
                                      unit of elapsed time.
  -----------------------------------------------------------------------

Common formulas used to interpret the results:

``` text
Turnaround Time = Completion Time - Arrival Time

Waiting Time = Turnaround Time - Burst Time

Response Time = First Start Time - Arrival Time

Average Waiting Time = Total Waiting Time / Number of Processes

Average Turnaround Time = Total Turnaround Time / Number of Processes

Average Response Time = Total Response Time / Number of Processes

CPU Utilization = (CPU Busy Time / Total Elapsed Time) × 100%

Throughput = Number of Completed Processes / Total Elapsed Time
```

*The formulas describe the reported metrics; the simulator's
implementation is the source of the actual calculated results.*

## How It Works

The planned program flow is:

1.  Start the application.
2.  Open the main menu.
3.  Select an operation, such as adding a process, running an algorithm,
    comparing algorithms, starting a live simulation, or starting a live
    race.
4.  Enter the required process details and scheduling settings.
5.  Validate the input.
6.  Run the selected scheduling algorithm using a copy of the process
    data.
7.  Display the Gantt chart, timing table, comparison, or live frames.
8.  Return to the main menu or exit.

## Object-Oriented Programming

The project applies the following OOP concepts:

  -----------------------------------------------------------------------
  OOP Concept                         Application in the Project
  ----------------------------------- -----------------------------------
  Classes and Objects                 `Job`, `Segment`, `ScheduleResult`,
                                      and scheduler classes represent
                                      processes, Gantt chart blocks,
                                      results, and algorithms.

  Methods                             Focused methods perform scheduling,
                                      process execution, input handling,
                                      and result calculations.

  Encapsulation                       Model fields are kept private and
                                      accessed through appropriate
                                      methods. Scheduling operates on
                                      copies of jobs.

  Inheritance                         Specific schedulers inherit shared
                                      scheduling behavior from abstract
                                      scheduler classes.

  Polymorphism                        Algorithms are handled through the
                                      common `Scheduler` type and can
                                      provide their own scheduling
                                      behavior.

  Abstraction                         Abstract scheduler classes define
                                      common contracts while leaving
                                      algorithm-specific behavior to
                                      subclasses.

  Exception Handling                  Invalid process data, invalid
                                      quantum values, empty process
                                      lists, and invalid menu input are
                                      handled through exceptions.

  Arrays and Data Handling            Arrays, `ArrayList`, `ArrayDeque`,
                                      and `Comparator` support workload
                                      management, ready queues,
                                      timelines, and sorting.
  -----------------------------------------------------------------------

## Proposed Class Design

The following classes are identified in the project proposal. The
implementation may change as development progresses.

  -----------------------------------------------------------------------
  Class                               Responsibility
  ----------------------------------- -----------------------------------
  `ConsoleApp`                        Handles the menu, user input,
                                      process management, and connection
                                      between the model and views.

  `Job`                               Represents a process and its
                                      scheduling state.

  `Segment`                           Represents one process or idle-time
                                      block in the Gantt chart.

  `ScheduleResult`                    Stores a completed schedule,
                                      process results, timeline, and
                                      summary metrics.

  `Scheduler`                         Abstract base class defining the
                                      common scheduling contract.

  `SelectionScheduler`                Abstract class containing shared
                                      selection-based scheduling logic.

  `FcfsScheduler`                     Implements First Come, First Served
                                      scheduling.

  `SjfScheduler`                      Implements non-preemptive Shortest
                                      Job First scheduling.

  `SrtfScheduler`                     Implements Shortest Remaining Time
                                      First scheduling.

  `PriorityScheduler`                 Implements priority scheduling,
                                      including preemptive behavior.

  `RoundRobinScheduler`               Implements Round Robin scheduling
                                      using a ready queue and time
                                      quantum.

  `ConsoleView`                       Formats console output, tables,
                                      Gantt charts, and comparisons.

  `LiveSimulation`                    Handles the single-algorithm live
                                      replay and all-algorithm live race.

  `Terminal`                          Provides terminal formatting,
                                      colors, alignment, and
                                      screen-clearing utilities.

  `InvalidJobException`               Represents invalid process data.

  `SampleData`                        Provides the sample process
                                      workload.
  -----------------------------------------------------------------------

## Requirements

-   Java Development Kit (JDK)
-   A terminal or command prompt
-   A Java-compatible IDE or text editor (optional)

> The project proposal does not specify a minimum JDK version. Use the
> version supported by the actual source code.

## Getting Started

### 1. Clone the repository

``` bash
git clone https://github.com/nokromine-star/OOP-Final-PROJECT.git
```

### 2. Open the project

``` bash
cd OOP-Final-PROJECT
```

Open the project folder in your preferred Java IDE, or inspect the
source files from your terminal.

### 3. Compile and run

The exact compile and run commands depend on the final source layout,
package declarations, and application entry-point class. Once those are
confirmed, add the project's exact commands here.

For an IDE, open the project, locate the class containing the `main`
method, and run that class.

## Example Process Input

A workload is made up of processes with arrival time, burst time, and
priority.

  Process     Arrival Time   Burst Time   Priority
  --------- -------------- ------------ ----------
  P1                     0            5          2
  P2                     1            3          1
  P3                     2            8          3
  P4                     3            2          2

*Illustrative input only. These values are not a required default
workload.*

For Round Robin, the user also configures a time quantum.

## Repository Structure

The GitHub repository should follow the required project organization:

```text
OOP-Final-PROJECT/
│
├── src/
│   ├── ConsoleApp.java
│   ├── Job.java
│   ├── Segment.java
│   ├── ScheduleResult.java
│   ├── Scheduler.java
│   ├── SelectionScheduler.java
│   ├── FcfsScheduler.java
│   ├── SjfScheduler.java
│   ├── SrtfScheduler.java
│   ├── PriorityScheduler.java
│   ├── RoundRobinScheduler.java
│   ├── ConsoleView.java
│   ├── LiveSimulation.java
│   ├── Terminal.java
│   ├── InvalidJobException.java
│   └── SampleData.java
│
├── docs/
│   ├── Project-Proposal.pdf
│   └── UML-Diagram.png
│
├── screenshots/
│   ├── main-menu.png
│   ├── gantt-chart.png
│   └── algorithm-comparison.png
│
└── README.md
```

- `src/` — Java source code for the CPU scheduling simulator.
- `docs/` — Project proposal, UML/class diagram, and supporting documentation.
- `screenshots/` — Images showing the application and its features.
- `README.md` — Project description, features, instructions, and documentation.

> This is a suggested structure based on the project proposal. Keep only source files and screenshots that actually exist in your project, and adjust filenames to match your implementation. Do not create empty placeholder Java files just to match this example.

## Project Limitations

According to the project proposal, the following are outside the
project's scope:

-   No graphical user interface; the application runs in the console.
-   No saving or loading workloads from a file or database; processes
    are kept in memory.
-   No I/O bursts, multiple CPU cores, multilevel queues, or priority
    aging.
-   No context-switch overhead; switching between processes takes no
    time in the simulation.
-   No real operating-system processes; all processes are simulated from
    user-entered values limited to `0–10,000`.

## Team

**Team Name:** Group 2  
**Team Number:** To be provided  
**Section:** To be provided

The four members and their assigned roles are listed in the **Project Team** section at the beginning of this README.

### Official Member Information and Signatures

Add the image containing the four members' names, SR-Codes, and signatures to the repository (for example, inside the `docs` folder). Then update the image path below if your filename is different.

<p align="center">
  <img src="docs/team-signatures.jpg" alt="Group 2 member information and signatures" width="750">
</p>

<!-- Replace docs/team-signatures.jpg with the exact path and filename of your uploaded image. -->

The project proposal requests that member names be listed alphabetically by last name.

## Development Schedule

  Week         Activity                         Expected Output
  ------------ -------------------------------- ------------------------
  Week 1       Planning and requirements        Project proposal
  Week 2       System and class design          Class/UML diagram
  Week 3       Initial coding                   Basic classes
  Weeks 4--5   Feature development              Working features
  Week 6       OOP implementation               Completed OOP features
  Week 7       Testing and debugging            Tested application
  Week 8       Documentation and presentation   Final project

*The schedule may be adjusted based on the course calendar.*

------------------------------------------------------------------------

## Repository Contents

The GitHub repository is intended to contain:

-   Java source code
-   `README.md` documentation
-   UML/class diagram
-   Application screenshots
-   Other required project files

## Acknowledgment

Developed as a final project for **OOP 101 -- Object-Oriented
Programming**, Computer Science Department, College of Informatics and
Computing Sciences, Batangas State University -- The National
Engineering University, Alangilan Campus.

------------------------------------------------------------------------

```{=html}
<p align="center">
```
`<strong>`{=html}CPU Scheduling Simulator`</strong>`{=html}`<br>`{=html}
OOP 101 -- Final Project \| Group 2
```{=html}
</p>
```
