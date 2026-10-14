#!/bin/bash
# ============================================================
# compile.sh — Compiles all Java source files for Παραδοτέο Α
# ============================================================
set -e

SRC_DIR="src"
OUT_DIR="out"

echo "=== Compiling Online Gaming System ==="
mkdir -p "$OUT_DIR"

# Copy config.properties to output directory (all JVM processes read it from CWD)
cp config.properties "$OUT_DIR/"

javac -encoding UTF-8 -d "$OUT_DIR" \
    "$SRC_DIR/ConfigLoader.java" \
    "$SRC_DIR/Game.java" \
    "$SRC_DIR/BetRecord.java" \
    "$SRC_DIR/GameBuffer.java" \
    "$SRC_DIR/ProducerThread.java" \
    "$SRC_DIR/SRGClientHandler.java" \
    "$SRC_DIR/SRGServer.java" \
    "$SRC_DIR/Worker.java" \
    "$SRC_DIR/WorkerRequestHandler.java" \
    "$SRC_DIR/ReduceJobManager.java" \
    "$SRC_DIR/WorkerResultHandler.java" \
    "$SRC_DIR/Reducer.java" \
    "$SRC_DIR/PendingResultsMap.java" \
    "$SRC_DIR/ReducerCallbackServer.java" \
    "$SRC_DIR/ClientHandler.java" \
    "$SRC_DIR/Master.java" \
    "$SRC_DIR/ManagerApp.java" \
    "$SRC_DIR/DummyPlayerApp.java"

echo ""
echo "=== Compilation successful! ==="
echo "Output: $OUT_DIR/"
echo ""
echo "Next step: run ./run_all.sh"
