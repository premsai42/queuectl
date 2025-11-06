
CREATE TABLE IF NOT EXISTS jobs (
  id           VARCHAR(100) PRIMARY KEY,
  command      VARCHAR(4000) NOT NULL,
  state        VARCHAR(20)   NOT NULL,
  attempts     INT           NOT NULL,
  max_retries  INT           NOT NULL,
  created_at   TIMESTAMP     NOT NULL,
  updated_at   TIMESTAMP     NOT NULL,
  run_at       TIMESTAMP,
  priority     INT           DEFAULT 0,
  last_error   VARCHAR(4000),
  worker_id    VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS dlq_jobs (
  id           VARCHAR(100) PRIMARY KEY,
  command      VARCHAR(4000) NOT NULL,
  attempts     INT           NOT NULL,
  max_retries  INT           NOT NULL,
  failed_at    TIMESTAMP     NOT NULL,
  last_error   VARCHAR(4000)
);

CREATE TABLE IF NOT EXISTS config (
  key    VARCHAR(100) PRIMARY KEY,
  value  VARCHAR(200) NOT NULL
);
