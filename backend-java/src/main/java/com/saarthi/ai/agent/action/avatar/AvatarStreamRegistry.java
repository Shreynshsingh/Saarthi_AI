package com.saarthi.ai.agent.action.avatar;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live-stream registry: the single place that knows which user owns which
 * provider stream. Every SDP/ICE/talk/close call resolves through here, so a
 * user can only ever drive their own stream. In-memory for the MVP.
 */
@Service
public class AvatarStreamRegistry {

    public record StreamRef(String streamId, String sessionId) {
    }

    private record Entry(UUID userId, String sessionId) {
    }

    private final Map<String, StreamRef> byUser = new ConcurrentHashMap<>();
    private final Map<String, Entry> byStream = new ConcurrentHashMap<>();

    public void register(UUID userId, String streamId, String sessionId) {
        byUser.put(userId.toString(), new StreamRef(streamId, sessionId));
        byStream.put(streamId, new Entry(userId, sessionId));
    }

    /** The user's current live stream, if any. */
    public StreamRef resolveForUser(UUID userId) {
        return byUser.get(userId.toString());
    }

    /** Session id when (and only when) the stream belongs to the user. */
    public String verify(UUID userId, String streamId) {
        if (streamId == null) {
            return null;
        }
        Entry entry = byStream.get(streamId);
        if (entry == null || !entry.userId().equals(userId)) {
            return null;
        }
        return entry.sessionId();
    }

    public void remove(String streamId) {
        Entry entry = byStream.remove(streamId);
        if (entry != null) {
            byUser.remove(entry.userId().toString(), new StreamRef(streamId, entry.sessionId()));
        }
    }
}
