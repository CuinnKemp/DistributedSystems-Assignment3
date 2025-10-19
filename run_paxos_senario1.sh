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
WAIT_TIME=10
CHECK_INTERVAL=0.1

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
sleep 1

echo "{\"type\":\"VALUE\",\"proposalValue\":\"$M1_Value\"}" | nc -w 2 localhost 9001

START_TIME=$(date +%s)
declare -A LEARN_TIMES
declare -A LEARN_VALUES

# Continuously check logs
while true; do
    ALL_LEARNED=true
    for i in $(seq 1 $NODE_COUNT); do
        NODE="M$i"
        LOG_FILE="$LOG_DIR/${NODE}.log"
        if [[ ! -v LEARN_VALUES["$NODE"] ]]; then
            CUR_LEARNED=$(grep "CONSENSUS:" "$LOG_FILE" | tail -n 1 | awk -F'CONSENSUS: ' '{print $2}')
            if [[ -n "$CUR_LEARNED" ]]; then
                LEARN_VALUES["$NODE"]="$CUR_LEARNED"
                LEARN_TIMES["$NODE"]=$(($(date +%s) - START_TIME))
            else
                ALL_LEARNED=false
            fi
        fi
    done

    if $ALL_LEARNED; then
        break
    fi

    CURRENT_TIME=$(date +%s)
    if (( CURRENT_TIME - START_TIME > WAIT_TIME )); then
        echo "Not all nodes learned a value"
        break
    fi

    sleep $CHECK_INTERVAL
done

# Summarize results
VALUES=($(printf "%s\n" "${LEARN_VALUES[@]}" | sort -u))
if [[ ${#VALUES[@]} -eq 1 ]]; then
    echo "All nodes agreed on value: ${VALUES[0]}"
else
    echo "Not All nodes agreed on a value"
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
