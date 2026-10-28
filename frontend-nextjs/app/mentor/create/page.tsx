"use client";

import { motion } from "framer-motion";
import { ArrowLeft, Check, ImagePlus, Loader2, Video } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import ProtectedRoute from "@/components/ProtectedRoute";
import { ApiError } from "@/lib/api";
import { uploadAvatar } from "@/lib/mentor";
import type { MentorProfile } from "@/lib/types";

const STEPS = ["Uploading photo", "Creating your mentor", "Adding voice", "Ready"] as const;
const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPTED = ["image/jpeg", "image/png", "image/webp"];
const VOICES = [
  { value: "en-IN-NeerjaNeural", label: "Female (en-IN-NeerjaNeural)" },
  { value: "hi-IN-SwaraNeural", label: "Female Hindi (hi-IN-SwaraNeural)" },
  { value: "en-IN-PrabhatNeural", label: "Male (en-IN-PrabhatNeural)" },
] as const;

export default function MentorCreatePage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [voiceId, setVoiceId] = useState<string>(VOICES[0].value);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [step, setStep] = useState<number>(-1);
  const [done, setDone] = useState<MentorProfile | null>(null);
  const timers = useRef<number[]>([]);

  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach((t) => window.clearTimeout(t));
      if (preview) URL.revokeObjectURL(preview);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const pickFile = (chosen: File | null) => {
    setError(null);
    if (preview) URL.revokeObjectURL(preview);
    if (!chosen) {
      setFile(null);
      setPreview(null);
      return;
    }
    if (!ACCEPTED.includes(chosen.type)) {
      setFile(null);
      setPreview(null);
      setError("That file won't work — please choose a JPG, PNG, or WebP photo.");
      return;
    }
    if (chosen.size > MAX_BYTES) {
      setFile(null);
      setPreview(null);
      setError("That photo is too large — please use one under 5MB.");
      return;
    }
    setFile(chosen);
    setPreview(URL.createObjectURL(chosen));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file || step >= 0) return;
    setError(null);
    setStep(0);
    try {
      const profile = await uploadAvatar(file, name.trim() || "Saarthi", voiceId);
      // The server work is one request; walk the remaining steps briefly so the
      // progress reads honestly as stages of that single creation.
      setStep(1);
      await new Promise<void>((resolve) => {
        timers.current.push(window.setTimeout(() => resolve(), 500));
      });
      setStep(2);
      await new Promise<void>((resolve) => {
        timers.current.push(window.setTimeout(() => resolve(), 500));
      });
      setStep(3);
      setDone(profile);
    } catch (err) {
      setStep(-1);
      setError(
        err instanceof ApiError
          ? err.message
          : "Your mentor couldn't be created right now — please try again in a moment."
      );
    }
  };

  return (
    <ProtectedRoute>
      <main className="min-h-screen bg-calm">
        <div className="mx-auto max-w-2xl px-6 py-8">
          <Link
            href="/dashboard"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-charcoal/70 hover:text-charcoal"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to dashboard
          </Link>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
            className="mt-4 rounded-3xl border border-slate-100 bg-white p-8 shadow-sm"
          >
            {done ? (
              <div className="text-center">
                <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary/15">
                  <Check className="h-7 w-7 text-primary" aria-hidden="true" />
                </div>
                <h1 className="mt-4 font-display text-2xl font-semibold text-charcoal">
                  Your Mentor is Ready!
                </h1>
                <p className="mt-2 text-charcoal/70">
                  {done.mentorName} will greet you in your next live session.
                </p>
                {done.status !== "READY" && (
                  <p role="status" className="mx-auto mt-3 max-w-sm rounded-2xl bg-amberWarm/25 px-4 py-3 text-sm text-charcoal">
                    Your mentor photo is saved. Live lip-sync will activate on the deployed
                    version - voice mode is ready.
                  </p>
                )}
                {preview && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={preview}
                    alt={`${done.mentorName}, your mentor`}
                    className="mx-auto mt-5 h-40 w-40 rounded-3xl object-cover shadow-md"
                  />
                )}
                <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
                  <button
                    type="button"
                    onClick={() => router.push("/session")}
                    className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-primary px-7 py-3 font-medium text-white shadow-lg transition hover:bg-primaryDark"
                  >
                    <Video className="h-5 w-5" aria-hidden="true" /> Start Talking to Your Mentor
                  </button>
                  <button
                    type="button"
                    onClick={() => router.push("/dashboard")}
                    className="inline-flex min-h-12 items-center justify-center rounded-full bg-slate-100 px-7 py-3 font-medium text-charcoal transition hover:bg-sage/40"
                  >
                    Back to Dashboard
                  </button>
                </div>
              </div>
            ) : (
              <>
                <h1 className="font-display text-2xl font-semibold text-charcoal">
                  Create your mentor
                </h1>
                <p className="mt-2 text-sm leading-relaxed text-mist">
                  Use a clear, front-facing photo with good light. Max 5MB, JPG/PNG.
                </p>

                <ol className="mt-6 space-y-2.5" aria-label="Creation progress">
                  {STEPS.map((label, i) => (
                    <li key={label} className="flex items-center gap-3 text-sm">
                      <span
                        aria-hidden="true"
                        className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold ${
                          step > i
                            ? "bg-primary text-white"
                            : step === i
                              ? "bg-amberWarm/40 text-charcoal"
                              : "bg-slate-100 text-mist"
                        }`}
                      >
                        {step > i ? <Check className="h-3.5 w-3.5" /> : i + 1}
                      </span>
                      <span className={step === i ? "font-medium text-charcoal" : "text-mist"}>
                        {label}
                        {step === i ? "…" : ""}
                      </span>
                    </li>
                  ))}
                </ol>

                <form onSubmit={submit} className="mt-6 space-y-4">
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-charcoal">Mentor name</span>
                    <input
                      type="text"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="My Teacher"
                      maxLength={100}
                      className="w-full rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none placeholder:text-mist focus:ring-2 focus:ring-teal-400"
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-charcoal">Voice</span>
                    <select
                      value={voiceId}
                      onChange={(e) => setVoiceId(e.target.value)}
                      className="w-full appearance-none rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none focus:ring-2 focus:ring-teal-400"
                    >
                      {VOICES.map((v) => (
                        <option key={v.value} value={v.value}>
                          {v.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div>
                    <span className="mb-1 block text-sm font-medium text-charcoal">Photo</span>
                    <label
                      onDragOver={(e) => e.preventDefault()}
                      onDrop={(e) => {
                        e.preventDefault();
                        pickFile(e.dataTransfer.files?.[0] ?? null);
                      }}
                      className={`flex cursor-pointer items-center gap-4 rounded-3xl border-2 border-dashed p-4 transition ${
                        preview ? "border-primary/40" : "border-slate-200 hover:border-sage"
                      }`}
                    >
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        className="sr-only"
                        aria-label="Mentor photo"
                        onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
                      />
                      {preview ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={preview}
                          alt="Chosen mentor photo preview"
                          className="h-20 w-20 rounded-2xl object-cover"
                        />
                      ) : (
                        <span className="flex h-20 w-20 items-center justify-center rounded-2xl bg-slate-100">
                          <ImagePlus className="h-8 w-8 text-mist" aria-hidden="true" />
                        </span>
                      )}
                      <span className="text-sm text-mist">
                        {preview
                          ? "Looking good — tap or drop to choose another."
                          : "Tap to choose, or drag a photo here."}
                      </span>
                    </label>
                  </div>
                  {error && (
                    <p role="alert" className="rounded-2xl bg-amberWarm/25 px-4 py-3 text-sm text-charcoal">
                      {error}
                    </p>
                  )}
                  <button
                    type="submit"
                    disabled={!file || step >= 0}
                    className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-primary px-6 py-3 font-medium text-white shadow-md transition hover:bg-primaryDark disabled:opacity-60"
                  >
                    {step >= 0 && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                    {step >= 0 ? "Creating your mentor…" : "Create mentor"}
                  </button>
                </form>
              </>
            )}
          </motion.div>
        </div>
      </main>
    </ProtectedRoute>
  );
}
