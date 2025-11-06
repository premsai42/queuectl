package com.example.queuectl.shell;

import com.example.queuectl.service.WorkerService;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class WorkerCommands {

    private final WorkerService workerService;

    public WorkerCommands(WorkerService workerService) {
        this.workerService = workerService;
    }

    @ShellMethod(key = "worker start", value = "Start workers. Example: worker start 3")
    public String start(int count) {
        return workerService.start(Math.max(1, count));
    }

    @ShellMethod(key = "worker stop", value = "Stop workers gracefully.")
    public String stop() {
        return workerService.stop();
    }

    public Availability startAvailability() {
        return workerService.isRunning() ? Availability.unavailable("workers already running") : Availability.available();
    }

    public Availability stopAvailability() {
        return workerService.isRunning() ? Availability.available() : Availability.unavailable("no workers running");
    }
}
