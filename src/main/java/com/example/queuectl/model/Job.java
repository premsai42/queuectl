package com.example.queuectl.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Job {
    public String id;
    public String command;
    public JobState state = JobState.pending;
    public int attempts = 0;
    @JsonProperty("max_retries")
    public int maxRetries = 3;
    @JsonProperty("created_at")
    public Instant createdAt = Instant.now();
    @JsonProperty("updated_at")
    public Instant updatedAt = Instant.now();
    @JsonProperty("run_at")
    public Instant runAt;
    public int priority = 0;
    @JsonProperty("last_error")
    public String lastError;
    @JsonProperty("worker_id")
    public String workerId;

    public Job() {}
    public Job(String id, String command) {
        this.id = id;
        this.command = command;
    }
}
