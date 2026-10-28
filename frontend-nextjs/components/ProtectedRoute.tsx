"use client";

import { useRouter } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";
import { isLoggedIn } from "@/lib/api";

/** Client-side route guard: no JWT, no entry. Redirects to /login. */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [allowed, setAllowed] = useState<boolean | null>(null);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.replace("/login");
      setAllowed(false);
      return;
    }
    setAllowed(true);
  }, [router]);

  if (allowed !== true) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-calm">
        <p className="font-display text-lg text-charcoal">Finding your space…</p>
      </main>
    );
  }
  return <>{children}</>;
}
