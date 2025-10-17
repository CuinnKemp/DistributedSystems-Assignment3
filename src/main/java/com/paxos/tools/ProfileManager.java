package com.paxos.tools;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages behavioral profiles for Paxos nodes, such as latency, reliability, or crash likelihood.
 */
public class ProfileManager {
    public enum MemberProfile {
        RELIABLE,   // Respond to messages almost instantly.
        LATENT,     // Experience significant, variable network delays.
        FAILING,    // May crash or become permanently unresponsive at any point.
        STANDARD    // Experience moderate, variable network delays.
    }

    // Cureent profile being enforced
    private final MemberProfile profile;

    // Delay Definitions
    private final int RELIABLE_DELAY_MS = 10;
    private final int STANDARD_DELAY_MS_MIN = 50;
    private final int STANDARD_DELAY_MS_MAX = 150;
    private final int LATENT_DELAY_MS_MIN = 200;
    private final int LATENT_DELAY_MS_MAX = 1000;

    // Failure Chance (i.e. rand < this = fail())
    private final double FAILING_CRASH_PROB = 0.2;

    ProfileManager(MemberProfile profile) {
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
            return ThreadLocalRandom.current().nextDouble() < FAILING_CRASH_PROB;
        }
        return false;
    }
}
