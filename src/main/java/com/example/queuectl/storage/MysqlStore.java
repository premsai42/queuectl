package com.example.queuectl.storage;

import com.example.queuectl.model.Job;
import com.example.queuectl.model.JobState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Component  // ✅ Makes this a Spring-managed bean
public class MysqlStore implements StorePort {

    private final JdbcTemplate jdbc;

    public MysqlStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Job> mapper = (ResultSet rs, int rowNum) -> {
        Job j = new Job();
        j.id = rs.getString("id");
        j.command = rs.getString("command");
        j.state = JobState.valueOf(rs.getString("state"));
        j.attempts = rs.getInt("attempts");
        j.maxRetries = rs.getInt("max_retries");
        j.createdAt = rs.getTimestamp("created_at").toInstant();
        j.updatedAt = rs.getTimestamp("updated_at").toInstant();
        var runAt = rs.getTimestamp("run_at");
        j.runAt = (runAt != null ? runAt.toInstant() : null);
        j.priority = rs.getInt("priority");
        j.lastError = rs.getString("last_error");
        j.workerId = rs.getString("worker_id");
        return j;
    };

    @Override
    public Map<String, Object> loadConfig() {
        Map<String, Object> config = new HashMap<>();
        jdbc.query("SELECT `key`, `value` FROM config", rs -> {
            config.put(rs.getString("key"), rs.getString("value"));
        });
        return config;
    }

    @Override
    public void saveConfig(Map<String, Object> cfg) {
        for (var e : cfg.entrySet()) {
            jdbc.update("INSERT INTO config(`key`, `value`) VALUES(?, ?) ON DUPLICATE KEY UPDATE value=?",
                    e.getKey(), e.getValue(), e.getValue());
        }
    }

    @Override
    public List<Job> loadJobs() {
        return jdbc.query("SELECT * FROM jobs", mapper);
    }

    @Override
    public void saveJobs(List<Job> jobs) {
        jdbc.update("DELETE FROM jobs");
        for (Job j : jobs) {
            jdbc.update("INSERT INTO jobs(id, command, state, attempts, max_retries, created_at, updated_at, run_at, priority, last_error, worker_id) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    j.id, j.command, j.state.name(), j.attempts, j.maxRetries,
                    java.sql.Timestamp.from(j.createdAt),
                    java.sql.Timestamp.from(j.updatedAt),
                    j.runAt == null ? null : java.sql.Timestamp.from(j.runAt),
                    j.priority, j.lastError, j.workerId);
        }
    }

    @Override
    public List<Job> loadDlq() {
        return jdbc.query("SELECT * FROM dlq_jobs", (rs, i) -> {
            Job j = new Job();
            j.id = rs.getString("id");
            j.command = rs.getString("command");
            j.state = JobState.dead;
            j.attempts = rs.getInt("attempts");
            j.maxRetries = rs.getInt("max_retries");
            j.updatedAt = rs.getTimestamp("failed_at").toInstant();
            j.lastError = rs.getString("last_error");
            return j;
        });
    }

    @Override
    public void saveDlq(List<Job> jobs) {
        jdbc.update("DELETE FROM dlq_jobs");
        for (Job j : jobs) {
            jdbc.update("INSERT INTO dlq_jobs(id, command, attempts, max_retries, failed_at, last_error) VALUES(?,?,?,?,?,?)",
                    j.id, j.command, j.attempts, j.maxRetries, java.sql.Timestamp.from(Instant.now()), j.lastError);
        }
    }
}
