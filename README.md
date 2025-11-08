# 🧵 queuectl-shell — Background Job Queue (Spring Shell)

A **Spring Shell–based CLI application** that manages background job execution with features like multiple workers, exponential retry backoff, a **Dead Letter Queue (DLQ)**, persistent **MySQL storage**, and runtime configuration.
🎥 **Demo Recording:** [Watch CLI Demo on Google Drive](https://drive.google.com/file/d/1eKA21Db51q4LuGqVInxY69SUF7KSGNrH/view?usp=sharing)


---

## ⚙️ 1. Setup Instructions

### ✅ Prerequisites
Ensure you have installed:
- **Java 17+**
- **Maven 3.9+**
- **MySQL Server** running locally

---

### ⚡ Build & Run

#### 1️⃣ Clone and build the project
```bash
git clone https://github.com/<your-username>/queuectl-shell.git
cd queuectl-shell
mvn clean package -DskipTests
```

#### 2️⃣ Configure MySQL connection  
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/queuectl
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.main.banner-mode=off
spring.shell.history.name=.queuectl-history
```

#### 3️⃣ Create database and tables  
Run the following SQL in MySQL:
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

#### 4️⃣ Run the CLI
```bash
java -jar target/queuectl-shell-0.1.0.jar
```

You’ll enter the interactive shell:
```
queuectl>
```

To exit:
```
queuectl> exit
```

---

## 💻 2. Usage Examples

### ⚙️ Configure Job Parameters
```bash
queuectl> config set max_retries 3
queuectl> config set backoff_base 2
queuectl> config set job_timeout_sec 10
```

### 🧾 Enqueue Jobs
(Always wrap JSON in single quotes)
```bash
queuectl> enqueue '{"id":"job1","command":"echo Hello World"}'
queuectl> enqueue '{"id":"job2","command":"sleep 3"}'
queuectl> enqueue '{"id":"job3","command":"invalid_cmd"}'
```

### 🏃 Start and Stop Workers
```bash
queuectl> worker start 3
# jobs execute in background
queuectl> worker stop
```

### 📊 Monitor Status
```bash
queuectl> status
```
Example output:
```
pending: 0
processing: 0
completed: 2
failed: 1
dead: 0
workers_active: 3
```

### 🧩 View and Retry DLQ
```bash
queuectl> dlq list
queuectl> dlq retry job3
```

---

## 🧱 3. Architecture Overview

### 🔄 Job Lifecycle
| State | Description |
|--------|--------------|
| `pending` | Waiting to be processed |
| `processing` | Currently running |
| `completed` | Successfully executed |
| `failed` | Failed, but will retry later |
| `dead` | Moved to DLQ after max retries |

---

### 💾 Persistence
All data is stored in **MySQL**:
- `jobs` — Active jobs and their states  
- `dlq` — Permanently failed jobs  
- `config` — Application runtime configuration  

Transactions ensure consistency and safety for concurrent workers.

---

### 🧵 Worker Logic
Each worker thread:
1. Selects one pending job  
2. Executes the command (`cmd.exe /c` on Windows or `/bin/sh -lc` on Linux)  
3. On success → marks as `completed`  
4. On failure → schedules retry using exponential backoff:  
   ```
   next_run = now + (backoff_base ^ attempts)
   ```
5. Moves to DLQ after exceeding `max_retries`

---

### 🧠 System Layers
| Layer | Responsibility |
|--------|----------------|
| **model/** | Job structure, states, and enums |
| **service/** | Job execution, retry logic, and DLQ management |
| **storage/** | MySQL persistence layer |
| **shell/** | CLI command definitions |
| **util/** | Helper utilities |

---

## ⚖️ 4. Assumptions & Trade-offs

| Decision | Reason |
|-----------|--------|
| MySQL persistence | Provides durable, concurrent-safe storage |
| Thread-based workers | Simple concurrency within JVM |
| Exponential backoff | Simulates production-grade retry logic |
| Platform shell commands | Ensures cross-platform job execution |
| Minimal logging | Focused on tracking state and retries |

**Trade-offs:**
- Single-node processing (no distributed worker scaling)
- Requires MySQL setup before running
- Job output is minimal for simplicity

---

## 🧪 5. Testing Instructions

### ✅ Option 1 — Interactive Test Script
Create a file named `test-script.txt`:
```bash
config set max_retries 2
config set backoff_base 2
config set job_timeout_sec 5
enqueue '{"id":"ok1","command":"echo Hello"}'
enqueue '{"id":"bad1","command":"no_such_command"}'
worker start 2
sleep 5
status
dlq list
worker stop
```

Run it inside queuectl:
```
queuectl> script test-script.txt
```

---

### ✅ Option 2 — Automated Bash Script
Create a file `scripts/test.sh`:
```bash
#!/bin/bash
echo "=== Building queuectl-shell ==="
mvn -q -DskipTests package

echo "=== Starting test run ==="
cat > test-script.txt <<'EOF'
config set max_retries 2
config set backoff_base 2
config set job_timeout_sec 5
enqueue '{"id":"ok1","command":"echo Hello from worker"}'
enqueue '{"id":"bad1","command":"no_such_command_zzz"}'
worker start 2
sleep 5
status
dlq list
worker stop
EOF

cat test-script.txt | java -jar target/queuectl-shell-0.1.0.jar
echo "=== Test run complete ==="
```

Run:
```bash
bash scripts/test.sh
```

To save logs:
```bash
bash scripts/test.sh > test-run.log 2>&1
```

---

## 🎬 Demo Recording

A recorded demo of the working CLI has been uploaded to Google Drive.  
You can view/download the recording here:

https://drive.google.com/file/d/1eKA21Db51q4LuGqVInxY69SUF7KSGNrH/view?usp=sharing

> Tip: Replace this link with your own drive link if you re-record the demo. Make sure link sharing is enabled (Anyone with the link can view).

---

## 🗄️ Database Verification
After testing, verify job and DLQ tables:
```sql
SELECT id, state, attempts FROM jobs;
SELECT * FROM dlq;
```

---

## 👤 Author
**Developed by:**  
**Konatham Prem Sai**  
Flam Backend Internship Assignment – 2025  

This project demonstrates:
- Spring Shell CLI development  
- Background job orchestration  
- Worker-based concurrency  
- Exponential retry logic  
- MySQL persistence with DLQ handling
