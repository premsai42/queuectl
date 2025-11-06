package com.example.queuectl.service;

import com.example.queuectl.model.Job;
import com.example.queuectl.model.JobState;
import com.example.queuectl.storage.StorePort;
import com.example.queuectl.util.Clock;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final StorePort store;
    private final Clock clock = new Clock();
    private final ReentrantLock opLock = new ReentrantLock(true);

    public JobService(StorePort store) {
        this.store = store;
    }

    public Map<String,Object> config() { return store.loadConfig(); }

    public void setConfig(String key, String value) {
        Map<String,Object> cfg = store.loadConfig();
        if (value.matches("-?\\d+")) {
            cfg.put(key, Integer.parseInt(value));
        } else {
            cfg.put(key, value);
        }
        store.saveConfig(cfg);
    }

    public Job enqueue(Job j) {
        opLock.lock();
        try {
            List<Job> jobs = store.loadJobs();
            boolean exists = jobs.stream().anyMatch(x -> x.id.equals(j.id));
            if (exists) throw new IllegalArgumentException("Job id already exists: " + j.id);
            Instant now = clock.now();
            j.state = JobState.pending;
            j.createdAt = now;
            j.updatedAt = now;
            jobs.add(j);
            store.saveJobs(jobs);
            return j;
        } finally {
            opLock.unlock();
        }
    }

    public List<Job> list(String stateFilter) {
        List<Job> jobs = store.loadJobs();
        if (stateFilter == null || stateFilter.isBlank()) return jobs;
        JobState s = JobState.valueOf(stateFilter);
        return jobs.stream().filter(j -> j.state == s).collect(Collectors.toList());
    }

    public Map<JobState, Long> counts() {
        return store.loadJobs().stream()
                .collect(Collectors.groupingBy(j -> j.state, Collectors.counting()));
    }

    public List<Job> listDlq() { return store.loadDlq(); }

    public boolean dlqRetry(String id) {
        opLock.lock();
        try {
            List<Job> dlq = store.loadDlq();
            Optional<Job> opt = dlq.stream().filter(j -> j.id.equals(id)).findFirst();
            if (opt.isEmpty()) return false;
            Job j = opt.get();
            dlq.remove(j);
            store.saveDlq(dlq);

            List<Job> jobs = store.loadJobs();
            j.state = JobState.pending;
            j.attempts = 0;
            j.runAt = null;
            j.lastError = null;
            j.updatedAt = clock.now();
            jobs.add(j);
            store.saveJobs(jobs);
            return true;
        } finally {
            opLock.unlock();
        }
    }
    public long dlqCount() {
        return listDlq().size();
    }

    // optional: list including DLQ when state == dead
    public List<Job> listIncludingDlq(String stateFilter) {
        if (stateFilter == null || stateFilter.isBlank()) {
            return list(null);
        }
        if ("dead".equalsIgnoreCase(stateFilter)) {
            return listDlq(); // show DLQ items for 'dead'
        }
        return list(stateFilter);
    }
    public Optional<Job> claimNext(String workerId) {
        opLock.lock();
        try {
            Instant now = clock.now();
            List<Job> jobs = store.loadJobs();
            Optional<Job> next = jobs.stream()
                    .filter(j -> j.state == JobState.pending ||
                                 (j.state == JobState.failed && (j.runAt == null || !j.runAt.isAfter(now))))
                    .sorted(Comparator.<Job>comparingInt(j -> -j.priority)
                            .thenComparing(j -> j.createdAt))
                    .findFirst();

            if (next.isEmpty()) return Optional.empty();

            Job job = next.get();
            job.state = JobState.processing;
            job.workerId = workerId;
            job.updatedAt = now;
            store.saveJobs(jobs);
            return Optional.of(job);
        } finally {
            opLock.unlock();
        }
    }

    public void complete(String jobId) {
        opLock.lock();
        try {
            List<Job> jobs = store.loadJobs();
            jobs.stream().filter(j -> j.id.equals(jobId)).findFirst().ifPresent(j -> {
                j.state = JobState.completed;
                j.workerId = null;
                j.updatedAt = clock.now();
            });
            store.saveJobs(jobs);
        } finally {
            opLock.unlock();
        }
    }

    public void fail(String jobId, int base, String errorTail) {
        opLock.lock();
        try {
            Map<String,Object> cfg = store.loadConfig();
            int maxRetries = Integer.parseInt(cfg.getOrDefault("max_retries", 3).toString());

            List<Job> jobs = store.loadJobs();
            Optional<Job> opt = jobs.stream().filter(j -> j.id.equals(jobId)).findFirst();
            if (opt.isEmpty()) return;
            Job j = opt.get();

            int attempts = j.attempts + 1;
            j.attempts = attempts;
            j.lastError = errorTail;
            j.updatedAt = new Clock().now();

            if (attempts >= maxRetries) {
                j.state = JobState.dead;
                List<Job> dlq = store.loadDlq();
                dlq.add(j);
                store.saveDlq(dlq);
                jobs.remove(j);
                store.saveJobs(jobs);
            } else {
                long delay = new BackoffService().delaySeconds(base, attempts);
                j.runAt = new Clock().now().plusSeconds(delay);
                j.state = JobState.failed;
                j.workerId = null;
                store.saveJobs(jobs);
            }
        } finally {
            opLock.unlock();
        }

    }
}
