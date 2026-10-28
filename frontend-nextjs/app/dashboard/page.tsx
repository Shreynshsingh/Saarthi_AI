"use client";

import { motion } from "framer-motion";
import { Frown, History, LogOut, Meh, MessageCircleHeart, ShieldCheck, Smile, Video, Zap } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import ProtectedRoute from "@/components/ProtectedRoute";
import { clearToken, getUserName } from "@/lib/api";
import { getRecentConversations } from "@/lib/conversations";
import { getMyMentor } from "@/lib/mentor";
import { checkinMood, getMoodHistory, getMoodInsight } from "@/lib/mood";
import type { ConversationSummary, MentorProfile, MoodHistoryEntry, MoodType } from "@/lib/types";

const CHECKINS = [
  { label: "Happy", mood: "HAPPY", icon: Smile },
  { label: "Neutral", mood: "NEUTRAL", icon: Meh },
  { label: "Low", mood: "SAD", icon: Frown },
  { label: "Stressed", mood: "STRESSED", icon: Zap },
] as const;

const MOOD_COLORS: Record<MoodType, string> = {
  HAPPY: "#81C784",
  SAD: "#64B5F6",
  STRESSED: "#E9C46A",
  ANXIOUS: "#4DB6AC",
  TIRED: "#90A4AE",
  NEUTRAL: "#CFD8DC",
  ANGRY: "#E76F51",
};

const MOOD_WORDS: Record<MoodType, string> = {
  HAPPY: "happy",
  SAD: "low",
  STRESSED: "stressed",
  ANXIOUS: "anxious",
  TIRED: "tired",
  NEUTRAL: "neutral",
  ANGRY: "angry",
};

interface DayColumn {
  key: string;
  label: string;
  mood: MoodType | null;
  count: number;
}

function dayKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

function isSameDay(a: Date, b: Date): boolean {
  return dayKey(a) === dayKey(b);
}

/** Last 7 local days, oldest first, each with its dominant mood (ties: latest). */
function buildWeek(entries: MoodHistoryEntry[]): DayColumn[] {
  const today = new Date();
  const days: DayColumn[] = [];
  for (let back = 6; back >= 0; back--) {
    const date = new Date(today);
    date.setDate(today.getDate() - back);
    days.push({
      key: dayKey(date),
      label: date.toLocaleDateString(undefined, { weekday: "short" }),
      mood: null,
      count: 0,
    });
  }
  const byDay = new Map<string, MoodHistoryEntry[]>();
  for (const entry of entries) {
    if (!entry.loggedAt) continue;
    const at = new Date(entry.loggedAt);
    if (Number.isNaN(at.getTime())) continue;
    const list = byDay.get(dayKey(at)) ?? [];
    list.push(entry);
    byDay.set(dayKey(at), list);
  }
  for (const day of days) {
    const list = byDay.get(day.key) ?? [];
    if (list.length === 0) continue;
    const counts = new Map<MoodType, number>();
    for (const entry of list) counts.set(entry.mood, (counts.get(entry.mood) ?? 0) + 1);
    let best: MoodType = list[0].mood;
    let bestCount = -1;
    for (const entry of list) {
      const count = counts.get(entry.mood) ?? 0;
      if (count > bestCount) {
        best = entry.mood;
        bestCount = count;
      }
    }
    day.mood = best;
    day.count = list.length;
  }
  return days;
}

function timeGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good Morning";
  if (hour < 17) return "Good Afternoon";
  return "Good Evening";
}

function moodLine(latest: MoodHistoryEntry | null): string | null {
  if (!latest?.loggedAt) return null;
  const at = new Date(latest.loggedAt);
  if (Number.isNaN(at.getTime())) return null;
  const word = MOOD_WORDS[latest.mood] ?? "okay";
  const now = new Date();
  if (isSameDay(at, now)) return `You're feeling ${word} today.`;
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (isSameDay(at, yesterday)) return `You felt ${word} yesterday. Want to talk today?`;
  return `Last time, you felt ${word}.`;
}

export default function DashboardPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [mood, setMood] = useState<string | null>(null);
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkinError, setCheckinError] = useState<string | null>(null);
  const [mentor, setMentor] = useState<MentorProfile | null>(null);
  const [history, setHistory] = useState<MoodHistoryEntry[] | null>(null);
  const [historyError, setHistoryError] = useState(false);
  const [insight, setInsight] = useState<string | null>(null);
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);

  useEffect(() => {
    setName(getUserName() ?? "");
    let cancelled = false;
    void getMyMentor()
      .then((profile) => {
        if (!cancelled) setMentor(profile);
      })
      .catch(() => undefined);
    void getMoodHistory(7)
      .then((entries) => {
        if (!cancelled) setHistory(entries);
      })
      .catch(() => {
        if (!cancelled) {
          setHistory([]);
          setHistoryError(true);
        }
      });
    void getMoodInsight()
      .then((result) => {
        if (!cancelled) setInsight(result.insight);
      })
      .catch(() => {
        if (!cancelled) setInsight("Every day you show up here matters.");
      });
    void getRecentConversations(10)
      .then((rows) => {
        if (!cancelled) setConversations(rows);
      })
      .catch(() => {
        if (!cancelled) setConversations([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const logout = () => {
    clearToken();
    router.replace("/login");
  };

  const checkin = async (label: string, value: MoodType) => {
    if (checkingIn) return;
    setCheckingIn(true);
    setCheckinError(null);
    try {
      await checkinMood(value);
      setMood(label);
      const [entries, fresh] = await Promise.all([getMoodHistory(7), getMoodInsight()]);
      setHistory(entries);
      setInsight(fresh.insight);
    } catch {
      setCheckinError("Couldn't save that check-in. Please try again.");
    } finally {
      setCheckingIn(false);
    }
  };

  const week = history === null ? null : buildWeek(history);
  const line = history === null ? null : moodLine(history[0] ?? null);

  return (
    <ProtectedRoute>
      <main className="min-h-screen bg-calm">
        <header className="mx-auto flex max-w-4xl items-center justify-between px-6 py-5">
          <span className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary font-display text-xl font-semibold text-white">
              {(name.trim()[0] ?? "S").toUpperCase()}
            </span>
            <span>
              <span className="block font-display text-xl font-semibold text-charcoal">
                {timeGreeting()}
                {name ? `, ${name}` : ""}
              </span>
              {line && <span className="block text-sm text-mist">{line}</span>}
            </span>
          </span>
          <button
            type="button"
            onClick={logout}
            className="inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium text-charcoal hover:bg-white"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" /> Logout
          </button>
        </header>

        <motion.section
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.3 }}
          className="mx-auto max-w-4xl space-y-6 px-6 pb-16"
        >
          <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-sm">
            <h2 className="font-display text-lg font-semibold text-charcoal">
              How are you feeling today?
            </h2>
            <p className="mt-1 text-sm text-mist">Just between us — a gentle check-in.</p>
            <div className="mt-4 flex flex-wrap gap-3" role="group" aria-label="Mood check-in">
              {CHECKINS.map(({ label, mood: value, icon: Icon }) => (
                <button
                  key={label}
                  type="button"
                  aria-pressed={mood === label}
                  disabled={checkingIn}
                  onClick={() => void checkin(label, value)}
                  className={`inline-flex min-h-12 items-center gap-2 rounded-full px-5 py-3 text-sm font-medium transition disabled:opacity-60 ${
                    mood === label
                      ? "bg-primary text-white shadow-md"
                      : "bg-slate-100 text-charcoal hover:bg-sage/40"
                  }`}
                >
                  <Icon className="h-4 w-4" aria-hidden="true" /> {label}
                </button>
              ))}
            </div>
            {checkinError && (
              <p role="alert" className="mt-3 text-sm text-charcoal">
                {checkinError}
              </p>
            )}
          </div>

          <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-sm">
            <h2 className="font-display text-lg font-semibold text-charcoal">Your week</h2>
            <p className="mt-1 text-sm text-mist">Your most frequent mood each day.</p>
            {week === null ? (
              <div className="mt-4 flex h-28 items-end gap-3" aria-label="Loading mood history">
                {[0, 1, 2, 3, 4, 5, 6].map((i) => (
                  <span key={i} className="flex-1 animate-pulse rounded-t-xl bg-slate-100" style={{ height: `${30 + ((i * 37) % 60)}%` }} />
                ))}
              </div>
            ) : week.every((day) => day.mood === null) ? (
              <p className="mt-4 text-sm text-mist">
                {historyError
                  ? "Couldn't load your mood history right now. Please try again later."
                  : "No moods yet — check in above or finish a session, and your week will appear here."}
              </p>
            ) : (
              <div
                className="mt-4 flex h-32 items-end gap-2 sm:gap-3"
                role="img"
                aria-label={`Weekly moods: ${week
                  .map((day) => `${day.label} ${day.mood ? MOOD_WORDS[day.mood] : "no data"}`)
                  .join(", ")}`}
              >
                {week.map((day) => (
                  <div key={day.key} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
                    <span
                      className="w-full max-w-10 rounded-t-xl"
                      style={{
                        height: day.mood === null ? "8%" : `${Math.min(100, 22 + day.count * 16)}%`,
                        backgroundColor: day.mood === null ? "#EEF2F2" : MOOD_COLORS[day.mood],
                      }}
                    />
                    <span className="text-xs text-mist">{day.label}</span>
                  </div>
                ))}
              </div>
            )}
            {insight !== null && (
              <div className="mt-5 rounded-2xl bg-calm px-5 py-4">
                <p className="flex items-start gap-2 text-sm leading-relaxed text-charcoal">
                  <MessageCircleHeart className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  {insight}
                </p>
              </div>
            )}
          </div>

          <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-sm">
            <h2 className="flex items-center gap-2 font-display text-lg font-semibold text-charcoal">
              <History className="h-5 w-5 text-primary" aria-hidden="true" /> Recent conversations
            </h2>
            {conversations === null ? (
              <div className="mt-4 space-y-3" aria-label="Loading conversations">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-14 animate-pulse rounded-2xl bg-slate-100" />
                ))}
              </div>
            ) : conversations.length === 0 ? (
              <p className="mt-3 text-sm text-mist">
                Your first conversation is waiting. Saarthi is here when you&apos;re ready.
              </p>
            ) : (
              <ul className="mt-4 space-y-3">
                {conversations.map((row) => (
                  <li key={row.id} className="rounded-2xl bg-calm px-5 py-3.5">
                    <p className="text-sm font-medium text-charcoal">
                      {row.title || "Conversation"}
                      {row.startedAt && (
                        <span className="ml-2 font-normal text-mist">
                          {new Date(row.startedAt).toLocaleDateString(undefined, {
                            month: "short",
                            day: "numeric",
                          })}
                        </span>
                      )}
                    </p>
                    {row.preview && (
                      <p className="mt-0.5 truncate text-sm text-mist">{row.preview}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="overflow-hidden rounded-3xl bg-slateDark text-white shadow-sm">
            <div className="flex flex-col items-center gap-6 p-8 md:flex-row md:p-10">
              {mentor?.avatarImageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={mentor.avatarImageUrl}
                  alt={`${mentor.mentorName}, your mentor`}
                  className="h-36 w-36 shrink-0 rounded-3xl object-cover ring-1 ring-white/10"
                />
              ) : (
                <div
                  className="flex h-36 w-36 shrink-0 items-center justify-center rounded-3xl bg-gradient-to-b from-slateDeep to-charcoal font-display text-6xl font-semibold text-sage ring-1 ring-white/10"
                  aria-label="Your mentor preview"
                >
                  S
                </div>
              )}
              <div className="text-center md:text-left">
                <p className="text-sm uppercase tracking-wide text-sage">Your live mentor</p>
                <h1 className="mt-2 font-display text-3xl font-semibold">Ready when you are.</h1>
                <p className="mt-3 max-w-lg leading-relaxed text-white/75">
                  Talk out loud, type quietly, or sign — Saarthi listens in Hindi, English, and
                  Hinglish, and always answers with a voice you can hear.
                </p>
                <button
                  type="button"
                  onClick={() => router.push("/session")}
                  className="mt-6 inline-flex items-center gap-2 rounded-full bg-primary px-7 py-3.5 font-medium text-white shadow-lg transition hover:bg-primaryDark"
                >
                  <Video className="h-5 w-5" aria-hidden="true" /> Start Live Session
                </button>
                <div className="mt-3">
                  <button
                    type="button"
                    onClick={() => router.push("/mentor/create")}
                    className="mt-3 inline-flex min-h-11 items-center rounded-full bg-white/10 px-5 py-2 text-sm font-medium text-white transition hover:bg-white/20"
                  >
                    Create / Change My Mentor
                  </button>
                </div>
              </div>
            </div>
          </div>

          <p className="flex items-start gap-2 text-sm text-mist">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
            Camera is on only inside the session, and video is never recorded — your expressions
            are read right here in the browser.
          </p>
        </motion.section>
      </main>
    </ProtectedRoute>
  );
}
