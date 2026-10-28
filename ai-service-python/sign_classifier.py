"""Saarthi AI sign classifier (Module 6).

MVP SCOPE - READ BEFORE CHANGING: only 10 STATIC single-hand poses, each held
steady ~1.5 seconds. This is NOT full ISL/ASL sentence translation, NOT dynamic
motion grammar, and NOT two-hand sequences. A deterministic geometric
rule-set is intentional: it works today with zero training data. A trained ML
model can replace SignClassifier later behind the same classify() interface.

=====================================================================
DEMO CHEAT SHEET - how to physically hold each sign (camera-facing)
=====================================================================
 1. HELLO        Open palm facing the camera, fingers together, thumb out
                 to the side. Hold at CHEST height (wrist in lower half
                 of the frame). This is the DEFAULT open-palm label.
 2. HELP         Same open palm, but RAISED HIGH - wrist in the UPPER
                 half of the frame (wrist y < 0.4). Wave optional.
 3. THANK_YOU    Open hand, 4 fingers extended and together, thumb folded
                 FLAT ACROSS the palm (thumb tip touches the palm).
 4. YES          Thumbs-up: tight fist, thumb pointing UP. All other
                 fingers folded.
 5. NO           Peace/V sign held STILL: index + middle up and spread
                 apart, ring + pinky folded, thumb folded in.
 6. PLEASE       Flat open hand, fingers pressed TOGETHER, thumb tucked
                 CLOSE against the index finger (not sticking out).
 7. HAPPY        Open palm with fingers SPREAD WIDE apart (big gaps
                 between fingertips), like a starfish.
 8. SAD          Point DOWN: only the index finger extended, pointing at
                 the floor; middle/ring/pinky folded, thumb folded in.
                      index tip clearly BELOW its knuckle (y_tip > y_mcp)
 9. STRESS       Tight fist: all fingers curled in, thumb folded over or
                 tucked against the fingers (NOT sticking up or out).
10. I_NEED_HELP  Fist with the thumb sticking OUT TO THE SIDE
                 (hitchhiker "call me" without the pinky).

Most reliable on camera (distinct geometry): YES, STRESS, HELLO/HELP.
Hardest to separate: THANK_YOU vs PLEASE (both = fingers together, only
the thumb differs) - hold the thumb pose deliberately for these two.
=====================================================================

Geometry notes
--------------
* Input is MediaPipe HandLandmarker output: 21 landmarks, each [x, y, z]
  with x/y normalized to 0..1 (y grows DOWNWARD) and z a relative depth.
* Only x/y are used. MediaPipe z is noisy across devices, so palm-facing
  tests use 2D proxies (documented per rule) instead of depth.
* All distances are divided by hand_scale = wrist->middle_mcp distance, so
  rules work whether the hand is near or far from the camera.
* Landmark indices: 0 wrist, thumb 1-4 (tip 4), index 5-8 (tip 8),
  middle 9-12 (tip 12), ring 13-16 (tip 16), pinky 17-20 (tip 20).
  PIP joints: index 6, middle 10, ring 14, pinky 18.
"""

from __future__ import annotations

import math

import numpy as np

# MediaPipe hand landmark indices.
WRIST = 0
THUMB_MCP, THUMB_IP, THUMB_TIP = 2, 3, 4
INDEX_MCP, INDEX_PIP, INDEX_TIP = 5, 6, 8
MIDDLE_MCP, MIDDLE_PIP, MIDDLE_TIP = 9, 10, 12
RING_PIP, RING_TIP = 14, 16
PINKY_MCP, PINKY_PIP, PINKY_TIP = 17, 18, 20

_FINGERS = (
    ("index", INDEX_TIP, INDEX_PIP),
    ("middle", MIDDLE_TIP, MIDDLE_PIP),
    ("ring", RING_TIP, RING_PIP),
    ("pinky", PINKY_TIP, PINKY_PIP),
)

FULL_MATCH = 1.0
NEAR_MATCH = 0.70  # Deliberately below the 0.80 Java threshold: near matches
                   # are reported but never accepted as messages.
_NO_MATCH = 0.0


def _dist(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.linalg.norm(a[:2] - b[:2]))


class SignClassifier:
    """Rule-based static-pose classifier over 21 hand landmarks.

    classify() returns {"sign": NAME | None, "confidence": 0.0..1.0}.
    1.0 = every condition of exactly one rule matched strictly.
    0.70 = exactly one rule matched loosely (near miss, below threshold).
    None = no rule matched, or two rules matched (ambiguous pose).
    """

    def classify(self, landmarks) -> dict:
        pts = self._validate(landmarks)
        if pts is None:
            return {"sign": None, "confidence": 0.0}
        palm = np.mean(pts[[WRIST, INDEX_MCP, MIDDLE_MCP, 13, PINKY_MCP]], axis=0)
        scale = _dist(pts[WRIST], pts[MIDDLE_MCP])
        if scale < 1e-3:
            return {"sign": None, "confidence": 0.0}  # Degenerate hand.
        if not self._plausible_hand(pts, scale):
            return {"sign": None, "confidence": 0.0}  # No real hand looks like this.

        ctx = _HandContext(pts, palm, scale)
        # Ordered most-specific-first; ties at FULL_MATCH resolve to None.
        rules = (
            ("YES", ctx.yes),
            ("I_NEED_HELP", ctx.i_need_help),
            ("STRESS", ctx.stress),
            ("NO", ctx.no),
            ("THANK_YOU", ctx.thank_you),
            ("PLEASE", ctx.please),
            ("SAD", ctx.sad),
            ("HELP", ctx.help),
            ("HAPPY", ctx.happy),
            ("HELLO", ctx.hello),
        )
        full_hits: list[str] = []
        near_hits: list[str] = []
        for name, rule in rules:
            score = rule()
            if score >= FULL_MATCH:
                full_hits.append(name)
            elif score > _NO_MATCH:
                near_hits.append(name)
        if len(full_hits) == 1:
            return {"sign": full_hits[0], "confidence": FULL_MATCH}
        if len(full_hits) > 1:
            return {"sign": None, "confidence": 0.0}  # Ambiguous, refuse to guess.
        if len(near_hits) == 1:
            return {"sign": near_hits[0], "confidence": NEAR_MATCH}
        return {"sign": None, "confidence": 0.0}

    @staticmethod
    def _plausible_hand(pts: np.ndarray, scale: float) -> bool:
        """Coarse anatomical gate: rejects scattered/partial garbage BEFORE
        the pose rules run, so random hand movement can never yield a
        confident label (goal: zero false messages to Llama). Checks only
        rigid palm structure, never the pose itself:
        - knuckle MCPs sit at plausible distances from the wrist,
        - knuckles run in order left-to-right (either direction: either hand,
          mirrored or not),
        - no finger is absurdly long, thumb chain length is sane.
        Limitation: hold the hand upright facing the camera; a hand rotated
        90 degrees fails the knuckle-order check by design."""
        wrist = pts[WRIST]
        for mcp in (INDEX_MCP, 13, PINKY_MCP):
            ratio = _dist(wrist, pts[mcp]) / scale
            if not 0.60 <= ratio <= 1.45:
                return False
        xs = [pts[i][0] for i in (INDEX_MCP, MIDDLE_MCP, 13, PINKY_MCP)]
        mcp_inc = all(a < b for a, b in zip(xs, xs[1:]))
        mcp_dec = all(a > b for a, b in zip(xs, xs[1:]))
        if not (mcp_inc or mcp_dec):
            return False
        # PIP joints follow the knuckle row (curled fingers keep PIPs near
        # their MCPs, so a scattered PIP row means scattered noise).
        pxs = [pts[i][0] for i in (INDEX_PIP, MIDDLE_PIP, RING_PIP, PINKY_PIP)]
        pip_inc = all(a < b for a, b in zip(pxs, pxs[1:]))
        pip_dec = all(a > b for a, b in zip(pxs, pxs[1:]))
        if (mcp_inc and not pip_inc) or (mcp_dec and not pip_dec):
            return False
        # Finger chains stay compact: every joint sits near its neighbors.
        # Real joints (straight or curled) always do; random points don't.
        for mcp, pip, dip, tip in ((INDEX_MCP, INDEX_PIP, 7, INDEX_TIP),
                                   (MIDDLE_MCP, MIDDLE_PIP, 11, MIDDLE_TIP),
                                   (13, RING_PIP, 15, RING_TIP),
                                   (PINKY_MCP, PINKY_PIP, 19, PINKY_TIP)):
            if _dist(pts[pip], pts[mcp]) / scale > 0.9:
                return False
            if _dist(pts[dip], pts[pip]) / scale > 0.8:
                return False
            if _dist(pts[dip], pts[tip]) / scale > 0.8:
                return False
            if _dist(pts[tip], pts[mcp]) / scale > 2.2:
                return False
        if _dist(pts[THUMB_IP], pts[THUMB_MCP]) / scale > 1.2:
            return False
        if _dist(pts[THUMB_TIP], pts[THUMB_IP]) / scale > 1.2:
            return False
        thumb_len = _dist(pts[THUMB_TIP], pts[THUMB_MCP]) / scale
        return 0.30 <= thumb_len <= 1.80

    @staticmethod
    def _validate(landmarks) -> np.ndarray | None:
        """Exactly 21 landmarks of 3 finite numbers, else None."""
        try:
            pts = np.asarray(landmarks, dtype=float)
        except (TypeError, ValueError):
            return None
        if pts.shape != (21, 3):
            return None
        if not np.all(np.isfinite(pts)):
            return None
        return pts


class _HandContext:
    """Precomputed per-finger geometry for one frame.

    A finger is EXTENDED when its two segments are near-collinear and the
    tip sits beyond the PIP joint (orientation-independent, so raised and
    pointing-down hands work). It is FOLDED when the tip collapses toward
    the PIP (curled fingers foreshorten in a front camera view) or the
    joint is clearly bent.
    """

    # tip->pip length below which a finger counts as curled (scale units).
    _FOLD_LEN = {"index": 0.34, "middle": 0.34, "ring": 0.34, "pinky": 0.28}

    def __init__(self, pts: np.ndarray, palm: np.ndarray, scale: float):
        self.p = pts
        self.palm = palm
        self.s = scale
        self.ext = {}   # finger -> (strict_extended, loose_extended)
        self.fold = {}  # finger -> (strict_folded, loose_folded)
        mcp_of = {"index": INDEX_MCP, "middle": MIDDLE_MCP,
                  "ring": 13, "pinky": PINKY_MCP}
        for name, tip, pip in _FINGERS:
            mcp = mcp_of[name]
            v1 = pts[pip][:2] - pts[mcp][:2]
            v2 = pts[tip][:2] - pts[pip][:2]
            n1, n2 = float(np.linalg.norm(v1)), float(np.linalg.norm(v2))
            cos = float(np.dot(v1, v2) / (n1 * n2)) if n1 > 1e-9 and n2 > 1e-9 else -1.0
            short = n2 / scale
            beyond = _dist(pts[tip], pts[mcp]) > _dist(pts[pip], pts[mcp])
            fold_t = self._FOLD_LEN[name]
            self.fold[name] = (short < fold_t or cos < 0.45,
                               short < fold_t + 0.06 or cos < 0.60)
            self.ext[name] = (cos > 0.75 and beyond and short >= fold_t,
                              cos > 0.60 and beyond and short >= fold_t - 0.04)
        spread = _dist(pts[THUMB_TIP], palm) / scale
        self.thumb_out = (spread > 0.80, spread > 0.68)
        self.thumb_in = (spread < 0.60, spread < 0.70)

    # -- shared measurements -------------------------------------------------
    def _up(self) -> tuple[bool, bool]:
        lift = (self.p[THUMB_IP][1] - self.p[THUMB_TIP][1]) / self.s
        return (lift > 0.30, lift > 0.18)

    def _sideways(self) -> tuple[bool, bool]:
        dx = abs(self.p[THUMB_TIP][0] - self.p[THUMB_MCP][0]) / self.s
        dy = abs(self.p[THUMB_TIP][1] - self.p[THUMB_MCP][1]) / self.s
        return (dx > 0.55 and dy < 0.50, dx > 0.42 and dy < 0.62)

    def _tip_gaps(self) -> list[float]:
        # Horizontal tip separation only: vertical stagger between fingertips
        # is anatomical (middle longest, pinky shortest), not pose signal.
        tips = [INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP]
        return [abs(self.p[a][0] - self.p[b][0]) / self.s
                for a, b in zip(tips, tips[1:])]

    def _all_ext(self, strict: bool) -> bool:
        idx = 0 if strict else 1
        return all(self.ext[f][idx] for f, _, _ in _FINGERS)

    def _all_fold(self, strict: bool) -> bool:
        idx = 0 if strict else 1
        return all(self.fold[f][idx] for f, _, _ in _FINGERS)

    @staticmethod
    def _score(strict_all: bool, loose_all: bool) -> float:
        if strict_all:
            return FULL_MATCH
        if loose_all:
            return NEAR_MATCH
        return _NO_MATCH

    # -- fist family: thumb position is the ONLY distinguisher ----------------
    def yes(self) -> float:
        up_s, up_l = self._up()
        out_s, out_l = self.thumb_out
        strict = self._all_fold(True) and out_s and up_s
        loose = self._all_fold(False) and out_l and up_l
        return self._score(strict, loose)

    def i_need_help(self) -> float:
        side_s, side_l = self._sideways()
        out_s, out_l = self.thumb_out
        strict = self._all_fold(True) and out_s and side_s
        loose = self._all_fold(False) and out_l and side_l
        return self._score(strict, loose)

    def stress(self) -> float:
        in_s, in_l = self.thumb_in
        strict = self._all_fold(True) and in_s
        loose = self._all_fold(False) and in_l
        return self._score(strict, loose)

    # -- two-finger / one-finger poses -----------------------------------------
    def no(self) -> float:
        gap = _dist(self.p[INDEX_TIP], self.p[MIDDLE_TIP]) / self.s
        in_s, in_l = self.thumb_in
        strict = (self.ext["index"][0] and self.ext["middle"][0]
                  and self.fold["ring"][0] and self.fold["pinky"][0]
                  and in_s and gap > 0.28)
        loose = (self.ext["index"][1] and self.ext["middle"][1]
                 and self.fold["ring"][1] and self.fold["pinky"][1]
                 and in_l and gap > 0.22)
        return self._score(strict, loose)

    def sad(self) -> float:
        drop = (self.p[INDEX_TIP][1] - self.p[INDEX_MCP][1]) / self.s
        in_s, in_l = self.thumb_in
        strict = (self.ext["index"][0] and drop > 0.55
                  and self.fold["middle"][0] and self.fold["ring"][0]
                  and self.fold["pinky"][0] and in_s)
        loose = (self.ext["index"][1] and drop > 0.42
                 and self.fold["middle"][1] and self.fold["ring"][1]
                 and self.fold["pinky"][1] and in_l)
        return self._score(strict, loose)

    # -- open-palm family --------------------------------------------------------
    # Spread/together use the index-middle-ring gaps only: the pinky splays
    # naturally, so its gap is pose noise, not signal. HELLO/HELP exclude
    # spread hands (a raised spread hand reads as HAPPY - the deliberate
    # signal wins). Relaxed vs pressed-together fingers overlap in gap
    # size, so THANK_YOU/PLEASE/HELLO are separated by THUMB position.
    def thank_you(self) -> float:
        across = _dist(self.p[THUMB_TIP], self.palm) / self.s
        gaps = self._tip_gaps()
        strict = (self._all_ext(True) and across < 0.50 and max(gaps) < 0.32)
        loose = (self._all_ext(False) and across < 0.60 and max(gaps) < 0.38)
        return self._score(strict, loose)

    def please(self) -> float:
        near_index = _dist(self.p[THUMB_TIP], self.p[INDEX_MCP]) / self.s
        across = _dist(self.p[THUMB_TIP], self.palm) / self.s
        gaps = self._tip_gaps()
        # across check excludes THANK_YOU: a thumb folded over the palm is
        # also "near the index", so PLEASE requires it off the palm.
        strict = (self._all_ext(True) and near_index < 0.55 and across > 0.55
                  and max(gaps) < 0.32)
        loose = (self._all_ext(False) and near_index < 0.65 and across > 0.48
                 and max(gaps) < 0.38)
        return self._score(strict, loose)

    def help(self) -> float:
        raised = self.p[WRIST][1]
        gaps = self._tip_gaps()[:2]
        out_s, out_l = self.thumb_out
        strict = self._all_ext(True) and out_s and raised < 0.40 and max(gaps) < 0.34
        loose = self._all_ext(False) and out_l and raised < 0.46 and max(gaps) < 0.40
        return self._score(strict, loose)

    def happy(self) -> float:
        gaps = self._tip_gaps()[:2]
        out_s, out_l = self.thumb_out
        strict = self._all_ext(True) and out_s and min(gaps) > 0.34
        loose = self._all_ext(False) and out_l and min(gaps) > 0.28
        return self._score(strict, loose)

    def hello(self) -> float:
        away = _dist(self.p[THUMB_TIP], self.p[INDEX_MCP]) / self.s
        gaps = self._tip_gaps()[:2]
        out_s, out_l = self.thumb_out
        lowered = self.p[WRIST][1] >= 0.40
        strict = (self._all_ext(True) and out_s and away > 0.60
                  and lowered and max(gaps) < 0.34)
        loose = (self._all_ext(False) and out_l and away > 0.52
                 and lowered and max(gaps) < 0.40)
        return self._score(strict, loose)


# ---------------------------------------------------------------------------
# Zero-dependency self-test: python3 sign_classifier.py --selftest
# Builds synthetic hands (parametric fingers, no camera needed) and asserts
# the headline poses classify correctly and garbage never does.
# ---------------------------------------------------------------------------

def _synth_hand(fingers: dict[str, tuple[float, float]],
                thumb_tip: tuple[float, float],
                wrist: tuple[float, float] = (0.5, 0.62)) -> list[list[float]]:
    """Build 21 landmarks. fingers maps name -> (tip_xy beside its mcp, curl).

    curl 0.0 = fully extended along `direction`, 1.0 = folded into the palm.
    Good enough to exercise every geometric rule deterministically.
    """
    mcps = {
        "thumb": (0.42, 0.58), "index": (0.46, 0.50),
        "middle": (0.50, 0.48), "ring": (0.54, 0.50), "pinky": (0.575, 0.53),
    }
    pts = [[wrist[0], wrist[1], 0.0] for _ in range(21)]
    pts[1] = [0.45, 0.60, 0.0]
    pts[THUMB_MCP] = [mcps["thumb"][0], mcps["thumb"][1], 0.0]
    pts[THUMB_IP] = [(mcps["thumb"][0] + thumb_tip[0]) / 2,
                     (mcps["thumb"][1] + thumb_tip[1]) / 2, 0.0]
    pts[THUMB_TIP] = [thumb_tip[0], thumb_tip[1], 0.0]
    order = (("index", 5, 6, 7, 8), ("middle", 9, 10, 11, 12),
             ("ring", 13, 14, 15, 16), ("pinky", 17, 18, 19, 20))
    for name, mcp_i, pip_i, dip_i, tip_i in order:
        (dx, dy), curl = fingers[name]
        mx, my = mcps[name]
        pts[mcp_i] = [mx, my, 0.0]
        length = 0.16 if name != "pinky" else 0.12
        if curl >= 1.0:
            # Folded: knuckle juts slightly, tip tucks into the palm close
            # to the PIP (curled fingers foreshorten in a front view).
            px, py = mx + dx * 0.02, my + 0.028
            tx, ty = mx + dx * 0.02 + 0.005, my + 0.055
            pts[tip_i] = [tx, ty, 0.0]
            pts[pip_i] = [px, py, 0.0]
            pts[dip_i] = [(px + tx) / 2, (py + ty) / 2, 0.0]
            continue
        tx, ty = mx + dx * length, my + dy * length
        pts[tip_i] = [tx, ty, 0.0]
        pts[pip_i] = [mx + (tx - mx) * 0.45, my + (ty - my) * 0.45, 0.0]
        pts[dip_i] = [mx + (tx - mx) * 0.75, my + (ty - my) * 0.75, 0.0]
    return pts


_UP = (0.0, -1.0)
_DOWN = (0.0, 1.0)


def _pose_builders() -> dict:
    """One synthetic 21-landmark hand per sign, shared by --selftest and
    --dump-pose (the manual test script's curl payloads)."""
    open_up = {n: (_UP, 0.0) for n in ("index", "middle", "ring", "pinky")}
    fist = {n: (_UP, 1.0) for n in ("index", "middle", "ring", "pinky")}
    peace = {"index": ((-0.25, -0.97), 0.0), "middle": ((0.25, -0.97), 0.0),
             "ring": (_UP, 1.0), "pinky": (_UP, 1.0)}
    sad = {"index": (_DOWN, 0.0), "middle": (_UP, 1.0),
           "ring": (_UP, 1.0), "pinky": (_UP, 1.0)}
    tight = {n: ((dx, -1.0), 0.0) for n, dx in
             (("index", -0.01), ("middle", 0.0), ("ring", 0.01), ("pinky", -0.10))}
    spread = {n: ((dx, -1.0), 0.0) for n, dx in
              (("index", -0.45), ("middle", -0.12), ("ring", 0.18), ("pinky", 0.45))}
    return {
        "HELP": lambda: _synth_hand(open_up, (0.30, 0.52), wrist=(0.5, 0.30)),
        "HELLO": lambda: _synth_hand(open_up, (0.30, 0.52)),
        "YES": lambda: _synth_hand(fist, (0.42, 0.38)),
        "STRESS": lambda: _synth_hand(fist, (0.46, 0.56)),
        "I_NEED_HELP": lambda: _synth_hand(fist, (0.28, 0.58)),
        "NO": lambda: _synth_hand(peace, (0.50, 0.55)),
        "SAD": lambda: _synth_hand(sad, (0.50, 0.55)),
        "THANK_YOU": lambda: _synth_hand(tight, (0.50, 0.54)),
        "PLEASE": lambda: _synth_hand(tight, (0.41, 0.52)),
        "HAPPY": lambda: _synth_hand(spread, (0.28, 0.50)),
    }


def _selftest() -> int:
    clf = SignClassifier()
    failures = []

    def check(label: str, got: dict, want_sign) -> None:
        ok = got["sign"] == want_sign and (
            want_sign is None or got["confidence"] > 0.80)
        print(("PASS " if ok else "FAIL ") + f"{label}: {got}")
        if not ok:
            failures.append(label)

    for name, build in _pose_builders().items():
        check(name, clf.classify(build()), name)

    # Negatives: garbage in, silence out (never a confident wrong label).
    check("empty", clf.classify([]), None)
    check("short", clf.classify([[0.1, 0.2, 0.0]] * 20), None)
    check("non-numeric", clf.classify([["a", "b", "c"]] * 21), None)
    check("nan", clf.classify([[math.nan, 0.0, 0.0]] * 21), None)
    check("flat", clf.classify([[0.5, 0.5, 0.0]] * 21), None)
    import random
    random.seed(7)
    noisy = [[random.random(), random.random(), 0.0] for _ in range(21)]
    check("random", clf.classify(noisy), None)

    # Fuzz: hundreds of scattered hands must ALL stay silent - this is the
    # "random hand movement sends nothing to Llama" guarantee.
    fuzz_bad = 0
    for seed in range(300):
        random.seed(1000 + seed)
        hand = [[random.random(), random.random(), 0.0] for _ in range(21)]
        got = clf.classify(hand)
        if got["sign"] is not None and got["confidence"] > 0.80:
            fuzz_bad += 1
    print(("PASS " if fuzz_bad == 0 else "FAIL ") + f"fuzz-300-quiet ({fuzz_bad} loud)")
    if fuzz_bad:
        failures.append("fuzz-300-quiet")

    # Mirrored hand (other hand / selfie view): geometry is left-right
    # agnostic, so a mirrored YES is still YES.
    yes_hand = _pose_builders()["YES"]()
    mirrored = [[1.0 - x, y, z] for x, y, z in yes_hand]
    check("YES-mirrored", clf.classify(mirrored), "YES")

    print(f"{'SELFTEST OK' if not failures else 'SELFTEST FAILED'} "
          f"({len(failures)} failures)")
    return 1 if failures else 0


if __name__ == "__main__":
    import json
    import sys

    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        sys.exit(_selftest())
    if len(sys.argv) == 3 and sys.argv[1] == "--dump-pose":
        builders = _pose_builders()
        name = sys.argv[2].upper()
        if name not in builders:
            print(f"unknown pose '{sys.argv[2]}', pick from: {', '.join(builders)}")
            sys.exit(2)
        print(json.dumps({"landmarks": builders[name]()}))
        sys.exit(0)
    print("usage: python3 sign_classifier.py --selftest")
    print("       python3 sign_classifier.py --dump-pose HELP  # curl payload")
