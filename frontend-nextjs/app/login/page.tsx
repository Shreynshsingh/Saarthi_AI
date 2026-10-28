"use client";

import { motion } from "framer-motion";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { ApiError, isLoggedIn, login, saveSession } from "@/lib/api";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState(params.get("email") ?? "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const justRegistered = params.get("registered") === "1";

  useEffect(() => {
    if (isLoggedIn()) router.replace("/dashboard");
  }, [router]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
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
          Welcome back
        </h1>
        <p className="mt-1 text-center text-sm text-mist">Your mentor is glad to see you.</p>
        {justRegistered && (
          <p role="status" className="mt-4 rounded-2xl bg-primary/10 px-4 py-3 text-center text-sm text-charcoal">
            Account created. Please log in to begin.
          </p>
        )}
        <form onSubmit={submit} className="mt-6 space-y-4">
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
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Your password"
              className="w-full rounded-full bg-slate-100 px-5 py-3 text-charcoal outline-none placeholder:text-mist focus:ring-2 focus:ring-teal-400"
            />
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
            {busy ? "Logging in…" : "Log in"}
          </button>
        </form>
        <p className="mt-5 text-center text-sm text-mist">
          New here?{" "}
          <Link href="/register" className="font-medium text-primary hover:underline">
            Create an account
          </Link>
        </p>
      </motion.div>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
