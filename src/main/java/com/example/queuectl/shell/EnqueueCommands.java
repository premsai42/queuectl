package com.example.queuectl.shell;

import com.example.queuectl.model.Job;
import com.example.queuectl.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class EnqueueCommands {

    private final JobService jobService;
    private final ObjectMapper om = com.example.queuectl.util.Jsons.mapper();

    public EnqueueCommands(JobService jobService) {
        this.jobService = jobService;
    }

    @ShellMethod(key = "enqueue", value = "Add a new job. Example: enqueue {\"id\":\"job1\",\"command\":\"echo hi\"}")
    public String enqueue(String jobJson) {
        try {
            Job j = om.readValue(jobJson, Job.class);
            if (j.id == null || j.id.isBlank()) return "id is required";
            if (j.command == null || j.command.isBlank()) return "command is required";
            jobService.enqueue(j);
            return "Enqueued " + j.id;
        } catch (Exception e) {
            return "Invalid JSON: " + e.getMessage();
        }
    }
}
