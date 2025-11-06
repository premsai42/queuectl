package com.example.queuectl.shell;

import com.example.queuectl.model.JobState;
import com.example.queuectl.service.JobService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.EnumMap;
import java.util.Map;

@ShellComponent
public class StatusCommands {

    private final JobService jobService;

    public StatusCommands(JobService jobService) {
        this.jobService = jobService;
    }


    @ShellMethod(key = "status", value = "Show job counts by state.")
    public String status() {
        Map<JobState, Long> c = new EnumMap<>(JobState.class);
        c.putAll(jobService.counts());

        long deadFromJobs = c.getOrDefault(JobState.dead, 0L);
        long deadFromDlq  = jobService.dlqCount();
        long deadTotal    = deadFromJobs + deadFromDlq;

        StringBuilder sb = new StringBuilder();
        for (JobState s : JobState.values()) {
            long n = c.getOrDefault(s, 0L);
            if (s == JobState.dead) {
                // print total including DLQ and show where it comes from
                sb.append(String.format("%-11s : %d (DLQ=%d)%n", "dead", deadTotal, deadFromDlq));
            } else {
                sb.append(String.format("%-11s : %d%n", s.name(), n));
            }
        }
        return sb.toString();
    }


}
