"use client";

import type { SignName } from "./types";

/**
 * Sign help text for the session page ("Which signs can I use?").
 *
 * DOCUMENTATION ONLY - the real 10-sign rule definitions live in exactly one
 * place: ai-service-python/sign_classifier.py. The pose strings below are
 * copied word-for-word from that file's DEMO CHEAT SHEET block; if the
 * classifier wording ever changes, update this file to match.
 */

export interface SignHelpEntry {
  name: SignName;
  pose: string;
}

export const SIGN_HELP: readonly SignHelpEntry[] = [
  {
    name: "HELLO",
    pose: "Open palm facing the camera, fingers together, thumb out to the side. Hold at CHEST height (wrist in lower half of the frame). This is the DEFAULT open-palm label.",
  },
  {
    name: "HELP",
    pose: "Same open palm, but RAISED HIGH - wrist in the UPPER half of the frame (wrist y < 0.4). Wave optional.",
  },
  {
    name: "THANK_YOU",
    pose: "Open hand, 4 fingers extended and together, thumb folded FLAT ACROSS the palm (thumb tip touches the palm).",
  },
  {
    name: "YES",
    pose: "Thumbs-up: tight fist, thumb pointing UP. All other fingers folded.",
  },
  {
    name: "NO",
    pose: "Peace/V sign held STILL: index + middle up and spread apart, ring + pinky folded, thumb folded in.",
  },
  {
    name: "PLEASE",
    pose: "Flat open hand, fingers pressed TOGETHER, thumb tucked CLOSE against the index finger (not sticking out).",
  },
  {
    name: "HAPPY",
    pose: "Open palm with fingers SPREAD WIDE apart (big gaps between fingertips), like a starfish.",
  },
  {
    name: "SAD",
    pose: "Point DOWN: only the index finger extended, pointing at the floor; middle/ring/pinky folded, thumb folded in.",
  },
  {
    name: "STRESS",
    pose: "Tight fist: all fingers curled in, thumb folded over or tucked against the fingers (NOT sticking up or out).",
  },
  {
    name: "I_NEED_HELP",
    pose: 'Fist with the thumb sticking OUT TO THE SIDE (hitchhiker "call me" without the pinky).',
  },
];

/** THANK_YOU -> "Thank You" for readable headings (pose text stays verbatim). */
export function signLabel(name: SignName): string {
  return name
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
