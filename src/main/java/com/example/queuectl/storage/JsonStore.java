package com.example.queuectl.storage;

import com.example.queuectl.model.Job;
import com.example.queuectl.util.Jsons;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.annotation.Primary;
@Component


@Primary
public class JsonStore implements StorePort {

    private final Path dir;
    private final Path jobsFile;
    private final Path dlqFile;
    private final Path cfgFile;

    private final ObjectMapper om = Jsons.mapper();
    private final ReentrantLock lock = new ReentrantLock(true);

    public JsonStore(@Value("${queuectl.data.dir:.queuectl}") String dataDir) {
        this.dir = Paths.get(dataDir);
        this.jobsFile = dir.resolve("jobs.json");
        this.dlqFile = dir.resolve("dlq.json");
        this.cfgFile = dir.resolve("config.json");
        init();
    }

    private void init() {
        try {
            Files.createDirectories(dir);
            if (!Files.exists(jobsFile)) om.writeValue(jobsFile.toFile(), new ArrayList<Job>());
            if (!Files.exists(dlqFile))  om.writeValue(dlqFile.toFile(), new ArrayList<Job>());
            if (!Files.exists(cfgFile))  om.writeValue(cfgFile.toFile(), defaultConfig());
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize store", e);
        }
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("max_retries", 3);
        cfg.put("backoff_base", 2);
        cfg.put("job_timeout_sec", 60);
        cfg.put("heartbeat_sec", 5);
        return cfg;
    }

    public Map<String,Object> loadConfig() {
        lock.lock();
        try {
            return om.readValue(cfgFile.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public void saveConfig(Map<String,Object> cfg) {
        lock.lock();
        try {
            om.writeValue(cfgFile.toFile(), cfg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public List<Job> loadJobs() {
        lock.lock();
        try {
            return om.readValue(jobsFile.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public void saveJobs(List<Job> jobs) {
        lock.lock();
        try {
            om.writeValue(jobsFile.toFile(), jobs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public List<Job> loadDlq() {
        lock.lock();
        try {
            return om.readValue(dlqFile.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public void saveDlq(List<Job> jobs) {
        lock.lock();
        try {
            om.writeValue(dlqFile.toFile(), jobs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
