#!/bin/bash

# Setup: All 9 members are launched with the reliable profile (i.e., no artificial delay or failures).
# Test: Trigger a single proposal from one member (M1 with proposal value "M1")
# Expected Outcome: Consensus is reached quickly and correctly. All members should output that M1 was elected.

TEST_NAME="scenario1"
echo "running ${TEST_NAME}"

JAR_PATH="target/paxos.jar"
CONFIG_FILE="cluster.conf"
BASE_PORT=9001
NODE_COUNT=9
LOG_DIR="./logs/${TEST_NAME}"
M1_Value="M1"
WAIT_TIME=15

mkdir -p "$LOG_DIR"
PIDS=()

# Start nodes
for i in $(seq 1 $NODE_COUNT); do
    NODE="M$i"
    PORT=$((BASE_PORT + i - 1))
    LOG_FILE="$LOG_DIR/${NODE}.log"
    java -jar "$JAR_PATH" "$NODE" "--profile" "RELIABLE" > "$LOG_FILE" 2>&1 &
    PIDS+=($!)
done

# Wait for nodes to be ready
sleep 5

echo "{\"type\":\"VALUE\",\"proposalValue\":\"$M1_Value\"}" | nc -w 2 localhost 9001

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
