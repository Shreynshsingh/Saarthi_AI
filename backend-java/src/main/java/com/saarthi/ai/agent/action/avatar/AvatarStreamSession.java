package com.saarthi.ai.agent.action.avatar;

/**
 * Live-stream handle. sdpOffer is the provider's SDP string; iceServersJson is
 * the raw provider ICE-servers JSON (parsed at the controller boundary, so no
 * other class depends on provider JSON structure).
 */
public record AvatarStreamSession(String streamId, String sessionId, String sdpOffer, String iceServersJson) {
}
