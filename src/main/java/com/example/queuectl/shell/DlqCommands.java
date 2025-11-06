package com.example.queuectl.shell;

import com.example.queuectl.model.Job;
import com.example.queuectl.service.JobService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.List;

@ShellComponent
public class DlqCommands {

    private final JobService jobService;

    public DlqCommands(JobService jobService) {
        this.jobService = jobService;
    }

    @ShellMethod(key = "dlq list", value = "List jobs in the Dead Letter Queue.")
    public String list() {
        List<Job> rows = jobService.listDlq();
        if (rows.isEmpty()) return "(DLQ empty)";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-16s %-8s %-8s %-22s %s%n", "id", "attempts", "max", "failed_at", "last_error"));
        for (Job j : rows) {
            String err = j.lastError == null ? "" : (j.lastError.length() > 80 ? j.lastError.substring(0, 80) + "…" : j.lastError);
            sb.append(String.format("%-16s %-8d %-8d %-22s %s%n",
                    j.id, j.attempts, j.maxRetries,
                    j.updatedAt == null ? "-" : j.updatedAt.toString(),
                    err));
        }
        return sb.toString();
    }

    @ShellMethod(key = "dlq retry", value = "Retry a job from DLQ by id.")
    public String retry(String id) {
        boolean ok = jobService.dlqRetry(id);
        return ok ? ("Requeued " + id) : ("No DLQ job with id " + id);
    }
}
