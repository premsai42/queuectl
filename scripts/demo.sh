
#!/usr/bin/env bash
set -euo pipefail

# Build
mvn -q -DskipTests package

# Prepare a command script
cat > /tmp/queuectl_cmds.txt <<'EOF'
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
EOF

# Run interactive shell and feed the script via stdin using expect-like here-doc
java -jar target/queuectl-shell-0.1.0.jar <<'CMDS'
:script /tmp/queuectl_cmds.txt
exit
CMDS

echo "Demo finished. Check .queuectl/ files for persisted data."
