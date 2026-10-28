"use client";

import { Hand } from "lucide-react";
import { useEffect, useRef } from "react";

export interface Turn {
  id: number;
  role: "user" | "mentor" | "note";
  text: string;
}

/** Live text transcript. Desktop side panel, or a collapsible bottom sheet on mobile. */
export default function TranscriptPanel({
  turns,
  className,
}: {
  turns: Turn[];
  className?: string;
}) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns.length]);

  return (
    <aside
      aria-label="Conversation transcript"
      className={
        className ?? "hidden w-80 shrink-0 flex-col overflow-hidden rounded-3xl bg-white shadow-sm lg:flex"
      }
    >
      <h2 className="border-b border-slate-100 px-5 py-4 font-display text-base font-semibold text-charcoal">
        Transcript
      </h2>
      <div className="flex-1 space-y-3 overflow-y-auto px-5 py-4">
        {turns.length === 0 && (
          <p className="text-sm text-mist">
            Your conversation will appear here. Saarthi is here when you&apos;re ready.
          </p>
        )}
        {turns.map((turn) =>
          turn.role === "note" ? (
            <p key={turn.id} className="text-center text-xs italic text-mist">
              {turn.text}
            </p>
          ) : (
            <div
              key={turn.id}
              className={`max-w-full rounded-2xl px-4 py-2.5 text-sm leading-relaxed ${
                turn.role === "user"
                  ? "ml-8 bg-primary/10 text-charcoal"
                  : "mr-8 bg-calm text-charcoal"
              }`}
            >
              {turn.role === "user" && turn.text.startsWith("[Sign: ") && (
                <Hand
                  className="mr-1.5 inline h-3.5 w-3.5 text-primary"
                  aria-label="Sign language message"
                />
              )}
              {turn.text}
            </div>
          )
        )}
        <div ref={bottomRef} />
      </div>
    </aside>
  );
}
