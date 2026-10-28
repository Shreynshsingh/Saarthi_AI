package com.saarthi.ai.agent.action.avatar;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Kill-switch provider: every live-video call throws so callers fall back
 * cleanly to static photo + voice (Module 4 behavior). Signaling teardowns
 * are silent no-ops — there is nothing live to drive.
 */
@Service
@ConditionalOnProperty(name = "saarthi.avatar.provider", havingValue = "static")
public class StaticAvatarServiceImpl implements AvatarAgent {

    @Override
    public String createPresenter(String imageUrl) {
        return "static";
    }

    @Override
    public AvatarStreamSession createStream(String presenterIdOrImageUrl) {
        throw new AvatarUnavailableException("Live video is unavailable right now — photo mode it is.");
    }

    @Override
    public void submitSdpAnswer(String streamId, String sessionId, String sdpAnswer) {
        // No live stream exists under the static provider.
    }

    @Override
    public void submitIceCandidate(String streamId, String sessionId, Object iceCandidate) {
        // No live stream exists under the static provider.
    }

    @Override
    public void sendTalkWithAudio(String streamId, String sessionId, String audioBase64) {
        // Must throw (not no-op): silence here would mute sentences the
        // frontend was told the avatar is voicing.
        throw new AvatarUnavailableException("Live video is unavailable right now — photo mode it is.");
    }

    @Override
    public void closeStream(String streamId, String sessionId) {
        // Nothing to tear down.
    }
}
