
package com.example.queuectl.storage;

import com.example.queuectl.model.Job;
import com.example.queuectl.model.JobState;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
@Profile("sqlite")
public class SqliteStore implements StorePort {

    private final JdbcTemplate jdbc;

    public SqliteStore(JdbcTemplate jdbc) {
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
            j.createdAt = Instant.parse(rs.getString("created_at"));
            j.updatedAt = Instant.parse(rs.getString("updated_at"));
            String runAt = rs.getString("run_at");
            j.runAt = (runAt == null) ? null : Instant.parse(runAt);
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
            jdbc.update("INSERT INTO config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", k, String.valueOf(v));
        }
    }

    @Override
    public void saveConfig(Map<String, Object> cfg) {
        for (var e : cfg.entrySet()) {
            jdbc.update("INSERT INTO config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                e.getKey(), String.valueOf(e.getValue()));
        }
    }

    @Override
    public List<Job> loadJobs() {
        return jdbc.query("SELECT * FROM jobs ORDER BY created_at", mapper);
    }

    @Override
    public void saveJobs(List<Job> jobs) {
        jdbc.update("DELETE FROM jobs");
        for (Job j : jobs) {
            jdbc.update("INSERT INTO jobs(id, command, state, attempts, max_retries, created_at, updated_at, run_at, priority, last_error, worker_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                j.id, j.command, j.state.name(), j.attempts, j.maxRetries,
                j.createdAt.toString(), j.updatedAt.toString(),
                j.runAt == null ? null : j.runAt.toString(),
                j.priority, j.lastError, j.workerId);
        }
    }

    @Override
    public List<Job> loadDlq() {
        return jdbc.query("SELECT id, command, attempts, max_retries, failed_at, last_error FROM dlq_jobs ORDER BY failed_at",
            (rs, n) -> {
                Job j = new Job();
                j.id = rs.getString("id");
                j.command = rs.getString("command");
                j.attempts = rs.getInt("attempts");
                j.maxRetries = rs.getInt("max_retries");
                String ts = rs.getString("failed_at");
                j.updatedAt = ts == null ? Instant.now() : Instant.parse(ts);
                j.lastError = rs.getString("last_error");
                j.state = JobState.dead;
                return j;
            });
    }

    @Override
    public void saveDlq(List<Job> jobs) {
        jdbc.update("DELETE FROM dlq_jobs");
        for (Job j : jobs) {
            jdbc.update("INSERT INTO dlq_jobs(id, command, attempts, max_retries, failed_at, last_error) VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET command=excluded.command, attempts=excluded.attempts, max_retries=excluded.max_retries, failed_at=excluded.failed_at, last_error=excluded.last_error",
                j.id, j.command, j.attempts, j.maxRetries,
                (j.updatedAt == null ? Instant.now().toString() : j.updatedAt.toString()),
                j.lastError);
        }
    }
}
