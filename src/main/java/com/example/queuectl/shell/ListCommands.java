package com.example.queuectl.shell;

import com.example.queuectl.model.Job;
import com.example.queuectl.service.JobService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.List;

@ShellComponent
public class ListCommands {

    private final JobService jobService;

    public ListCommands(JobService jobService) {
        this.jobService = jobService;
    }

    @ShellMethod(key = "list", value = "List jobs. Example: list pending | processing | completed | failed | dead")
    public String list(String state) {

        // If state is 'dead' → show DLQ jobs instead of active queue
        List<Job> rows = (state == null || state.isBlank())
                ? jobService.list(null)                     // default: list all active jobs
                : jobService.listIncludingDlq(state);       // new behavior includes DLQ when state=dead

        if (rows.isEmpty()) {
            return "(no jobs)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-16s %-11s %-8s %-8s %-22s %s%n",
                "id", "state", "attempts", "max", "run_at", "command"));

        for (Job j : rows) {
            String st = (j.state == null) ? "dead" : j.state.name(); // DLQ entries appear as dead
            sb.append(String.format("%-16s %-11s %-8d %-8d %-22s %s%n",
                    j.id, st, j.attempts, j.maxRetries,
                    j.runAt == null ? "-" : j.runAt.toString(),
                    j.command));
        }

        return sb.toString();
    }
}
