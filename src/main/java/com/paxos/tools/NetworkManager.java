package com.paxos.tools;

import com.paxos.Paxos;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages PAXOS communication
 */
public class NetworkManager {
    private final String memberId;
    private final int port;

    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Paxos messageHandler;
    private final ProfileManager profileManager;

    private final Map<String, AtomicBoolean> memberAccessibility = new HashMap<>();
    private final Map<String, InetSocketAddress> memberAddresses = new HashMap<>();

    private static final int MAX_RETRIES = 4; // max connection retries
    private static final int BASE_TIMEOUT = 125; // 125ms base timeout

    public NetworkManager(String memberId, ProfileManager.MemberProfile profile, String configPath, Paxos messageHandler) {
        this.memberId = memberId;
        this.port = readConfig(memberId, configPath);
        if (this.port == -1) throw new RuntimeException("[NetworkManager] Error: Config File Not found!");
        this.profileManager = new ProfileManager(profile, getClusterSize());
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
        Logger.log("[startServer] NetworkManager for " + memberId + " listening on port " + port);
    }

    /**
     * the loop that handles incoming connections and
     * sens them to the thread pool to be actioned
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Logger.log("[acceptLoop]" + memberId + " accepted connection from " + client.getRemoteSocketAddress());
                executor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running) {
                    Logger.log("[acceptLoop] Error accepting connection: " + e.getMessage());
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
                    if (!line.contains("VALUE")  && profileManager.shouldFail()){
                        // simulate drop message (message not received)
                        Logger.log("[handleClient] Simulating dropped message");
                        continue;
                    }
                    Message msg = Message.fromJson(line);
                    if (msg.getSender() != null)
                        memberAccessibility.get(msg.getSender()).set(true);

                    // simulate send delay
                    profileManager.simulateDelay();
                    messageHandler.onMessage(msg);
                }
                in.close();
            } catch (IOException e) {
                Logger.log("[handleClient] Error reading client: " + e.getMessage());
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
        AtomicBoolean accessible = memberAccessibility.get(targetMemberId);
        InetSocketAddress addr = memberAddresses.get(targetMemberId);
        if (addr == null) {
            Logger.log("[sendMessage] Unknown memberId: " + targetMemberId);
            return;
        }
        if (!accessible.get()){
            Logger.log("[sendMessage] Inaccessible Target: " + targetMemberId + " not sending message");
            return;
        }
        executor.execute(() -> sendMessageToAddress(targetMemberId, addr, msg));
    }

    /**
     * sends a message to an address
     *
     * @param targetId the target the message is being sent to
     * @param addr address to send to
     * @param msg the message to send
     */
    private void sendMessageToAddress(String targetId, InetSocketAddress addr, Message msg) {
        // do send delay
        profileManager.simulateDelay();

        for (int i = 0; i < MAX_RETRIES; i++){
            try (Socket socket = new Socket(addr.getHostString(), addr.getPort());
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                out.write(msg.toString());
                out.newLine();
                out.flush();

                Logger.log("[sendMessageToAddress] Sent " + msg.getType() + " from " + memberId + " to " + addr.getHostString() + ":" + addr.getPort());

                if (profileManager.shouldCrash()){
                    Logger.log("[sendMessageToAddress] Simulating crash");
                    System.exit(0);
                }
                return;
            } catch (IOException e) {
                if (i == MAX_RETRIES - 1) {
                    // message couldn't be sent mark as inaccessible
                    memberAccessibility.get(targetId).set(false);
                    break;
                }
                int timeout = ThreadLocalRandom.current().nextInt((int) (BASE_TIMEOUT * Math.pow(2, i)));
                Logger.log("[sendMessageToAddress] Failed to send message to " + addr + " trying again after " + timeout + "ms");
                try {Thread.sleep(timeout);} catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * broadcast a message to all members
     *
     * @param msg the message to be broadcast
     */
    public void broadcast(Message msg) {
        for (String targetId : memberAddresses.keySet()) {
            if (!targetId.equals(memberId) && memberAccessibility.get(targetId).get()) {
                Logger.log("[broadcast] Broadcasting " + msg.getType() + " from " + memberId + " to " + targetId);
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

                // line format: {name} {uri} {port}
                String[] parts = line.split("\\s+");
                if (parts.length != 3) continue;

                String id = parts[0].trim();
                String host = parts[1].trim();
                int memberPort = Integer.parseInt(parts[2].trim());

                memberAddresses.put(id, new InetSocketAddress(host, memberPort));
                memberAccessibility.put(id, new AtomicBoolean(true));
            }

            Logger.log("[readConfig] Loaded " + memberAddresses.size() + " members from config.");
            for (Map.Entry<String, InetSocketAddress> e : memberAddresses.entrySet()) {
                Logger.log("[readConfig] " + e.getKey() + " -> " + e.getValue());
            }
        } catch (IOException e) {
            Logger.log("[readConfig] Failed to read config: " + e.getMessage());
            return -1;
        }

        return memberAddresses.containsKey(memberId) ? memberAddresses.get(memberId).getPort() : -1;
    }
}
