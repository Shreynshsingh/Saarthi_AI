"use client";

import { motion } from "framer-motion";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, isLoggedIn, login, register, saveSession } from "@/lib/api";

const LANGUAGES = ["Hinglish", "Hindi", "English"] as const;

export default function RegisterPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [preferredLanguage, setPreferredLanguage] = useState<string>(LANGUAGES[0]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (isLoggedIn()) router.replace("/dashboard");
  }, [router]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (password.length < 8) {
      setError("Please choose a password with at least 8 characters.");
      return;
    }
    setBusy(true);
    try {
      await register(name.trim(), email.trim(), password, preferredLanguage);
      const data = await login(email.trim(), password);
      saveSession(data.token, data.name);
      router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Please try again in a moment.");
      setBusy(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-calm px-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3 }}
        className="w-full max-w-md rounded-3xl border border-slate-100 bg-white p-8 shadow-sm"
      >
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary font-display text-2xl font-semibold text-white">
          S
        </div>
        <h1 className="mt-4 text-center font-display text-2xl font-semibold text-charcoal">
          Join Saarthi
        </h1>
        <p className="mt-1 text-center text-sm text-mist">A safe space is waiting for you.</p>
        <form onSubmit={submit} className="mt-6 space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-charcoal">Your name</span>
            <input
              type="text"
              required
              autoComplete="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="What should your mentor call you?"
              className="w-full rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none placeholder:text-mist focus:ring-2 focus:ring-teal-400"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-charcoal">Email</span>
            <input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none placeholder:text-mist focus:ring-2 focus:ring-teal-400"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-charcoal">Password</span>
            <input
              type="password"
              required
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 characters"
              className="w-full rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none placeholder:text-mist focus:ring-2 focus:ring-teal-400"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-charcoal">Preferred language</span>
            <select
              value={preferredLanguage}
              onChange={(e) => setPreferredLanguage(e.target.value)}
              className="w-full appearance-none rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none focus:ring-2 focus:ring-teal-400"
            >
              {LANGUAGES.map((lang) => (
                <option key={lang} value={lang}>
                  {lang}
                </option>
              ))}
            </select>
          </label>
          {error && (
            <p role="alert" className="rounded-2xl bg-amberWarm/25 px-4 py-3 text-sm text-charcoal">
              {error}
            </p>
          )}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-full bg-primary px-6 py-3 font-medium text-white shadow-md transition hover:bg-primaryDark disabled:opacity-60"
          >
            {busy ? "Creating your space…" : "Create account"}
          </button>
        </form>
        <p className="mt-5 text-center text-sm text-mist">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-primary hover:underline">
            Log in
          </Link>
        </p>
      </motion.div>
    </main>
  );
}
