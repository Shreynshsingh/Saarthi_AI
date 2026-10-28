/** Wire contract with the Java backend (Modules 1–3). Names match exactly. */

export type InputMode = 'TEXT' | 'VOICE' | 'SIGN';
export type MoodType = 'HAPPY' | 'SAD' | 'ANXIOUS' | 'STRESSED' | 'TIRED' | 'NEUTRAL' | 'ANGRY';

// Module 6: the 10 static poses the Python classifier recognizes. Labels are
// assigned server-side only - the frontend never maps poses to names.
export type SignName =
  | 'HELLO' | 'THANK_YOU' | 'YES' | 'NO' | 'PLEASE'
  | 'SAD' | 'HAPPY' | 'STRESS' | 'HELP' | 'I_NEED_HELP';

export interface SignDetection {
  sign: SignName | null;
  confidence: number;
}

// Module 8: dashboard mood + memory shapes. Timestamps are ISO strings.
export interface MoodHistoryEntry {
  id: string;
  mood: MoodType;
  confidence: number | null;
  note: string | null;
  loggedAt: string | null;
  conversationId: string | null;
}

export interface MoodInsight {
  insight: string;
}

export interface ConversationSummary {
  id: string;
  title: string;
  startedAt: string | null;
  preview: string;
}

export interface ChatRequest {
  type: 'USER_MESSAGE';
  conversationId: string | null;
  inputMode: InputMode;
  text?: string;
  audioBase64?: string;
  emotion: MoodType;
  emotionConfidence: number;
  signDetected?: string | null;
  // Module 6: 21 MediaPipe [x, y, z] triples per SIGN input frame.
  // Only numbers cross the wire - never video, never images.
  landmarks?: number[][] | null;
  avatarStreamId?: string | null;
  avatarSessionId?: string | null;
}

export interface ChatResponse {
  type: 'TRANSCRIPTION' | 'MENTOR_RESPONSE_CHUNK' | 'MENTOR_RESPONSE_END' | 'CRISIS_ALERT' | 'ERROR';
  textChunk?: string;
  transcribedText?: string;
  audioBase64?: string | null;
  conversationId?: string;
  isFinal?: boolean;
  message?: string;
  avatarSpeaking?: boolean | null;
}

export interface MentorProfile {
  mentorProfileId: string;
  mentorName: string;
  avatarImageUrl: string | null;
  avatarProviderId: string | null;
  status: string;
}

export interface IceServer {
  urls: string | string[];
  username?: string;
  credential?: string;
}

export interface AvatarStreamResponse {
  provider: 'did' | 'static';
  streamId?: string | null;
  sessionId?: string | null;
  sdpOffer?: string | null;
  iceServers?: IceServer[] | null;
  fallbackReason?: string | null;
}

export interface AuthResponse {
  token: string;
  userId: string;
  name: string;
}

/** Human-readable text of a frame, whatever alias the backend used. */
export function frameText(frame: ChatResponse): string {
  return frame.textChunk ?? frame.transcribedText ?? frame.message ?? "";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

const KNOWN_TYPES = new Set([
  "TRANSCRIPTION",
  "MENTOR_RESPONSE_CHUNK",
  "MENTOR_RESPONSE_END",
  "CRISIS_ALERT",
  "ERROR",
]);

export function parseChatResponse(raw: string): ChatResponse | null {
  try {
    const data: unknown = JSON.parse(raw);
    if (!isRecord(data) || typeof data.type !== "string") return null;
    if (!KNOWN_TYPES.has(data.type)) return null;
    return data as unknown as ChatResponse;
  } catch {
    return null;
  }
}
