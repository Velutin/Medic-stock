package com.project.mss.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.project.mss.exception.TooManyAttemptsException;

/**
 * Brake on the login endpoint, against password guessing and against the processor cost of it.
 *
 * <p>BCrypt at cost 12 takes about a quarter of a second per attempt <em>by design</em>: it is what
 * makes a stolen password hash expensive to crack. The other side of that coin is that an unlimited
 * login endpoint lets anyone spend the server's processor at will — on a two-core machine a few
 * hundred simultaneous attempts take the system down without guessing a single password. That is
 * why the check happens <em>before</em> the password is verified.
 *
 * <p>Two independent brakes:
 * <ul>
 *   <li><b>Per account</b>: a growing wait after consecutive failures ({@code accountFreeAttempts}
 *       mistakes are free, then 2s, 4s, 8s… up to {@code accountMaxDelaySeconds}). The account is
 *       <b>never locked</b>. A lockout would let anyone leave a colleague outside the system just by
 *       typing the wrong password a few times — in an operating room that is worse than the attack
 *       it prevents. A growing wait cuts guessing down to a trickle and costs a few seconds to
 *       whoever simply mistyped.</li>
 *   <li><b>Per address</b>: a ceiling of {@code ipMaxFailures} failures per window, which is what
 *       bounds the processor cost.</li>
 * </ul>
 *
 * <p>Only <em>failures</em> count. A busy day with everyone signing in normally never trips either
 * brake, and an attacker produces nothing but failures.
 *
 * <p>The counters live in memory, not in the database: they are disposable by nature, and this way
 * the feature needs no migration. Restarting the service clears them — which matters little, since
 * an attacker cannot restart it. With more than one instance of the mss the brakes would count per
 * instance; today there is one.
 */
@Service
public class LoginThrottleService {

    /** Entries idle for longer than this are discarded by the hourly cleanup. */
    private static final Duration IDLE_BEFORE_CLEANUP = Duration.ofHours(1);

    private final Map<String, Attempts> byAccount = new ConcurrentHashMap<>();
    private final Map<String, Attempts> byAddress = new ConcurrentHashMap<>();

    @Value("${app.security.login.account-free-attempts:3}")
    private int accountFreeAttempts;

    @Value("${app.security.login.account-max-delay-seconds:30}")
    private long accountMaxDelaySeconds;

    @Value("${app.security.login.ip-max-failures:30}")
    private int ipMaxFailures;

    @Value("${app.security.login.ip-window-seconds:300}")
    private long ipWindowSeconds;

    /**
     * State of one account or one address.
     *
     * <p>Every read and every write of these fields happens inside a {@code compute} /
     * {@code computeIfPresent} of the map, which {@link ConcurrentHashMap} runs atomically for the
     * key. Synchronising on the object itself would not do: the writer reaches it through
     * {@code compute} and would not hold that monitor.
     */
    private static final class Attempts {
        int failures;
        Instant lastFailure = Instant.EPOCH;
        Instant windowStart = Instant.EPOCH;
        int inWindow;
    }

    /**
     * Called before the password is verified. Throws when the caller has to wait.
     *
     * @param email   the account being attempted, already normalised
     * @param address the client address (the real one: nginx sends X-Forwarded-For and
     *                {@code server.forward-headers-strategy} makes Spring honour it)
     */
    public void checkAllowed(String email, String address) {
        long addressWait = addressWaitSeconds(address);
        if (addressWait > 0) {
            throw new TooManyAttemptsException("Too many sign-in attempts from this address. Try again in "
                    + addressWait + " seconds.");
        }
        long accountWait = accountWaitSeconds(email);
        if (accountWait > 0) {
            throw new TooManyAttemptsException("Too many sign-in attempts. Try again in " + accountWait + " seconds.");
        }
    }

    /** Called when the password did not match. */
    public void recordFailure(String email, String address) {
        Instant now = Instant.now();
        if (email != null) {
            byAccount.compute(key(email), (k, a) -> {
                Attempts attempts = a == null ? new Attempts() : a;
                attempts.failures++;
                attempts.lastFailure = now;
                return attempts;
            });
        }
        if (address != null) {
            byAddress.compute(address, (k, a) -> {
                Attempts attempts = a == null ? new Attempts() : a;
                if (Duration.between(attempts.windowStart, now).getSeconds() >= ipWindowSeconds) {
                    attempts.windowStart = now;
                    attempts.inWindow = 0;
                }
                attempts.inWindow++;
                attempts.lastFailure = now;
                return attempts;
            });
        }
    }

    /** Called after a successful sign-in: the account starts over. */
    public void recordSuccess(String email) {
        if (email != null) {
            byAccount.remove(key(email));
        }
    }

    private long accountWaitSeconds(String email) {
        // The holder is how a value comes out of computeIfPresent, whose remapping function runs
        // atomically for the key — the same guarantee recordFailure relies on.
        long[] wait = {0};
        byAccount.computeIfPresent(key(email), (k, attempts) -> {
            int excess = attempts.failures - accountFreeAttempts;
            if (excess > 0) {
                long required = Math.min(accountMaxDelaySeconds, 1L << Math.min(excess, 20));
                long elapsed = Duration.between(attempts.lastFailure, Instant.now()).getSeconds();
                wait[0] = Math.max(0, required - elapsed);
            }
            return attempts;
        });
        return wait[0];
    }

    private long addressWaitSeconds(String address) {
        if (address == null) return 0;
        long[] wait = {0};
        byAddress.computeIfPresent(address, (k, attempts) -> {
            long elapsed = Duration.between(attempts.windowStart, Instant.now()).getSeconds();
            if (elapsed < ipWindowSeconds && attempts.inWindow >= ipMaxFailures) {
                wait[0] = ipWindowSeconds - elapsed;
            }
            return attempts;
        });
        return wait[0];
    }

    private static String key(String email) {
        return email.trim().toLowerCase();
    }

    /** Keeps the maps from growing without bound on a long-running service. */
    @Scheduled(cron = "0 30 * * * *")
    public void cleanUp() {
        Instant cutoff = Instant.now().minus(IDLE_BEFORE_CLEANUP);
        byAccount.values().removeIf(a -> a.lastFailure.isBefore(cutoff));
        byAddress.values().removeIf(a -> a.lastFailure.isBefore(cutoff));
    }
}
