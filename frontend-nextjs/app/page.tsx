"use client";

import { motion } from "framer-motion";
import { ArrowRight, Eye, Hand, Mic, ShieldCheck, Volume2 } from "lucide-react";
import Link from "next/link";

const STEPS = [
  {
    n: "1",
    title: "Start a live session",
    body: "One click from your dashboard. Your mentor greets you by name and picks up where you left off.",
  },
  {
    n: "2",
    title: "Talk, type, or sign",
    body: "Speak in English, Hindi, or Hinglish. Type when it's late at night. Sign when words aren't your first language.",
  },
  {
    n: "3",
    title: "Be heard, feel lighter",
    body: "Saarthi notices how you seem — not just what you say — and answers like a mentor, in your language.",
  },
];

const SENSES = [
  { icon: Eye, label: "See — your expressions, read privately in this browser" },
  { icon: Mic, label: "Hear — your voice in Hindi, English, and Hinglish" },
  { icon: Hand, label: "Understand — your signs and what they mean" },
  { icon: Volume2, label: "Talk back — with a voice you can actually hear" },
];

export default function LandingPage() {
  return (
    <main className="min-h-screen bg-calm">
      <header className="mx-auto flex max-w-5xl items-center justify-between px-6 py-5">
        <Link href="/" className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary font-display text-xl font-semibold text-white">
            S
          </span>
          <span className="font-display text-xl font-semibold text-charcoal">Saarthi AI</span>
        </Link>
        <nav className="flex items-center gap-3">
          <Link href="/login" className="rounded-full px-5 py-2.5 text-sm font-medium text-charcoal hover:bg-white">
            I already have an account
          </Link>
          <Link
            href="/register"
            className="rounded-full bg-primary px-5 py-2.5 text-sm font-medium text-white shadow-md transition hover:bg-primaryDark"
          >
            Start Talking
          </Link>
        </nav>
      </header>

      <motion.section
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3 }}
        className="mx-auto max-w-5xl px-6 pb-16 pt-10"
      >
        <div className="max-w-2xl">
          <h1 className="font-display text-4xl font-semibold leading-tight text-charcoal md:text-5xl">
            Feeling lost? Talk to a mentor who truly listens.
          </h1>
          <p className="mt-5 text-lg leading-relaxed text-charcoal/75">
            Saarthi sees your expressions, hears your voice, understands your signs, and talks
            back — face to face, in your language.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-4">
            <Link
              href="/register"
              className="inline-flex items-center gap-2 rounded-full bg-primary px-7 py-3.5 font-medium text-white shadow-lg transition hover:bg-primaryDark"
            >
              Start Talking <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
              I already have an account
            </Link>
          </div>
        </div>

        <ul className="mt-12 space-y-3">
          {SENSES.map(({ icon: Icon, label }) => (
            <li key={label} className="flex items-center gap-3 text-charcoal/80">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-sage/30">
                <Icon className="h-4 w-4 text-primaryDark" aria-hidden="true" />
              </span>
              {label}
            </li>
          ))}
        </ul>

        <section aria-label="How it works" className="mt-16">
          <h2 className="font-display text-2xl font-semibold text-charcoal">How it works</h2>
          <ol className="mt-6 divide-y divide-slate-200/70 border-y border-slate-200/70">
            {STEPS.map((step) => (
              <li key={step.n} className="flex gap-5 py-6">
                <span className="font-display text-3xl font-semibold text-sage">{step.n}</span>
                <div>
                  <h3 className="font-display text-lg font-semibold text-charcoal">{step.title}</h3>
                  <p className="mt-1 max-w-xl leading-relaxed text-charcoal/70">{step.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className="mt-12 flex items-start gap-4 rounded-3xl border border-slate-100 bg-white p-6 shadow-sm">
          <ShieldCheck className="mt-1 h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
          <div>
            <h2 className="font-display text-lg font-semibold text-charcoal">Private by design</h2>
            <p className="mt-1 leading-relaxed text-charcoal/70">
              Your camera feed never leaves this browser — emotion is detected on your device and
              only the label travels to the server. Conversations are yours, protected by your
              login, and you can end a session any time.
            </p>
          </div>
        </section>

        <footer className="mt-12 border-t border-slate-200/70 pt-6 text-sm text-mist">
          Saarthi AI — your mentor who sees, hears, and understands you.
        </footer>
      </motion.section>
    </main>
  );
}
