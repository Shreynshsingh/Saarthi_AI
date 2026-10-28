"use client";

import { motion } from "framer-motion";
import Image from "next/image";
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react";
import { createAvatarStream, type AvatarConnection } from "@/lib/avatarStream";
import type { MentorProfile } from "@/lib/types";

export interface AvatarPlayerHandle {
  disconnect: () => void;
}

interface AvatarPlayerProps {
  isSpeaking: boolean;
  thinking?: boolean;
  listening?: boolean;
  mentorName?: string;
  imageSrc?: string;
  mentor?: MentorProfile | null;
  onStreamIds?: (ids: { streamId: string; sessionId: string } | null) => void;
  onPeerLive?: (live: boolean) => void;
}

type PlayerMode = "loading" | "live" | "static";

/**
 * Mentor presence panel. Tries a live WebRTC avatar on mount and renders one
 * of three modes: DID_LIVE video, STATIC mentor photo (Module 4 behavior), or
 * a calm LOADING skeleton while connecting. Every failure lands on STATIC —
 * never a black screen, never a crash.
 */
const AvatarPlayer = forwardRef<AvatarPlayerHandle, AvatarPlayerProps>(function AvatarPlayer(
  {
    isSpeaking,
    thinking = false,
    listening = false,
    mentorName = "Saarthi",
    imageSrc = "/mentor-default.png",
    mentor = null,
    onStreamIds,
    onPeerLive,
  },
  ref
) {
  const [imageMissing, setImageMissing] = useState(false);
  const [mode, setMode] = useState<PlayerMode>("loading");
  const videoRef = useRef<HTMLVideoElement>(null);
  const connectionRef = useRef<AvatarConnection | null>(null);
  const callbacksRef = useRef({ onStreamIds, onPeerLive });
  callbacksRef.current = { onStreamIds, onPeerLive };

  const name = mentor?.mentorName ?? mentorName;
  const photo = mentor?.avatarImageUrl ?? imageSrc;
  const initial = Array.from(name.trim())[0]?.toUpperCase() ?? "S";

  useEffect(() => {
    setImageMissing(false);
  }, [photo]);

  useImperativeHandle(ref, () => ({
    disconnect: () => connectionRef.current?.disconnect(),
  }));

  useEffect(() => {
    let cancelled = false;
    void createAvatarStream(videoRef.current, {
      onPeerLive: (live) => {
        if (cancelled) return;
        setMode(live ? "live" : "static");
        callbacksRef.current.onPeerLive?.(live);
      },
      onFallback: () => {
        if (cancelled) return;
        setMode("static");
        callbacksRef.current.onStreamIds?.(null);
      },
    }).then((connection) => {
      connectionRef.current = connection;
      if (cancelled) {
        connection.disconnect();
        return;
      }
      if (connection.mode === "did") {
        callbacksRef.current.onStreamIds?.({
          streamId: connection.streamId ?? "",
          sessionId: connection.sessionId ?? "",
        });
      } else {
        setMode("static");
      }
    });
    return () => {
      cancelled = true;
      connectionRef.current?.disconnect();
      connectionRef.current = null;
    };
  }, []);

  const live = mode === "live";

  return (
    <div
      className={`relative flex aspect-video w-full max-w-3xl items-center justify-center overflow-hidden rounded-3xl bg-gradient-to-b from-slateDeep to-slateDark ${
        isSpeaking ? "ring-2 ring-primary/40 speak-ring" : "ring-1 ring-white/10"
      }`}
    >
      <video
        ref={videoRef}
        autoPlay
        playsInline
        aria-label={`${name} live video`}
        className={live ? "absolute inset-0 h-full w-full object-cover" : "hidden"}
      />
      {!live && mode === "loading" && (
        <div className="relative flex flex-col items-center gap-3" role="status">
          <span aria-hidden="true" className="h-16 w-16 animate-pulse rounded-full bg-white/10" />
          <p className="text-sm text-white/70">Connecting to your mentor…</p>
        </div>
      )}
      {!live && mode === "static" && !imageMissing && (
        <Image
          src={photo}
          alt={`${name}, your AI mentor`}
          fill
          sizes="(max-width: 768px) 100vw, 768px"
          className="object-cover"
          priority={false}
          onError={() => setImageMissing(true)}
        />
      )}
      {!live && mode === "static" && !imageMissing && (
        <div aria-hidden="true" className="absolute inset-0 bg-slateDeep/45" />
      )}
      {!live && mode === "static" && imageMissing && (
        <div aria-hidden="true" className="absolute inset-0 opacity-20">
          <div className="absolute left-1/4 top-1/4 h-40 w-40 rounded-full bg-sage/40 blur-3xl" />
          <div className="absolute bottom-1/4 right-1/4 h-48 w-48 rounded-full bg-primary/30 blur-3xl" />
        </div>
      )}
      {!live && mode === "static" && (
        <div className="relative flex flex-col items-center gap-4">
          <motion.div
            animate={isSpeaking ? { scale: [1, 1.04, 1] } : { scale: 1 }}
            transition={isSpeaking ? { duration: 1.5, repeat: Infinity, ease: "easeInOut" } : {}}
            className="flex h-28 w-28 items-center justify-center rounded-full bg-primary font-display text-5xl font-semibold text-white shadow-lg"
            aria-label={`${name}, your AI mentor`}
          >
            {initial}
          </motion.div>
          {isSpeaking ? (
            <div className="flex h-8 items-end gap-1.5" aria-hidden="true">
              {[0, 1, 2, 3, 4, 5, 6].map((i) => (
                <span key={i} className="eq-bar w-1.5 rounded-full bg-sage" style={{ animationDelay: `${i * 0.12}s` }} />
              ))}
            </div>
          ) : thinking ? (
            <p className="text-sm text-white/80">
              Saarthi is thinking<span className="thinking-dots" aria-hidden="true" />
            </p>
          ) : listening ? (
            <p className="text-sm text-white/80">I&apos;m listening…</p>
          ) : (
            <p className="text-sm text-white/60">What&apos;s on your mind today?</p>
          )}
        </div>
      )}
    </div>
  );
});

AvatarPlayer.displayName = "AvatarPlayer";

export default AvatarPlayer;
