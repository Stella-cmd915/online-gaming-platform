#!/bin/bash
# ============================================================
# run_all.sh — Starts all system components in the right order
#
# Order:
#   1. SRG    (Secure Random Generator)   — port 5005
#   2. Workers 0,1,2                      — ports 5001,5002,5003
#   3. Reducer                            — port 5004
#   4. Master                             — port 5000 (callback: 5006)
#
# Each component runs in a separate terminal window (xterm).
# If xterm is unavailable, all components log to files in logs/
#
# Clients (ManagerApp, DummyPlayerApp) are started separately.
# ============================================================
set -e

OUT_DIR="out"
LOG_DIR="logs"
mkdir -p "$LOG_DIR"

CP="$OUT_DIR"

echo "=== Starting Online Gaming System ==="
echo ""

# Helper: open a new terminal or fall back to background with log
launch() {
    local TITLE="$1"
    local CMD="$2"
    local LOG="$LOG_DIR/$3.log"

    if command -v xterm &>/dev/null; then
        xterm -title "$TITLE" -e "bash -c \"$CMD 2>&1 | tee $LOG; read\"" &
    else
        echo "Starting $TITLE (log: $LOG)"
        bash -c "$CMD" > "$LOG" 2>&1 &
    fi
    # Small delay between launches so each service binds its port before the next starts
    sleep 1
}

# 1. SRG
launch "SRG - Random Generator" \
    "cd $OUT_DIR && java SRGServer" \
    "srg"

# 2. Workers (3 instances)
for i in 0 1 2; do
    launch "Worker $i" \
        "cd $OUT_DIR && java Worker $i" \
        "worker$i"
done

# 3. Reducer
launch "Reducer" \
    "cd $OUT_DIR && java Reducer" \
    "reducer"

# 4. Master
launch "Master" \
    "cd $OUT_DIR && java Master" \
    "master"

echo ""
echo "=== All components started ==="
echo ""
echo "To start the Manager console:"
echo "  cd out && java ManagerApp"
echo ""
echo "To start a Dummy Player:"
echo "  cd out && java DummyPlayerApp"
echo ""
echo "Logs are in: $LOG_DIR/"
