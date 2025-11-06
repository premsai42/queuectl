
package com.example.queuectl.storage;

import com.example.queuectl.model.Job;
import com.example.queuectl.model.JobState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Component
@Profile("h2")
public class H2Store implements StorePort {

    private final JdbcTemplate jdbc;

    public H2Store(JdbcTemplate jdbc, @Value("${queuectl.data.dir:.queuectl}") String dataDir) {
        this.jdbc = jdbc;
    }

    private RowMapper<Job> mapper = new RowMapper<Job>() {
        @Override public Job mapRow(ResultSet rs, int rowNum) throws SQLException {
            Job j = new Job();
            j.id = rs.getString("id");
            j.command = rs.getString("command");
            j.state = JobState.valueOf(rs.getString("state"));
            j.attempts = rs.getInt("attempts");
            j.maxRetries = rs.getInt("max_retries");
            j.createdAt = rs.getTimestamp("created_at").toInstant();
            j.updatedAt = rs.getTimestamp("updated_at").toInstant();
            var runAt = rs.getTimestamp("run_at");
            j.runAt = (runAt == null) ? null : runAt.toInstant();
            j.priority = rs.getInt("priority");
            j.lastError = rs.getString("last_error");
            j.workerId = rs.getString("worker_id");
            return j;
        }
    };

    @Override
    public Map<String, Object> loadConfig() {
        Map<String,Object> m = new LinkedHashMap<>();
        jdbc.query("SELECT key, value FROM config", rs -> {
            m.put(rs.getString("key"), parseNumeric(rs.getString("value")));
        });
        // seed defaults if missing
        seedDefault(m, "max_retries", 3);
        seedDefault(m, "backoff_base", 2);
        seedDefault(m, "job_timeout_sec", 60);
        seedDefault(m, "heartbeat_sec", 5);
        return m;
    }

    private Object parseNumeric(String v) {
        if (v == null) return null;
        try { return Integer.parseInt(v); } catch (NumberFormatException nfe) { return v; }
    }

    private void seedDefault(Map<String,Object> m, String k, Object v) {
        if (!m.containsKey(k)) {
            m.put(k, v);
            jdbc.update("MERGE INTO config(key,value) KEY(key) VALUES(?,?)", k, String.valueOf(v));
        }
    }

    @Override
    public void saveConfig(Map<String, Object> cfg) {
        for (var e : cfg.entrySet()) {
            jdbc.update("MERGE INTO config(key,value) KEY(key) VALUES(?,?)", e.getKey(), String.valueOf(e.getValue()));
        }
    }

    @Override
    public List<Job> loadJobs() {
        return jdbc.query("SELECT * FROM jobs ORDER BY created_at", mapper);
    }

    @Override
    public void saveJobs(List<Job> jobs) {
        // naive implementation: delete all and reinsert (fine for small demo)
        jdbc.update("DELETE FROM jobs");
        for (Job j : jobs) {
            jdbc.update("INSERT INTO jobs(id, command, state, attempts, max_retries, created_at, updated_at, run_at, priority, last_error, worker_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                j.id, j.command, j.state.name(), j.attempts, j.maxRetries,
                java.sql.Timestamp.from(j.createdAt),
                java.sql.Timestamp.from(j.updatedAt),
                (j.runAt == null ? null : java.sql.Timestamp.from(j.runAt)),
                j.priority, j.lastError, j.workerId);
        }
    }

    @Override
    public List<Job> loadDlq() {
        return jdbc.query("SELECT id, command, attempts, max_retries, failed_at as updated_at, last_error FROM dlq_jobs ORDER BY failed_at",
            (rs, n) -> {
                Job j = new Job();
                j.id = rs.getString("id");
                j.command = rs.getString("command");
                j.attempts = rs.getInt("attempts");
                j.maxRetries = rs.getInt("max_retries");
                j.updatedAt = rs.getTimestamp("updated_at").toInstant();
                j.lastError = rs.getString("last_error");
                j.state = JobState.dead;
                return j;
            });
    }

    @Override
    public void saveDlq(List<Job> jobs) {
        jdbc.update("DELETE FROM dlq_jobs");
        for (Job j : jobs) {
            jdbc.update("MERGE INTO dlq_jobs(id, command, attempts, max_retries, failed_at, last_error) KEY(id) VALUES(?,?,?,?,?,?)",
                j.id, j.command, j.attempts, j.maxRetries,
                java.sql.Timestamp.from(j.updatedAt == null ? Instant.now() : j.updatedAt),
                j.lastError);
        }
    }
}
