"use client";

import {
  closeAvatarStream,
  createAvatarStreamRequest,
  sendAvatarIce,
  submitAvatarAnswer,
} from "./mentor";

/**
 * Live-avatar connection engine (native WebRTC only). Opens the provider
 * stream via the backend, wires a recvonly peer connection, and attaches the
 * remote video to the given element. Every failure path resolves static and
 * calls onFallback — this function never throws into session UI.
 */

export interface AvatarConnection {
  mode: "did" | "static";
  streamId: string | null;
  sessionId: string | null;
  disconnect: () => void;
}

interface StreamCallbacks {
  onPeerLive: (live: boolean) => void;
  onFallback: () => void;
}

const TRACK_TIMEOUT_MS = 8000;

function staticConnection(onFallback: () => void): AvatarConnection {
  onFallback();
  return { mode: "static", streamId: null, sessionId: null, disconnect: () => undefined };
}

export async function createAvatarStream(
  video: HTMLVideoElement | null,
  callbacks: StreamCallbacks
): Promise<AvatarConnection> {
  const { onPeerLive, onFallback } = callbacks;
  let setup;
  try {
    setup = await createAvatarStreamRequest();
  } catch {
    return staticConnection(onFallback);
  }
  if (
    setup.provider !== "did" ||
    !setup.streamId ||
    !setup.sessionId ||
    !setup.sdpOffer ||
    !video
  ) {
    return staticConnection(onFallback);
  }
  const streamId = setup.streamId;
  const sessionId = setup.sessionId;

  let peer: RTCPeerConnection | null = null;
  let settled = false;
  let watchdog = 0;

  const teardown = () => {
    window.clearTimeout(watchdog);
    try {
      peer?.close();
    } catch {
      // already closed
    }
    peer = null;
  };

  try {
    peer = new RTCPeerConnection({
      iceServers: (setup.iceServers ?? []).map((server) => ({
        urls: server.urls,
        username: server.username,
        credential: server.credential,
      })),
    });
    peer.addTransceiver("video", { direction: "recvonly" });
    peer.addTransceiver("audio", { direction: "recvonly" });
    peer.ontrack = (event: RTCTrackEvent) => {
      const remote = event.streams[0];
      if (!remote || settled) return;
      settled = true;
      window.clearTimeout(watchdog);
      video.srcObject = remote;
      onPeerLive(true);
    };
    peer.onicecandidate = (event: RTCPeerConnectionIceEvent) => {
      const candidate = event.candidate;
      if (candidate && candidate.candidate) {
        void sendAvatarIce(streamId, sessionId, candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex).catch(
          () => undefined
        );
      }
    };
    peer.onconnectionstatechange = () => {
      const state = peer?.connectionState;
      if (state === "failed" || state === "closed" || state === "disconnected") {
        onPeerLive(false);
      }
    };
    await peer.setRemoteDescription({ type: "offer", sdp: setup.sdpOffer });
    const answer = await peer.createAnswer();
    await peer.setLocalDescription(answer);
    if (!answer.sdp) throw new Error("no answer");
    await submitAvatarAnswer(streamId, sessionId, answer.sdp);
  } catch {
    teardown();
    return staticConnection(onFallback);
  }

  // Black-video watchdog: no track within 8s means STATIC, and local audio
  // resumes for the following chunks via onPeerLive(false).
  watchdog = window.setTimeout(() => {
    if (!settled) {
      teardown();
      onPeerLive(false);
      onFallback();
    }
  }, TRACK_TIMEOUT_MS);

  return {
    mode: "did",
    streamId,
    sessionId,
    disconnect: () => {
      teardown();
      onPeerLive(false);
      void closeAvatarStream(streamId, sessionId).catch(() => undefined);
    },
  };
}
