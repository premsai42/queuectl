# 🧵 **queuectl-shell — Background Job Queue (Spring Shell)**

A **Spring Shell–based CLI** application that manages background job execution with features like multiple workers, exponential retry backoff, a Dead Letter Queue (DLQ), persistent storage, and runtime configuration.

---

## ⚙️ **1. Setup Instructions**

### ✅ **Prerequisites**
Make sure you have:
- **Java 17+**
- **Maven 3.9+**
- (Optional) MySQL or SQLite if you switch to DB persistence

### ⚡ **Run Locally**
Clone and build the project:
```bash
git clone https://github.com/<your-username>/queuectl-shell.git
cd queuectl-shell
mvn clean package -DskipTests
```

Run the CLI:
```bash
java -jar target/queuectl-shell-0.1.0.jar
```

You’ll enter an interactive shell:
```
queuectl>
```

Exit with:
```
queuectl> exit
```

By default, the app uses **JSON file storage** under `.queuectl/`.  
To use a database, switch profiles (see Optional section below).

---

## 💻 **2. Usage Examples**

### ⚙️ Configure Job Parameters
```
queuectl> config set max_retries 3
queuectl> config set backoff_base 2
queuectl> config set job_timeout_sec 10
```

### 🧾 Enqueue Jobs
```
queuectl> enqueue {"id":"job1","command":"echo Hello World"}
queuectl> enqueue {"id":"job2","command":"sleep 3"}
queuectl> enqueue {"id":"job3","command":"invalid_cmd"}
```

### 🏃 Start and Stop Workers
```
queuectl> worker start 3
# workers begin executing jobs
queuectl> worker stop
```

### 📊 Monitor Status
```
queuectl> status
```
Example Output:
```
pending: 0
processing: 0
completed: 2
failed: 1
dead: 0
workers_active: 3
```

### 🧩 View and Retry DLQ
```
queuectl> dlq list
queuectl> dlq retry job3
```

---

## 🧱 **3. Architecture Overview**

### 🔄 **Job Lifecycle**
| State | Description |
|--------|--------------|
| `pending` | Waiting to be processed |
| `processing` | Currently running |
| `completed` | Finished successfully |
| `failed` | Failed but retryable |
| `dead` | Moved to DLQ after retries exhausted |

### 💾 **Persistence**
The system saves data under `.queuectl/`:
- `jobs.json` → active jobs and states  
- `dlq.json` → permanently failed jobs  
- `config.json` → retry & timeout configuration

Each operation synchronizes access to avoid corruption.

### 🧵 **Worker Design**
- Multiple threads handle concurrent jobs.
- Each worker:
  1. Picks one pending job
  2. Executes command (`cmd.exe /c` on Windows or `/bin/sh -lc` on Linux)
  3. Updates state to *completed* or *failed*
  4. On failure, applies **exponential backoff** delay:
     ```
     next_run = current_time + backoff_base ^ attempts
     ```
  5. Moves job to **DLQ** after max retries.

### 🧠 **Key Components**
| Layer | Responsibility |
|--------|----------------|
| `model/` | Job structure, states |
| `storage/` | Persistence layer (JSON, DB) |
| `service/` | Core business logic |
| `shell/` | CLI command handlers |
| `util/` | Helper utilities |

---

## ⚖️ **4. Assumptions & Trade-offs**

| Decision | Rationale |
|-----------|------------|
| **JSON-based persistence** | Keeps setup simple and file-based. Easy to inspect and portable. |
| **Single-JVM concurrency** | Avoids distributed locks. Can be extended to DB later. |
| **Thread-level workers** | Simpler than managing multiple processes. |
| **Exponential backoff capped** | Prevents runaway retries. |
| **Platform shell commands** | Executes through system shell for cross-platform support. |

**Trade-offs:**
- Not horizontally scalable (one instance only).
- JSON I/O may limit high throughput.
- Lacks advanced metrics/log aggregation (simplified for assignment scope).

---

## 🧪 **5. Testing Instructions**

### ✅ **Option 1 — Interactive Script**
Create a `script.txt` file:
```
config set max_retries 2
config set backoff_base 2
config set job_timeout_sec 5
enqueue {"id":"ok1","command":"echo HI"}
enqueue {"id":"bad1","command":"no_such_cmd"}
worker start 2
status
list
dlq list
worker stop
```

Run the script:
```
queuectl> :script script.txt
```

### ✅ **Option 2 — Shell Script Automation**
Run:
```bash
bash scripts/demo.sh
```
This will:
1. Build the project  
2. Launch the shell  
3. Execute demo commands  
4. Print final status and DLQ summary

---

## 🧩 **Deliverables Mapping**

| Requirement | Implementation |
|--------------|----------------|
| CLI interface (`queuectl`) | Spring Shell interactive commands |
| Persistent job storage | JSON files under `.queuectl/` |
| Multiple workers | Thread pool controlled by `worker start` |
| Retry & backoff | `delay = base ^ attempts` |
| Dead Letter Queue | `dlq.json` + `dlq retry` command |
| Config management | `config get/set` |
| README.md | This document |
| Modular structure | Separate layers: model, service, storage, shell |
| Testing validation | Interactive script & demo.sh |

---

## 🧰 **Optional Profiles**

You can switch to DB-backed persistence.

### H2 Database
```bash
java -Dspring.profiles.active=h2 -jar target/queuectl-shell-0.1.0.jar
```

### SQLite
```bash
java -Dspring.profiles.active=sqlite -jar target/queuectl-shell-0.1.0.jar
```

### MySQL
Edit `application.properties`:
```
spring.datasource.url=jdbc:mysql://localhost:3306/queuectl
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```
Then run:
```bash
java -jar target/queuectl-shell-0.1.0.jar
```

---

## 🗄️ **Database Schema (MySQL)**

If you are using MySQL persistence, create the following database and tables before running the app:

```sql
CREATE DATABASE queuectl;

USE queuectl;

CREATE TABLE jobs (
    id VARCHAR(255) PRIMARY KEY,
    command TEXT NOT NULL,
    state VARCHAR(50),
    attempts INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    run_at DATETIME,
    last_error TEXT
);

CREATE TABLE dlq (
    id VARCHAR(255) PRIMARY KEY,
    command TEXT NOT NULL,
    attempts INT DEFAULT 0,
    last_error TEXT
);

CREATE TABLE config (
    key_name VARCHAR(100) PRIMARY KEY,
    value_text VARCHAR(255)
);
```

---

## 👤 **Author**
Developed by **Konatham Prem Sai**  
as part of the **Flam Internship Backend Developer Assignment (2025)**.  
This project demonstrates:
- Command-line interface development  
- Background job orchestration  
- Concurrency and retry logic  
- Persistent state management  
- Clean, modular Spring Boot + Spring Shell design
