package com.example.queuectl.service;

import com.example.queuectl.model.Job;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WorkerService {

    private final JobService jobService;
    private final CommandRunner runner;

    private ExecutorService pool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Future<?>> futures = new CopyOnWriteArrayList<>();

    public WorkerService(JobService jobService, CommandRunner runner) {
        this.jobService = jobService;
        this.runner = runner;
    }

    public synchronized String start(int count) {
        if (running.get()) return "Workers already running.";
        running.set(true);
        pool = Executors.newFixedThreadPool(count);
        Map<String,Object> cfg = jobService.config();
        int heartbeat = Integer.parseInt(cfg.getOrDefault("heartbeat_sec", 5).toString());
        int timeout = Integer.parseInt(cfg.getOrDefault("job_timeout_sec", 60).toString());
        int base = Integer.parseInt(cfg.getOrDefault("backoff_base", 2).toString());

        for (int i = 0; i < count; i++) {
            String workerId = "w-" + UUID.randomUUID().toString().substring(0,8);
            Future<?> f = pool.submit(() -> loop(workerId, heartbeat, timeout, base));
            futures.add(f);
        }
        return "Started " + count + " worker(s).";
    }

    private void loop(String workerId, int heartbeatSec, int timeoutSec, int base) {
        long lastBeat = 0;
        while (running.get()) {
            long now = System.currentTimeMillis();
            if (now - lastBeat > heartbeatSec * 1000L) {
                lastBeat = now;
            }
            Optional<Job> claim = jobService.claimNext(workerId);
            if (claim.isEmpty()) {
                sleepQuiet(300);
                continue;
            }
            Job job = claim.get();
            CommandRunner.Result r = runner.run(job.command, timeoutSec);
            if (r.exitCode == 0) {
                jobService.complete(job.id);
            } else {
                String tail = r.output == null ? ("exit=" + r.exitCode) :
                        ("exit=" + r.exitCode + "\n" + (r.output.length() > 1500 ? r.output.substring(r.output.length()-1500) : r.output));
                jobService.fail(job.id, base, tail);
            }
        }
    }

    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public synchronized String stop() {
        if (!running.get()) return "No workers running.";
        running.set(false);
        if (pool != null) {
            pool.shutdown();
            try { pool.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            pool.shutdownNow();
        }
        futures.clear();
        return "Stopped workers.";
    }

    public boolean isRunning() { return running.get(); }
}
