#!/bin/bash
# kill_paxos_nodes.sh
# Kill all Paxos node processes based on cluster config

CONFIG_FILE="cluster.conf"

if [[ ! -f $CONFIG_FILE ]]; then
    echo "Config file '$CONFIG_FILE' not found!"
    exit 1
fi

# Read the ports from the config file
while read -r line; do
    # Skip empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue

    NODE_PORT=$(echo "$line" | awk '{print $3}')
    if [[ -n "$NODE_PORT" ]]; then
        # Find Java processes listening on this port
        PIDS=$(lsof -ti tcp:"$NODE_PORT")
        if [[ -n "$PIDS" ]]; then
            echo "Killing node on port $NODE_PORT (PID $PIDS)"
            kill -9 $PIDS
        fi
    fi
done < "$CONFIG_FILE"

