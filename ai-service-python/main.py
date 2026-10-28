"""Saarthi AI Python service.

Module 3: Text-to-Speech via Microsoft Edge-TTS (Java calls POST /tts).
Module 6: Sign-language classification (POST /predict-sign, rule-based,
no ML weights - see sign_classifier.py).

Run (from ai-service-python/, with the project venv active):
    uvicorn main:app --reload --port 8000
"""

import asyncio
import logging
import re
import time

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel, Field

import edge_tts

from sign_classifier import SignClassifier

log = logging.getLogger("saarthi-tts")
sign_log = logging.getLogger("saarthi-sign")
sign_classifier = SignClassifier()

app = FastAPI(title="Saarthi AI Service")

# Short names accepted from Java. Full "<lang>-<Region>-<Name>Neural" ids pass through.
VOICES = {
    "female": "en-IN-NeerjaNeural",
    "male": "en-IN-PrabhatNeural",
    "hindi": "hi-IN-SwaraNeural",
}
DEFAULT_VOICE = VOICES["female"]
FULL_VOICE_RE = re.compile(r"^[a-z]{2}-[A-Z]{2}-.+Neural$")
TTS_TIMEOUT_S = 15
MAX_TEXT_CHARS = 2000


class TtsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_TEXT_CHARS)
    voice: str | None = None
    language: str | None = None


def resolve_voice(voice: str | None, language: str | None, text: str) -> str:
    """Explicit voice wins; otherwise Hindi language/Devanagari script picks the
    Hindi voice. Latin-script text (English and Hinglish, which cannot be told
    apart reliably) uses the default Indian-English voice."""
    if voice and voice.strip():
        key = voice.strip().lower()
        if key in VOICES:
            return VOICES[key]
        if FULL_VOICE_RE.match(voice.strip()):
            return voice.strip()
    lang = (language or "").strip().lower()
    if lang.startswith("hi"):
        return VOICES["hindi"]
    for ch in text or "":
        if "\u0900" <= ch <= "\u097F":
            return VOICES["hindi"]
    return DEFAULT_VOICE


async def synthesize(text: str, voice: str) -> bytes:
    communicate = edge_tts.Communicate(text, voice)
    parts: list[bytes] = []
    async for chunk in communicate.stream():
        if chunk.get("type") == "audio":
            parts.append(chunk["data"])
    return b"".join(parts)


@app.get("/health")
def health():
    return {"status": "UP", "service": "ai-service", "tts": "UP", "sign": "UP"}


class SignRequest(BaseModel):
    # 21 MediaPipe hand landmarks, each [x, y, z]. Kept loose (list) so a
    # malformed frame degrades to {sign: null} instead of a 422: sign input
    # must never break a session, per 08_AGENT_CONNECTION.md section 7.
    landmarks: list = Field(default_factory=list)


@app.post("/predict-sign")
def predict_sign(req: SignRequest):
    start = time.perf_counter()
    try:
        result = sign_classifier.classify(req.landmarks)
    except Exception:
        sign_log.warning("Sign classification failed", exc_info=True)
        result = {"sign": None, "confidence": 0.0}
    elapsed_ms = (time.perf_counter() - start) * 1000
    sign_log.info("[Sign] sign=%s confidence=%.2f latency=%.1fms",
                  result.get("sign"), result.get("confidence", 0.0), elapsed_ms)
    # Always HTTP 200: unknown poses and bad frames are {sign: null}, never
    # an error, so Java treats them as "no sign" without retries or alerts.
    return {"sign": result.get("sign"), "confidence": result.get("confidence", 0.0)}


@app.post("/tts")
async def text_to_speech(req: TtsRequest):
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text must not be empty")
    voice = resolve_voice(req.voice, req.language, text)
    try:
        audio = await asyncio.wait_for(synthesize(text, voice), timeout=TTS_TIMEOUT_S)
    except asyncio.TimeoutError:
        log.warning("TTS timed out voice=%s chars=%d", voice, len(text))
        raise HTTPException(status_code=500, detail="TTS failed")
    except Exception:
        log.warning("TTS provider failed voice=%s chars=%d", voice, len(text))
        raise HTTPException(status_code=500, detail="TTS failed")
    if not audio:
        raise HTTPException(status_code=500, detail="TTS failed")
    log.info("[TTS] Generated %d bytes for voice %s | text: %.50s", len(audio), voice, text)
    return Response(content=audio, media_type="audio/mpeg")
