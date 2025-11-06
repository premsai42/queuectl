
CREATE TABLE IF NOT EXISTS jobs (
  id           TEXT PRIMARY KEY,
  command      TEXT NOT NULL,
  state        TEXT NOT NULL,
  attempts     INTEGER NOT NULL,
  max_retries  INTEGER NOT NULL,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL,
  run_at       TEXT,
  priority     INTEGER DEFAULT 0,
  last_error   TEXT,
  worker_id    TEXT
);

CREATE TABLE IF NOT EXISTS dlq_jobs (
  id           TEXT PRIMARY KEY,
  command      TEXT NOT NULL,
  attempts     INTEGER NOT NULL,
  max_retries  INTEGER NOT NULL,
  failed_at    TEXT NOT NULL,
  last_error   TEXT
);

CREATE TABLE IF NOT EXISTS config (
  key    TEXT PRIMARY KEY,
  value  TEXT NOT NULL
);
