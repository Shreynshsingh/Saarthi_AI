"use client";

import { motion } from "framer-motion";
import { HeartHandshake, Phone } from "lucide-react";

/**
 * Calm crisis overlay per 05 section 7.6. Soft colors only — never alarming
 * red. Numbers are plain tel: links so they work on every device.
 */
export default function CrisisModal({
  message,
  onContinue,
}: {
  message: string;
  onContinue: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="crisis-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slateDeep/70 p-4"
    >
      <div className="w-full max-w-md rounded-3xl border border-slate-100 bg-white p-8 text-center shadow-xl">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-amberWarm/30">
          <HeartHandshake className="h-7 w-7 text-charcoal" aria-hidden="true" />
        </div>
        <h2 id="crisis-title" className="font-display text-2xl font-semibold text-charcoal">
          I&apos;m really glad you told me this.
        </h2>
        <p className="mt-3 text-charcoal/80">{message}</p>
        <p className="mt-2 text-sm text-mist">
          You don&apos;t have to go through this alone. Please talk to someone who can help right now.
        </p>
        <div className="mt-6 flex flex-col gap-3">
          <a
            href="tel:14416"
            className="inline-flex items-center justify-center gap-2 rounded-full bg-primary px-6 py-3 font-medium text-white shadow-md transition hover:bg-primaryDark"
          >
            <Phone className="h-4 w-4" aria-hidden="true" /> Call Tele-MANAS: 14416
          </a>
          <a
            href="tel:9820466726"
            className="inline-flex items-center justify-center gap-2 rounded-full bg-charcoal px-6 py-3 font-medium text-white shadow-md transition hover:bg-slateDeep"
          >
            <Phone className="h-4 w-4" aria-hidden="true" /> Call AASRA: 9820466726
          </a>
          <button
            type="button"
            onClick={onContinue}
            className="mt-1 inline-block min-h-11 px-4 py-2 text-sm font-medium text-primary underline-offset-4 hover:underline"
          >
            I am safe, continue
          </button>
        </div>
      </div>
    </motion.div>
  );
}
