
# queuectl-shell (Spring Shell)

A Spring Shell CLI background job queue that supports enqueueing jobs, multiple workers, exponential backoff retries, a Dead Letter Queue (DLQ), persistent storage (JSON files), and runtime configuration.

---

## 1) Setup Instructions

### Prerequisites
- Java 17+
- Maven 3.9+

### Build & Run
```bash
mvn -q -DskipTests package
java -jar target/queuectl-shell-0.1.0.jar
```
This launches the **interactive** shell with the prompt:
```
queuectl>
```

To quit: `exit` or `quit`.

---

## 2) Usage Examples

### Configure defaults
```
queuectl> config set max_retries 3
queuectl> config set backoff_base 2
queuectl> config set job_timeout_sec 15
```

### Enqueue jobs
```
queuectl> enqueue {"id":"ok1","command":"echo Hello"}
queuectl> enqueue {"id":"bad1","command":"no_such_cmd_zzz"}
```

### Start/stop workers
```
queuectl> worker start 2
# wait a few seconds while jobs execute
queuectl> worker stop
```

### Status and listing
```
queuectl> status
queuectl> list
queuectl> list completed
queuectl> list failed
```

### Dead Letter Queue
```
queuectl> dlq list
queuectl> dlq retry bad1
```

> Non-interactive examples can be scripted by starting the shell and feeding a script file via `:script`, see **Testing** below.

---

## 3) Architecture Overview

**Job lifecycle**
```
pending -> processing -> (completed | failed)
failed --(retry with backoff)--> pending (when run_at is due)
failed (exhausted retries) -> dead (moved to DLQ)
```

**Persistence**
- JSON files under `.queuectl/`:
  - `jobs.json`: active jobs and their state
  - `dlq.json`: permanently failed jobs
  - `config.json`: `max_retries`, `backoff_base`, `job_timeout_sec`, `heartbeat_sec`

**Worker logic**
- N worker threads started via `worker start <count>`
- Each loop:
  1) Claim one *due* job (pending or failed with `run_at <= now`), mark `processing`
  2) Execute the shell command with a timeout
  3) On success: `completed`
  4) On failure: `attempts += 1` and either
     - schedule retry with `run_at = now + backoff_base^attempts` and `state=failed`, or
     - move to DLQ (`dead`) if attempts reached `max_retries`

**Concurrency**
- A JVM-wide lock serializes JSON read/writes to avoid corruption.
- For true multi-process workers, switch to H2/SQLite with row locking (out of scope for this simple file-backed version).

---

## 4) Assumptions & Trade-offs

- **File-backed JSON** to keep the setup dependency-free. Easiest to run and inspect, but not ideal for multi-process concurrency.
- **Worker threads inside one JVM** for simplicity. Horizontal scaling would need a DB.
- **Backoff cap** at 24 hours to prevent runaway delays.
- **Command execution** uses the platform shell (`/bin/sh -lc` on Unix, `cmd.exe /c` on Windows).
- **Minimal logging**: only error tail is kept in `last_error`. You can extend with an executions log table/file if needed.

---

## 5) Testing Instructions

A simple demo script is included to validate the main flows.

### Option A: Run the interactive script
Create a file `script.txt`:
```
config set max_retries 2
config set backoff_base 2
config set job_timeout_sec 5
enqueue {"id":"ok1","command":"echo HI"}
enqueue {"id":"bad1","command":"no_such_cmd_zzz"}
worker start 2
status
list
dlq list
worker stop
```

Start the shell, then run:
```
queuectl> :script script.txt
```

### Option B: Use the provided shell script
Run the helper:
```bash
bash scripts/demo.sh
```

It will:
- build the project,
- launch the shell,
- feed a command script,
- and print final status and DLQ.

---

## Commands (Help Text)

- `enqueue {json}` — add a job, e.g. `enqueue {"id":"j1","command":"echo hi"}`
- `worker start <count>` — start N worker threads
- `worker stop` — stop workers gracefully
- `status` — show counts per state
- `list [state]` — list jobs (optionally filter by state)
- `dlq list` — show DLQ
- `dlq retry <id>` — requeue a DLQ job
- `config get <key>` — show a config value
- `config set <key> <value>` — update config

---

## Deliverables Mapping

- **Working CLI (`queuectl`)** — Spring Shell interactive CLI.
- **Persistent storage** — JSON files under `.queuectl/`.
- **Multiple workers** — `worker start <count>` uses a fixed thread pool.
- **Retry + exponential backoff** — `delay = backoff_base ^ attempts`.
- **DLQ** — `dlq.json` + `dlq list` + `dlq retry`.
- **Config management** — `config get/set`.
- **Clean CLI** — annotated commands with help text.
- **README** — this document.
- **Separation of concerns** — `model/`, `service/`, `storage/`, `shell/`, `util/`.
- **Testing/script** — `scripts/demo.sh` and the `:script` flow.

---

## Notes

- If you need a non-interactive wrapper (single-shot command), we can add a thin `CommandLineRunner` bridge that executes one command and exits.
- For production-grade concurrency across processes or machines, switch persistence to a DB (H2/SQLite/Postgres) and use row-level locking with `SKIP LOCKED`.


### Use H2 (file) persistence
```bash
mvn -q -DskipTests package
java -Dspring.profiles.active=h2 -jar target/queuectl-shell-0.1.0.jar
```

### Use SQLite persistence
```bash
mvn -q -DskipTests package
java -Dspring.profiles.active=sqlite -jar target/queuectl-shell-0.1.0.jar
```
Data will be created under `.queuectl/queue.db`.
