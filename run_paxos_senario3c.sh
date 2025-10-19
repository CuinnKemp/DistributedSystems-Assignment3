#!/bin/bash

# Setup: Launch the members with a mix of profiles: M1 (reliable), M2 (latent), M3 (failure), M4-M9 (standard).
# Test: A FAILING member (M3) initiates a proposal and immediately crashes.
# Expected Outcome: the remaining operational members must successfully reach consensus on a single winner.

TEST_NAME="scenario3c"
echo "running ${TEST_NAME}"

JAR_PATH="target/paxos.jar"
CONFIG_FILE="cluster.conf"
BASE_PORT=9001
NODE_COUNT=9
LOG_DIR="./logs/${TEST_NAME}"
M3_Value="M3"
WAIT_TIME=15 # longer max wait to ensure completion
CHECK_INTERVAL=0.1

PROFILES=("RELIABLE" "LATENT" "FAILING" "RELIABLE" "RELIABLE" "RELIABLE" "RELIABLE" "RELIABLE" "RELIABLE")

mkdir -p "$LOG_DIR"
PIDS=()

# Start nodes
for i in $(seq 1 $NODE_COUNT); do
    NODE="M$i"
    PORT=$((BASE_PORT + i - 1))
    LOG_FILE="$LOG_DIR/${NODE}.log"
    PROFILE=${PROFILES[$((i - 1))]}
    java -jar "$JAR_PATH" "$NODE" "--configPath" "$CONFIG_FILE"  "--profile" "$PROFILE"> "$LOG_FILE" 2>&1 &
    PIDS+=($!)
done

# Wait for nodes to be ready
sleep 1

echo "{\"type\":\"VALUE\",\"proposalValue\":\"$M3_Value\"}" | nc -w 1 localhost 9003

# Wait for consensus
START_TIME=$(date +%s)
declare -A LEARN_TIMES
declare -A LEARN_VALUES

# Continuously check logs
while true; do
    ALL_LEARNED=true
    for i in $(seq 1 $NODE_COUNT); do
        NODE="M$i"
        LOG_FILE="$LOG_DIR/${NODE}.log"
        if [[ "$NODE" == "M3" ]]; then
            continue
        fi
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
    echo "All nodes agreed on value: ${VALUES[0]} except M3 (as expected)"
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

