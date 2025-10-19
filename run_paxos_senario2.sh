#!/bin/bash

# Setup: All 9 members are launched with the reliable profile.
# Test: Two proposals are triggered at the same time from M1 with value "M1" and M2 with "M2"
# Expected Outcome: The Paxos algorithm correctly resolves the conflict, and all members reach a consensus on a single winner (either "M1" or "M2").

TEST_NAME="scenario2"
echo "running ${TEST_NAME}"

JAR_PATH="target/paxos.jar"
CONFIG_FILE="cluster.conf"
BASE_PORT=9001
NODE_COUNT=9
LOG_DIR="./logs/${TEST_NAME}"
M1_Value="M1"
M2_Value="M2"
WAIT_TIME=15

mkdir -p "$LOG_DIR"
PIDS=()

# Start nodes
for i in $(seq 1 $NODE_COUNT); do
    NODE="M$i"
    PORT=$((BASE_PORT + i - 1))
    LOG_FILE="$LOG_DIR/${NODE}.log"
    java -jar "$JAR_PATH" "$NODE" "--profile" "RELIABLE"> "$LOG_FILE" 2>&1 &
    PIDS+=($!)
done

# Wait for nodes to be ready
sleep 5

echo "{\"type\":\"VALUE\",\"proposalValue\":\"$M1_Value\"}" | nc -w 1 localhost 9001 & echo "{\"type\":\"VALUE\",\"proposalValue\":\"$M2_Value\"}" | nc -w 1 localhost 9002

# Wait for consensus
echo "Waiting $WAIT_TIME seconds for consensus..."
sleep $WAIT_TIME

# Fetch learned values
LEARNED=""  # Initialize empty string to store the consensus value
for i in $(seq 1 $NODE_COUNT); do
    NODE="M$i"
    LOG_FILE="$LOG_DIR/${NODE}.log"

    CUR_LEARNED=$(grep "CONSENSUS:" "$LOG_FILE" | tail -n 1 | awk -F'CONSENSUS: ' '{print $2}')

    if [[ -n "$CUR_LEARNED" ]]; then
        if [[ -z "$LEARNED" ]]; then
            LEARNED="$CUR_LEARNED"
        elif [[ "$LEARNED" != "$CUR_LEARNED" ]]; then
            echo "$NODE got different value: $CUR_LEARNED"
        fi
    else
        echo "$NODE did not learn any value"
    fi
done

if [[ -n "$LEARNED" ]]; then
    echo "CONSENSUS VALUE: $LEARNED"
else
    echo "No consensus value learned by any node"
fi

# Kill all nodes
for PID in "${PIDS[@]}"; do
    if kill -0 $PID 2>/dev/null; then
        kill $PID
    fi
done

# Give some time for graceful shutdown, then force kill if needed
sleep 3
for PID in "${PIDS[@]}"; do
    if kill -0 $PID 2>/dev/null; then
        kill -9 $PID
    fi
done
