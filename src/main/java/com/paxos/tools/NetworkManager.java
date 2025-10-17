package com.paxos.tools;

import com.paxos.Paxos;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class NetworkManager {
    private final String memberId;
    private final int port;

    private ServerSocket serverSocket;
    private boolean running = false;
    private final Paxos messageHandler;
    private final ProfileManager profileManager;
    private final Map<String, InetSocketAddress> memberAddresses = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public NetworkManager(String memberId, ProfileManager.MemberProfile profile, String configPath, Paxos messageHandler) {
        this.memberId = memberId;
        this.port = readConfig(memberId, configPath);
        if (this.port == -1) throw new RuntimeException("Error: Config File Not found!");
        this.profileManager = new ProfileManager(profile);
        this.messageHandler = messageHandler;
    }

    /**
     * get the cluster size
     *
     * @return the number of members in the cluster
     */
    public int getClusterSize() {
        return memberAddresses.size();
    }

    /**
     * Starts the server - allows incoming messages
     *
     * @throws IOException if the server cannot be started e.g. port in use etc.
     */
    public void startServer() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        executor.submit(this::acceptLoop);
        Logger.log("NetworkManager for " + memberId + " listening on port " + port);
    }

    /**
     * the loop that handles incoming connections and
     * sens them to the thread pool to be actioned
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Logger.log(memberId + " accepted connection from " + client.getRemoteSocketAddress());
                executor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running) {
                    Logger.log("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles a connection - extracts the message and passes to the message handler
     *
     * @param client the connecting client
     */
    private void handleClient(Socket client) {
        try (client; BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    Message msg = Message.fromJson(line);
                    messageHandler.onMessage(msg);
                }
            } catch (IOException e) {
                Logger.log("Error reading client: " + e.getMessage());
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Closes the port - stops the server
     */
    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        executor.shutdownNow();
    }

    /**
     * Send a message to a member using their memberId
     *
     * @param targetMemberId the member to send the message to
     * @param msg the message to send
     */
    public void sendMessage(String targetMemberId, Message msg) {
        InetSocketAddress addr = memberAddresses.get(targetMemberId);
        if (addr == null) {
            Logger.log("Unknown memberId: " + targetMemberId);
            return;
        }
        executor.execute(() -> sendMessageToAddress(addr, msg));
    }

    /**
     * sends a message to an address
     *
     * @param addr address to send to
     * @param msg the message to send
     */
    private void sendMessageToAddress(InetSocketAddress addr, Message msg) {
        // do send delay
        profileManager.simulateDelay();

        try (Socket socket = new Socket(addr.getHostString(), addr.getPort());
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            out.write(msg.toString());
            out.newLine();
            out.flush();

            Logger.log("Sent " + msg.getType() + " from " + memberId + " to " + addr.getHostString() + ":" + addr.getPort());

        } catch (IOException e) {
            Logger.log("Failed to send message to " + addr + ": " + e.getMessage());
        }
    }

    /**
     * broadcast a message to all members
     *
     * @param msg the message to be broadcast
     */
    public void broadcast(Message msg) {
        for (String targetId : memberAddresses.keySet()) {
            if (!targetId.equals(memberId)) {
                Logger.log("Broadcasting " + msg.getType() + " from " + memberId + " to " + targetId);
                sendMessage(targetId, msg);
            }
        }
    }

    /**
     * Reads config file building member address map and returning the port to use
     *
     * @param memberId id of current paxos member
     * @param configPath path to config file
     * @return the port to use or -1 if no port was found / error occurred
     */
    private int readConfig(String memberId, String configPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // format: {name} {uri} {port}
                String[] parts = line.split("\\s+");
                if (parts.length != 3) continue;

                String id = parts[0].trim();
                String host = parts[1].trim();
                int memberPort = Integer.parseInt(parts[2].trim());

                memberAddresses.put(id, new InetSocketAddress(host, memberPort));
            }

            Logger.log("Loaded " + memberAddresses.size() + " members from config.");
            for (Map.Entry<String, InetSocketAddress> e : memberAddresses.entrySet()) {
                Logger.log("  " + e.getKey() + " -> " + e.getValue());
            }
        } catch (IOException e) {
            Logger.log("Failed to read config: " + e.getMessage());
            return -1;
        }

        return memberAddresses.containsKey(memberId) ?
                memberAddresses.get(memberId).getPort() :
                -1;
    }
}
