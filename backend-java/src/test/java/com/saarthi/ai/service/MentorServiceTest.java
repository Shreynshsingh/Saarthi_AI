package com.saarthi.ai.service;

import com.saarthi.ai.agent.action.avatar.AvatarAgent;
import com.saarthi.ai.agent.action.avatar.AvatarStreamSession;
import com.saarthi.ai.agent.action.avatar.AvatarUnavailableException;
import com.saarthi.ai.agent.action.avatar.StaticAvatarServiceImpl;
import com.saarthi.ai.dto.MentorProfileResponse;
import com.saarthi.ai.model.MentorProfile;
import com.saarthi.ai.repository.MentorProfileRepository;
import com.saarthi.ai.service.avatar.AvatarStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mentor upload + lookup with map-backed fakes. No D-ID, no database.
 */
class MentorServiceTest {

    @TempDir
    Path tempDir;

    private final Map<UUID, MentorProfile> profiles = new HashMap<>();

    /** Records presenter calls; tests choose the outcome per case. */
    static class StubAvatar implements AvatarAgent {
        int presenterCalls;
        String presenterResult = "https://cdn.example/mentor.jpg";
        boolean failPresenter;

        @Override
        public String createPresenter(String imageUrl) {
            presenterCalls++;
            if (failPresenter) {
                throw new AvatarUnavailableException("down");
            }
            return presenterResult;
        }

        @Override
        public AvatarStreamSession createStream(String presenterIdOrImageUrl) {
            throw new AvatarUnavailableException("down");
        }

        @Override
        public void submitSdpAnswer(String streamId, String sessionId, String sdpAnswer) {
        }

        @Override
        public void submitIceCandidate(String streamId, String sessionId, Object iceCandidate) {
        }

        @Override
        public void sendTalkWithAudio(String streamId, String sessionId, String audioBase64) {
            throw new AvatarUnavailableException("down");
        }

        @Override
        public void closeStream(String streamId, String sessionId) {
        }
    }

    private MentorService service(AvatarAgent avatar) {
        MentorProfileRepository repo = (MentorProfileRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{MentorProfileRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUserId")) {
                        return profiles.values().stream()
                                .filter(p -> p.getUserId().equals(args[0])).findFirst();
                    }
                    if (method.getName().equals("save")) {
                        MentorProfile p = (MentorProfile) args[0];
                        if (p.getId() == null) {
                            p.setId(UUID.randomUUID());
                        }
                        profiles.put(p.getId(), p);
                        return p;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new MentorService(repo,
                new AvatarStorage(tempDir.toString(), 5, "http://localhost:8080"), avatar);
    }

    private static MultipartFile file(String name, String contentType, byte[] content) {
        return (MultipartFile) Proxy.newProxyInstance(
                MentorServiceTest.class.getClassLoader(),
                new Class<?>[]{MultipartFile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBytes" -> content;
                    case "getContentType" -> contentType;
                    case "getSize" -> (long) content.length;
                    case "isEmpty" -> content.length == 0;
                    case "getOriginalFilename" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static byte[] png() {
        byte[] out = new byte[16];
        out[0] = (byte) 0x89;
        out[1] = 'P';
        out[2] = 'N';
        out[3] = 'G';
        return out;
    }

    @Test
    void uploadBindsPresenterOnPublicUrl() {
        UUID user = UUID.randomUUID();
        StubAvatar avatar = new StubAvatar();

        MentorProfileResponse dto = service(avatar).uploadAvatar(
                user, file("me.png", "image/png", png()), "My Teacher", "en-IN-female",
                "https://api.example.com");

        assertEquals("My Teacher", dto.mentorName());
        assertEquals("READY", dto.status());
        assertEquals("https://cdn.example/mentor.jpg", dto.avatarProviderId());
        assertTrue(dto.avatarImageUrl().startsWith("https://api.example.com/uploads/avatars/" + user + "/"));
        assertEquals(1, avatar.presenterCalls);
        assertEquals("en-IN-female", profiles.get(dto.mentorProfileId()).getVoiceId());
        assertEquals(user, profiles.get(dto.mentorProfileId()).getUserId());
    }

    @Test
    void uploadStaysStaticReadyOnLocalhost() {
        StubAvatar avatar = new StubAvatar();

        MentorProfileResponse dto = service(avatar).uploadAvatar(
                UUID.randomUUID(), file("me.png", "image/png", png()), "Local", null,
                "http://localhost:8080");

        assertEquals("STATIC_READY", dto.status());
        assertNull(dto.avatarProviderId());
        assertEquals(0, avatar.presenterCalls, "Localhost URLs must never reach the provider");
        assertTrue(dto.avatarImageUrl().contains("/uploads/avatars/"));
    }

    @Test
    void uploadStaysStaticReadyWhenPresenterFails() {
        StubAvatar avatar = new StubAvatar();
        avatar.failPresenter = true;

        MentorProfileResponse dto = service(avatar).uploadAvatar(
                UUID.randomUUID(), file("me.png", "image/png", png()), "Mine", null,
                "https://api.example.com");

        assertEquals("STATIC_READY", dto.status());
        assertNull(dto.avatarProviderId());
        assertTrue(dto.avatarImageUrl().startsWith("https://api.example.com/uploads/avatars/"));
    }

    @Test
    void staticProviderYieldsStaticReady() {
        MentorProfileResponse dto = service(new StaticAvatarServiceImpl()).uploadAvatar(
                UUID.randomUUID(), file("me.png", "image/png", png()), "Mine", null,
                "https://api.example.com");

        assertEquals("STATIC_READY", dto.status());
        assertNull(dto.avatarProviderId());
    }

    @Test
    void uploadDefaultsBlankName() {
        MentorProfileResponse dto = service(new StubAvatar()).uploadAvatar(
                UUID.randomUUID(), file("me.png", "image/png", png()), "  ", null,
                "http://localhost:8080");

        assertEquals("Saarthi", dto.mentorName());
    }

    @Test
    void uploadRejectsExecutableBeforeProvider() {
        byte[] exe = new byte[16];
        exe[0] = 'M';
        exe[1] = 'Z';
        StubAvatar avatar = new StubAvatar();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service(avatar).uploadAvatar(UUID.randomUUID(),
                        file("x.png", "image/png", exe), "X", null, "https://api.example.com"));
        assertTrue(e.getMessage().contains("doesn't look like a photo"), e.getMessage());
        assertEquals(0, avatar.presenterCalls);
        assertTrue(profiles.isEmpty(), "Rejected upload must not create a profile");
    }

    @Test
    void myMentorIsPerUser() {
        UUID first = UUID.randomUUID();
        MentorService service = service(new StubAvatar());
        service.uploadAvatar(first, file("me.png", "image/png", png()), "Mine", null,
                "http://localhost:8080");

        assertTrue(service.myMentor(first).isPresent());
        assertEquals(Optional.empty(), service.myMentor(UUID.randomUUID()));
    }

    @Test
    void localhostDetection() {
        assertTrue(MentorService.isLocalhostUrl("http://localhost:8080/uploads/a.jpg"));
        assertTrue(MentorService.isLocalhostUrl("http://127.0.0.1:8080/uploads/a.jpg"));
        assertTrue(MentorService.isLocalhostUrl("not a url"));
        assertTrue(!MentorService.isLocalhostUrl("https://api.example.com/uploads/a.jpg"));
    }
}
