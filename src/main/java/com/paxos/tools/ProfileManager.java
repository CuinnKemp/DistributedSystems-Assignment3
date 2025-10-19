package com.paxos.tools;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages behavioral profiles for Paxos nodes, such as latency, reliability, or crash likelihood.
 */
public class ProfileManager {
    /**
     * Member Connection and Run Profiles
     */
    public enum MemberProfile {
        RELIABLE,   // Respond to messages almost instantly.
        LATENT,     // Experience significant, variable network delays.
        FAILING,    // May crash or become permanently unresponsive at any point.
        STANDARD    // Experience moderate, variable network delays.
    }

    // Cureent profile being enforced
    private final MemberProfile profile;

    // Delay Definitions
    private final int RELIABLE_DELAY_MS = 0;
    private final int STANDARD_DELAY_MS_MIN = 50;
    private final int STANDARD_DELAY_MS_MAX = 150;
    private final int LATENT_DELAY_MS_MIN = 200;
    private final int LATENT_DELAY_MS_MAX = 1000;

    // Failure Chance (i.e. rand < this = DropMessage())
    private final double DROP_PROB = 0.2;
    private final double CRASH_PROB = 0.9;

    private final int memberCount;

    ProfileManager(MemberProfile profile, int memberCount) {
        this.memberCount = memberCount;

        if (profile == null) this.profile = MemberProfile.STANDARD;
        else this.profile = profile;
    }

    /**
     * Simulate network delay based profile.
     */
    public void simulateDelay() {
        int delay = switch (this.profile) {
            case RELIABLE -> RELIABLE_DELAY_MS;
            case STANDARD -> ThreadLocalRandom.current().nextInt(STANDARD_DELAY_MS_MIN, STANDARD_DELAY_MS_MAX + 1);
            case LATENT -> ThreadLocalRandom.current().nextInt(LATENT_DELAY_MS_MIN, LATENT_DELAY_MS_MAX + 1);
            default -> ThreadLocalRandom.current().nextInt(STANDARD_DELAY_MS_MIN, LATENT_DELAY_MS_MAX + 1);
        };
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Indicates whether a message should be sent or rejected
     *
     * @return true if we should fail, false otherwise
     */
    public boolean shouldFail() {
        if (profile == MemberProfile.FAILING) {
            return ThreadLocalRandom.current().nextDouble() < DROP_PROB;
        }
        return false;
    }

    private final AtomicInteger crashMessageCounter = new AtomicInteger(0);

    /**
     * Gets whether the member should crash - will send a minimum of 1 message per node to ensure a proposal can be sent
     *
     * @return true if crash should occur false otherwise.
     */
    public boolean shouldCrash() {
        if (profile == MemberProfile.FAILING) {
            synchronized (crashMessageCounter) {
                crashMessageCounter.addAndGet(1);
                if (crashMessageCounter.get() == memberCount - 1) {
                    return ThreadLocalRandom.current().nextDouble() < CRASH_PROB;
                }
            }
        }
        return false;
    }
}
