package com.saarthi.ai.agent.action.avatar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Provider kill-switch, presenter binding, and stream ownership. No network.
 */
class AvatarProvidersTest {

    @Test
    void staticProviderFallsBackCleanly() {
        StaticAvatarServiceImpl statik = new StaticAvatarServiceImpl();

        assertEquals("static", statik.createPresenter("http://x/y.jpg"));
        assertThrows(AvatarUnavailableException.class,
                () -> statik.createStream("http://x/y.jpg"));
        assertThrows(AvatarUnavailableException.class,
                () -> statik.sendTalkWithAudio("s", "sess", "QUJD"));
        // Teardowns never throw.
        statik.submitSdpAnswer("s", "sess", "sdp");
        statik.submitIceCandidate("s", "sess", null);
        statik.closeStream("s", "sess");
    }

    @Test
    void didWithoutKeyRefusesEverything() {
        DidAvatarServiceImpl keyless =
                new DidAvatarServiceImpl(null, "  ", null, new ObjectMapper());

        assertThrows(AvatarUnavailableException.class,
                () -> keyless.createPresenter("http://x/y.jpg"));
        assertThrows(AvatarUnavailableException.class,
                () -> keyless.createStream("http://x/y.jpg"));
        assertThrows(AvatarUnavailableException.class,
                () -> keyless.sendTalkWithAudio("s", "sess", "QUJD"));
        keyless.closeStream("s", "sess");
    }

    @Test
    void createPresenterBindsSourceUrl() {
        DidAvatarServiceImpl did =
                new DidAvatarServiceImpl(null, "k", null, new ObjectMapper());

        assertEquals("https://cdn.example/mentor.jpg",
                did.createPresenter("https://cdn.example/mentor.jpg"));
        assertThrows(AvatarUnavailableException.class, () -> did.createPresenter("  "));
        assertThrows(AvatarUnavailableException.class, () -> did.createPresenter("not-a-url"));
    }

    @Test
    void registryEnforcesOwnership() {
        AvatarStreamRegistry registry = new AvatarStreamRegistry();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        registry.register(owner, "stream-1", "sess-1");

        AvatarStreamRegistry.StreamRef ref = registry.resolveForUser(owner);
        assertEquals("stream-1", ref.streamId());
        assertEquals("sess-1", ref.sessionId());
        assertNull(registry.resolveForUser(stranger));
        assertEquals("sess-1", registry.verify(owner, "stream-1"));
        assertNull(registry.verify(stranger, "stream-1"));
        assertNull(registry.verify(owner, "nope"));

        registry.remove("stream-1");
        assertNull(registry.verify(owner, "stream-1"));
        assertNull(registry.resolveForUser(owner));
    }
}
