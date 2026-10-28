"use client";

import { MotionConfig, motion } from "framer-motion";
import {
  ChevronDown,
  Hand,
  Keyboard,
  LogIn,
  PhoneOff,
  RotateCcw,
  Send,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import AvatarPlayer from "@/components/AvatarPlayer";
import type { AvatarPlayerHandle } from "@/components/AvatarPlayer";
import CrisisModal from "@/components/CrisisModal";
import EmotionBadge from "@/components/EmotionBadge";
import EmotionDetector from "@/components/EmotionDetector";
import ProtectedRoute from "@/components/ProtectedRoute";
import SignDetector, { SignDetectorStatus } from "@/components/SignDetector";
import TranscriptPanel, { Turn } from "@/components/TranscriptPanel";
import VoiceRecorder from "@/components/VoiceRecorder";
import { clearToken, getToken } from "@/lib/api";
import { AudioPlaybackQueue } from "@/lib/audioPlayback";
import { getMyMentor } from "@/lib/mentor";
import { SIGN_HELP, signLabel } from "@/lib/signHelp";
import {
  ChatRequest,
  ChatResponse,
  MentorProfile,
  MoodType,
  SignDetection,
  SignName,
  frameText,
} from "@/lib/types";
import { useSessionSocket } from "@/lib/useSessionSocket";

let turnId = 1;

const KNOWN_SIGNS: readonly SignName[] = [
  "HELLO",
  "THANK_YOU",
  "YES",
  "NO",
  "PLEASE",
  "SAD",
  "HAPPY",
  "STRESS",
  "HELP",
  "I_NEED_HELP",
];

/** Long server guidance shortened to fit the small PiP badge. */
function shortNotice(text: string): string {
  if (text.includes("hold the sign steady")) return "Hold the sign steady…";
  if (text.includes("couldn't load")) return "Sign model unavailable";
  return text;
}

export default function SessionPage() {
  const router = useRouter();
  const [token, setToken] = useState<string | null>(null);
  const [live, setLive] = useState(true);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [subtitle, setSubtitle] = useState("What\u2019s on your mind today?");
  const [text, setText] = useState("");
  const [awaiting, setAwaiting] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [listening, setListening] = useState(false);
  const [softError, setSoftError] = useState<string | null>(null);
  const [crisis, setCrisis] = useState<string | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [mediaError, setMediaError] = useState<string | null>(null);
  const [textModeOnly, setTextModeOnly] = useState(false);
  const [transcriptOpen, setTranscriptOpen] = useState(false);
  const [avatarSpeaking, setAvatarSpeaking] = useState(false);
  const [mentor, setMentor] = useState<MentorProfile | null>(null);
  const [signMode, setSignMode] = useState(false);
  const [handPresent, setHandPresent] = useState(false);
  const [signHelpOpen, setSignHelpOpen] = useState(false);
  const [signStatus, setSignStatus] = useState<SignDetectorStatus | null>(null);
  const [signNotice, setSignNotice] = useState<{ text: string; at: number } | null>(null);
  const [committedSign, setCommittedSign] = useState<SignDetection | null>(null);
  // Shared preview <video> element: EmotionDetector publishes it, SignDetector
  // reads pixels from it. Ref (not state) so camera attach never re-renders.
  const sharedVideoRef = useRef<HTMLVideoElement | null>(null);
  // Mirrors for the stable handleFrame callback. Sign spam protection lives
  // in SignDetector (stability + cooldown + re-arm); the page only drops
  // emits that arrive mid-turn and routes unclear-sign ERRORs to the badge.
  const signModeRef = useRef(false);
  signModeRef.current = signMode;
  const awaitingRef = useRef(false);
  awaitingRef.current = awaiting;
  const lastSignFrameAtRef = useRef(0);

  /**
   * Latest detected emotion. A ref — NOT state — so the 400ms detection loop
   * never re-renders this page into recreating the WebSocket (the socket lives
   * in a class instance keyed only by token). The top-bar badge below keeps a
   * separate display-only copy that the socket never observes.
   */
  const emotionRef = useRef<{ emotion: MoodType; confidence: number }>({
    emotion: "NEUTRAL",
    confidence: 0.6,
  });
  const [badge, setBadge] = useState(emotionRef.current);
  const [badgeLive, setBadgeLive] = useState(false);

  const conversationRef = useRef<string | null>(null);
  const mentorTurnRef = useRef<number | null>(null);
  const peerLiveRef = useRef(false);
  const avatarIdsRef = useRef<{ streamId: string; sessionId: string } | null>(null);
  const avatarRef = useRef<AvatarPlayerHandle>(null);
  const queueRef = useRef<AudioPlaybackQueue | null>(null);
  if (!queueRef.current) queueRef.current = new AudioPlaybackQueue();
  const textInputRef = useRef<HTMLInputElement>(null);

  const handleEmotion = useCallback((emotion: MoodType, confidence: number) => {
    emotionRef.current = { emotion, confidence };
    setBadge({ emotion, confidence });
    setBadgeLive(true);
  }, []);

  const handleCameraError = useCallback((error: string | null) => {
    setCameraError(error);
    if (error) {
      emotionRef.current = { emotion: "NEUTRAL", confidence: 0.6 };
      setBadge({ emotion: "NEUTRAL", confidence: 0.6 });
      setBadgeLive(false);
    }
  }, []);

  useEffect(() => {
    setToken(getToken());
  }, []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    void getMyMentor()
      .then((profile) => {
        if (!cancelled) setMentor(profile);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [token]);

  /** Stream ids ride every message only while the avatar video is actually live. */
  const avatarIds = (): { avatarStreamId: string; avatarSessionId: string } | Record<string, never> => {
    const ids = avatarIdsRef.current;
    if (!peerLiveRef.current || !ids) return {};
    return { avatarStreamId: ids.streamId, avatarSessionId: ids.sessionId };
  };

  useEffect(() => {
    const queue = queueRef.current;
    if (!queue) return;
    return queue.onSpeakingChange(setSpeaking);
  }, []);

  const handleFrame = useCallback((frame: ChatResponse) => {
    if (frame.conversationId) conversationRef.current = frame.conversationId;
    switch (frame.type) {
      case "TRANSCRIPTION": {
        const heard = frame.transcribedText ?? frameText(frame);
        if (heard) {
          const signMatch = /^\[Sign: ([^\]]+)\]/.exec(heard);
          if (signMatch) {
            // Confident sign: open a turn exactly like a sent text/voice
            // message (further emits drop via awaiting), badge the label.
            const label = signMatch[1];
            if ((KNOWN_SIGNS as readonly string[]).includes(label)) {
              setCommittedSign({ sign: label as SignName, confidence: 1 });
            }
            setTurns((prev) => [...prev, { id: turnId++, role: "user", text: heard }]);
            beginTurn();
            setSubtitle("Reading your sign…");
          } else {
            setTurns((prev) => [...prev, { id: turnId++, role: "user", text: heard }]);
            setSubtitle(`You said: \u201C${heard}\u201D`);
          }
        }
        break;
      }
      case "MENTOR_RESPONSE_CHUNK": {
        const sentence = frame.textChunk ?? "";
        if (!sentence) break;
        if (mentorTurnRef.current === null) {
          mentorTurnRef.current = turnId++;
          const id = mentorTurnRef.current;
          setTurns((prev) => [...prev, { id, role: "mentor", text: sentence }]);
        } else {
          const id = mentorTurnRef.current;
          setTurns((prev) =>
            prev.map((t) => (t.id === id ? { ...t, text: `${t.text} ${sentence}` } : t))
          );
        }
        // Subtitle updates the instant a chunk arrives, even while its audio waits in line.
        setSubtitle(sentence);
        // AUDIO RULE: the avatar video already produces sound for chunks it
        // voiced, so those skip the local queue (no double voice). Anything
        // else — including a dead peer overriding a stale true — plays locally.
        const avatarVoices = frame.avatarSpeaking === true && peerLiveRef.current;
        setAvatarSpeaking(avatarVoices);
        if (!avatarVoices) queueRef.current?.enqueue(frame.audioBase64);
        break;
      }
      case "MENTOR_RESPONSE_END":
        mentorTurnRef.current = null;
        setAwaiting(false);
        setAvatarSpeaking(false);
        break;
      case "CRISIS_ALERT":
        queueRef.current?.stop();
        mentorTurnRef.current = null;
        setAwaiting(false);
        setAvatarSpeaking(false);
        setCrisis(frameText(frame) || "I\u2019m really glad you told me this.");
        break;
      case "ERROR": {
        const text = frameText(frame) || "I couldn't hear that clearly. Take your time, I'm here.";
        // Unclear-sign guidance (Task 3) arrives while NOT awaiting a turn, so
        // it becomes the sign badge hint - never an error toast. Genuine turn
        // failures arrive while awaiting and keep the existing path.
        if (
          signModeRef.current &&
          !awaitingRef.current &&
          Date.now() - lastSignFrameAtRef.current < 4000
        ) {
          setSignNotice({ text, at: Date.now() });
          break;
        }
        queueRef.current?.stop();
        mentorTurnRef.current = null;
        setAwaiting(false);
        setAvatarSpeaking(false);
        setSoftError(text);
        break;
      }
      default:
        break;
    }
  }, []);

  const { state: connState, notice: connNotice, send, connect, disconnect } = useSessionSocket(
    token,
    handleFrame
  );

  const beginTurn = () => {
    mentorTurnRef.current = null;
    queueRef.current?.reset();
    setSoftError(null);
    setMediaError(null);
    setAwaiting(true);
  };

  const sendText = () => {
    const value = text.trim();
    if (!value || awaiting) return;
    const { emotion, confidence } = emotionRef.current;
    const message: ChatRequest = {
      type: "USER_MESSAGE",
      conversationId: conversationRef.current,
      inputMode: "TEXT",
      text: value,
      emotion,
      emotionConfidence: confidence,
      ...avatarIds(),
    };
    if (!send(message)) {
      setSoftError("Not connected yet. Press Reconnect, then send again — I\u2019m here.");
      return;
    }
    beginTurn();
    setTurns((prev) => [...prev, { id: turnId++, role: "user", text: value }]);
    setSubtitle("Saarthi is thinking…");
    setText("");
  };

  const sendVoice = (audioBase64: string) => {
    if (awaiting) return;
    const { emotion, confidence } = emotionRef.current;
    const message: ChatRequest = {
      type: "USER_MESSAGE",
      conversationId: conversationRef.current,
      inputMode: "VOICE",
      audioBase64,
      emotion,
      emotionConfidence: confidence,
      ...avatarIds(),
    };
    if (!send(message)) {
      setMediaError("Not connected yet. Press Reconnect, then speak again — I\u2019m here.");
      return;
    }
    beginTurn();
    setSubtitle("Hearing you…");
  };

  const handleStableSign = (
    _sign: SignName | null,
    _confidence: number,
    landmarks: number[][]
  ) => {
    // One stable hold = ONE classification request. Emits arriving mid-turn
    // are dropped; the detector's own cooldown + re-arm spaces the rest.
    // No direct frontend->Python call exists anywhere on this path: the
    // landmarks travel to Java, Java calls Python, the reply comes back
    // over this same socket as TRANSCRIPTION / chunks / ERROR.
    if (!signMode || awaiting) return;
    if (!landmarks || landmarks.length !== 21) return;
    const { emotion, confidence } = emotionRef.current;
    const message: ChatRequest = {
      type: "USER_MESSAGE",
      conversationId: conversationRef.current,
      inputMode: "SIGN",
      landmarks,
      emotion,
      emotionConfidence: confidence,
      ...avatarIds(),
    };
    if (!send(message)) {
      setSignNotice({
        text: "Not connected yet. Press Reconnect, then sign again — I\u2019m here.",
        at: Date.now(),
      });
      return;
    }
    lastSignFrameAtRef.current = Date.now();
    setCommittedSign(null);
    setSubtitle("Understanding your sign…");
  };

  const toggleSignMode = () => {
    setSignNotice(null);
    setCommittedSign(null);
    setHandPresent(false);
    setSignMode((v) => {
      if (!v) setTextModeOnly(false); // Sign mode needs the camera preview.
      return !v;
    });
  };

  const endSession = () => {
    avatarRef.current?.disconnect();
    queueRef.current?.stop();
    disconnect();
    setLive(false);
    router.push("/dashboard");
  };

  const goLogin = () => {
    clearToken();
    router.replace("/login");
  };

  const thinking = awaiting && !speaking;
  const showMediaBanner = (mediaError || cameraError) && !textModeOnly;
  const connecting = connState === "connecting" || connState === "reconnecting";
  // Sign panel line. The sent label comes from the server TRANSCRIPTION
  // ([Sign: HELP]), never from a frontend guess.
  const signPanelLine =
    committedSign?.sign && awaiting
      ? `Sign sent: ${committedSign.sign} - Saarthi is replying...`
      : signNotice
        ? shortNotice(signNotice.text)
        : signStatus === "loading"
          ? "Starting sign mode…"
          : signStatus === "no-camera"
            ? "Sign mode needs the camera - text and voice still work."
            : signStatus === "model-failed"
              ? "Sign model unavailable - text and voice still work."
              : handPresent
                ? "Hold it steady..."
                : "Show a sign and hold it steady for a moment";

  return (
    <ProtectedRoute>
      <MotionConfig reducedMotion="user">
        <main className="flex min-h-screen flex-col bg-slateDeep text-white">
          <header className="flex items-center justify-between gap-3 px-4 py-3 md:px-6">
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary font-display text-base font-semibold">
                S
              </span>
              <span className="font-display font-semibold">Saarthi</span>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium">
                <span className={`h-2 w-2 rounded-full ${connState === "open" ? "bg-emerald-400" : "bg-amberWarm"}`} />
                {connState === "open" ? "LIVE" : connState.toUpperCase()}
              </span>
            </div>
            <div className="flex items-center gap-3">
              <span className="hidden text-xs text-white/60 md:inline">
                Camera on - video is NOT recorded
              </span>
              <EmotionBadge emotion={badge.emotion} confidence={badge.confidence} live={badgeLive} />
            </div>
          </header>

          <div className="flex flex-1 gap-4 px-4 pb-4 md:px-6">
            <section className="flex flex-1 flex-col items-center" aria-label="Live mentor session">
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.3 }}
                className="flex w-full flex-col items-center"
              >
                <AvatarPlayer
                  ref={avatarRef}
                  isSpeaking={speaking || avatarSpeaking}
                  thinking={thinking}
                  listening={listening}
                  mentor={mentor}
                  onStreamIds={(ids) => {
                    avatarIdsRef.current = ids;
                  }}
                  onPeerLive={(live) => {
                    peerLiveRef.current = live;
                  }}
                />
              </motion.div>

              <p
                aria-live="polite"
                className="mt-4 min-h-12 w-full max-w-3xl rounded-2xl bg-white/5 px-5 py-3 text-center text-lg leading-relaxed"
              >
                {listening ? "Listening…" : subtitle}
              </p>

              {connecting && !connNotice && (
                <p role="status" className="mt-3 text-sm text-white/70">
                  Connecting to Saarthi…
                </p>
              )}
              {softError && (
                <p role="alert" className="mt-3 w-full max-w-3xl rounded-2xl bg-amberWarm/20 px-5 py-3 text-center text-sm">
                  {softError}
                </p>
              )}
              {showMediaBanner && (
                <div
                  role="alert"
                  className="mt-3 flex w-full max-w-3xl flex-col items-center justify-between gap-3 rounded-2xl bg-amberWarm/20 px-5 py-3 text-center sm:flex-row sm:text-left"
                >
                  <p className="text-sm">{mediaError ?? cameraError}</p>
                  <button
                    type="button"
                    onClick={() => setTextModeOnly(true)}
                    className="inline-flex min-h-11 shrink-0 items-center rounded-full bg-white/10 px-5 py-2 text-sm font-medium transition hover:bg-white/20"
                  >
                    Use Text Mode
                  </button>
                </div>
              )}
              {textModeOnly && (
                <p className="mt-3 text-sm text-white/70">
                  Text mode only.{" "}
                  <button
                    type="button"
                    onClick={() => {
                      setTextModeOnly(false);
                      setMediaError(null);
                    }}
                    className="font-medium text-sage underline-offset-4 hover:underline"
                  >
                    Show voice &amp; video again
                  </button>
                </p>
              )}

              {signMode && (
                <div className="mt-4 w-full max-w-3xl rounded-2xl bg-sage/15 px-5 py-3 ring-1 ring-sage/25">
                  <div className="flex items-center justify-between gap-3">
                    <p aria-live="polite" className="text-sm text-white/90">
                      {signPanelLine}
                    </p>
                    <button
                      type="button"
                      aria-expanded={signHelpOpen}
                      onClick={() => setSignHelpOpen((v) => !v)}
                      className="shrink-0 rounded-full bg-white/10 px-4 py-1.5 text-xs font-medium text-white/85 transition hover:bg-white/20"
                    >
                      Which signs can I use?
                    </button>
                  </div>
                  {signHelpOpen && (
                    <ul className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
                      {SIGN_HELP.map((entry) => (
                        <li key={entry.name} className="rounded-xl bg-black/25 px-3.5 py-2.5">
                          <p className="text-xs font-semibold text-sage">{signLabel(entry.name)}</p>
                          <p className="mt-0.5 text-xs leading-relaxed text-white/75">{entry.pose}</p>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              <div className="mt-4 flex w-full max-w-3xl items-end justify-between gap-4">
                {!textModeOnly ? (
                  <div className="relative shrink-0">
                    <EmotionDetector
                      onEmotionChange={handleEmotion}
                      onCameraError={handleCameraError}
                      onVideoReady={(video) => {
                        sharedVideoRef.current = video;
                      }}
                      active={live}
                    />
                    <SignDetector
                      videoRef={sharedVideoRef}
                      enabled={signMode && live}
                      onSignDetected={handleStableSign}
                      onStatusChange={setSignStatus}
                      onError={(message) => {
                        if (message) setSignNotice({ text: message, at: Date.now() });
                      }}
                      onPresenceChange={setHandPresent}
                    />
                  </div>
                ) : (
                  <div className="flex h-32 w-44 shrink-0 items-center justify-center rounded-2xl bg-black/40 px-3 text-center text-xs text-white/60 ring-1 ring-white/10 md:h-36 md:w-52">
                    Camera hidden in text mode
                  </div>
                )}

                <div className="flex flex-1 items-start justify-center gap-3 md:gap-4">
                  {!textModeOnly && (
                    <VoiceRecorder
                      onAudioReady={sendVoice}
                      onError={setMediaError}
                      onRecordingChange={setListening}
                      disabled={awaiting || signMode}
                    />
                  )}
                  <button
                    type="button"
                    aria-label="Type a message"
                    onClick={() => textInputRef.current?.focus()}
                    className="flex h-12 w-12 items-center justify-center rounded-full bg-white/10 transition hover:bg-white/20"
                  >
                    <Keyboard className="h-5 w-5" aria-hidden="true" />
                  </button>
                  <span className="flex flex-col items-center gap-1">
                    <button
                      type="button"
                      aria-label="Use sign language"
                      aria-pressed={signMode}
                      title="Sign language mode - hold a basic sign steady"
                      onClick={toggleSignMode}
                      className={`flex h-12 w-12 items-center justify-center rounded-full transition ${
                        signMode ? "bg-primary hover:bg-primaryDark" : "bg-white/10 hover:bg-white/20"
                      }`}
                    >
                      <Hand className="h-5 w-5" aria-hidden="true" />
                    </button>
                    <span className="max-w-16 text-center text-[10px] leading-tight text-white/60">
                      {signMode ? "Signing on" : "Sign"}
                    </span>
                  </span>
                  <button
                    type="button"
                    aria-label="End session"
                    onClick={endSession}
                    className="flex h-12 w-12 items-center justify-center rounded-full bg-charcoal ring-1 ring-white/20 transition hover:bg-black"
                  >
                    <PhoneOff className="h-5 w-5" aria-hidden="true" />
                  </button>
                </div>
              </div>

              <form
                className="mt-3 flex w-full max-w-3xl gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  sendText();
                }}
              >
                <input
                  ref={textInputRef}
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  placeholder="What’s on your mind today?"
                  aria-label="Type your message"
                  maxLength={1000}
                  className="min-h-12 flex-1 rounded-full bg-white/10 px-5 py-3 text-white outline-none placeholder:text-white/40 focus:ring-2 focus:ring-teal-400"
                />
                <button
                  type="submit"
                  disabled={!text.trim() || awaiting}
                  aria-label="Send message"
                  className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary transition hover:bg-primaryDark disabled:opacity-50"
                >
                  <Send className="h-5 w-5" aria-hidden="true" />
                </button>
              </form>

              <div className="mt-3 w-full max-w-3xl lg:hidden">
                <button
                  type="button"
                  aria-expanded={transcriptOpen}
                  onClick={() => setTranscriptOpen((v) => !v)}
                  className="inline-flex min-h-11 items-center gap-1.5 rounded-full bg-white/10 px-5 py-2 text-sm font-medium"
                >
                  Transcript
                  <ChevronDown
                    className={`h-4 w-4 transition-transform ${transcriptOpen ? "rotate-180" : ""}`}
                    aria-hidden="true"
                  />
                </button>
                {transcriptOpen && (
                  <TranscriptPanel
                    turns={turns}
                    className="mt-2 flex max-h-56 w-full flex-col overflow-hidden rounded-3xl bg-white text-charcoal shadow-sm"
                  />
                )}
              </div>
            </section>

            <TranscriptPanel turns={turns} />
          </div>

          {connNotice && (
            <div className="fixed inset-x-0 bottom-0 z-40 flex justify-center px-4 pb-4" role="alert">
              <div className="flex w-full max-w-xl items-center justify-between gap-3 rounded-2xl bg-charcoal px-5 py-3.5 shadow-xl ring-1 ring-white/15">
                <p className="text-sm">{connNotice}</p>
                {connState === "unauthorized" ? (
                  <button
                    type="button"
                    onClick={goLogin}
                    className="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-sm font-medium hover:bg-primaryDark"
                  >
                    <LogIn className="h-4 w-4" aria-hidden="true" /> Log in
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={connect}
                    className="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-sm font-medium hover:bg-primaryDark"
                  >
                    <RotateCcw className="h-4 w-4" aria-hidden="true" /> Reconnect
                  </button>
                )}
              </div>
            </div>
          )}

          {crisis !== null && (
            <CrisisModal
              message={crisis}
              onContinue={() => {
                setCrisis(null);
                queueRef.current?.reset();
              }}
            />
          )}
        </main>
      </MotionConfig>
    </ProtectedRoute>
  );
}
