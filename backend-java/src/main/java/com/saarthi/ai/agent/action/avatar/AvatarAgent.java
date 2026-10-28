package com.saarthi.ai.agent.action.avatar;

/**
 * Avatar provider contract. Blocking by design (servlet stack): every method
 * either completes the provider call or throws {@link AvatarUnavailableException},
 * and callers fall back to static photo + voice. DidAvatarServiceImpl is the
 * MVP; a MuseTalk impl swaps in here later without touching the orchestrator,
 * the controllers, or a line of frontend code.
 */
public interface AvatarAgent {

    /**
     * Binds a mentor photo to the provider. Returns the provider-side
     * presenter/source identifier to store on the mentor profile.
     */
    String createPresenter(String imageUrl);

    /** Opens a live stream. The returned offer goes to the browser's peer connection. */
    AvatarStreamSession createStream(String presenterIdOrImageUrl);

    /** Forwards the browser's SDP answer to the provider. */
    void submitSdpAnswer(String streamId, String sessionId, String sdpAnswer);

    /** Forwards one trickled ICE candidate. Best-effort. */
    void submitIceCandidate(String streamId, String sessionId, Object iceCandidate);

    /**
     * Voices one sentence of Base64 MP3 through the live avatar. Returns
     * normally only when the provider accepted the talk.
     */
    void sendTalkWithAudio(String streamId, String sessionId, String audioBase64);

    /** Best-effort teardown. */
    void closeStream(String streamId, String sessionId);
}
