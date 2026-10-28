package com.saarthi.ai.service;

import com.saarthi.ai.agent.action.avatar.AvatarAgent;
import com.saarthi.ai.agent.action.avatar.AvatarUnavailableException;
import com.saarthi.ai.dto.MentorProfileResponse;
import com.saarthi.ai.model.MentorProfile;
import com.saarthi.ai.repository.MentorProfileRepository;
import com.saarthi.ai.service.avatar.AvatarStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mentor profiles: one active mentor per user (upload updates the existing
 * profile). The photo is validated + stored first; provider binding is
 * best-effort — a localhost URL or a provider failure yields STATIC_READY
 * (photo kept for static mode) instead of failing the whole upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MentorService {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    private final MentorProfileRepository mentorProfileRepository;
    private final AvatarStorage avatarStorage;
    private final AvatarAgent avatarAgent;

    @Transactional
    public MentorProfileResponse uploadAvatar(UUID userId, MultipartFile file, String mentorName,
                                              String voiceId, String requestBaseUrl) {
        String name = mentorName == null || mentorName.isBlank() ? "Saarthi" : mentorName.trim();
        if (name.length() > 100) {
            throw new IllegalArgumentException("Please keep the mentor name under 100 characters.");
        }
        String relativePath = avatarStorage.savePhoto(file, userId);
        String photoUrl = requestBaseUrl.endsWith("/")
                ? requestBaseUrl + relativePath : requestBaseUrl + "/" + relativePath;

        String providerId = null;
        String status = "STATIC_READY";
        if (!isLocalhostUrl(photoUrl)) {
            try {
                String bound = avatarAgent.createPresenter(photoUrl);
                // The static sentinel means "no provider binding", not a real id.
                if (bound != null && !"static".equals(bound)) {
                    providerId = bound;
                    status = "READY";
                }
            } catch (AvatarUnavailableException e) {
                log.warn("[MentorService] Presenter binding failed, photo kept for static mode");
            }
        }

        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseGet(() -> MentorProfile.builder().userId(userId).build());
        profile.setMentorName(name);
        profile.setAvatarImageUrl(photoUrl);
        profile.setAvatarProviderId(providerId);
        profile.setStatus(status);
        if (voiceId != null && !voiceId.isBlank()) {
            profile.setVoiceId(voiceId.trim());
        }
        MentorProfile saved = mentorProfileRepository.save(profile);
        log.info("[MentorService] Mentor {} for user={} mentor={}", status, userId, saved.getId());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<MentorProfileResponse> myMentor(UUID userId) {
        return mentorProfileRepository.findByUserId(userId).map(this::toDto);
    }

    private MentorProfileResponse toDto(MentorProfile profile) {
        return new MentorProfileResponse(profile.getId(), profile.getMentorName(),
                profile.getAvatarImageUrl(), profile.getAvatarProviderId(), profile.getStatus());
    }

    /** D-ID's cloud cannot fetch loopback URLs — those uploads stay STATIC_READY. */
    static boolean isLocalhostUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
