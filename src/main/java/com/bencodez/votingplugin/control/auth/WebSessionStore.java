package com.bencodez.votingplugin.control.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded, in-memory WebUI sessions with idle and absolute expiry. */
public final class WebSessionStore {
    private static final int MAX_SESSIONS = 100;
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);

    private final Clock clock;
    private final SecureRandom random;
    private final LinkedHashMap<String, StoredSession> sessions = new LinkedHashMap<>();

    public WebSessionStore(Clock clock) {
        this(clock, new SecureRandom());
    }

    WebSessionStore(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized Session create(String credentialRevision) {
        Objects.requireNonNull(credentialRevision, "credentialRevision");
        Instant now = clock.instant();
        removeExpired(now);
        while (sessions.size() >= MAX_SESSIONS) {
            sessions.remove(sessions.keySet().iterator().next());
        }
        String id = token();
        Session session = new Session(id, token(), credentialRevision);
        sessions.put(id, new StoredSession(session, now, now));
        return session;
    }

    public synchronized Session authenticate(String id, String credentialRevision) {
        if (id == null || credentialRevision == null) return null;
        Instant now = clock.instant();
        StoredSession stored = sessions.get(id);
        if (stored == null || expired(stored, now)
                || !MessageDigest.isEqual(stored.session().credentialRevision()
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        credentialRevision.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            sessions.remove(id);
            return null;
        }
        sessions.put(id, new StoredSession(stored.session(), stored.created(), now));
        return stored.session();
    }

    public synchronized void remove(String id) {
        if (id != null) sessions.remove(id);
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<String, StoredSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            if (expired(iterator.next().getValue(), now)) iterator.remove();
        }
    }

    private static boolean expired(StoredSession stored, Instant now) {
        return !now.isBefore(stored.lastUsed().plus(IDLE_TIMEOUT))
                || !now.isBefore(stored.created().plus(ABSOLUTE_TIMEOUT));
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Session(String id, String csrfToken, String credentialRevision) { }
    private record StoredSession(Session session, Instant created, Instant lastUsed) { }
}
